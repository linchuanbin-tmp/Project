import os
from typing import List, Optional, Union

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer


DEFAULT_MODEL = os.getenv("RAG_WORKER_MODEL", "BAAI/bge-m3")
DEFAULT_DEVICE = os.getenv("RAG_WORKER_DEVICE") or None
NORMALIZE_EMBEDDINGS = os.getenv("RAG_WORKER_NORMALIZE", "true").lower() not in {"0", "false", "no"}
MAX_BATCH_SIZE = int(os.getenv("RAG_WORKER_MAX_BATCH_SIZE", "32"))
ALLOW_DOWNLOAD = os.getenv("HF_DOWNLOAD", "1").lower() not in {"0", "false", "no"}
TRUST_REMOTE_CODE = os.getenv("RAG_WORKER_TRUST_REMOTE_CODE", "false").lower() in {"1", "true", "yes"}

app = FastAPI(title="RAG Embedding Worker", version="1.0.0")

_model: Optional[SentenceTransformer] = None
_model_name: Optional[str] = None
_dimension: Optional[int] = None


class EmbedRequest(BaseModel):
    input: Optional[Union[str, List[str]]] = Field(default=None)
    text: Optional[Union[str, List[str]]] = Field(default=None)
    model: Optional[str] = Field(default=None)


class EmbedResponse(BaseModel):
    embedding: Optional[List[float]] = None
    data: Optional[List[dict]] = None
    dimension: int
    model: str
    normalized: bool


def get_model(model_name: Optional[str] = None) -> SentenceTransformer:
    global _model, _model_name, _dimension
    requested = model_name or DEFAULT_MODEL
    if _model is None or _model_name != requested:
        _model = SentenceTransformer(
            requested,
            device=DEFAULT_DEVICE,
            local_files_only=not ALLOW_DOWNLOAD,
            trust_remote_code=TRUST_REMOTE_CODE,
        )
        _model_name = requested
        probe = _model.encode(["dimension probe"], normalize_embeddings=NORMALIZE_EMBEDDINGS)
        _dimension = int(probe.shape[1])
    return _model


def normalize_input(payload: EmbedRequest) -> List[str]:
    raw = payload.input if payload.input is not None else payload.text
    if raw is None:
        raise HTTPException(status_code=400, detail="Request body must include 'input' or 'text'.")
    values = raw if isinstance(raw, list) else [raw]
    if not values:
        raise HTTPException(status_code=400, detail="Input list must not be empty.")
    if len(values) > MAX_BATCH_SIZE:
        raise HTTPException(status_code=400, detail=f"Batch size exceeds limit: {MAX_BATCH_SIZE}.")
    cleaned = [str(item).strip() for item in values]
    if any(not item for item in cleaned):
        raise HTTPException(status_code=400, detail="Input text must not be empty.")
    return cleaned


@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "rag-worker",
        "model": _model_name or DEFAULT_MODEL,
        "modelLoaded": _model is not None,
        "dimension": _dimension,
        "device": DEFAULT_DEVICE or "auto",
        "normalized": NORMALIZE_EMBEDDINGS,
        "allowDownload": ALLOW_DOWNLOAD,
        "maxBatchSize": MAX_BATCH_SIZE,
    }


@app.post("/embed", response_model=EmbedResponse)
def embed(payload: EmbedRequest):
    texts = normalize_input(payload)
    model_name = payload.model or DEFAULT_MODEL
    try:
        model = get_model(model_name)
        vectors = model.encode(texts, normalize_embeddings=NORMALIZE_EMBEDDINGS)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Embedding generation failed: {exc}") from exc

    embeddings = [[float(value) for value in vector] for vector in vectors]
    dimension = len(embeddings[0])
    if len(embeddings) == 1:
        return EmbedResponse(
            embedding=embeddings[0],
            dimension=dimension,
            model=model_name,
            normalized=NORMALIZE_EMBEDDINGS,
        )
    return EmbedResponse(
        data=[{"embedding": embedding, "index": index} for index, embedding in enumerate(embeddings)],
        dimension=dimension,
        model=model_name,
        normalized=NORMALIZE_EMBEDDINGS,
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("RAG_WORKER_PORT", "8091")))
