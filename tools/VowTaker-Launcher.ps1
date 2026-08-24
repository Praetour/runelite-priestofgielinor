<#
    VowTaker launcher.

    Checks a manifest URL for a newer plugin build, installs it if there is one, then starts
    RuneLite. Give this (plus launcher-config.json) to anyone you want to keep up to date -
    they run this instead of RuneLite.exe and always get the current build.

    Fails open: if the update check fails for any reason, RuneLite still launches.
#>

[CmdletBinding()]
param(
    # Check for updates but do not launch RuneLite.
    [switch] $UpdateOnly,

    # Launch RuneLite without checking for updates.
    [switch] $SkipUpdate,

    # Reinstall the current version even if it is already present.
    [switch] $Force
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($scriptDir)) { $scriptDir = (Get-Location).Path }

$runeliteDir = Join-Path $env:USERPROFILE '.runelite'
$pluginDir   = Join-Path $runeliteDir 'sideloaded-plugins'
$dataDir     = Join-Path $runeliteDir 'vowtaker'
$statePath   = Join-Path $dataDir 'installed-version.json'

function Write-Info { param([string] $m) Write-Host "  $m" -ForegroundColor Cyan }
function Write-Ok   { param([string] $m) Write-Host "  $m" -ForegroundColor Green }
function Write-Warn { param([string] $m) Write-Host "  $m" -ForegroundColor Yellow }

Write-Host "`nVowTaker" -ForegroundColor White
Write-Host ("-" * 46)

# --- Config ---------------------------------------------------------------
$configPath = Join-Path $scriptDir 'launcher-config.json'
if (-not (Test-Path $configPath)) {
    Write-Warn "launcher-config.json not found next to this script. Skipping update check."
    $config = $null
} else {
    try {
        $config = Get-Content $configPath -Raw | ConvertFrom-Json
    } catch {
        Write-Warn "launcher-config.json is not valid JSON. Skipping update check."
        $config = $null
    }
}

# --- Locate RuneLite ------------------------------------------------------
function Find-RuneLite {
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA 'RuneLite\RuneLite.exe'),
        (Join-Path ${env:ProgramFiles} 'RuneLite\RuneLite.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'RuneLite\RuneLite.exe')
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { return $c }
    }
    return $null
}

$runelite = Find-RuneLite
if (-not $runelite) {
    Write-Warn "Could not find RuneLite.exe automatically."
}

# --- Update check ---------------------------------------------------------
function Get-InstalledVersion {
    if (-not (Test-Path $statePath)) { return $null }
    try { return (Get-Content $statePath -Raw | ConvertFrom-Json).version } catch { return $null }
}

function Invoke-UpdateCheck {
    param([object] $Config)

    if (-not $Config -or [string]::IsNullOrWhiteSpace($Config.manifestUrl)) {
        Write-Warn "No manifestUrl configured. Skipping update check."
        return
    }

    Write-Info "Checking for updates..."

    # TLS 1.2 is not the default on stock PowerShell 5.1 and GitHub requires it.
    try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }

    $manifest = $null
    try {
        # Fetch as text and parse explicitly: GitHub serves release assets as
        # application/octet-stream, so Invoke-RestMethod would hand back a raw string.
        $raw = (Invoke-WebRequest -Uri $Config.manifestUrl -TimeoutSec 15 -UseBasicParsing `
                    -Headers @{ 'Cache-Control' = 'no-cache' }).Content
        if ($raw -is [byte[]]) { $raw = [Text.Encoding]::UTF8.GetString($raw) }
        # ConvertFrom-Json rejects a leading UTF-8 BOM.
        $raw = $raw.Trim([char]0xFEFF, [char]0x200B).Trim()
        $manifest = $raw | ConvertFrom-Json
    } catch {
        Write-Warn "Update check failed ($($_.Exception.Message)). Launching current version."
        return
    }

    if (-not $manifest.version -or -not $manifest.url) {
        Write-Warn "Manifest is missing 'version' or 'url'. Skipping."
        return
    }

    $installed = Get-InstalledVersion
    $jarPresent = @(Get-ChildItem -Path $pluginDir -Filter 'VowTaker-*.jar' -File -ErrorAction SilentlyContinue).Count -gt 0

    if (-not $Force -and $jarPresent -and $installed -eq $manifest.version) {
        Write-Ok "Up to date (version $installed)."
        return
    }

    if ($installed) {
        Write-Info "Update available: $installed -> $($manifest.version)"
    } else {
        Write-Info "Installing VowTaker $($manifest.version)"
    }

    # Refuse to swap the jar underneath a running client.
    if (Get-Process -Name 'RuneLite' -ErrorAction SilentlyContinue) {
        Write-Warn "RuneLite is already running. Close it and re-run to apply the update."
        return
    }

    $temp = Join-Path $env:TEMP "VowTaker-$($manifest.version).jar"
    try {
        Write-Info "Downloading..."
        Invoke-WebRequest -Uri $manifest.url -OutFile $temp -TimeoutSec 120 -UseBasicParsing
    } catch {
        Write-Warn "Download failed ($($_.Exception.Message)). Launching current version."
        return
    }

    if ($manifest.sha256) {
        $actual = (Get-FileHash $temp -Algorithm SHA256).Hash
        if ($actual -ne $manifest.sha256.ToUpper()) {
            Write-Warn "Checksum mismatch - discarding download. Launching current version."
            Remove-Item $temp -Force -ErrorAction SilentlyContinue
            return
        }
        Write-Info "Checksum verified."
    }

    New-Item -ItemType Directory -Path $pluginDir -Force | Out-Null
    New-Item -ItemType Directory -Path $dataDir   -Force | Out-Null

    # Only one VowTaker jar may be present or RuneLite loads duplicates.
    Get-ChildItem -Path $pluginDir -Filter 'VowTaker-*.jar' -File -ErrorAction SilentlyContinue |
        ForEach-Object { Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue }

    Move-Item $temp (Join-Path $pluginDir "VowTaker-$($manifest.version).jar") -Force
    @{ version = $manifest.version; installedUtc = (Get-Date).ToUniversalTime().ToString('o') } |
        ConvertTo-Json | Set-Content $statePath -Encoding UTF8

    Write-Ok "Installed VowTaker $($manifest.version)."
    if ($manifest.notes) { Write-Host "  $($manifest.notes)" -ForegroundColor Gray }
}

if (-not $SkipUpdate) {
    try { Invoke-UpdateCheck -Config $config }
    catch { Write-Warn "Update check errored ($($_.Exception.Message)). Continuing." }
}

# --- Launch ---------------------------------------------------------------
if ($UpdateOnly) {
    Write-Host ("-" * 46)
    Write-Host "Done (update only).`n" -ForegroundColor White
    return
}

if (Get-Process -Name 'RuneLite' -ErrorAction SilentlyContinue) {
    Write-Info "RuneLite is already running."
} elseif ($runelite) {
    Write-Info "Starting RuneLite..."
    Start-Process -FilePath $runelite | Out-Null
} else {
    Write-Warn "RuneLite.exe not found - start it manually."
    Write-Warn "Searched: %LOCALAPPDATA%\RuneLite, Program Files, Program Files (x86)."
}

Write-Host ("-" * 46)
Start-Sleep -Seconds 2
