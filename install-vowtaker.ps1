<#
    VowTaker installer.

    Copies the plugin jar into RuneLite's sideloaded-plugins folder and seeds the editable
    item blocklist. Safe to re-run - it never overwrites an existing item-tags.json, so
    custom blocklist edits survive upgrades.

    Usage (from anywhere):
        PowerShell -NoProfile -ExecutionPolicy Bypass -File .\install-vowtaker.ps1
#>

[CmdletBinding()]
param(
    # Folder containing VowTaker-<version>.jar. Defaults to the folder this script sits in.
    [string] $SourceDir,

    # Overwrite item-tags.json even if the user already has one.
    [switch] $ResetTags
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($SourceDir)) {
    $SourceDir = Split-Path -Parent $MyInvocation.MyCommand.Path
}
if ([string]::IsNullOrWhiteSpace($SourceDir)) { $SourceDir = (Get-Location).Path }

function Write-Step { param([string] $Message) Write-Host "  $Message" -ForegroundColor Cyan }
function Write-Ok   { param([string] $Message) Write-Host "  $Message" -ForegroundColor Green }
function Write-Warn { param([string] $Message) Write-Host "  $Message" -ForegroundColor Yellow }

Write-Host "`nVowTaker installer" -ForegroundColor White
Write-Host ("-" * 40)

# --- Locate the jar -------------------------------------------------------
$jar = Get-ChildItem -Path $SourceDir -Filter 'VowTaker-*.jar' -File -ErrorAction SilentlyContinue |
       Sort-Object LastWriteTime -Descending |
       Select-Object -First 1

if (-not $jar) {
    # Fall back to a Gradle build output if the script is run from the repo root.
    $jar = Get-ChildItem -Path (Join-Path $SourceDir 'build\libs') -Filter 'VowTaker-*.jar' -File -ErrorAction SilentlyContinue |
           Sort-Object LastWriteTime -Descending |
           Select-Object -First 1
}

if (-not $jar) {
    Write-Error "No VowTaker-*.jar found in '$SourceDir' (or its build\libs). Put the jar next to this script and re-run."
}
Write-Step "Found jar: $($jar.Name)"

# --- Locate RuneLite ------------------------------------------------------
$runeliteDir = Join-Path $env:USERPROFILE '.runelite'
if (-not (Test-Path $runeliteDir)) {
    Write-Error "RuneLite folder not found at '$runeliteDir'. Install and launch RuneLite once, then re-run this script."
}

$pluginDir = Join-Path $runeliteDir 'sideloaded-plugins'
$dataDir   = Join-Path $runeliteDir 'vowtaker'
New-Item -ItemType Directory -Path $pluginDir -Force | Out-Null
New-Item -ItemType Directory -Path $dataDir   -Force | Out-Null

# --- Clear out older builds so RuneLite doesn't load two copies ------------
$stale = Get-ChildItem -Path $pluginDir -Filter 'VowTaker-*.jar' -File -ErrorAction SilentlyContinue
foreach ($old in $stale) {
    Remove-Item $old.FullName -Force
    Write-Step "Removed previous build: $($old.Name)"
}

Copy-Item $jar.FullName (Join-Path $pluginDir $jar.Name) -Force
Write-Ok "Installed plugin -> $pluginDir\$($jar.Name)"

# --- Seed the editable blocklist -----------------------------------------
$tagsTarget = Join-Path $dataDir 'item-tags.json'
$tagsSource = Get-ChildItem -Path $SourceDir -Filter 'item-tags.json' -File -Recurse -ErrorAction SilentlyContinue |
              Select-Object -First 1

if (Test-Path $tagsTarget) {
    if ($ResetTags -and $tagsSource) {
        Copy-Item $tagsSource.FullName $tagsTarget -Force
        Write-Warn "Reset item-tags.json to defaults (-ResetTags was passed)."
    } else {
        Write-Step "Kept your existing item-tags.json (use -ResetTags to restore defaults)."
    }
} elseif ($tagsSource) {
    Copy-Item $tagsSource.FullName $tagsTarget -Force
    Write-Ok "Seeded blocklist -> $tagsTarget"
} else {
    Write-Step "No item-tags.json alongside the jar; the plugin will create one on first launch."
}

# --- Done -----------------------------------------------------------------
Write-Host ("-" * 40)
Write-Host "Done." -ForegroundColor White
Write-Host @"

Next steps:
  1. Close RuneLite completely if it is running.
  2. Launch RuneLite however you normally do (Jagex Launcher, Steam or the exe).
  3. Open the plugin list (wrench icon) and enable 'VowTaker'.
  4. Log in, then pick your god in the VowTaker side panel.

This is a one-off setup. The plugin updates itself from then on: it checks on
startup, downloads any new build in the background, and swaps it in when you
close the client. You never need to run this installer again.

Your progress saves to:
  $dataDir\<username>_vow_state.json

Your editable item blocklist is:
  $tagsTarget

In-game commands:
  !vow status              - rank, points, milestone and pending promotion
  !vow tags                - blocklist file location and loaded tag count
  !vow tags <item name>    - show which tags an item matches
"@ -ForegroundColor Gray
