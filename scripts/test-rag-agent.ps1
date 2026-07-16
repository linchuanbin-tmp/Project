param(
    [string]$RagBaseUrl = "http://localhost:8085",
    [string]$Question = "loan limits and credit authorization metrics",
    [int]$TopK = 5,
    [switch]$SkipRebuild,
    [switch]$SubmitAccessRequest
)

$ErrorActionPreference = "Stop"
$failures = New-Object System.Collections.Generic.List[string]

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "== $Message ==" -ForegroundColor Cyan
}

function Write-Pass {
    param([string]$Message)
    Write-Host "[PASS] $Message" -ForegroundColor Green
}

function Write-WarnLine {
    param([string]$Message)
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Write-Fail {
    param([string]$Message)
    $failures.Add($Message) | Out-Null
    Write-Host "[FAIL] $Message" -ForegroundColor Red
}

function Invoke-RagGet {
    param([string]$Path)
    Invoke-RestMethod -Uri "$RagBaseUrl$Path" -Method Get
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
        -Body ($Body | ConvertTo-Json -Depth 8)
}

function Assert-Condition {
    param(
        [bool]$Condition,
        [string]$SuccessMessage,
        [string]$FailureMessage
    )

    if ($Condition) {
        Write-Pass $SuccessMessage
    } else {
        Write-Fail $FailureMessage
    }
}

function Wait-RagIndexTask {
    param(
        [long]$TaskId,
        [int]$TimeoutSeconds = 600
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $task = Invoke-RagGet "/rag/index/tasks/$TaskId"
        if ($task.status -in @("SUCCESS", "FAIL")) {
            return $task
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for RAG index task $TaskId"
}

function Test-NoPermissionLeak {
    param(
        [object]$Response,
        [object]$DeptId,
        [int]$ClearanceLevel
    )
    foreach ($chunk in @($Response.chunks)) {
        $chunkDept = if ($null -ne $chunk.deptId) { [int]$chunk.deptId } else { 0 }
        $chunkLevel = if ($null -ne $chunk.securityLevel) { [int]$chunk.securityLevel } else { 1 }
        $global = $chunkDept -eq 0
        $sameDept = $null -ne $DeptId -and $chunkDept -eq [int]$DeptId
        if (($global -or $sameDept) -and $chunkLevel -le $ClearanceLevel) {
            continue
        }
        return $false
    }
    return $true
}

Write-Host "RAG Agent verification"
Write-Host "Base URL: $RagBaseUrl"
Write-Host "Question: $Question"
Write-Host "TopK: $TopK"

Write-Step "1. Health check"
try {
    $health = Invoke-RagGet "/rag/health"
    $health | Format-List
    Assert-Condition ($health.status -eq "UP") "rag-agent health is UP." "rag-agent health is not UP."
    Assert-Condition ($health.embeddingProvider -in @("mock", "http")) "Embedding provider is recognized: $($health.embeddingProvider)." "Embedding provider is unsupported: $($health.embeddingProvider)."
    Assert-Condition ($health.embeddingDim -gt 0) "Embedding dimension is configured: $($health.embeddingDim)." "Embedding dimension is not configured."
    if ($health.embeddingProvider -eq "http") {
        Assert-Condition ($health.embeddingEndpointConfigured -eq $true) "HTTP embedding endpoint is configured." "HTTP embedding provider requires RAG_EMBEDDING_ENDPOINT."
    } else {
        Write-WarnLine "Embedding provider is mock; retrieval flow is verified, semantic quality is not final."
    }
    Assert-Condition ($health.llmProvider -in @("mock", "http")) "LLM provider is recognized: $($health.llmProvider)." "LLM provider is unsupported: $($health.llmProvider)."
    if ($health.llmProvider -eq "http") {
        Assert-Condition ($health.llmBaseUrlConfigured -eq $true) "HTTP LLM base URL is configured." "HTTP LLM provider requires RAG_LLM_BASE_URL."
        Assert-Condition ([string]::IsNullOrWhiteSpace($health.llmModel) -eq $false) "HTTP LLM model is configured: $($health.llmModel)." "HTTP LLM provider requires RAG_LLM_MODEL."
    } else {
        Write-WarnLine "LLM provider is mock; generation flow is verified, answer quality is not final."
    }
} catch {
    Write-Fail "Health check failed: $($_.Exception.Message)"
}

if (-not $SkipRebuild) {
    Write-Step "2. Rebuild all indexes"
    try {
        $rebuild = Invoke-RagPost -Path "/rag/index/rebuild" -Body @{} -Username "admin" -Roles "ROLE_ADMIN"
        $rebuild | Format-List
        $finalTask = if ($rebuild.taskId) { Wait-RagIndexTask -TaskId $rebuild.taskId } else { $rebuild }
        $finalTask | Format-List
        Assert-Condition ($finalTask.status -eq "SUCCESS") "Index rebuild task completed successfully." "Index rebuild did not succeed."
    } catch {
        Write-Fail "Index rebuild failed: $($_.Exception.Message)"
    }
} else {
    Write-Step "2. Rebuild skipped"
    Write-WarnLine "Using existing RAG indexes."
}

Write-Step "3. Document index status"
$indexedDocumentId = $null
try {
    $statuses = Invoke-RagGet "/rag/index/documents/status"
    $indexed = @($statuses | Where-Object { $_.indexed -eq $true })
    $statuses | Select-Object documentId,title,indexed,chunkCount,lastIndexedAt | Format-Table -AutoSize
    Assert-Condition ($indexed.Count -gt 0) "At least one document is indexed." "No indexed documents found."
    if ($indexed.Count -gt 0) {
        $indexedDocumentId = $indexed[0].documentId
    }
} catch {
    Write-Fail "Document index status check failed: $($_.Exception.Message)"
}

Write-Step "4. Chunk inspection"
if ($indexedDocumentId) {
    try {
        $chunks = @(Invoke-RagGet "/rag/index/document/$indexedDocumentId/chunks")
        $chunks | Select-Object chunkId,documentId,chunkIndex,tokenCount,securityLevel,vectorId | Format-Table -AutoSize
        Assert-Condition ($chunks.Count -gt 0) "Document $indexedDocumentId has inspectable chunks." "Document $indexedDocumentId returned no chunks."
    } catch {
        Write-Fail "Chunk inspection failed: $($_.Exception.Message)"
    }
} else {
    Write-WarnLine "Skipped chunk inspection because no indexed document was found."
}

Write-Step "5. Permission-aware queries"
$users = @(
    @{ username = "admin"; roles = "ROLE_ADMIN"; deptId = $null; clearance = 3; checkLeak = $false },
    @{ username = "credit_mgr"; roles = "ROLE_DEPT_ADMIN"; deptId = 1; clearance = 2; checkLeak = $true },
    @{ username = "credit_staff"; roles = "ROLE_USER"; deptId = 1; clearance = 1; checkLeak = $true },
    @{ username = "compliance_staff"; roles = "ROLE_USER"; deptId = 2; clearance = 1; checkLeak = $true }
)

$lastCreditStaffResponse = $null
foreach ($user in $users) {
    try {
        Write-Host ""
        Write-Host "-- Query as $($user.username) --" -ForegroundColor DarkCyan
        $response = Invoke-RagPost `
            -Path "/rag/query" `
            -Body @{ question = $Question; topK = $TopK } `
            -Username $user.username `
            -Roles $user.roles

        [PSCustomObject]@{
            user = $user.username
            status = $response.status
            message = $response.message
            retrievedDocumentIds = ($response.retrievedDocumentIds -join ",")
            blockedDocumentIds = ($response.blockedDocumentIds -join ",")
            latencyMs = $response.latencyMs
        } | Format-List

        Assert-Condition ($response.status -in @("SUCCESS", "NO_CONTEXT", "LLM_FALLBACK")) "$($user.username) query returned a controlled RAG status." "$($user.username) query failed unexpectedly."
        if ($user.checkLeak) {
            Assert-Condition (Test-NoPermissionLeak -Response $response -DeptId $user.deptId -ClearanceLevel $user.clearance) "$($user.username) retrieved only permission-safe chunks." "$($user.username) retrieved chunks outside department/clearance scope."
        }
        if ($user.username -eq "credit_staff") {
            $lastCreditStaffResponse = $response
        }
    } catch {
        Write-Fail "$($user.username) query failed: $($_.Exception.Message)"
    }
}

if ($SubmitAccessRequest) {
    Write-Step "6. Optional access request submission"
    $blockedId = $null
    if ($lastCreditStaffResponse -and @($lastCreditStaffResponse.blockedDocumentIds).Count -gt 0) {
        $blockedId = @($lastCreditStaffResponse.blockedDocumentIds)[0]
    } else {
        try {
            $statuses = @(Invoke-RagGet "/rag/index/documents/status")
            $blockedId = @($statuses | Where-Object {
                ($_.deptId -eq 1 -and $_.securityLevel -gt 1) -or ($_.deptId -ne $null -and $_.deptId -ne 1)
            } | Select-Object -First 1).documentId
        } catch {
            Write-WarnLine "Could not infer an inaccessible document for access request: $($_.Exception.Message)"
        }
    }

    if ($blockedId) {
        try {
            $access = Invoke-RagPost `
                -Path "/rag/access-request" `
                -Body @{ documentId = $blockedId; reason = "Verification script requires temporary RAG access for permission workflow testing." } `
                -Username "credit_staff" `
                -Roles "ROLE_USER"
            $access | Format-List
            Assert-Condition ($access.status -in @("PENDING", "ALREADY_ACCESSIBLE")) "Access request endpoint returned an expected status." "Access request endpoint returned an unexpected status."
        } catch {
            Write-Fail "Access request submission failed: $($_.Exception.Message)"
        }
    } else {
        Write-WarnLine "Skipped access request because credit_staff had no blocked documents."
    }
} else {
    Write-Step "6. Optional access request skipped"
    Write-WarnLine "Run with -SubmitAccessRequest to create a RAG_APPLY notification."
}

Write-Step "Summary"
if ($failures.Count -eq 0) {
    Write-Host "All RAG verification checks passed." -ForegroundColor Green
    exit 0
}

Write-Host "$($failures.Count) check(s) failed:" -ForegroundColor Red
foreach ($failure in $failures) {
    Write-Host "- $failure" -ForegroundColor Red
}
exit 1
