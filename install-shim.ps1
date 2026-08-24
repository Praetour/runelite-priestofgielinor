$ErrorActionPreference = 'Stop'
$rl = "$env:LOCALAPPDATA\RuneLite"
$sideload = "$env:USERPROFILE\.runelite\sideloaded-plugins"
$jarSrc = "D:\Unreal\Projects\RS Plugins\VowTaker\build\libs\VowTaker-1.0.0.jar"

# 1. sideload folder + jar
New-Item -ItemType Directory -Force -Path $sideload | Out-Null
Copy-Item $jarSrc $sideload -Force
Write-Output "[1/5] Sideload folder: $sideload"
Get-ChildItem $sideload | Format-Table Name, Length

# 2. Backup RuneLite.exe
$exe = Join-Path $rl 'RuneLite.exe'
$orig = Join-Path $rl 'RuneLite-original.exe'
if (Test-Path $orig) {
    Write-Output "[2/5] Backup already exists: $orig"
    if (Test-Path $exe) {
        # Existing RuneLite.exe present alongside backup — assume prior shim, remove for overwrite
        Remove-Item -LiteralPath $exe -Force
    }
} else {
    if (-not (Test-Path $exe)) { throw "RuneLite.exe not found at $exe" }
    Rename-Item -LiteralPath $exe -NewName 'RuneLite-original.exe'
    Write-Output "[2/5] Backed up RuneLite.exe -> RuneLite-original.exe"
}

# 3. Shim source
$shimSrc = @'
using System;
using System.Diagnostics;
using System.IO;
using System.Text;

class Shim {
    static int Main(string[] args) {
        string dir = AppDomain.CurrentDomain.BaseDirectory;
        string target = Path.Combine(dir, "RuneLite-original.exe");
        string sideload = Environment.ExpandEnvironmentVariables(@"%USERPROFILE%\.runelite\sideloaded-plugins");

        StringBuilder argsSb = new StringBuilder();
        argsSb.Append("--sideloaded-plugins=\"" + sideload + "\"");
        foreach (string a in args) {
            argsSb.Append(' ');
            argsSb.Append(QuoteArg(a));
        }

        try {
            ProcessStartInfo psi = new ProcessStartInfo();
            psi.FileName = target;
            psi.Arguments = argsSb.ToString();
            psi.UseShellExecute = false;
            Process p = Process.Start(psi);
            p.WaitForExit();
            return p.ExitCode;
        } catch (Exception ex) {
            Console.Error.WriteLine("Shim failed: " + ex.Message);
            return 1;
        }
    }

    static string QuoteArg(string a) {
        if (string.IsNullOrEmpty(a)) return "\"\"";
        if (a.IndexOfAny(new char[] {' ', '\t', '"'}) < 0) return a;
        return "\"" + a.Replace("\"", "\\\"") + "\"";
    }
}
'@

$shimSrcPath = Join-Path $env:TEMP 'RuneLiteShim.cs'
$shimSrc | Set-Content -Encoding ASCII $shimSrcPath
Write-Output "[3/5] Shim source at $shimSrcPath"

# 4. Compile
$csc = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (-not (Test-Path $csc)) { $csc = "$env:WINDIR\Microsoft.NET\Framework\v4.0.30319\csc.exe" }
if (-not (Test-Path $csc)) { throw "csc.exe not found" }

$out = Join-Path $rl 'RuneLite.exe'
& $csc /nologo /target:exe /out:$out $shimSrcPath
if (-not (Test-Path $out)) { throw "Shim compile failed" }
Write-Output "[4/5] Compiled shim: $out"

# 5. Verify
Write-Output "[5/5] Final state:"
Get-ChildItem $rl -Filter 'RuneLite*.exe' | Format-Table Name, Length, LastWriteTime
