param(
  [switch]$SkipDocker,
  [switch]$ForceMavenDownload
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceRoot = (Resolve-Path (Join-Path $scriptRoot "..\..")).Path

. (Join-Path $scriptRoot "_tooling.ps1")

$packagesToInstall = @()

if (-not (Test-CommandExists -Name "node")) {
  $packagesToInstall += [ordered]@{
    id = "OpenJS.NodeJS.LTS"
    name = "Node.js LTS"
  }
}

if (-not (Test-CommandExists -Name "java")) {
  $packagesToInstall += [ordered]@{
    id = "EclipseAdoptium.Temurin.17.JDK"
    name = "Temurin JDK 17"
  }
}

if (-not (Test-CommandExists -Name "python")) {
  $packagesToInstall += [ordered]@{
    id = "Python.Python.3.11"
    name = "Python 3.11"
  }
}

if ((-not $SkipDocker) -and -not (Test-CommandExists -Name "docker")) {
  $packagesToInstall += [ordered]@{
    id = "Docker.DockerDesktop"
    name = "Docker Desktop"
  }
}

foreach ($package in $packagesToInstall) {
  Install-WingetPackage -Id $package.id -DisplayName $package.name
}

if ($packagesToInstall.Count -gt 0) {
  Write-Step "Refreshing PATH after host tool installation"
  Update-ProcessPath
}

if (-not (Test-CommandReady -Name "npm" -Arguments @("-v"))) {
  throw "npm is still unavailable after installing Node.js. Please reopen your terminal and run the script again."
}

if (-not (Test-CommandReady -Name "java" -Arguments @("-version"))) {
  throw "Java 17 is still unavailable after automatic installation."
}

if (-not (Test-CommandReady -Name "python" -Arguments @("--version"))) {
  throw "Python 3.11 is still unavailable after automatic installation."
}

Write-Step "Preparing portable Maven in workspace"
$mavenCommand = Ensure-PortableMaven -WorkspaceRoot $workspaceRoot -ForceDownload:$ForceMavenDownload
Write-Host "Portable Maven ready: $mavenCommand" -ForegroundColor Green

if (-not $SkipDocker) {
  if (-not (Test-CommandReady -Name "docker" -Arguments @("--version"))) {
    throw "Docker CLI is still unavailable after automatic installation."
  }

  $dockerDesktopPath = Get-DockerDesktopPath
  if (-not $dockerDesktopPath) {
    throw "Docker Desktop appears to be installed, but its executable could not be found."
  }

  if (-not (Get-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue)) {
    Write-Step "Starting Docker Desktop"
    Start-Process -FilePath $dockerDesktopPath | Out-Null
  }

  Write-Step "Waiting for Docker daemon"
  Wait-ForDockerDaemon
  Write-Host "Docker Desktop is ready." -ForegroundColor Green
}

Write-Host ""
Write-Host "Host toolchain bootstrap completed." -ForegroundColor Green
