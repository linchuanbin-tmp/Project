FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    RAG_WORKER_PORT=8091 \
    RAG_WORKER_MODEL=BAAI/bge-m3 \
    RAG_WORKER_NORMALIZE=true \
    HF_DOWNLOAD=1

WORKDIR /app

COPY rag-worker/requirements.txt ./requirements.txt
RUN pip install --upgrade pip && pip install -r requirements.txt

COPY rag-worker/app.py ./app.py

EXPOSE 8091

CMD ["python", "app.py"]
