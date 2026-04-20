$ErrorActionPreference = "Stop"

docker compose -f "docker-compose.dev.yml" up -d

Write-Host ""
Write-Host "Local infrastructure is running." -ForegroundColor Green
Write-Host "MySQL  : localhost:$($env:MYSQL_PORT ? $env:MYSQL_PORT : '3306')"
Write-Host "Redis  : localhost:$($env:REDIS_PORT ? $env:REDIS_PORT : '6379')"
Write-Host "IPFS   : localhost:$($env:IPFS_API_PORT ? $env:IPFS_API_PORT : '5001')"
Write-Host "Gateway: localhost:$($env:IPFS_GATEWAY_PORT ? $env:IPFS_GATEWAY_PORT : '8088')"
Write-Host "MinIO  : localhost:$($env:MINIO_PORT ? $env:MINIO_PORT : '9000')"
Write-Host "Console: localhost:$($env:MINIO_CONSOLE_PORT ? $env:MINIO_CONSOLE_PORT : '9001')"
