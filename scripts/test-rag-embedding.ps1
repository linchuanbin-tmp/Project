param(
    [string]$WorkerBaseUrl = "http://localhost:8091",
    [string]$RagBaseUrl = "http://localhost:8085",
    [string]$Model = "BAAI/bge-m3",
    [int]$ExpectedDimension = 1024,
    [switch]$SkipRagHealth
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "== $Message ==" -ForegroundColor Cyan
}

Write-Step "1. Worker health"
$workerHealth = Invoke-RestMethod -Uri "$WorkerBaseUrl/health" -Method Get
$workerHealth | Format-List

Write-Step "2. Worker embedding probe"
$body = @{
    input = "这是一个 RAG embedding 测试"
    model = $Model
} | ConvertTo-Json

$embed = Invoke-RestMethod `
    -Uri "$WorkerBaseUrl/embed" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body

$actualDimension = @($embed.embedding).Count
[PSCustomObject]@{
    model = $embed.model
    expectedDimension = $ExpectedDimension
    actualDimension = $actualDimension
    normalized = $embed.normalized
} | Format-List

if ($actualDimension -ne $ExpectedDimension) {
    throw "Embedding dimension mismatch: expected $ExpectedDimension, got $actualDimension."
}

if (-not $SkipRagHealth) {
    Write-Step "3. RAG embedding readiness"
    $ragHealth = Invoke-RestMethod -Uri "$RagBaseUrl/rag/health/embedding" -Method Get
    $ragHealth | Format-List
    if ($ragHealth.ready -ne $true) {
        throw "RAG embedding readiness is not ready: $($ragHealth.message)"
    }
}

Write-Host ""
Write-Host "Embedding verification passed." -ForegroundColor Green
