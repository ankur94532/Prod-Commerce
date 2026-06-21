import os
from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer


MODEL_PATH = os.getenv("EMBEDDING_MODEL_PATH", "/models/bge-small-en-v1.5")
MODEL_NAME = os.getenv("EMBEDDING_MODEL_NAME", "BAAI/bge-small-en-v1.5")

app = FastAPI(title="Prod-Commerce Embedding Service")
model: SentenceTransformer | None = None


class EmbeddingRequest(BaseModel):
    texts: list[str] = Field(default_factory=list)


class EmbeddingResponse(BaseModel):
    embeddings: list[list[float]]
    dimensions: int
    model: str
    usage: dict[str, Any]


@app.on_event("startup")
def load_model() -> None:
    global model
    model = SentenceTransformer(MODEL_PATH)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/api/v1/embeddings")
def create_embeddings(request: EmbeddingRequest) -> EmbeddingResponse:
    if model is None:
        raise RuntimeError("Embedding model is not loaded")

    texts = [text or "" for text in request.texts]
    vectors = model.encode(
        texts,
        normalize_embeddings=True,
        convert_to_numpy=True,
        show_progress_bar=False,
    )

    embeddings = vectors.astype("float32").tolist()
    dimensions = len(embeddings[0]) if embeddings else 0
    return EmbeddingResponse(
        embeddings=embeddings,
        dimensions=dimensions,
        model=MODEL_NAME,
        usage={"texts": len(texts)},
    )
