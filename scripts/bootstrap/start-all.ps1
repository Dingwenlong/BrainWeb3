param(
  [switch]$InstallDeps,
  [switch]$IncludeFederatedService
)

$ErrorActionPreference = "Stop"

function Ensure-FileFromTemplate {
  param(
    [string]$TargetPath,
    [string]$TemplatePath
  )

  if (Test-Path -LiteralPath $TargetPath) {
    return
  }

  Copy-Item -LiteralPath $TemplatePath -Destination $TargetPath
  Write-Host "Created $TargetPath from template." -ForegroundColor Yellow
}

function Ensure-NodeDependencies {
  param(
    [string]$WorkspaceRoot,
    [switch]$ForceInstall
  )

  $nodeModulesPath = Join-Path $WorkspaceRoot "node_modules"
  if ((Test-Path -LiteralPath $nodeModulesPath) -and -not $ForceInstall) {
    return
  }

  Write-Step "Installing Node dependencies"
  Push-Location $WorkspaceRoot
  try {
    npm install
  }
  finally {
    Pop-Location
  }
}

function Get-RuntimeStatePath {
  param(
    [string]$WorkspaceRoot
  )

  $runtimeDirectory = Join-Path $WorkspaceRoot ".codex-temp\dev-runtime"
  if (-not (Test-Path -LiteralPath $runtimeDirectory)) {
    New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
  }

  return Join-Path $runtimeDirectory "start-all-state.json"
}

function Save-RuntimeState {
  param(
    [string]$StatePath,
    [System.Collections.IEnumerable]$ServiceEntries
  )

  $payload = [ordered]@{
    startedAt = (Get-Date).ToString("o")
    services = @($ServiceEntries)
  }

  $payload | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $StatePath -Encoding utf8
}

function Stop-TrackedServices {
  param(
    [string]$StatePath
  )

  if (-not (Test-Path -LiteralPath $StatePath)) {
    return
  }

  try {
    $state = Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
  }
  catch {
    Write-Host "Existing runtime state could not be read. It will be replaced." -ForegroundColor Yellow
    Remove-Item -LiteralPath $StatePath -Force -ErrorAction SilentlyContinue
    return
  }

  foreach ($service in @($state.services)) {
    if (-not $service.pid) {
      continue
    }

    try {
      $process = Get-Process -Id ([int]$service.pid) -ErrorAction Stop
      Stop-Process -Id $process.Id -Force -ErrorAction Stop
      Write-Host "Stopped previous $($service.name) window (PID $($process.Id))." -ForegroundColor Yellow
    }
    catch {
      continue
    }
  }

  Remove-Item -LiteralPath $StatePath -Force -ErrorAction SilentlyContinue
}

function Ensure-PythonRequirements {
  param(
    [string]$RequirementsPath,
    [string[]]$Imports,
    [switch]$ForceInstall
  )

  $needsInstall = $ForceInstall

  if (-not $needsInstall) {
    $importClause = ($Imports | ForEach-Object { "import $_" }) -join "; "
    $checkScript = "$importClause; print('ready')"
    $null = & python -c $checkScript 2>$null
    if ($LASTEXITCODE -ne 0) {
      $needsInstall = $true
    }
  }

  if (-not $needsInstall) {
    return
  }

  Write-Step "Installing Python dependencies from $RequirementsPath"
  python -m pip install -r $RequirementsPath
}

function Open-ServiceWindow {
  param(
    [string]$Name,
    [string]$WorkingDirectory,
    [string]$Command
  )

  $hostPath = (Get-Process -Id $PID).Path
  $escapedWorkingDirectory = $WorkingDirectory.Replace("'", "''")
  $escapedCommand = $Command.Replace("'", "''")
  $escapedTitle = "BrainWeb3 - $Name".Replace("'", "''")
  $windowCommand = "`$Host.UI.RawUI.WindowTitle = '$escapedTitle'; Set-Location -LiteralPath '$escapedWorkingDirectory'; $escapedCommand"

  $process = Start-Process -FilePath $hostPath -WorkingDirectory $WorkingDirectory -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy",
    "Bypass",
    "-Command",
    $windowCommand
  ) -PassThru

  Write-Host "Started $Name in a new window (PID $($process.Id))." -ForegroundColor Green
  return [ordered]@{
    name = $Name
    pid = $process.Id
    workingDirectory = $WorkingDirectory
    command = $Command
  }
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceRoot = (Resolve-Path (Join-Path $scriptRoot "..\..")).Path
$runtimeStatePath = Get-RuntimeStatePath -WorkspaceRoot $workspaceRoot

. (Join-Path $scriptRoot "_tooling.ps1")

Write-Step "Preparing workspace"
Push-Location $workspaceRoot
try {
  Ensure-FileFromTemplate -TargetPath (Join-Path $workspaceRoot ".env") -TemplatePath (Join-Path $workspaceRoot ".env.example")
  Stop-TrackedServices -StatePath $runtimeStatePath

  Write-Step "Checking local toolchain"
  & (Join-Path $scriptRoot "check-env.ps1") -AllowPortableMaven

  $mavenCommand = Get-MavenCommand -WorkspaceRoot $workspaceRoot
  if (-not $mavenCommand) {
    throw "Maven is unavailable. Run .\scripts\bootstrap\install-host-tools.ps1 or .\start-project.cmd first."
  }

  $escapedMavenCommand = $mavenCommand.Replace("'", "''")

  Write-Step "Starting local infrastructure"
  & (Join-Path $scriptRoot "dev-up.ps1")

  Ensure-NodeDependencies -WorkspaceRoot $workspaceRoot -ForceInstall:$InstallDeps
  Ensure-PythonRequirements -RequirementsPath (Join-Path $workspaceRoot "services\eeg-service\requirements.txt") -Imports @("flask", "numpy", "mne") -ForceInstall:$InstallDeps

  if ($IncludeFederatedService) {
    Ensure-PythonRequirements -RequirementsPath (Join-Path $workspaceRoot "services\federated-service\requirements.txt") -Imports @("flask") -ForceInstall:$InstallDeps
  }

  Write-Step "Launching application services"
  $serviceEntries = @()
  $serviceEntries += Open-ServiceWindow -Name "frontend" -WorkingDirectory $workspaceRoot -Command "npm run dev:frontend"
  $serviceEntries += Open-ServiceWindow -Name "backend" -WorkingDirectory $workspaceRoot -Command "& '$escapedMavenCommand' -pl apps/backend spring-boot:run"
  $serviceEntries += Open-ServiceWindow -Name "eeg-service" -WorkingDirectory (Join-Path $workspaceRoot "services\eeg-service") -Command "python .\app.py"

  if ($IncludeFederatedService) {
    $serviceEntries += Open-ServiceWindow -Name "federated-service" -WorkingDirectory (Join-Path $workspaceRoot "services\federated-service") -Command "python .\app.py"
  }

  Save-RuntimeState -StatePath $runtimeStatePath -ServiceEntries $serviceEntries

  Write-Host ""
  Write-Host "BrainWeb3 launch sequence started." -ForegroundColor Green
  Write-Host "Frontend        : http://localhost:5173"
  Write-Host "Backend         : http://localhost:8080"
  Write-Host "EEG Service     : http://localhost:8101"
  if ($IncludeFederatedService) {
    Write-Host "Federated Svcs  : http://localhost:8102"
  }
  Write-Host ""
  Write-Host "Tip: run with -InstallDeps on first boot, and add -IncludeFederatedService if you want the federated placeholder too." -ForegroundColor DarkGray
}
finally {
  Pop-Location
}
