<#
    VowTaker uninstaller.

    Puts RuneLite back exactly as it was: restores the original launcher, removes the plugin jar,
    and clears any staged update. Your progress and your edited blocklist are kept unless you
    pass -RemoveData.

    Usage:
        PowerShell -NoProfile -ExecutionPolicy Bypass -File .\uninstall-vowtaker.ps1

        -RemoveData   also delete saves, blocklist and settings
        -DryRun       show what would happen, change nothing
#>

[CmdletBinding()]
param(
    # Also delete save files, the item blocklist and staged updates.
    [switch] $RemoveData,

    # Report the actions without performing them.
    [switch] $DryRun
)

$ErrorActionPreference = 'Stop'

function Write-Step { param([string] $m) Write-Host "  $m" -ForegroundColor Cyan }
function Write-Ok   { param([string] $m) Write-Host "  $m" -ForegroundColor Green }
function Write-Warn { param([string] $m) Write-Host "  $m" -ForegroundColor Yellow }
function Act        { param([string] $m) if ($DryRun) { Write-Host "  [dry run] $m" -ForegroundColor DarkGray; return $false } return $true }

Write-Host "`nVowTaker uninstaller" -ForegroundColor White
if ($DryRun) { Write-Host "(dry run - nothing will be changed)" -ForegroundColor DarkGray }
Write-Host ("-" * 46)

# --- Nothing may be in use ------------------------------------------------
if (Get-Process -Name 'RuneLite', 'java' -ErrorAction SilentlyContinue) {
    Write-Warn "RuneLite appears to be running. Close it first, then re-run this script."
    if (-not $DryRun) { return }
}

$runeliteDir = Join-Path $env:USERPROFILE '.runelite'
$pluginDir   = Join-Path $runeliteDir 'sideloaded-plugins'
$dataDir     = Join-Path $runeliteDir 'vowtaker'

# --- Restore the real launcher -------------------------------------------
function Find-RuneLiteFolder {
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA 'RuneLite'),
        (Join-Path ${env:ProgramFiles} 'RuneLite'),
        (Join-Path ${env:ProgramFiles(x86)} 'RuneLite')
    )
    foreach ($c in $candidates) { if ($c -and (Test-Path (Join-Path $c 'RuneLite.exe'))) { return $c } }
    return $null
}

$rlDir = Find-RuneLiteFolder
if ($rlDir) {
    $live     = Join-Path $rlDir 'RuneLite.exe'
    $original = Join-Path $rlDir 'RuneLite-original.exe'

    if (Test-Path $original) {
        if (Act "restore $original -> RuneLite.exe") {
            Remove-Item $live -Force -ErrorAction SilentlyContinue
            Move-Item $original $live -Force
            Remove-Item (Join-Path $rlDir 'RuneLite.exe.config') -Force -ErrorAction SilentlyContinue
            Write-Ok "Restored the original RuneLite launcher."
        }
    } else {
        Write-Step "No shim installed (RuneLite-original.exe not found) - launcher untouched."
    }
} else {
    Write-Warn "Could not locate the RuneLite folder - skipping launcher restore."
}

# --- Remove the plugin ----------------------------------------------------
$jars = @(Get-ChildItem -Path $pluginDir -Filter 'VowTaker*.jar' -File -ErrorAction SilentlyContinue)
if ($jars.Count -gt 0) {
    foreach ($j in $jars) {
        if (Act "delete $($j.Name)") { Remove-Item $j.FullName -Force }
    }
    if (-not $DryRun) { Write-Ok "Removed $($jars.Count) plugin jar(s)." }
} else {
    Write-Step "No plugin jar found."
}

# --- Clear any staged update and its helper -------------------------------
$updates = Join-Path $dataDir 'updates'
if (Test-Path $updates) {
    if (Act "delete staged updates in $updates") {
        Remove-Item $updates -Recurse -Force
        Write-Ok "Cleared staged updates."
    }
} else {
    Write-Step "No staged updates pending."
}

# --- Optionally wipe player data -----------------------------------------
if ($RemoveData) {
    if (Test-Path $dataDir) {
        if (Act "delete $dataDir (saves, blocklist, settings)") {
            Remove-Item $dataDir -Recurse -Force
            Write-Ok "Removed all VowTaker data."
        }
    }
} elseif (Test-Path $dataDir) {
    Write-Step "Kept your saves and blocklist in $dataDir (pass -RemoveData to delete)."
}

Write-Host ("-" * 46)
Write-Host "Done." -ForegroundColor White
Write-Host @"

RuneLite is back to normal. Launch it however you normally do.

If you only wanted to stop the update checks but keep playing, you did not
need this script - untick 'Automatic updates' in the VowTaker plugin settings,
or just disable the VowTaker plugin in the plugin list.
"@ -ForegroundColor Gray
