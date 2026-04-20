param(
  [string]$SampleRoot = ".brainweb3-samples"
)

$ErrorActionPreference = "Stop"

$destinationDir = Join-Path $SampleRoot "physionet\S001"
$destinationFile = Join-Path $destinationDir "S001R04.edf"
$sourceUrl = "https://physionet.org/files/eegmmidb/1.0.0/S001/S001R04.edf"

New-Item -ItemType Directory -Path $destinationDir -Force | Out-Null

if (Test-Path $destinationFile) {
  Write-Host "PhysioNet sample already exists: $destinationFile" -ForegroundColor Yellow
  exit 0
}

Write-Host "Downloading PhysioNet sample..." -ForegroundColor Cyan
Write-Host "Source: $sourceUrl"
Write-Host "Target: $destinationFile"

Invoke-WebRequest -Uri $sourceUrl -OutFile $destinationFile

Write-Host ""
Write-Host "Sample ready." -ForegroundColor Green
Write-Host "Set BRAINWEB3_SAMPLE_ROOT=$SampleRoot if you use a custom path."
