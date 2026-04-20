$ErrorActionPreference = "Stop"

$commands = @(
  @{ Name = "node"; Command = "node -v" },
  @{ Name = "npm"; Command = "npm -v" },
  @{ Name = "java"; Command = "java -version" },
  @{ Name = "mvn"; Command = "mvn -v" },
  @{ Name = "python"; Command = "python --version" },
  @{ Name = "docker"; Command = "docker --version" }
)

foreach ($item in $commands) {
  Write-Host "Checking $($item.Name) ..." -ForegroundColor Cyan
  Invoke-Expression $item.Command | Out-Host
}

Write-Host ""
Write-Host "Environment check completed." -ForegroundColor Green
