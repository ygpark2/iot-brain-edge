import os
import json
import threading
import logging
import signal
import sys
from typing import List, Optional, Set, Tuple

import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from kafka import KafkaConsumer, KafkaProducer

# Setup logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
logger = logging.getLogger("inference-service")

try:
    import tritonclient.grpc as triton_grpc
except ImportError:
    triton_grpc = None
    logger.warning("tritonclient.grpc not found, Triton mode will be disabled.")

app = FastAPI(title="brain inference-service")

class InferenceRequest(BaseModel):
    device_id: str
    session_id: str
    sensor_type: str
    start_ts_ms: int
    end_ts_ms: int
    feature_schema_version: str
    features: List[float]

class InferenceResponse(BaseModel):
    device_id: str
    session_id: str
    sensor_type: str
    model_version: str
    label: str
    score: float
    start_ts_ms: int
    end_ts_ms: int

# Configuration
KAFKA_BOOTSTRAP = os.getenv("INFERENCE_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
INFERENCE_REQUESTS_TOPIC = os.getenv("INFERENCE_REQUESTS_TOPIC", "inference-requests")
INFERENCE_RESULTS_TOPIC = os.getenv("INFERENCE_RESULTS_TOPIC", "inference-results")
INFERENCE_ALERTS_TOPIC = os.getenv("INFERENCE_ALERTS_TOPIC", "inference-alerts")
MODEL_VERSION = os.getenv("MODEL_VERSION", "brain-v1-stable")
ALERT_THRESHOLD = float(os.getenv("INFERENCE_ALERT_THRESHOLD", "0.8"))
INFERENCE_SENSOR_TYPES = os.getenv("INFERENCE_SENSOR_TYPES", "GRF,PLANTAR,SCANNER")

TRITON_ENABLED = os.getenv("TRITON_ENABLED", "").lower() in ("1", "true", "yes", "on")
TRITON_URL = os.getenv("TRITON_URL", "")
TRITON_MODEL_NAME = os.getenv("TRITON_MODEL_NAME", "health_motion_model")
TRITON_LABELS = os.getenv("TRITON_LABELS", "Normal,Anomaly,Critical")

# Kafka Producer
producer = KafkaProducer(
    bootstrap_servers=KAFKA_BOOTSTRAP,
    value_serializer=lambda v: json.dumps(v).encode("utf-8"),
    acks='all',
    retries=3
)

class TritonInferenceClient:
    def __init__(self) -> None:
        if not triton_grpc:
            raise RuntimeError("tritonclient.grpc is not available")
        if not TRITON_URL:
            raise RuntimeError("TRITON_URL is not set")
        self.client = triton_grpc.InferenceServerClient(url=TRITON_URL, verbose=False)
        self.labels = [l.strip() for l in TRITON_LABELS.split(",")]

    def infer(self, features: List[float]) -> Tuple[str, float, str]:
        # Implementation for Triton gRPC inference
        # (Assuming FP32 input and Scores output)
        array = np.asarray(features, dtype=np.float32).reshape(1, -1)
        inputs = [triton_grpc.InferInput("features", array.shape, "FP32")]
        inputs[0].set_data_from_numpy(array)
        outputs = [triton_grpc.InferRequestedOutput("scores")]
        
        result = self.client.infer(model_name=TRITON_MODEL_NAME, inputs=inputs, outputs=outputs)
        scores = result.as_numpy("scores")[0]
        
        best_idx = int(np.argmax(scores))
        label = self.labels[best_idx] if best_idx < len(self.labels) else str(best_idx)
        score = float(scores[best_idx])
        
        return label, score, TRITON_MODEL_NAME

class LocalFallbackModel:
    """A simple heuristic-based fallback model when Triton is unavailable."""
    def infer(self, features: List[float]) -> Tuple[str, float, str]:
        if not features:
            return "Unknown", 0.0, "fallback-v1"
        
        # Mock logic: use variance as anomaly score
        avg = sum(features) / len(features)
        variance = sum((x - avg) ** 2 for x in features) / len(features)
        score = min(1.0, variance * 10) # arbitrary scaling
        
        label = "Critical" if score > 0.9 else "Anomaly" if score > 0.6 else "Normal"
        return label, score, "fallback-v1"

_sensor_filter = {v.strip().upper() for v in INFERENCE_SENSOR_TYPES.split(",")}
_inference_engine = None

if TRITON_ENABLED and TRITON_URL:
    try:
        _inference_engine = TritonInferenceClient()
        logger.info(f"Connected to Triton at {TRITON_URL}")
    except Exception as e:
        logger.error(f"Failed to connect to Triton: {e}. Falling back to local model.")
        _inference_engine = LocalFallbackModel()
else:
    logger.info("Triton not enabled. Using local fallback model.")
    _inference_engine = LocalFallbackModel()

@app.get("/health")
def health():
    return {"status": "ok", "engine": _inference_engine.__class__.__name__}

@app.post("/v1/infer", response_model=InferenceResponse)
def infer_endpoint(req: InferenceRequest):
    try:
        label, score, model_ver = _inference_engine.infer(req.features)
        return InferenceResponse(
            device_id=req.device_id,
            session_id=req.session_id,
            sensor_type=req.sensor_type,
            model_version=model_ver,
            label=label,
            score=score,
            start_ts_ms=req.start_ts_ms,
            end_ts_ms=req.end_ts_ms
        )
    except Exception as e:
        logger.error(f"Inference error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

def kafka_worker():
    logger.info(f"Starting Kafka consumer for topic: {INFERENCE_REQUESTS_TOPIC}")
    consumer = KafkaConsumer(
        INFERENCE_REQUESTS_TOPIC,
        bootstrap_servers=KAFKA_BOOTSTRAP,
        group_id="inference-service-group",
        auto_offset_reset='earliest',
        value_deserializer=lambda v: json.loads(v.decode('utf-8'))
    )

    for msg in consumer:
        try:
            req_data = msg.value
            if req_data.get('sensor_type', '').upper() not in _sensor_filter:
                continue

            label, score, model_ver = _inference_engine.infer(req_data['features'])
            
            result = {
                **req_data,
                "label": label,
                "score": score,
                "model_version": model_ver,
                "event_id": f"inf-{req_data.get('session_id')}-{int(time.time()*1000)}"
            }
            
            # Publish result
            producer.send(INFERENCE_RESULTS_TOPIC, result)
            
            # Publish alert if threshold exceeded
            if score >= ALERT_THRESHOLD:
                alert = {
                    "event_id": f"alt-{result['event_id']}",
                    "device_id": result['device_id'],
                    "session_id": result['session_id'],
                    "label": label,
                    "score": score,
                    "threshold": ALERT_THRESHOLD,
                    "ts": int(time.time() * 1000)
                }
                producer.send(INFERENCE_ALERTS_TOPIC, alert)
                logger.info(f"ALERT generated for session {result['session_id']}: {label} ({score:.2f})")
                
        except Exception as e:
            logger.error(f"Error processing Kafka message: {e}")

# Graceful shutdown
def handle_exit(sig, frame):
    logger.info("Shutting down...")
    producer.flush()
    producer.close()
    sys.exit(0)

signal.signal(signal.SIGINT, handle_exit)
signal.signal(signal.SIGTERM, handle_exit)

import time
threading.Thread(target=kafka_worker, daemon=True).start()
