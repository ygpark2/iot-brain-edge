from fastapi import FastAPI
from pydantic import BaseModel
from typing import List
import os
import json
from kafka import KafkaConsumer, KafkaProducer
import threading
import time

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

producer = KafkaProducer(
    bootstrap_servers=KAFKA_BOOTSTRAP,
    value_serializer=lambda v: json.dumps(v).encode("utf-8"),
)

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/v1/infer", response_model=InferenceResponse)
def infer(req: InferenceRequest):
    score = sum(req.features) / max(1.0, len(req.features))
    label = "demo" if score >= 0 else "demo"
    return InferenceResponse(
        device_id=req.device_id,
        session_id=req.session_id,
        sensor_type=req.sensor_type,
        model_version=MODEL_VERSION,
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
