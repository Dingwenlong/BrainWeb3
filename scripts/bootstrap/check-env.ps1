param(
  [switch]$AllowPortableMaven
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceRoot = (Resolve-Path (Join-Path $scriptRoot "..\..")).Path

. (Join-Path $scriptRoot "_tooling.ps1")

Write-Host "Checking node ..." -ForegroundColor Cyan
node -v | Out-Host

Write-Host "Checking npm ..." -ForegroundColor Cyan
npm -v | Out-Host

Write-Host "Checking java ..." -ForegroundColor Cyan
java -version | Out-Host

$mavenCommand = Get-MavenCommand -WorkspaceRoot $workspaceRoot
if (-not $mavenCommand) {
  if ($AllowPortableMaven) {
    throw "Maven is not available yet. Run .\scripts\bootstrap\install-host-tools.ps1 or .\start-project.cmd first."
  }

  throw "Maven is not available in PATH."
}

Write-Host "Checking maven ..." -ForegroundColor Cyan
& $mavenCommand -v | Out-Host

Write-Host "Checking python ..." -ForegroundColor Cyan
python --version | Out-Host

Write-Host "Checking docker ..." -ForegroundColor Cyan
docker --version | Out-Host

Write-Host ""
Write-Host "Environment check completed." -ForegroundColor Green
