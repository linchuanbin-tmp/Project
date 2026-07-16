param(
    [string]$RagBaseUrl = "http://localhost:8085",
    [string]$OutputPath = "docs/RAG_EVALUATION_RESULTS.md",
    [int]$TopK = 3,
    [switch]$SkipRebuild
)

$ErrorActionPreference = "Stop"
$results = New-Object System.Collections.Generic.List[object]
$failures = New-Object System.Collections.Generic.List[string]

function Add-Result {
    param(
        [string]$Area,
        [string]$Case,
        [string]$Expected,
        [string]$Observed,
        [string]$Status
    )
    $results.Add([PSCustomObject]@{
        Area = $Area
        Case = $Case
        Expected = $Expected
        Observed = $Observed
        Status = $Status
    }) | Out-Null
    if ($Status -ne "PASS" -and $Status -ne "INFO") {
        $failures.Add("$Area - ${Case}: $Observed") | Out-Null
    }
}

function Invoke-RagGet {
    param([string]$Path)
    Invoke-RestMethod -Uri "$RagBaseUrl$Path" -Method Get -TimeoutSec 60
}

function Invoke-RagPost {
    param(
        [string]$Path,
        [hashtable]$Body,
        [string]$Username = "admin",
        [string]$Roles = "ROLE_ADMIN"
    )
    Invoke-RestMethod `
        -Uri "$RagBaseUrl$Path" `
        -Method Post `
        -Headers @{
            "Content-Type" = "application/json"
            "X-User-Name" = $Username
            "X-User-Roles" = $Roles
        } `
        -Body ($Body | ConvertTo-Json -Depth 8) `
        -TimeoutSec 240
}

function Test-NoPermissionLeak {
    param(
        [object]$Response,
        [Nullable[int]]$DeptId,
        [int]$ClearanceLevel
    )
    foreach ($chunk in @($Response.chunks)) {
        $chunkDept = if ($null -ne $chunk.deptId) { [int]$chunk.deptId } else { 0 }
        $chunkLevel = if ($null -ne $chunk.securityLevel) { [int]$chunk.securityLevel } else { 1 }
        $global = $chunkDept -eq 0
        $sameDept = $DeptId.HasValue -and $chunkDept -eq $DeptId.Value
        if (($global -or $sameDept) -and $chunkLevel -le $ClearanceLevel) {
            continue
        }
        return $false
    }
    return $true
}

function Test-CitationConsistency {
    param([object]$Response)
    $chunkKeys = @{}
    foreach ($chunk in @($Response.chunks)) {
        $chunkKeys["$($chunk.documentId):$($chunk.chunkId):$($chunk.chunkIndex)"] = $true
    }
    foreach ($citation in @($Response.citations)) {
        $key = "$($citation.documentId):$($citation.chunkId):$($citation.chunkIndex)"
        if (-not $chunkKeys.ContainsKey($key)) {
            return $false
        }
    }
    return $true
}

function Escape-MarkdownCell {
    param([string]$Text)
    if ($null -eq $Text) { return "" }
    return ($Text -replace "\|", "\|" -replace "`r?`n", "<br>")
}

Write-Host "RAG evaluation"
Write-Host "Base URL: $RagBaseUrl"

try {
    $health = Invoke-RagGet "/rag/health"
    Add-Result "Service readiness" "RAG health" "status = UP" "status=$($health.status), profile=$($health.embeddingProfile), collection=$($health.milvusCollection)" ($(if ($health.status -eq "UP") { "PASS" } else { "FAIL" }))
    Add-Result "Service readiness" "Embedding readiness" "embeddingReady = true" "embeddingReady=$($health.embeddingReady), provider=$($health.embeddingProvider), dim=$($health.embeddingDim)" ($(if ($health.embeddingReady -eq $true) { "PASS" } else { "FAIL" }))
} catch {
    Add-Result "Service readiness" "RAG health" "health endpoint reachable" $_.Exception.Message "FAIL"
}

try {
    $active = Invoke-RagGet "/rag/embedding/active"
    Add-Result "Embedding profile" "Active profile" "active profile has isolated collection" "id=$($active.id), model=$($active.model), collection=$($active.collectionName), indexStatus=$($active.indexStatus)" ($(if ($active.collectionName -and $active.indexStatus -eq "READY") { "PASS" } else { "FAIL" }))
} catch {
    Add-Result "Embedding profile" "Active profile" "active profile endpoint reachable" $_.Exception.Message "FAIL"
}

if (-not $SkipRebuild) {
    try {
        $rebuild = Invoke-RagPost -Path "/rag/index/rebuild" -Body @{}
        Add-Result "Index rebuild" "Active profile rebuild" "status = SUCCESS and chunkCount > 0" "status=$($rebuild.status), chunkCount=$($rebuild.chunkCount)" ($(if ($rebuild.status -eq "SUCCESS" -and $rebuild.chunkCount -gt 0) { "PASS" } else { "FAIL" }))
    } catch {
        Add-Result "Index rebuild" "Active profile rebuild" "rebuild endpoint succeeds" $_.Exception.Message "FAIL"
    }
} else {
    Add-Result "Index rebuild" "Active profile rebuild" "optional in repeated runs" "Skipped by -SkipRebuild" "INFO"
}

try {
    $statuses = @(Invoke-RagGet "/rag/index/documents/status")
    $indexed = @($statuses | Where-Object { $_.indexed -eq $true })
    Add-Result "Index status" "Indexed documents" "at least one indexed document" "indexed=$($indexed.Count), total=$($statuses.Count)" ($(if ($indexed.Count -gt 0) { "PASS" } else { "FAIL" }))
} catch {
    Add-Result "Index status" "Indexed documents" "document index endpoint reachable" $_.Exception.Message "FAIL"
}

$queryCases = @(
    @{
        Name = "Admin retrieval"
        Username = "admin"
        Roles = "ROLE_ADMIN"
        DeptId = $null
        Clearance = 3
        Question = "Please summarize the main functions of the BankAgent platform."
    },
    @{
        Name = "Credit staff permission leakage"
        Username = "credit_staff"
        Roles = "ROLE_USER"
        DeptId = 1
        Clearance = 1
        Question = "What are the confidential credit risk loan limits?"
    },
    @{
        Name = "Compliance staff permission leakage"
        Username = "compliance_staff"
        Roles = "ROLE_USER"
        DeptId = 2
        Clearance = 1
        Question = "What are the high risk client investigation guidelines?"
    }
)

$latencies = New-Object System.Collections.Generic.List[int]
foreach ($case in $queryCases) {
    try {
        $response = Invoke-RagPost `
            -Path "/rag/query" `
            -Body @{ question = $case.Question; topK = $TopK } `
            -Username $case.Username `
            -Roles $case.Roles

        if ($response.latencyMs -ge 0) {
            $latencies.Add([int]$response.latencyMs) | Out-Null
        }

        $controlledStatus = $response.status -in @("SUCCESS", "NO_CONTEXT", "LLM_FALLBACK")
        Add-Result "Query behavior" $case.Name "controlled RAG status" "status=$($response.status), retrieved=$(@($response.retrievedDocumentIds).Count), citations=$(@($response.citations).Count), latencyMs=$($response.latencyMs)" ($(if ($controlledStatus) { "PASS" } else { "FAIL" }))

        $citationOk = Test-CitationConsistency $response
        Add-Result "Citation correctness" "$($case.Name) citations" "every citation references a returned chunk" "citations=$(@($response.citations).Count), chunks=$(@($response.chunks).Count)" ($(if ($citationOk) { "PASS" } else { "FAIL" }))

        if ($case.Username -ne "admin") {
            $leakFree = Test-NoPermissionLeak -Response $response -DeptId $case.DeptId -ClearanceLevel $case.Clearance
            Add-Result "Permission leakage" $case.Name "no retrieved chunk exceeds department/clearance scope" "retrievedDocumentIds=$($response.retrievedDocumentIds -join ',')" ($(if ($leakFree) { "PASS" } else { "FAIL" }))
        }
    } catch {
        Add-Result "Query behavior" $case.Name "query endpoint succeeds" $_.Exception.Message "FAIL"
    }
}

if ($latencies.Count -gt 0) {
    $avgLatency = [Math]::Round(($latencies | Measure-Object -Average).Average, 2)
    $maxLatency = ($latencies | Measure-Object -Maximum).Maximum
    Add-Result "Latency" "Observed query latency" "record average and max latency" "avg=${avgLatency}ms, max=${maxLatency}ms, samples=$($latencies.Count)" "INFO"
}

$markdown = New-Object System.Collections.Generic.List[string]
$markdown.Add("# RAG Evaluation Results") | Out-Null
$markdown.Add("") | Out-Null
$markdown.Add("Generated by `scripts/test-rag-evaluation.ps1`.") | Out-Null
$markdown.Add("") | Out-Null
$markdown.Add("| Area | Case | Expected | Observed | Status |") | Out-Null
$markdown.Add("| --- | --- | --- | --- | --- |") | Out-Null
foreach ($row in $results) {
    $markdown.Add("| $(Escape-MarkdownCell $row.Area) | $(Escape-MarkdownCell $row.Case) | $(Escape-MarkdownCell $row.Expected) | $(Escape-MarkdownCell $row.Observed) | $(Escape-MarkdownCell $row.Status) |") | Out-Null
}
$markdown.Add("") | Out-Null
$markdown.Add("Notes: failure-fallback behavior is represented by controlled `LLM_FALLBACK`/`FAIL` statuses and should be tested manually by disabling the relevant external dependency when required.") | Out-Null

$outFile = Resolve-Path -Path "." | ForEach-Object { Join-Path $_ $OutputPath }
$outDir = Split-Path $outFile -Parent
if (-not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir | Out-Null
}
[System.IO.File]::WriteAllLines($outFile, $markdown, [System.Text.UTF8Encoding]::new($false))
Write-Host "Wrote evaluation report: $outFile" -ForegroundColor Cyan

$results | Format-Table -AutoSize
if ($failures.Count -gt 0) {
    Write-Host "$($failures.Count) evaluation check(s) failed." -ForegroundColor Red
    exit 1
}

Write-Host "All required RAG evaluation checks passed." -ForegroundColor Green
