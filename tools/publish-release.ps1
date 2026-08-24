<#
    Cuts a VowTaker release.

    Builds the jar, writes update-manifest.json with a SHA-256, and optionally publishes both
    to GitHub Releases so every launcher picks the update up on next start.

    Requires the GitHub CLI (https://cli.github.com) for -Publish. Run 'gh auth login' once first.

    Examples:
        .\publish-release.ps1 -Version 1.0.1
        .\publish-release.ps1 -Version 1.0.1 -Notes "Fixed promotion bug" -Publish
#>

[CmdletBinding()]
param(
    # Version string for this release, e.g. 1.0.1. Must be unique per release.
    [Parameter(Mandatory = $true)]
    [string] $Version,

    # Short changelog line shown in the launcher.
    [string] $Notes = '',

    # Upload to GitHub Releases via the gh CLI.
    [switch] $Publish,

    # owner/repo to publish to. Defaults to the repo in the current directory.
    [string] $Repo,

    # Skip the Gradle build and use whatever is already in build\libs.
    [switch] $NoBuild
)

$ErrorActionPreference = 'Stop'
$scriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir
$distDir    = Join-Path $projectDir 'dist'
$gradle     = 'C:\tools\gradle-8.10\bin\gradle.bat'

function Write-Step { param([string] $m) Write-Host "  $m" -ForegroundColor Cyan }
function Write-Ok   { param([string] $m) Write-Host "  $m" -ForegroundColor Green }

Write-Host "`nPublishing VowTaker $Version" -ForegroundColor White
Write-Host ("-" * 46)

# --- Keep build.gradle's version in step ---------------------------------
$buildFile = Join-Path $projectDir 'build.gradle'
$buildText = Get-Content $buildFile -Raw
if ($buildText -notmatch "(?m)^version\s*=\s*'$([regex]::Escape($Version))'") {
    $buildText = $buildText -replace "(?m)^version\s*=\s*'[^']*'", "version = '$Version'"
    # BOM-less: Gradle cannot parse a build script that starts with one.
    [IO.File]::WriteAllText($buildFile, $buildText, (New-Object Text.UTF8Encoding $false))
    Write-Step "Set build.gradle version to $Version"
}

# --- Keep the plugin manifest in step ------------------------------------
$propsFile = Join-Path $projectDir 'src\main\resources\runelite-plugin.properties'
if (Test-Path $propsFile) {
    $props = Get-Content $propsFile -Raw
    $props = $props -replace "(?m)^version=.*$", "version=$Version"
    [IO.File]::WriteAllText($propsFile, $props, (New-Object Text.UTF8Encoding $false))
    Write-Step "Set runelite-plugin.properties version to $Version"
}

# --- Build ----------------------------------------------------------------
if (-not $NoBuild) {
    Write-Step "Building..."
    Push-Location $projectDir
    try {
        # Gradle writes progress to stderr, so redirect it away rather than tripping $ErrorActionPreference.
        $ErrorActionPreference = 'Continue'
        & $gradle --console=plain --no-daemon build 2>&1 | Out-String -Stream | Where-Object { $_ -match 'BUILD|error:' } | Write-Host
        $ErrorActionPreference = 'Stop'
    } finally {
        Pop-Location
    }
}

$jar = Get-ChildItem -Path (Join-Path $projectDir 'build\libs') -Filter "VowTaker-$Version.jar" -File -ErrorAction SilentlyContinue |
       Select-Object -First 1
if (-not $jar) {
    Write-Error "Expected build\libs\VowTaker-$Version.jar but it was not produced. Check the build output."
}
Write-Ok "Built $($jar.Name) ($([math]::Round($jar.Length / 1KB)) KB)"

# --- Manifest -------------------------------------------------------------
New-Item -ItemType Directory -Path $distDir -Force | Out-Null
$hash = (Get-FileHash $jar.FullName -Algorithm SHA256).Hash

if (-not $Repo) {
    try { $Repo = (gh repo view --json nameWithOwner -q .nameWithOwner 2>$null) } catch { }
}
if (-not $Repo) { $Repo = 'Praetour/runelite-priestofgielinor' }

$manifest = [ordered]@{
    version     = $Version
    url         = "https://github.com/$Repo/releases/download/v$Version/VowTaker-$Version.jar"
    sha256      = $hash
    notes       = $Notes
    releasedUtc = (Get-Date).ToUniversalTime().ToString('o')
}

$manifestPath = Join-Path $distDir 'update-manifest.json'
# BOM-less UTF-8: PowerShell 5.1's -Encoding UTF8 adds a BOM that breaks ConvertFrom-Json.
[IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json), (New-Object Text.UTF8Encoding $false))
Copy-Item $jar.FullName $distDir -Force

Write-Ok "Wrote $manifestPath"
Write-Host "    sha256 $hash" -ForegroundColor Gray

# --- Publish --------------------------------------------------------------
if ($Publish) {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        Write-Error "GitHub CLI 'gh' not found. Install from https://cli.github.com and run 'gh auth login'."
    }

    $tag = "v$Version"
    Write-Step "Creating release $tag on $Repo..."

    $existing = gh release view $tag --repo $Repo 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Step "Release $tag exists - replacing assets."
        gh release upload $tag $jar.FullName $manifestPath --repo $Repo --clobber
    } else {
        $title = "VowTaker $Version"
        $body  = if ($Notes) { $Notes } else { "VowTaker $Version" }
        gh release create $tag $jar.FullName $manifestPath --repo $Repo --title $title --notes $body
    }

    Write-Ok "Published. Launchers will pick this up on next start."
} else {
    Write-Host ""
    Write-Host "  Not published (pass -Publish to upload). To publish manually," -ForegroundColor Gray
    Write-Host "  create a release tagged v$Version and attach both files from dist\." -ForegroundColor Gray
}

Write-Host ("-" * 46)
Write-Host "Done.`n" -ForegroundColor White
