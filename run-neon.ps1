# ==============================================================================
# Start the CelebStash backend against the linked Neon Postgres branch (PowerShell).
#
# PowerShell equivalent of run-neon.sh. Reads the Neon connection string and any
# other secrets from .env.local (written by `neon link`, git-ignored via "*.local")
# and converts them into the SPRING_DATASOURCE_* variables application.properties
# already expects. No credential is written into a tracked file.
#
# Usage:
#   .\run-neon.ps1              # direct (unpooled) endpoint  [recommended]
#   .\run-neon.ps1 -Pooled      # pooled (PgBouncer) endpoint
#
# Plain `.\mvnw.cmd spring-boot:run` skips all of this and falls back to the local
# Postgres defaults in application.properties — use this script to target Neon.
# ==============================================================================
param(
    [switch]$Pooled,
    [switch]$Migrate   # allow Hibernate to apply new entity columns this run
)

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

$envFile = Join-Path $PSScriptRoot '.env.local'
if (-not (Test-Path $envFile)) {
    Write-Error "$envFile not found. Run: neon link --project-id <id> --branch <branch>"
    exit 1
}

# Load every KEY=VALUE from .env.local (Cloudinary, SMTP, seed passwords, ...).
$values = @{}
foreach ($line in Get-Content $envFile) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
    $idx = $trimmed.IndexOf('=')
    if ($idx -lt 1) { continue }
    $key = $trimmed.Substring(0, $idx).Trim()
    $val = $trimmed.Substring($idx + 1).Trim().Trim('"')
    $values[$key] = $val
    # DATABASE_URL* are translated into SPRING_DATASOURCE_* below, not exported raw.
    if ($key -notlike 'DATABASE_URL*' -and $key -ne 'NEON_BRANCH') {
        [Environment]::SetEnvironmentVariable($key, $val, 'Process')
    }
}

$raw = if ($Pooled) { $values['DATABASE_URL'] } else { $values['DATABASE_URL_UNPOOLED'] }
if ([string]::IsNullOrWhiteSpace($raw)) { $raw = $values['DATABASE_URL'] }
if ([string]::IsNullOrWhiteSpace($raw)) {
    Write-Error "No DATABASE_URL found in $envFile"
    exit 1
}

# postgresql://user:password@host/db?params  ->  JDBC url + user + password
$rest        = $raw.Substring($raw.IndexOf('://') + 3)
$credentials = $rest.Substring(0, $rest.IndexOf('@'))
$hostAndDb   = $rest.Substring($rest.IndexOf('@') + 1)
$dbUser      = $credentials.Substring(0, $credentials.IndexOf(':'))
$dbPass      = $credentials.Substring($credentials.IndexOf(':') + 1)
$hostPort    = $hostAndDb.Split('/')[0]
$dbNamePart  = $hostAndDb.Substring($hostAndDb.IndexOf('/') + 1)
$dbName      = $dbNamePart.Split('?')[0]

# Neon requires TLS. channel_binding is libpq-only and the JDBC driver rejects it, so it
# is deliberately not carried over.
$jdbcParams = 'sslmode=require'
if ($Pooled) {
    # PgBouncer transaction pooling: disable server-side prepared statements.
    $jdbcParams += '&prepareThreshold=0'
}

$env:SPRING_DATASOURCE_URL      = "jdbc:postgresql://$hostPort/$dbName`?$jdbcParams"
$env:SPRING_DATASOURCE_USERNAME = $dbUser
$env:SPRING_DATASOURCE_PASSWORD = $dbPass

# The schema already exists in Neon. Skipping Hibernate's metadata scan keeps startup at
# ~30s instead of ~100s; -Migrate re-enables it for a run that adds entity columns (that
# scan can outlast the JDBC socketTimeout, so give it more room too).
if ($Migrate) {
    $env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'update'
    $env:DB_SOCKET_TIMEOUT_SECONDS     = '300'
    Write-Host '[run-neon] schema migration ENABLED for this run (ddl-auto=update)'
} else {
    $env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'none'
}
$env:SPRING_SQL_INIT_MODE = 'never'

Write-Host "[run-neon] endpoint : $hostPort"
Write-Host "[run-neon] database : $dbName"
Write-Host "[run-neon] user     : $dbUser"
Write-Host "[run-neon] jdbc     : jdbc:postgresql://$hostPort/$dbName`?$jdbcParams"
Write-Host "[run-neon] password : (read from .env.local, not shown)"
if ($values.ContainsKey('CLOUDINARY_URL')) {
    Write-Host "[run-neon] uploads  : Cloudinary"
} else {
    Write-Host "[run-neon] uploads  : database (stored_files) - CLOUDINARY_URL not set"
}

& (Join-Path $PSScriptRoot 'mvnw.cmd') spring-boot:run
