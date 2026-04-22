param(
  [string]$EnvFile = ".env.production",
  [switch]$AllowExampleValues,
  [switch]$CheckReachability,
  [switch]$SkipSmoke,
  [string]$BaseUrl = "http://localhost:8080",
  [string]$ActorId,
  [string]$Password,
  [string]$OutputDir = "artifacts\\acceptance"
)

$ErrorActionPreference = "Stop"

function Ensure-Directory {
  param([string]$Path)

  if (-not (Test-Path -LiteralPath $Path)) {
    New-Item -ItemType Directory -Path $Path | Out-Null
  }
}

function To-MarkdownBulletList {
  param([object[]]$Items)

  if (-not $Items -or $Items.Count -eq 0) {
    return "- none"
  }

  $lines = @()
  foreach ($item in $Items) {
    $lines += "- $item"
  }
  return ($lines -join [Environment]::NewLine)
}

$workspaceRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$resolvedOutputDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $workspaceRoot $OutputDir }
Ensure-Directory -Path $resolvedOutputDir

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$preflightJsonPath = Join-Path $resolvedOutputDir "preflight-$timestamp.json"
$smokeJsonPath = Join-Path $resolvedOutputDir "smoke-$timestamp.json"
$reportMarkdownPath = Join-Path $resolvedOutputDir "P5-acceptance-$timestamp.md"

$preflightScript = Join-Path $PSScriptRoot "check-prod-env.ps1"
$smokeScript = Join-Path $PSScriptRoot "run-prod-smoke.ps1"

$preflightArgs = @(
  "-ExecutionPolicy", "Bypass",
  "-File", $preflightScript,
  "-EnvFile", $EnvFile,
  "-ReportPath", $preflightJsonPath
)
if ($AllowExampleValues) {
  $preflightArgs += "-AllowExampleValues"
}
if ($CheckReachability) {
  $preflightArgs += "-CheckReachability"
}

& powershell @preflightArgs
$preflight = Get-Content -LiteralPath $preflightJsonPath -Raw | ConvertFrom-Json

$smoke = $null
if (-not $SkipSmoke) {
  if ([string]::IsNullOrWhiteSpace($ActorId) -or [string]::IsNullOrWhiteSpace($Password)) {
    throw "ActorId and Password are required unless -SkipSmoke is used."
  }

  $smokeArgs = @(
    "-ExecutionPolicy", "Bypass",
    "-File", $smokeScript,
    "-BaseUrl", $BaseUrl,
    "-ActorId", $ActorId,
    "-Password", $Password,
    "-ReportPath", $smokeJsonPath
  )
  & powershell @smokeArgs
  $smoke = Get-Content -LiteralPath $smokeJsonPath -Raw | ConvertFrom-Json
}

$overallStatus = "failed"
if ($preflight.passed -and ($SkipSmoke -or ($smoke -and $smoke.failed -eq 0))) {
  $overallStatus = "passed"
}

$generatedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

$markdownLines = [System.Collections.Generic.List[string]]::new()
$markdownLines.Add("# BrainWeb3 P5 Acceptance Report") | Out-Null
$markdownLines.Add("") | Out-Null
$markdownLines.Add(('- Generated at: `"{0}"`' -f $generatedAt)) | Out-Null
$markdownLines.Add(('- Environment file: `"{0}"`' -f $EnvFile)) | Out-Null
$markdownLines.Add(('- Overall status: `"{0}"`' -f $overallStatus)) | Out-Null
$markdownLines.Add("") | Out-Null
$markdownLines.Add("## Preflight") | Out-Null
$markdownLines.Add(('- Passed: `"{0}"`' -f $preflight.passed)) | Out-Null
$markdownLines.Add(('- Success count: `"{0}"`' -f $preflight.successCount)) | Out-Null
$markdownLines.Add(('- Warning count: `"{0}"`' -f $preflight.warningCount)) | Out-Null
$markdownLines.Add(('- Error count: `"{0}"`' -f $preflight.errorCount)) | Out-Null
$markdownLines.Add("") | Out-Null
$markdownLines.Add("### Successes") | Out-Null
$markdownLines.Add((To-MarkdownBulletList -Items $preflight.successes)) | Out-Null
$markdownLines.Add("") | Out-Null
$markdownLines.Add("### Warnings") | Out-Null
$markdownLines.Add((To-MarkdownBulletList -Items $preflight.warnings)) | Out-Null
$markdownLines.Add("") | Out-Null
$markdownLines.Add("### Errors") | Out-Null
$markdownLines.Add((To-MarkdownBulletList -Items $preflight.errors)) | Out-Null
$markdownLines.Add("") | Out-Null
$markdownLines.Add("## Smoke") | Out-Null

if ($SkipSmoke) {
  $markdownLines.Add("- Smoke was skipped with `-SkipSmoke`.") | Out-Null
} else {
  $markdownLines.Add(('- Base URL: `"{0}"`' -f $BaseUrl)) | Out-Null
  $markdownLines.Add(('- Actor ID: `"{0}"`' -f $ActorId)) | Out-Null
  $markdownLines.Add(('- Passed checks: `"{0}"`' -f $smoke.passed)) | Out-Null
  $markdownLines.Add(('- Failed checks: `"{0}"`' -f $smoke.failed)) | Out-Null
  foreach ($item in $smoke.checks) {
    $markdownLines.Add("- [$($item.status)] $($item.name): $($item.detail)") | Out-Null
  }
}

$markdownLines.Add("") | Out-Null
$markdownLines.Add("## Attachments") | Out-Null
$markdownLines.Add(('- Preflight JSON: `"{0}"`' -f $preflightJsonPath)) | Out-Null
if ($SkipSmoke) {
  $markdownLines.Add("- Smoke JSON: not generated") | Out-Null
} else {
  $markdownLines.Add(('- Smoke JSON: `"{0}"`' -f $smokeJsonPath)) | Out-Null
}

$markdown = $markdownLines -join [Environment]::NewLine
Set-Content -LiteralPath $reportMarkdownPath -Value $markdown -Encoding UTF8

Write-Host ""
Write-Host "Acceptance report written to $reportMarkdownPath" -ForegroundColor Green
