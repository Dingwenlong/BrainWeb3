param(
  [switch]$ForceRebuild
)

$ErrorActionPreference = "Stop"

function Require-Command {
  param([string]$Name)

  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Required command '$Name' was not found in PATH."
  }
}

function Resolve-BashExecutable {
  $gitBashCandidates = @(
    "C:\Program Files\Git\bin\bash.exe",
    "C:\Program Files\Git\usr\bin\bash.exe"
  )

  foreach ($candidate in $gitBashCandidates) {
    if (Test-Path $candidate) {
      return $candidate
    }
  }

  $bashCommand = Get-Command bash -ErrorAction SilentlyContinue
  if ($bashCommand) {
    return $bashCommand.Source
  }

  throw "No bash executable was found. Install Git Bash or enable a working WSL bash."
}

function To-BashPath {
  param([string]$WindowsPath)

  $fullPath = [System.IO.Path]::GetFullPath($WindowsPath)
  if ($fullPath -match '^([A-Za-z]):\\(.*)$') {
    $drive = $matches[1].ToLower()
    $tail = $matches[2] -replace '\\', '/'
    if ($script:UseGitBash) {
      return "/$drive/$tail"
    }
    return "/mnt/$drive/$tail"
  }

  throw "Unable to convert path to bash format: $WindowsPath"
}

function Invoke-BashInDir {
  param(
    [string]$WindowsDirectory,
    [string]$Command
  )

  $bashDirectory = To-BashPath $WindowsDirectory
  $escapedCommand = $Command.Replace('"', '\"')
  $bashToolsDirectory = if ($script:BashToolsRoot) { To-BashPath $script:BashToolsRoot } else { "" }
  $pathPrefix = if ($bashToolsDirectory) { "export PATH='$bashToolsDirectory':`$PATH && " } else { "" }
  $envPrefix = if ($script:UseGitBash) {
    "export MSYS_NO_PATHCONV=1 && export MSYS2_ARG_CONV_EXCL='*' && "
  } else {
    ""
  }
  & $script:BashExecutable -lc "${envPrefix}${pathPrefix}cd '$bashDirectory' && $escapedCommand"
  if ($LASTEXITCODE -ne 0) {
    throw "Bash command failed: $Command"
  }
}

function Stop-ExistingFiscoNodes {
  param([string]$NodeRoot)

  $stopScript = Join-Path $NodeRoot "stop_all.sh"
  if (-not (Test-Path $stopScript)) {
    return
  }

  try {
    Invoke-BashInDir $NodeRoot "bash ./stop_all.sh"
  } catch {
    Write-Host "Failed to stop existing FISCO nodes cleanly. Continuing with rebuild ..." -ForegroundColor Yellow
  }
}

function Ensure-BashCompatTools {
  param([string]$RootPath)

  if (-not $script:UseGitBash) {
    return $null
  }

  $toolsRoot = Join-Path $RootPath "bash-tools"
  New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null

  $wgetShimPath = Join-Path $toolsRoot "wget"
  if (-not (Test-Path $wgetShimPath)) {
    $wgetShim = @'
#!/usr/bin/env bash
set -euo pipefail

args=()
for arg in "$@"; do
  if [[ "$arg" == "--no-check-certificate" ]]; then
    continue
  fi
  args+=("$arg")
done

curl -L --fail "${args[@]}"
'@
    Set-Content -Path $wgetShimPath -Value $wgetShim -Encoding Ascii
  }

  $uuidgenShimPath = Join-Path $toolsRoot "uuidgen"
  if (-not (Test-Path $uuidgenShimPath)) {
    $uuidgenShim = @'
#!/usr/bin/env bash
powershell.exe -NoProfile -Command "[guid]::NewGuid().ToString()" | tr -d '\r'
'@
    Set-Content -Path $uuidgenShimPath -Value $uuidgenShim -Encoding Ascii
  }

  $bashWgetShim = To-BashPath $wgetShimPath
  & $script:BashExecutable -lc "chmod u+x '$bashWgetShim'"
  $bashUuidgenShim = To-BashPath $uuidgenShimPath
  & $script:BashExecutable -lc "chmod u+x '$bashUuidgenShim'"

  return $toolsRoot
}

function Ensure-TasslShim {
  if (-not $script:UseGitBash) {
    return
  }

  $userFiscoRoot = Join-Path $env:USERPROFILE ".fisco"
  New-Item -ItemType Directory -Force -Path $userFiscoRoot | Out-Null

  $tasslShimPath = Join-Path $userFiscoRoot "tassl-1.1.1b"
  if (-not (Test-Path $tasslShimPath)) {
    $tasslShim = @'
#!/usr/bin/env bash
exec openssl "$@"
'@
    Set-Content -Path $tasslShimPath -Value $tasslShim -Encoding Ascii
  }

  $bashTasslShim = To-BashPath $tasslShimPath
  & $script:BashExecutable -lc "chmod u+x '$bashTasslShim'"
}

function Resolve-FiscoRoot {
  param([string]$RepoRoot)

  if ($env:BRAINWEB3_FISCO_ROOT) {
    return [System.IO.Path]::GetFullPath($env:BRAINWEB3_FISCO_ROOT)
  }

  if ($script:UseGitBash) {
    return (Join-Path $env:USERPROFILE ".brainweb3-fisco-dev")
  }

  return (Join-Path $RepoRoot ".fisco")
}

function Test-RemoteShellScript {
  param(
    [string]$Path,
    [string]$ExpectedFirstLine
  )

  if (-not (Test-Path $Path)) {
    return $false
  }

  $firstLine = Get-Content -Path $Path -TotalCount 1 -ErrorAction SilentlyContinue
  if (-not $firstLine) {
    return $false
  }

  return $firstLine.Trim() -eq $ExpectedFirstLine
}

function Ensure-RemoteShellScript {
  param(
    [string]$Path,
    [string]$Uri,
    [string]$ExpectedFirstLine,
    [string]$Label
  )

  $needsDownload = $ForceRebuild -or -not (Test-RemoteShellScript -Path $Path -ExpectedFirstLine $ExpectedFirstLine)
  if (-not $needsDownload) {
    return
  }

  Write-Host "Downloading $Label ..." -ForegroundColor Cyan
  Invoke-WebRequest -Uri $Uri -OutFile $Path

  if (-not (Test-RemoteShellScript -Path $Path -ExpectedFirstLine $ExpectedFirstLine)) {
    throw "Downloaded $Label is invalid: $Path"
  }
}

function Use-GitBashCompatibleAccountShim {
  param([string]$Path)

  if (-not $script:UseGitBash) {
    return
  }

  $shim = @'
#!/usr/bin/env bash
set -euo pipefail

output_path="accounts"
mkdir -p "${output_path}"
paramFile="${output_path}/secp256k1.param"

if [[ ! -f "${paramFile}" ]]; then
  openssl ecparam -out "${paramFile}" -name secp256k1 >/dev/null 2>&1
fi

openssl genpkey -paramfile "${paramFile}" -out "${output_path}/ecprivkey.pem" >/dev/null 2>&1
pubKey=$(openssl ec -in "${output_path}/ecprivkey.pem" -text -noout 2>/dev/null | sed -n '7,11p' | tr -d ': \n' | awk '{print substr($0,3);}')
hash=$(printf "%s" "${pubKey}" | openssl dgst -sha256 | awk '{print $NF}')
accountAddress="0x${hash: -40}"

mv "${output_path}/ecprivkey.pem" "${output_path}/${accountAddress}.pem"
openssl ec -in "${output_path}/${accountAddress}.pem" -pubout -out "${output_path}/${accountAddress}.pem.pub" 2>/dev/null
openssl ec -in "${output_path}/${accountAddress}.pem" -pubout -out "${output_path}/${accountAddress}.public.pem" 2>/dev/null

echo -e "\033[32m[INFO] Account Address   : ${accountAddress}\033[0m"
echo -e "\033[32m[INFO] Private Key (pem) : ${output_path}/${accountAddress}.pem\033[0m"
echo -e "\033[32m[INFO] Public  Key (pem) : ${output_path}/${accountAddress}.public.pem\033[0m"
'@

  Set-Content -Path $Path -Value $shim -Encoding Ascii
}

function Use-GitBashCompatibleBuildChainPatch {
  param([string]$Path)

  if (-not $script:UseGitBash) {
    return
  }

  $content = Get-Content -Path $Path -Raw
  $patchedContent = $content.Replace('/tmp/secp256k1.param', '${output_path}/secp256k1.param')
  Set-Content -Path $Path -Value $patchedContent -Encoding Ascii
}

function Use-GitBashCompatibleDockerStartScripts {
  param([string]$NodeRoot)

  if (-not $script:UseGitBash) {
    return
  }

  Get-ChildItem -Path $NodeRoot -Directory -Filter "node*" | ForEach-Object {
    $configPath = Join-Path $_.FullName "config.ini"
    $startScriptPath = Join-Path $_.FullName "start.sh"
    if (-not (Test-Path $configPath) -or -not (Test-Path $startScriptPath)) {
      return
    }

    $configContent = Get-Content -Path $configPath -Raw
    $p2pPortMatch = [regex]::Match($configContent, '(?ms)\[p2p\].*?listen_port=(\d+)')
    $rpcPortMatch = [regex]::Match($configContent, '(?ms)\[rpc\].*?listen_port=(\d+)')
    if (-not $p2pPortMatch.Success -or -not $rpcPortMatch.Success) {
      throw "Unable to detect FISCO ports from $configPath"
    }

    $p2pPort = $p2pPortMatch.Groups[1].Value
    $rpcPort = $rpcPortMatch.Groups[1].Value
    $startContent = Get-Content -Path $startScriptPath -Raw
    $patchedStartContent = $startContent.Replace('--network=host -w=/data', "-p ${p2pPort}:${p2pPort} -p ${rpcPort}:${rpcPort} -w=/data")
    $patchedStartContent = $patchedStartContent.Replace('    kill -USR1 ${node_pid}', '    true')
    $patchedStartContent = $patchedStartContent.Replace('    kill -USR2 ${node_pid}', '    true')
    Set-Content -Path $startScriptPath -Value $patchedStartContent -Encoding Ascii
  }
}

function Use-GitBashCompatibleNodeConfigPatch {
  param([string]$NodeRoot)

  if (-not $script:UseGitBash) {
    return
  }

  Get-ChildItem -Path $NodeRoot -Directory -Filter "node*" | ForEach-Object {
    $configPath = Join-Path $_.FullName "config.ini"
    if (-not (Test-Path $configPath)) {
      return
    }

    $configContent = Get-Content -Path $configPath -Raw
    $patchedConfigContent = $configContent.Replace(';disable_ssl=true', 'disable_ssl=true')
    Set-Content -Path $configPath -Value $patchedConfigContent -Encoding Ascii
  }
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$dockerTag = if ($env:FISCO_DOCKER_TAG) { $env:FISCO_DOCKER_TAG } else { "v3.6.0" }
$buildScriptUrl = "https://github.com/FISCO-BCOS/FISCO-BCOS/releases/download/$dockerTag/build_chain.sh"
$accountScriptUrl = "https://raw.githubusercontent.com/FISCO-BCOS/console/master/tools/get_account.sh"

Require-Command "docker"
$script:BashExecutable = Resolve-BashExecutable
$script:UseGitBash = $script:BashExecutable -match '\\Git\\'
$nodeCount = if ($script:UseGitBash) { 1 } else { 4 }
$nodeLayout = "127.0.0.1:$nodeCount"
$requiredRpcPorts = 0..($nodeCount - 1) | ForEach-Object { 20200 + $_ }
$sdkPeerList = ($requiredRpcPorts | ForEach-Object { '"127.0.0.1:{0}"' -f $_ }) -join ", "
$fiscoRoot = Resolve-FiscoRoot -RepoRoot $repoRoot
$networkRoot = Join-Path $fiscoRoot "network"
$sdkRoot = Join-Path $fiscoRoot "sdk"
$sdkConfRoot = Join-Path $sdkRoot "conf"
$nodeRoot = Join-Path $networkRoot "nodes\127.0.0.1"
$buildScript = Join-Path $networkRoot "build_chain.sh"
$accountScript = Join-Path $networkRoot "get_account.sh"
$script:BashToolsRoot = Ensure-BashCompatTools $fiscoRoot
Ensure-TasslShim

New-Item -ItemType Directory -Force -Path $networkRoot | Out-Null
New-Item -ItemType Directory -Force -Path $sdkConfRoot | Out-Null

if ($ForceRebuild -and (Test-Path (Join-Path $networkRoot "nodes"))) {
  Stop-ExistingFiscoNodes -NodeRoot $nodeRoot
  Remove-Item -LiteralPath (Join-Path $networkRoot "nodes") -Recurse -Force
}

if ((Test-Path (Join-Path $networkRoot "nodes")) -and -not (Test-Path $nodeRoot)) {
  Write-Host "Cleaning incomplete FISCO node output ..." -ForegroundColor Yellow
  Remove-Item -LiteralPath (Join-Path $networkRoot "nodes") -Recurse -Force
}

Ensure-RemoteShellScript -Path $buildScript -Uri $buildScriptUrl -ExpectedFirstLine "#!/bin/bash" -Label "build_chain.sh ($dockerTag)"
Ensure-RemoteShellScript -Path $accountScript -Uri $accountScriptUrl -ExpectedFirstLine "#!/bin/bash" -Label "get_account.sh from GitHub raw"
Use-GitBashCompatibleBuildChainPatch -Path $buildScript
Use-GitBashCompatibleAccountShim -Path $accountScript

if (-not (Test-Path $nodeRoot)) {
  Write-Host "Generating local FISCO BCOS docker network ..." -ForegroundColor Cyan
  Invoke-BashInDir $networkRoot "chmod u+x ./build_chain.sh ./get_account.sh && bash ./build_chain.sh -D -l $nodeLayout -p 30300,20200"
}

Use-GitBashCompatibleDockerStartScripts -NodeRoot $nodeRoot
Use-GitBashCompatibleNodeConfigPatch -NodeRoot $nodeRoot

Write-Host "Starting FISCO BCOS nodes ..." -ForegroundColor Cyan
Invoke-BashInDir $nodeRoot "bash ./start_all.sh"

$deadline = (Get-Date).AddMinutes(2)
$reachableRpcPorts = @()
do {
  $reachableRpcPorts = @()
  foreach ($port in $requiredRpcPorts) {
    if (Test-NetConnection -ComputerName "127.0.0.1" -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue) {
      $reachableRpcPorts += $port
    }
  }

  if ($reachableRpcPorts.Count -ge $requiredRpcPorts.Count) {
    break
  }

  Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)

if ($reachableRpcPorts.Count -lt $requiredRpcPorts.Count) {
  throw "FISCO nodes did not start successfully. Reachable RPC ports: $($reachableRpcPorts -join ', ')"
}

$sourceSdkPath = Join-Path $nodeRoot "sdk\*"
if (-not (Test-Path (Join-Path $nodeRoot "sdk"))) {
  throw "FISCO SDK certificates were not generated: $sourceSdkPath"
}
Copy-Item -Path $sourceSdkPath -Destination $sdkConfRoot -Recurse -Force

$configPath = Join-Path $sdkRoot "config.toml"
$clogPath = Join-Path $sdkRoot "clog.ini"
$backendEnvPath = Join-Path $sdkRoot "backend.env"
$sdkCertPathForToml = $sdkConfRoot -replace '\\', '/'
$sdkDisableSslLine = if ($script:UseGitBash) { 'disableSsl = "true"' } else { '' }
$configToml = @"
[cryptoMaterial]
certPath = "$sdkCertPathForToml"
useSMCrypto = "false"
$sdkDisableSslLine

[network]
messageTimeout = "10000"
defaultGroup = "group0"
peers = [$sdkPeerList]

[account]
keyStoreDir = "account"
accountFileFormat = "pem"

[threadPool]
# threadPoolSize = "16"
"@

$clogIni = @"
[log]
enable=true
log_path=./log
level=INFO
max_log_file_size=200
"@

$backendEnv = @"
CHAIN_ENABLED=true
CHAIN_PROVIDER=fisco-bcos-3
CHAIN_GROUP=group0
CHAIN_CONTRACT_NAME=DataNotary
CHAIN_CONTRACT_ADDRESS=
CHAIN_AUTO_DEPLOY=true
CHAIN_CONFIG_PATH=$configPath
"@

Set-Content -Path $configPath -Value $configToml -Encoding Ascii
Set-Content -Path $clogPath -Value $clogIni -Encoding Ascii
Set-Content -Path $backendEnvPath -Value $backendEnv -Encoding Ascii

Write-Host ""
Write-Host "Local FISCO BCOS network is running." -ForegroundColor Green
Write-Host "Bash executable     : $script:BashExecutable"
Write-Host "FISCO root         : $fiscoRoot"
Write-Host "Node root          : $nodeRoot"
Write-Host "Backend config TOML: $configPath"
Write-Host "Backend env sample : $backendEnvPath"
Write-Host ""
Write-Host "To enable real chain writes in this repo:" -ForegroundColor Yellow
Write-Host "  `$env:CHAIN_ENABLED='true'"
Write-Host "  `$env:CHAIN_CONFIG_PATH='$configPath'"
