from fastapi import FastAPI
from pydantic import BaseModel
from typing import List, Optional, Set, Tuple
import os
import json
import threading
import time

import numpy as np
from kafka import KafkaConsumer, KafkaProducer

try:
    import tritonclient.grpc as triton_grpc
except Exception:
    triton_grpc = None

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

KAFKA_BOOTSTRAP = os.getenv("INFERENCE_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
INFERENCE_REQUESTS_TOPIC = os.getenv("INFERENCE_REQUESTS_TOPIC", "inference-requests")
INFERENCE_RESULTS_TOPIC = os.getenv("INFERENCE_RESULTS_TOPIC", "inference-results")
INFERENCE_ALERTS_TOPIC = os.getenv("INFERENCE_ALERTS_TOPIC", "inference-alerts")
MODEL_VERSION = os.getenv("MODEL_VERSION", "demo-v1")
ALERT_THRESHOLD = float(os.getenv("INFERENCE_ALERT_THRESHOLD", "0.7"))
INFERENCE_SENSOR_TYPES = os.getenv("INFERENCE_SENSOR_TYPES", "GRF,PLANTAR,SCANNER")
TRITON_ENABLED = os.getenv("TRITON_ENABLED", "").lower() in ("1", "true", "yes", "on")
TRITON_URL = os.getenv("TRITON_URL", "")
TRITON_MODEL_NAME = os.getenv("TRITON_MODEL_NAME", "health_motion_model")
TRITON_MODEL_VERSION = os.getenv("TRITON_MODEL_VERSION", "")
TRITON_INPUT_NAME = os.getenv("TRITON_INPUT_NAME", "features")
TRITON_OUTPUT_SCORES = os.getenv("TRITON_OUTPUT_SCORES", "scores")
TRITON_OUTPUT_LABELS = os.getenv("TRITON_OUTPUT_LABELS", "")
TRITON_LABELS = os.getenv("TRITON_LABELS", "")

producer = KafkaProducer(
    bootstrap_servers=KAFKA_BOOTSTRAP,
    value_serializer=lambda v: json.dumps(v).encode("utf-8"),
)

def parse_sensor_types(value: str) -> Set[str]:
    return {v.strip().upper() for v in value.split(",") if v.strip()}

def parse_label_list(value: str) -> List[str]:
    return [v.strip() for v in value.split(",") if v.strip()]

class TritonInferenceClient:
    def __init__(self) -> None:
        if not triton_grpc:
            raise RuntimeError("tritonclient.grpc is not available")
        if not TRITON_URL:
            raise RuntimeError("TRITON_URL is not set")
        self.client = triton_grpc.InferenceServerClient(url=TRITON_URL, verbose=False)
        self.labels = parse_label_list(TRITON_LABELS)

    def infer(self, features: List[float]) -> Tuple[str, float, str]:
        array = np.asarray(features, dtype=np.float32)
        if array.ndim == 1:
            array = array.reshape(1, -1)

        inputs = []
        inp = triton_grpc.InferInput(TRITON_INPUT_NAME, array.shape, "FP32")
        inp.set_data_from_numpy(array)
        inputs.append(inp)

        outputs = [triton_grpc.InferRequestedOutput(TRITON_OUTPUT_SCORES)]
        if TRITON_OUTPUT_LABELS:
            outputs.append(triton_grpc.InferRequestedOutput(TRITON_OUTPUT_LABELS))

        result = self.client.infer(
            model_name=TRITON_MODEL_NAME,
            model_version=TRITON_MODEL_VERSION or None,
            inputs=inputs,
            outputs=outputs,
        )

        scores = result.as_numpy(TRITON_OUTPUT_SCORES)
        if scores is None:
            raise RuntimeError("Triton response missing scores output")
        scores = np.asarray(scores).astype(np.float32).reshape(-1)

        if TRITON_OUTPUT_LABELS:
            labels = result.as_numpy(TRITON_OUTPUT_LABELS)
            if labels is None:
                raise RuntimeError("Triton response missing labels output")
            label_val = labels.reshape(-1)[0]
            if isinstance(label_val, bytes):
                label = label_val.decode("utf-8")
            else:
                label = str(label_val)
            score = float(scores[0]) if scores.size == 1 else float(scores.max())
        else:
            best_idx = int(np.argmax(scores))
            label = self.labels[best_idx] if best_idx < len(self.labels) else str(best_idx)
            score = float(scores[best_idx])

        model_version = TRITON_MODEL_VERSION or getattr(result.get_response(), "model_version", "") or MODEL_VERSION
        return label, score, model_version


_sensor_filter = parse_sensor_types(INFERENCE_SENSOR_TYPES)
_triton_client: Optional[TritonInferenceClient] = None
if TRITON_ENABLED or TRITON_URL:
    try:
        _triton_client = TritonInferenceClient()
    except Exception as exc:
        print(f"[inference-service] Triton disabled: {exc}")
        _triton_client = None

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/v1/infer", response_model=InferenceResponse)
def infer(req: InferenceRequest):
    if _triton_client:
        label, score, model_version = _triton_client.infer(req.features)
    else:
        score = sum(req.features) / max(1.0, len(req.features))
        label = "demo" if score >= 0 else "demo"
        model_version = MODEL_VERSION
    return InferenceResponse(
        device_id=req.device_id,
        session_id=req.session_id,
        sensor_type=req.sensor_type,
        model_version=model_version,
        label=label,
        score=score,
        start_ts_ms=req.start_ts_ms,
        end_ts_ms=req.end_ts_ms,
    )


def kafka_loop():
    consumer = KafkaConsumer(
        INFERENCE_REQUESTS_TOPIC,
        bootstrap_servers=KAFKA_BOOTSTRAP,
        auto_offset_reset="earliest",
        enable_auto_commit=True,
        value_deserializer=lambda v: json.loads(v.decode("utf-8")),
        group_id="inference-service",
    )
    for msg in consumer:
        try:
            payload = msg.value
            req = InferenceRequest(**payload)
            if _sensor_filter and req.sensor_type.upper() not in _sensor_filter:
                continue
            resp = infer(req)
            payload = resp.dict()
            producer.send(INFERENCE_RESULTS_TOPIC, payload)
            if payload["score"] >= ALERT_THRESHOLD:
                alert = {
                    "device_id": payload["device_id"],
                    "session_id": payload["session_id"],
                    "sensor_type": payload["sensor_type"],
                    "model_version": payload["model_version"],
                    "label": payload["label"],
                    "score": payload["score"],
                    "threshold": ALERT_THRESHOLD,
                    "start_ts_ms": payload["start_ts_ms"],
                    "end_ts_ms": payload["end_ts_ms"],
                }
                producer.send(INFERENCE_ALERTS_TOPIC, alert)
        except Exception:
            continue


def start_consumer_thread():
    t = threading.Thread(target=kafka_loop, daemon=True)
    t.start()


start_consumer_thread()
