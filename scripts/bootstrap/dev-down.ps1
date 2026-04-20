$ErrorActionPreference = "Stop"

docker compose -f "docker-compose.dev.yml" down

Write-Host "Local infrastructure stopped." -ForegroundColor Yellow
