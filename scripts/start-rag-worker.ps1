param(
    [string]$VenvPath = ".\.venv-rag",
    [string]$Model = "BAAI/bge-m3",
    [int]$Port = 8091,
    [string]$Device = ""
)

$ErrorActionPreference = "Stop"

$activate = Join-Path $VenvPath "Scripts\Activate.ps1"
if (-not (Test-Path $activate)) {
    throw "Virtual environment not found: $VenvPath. Run: python -m venv .venv-rag"
}

. $activate

$env:HF_DOWNLOAD = "1"
$env:RAG_WORKER_MODEL = $Model
$env:RAG_WORKER_PORT = "$Port"
$env:RAG_WORKER_NORMALIZE = "true"
if ($Device) {
    $env:RAG_WORKER_DEVICE = $Device
}

Write-Host "Starting RAG worker on http://localhost:$Port" -ForegroundColor Cyan
Write-Host "Model: $Model" -ForegroundColor Cyan
python rag-worker\app.py
