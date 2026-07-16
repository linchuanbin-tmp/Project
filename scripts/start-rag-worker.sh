#!/bin/bash
# Start RAG embedding worker (BGE-M3)
# Usage: bash scripts/start-rag-worker.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
VENV_DIR="$PROJECT_DIR/rag-worker/.venv"

# Use project .env if available
if [ -f "$PROJECT_DIR/.env" ]; then
  set -a
  source "$PROJECT_DIR/.env"
  set +a
fi

PORT="${RAG_WORKER_PORT:-8091}"
MODEL="${RAG_WORKER_MODEL:-BAAI/bge-m3}"
DEVICE="${RAG_WORKER_DEVICE:-}"
BATCH="${RAG_WORKER_MAX_BATCH_SIZE:-32}"
NORMALIZE="${RAG_WORKER_NORMALIZE:-true}"

# Create venv if needed
if [ ! -d "$VENV_DIR" ]; then
  echo "Creating virtual environment..."
  python3.12 -m venv "$VENV_DIR"
fi

# Activate venv
source "$VENV_DIR/bin/activate"

# Install deps if needed
if ! python -c "import sentence_transformers" 2>/dev/null; then
  echo "Installing dependencies..."
  pip install -q -r "$PROJECT_DIR/rag-worker/requirements.txt"
fi

# Export env vars
export RAG_WORKER_PORT="$PORT"
export RAG_WORKER_MODEL="$MODEL"
export RAG_WORKER_DEVICE="$DEVICE"
export RAG_WORKER_MAX_BATCH_SIZE="$BATCH"
export RAG_WORKER_NORMALIZE="$NORMALIZE"
export HF_DOWNLOAD="${HF_DOWNLOAD:-1}"
export HF_HUB_DISABLE_SYMLINKS_WARNING=1

echo "Starting RAG worker on http://localhost:$PORT"
echo "Model: $MODEL"
echo "Keep this terminal open while using RAG."
echo "First startup may download model files into Hugging Face cache."

python "$PROJECT_DIR/rag-worker/app.py"
