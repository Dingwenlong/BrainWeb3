param(
  [string]$EnvFile = ".env.production",
  [switch]$CheckReachability,
  [switch]$AllowExampleValues,
  [string]$ReportPath
)

$ErrorActionPreference = "Stop"

function Import-DotEnvFile {
  param([string]$Path)

  if (-not (Test-Path -LiteralPath $Path)) {
    throw "Environment file not found: $Path"
  }

  Get-Content -LiteralPath $Path | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) {
      return
    }

    $separatorIndex = $line.IndexOf("=")
    if ($separatorIndex -lt 1) {
      return
    }

    $name = $line.Substring(0, $separatorIndex).Trim()
    $value = $line.Substring($separatorIndex + 1).Trim()

    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
      $value = $value.Substring(1, $value.Length - 2)
    }

    [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
  }
}

function Get-EnvValue {
  param([string]$Name)

  return [System.Environment]::GetEnvironmentVariable($Name, "Process")
}

function Add-Result {
  param(
    [System.Collections.Generic.List[string]]$Bucket,
    [string]$Message
  )

  $Bucket.Add($Message) | Out-Null
}

function Test-PlaceholderValue {
  param([string]$Value)

  if (-not $Value) {
    return $true
  }

  $normalized = $Value.Trim().ToLowerInvariant()
  return $normalized.StartsWith("replace-") `
    -or $normalized.Contains("replace-with") `
    -or $normalized.Contains("placeholder") `
    -or $normalized.Contains("change-me") `
    -or $normalized.Contains("replace-me")
}

function Test-BooleanFalse {
  param([string]$Value)

  return $Value -and $Value.Trim().ToLowerInvariant() -eq "false"
}

function Test-SecretLength {
  param(
    [string]$Value,
    [int]$Minimum = 32
  )

  return $Value -and $Value.Length -ge $Minimum
}

function Test-UrlReachability {
  param([string]$Url)

  try {
    $uri = [Uri]$Url
    if (-not $uri.Host) {
      return $false
    }

    $port = if ($uri.IsDefaultPort) {
      if ($uri.Scheme -eq "https") { 443 } else { 80 }
    } else {
      $uri.Port
    }

    $result = Test-NetConnection -ComputerName $uri.Host -Port $port -WarningAction SilentlyContinue
    return [bool]$result.TcpTestSucceeded
  } catch {
    return $false
  }
}

function Test-JdbcReachability {
  param([string]$JdbcUrl)

  if (-not $JdbcUrl) {
    return $false
  }

  $match = [regex]::Match($JdbcUrl, "^jdbc:mysql://(?<host>[^:/?]+)(:(?<port>\d+))?/")
  if (-not $match.Success) {
    return $false
  }

  $host = $match.Groups["host"].Value
  $port = if ($match.Groups["port"].Success) { [int]$match.Groups["port"].Value } else { 3306 }
  $result = Test-NetConnection -ComputerName $host -Port $port -WarningAction SilentlyContinue
  return [bool]$result.TcpTestSucceeded
}

$workspaceRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$resolvedEnvFile = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $workspaceRoot $EnvFile }

Import-DotEnvFile -Path $resolvedEnvFile

$errors = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()
$successes = [System.Collections.Generic.List[string]]::new()

$requiredVars = @(
  "APP_STAGE",
  "SPRING_DATASOURCE_URL",
  "SPRING_DATASOURCE_USERNAME",
  "SPRING_DATASOURCE_PASSWORD",
  "AUTH_JWT_SECRET",
  "AUTH_DEMO_PASSWORD",
  "AUTH_ALLOW_DEMO_BOOTSTRAP",
  "AUTH_ALLOW_DEMO_PASSWORD_LOGIN",
  "IDENTITY_ISSUER_DID",
  "IDENTITY_CREDENTIAL_SECRET",
  "EEG_SERVICE_BASE_URL",
  "CHAIN_ENABLED",
  "STORAGE_PROVIDER"
)

foreach ($name in $requiredVars) {
  if ([string]::IsNullOrWhiteSpace((Get-EnvValue $name))) {
    Add-Result $errors "Missing required environment variable: $name"
  } else {
    Add-Result $successes "Loaded $name"
  }
}

$stage = Get-EnvValue "APP_STAGE"
if ($stage -and @("production", "staging") -notcontains $stage.Trim().ToLowerInvariant()) {
  Add-Result $warnings "APP_STAGE is '$stage'. P5 formal environment checks are usually run with 'production' or 'staging'."
}

$jwtSecret = Get-EnvValue "AUTH_JWT_SECRET"
if (-not $AllowExampleValues -and (Test-PlaceholderValue $jwtSecret)) {
  Add-Result $errors "AUTH_JWT_SECRET still looks like a placeholder."
} elseif (-not (Test-SecretLength $jwtSecret)) {
  Add-Result $errors "AUTH_JWT_SECRET must be at least 32 characters."
}

$identitySecret = Get-EnvValue "IDENTITY_CREDENTIAL_SECRET"
if (-not $AllowExampleValues -and (Test-PlaceholderValue $identitySecret)) {
  Add-Result $errors "IDENTITY_CREDENTIAL_SECRET still looks like a placeholder."
} elseif (-not (Test-SecretLength $identitySecret)) {
  Add-Result $errors "IDENTITY_CREDENTIAL_SECRET must be at least 32 characters."
}

$dbPassword = Get-EnvValue "SPRING_DATASOURCE_PASSWORD"
if (-not $AllowExampleValues -and (Test-PlaceholderValue $dbPassword)) {
  Add-Result $errors "SPRING_DATASOURCE_PASSWORD still looks like a placeholder."
}

$demoPassword = Get-EnvValue "AUTH_DEMO_PASSWORD"
if (-not $AllowExampleValues -and ((Test-PlaceholderValue $demoPassword) -or $demoPassword -eq "brainweb3-demo")) {
  Add-Result $errors "AUTH_DEMO_PASSWORD must be replaced before formal environment startup."
}

if (-not (Test-BooleanFalse (Get-EnvValue "AUTH_ALLOW_DEMO_BOOTSTRAP"))) {
  Add-Result $errors "AUTH_ALLOW_DEMO_BOOTSTRAP must be false in staging/production."
}

if (-not (Test-BooleanFalse (Get-EnvValue "AUTH_ALLOW_DEMO_PASSWORD_LOGIN"))) {
  Add-Result $errors "AUTH_ALLOW_DEMO_PASSWORD_LOGIN must be false in staging/production."
}

$chainEnabled = (Get-EnvValue "CHAIN_ENABLED")
if ($chainEnabled -and $chainEnabled.Trim().ToLowerInvariant() -eq "true") {
  foreach ($name in @("CHAIN_CONFIG_PATH", "CHAIN_CONTRACT_ADDRESS", "CHAIN_BUSINESS_CONTRACT_ADDRESS")) {
    $value = Get-EnvValue $name
    if ([string]::IsNullOrWhiteSpace($value)) {
      Add-Result $errors "Missing chain variable: $name"
      continue
    }
    if (-not $AllowExampleValues -and (Test-PlaceholderValue $value)) {
      Add-Result $errors "$name still looks like a placeholder."
    }
  }
}

$storageProvider = (Get-EnvValue "STORAGE_PROVIDER")
if ($storageProvider -and $storageProvider.Trim().ToLowerInvariant() -eq "minio") {
  foreach ($name in @("MINIO_ENDPOINT", "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY", "MINIO_BUCKET")) {
    $value = Get-EnvValue $name
    if ([string]::IsNullOrWhiteSpace($value)) {
      Add-Result $errors "Missing MinIO variable: $name"
      continue
    }
    if (-not $AllowExampleValues -and (Test-PlaceholderValue $value)) {
      Add-Result $errors "$name still looks like a placeholder."
    }
  }
}

if ($CheckReachability) {
  $checks = @(
    @{ Label = "MySQL"; Ok = (Test-JdbcReachability (Get-EnvValue "SPRING_DATASOURCE_URL")) },
    @{ Label = "EEG service"; Ok = (Test-UrlReachability (Get-EnvValue "EEG_SERVICE_BASE_URL")) }
  )

  if ($storageProvider -and $storageProvider.Trim().ToLowerInvariant() -eq "minio") {
    $checks += @{ Label = "MinIO"; Ok = (Test-UrlReachability (Get-EnvValue "MINIO_ENDPOINT")) }
  }

  foreach ($check in $checks) {
    if ($check.Ok) {
      Add-Result $successes "$($check.Label) endpoint is reachable."
    } else {
      Add-Result $warnings "$($check.Label) endpoint could not be reached from this machine."
    }
  }
}

Write-Host ""
Write-Host "BrainWeb3 formal environment preflight" -ForegroundColor Cyan
Write-Host "Env file: $resolvedEnvFile"
Write-Host ""

foreach ($message in $successes) {
  Write-Host "[ok] $message" -ForegroundColor Green
}

foreach ($message in $warnings) {
  Write-Host "[warn] $message" -ForegroundColor Yellow
}

foreach ($message in $errors) {
  Write-Host "[error] $message" -ForegroundColor Red
}

$summary = [PSCustomObject]@{
  envFile = $resolvedEnvFile
  generatedAt = (Get-Date).ToString("s")
  allowExampleValues = [bool]$AllowExampleValues
  checkReachability = [bool]$CheckReachability
  passed = ($errors.Count -eq 0)
  successCount = $successes.Count
  warningCount = $warnings.Count
  errorCount = $errors.Count
  successes = $successes
  warnings = $warnings
  errors = $errors
}

if ($ReportPath) {
  $resolvedReportPath = if ([System.IO.Path]::IsPathRooted($ReportPath)) { $ReportPath } else { Join-Path $workspaceRoot $ReportPath }
  $summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $resolvedReportPath -Encoding UTF8
  Write-Host ""
  Write-Host "Preflight report written to $resolvedReportPath" -ForegroundColor Green
}

Write-Host ""
if ($errors.Count -gt 0) {
  Write-Host "Formal environment preflight failed." -ForegroundColor Red
  exit 1
}

Write-Host "Formal environment preflight passed." -ForegroundColor Green
