param(
  [ValidateSet("admin-only", "standard-demo-roles")]
  [string]$Profile = "standard-demo-roles",
  [string]$OutputPath,
  [string]$ClassPathFile = ".codex-temp\\backend.classpath",
  [string]$DefaultPassword,
  [string]$AdminActorId = "admin-01",
  [string]$AdminDisplayName = "平台管理员",
  [string]$AdminOrganization = "Huaxi Medical Union",
  [string]$AdminPassword,
  [string]$ResearcherPassword,
  [string]$OwnerPassword,
  [string]$ApproverPassword
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

  $resolved = if ([System.IO.Path]::IsPathRooted($Path)) { $Path } else { Join-Path $workspaceRoot $Path }
  if (-not $AllowMissing -and -not (Test-Path -LiteralPath $resolved)) {
    throw "Path not found: $resolved"
  }
  return $resolved
}

function Resolve-AccountPassword {
  param(
    [string]$Password,
    [string]$FallbackPassword,
    [string]$Label
  )

  $resolved = if (-not [string]::IsNullOrWhiteSpace($Password)) { $Password } else { $FallbackPassword }
  if ([string]::IsNullOrWhiteSpace($resolved)) {
    throw "Password is required for $Label. Pass -$Label`Password or -DefaultPassword."
  }
  return $resolved
}

function Ensure-BackendClasspath {
  param([string]$Path)

  if (Test-Path -LiteralPath $Path) {
    return
  }

  Ensure-Directory -Path (Split-Path -Parent $Path)
  & mvn -q -pl apps/backend dependency:build-classpath "-Dmdep.outputFile=$Path"
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $Path)) {
    throw "Failed to generate backend dependency classpath."
  }
}

function Ensure-BcryptCli {
  param(
    [string]$TempDir,
    [string]$Classpath
  )

  Ensure-Directory -Path $TempDir
  $effectiveClasspath = $Classpath.Trim()
  $sourcePath = Join-Path $TempDir "BcryptCli.java"
  $classPath = Join-Path $TempDir "BcryptCli.class"

  $source = @'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptCli {
  public static void main(String[] args) {
    if (args.length != 1) {
      throw new IllegalArgumentException("Expected exactly one password argument.");
    }
    System.out.println(new BCryptPasswordEncoder().encode(args[0]));
  }
}
'@
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($sourcePath, $source, $utf8NoBom)

  if ((-not (Test-Path -LiteralPath $classPath)) -or ((Get-Item -LiteralPath $classPath).LastWriteTime -lt (Get-Item -LiteralPath $sourcePath).LastWriteTime)) {
    & javac -cp $effectiveClasspath $sourcePath
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $classPath)) {
      throw "Failed to compile BCrypt helper."
    }
  }
}

function Get-BcryptHash {
  param(
    [string]$Password,
    [string]$Classpath,
    [string]$TempDir
  )

  $effectiveClasspath = $Classpath.Trim()
  $hash = & java -cp "$effectiveClasspath;$TempDir" BcryptCli $Password
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($hash)) {
    throw "Failed to generate BCrypt hash."
  }
  return $hash.Trim()
}

function Escape-SqlLiteral {
  param([string]$Value)

  return $Value.Replace("'", "''")
}

function New-AccountSeedStatement {
  param(
    [pscustomobject]$Account,
    [string]$PasswordHash
  )

  $id = Escape-SqlLiteral $Account.actorId
  $displayName = Escape-SqlLiteral $Account.displayName
  $role = Escape-SqlLiteral $Account.roleCode
  $organization = Escape-SqlLiteral $Account.organization
  $hash = Escape-SqlLiteral $PasswordHash

  return @"
insert into app_users (
    id,
    display_name,
    password_hash,
    role_code,
    organization,
    status,
    created_at,
    updated_at,
    password_changed_at,
    last_login_at
) values (
    '$id',
    '$displayName',
    '$hash',
    '$role',
    '$organization',
    'active',
    current_timestamp(6),
    current_timestamp(6),
    current_timestamp(6),
    null
) on duplicate key update
    display_name = values(display_name),
    password_hash = values(password_hash),
    role_code = values(role_code),
    organization = values(organization),
    status = 'active',
    updated_at = current_timestamp(6),
    password_changed_at = current_timestamp(6),
    last_login_at = null;
"@
}

$workspaceRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
  $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
  $OutputPath = "artifacts\\account-seeds\\account-seed-$Profile-$timestamp.sql"
}

$resolvedOutputPath = Resolve-WorkspacePath -Path $OutputPath -AllowMissing
Ensure-Directory -Path (Split-Path -Parent $resolvedOutputPath)

$resolvedClasspathFile = Resolve-WorkspacePath -Path $ClassPathFile -AllowMissing
Ensure-BackendClasspath -Path $resolvedClasspathFile
$classpath = (Get-Content -LiteralPath $resolvedClasspathFile -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($classpath)) {
  throw "Backend dependency classpath file is empty: $resolvedClasspathFile"
}

$bcryptTempDir = Join-Path $workspaceRoot ".codex-temp\\bcrypt-cli"
Ensure-BcryptCli -TempDir $bcryptTempDir -Classpath $classpath

$accounts = [System.Collections.Generic.List[object]]::new()
$accounts.Add([PSCustomObject]@{
    actorId = $AdminActorId.Trim().ToLowerInvariant()
    displayName = $AdminDisplayName.Trim()
    roleCode = "admin"
    organization = $AdminOrganization.Trim()
    password = (Resolve-AccountPassword -Password $AdminPassword -FallbackPassword $DefaultPassword -Label "Admin")
  }) | Out-Null

if ($Profile -eq "standard-demo-roles") {
  $accounts.Add([PSCustomObject]@{
      actorId = "researcher-01"
      displayName = "研究员一号"
      roleCode = "researcher"
      organization = "Sichuan Neuro Lab"
      password = (Resolve-AccountPassword -Password $ResearcherPassword -FallbackPassword $DefaultPassword -Label "Researcher")
    }) | Out-Null

  $accounts.Add([PSCustomObject]@{
      actorId = "owner-01"
      displayName = "归属方一号"
      roleCode = "owner"
      organization = "Huaxi Medical Union"
      password = (Resolve-AccountPassword -Password $OwnerPassword -FallbackPassword $DefaultPassword -Label "Owner")
    }) | Out-Null

  $accounts.Add([PSCustomObject]@{
      actorId = "approver-01"
      displayName = "审批人一号"
      roleCode = "approver"
      organization = "Huaxi Medical Union"
      password = (Resolve-AccountPassword -Password $ApproverPassword -FallbackPassword $DefaultPassword -Label "Approver")
    }) | Out-Null
}

$sqlLines = [System.Collections.Generic.List[string]]::new()
$sqlLines.Add("-- BrainWeb3 account seed SQL") | Out-Null
$sqlLines.Add(("-- Generated at: {0}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"))) | Out-Null
$sqlLines.Add(("-- Profile: {0}" -f $Profile)) | Out-Null
$sqlLines.Add("-- This script updates passwords for duplicate actor IDs.") | Out-Null
$sqlLines.Add("start transaction;") | Out-Null
$sqlLines.Add("") | Out-Null

foreach ($account in $accounts) {
  $passwordHash = Get-BcryptHash -Password $account.password -Classpath $classpath -TempDir $bcryptTempDir
  $sqlLines.Add(("-- account: {0} ({1})" -f $account.actorId, $account.roleCode)) | Out-Null
  $sqlLines.Add((New-AccountSeedStatement -Account $account -PasswordHash $passwordHash).TrimEnd()) | Out-Null
  $sqlLines.Add("") | Out-Null
}

$sqlLines.Add("commit;") | Out-Null

Set-Content -LiteralPath $resolvedOutputPath -Value ($sqlLines -join [Environment]::NewLine) -Encoding UTF8

Write-Host ""
Write-Host "Account seed SQL written to $resolvedOutputPath" -ForegroundColor Green
Write-Host "Included accounts:" -ForegroundColor Cyan
foreach ($account in $accounts) {
  Write-Host "- $($account.actorId) [$($account.roleCode)]"
}
