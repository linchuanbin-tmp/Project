param(
    [string]$ApiKey = $env:RAG_EMBEDDING_API_KEY,
    [string]$Model = "text-embedding-v4",
    [int]$ExpectedDimension = 1024,
    [string]$Endpoint = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding",
    [string]$Text = "This is a Qwen embedding verification test."
)

$ErrorActionPreference = "Stop"

if (-not $ApiKey) {
    throw "Missing API key. Set `$env:RAG_EMBEDDING_API_KEY or pass -ApiKey."
}

$body = @{
    model = $Model
    input = @{
        texts = @($Text)
    }
    parameters = @{
        dimension = $ExpectedDimension
    }
} | ConvertTo-Json -Depth 5

$response = Invoke-RestMethod `
    -Uri $Endpoint `
    -Method Post `
    -ContentType "application/json" `
    -Headers @{
        Authorization = "Bearer $ApiKey"
    } `
    -Body $body

$embedding = $response.output.embeddings[0].embedding
$actualDimension = @($embedding).Count

[PSCustomObject]@{
    model = $Model
    expectedDimension = $ExpectedDimension
    actualDimension = $actualDimension
    requestId = $response.request_id
} | Format-List

if ($actualDimension -ne $ExpectedDimension) {
    throw "Embedding dimension mismatch: expected $ExpectedDimension, got $actualDimension."
}

Write-Host ""
Write-Host "Qwen embedding verification passed." -ForegroundColor Green
