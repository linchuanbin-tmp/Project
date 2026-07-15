param(
    [string]$VenvPath = ".\.venv-rag",
    [string]$Model = "BAAI/bge-m3",
    [int]$Port = 8091,
    [string]$Device = "",
    [switch]$ShowSymlinkWarning
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
if (-not $ShowSymlinkWarning) {
    $env:HF_HUB_DISABLE_SYMLINKS_WARNING = "1"
}
if ($Device) {
    $env:RAG_WORKER_DEVICE = $Device
}

Write-Host "Starting RAG worker on http://localhost:$Port" -ForegroundColor Cyan
Write-Host "Model: $Model" -ForegroundColor Cyan
Write-Host "Keep this terminal open while using RAG." -ForegroundColor Yellow
Write-Host "First startup may download model files into the Hugging Face cache." -ForegroundColor Yellow
Write-Host "HF_TOKEN is optional; set it only if Hugging Face rate limits or slows downloads." -ForegroundColor Yellow
Write-Host "Windows symlink cache warnings are harmless and are hidden by default in this script." -ForegroundColor Yellow
python rag-worker\app.py
