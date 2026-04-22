param(
  [string]$SqlPath,
  [string]$EnvFile = ".env.production",
  [ValidateSet("auto", "local-mysql", "docker-compose")]
  [string]$Mode = "auto",
  [string]$ComposeFile = "docker-compose.dev.yml",
  [string]$ComposeService = "mysql",
  [string]$DbHost,
  [int]$DbPort,
  [string]$DbName,
  [string]$DbUsername,
  [string]$DbPassword,
  [switch]$PrintOnly
)

$ErrorActionPreference = "Stop"

function Resolve-WorkspacePath {
  param(
    [string]$Path,
    [switch]$AllowMissing
  )

  if ([string]::IsNullOrWhiteSpace($Path)) {
    return $null
  }

  $resolved = if ([System.IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path $workspaceRoot $Path }
  if (-not $AllowMissing -and -not (Test-Path -LiteralPath $resolved)) {
    throw "Path not found: $resolved"
  }

  return $resolved
}

function Import-DotEnvFile {
  param([string]$Path)

  if (-not $Path -or -not (Test-Path -LiteralPath $Path)) {
    return
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

function Get-FirstNonEmpty {
  param([string[]]$Values)

  foreach ($value in $Values) {
    if (-not [string]::IsNullOrWhiteSpace($value)) {
      return $value
    }
  }

  return $null
}

function Find-LatestAccountSeed {
  param([string]$SeedDir)

  if (-not (Test-Path -LiteralPath $SeedDir)) {
    throw "Account seed directory not found: $SeedDir. Run seed:accounts first or pass -SqlPath."
  }

  $candidate = Get-ChildItem -LiteralPath $SeedDir -Filter "account-seed-*.sql" -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

  if ($null -eq $candidate) {
    throw "No account seed SQL found under $SeedDir. Run seed:accounts first or pass -SqlPath."
  }

  return $candidate.FullName
}

function Parse-JdbcUrl {
  param([string]$JdbcUrl)

  if ([string]::IsNullOrWhiteSpace($JdbcUrl)) {
    return $null
  }

  $match = [regex]::Match($JdbcUrl, "^jdbc:mysql://(?<host>[^:/?]+)(:(?<port>\d+))?/(?<db>[^?]+)(\?(?<query>.*))?$")
  if (-not $match.Success) {
    throw "Unsupported JDBC URL format: $JdbcUrl"
  }

  $queryPairs = @{}
  if ($match.Groups["query"].Success) {
    foreach ($segment in $match.Groups["query"].Value.Split("&")) {
      if ([string]::IsNullOrWhiteSpace($segment)) {
        continue
      }
      $pair = $segment.Split("=", 2)
      $key = $pair[0]
      $value = if ($pair.Length -gt 1) { $pair[1] } else { "" }
      $queryPairs[$key] = $value
    }
  }

  return [PSCustomObject]@{
    host = $match.Groups["host"].Value
    port = if ($match.Groups["port"].Success) { [int]$match.Groups["port"].Value } else { 3306 }
    database = $match.Groups["db"].Value
    useSsl = ($queryPairs.ContainsKey("useSSL") -and $queryPairs["useSSL"].ToLowerInvariant() -eq "true")
  }
}

function Resolve-ExecutionMode {
  param(
    [string]$RequestedMode,
    [string]$ComposeFilePath
  )

  if ($RequestedMode -ne "auto") {
    return $RequestedMode
  }

  $mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
  if ($mysqlCommand) {
    return "local-mysql"
  }

  $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
  if ($dockerCommand -and (Test-Path -LiteralPath $ComposeFilePath)) {
    return "docker-compose"
  }

  throw "Could not resolve import mode automatically. Install mysql client or pass -Mode docker-compose with Docker available."
}

function Invoke-LocalMysqlImport {
  param(
    [string]$MysqlExecutable,
    [string]$SqlFilePath,
    [string]$DbHost,
    [int]$DbPort,
    [string]$DbName,
    [string]$DbUser,
    [string]$DbPassword,
    [bool]$UseSsl,
    [switch]$PrintOnly
  )

  $args = @(
    "--host=$DbHost",
    "--port=$DbPort",
    "--user=$DbUser",
    "--database=$DbName",
    "--default-character-set=utf8mb4"
  )

  if ($UseSsl) {
    $args += "--ssl-mode=REQUIRED"
  }

  if ($PrintOnly) {
    Write-Host ""
    Write-Host "Dry run: local mysql import" -ForegroundColor Cyan
    Write-Host "$MysqlExecutable $($args -join ' ') < $SqlFilePath"
    return
  }

  $previousMysqlPwd = [System.Environment]::GetEnvironmentVariable("MYSQL_PWD", "Process")
  try {
    [System.Environment]::SetEnvironmentVariable("MYSQL_PWD", $DbPassword, "Process")
    Get-Content -LiteralPath $SqlFilePath -Raw | & $MysqlExecutable @args
    if ($LASTEXITCODE -ne 0) {
      throw "mysql client exited with code $LASTEXITCODE."
    }
  } finally {
    [System.Environment]::SetEnvironmentVariable("MYSQL_PWD", $previousMysqlPwd, "Process")
  }
}

function Invoke-DockerComposeImport {
  param(
    [string]$ComposeFilePath,
    [string]$ComposeServiceName,
    [string]$SqlFilePath,
    [string]$DbHost,
    [int]$DbPort,
    [string]$DbName,
    [string]$DbUser,
    [string]$DbPassword,
    [bool]$UseSsl,
    [switch]$PrintOnly
  )

  $innerArgs = @(
    "MYSQL_PWD=$DbPassword",
    "mysql",
    "--host=$DbHost",
    "--port=$DbPort",
    "--user=$DbUser",
    "--database=$DbName",
    "--default-character-set=utf8mb4"
  )

  if ($UseSsl) {
    $innerArgs += "--ssl-mode=REQUIRED"
  }

  if ($PrintOnly) {
    Write-Host ""
    Write-Host "Dry run: docker compose mysql import" -ForegroundColor Cyan
    Write-Host "docker compose -f $ComposeFilePath exec -T $ComposeServiceName env MYSQL_PWD=*** mysql --host=$DbHost --port=$DbPort --user=$DbUser --database=$DbName --default-character-set=utf8mb4 < $SqlFilePath"
    return
  }

  Get-Content -LiteralPath $SqlFilePath -Raw | & docker compose -f $ComposeFilePath exec -T $ComposeServiceName env @innerArgs
  if ($LASTEXITCODE -ne 0) {
    throw "docker compose mysql import exited with code $LASTEXITCODE."
  }
}

$workspaceRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$resolvedEnvFile = Resolve-WorkspacePath -Path $EnvFile -AllowMissing
Import-DotEnvFile -Path $resolvedEnvFile

$resolvedSqlPath = if ($SqlPath) {
  Resolve-WorkspacePath -Path $SqlPath
} else {
  Find-LatestAccountSeed -SeedDir (Join-Path $workspaceRoot "artifacts\\account-seeds")
}

$jdbcInfo = Parse-JdbcUrl -JdbcUrl (Get-EnvValue "SPRING_DATASOURCE_URL")
$dbHost = if ($DbHost) { $DbHost } elseif ($jdbcInfo) { $jdbcInfo.host } else { "127.0.0.1" }
$dbPort = if ($DbPort) { $DbPort } elseif ($jdbcInfo) { $jdbcInfo.port } else { 3306 }
$dbName = if ($DbName) { $DbName } elseif ($jdbcInfo) { $jdbcInfo.database } else { Get-EnvValue "MYSQL_DATABASE" }
$dbUser = if ($DbUsername) { $DbUsername } else { Get-FirstNonEmpty -Values @((Get-EnvValue "SPRING_DATASOURCE_USERNAME"), (Get-EnvValue "MYSQL_USERNAME")) }
$dbPassword = if ($DbPassword) { $DbPassword } else { Get-FirstNonEmpty -Values @((Get-EnvValue "SPRING_DATASOURCE_PASSWORD"), (Get-EnvValue "MYSQL_PASSWORD")) }

if ([string]::IsNullOrWhiteSpace($dbName)) {
  throw "Database name could not be resolved. Pass -Database or provide SPRING_DATASOURCE_URL / MYSQL_DATABASE."
}
if ([string]::IsNullOrWhiteSpace($dbUser)) {
  throw "Database username could not be resolved. Pass -Username or provide SPRING_DATASOURCE_USERNAME / MYSQL_USERNAME."
}
if ([string]::IsNullOrWhiteSpace($dbPassword)) {
  throw "Database password could not be resolved. Pass -Password or provide SPRING_DATASOURCE_PASSWORD / MYSQL_PASSWORD."
}

$useSsl = if ($jdbcInfo) { [bool]$jdbcInfo.useSsl } else { $false }
$resolvedComposeFile = Resolve-WorkspacePath -Path $ComposeFile -AllowMissing
$executionMode = Resolve-ExecutionMode -RequestedMode $Mode -ComposeFilePath $resolvedComposeFile

Write-Host ""
Write-Host "BrainWeb3 account seed import" -ForegroundColor Cyan
Write-Host "SQL file : $resolvedSqlPath"
Write-Host "Mode     : $executionMode"
Write-Host "Database : $dbUser@$dbHost`:$dbPort/$dbName"
Write-Host "Env file : $(if ($resolvedEnvFile) { $resolvedEnvFile } else { 'not provided / not found' })"

switch ($executionMode) {
  "local-mysql" {
    $mysqlCommand = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $mysqlCommand) {
      throw "mysql executable was not found on PATH."
    }
    Invoke-LocalMysqlImport `
      -MysqlExecutable $mysqlCommand.Source `
      -SqlFilePath $resolvedSqlPath `
      -DbHost $dbHost `
      -DbPort $dbPort `
      -DbName $dbName `
      -DbUser $dbUser `
      -DbPassword $dbPassword `
      -UseSsl $useSsl `
      -PrintOnly:$PrintOnly
    break
  }
  "docker-compose" {
    if (-not (Test-Path -LiteralPath $resolvedComposeFile)) {
      throw "Compose file not found: $resolvedComposeFile"
    }
    Invoke-DockerComposeImport `
      -ComposeFilePath $resolvedComposeFile `
      -ComposeServiceName $ComposeService `
      -SqlFilePath $resolvedSqlPath `
      -DbHost $dbHost `
      -DbPort $dbPort `
      -DbName $dbName `
      -DbUser $dbUser `
      -DbPassword $dbPassword `
      -UseSsl $useSsl `
      -PrintOnly:$PrintOnly
    break
  }
  default {
    throw "Unsupported execution mode: $executionMode"
  }
}

if (-not $PrintOnly) {
  Write-Host ""
  Write-Host "Account seed import completed." -ForegroundColor Green
}
