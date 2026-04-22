function Write-Step {
  param(
    [string]$Message
  )

  Write-Host ""
  Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-CommandPath {
  param(
    [string]$Name
  )

  $command = Get-Command $Name -ErrorAction SilentlyContinue
  if ($null -eq $command) {
    return $null
  }

  return $command.Source
}

function Test-CommandReady {
  param(
    [string]$Name,
    [string[]]$Arguments
  )

  $commandPath = Get-CommandPath -Name $Name
  if (-not $commandPath) {
    return $false
  }

  try {
    & $commandPath @Arguments *> $null
    return $LASTEXITCODE -eq 0
  }
  catch {
    return $false
  }
}

function Update-ProcessPath {
  $machinePath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
  $userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")

  if ([string]::IsNullOrWhiteSpace($machinePath)) {
    $env:Path = $userPath
    return
  }

  if ([string]::IsNullOrWhiteSpace($userPath)) {
    $env:Path = $machinePath
    return
  }

  $env:Path = "$machinePath;$userPath"
}

function Get-WorkspaceToolRoot {
  param(
    [string]$WorkspaceRoot
  )

  $toolRoot = Join-Path $WorkspaceRoot ".codex-temp\tools"
  if (-not (Test-Path -LiteralPath $toolRoot)) {
    New-Item -ItemType Directory -Path $toolRoot -Force | Out-Null
  }

  return $toolRoot
}

function Get-PortableMavenVersion {
  return "3.9.14"
}

function Get-PortableMavenHome {
  param(
    [string]$WorkspaceRoot
  )

  $version = Get-PortableMavenVersion
  return Join-Path (Get-WorkspaceToolRoot -WorkspaceRoot $WorkspaceRoot) "apache-maven-$version"
}

function Get-PortableMavenCommand {
  param(
    [string]$WorkspaceRoot
  )

  return Join-Path (Get-PortableMavenHome -WorkspaceRoot $WorkspaceRoot) "bin\mvn.cmd"
}

function Ensure-PortableMaven {
  param(
    [string]$WorkspaceRoot,
    [switch]$ForceDownload
  )

  $version = Get-PortableMavenVersion
  $mavenHome = Get-PortableMavenHome -WorkspaceRoot $WorkspaceRoot
  $mavenCommand = Get-PortableMavenCommand -WorkspaceRoot $WorkspaceRoot

  if ((Test-Path -LiteralPath $mavenCommand) -and -not $ForceDownload) {
    return $mavenCommand
  }

  $toolRoot = Get-WorkspaceToolRoot -WorkspaceRoot $WorkspaceRoot
  $archivePath = Join-Path $toolRoot "apache-maven-$version-bin.zip"
  $downloadUrl = "https://downloads.apache.org/maven/maven-3/$version/binaries/apache-maven-$version-bin.zip"

  if (Test-Path -LiteralPath $mavenHome) {
    Remove-Item -LiteralPath $mavenHome -Recurse -Force
  }

  if (Test-Path -LiteralPath $archivePath) {
    Remove-Item -LiteralPath $archivePath -Force
  }

  Write-Step "Downloading portable Maven $version"
  Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath

  Write-Step "Extracting portable Maven $version"
  Expand-Archive -LiteralPath $archivePath -DestinationPath $toolRoot -Force
  Remove-Item -LiteralPath $archivePath -Force

  if (-not (Test-Path -LiteralPath $mavenCommand)) {
    throw "Portable Maven bootstrap failed: $mavenCommand was not created."
  }

  return $mavenCommand
}

function Get-MavenCommand {
  param(
    [string]$WorkspaceRoot
  )

  $globalCommand = Get-CommandPath -Name "mvn"
  if ($globalCommand) {
    return $globalCommand
  }

  $portableCommand = Get-PortableMavenCommand -WorkspaceRoot $WorkspaceRoot
  if (Test-Path -LiteralPath $portableCommand) {
    return $portableCommand
  }

  return $null
}

function Install-WingetPackage {
  param(
    [string]$Id,
    [string]$DisplayName
  )

  $wingetPath = Get-CommandPath -Name "winget"
  if (-not $wingetPath) {
    throw "winget is required to install $DisplayName automatically. Please install App Installer from Microsoft Store first."
  }

  Write-Step "Installing $DisplayName"
  & $wingetPath install --id $Id --exact --silent --accept-package-agreements --accept-source-agreements --disable-interactivity

  if ($LASTEXITCODE -ne 0) {
    throw "winget failed while installing $DisplayName ($Id)."
  }
}

function Get-DockerDesktopPath {
  $candidates = @(
    (Join-Path ${env:ProgramFiles} "Docker\Docker\Docker Desktop.exe"),
    (Join-Path ${env:LocalAppData} "Programs\Docker\Docker\Docker Desktop.exe")
  )

  foreach ($candidate in $candidates) {
    if ($candidate -and (Test-Path -LiteralPath $candidate)) {
      return $candidate
    }
  }

  return $null
}

function Wait-ForDockerDaemon {
  param(
    [int]$TimeoutSeconds = 180
  )

  $dockerPath = Get-CommandPath -Name "docker"
  if (-not $dockerPath) {
    throw "Docker CLI was not found in PATH."
  }

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    try {
      & $dockerPath info *> $null
      if ($LASTEXITCODE -eq 0) {
        return
      }
    }
    catch {
    }

    Start-Sleep -Seconds 3
  }

  throw "Docker Desktop is installed but the daemon did not become ready within $TimeoutSeconds seconds."
}
