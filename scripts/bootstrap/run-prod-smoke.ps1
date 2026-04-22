param(
  [string]$BaseUrl = "http://localhost:8080",
  [Parameter(Mandatory = $true)][string]$ActorId,
  [Parameter(Mandatory = $true)][string]$Password,
  [switch]$SkipPrivilegedChecks,
  [string]$ReportPath
)

$ErrorActionPreference = "Stop"

function Invoke-JsonRequest {
  param(
    [string]$Method,
    [string]$Url,
    [object]$Body,
    [hashtable]$Headers
  )

  $request = @{
    Method = $Method
    Uri = $Url
    Headers = $Headers
    ContentType = "application/json"
  }

  if ($null -ne $Body) {
    $request.Body = ($Body | ConvertTo-Json -Depth 10)
  }

  return Invoke-RestMethod @request
}

function Add-SmokeResult {
  param(
    [System.Collections.Generic.List[object]]$Results,
    [string]$Name,
    [string]$Status,
    [string]$Detail
  )

  $Results.Add([PSCustomObject]@{
      name = $Name
      status = $Status
      detail = $Detail
    }) | Out-Null
}

function Invoke-SmokeStep {
  param(
    [System.Collections.Generic.List[object]]$Results,
    [string]$Name,
    [scriptblock]$Action
  )

  try {
    $result = & $Action
    $detail = if ($null -eq $result -or [string]::IsNullOrWhiteSpace("$result")) { "ok" } else { "$result" }
    Add-SmokeResult -Results $Results -Name $Name -Status "passed" -Detail $detail
    return $true
  } catch {
    $detail = $_.Exception.Message
    Add-SmokeResult -Results $Results -Name $Name -Status "failed" -Detail $detail
    return $false
  }
}

$normalizedBaseUrl = $BaseUrl.TrimEnd("/")
$results = [System.Collections.Generic.List[object]]::new()

$statusResponse = $null
$session = $null
$token = $null
$actorRole = $null
$canContinue = $true

$canContinue = (Invoke-SmokeStep -Results $results -Name "system-status" -Action {
    $script:statusResponse = Invoke-RestMethod -Method Get -Uri "$normalizedBaseUrl/api/v1/system/status"
    "stage=$($script:statusResponse.stage); chain=$($script:statusResponse.chain.provider)/$($script:statusResponse.chain.mode)"
  }) -and $canContinue

$canContinue = (Invoke-SmokeStep -Results $results -Name "auth-login" -Action {
    $login = Invoke-JsonRequest -Method Post -Url "$normalizedBaseUrl/api/v1/auth/login" -Body @{
      actorId = $ActorId
      password = $Password
    } -Headers @{}

    $script:session = $login
    $script:token = $login.token
    if (-not $script:token) {
      throw "Login succeeded but token is empty."
    }

    "actor=$($login.actor.actorId); role=$($login.actor.actorRole)"
  }) -and $canContinue

if ($canContinue) {
  $authHeaders = @{ Authorization = "Bearer $token" }

  $canContinue = (Invoke-SmokeStep -Results $results -Name "auth-session" -Action {
      $sessionActor = Invoke-RestMethod -Method Get -Uri "$normalizedBaseUrl/api/v1/auth/session" -Headers $authHeaders
      $script:actorRole = $sessionActor.actorRole
      "actor=$($sessionActor.actorId); role=$($sessionActor.actorRole)"
    }) -and $canContinue

  $canContinue = (Invoke-SmokeStep -Results $results -Name "accounts-me" -Action {
      $me = Invoke-RestMethod -Method Get -Uri "$normalizedBaseUrl/api/v1/accounts/me" -Headers $authHeaders
      "actor=$($me.actorId); org=$($me.actorOrg)"
    }) -and $canContinue

  $canContinue = (Invoke-SmokeStep -Results $results -Name "identity-me" -Action {
      $identity = Invoke-RestMethod -Method Get -Uri "$normalizedBaseUrl/api/v1/identity/me" -Headers $authHeaders
      "actorDid=$($identity.actorDid); credentialStatus=$($identity.statusSnapshot.status)"
    }) -and $canContinue

  $canContinue = (Invoke-SmokeStep -Results $results -Name "datasets-list" -Action {
      $datasets = Invoke-RestMethod -Method Get -Uri "$normalizedBaseUrl/api/v1/datasets" -Headers $authHeaders
      "count=$($datasets.Count)"
    }) -and $canContinue

  if (-not $SkipPrivilegedChecks -and $actorRole -and @("admin", "owner", "approver") -contains $actorRole.ToLowerInvariant()) {
    Invoke-SmokeStep -Results $results -Name "audits-list" -Action {
      $audits = Invoke-RestMethod -Method Get -Uri "$normalizedBaseUrl/api/v1/audits" -Headers $authHeaders
      "count=$($audits.Count)"
    } | Out-Null

    Invoke-SmokeStep -Results $results -Name "training-jobs-list" -Action {
      $jobs = Invoke-RestMethod -Method Get -Uri "$normalizedBaseUrl/api/v1/training-jobs" -Headers $authHeaders
      "count=$($jobs.Count)"
    } | Out-Null

    Invoke-SmokeStep -Results $results -Name "model-records-list" -Action {
      $models = Invoke-RestMethod -Method Get -Uri "$normalizedBaseUrl/api/v1/model-records" -Headers $authHeaders
      "count=$($models.Count)"
    } | Out-Null

    Invoke-SmokeStep -Results $results -Name "chain-records-list" -Action {
      $records = Invoke-RestMethod -Method Get -Uri "$normalizedBaseUrl/api/v1/chain-records" -Headers $authHeaders
      "count=$($records.Count)"
    } | Out-Null
  }
}

$summary = [PSCustomObject]@{
  baseUrl = $normalizedBaseUrl
  actorId = $ActorId
  actorRole = $actorRole
  stage = if ($statusResponse) { $statusResponse.stage } else { "" }
  generatedAt = (Get-Date).ToString("s")
  passed = @($results | Where-Object { $_.status -eq "passed" }).Count
  failed = @($results | Where-Object { $_.status -eq "failed" }).Count
  checks = $results
}

Write-Host ""
Write-Host "BrainWeb3 formal smoke report" -ForegroundColor Cyan
Write-Host "Base URL : $normalizedBaseUrl"
Write-Host "Actor ID : $ActorId"
if ($summary.actorRole) {
  Write-Host "Role     : $($summary.actorRole)"
}
Write-Host "Stage    : $($summary.stage)"
Write-Host ""

foreach ($item in $results) {
  $color = if ($item.status -eq "passed") { "Green" } else { "Red" }
  Write-Host "[$($item.status)] $($item.name) - $($item.detail)" -ForegroundColor $color
}

if ($ReportPath) {
  $resolvedReportPath = if ([System.IO.Path]::IsPathRooted($ReportPath)) { $ReportPath } else { Join-Path (Get-Location) $ReportPath }
  $summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $resolvedReportPath -Encoding UTF8
  Write-Host ""
  Write-Host "Smoke report written to $resolvedReportPath" -ForegroundColor Green
}

Write-Host ""
if ($summary.failed -gt 0) {
  Write-Host "Formal smoke test failed." -ForegroundColor Red
  exit 1
}

Write-Host "Formal smoke test passed." -ForegroundColor Green
