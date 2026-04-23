$ErrorActionPreference = "Stop"

function Get-EnvOrDefault {
  param(
    [string]$Name,
    [string]$DefaultValue
  )

  $value = [System.Environment]::GetEnvironmentVariable($Name)
  if ([string]::IsNullOrWhiteSpace($value)) {
    return $DefaultValue
  }

  return $value
}

docker compose -f "docker-compose.dev.yml" up -d

Write-Host ""
Write-Host "Local infrastructure is running." -ForegroundColor Green
Write-Host "MySQL  : localhost:$(Get-EnvOrDefault -Name 'MYSQL_PORT' -DefaultValue '3306')"
Write-Host "Redis  : localhost:$(Get-EnvOrDefault -Name 'REDIS_PORT' -DefaultValue '6379')"
Write-Host "IPFS   : localhost:$(Get-EnvOrDefault -Name 'IPFS_API_PORT' -DefaultValue '5001')"
Write-Host "Gateway: localhost:$(Get-EnvOrDefault -Name 'IPFS_GATEWAY_PORT' -DefaultValue '8088')"
Write-Host "MinIO  : localhost:$(Get-EnvOrDefault -Name 'MINIO_PORT' -DefaultValue '9000')"
Write-Host "Console: localhost:$(Get-EnvOrDefault -Name 'MINIO_CONSOLE_PORT' -DefaultValue '9001')"
