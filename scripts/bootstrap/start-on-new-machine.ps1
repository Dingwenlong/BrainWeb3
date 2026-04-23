param(
  [switch]$IncludeFederatedService,
  [switch]$SkipDocker,
  [switch]$ForceMavenDownload
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceRoot = (Resolve-Path (Join-Path $scriptRoot "..\..")).Path

. (Join-Path $scriptRoot "_tooling.ps1")

$needsBootstrap = $ForceMavenDownload -or -not (Test-LauncherPrerequisites -WorkspaceRoot $workspaceRoot -SkipDocker:$SkipDocker)

if ($needsBootstrap) {
  Write-Step "Bootstrapping host machine"
  & (Join-Path $scriptRoot "install-host-tools.ps1") -SkipDocker:$SkipDocker -ForceMavenDownload:$ForceMavenDownload

  Write-Step "Starting BrainWeb3 services"
  & (Join-Path $scriptRoot "start-all.ps1") -InstallDeps -IncludeFederatedService:$IncludeFederatedService
}
else {
  Write-Step "Detected existing local toolchain"
  & (Join-Path $scriptRoot "start-all.ps1") -IncludeFederatedService:$IncludeFederatedService
}
