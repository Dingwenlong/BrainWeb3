param(
  [string]$AcceptanceReportPath,
  [string]$PackageName = "P5-delivery",
  [string]$EnvironmentLabel = "simulated-formal",
  [string]$OutputDir = "artifacts\\delivery",
  [string]$FormalEnvChecklistPath = "docs\\P5 正式环境联调清单.md",
  [string]$AcceptanceBaselinePath = "docs\\P5 交付与验收基线.md",
  [string]$DemoScriptPath = "docs\\P5 演示脚本.md",
  [string]$AccountsDocPath = "docs\\P5 演示账号清单.md",
  [string]$AcceptanceChecklistPath = "docs\\P5 验收路径清单.md",
  [string]$KnownLimitsPath = "docs\\P5 已知边界与正式环境迁移清单.md",
  [string]$EnvTemplatePath = ".env.production.example",
  [string[]]$EvidencePaths = @(),
  [string[]]$Notes = @()
)

$ErrorActionPreference = "Stop"

function Ensure-Directory {
  param([string]$Path)

  if (-not (Test-Path -LiteralPath $Path)) {
    New-Item -ItemType Directory -Path $Path | Out-Null
  }
}

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

function Get-RelativePackagePath {
  param([string]$TargetPath)

  $resolvedPackageRoot = [System.IO.Path]::GetFullPath($packageRoot).TrimEnd("\")
  $resolvedTargetPath = [System.IO.Path]::GetFullPath($TargetPath)

  if ($resolvedTargetPath.StartsWith($resolvedPackageRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    return $resolvedTargetPath.Substring($resolvedPackageRoot.Length).TrimStart("\")
  }

  return Split-Path -Leaf $resolvedTargetPath
}

function Find-LatestAcceptanceReport {
  param([string]$AcceptanceDir)

  $candidate = Get-ChildItem -LiteralPath $AcceptanceDir -Filter "P5-acceptance-*.md" -File |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

  if ($null -eq $candidate) {
    throw "No acceptance report found under $AcceptanceDir. Run report:acceptance first or pass -AcceptanceReportPath."
  }

  return $candidate.FullName
}

function Copy-PackageFile {
  param(
    [string]$SourcePath,
    [string]$DestinationPath,
    [System.Collections.Generic.List[object]]$ManifestFiles
  )

  Ensure-Directory -Path (Split-Path -Parent $DestinationPath)
  Copy-Item -LiteralPath $SourcePath -Destination $DestinationPath -Force

  $relativePath = Get-RelativePackagePath -TargetPath $DestinationPath
  $ManifestFiles.Add([PSCustomObject]@{
      source = $SourcePath
      packagedAs = $relativePath.Replace("\", "/")
    }) | Out-Null
}

function Copy-PackageDirectory {
  param(
    [string]$SourcePath,
    [string]$DestinationPath,
    [System.Collections.Generic.List[object]]$ManifestFiles
  )

  Ensure-Directory -Path (Split-Path -Parent $DestinationPath)
  Copy-Item -LiteralPath $SourcePath -Destination $DestinationPath -Recurse -Force

  Get-ChildItem -LiteralPath $DestinationPath -Recurse -File | ForEach-Object {
    $relativePath = Get-RelativePackagePath -TargetPath $_.FullName
    $ManifestFiles.Add([PSCustomObject]@{
        source = $_.FullName
        packagedAs = $relativePath.Replace("\", "/")
      }) | Out-Null
  }
}

function Get-AcceptanceCompanionPaths {
  param([string]$ReportPath)

  $reportName = Split-Path -Leaf $ReportPath
  $match = [regex]::Match($reportName, "^P5-acceptance-(?<stamp>\d{8}-\d{6})\.md$")
  if (-not $match.Success) {
    return @()
  }

  $stamp = $match.Groups["stamp"].Value
  $dir = Split-Path -Parent $ReportPath
  $companions = @(
    (Join-Path $dir "preflight-$stamp.json"),
    (Join-Path $dir "smoke-$stamp.json")
  )

  return @($companions | Where-Object { Test-Path -LiteralPath $_ })
}

function New-EvidencePlaceholder {
  param([string]$Path)

  $content = @(
    "# Delivery Evidence Checklist",
    "",
    "补充本轮交付建议保留的截图或录屏证据：",
    "",
    "- 登录页与账号切换入口",
    "- 总览页主工作台",
    "- 数据详情页与 3D 热力图",
    "- 访问申请与审批处理结果",
    "- 训练任务列表与状态结果",
    "- 审计中心与链轨迹页",
    "- 身份中心或模型治理页",
    "",
    "若本轮有失败或降级演示，请在此目录补一份文字说明。"
  )

  Set-Content -LiteralPath $Path -Value ($content -join [Environment]::NewLine) -Encoding UTF8
}

$workspaceRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$resolvedOutputDir = Resolve-WorkspacePath -Path $OutputDir -AllowMissing
Ensure-Directory -Path $resolvedOutputDir

$acceptanceDir = Join-Path $workspaceRoot "artifacts\\acceptance"
$resolvedAcceptanceReportPath = if ($AcceptanceReportPath) {
  Resolve-WorkspacePath -Path $AcceptanceReportPath
} else {
  Find-LatestAcceptanceReport -AcceptanceDir $acceptanceDir
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$packageRoot = Join-Path $resolvedOutputDir "$PackageName-$timestamp"
$packageDocsDir = Join-Path $packageRoot "docs"
$packageAcceptanceDir = Join-Path $packageRoot "acceptance"
$packageEnvDir = Join-Path $packageRoot "env"
$packageEvidenceDir = Join-Path $packageRoot "evidence"

Ensure-Directory -Path $packageDocsDir
Ensure-Directory -Path $packageAcceptanceDir
Ensure-Directory -Path $packageEnvDir
Ensure-Directory -Path $packageEvidenceDir

$manifestFiles = [System.Collections.Generic.List[object]]::new()

$docMappings = @(
  @{ Source = (Resolve-WorkspacePath -Path $AcceptanceBaselinePath); Destination = (Join-Path $packageDocsDir (Split-Path -Leaf $AcceptanceBaselinePath)) },
  @{ Source = (Resolve-WorkspacePath -Path $FormalEnvChecklistPath); Destination = (Join-Path $packageDocsDir (Split-Path -Leaf $FormalEnvChecklistPath)) },
  @{ Source = (Resolve-WorkspacePath -Path $DemoScriptPath); Destination = (Join-Path $packageDocsDir (Split-Path -Leaf $DemoScriptPath)) },
  @{ Source = (Resolve-WorkspacePath -Path $AccountsDocPath); Destination = (Join-Path $packageDocsDir (Split-Path -Leaf $AccountsDocPath)) },
  @{ Source = (Resolve-WorkspacePath -Path $AcceptanceChecklistPath); Destination = (Join-Path $packageDocsDir (Split-Path -Leaf $AcceptanceChecklistPath)) },
  @{ Source = (Resolve-WorkspacePath -Path $KnownLimitsPath); Destination = (Join-Path $packageDocsDir (Split-Path -Leaf $KnownLimitsPath)) },
  @{ Source = (Resolve-WorkspacePath -Path $EnvTemplatePath); Destination = (Join-Path $packageEnvDir (Split-Path -Leaf $EnvTemplatePath)) }
)

foreach ($mapping in $docMappings) {
  Copy-PackageFile -SourcePath $mapping.Source -DestinationPath $mapping.Destination -ManifestFiles $manifestFiles
}

Copy-PackageFile -SourcePath $resolvedAcceptanceReportPath -DestinationPath (Join-Path $packageAcceptanceDir (Split-Path -Leaf $resolvedAcceptanceReportPath)) -ManifestFiles $manifestFiles

foreach ($companionPath in (Get-AcceptanceCompanionPaths -ReportPath $resolvedAcceptanceReportPath)) {
  Copy-PackageFile -SourcePath $companionPath -DestinationPath (Join-Path $packageAcceptanceDir (Split-Path -Leaf $companionPath)) -ManifestFiles $manifestFiles
}

if ($EvidencePaths.Count -gt 0) {
  foreach ($path in $EvidencePaths) {
    $resolvedEvidencePath = Resolve-WorkspacePath -Path $path
    $destinationPath = Join-Path $packageEvidenceDir (Split-Path -Leaf $resolvedEvidencePath)
    if (Test-Path -LiteralPath $resolvedEvidencePath -PathType Container) {
      Copy-PackageDirectory -SourcePath $resolvedEvidencePath -DestinationPath $destinationPath -ManifestFiles $manifestFiles
    } else {
      Copy-PackageFile -SourcePath $resolvedEvidencePath -DestinationPath $destinationPath -ManifestFiles $manifestFiles
    }
  }
} else {
  $placeholderPath = Join-Path $packageEvidenceDir "README.md"
  New-EvidencePlaceholder -Path $placeholderPath
  $manifestFiles.Add([PSCustomObject]@{
      source = "generated"
      packagedAs = "evidence/README.md"
    }) | Out-Null
}

$relativeAcceptanceReport = (Get-RelativePackagePath -TargetPath (Join-Path $packageAcceptanceDir (Split-Path -Leaf $resolvedAcceptanceReportPath))).Replace("\", "/")
$notesBlock = if ($Notes.Count -gt 0) {
  ($Notes | ForEach-Object { "- $_" }) -join [Environment]::NewLine
} else {
  "- none"
}

$evidenceEntries = Get-ChildItem -LiteralPath $packageEvidenceDir -Recurse -File | ForEach-Object {
  (Get-RelativePackagePath -TargetPath $_.FullName).Replace("\", "/")
}

$generatedAtDisplay = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$readmeLines = [System.Collections.Generic.List[string]]::new()
$readmeLines.Add("# BrainWeb3 P5 Delivery Package") | Out-Null
$readmeLines.Add("") | Out-Null
$readmeLines.Add(('- Generated at: `' + $generatedAtDisplay + '`')) | Out-Null
$readmeLines.Add(('- Package name: `' + $PackageName + '`')) | Out-Null
$readmeLines.Add(('- Environment label: `' + $EnvironmentLabel + '`')) | Out-Null
$readmeLines.Add(('- Acceptance report: `' + $relativeAcceptanceReport + '`')) | Out-Null
$readmeLines.Add("") | Out-Null
$readmeLines.Add("## Suggested Review Order") | Out-Null
$readmeLines.Add('1. Read `docs/P5 交付与验收基线.md` for the current delivery baseline.') | Out-Null
$readmeLines.Add('2. Read `docs/P5 正式环境联调清单.md` to confirm the environment mapping and run order.') | Out-Null
$readmeLines.Add('3. Review `acceptance/` for the latest preflight, smoke and acceptance outputs.') | Out-Null
$readmeLines.Add('4. Use `docs/P5 演示脚本.md` to drive the demo and acceptance walkthrough.') | Out-Null
$readmeLines.Add('5. Use `docs/P5 验收路径清单.md` and `docs/P5 已知边界与正式环境迁移清单.md` as the sign-off and risk appendix.') | Out-Null
$readmeLines.Add("") | Out-Null
$readmeLines.Add("## Included Evidence") | Out-Null

if ($evidenceEntries.Count -gt 0) {
  foreach ($entry in $evidenceEntries) {
    $readmeLines.Add("- $entry") | Out-Null
  }
} else {
  $readmeLines.Add("- evidence/README.md") | Out-Null
}

$readmeLines.Add("") | Out-Null
$readmeLines.Add("## Notes") | Out-Null
foreach ($line in ($notesBlock -split [Environment]::NewLine)) {
  $readmeLines.Add($line) | Out-Null
}

$readmePath = Join-Path $packageRoot "README.md"
Set-Content -LiteralPath $readmePath -Value ($readmeLines -join [Environment]::NewLine) -Encoding UTF8
$manifestFiles.Add([PSCustomObject]@{
    source = "generated"
    packagedAs = "README.md"
  }) | Out-Null

$manifest = [PSCustomObject]@{
  packageName = $PackageName
  generatedAt = (Get-Date).ToString("s")
  environmentLabel = $EnvironmentLabel
  sourceAcceptanceReport = $resolvedAcceptanceReportPath
  notes = $Notes
  files = $manifestFiles
}

$manifestPath = Join-Path $packageRoot "package-manifest.json"
$manifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

Write-Host ""
Write-Host "Delivery package written to $packageRoot" -ForegroundColor Green
