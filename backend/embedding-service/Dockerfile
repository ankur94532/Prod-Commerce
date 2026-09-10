# syntax=docker/dockerfile:1.7
# The model is baked in, at a pinned revision.
#
# The Kubernetes manifest used to pass only EMBEDDING_MODEL_NAME with no model on disk and
# readOnlyRootFilesystem: true, so the pod would try to download BAAI/bge-small-en-v1.5 from
# Hugging Face on every start and fail writing the cache into a read-only home. Even with a
# writable cache it would be wrong: an unpinned download means the weights -- and therefore
# every embedding, and therefore every search result -- can change without a deploy.
#
# So the weights are part of the image, fetched once at build time at the revision below.
# That is also what makes the evaluation reproducible: the sha256 recorded in
# docs/evidence/search-collapse-and-embeddings-2026-09-09.json is the sha256 of the
# model.safetensors this revision produces.
FROM python:3.11-slim AS model

# BAAI/bge-small-en-v1.5. Pinned to a commit, not a tag or a branch: "main" is a moving
# target and would make two builds of the same source produce different search behaviour.
ARG EMBEDDING_MODEL_NAME=BAAI/bge-small-en-v1.5
ARG EMBEDDING_MODEL_REVISION=5c38ec7c405ec4b44b94cc5a9bb96e735b38267a
ARG EMBEDDING_MODEL_SHA256=3c9f31665447c8911517620762200d2245a2518d6e7208acc78cd9db317e21ad

RUN pip install --no-cache-dir "huggingface_hub==0.26.2"
RUN python - <<'PY'
import os
from huggingface_hub import snapshot_download

# allow_patterns keeps the ONNX and PyTorch-pickle copies out: sentence-transformers reads
# the safetensors weights, and the rest triples the image for nothing.
snapshot_download(
    repo_id=os.environ.get('MODEL_NAME', 'BAAI/bge-small-en-v1.5'),
    revision=os.environ.get('MODEL_REVISION', '5c38ec7c405ec4b44b94cc5a9bb96e735b38267a'),
    local_dir='/model',
    allow_patterns=['*.json', '*.txt', 'model.safetensors', '1_Pooling/*'],
)
PY

# The weights are the one thing here that must not be silently substituted, so the build
# fails rather than shipping something the evaluation evidence does not describe.
RUN echo "${EMBEDDING_MODEL_SHA256}  /model/model.safetensors" | sha256sum -c -

FROM python:3.11-slim AS runtime

# An unprivileged user: this process never needs root.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY --from=model --chown=app:app /model /models/bge-small-en-v1.5
COPY --chown=app:app app.py .

# Load from disk, never from the network. Combined with the pinned revision above, a pod
# starts with no egress to Hugging Face and cannot pick up a different model.
ENV EMBEDDING_MODEL_PATH=/models/bge-small-en-v1.5 \
    EMBEDDING_MODEL_NAME=BAAI/bge-small-en-v1.5 \
    HF_HUB_OFFLINE=1 \
    TRANSFORMERS_OFFLINE=1

USER 10001:10001
EXPOSE 8090
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8090"]
