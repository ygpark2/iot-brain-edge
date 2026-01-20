from fastapi import FastAPI
from pydantic import BaseModel
from typing import List

app = FastAPI(title="brain inference-service")

class InferenceRequest(BaseModel):
    session_id: str
    features: List[float]

class InferenceResponse(BaseModel):
    session_id: str
    label: str
    score: float

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/v1/infer", response_model=InferenceResponse)
def infer(req: InferenceRequest):
    return InferenceResponse(session_id=req.session_id, label="demo", score=0.5)
