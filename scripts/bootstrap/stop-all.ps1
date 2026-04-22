param(
  [switch]$KeepInfrastructure
)

$ErrorActionPreference = "Stop"

function Write-Step {
  param(
    [string]$Message
  )

  Write-Host ""
  Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-RuntimeStatePath {
  param(
    [string]$WorkspaceRoot
  )

  return Join-Path $WorkspaceRoot ".codex-temp\dev-runtime\start-all-state.json"
}

function Stop-TrackedServices {
  param(
    [string]$StatePath
  )

  if (-not (Test-Path -LiteralPath $StatePath)) {
    Write-Host "No tracked application windows found." -ForegroundColor Yellow
    return
  }

  try {
    $state = Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
  }
  catch {
    Write-Host "Runtime state file is unreadable. Remove it manually if needed: $StatePath" -ForegroundColor Red
    throw
  }

  $stoppedAny = $false

  foreach ($service in @($state.services)) {
    if (-not $service.pid) {
      continue
    }

    try {
      $process = Get-Process -Id ([int]$service.pid) -ErrorAction Stop
      Stop-Process -Id $process.Id -Force -ErrorAction Stop
      Write-Host "Stopped $($service.name) window (PID $($process.Id))." -ForegroundColor Green
      $stoppedAny = $true
    }
    catch {
      Write-Host "$($service.name) window is already closed." -ForegroundColor DarkGray
    }
  }

  Remove-Item -LiteralPath $StatePath -Force -ErrorAction SilentlyContinue

  if (-not $stoppedAny) {
    Write-Host "No tracked application windows were running." -ForegroundColor Yellow
  }
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceRoot = (Resolve-Path (Join-Path $scriptRoot "..\..")).Path
$runtimeStatePath = Get-RuntimeStatePath -WorkspaceRoot $workspaceRoot

Push-Location $workspaceRoot
try {
  Write-Step "Stopping tracked application windows"
  Stop-TrackedServices -StatePath $runtimeStatePath

  if (-not $KeepInfrastructure) {
    Write-Step "Stopping local infrastructure"
    & (Join-Path $scriptRoot "dev-down.ps1")
  }
  else {
    Write-Host ""
    Write-Host "Infrastructure left running because -KeepInfrastructure was used." -ForegroundColor Yellow
  }

  Write-Host ""
  Write-Host "BrainWeb3 stop sequence finished." -ForegroundColor Green
}
finally {
  Pop-Location
}
