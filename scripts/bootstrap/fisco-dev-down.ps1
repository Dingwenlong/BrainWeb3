$ErrorActionPreference = "Stop"

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

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$script:BashExecutable = Resolve-BashExecutable
$script:UseGitBash = $script:BashExecutable -match '\\Git\\'
$fiscoRoot = Resolve-FiscoRoot -RepoRoot $repoRoot
$nodeRoot = Join-Path $fiscoRoot "network\nodes\127.0.0.1"
$stopScript = Join-Path $nodeRoot "stop_all.sh"

if (-not (Test-Path $stopScript)) {
  Write-Host "No local FISCO network scripts were found. Nothing to stop." -ForegroundColor Yellow
  exit 0
}

$bashNodeRoot = To-BashPath $nodeRoot
$envPrefix = if ($script:UseGitBash) {
  "export MSYS_NO_PATHCONV=1 && export MSYS2_ARG_CONV_EXCL='*' && "
} else {
  ""
}
& $script:BashExecutable -lc "${envPrefix}cd '$bashNodeRoot' && bash ./stop_all.sh"
if ($LASTEXITCODE -ne 0) {
  throw "Failed to stop local FISCO BCOS nodes."
}

Write-Host "Local FISCO BCOS network stopped." -ForegroundColor Yellow
