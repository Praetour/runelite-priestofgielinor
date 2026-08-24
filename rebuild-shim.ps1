$ErrorActionPreference = 'Stop'
$rl = "$env:LOCALAPPDATA\RuneLite"

$shimSrc = @'
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;

class Shim {
    const string MAIN_CLASS = "net.runelite.client.RuneLite";
    const string WRAPPER_CLASS = "com.vowtaker.launch.WrapperMain";

    static int Main(string[] args) {
        string dir = AppDomain.CurrentDomain.BaseDirectory;
        string userProfile = Environment.GetEnvironmentVariable("USERPROFILE");
        string logDir = Path.Combine(userProfile, ".runelite", "logs");
        Directory.CreateDirectory(logDir);
        string shimLog = Path.Combine(logDir, "shim.log");
        string pluginJar = Path.Combine(userProfile, ".runelite", "sideloaded-plugins", "VowTaker-1.0.0.jar");

        StreamWriter log = null;
        try {
            log = new StreamWriter(new FileStream(shimLog, FileMode.Create, FileAccess.Write, FileShare.Read));
            log.WriteLine("[shim] " + DateTime.Now.ToString("s"));
            log.Flush();

            if (!File.Exists(pluginJar)) {
                log.WriteLine("[shim-warn] plugin jar missing: " + pluginJar);
            }

            string launcherLog = Path.Combine(logDir, "launcher.log");
            if (!File.Exists(launcherLog)) {
                log.WriteLine("[shim-error] launcher.log not found. Run RuneLite-original.exe once first.");
                return 2;
            }

            string runningLine = null;
            using (var fs = new FileStream(launcherLog, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            using (var sr = new StreamReader(fs)) {
                string line;
                while ((line = sr.ReadLine()) != null) {
                    int idx = line.IndexOf("Running [");
                    if (idx >= 0) {
                        runningLine = line.Substring(idx + "Running [".Length).TrimEnd(']');
                    }
                }
            }
            if (runningLine == null) {
                log.WriteLine("[shim-error] No 'Running [' line in launcher.log.");
                return 3;
            }

            List<string> parts = new List<string>(runningLine.Split(new string[] { ", " }, StringSplitOptions.None));
            log.WriteLine("[shim] parsed " + parts.Count + " tokens");

            // Append plugin jar to -cp so WrapperMain and VowTakerPlugin are on the app classpath
            int cpIdx = parts.IndexOf("-cp");
            if (cpIdx < 0 || cpIdx + 1 >= parts.Count) {
                log.WriteLine("[shim-error] -cp not found in launcher command.");
                return 4;
            }
            if (File.Exists(pluginJar) && parts[cpIdx + 1].IndexOf(pluginJar, StringComparison.OrdinalIgnoreCase) < 0) {
                parts[cpIdx + 1] = parts[cpIdx + 1] + ";" + pluginJar;
            }

            // Enable assertions (required by ExternalPluginManager.loadBuiltin)
            if (!parts.Contains("-ea")) {
                int insertAt = cpIdx + 2;
                parts.Insert(insertAt, "-ea");
            }

            // Replace main class with wrapper so we can call loadBuiltin before RuneLite.main
            int mainIdx = parts.IndexOf(MAIN_CLASS);
            if (mainIdx < 0) {
                log.WriteLine("[shim-error] main class '" + MAIN_CLASS + "' not found in launcher command.");
                return 5;
            }
            parts[mainIdx] = WRAPPER_CLASS;

            // Passthrough any args from Jagex Launcher
            foreach (string a in args) {
                parts.Add(a);
            }

            string javaExe = parts[0];
            parts.RemoveAt(0);
            StringBuilder argsSb = new StringBuilder();
            for (int i = 0; i < parts.Count; i++) {
                if (i > 0) argsSb.Append(' ');
                argsSb.Append(QuoteArg(parts[i]));
            }

            log.WriteLine("[shim] java=" + javaExe);
            log.WriteLine("[shim] main=" + WRAPPER_CLASS);
            log.WriteLine("[shim] args=" + argsSb.ToString());
            log.Flush();

            ProcessStartInfo psi = new ProcessStartInfo();
            psi.FileName = javaExe;
            psi.Arguments = argsSb.ToString();
            psi.UseShellExecute = false;
            psi.RedirectStandardOutput = true;
            psi.RedirectStandardError = true;
            psi.WorkingDirectory = dir;

            Process proc = new Process();
            proc.StartInfo = psi;
            StreamWriter logRef = log;
            proc.OutputDataReceived += delegate(object s, DataReceivedEventArgs e) {
                if (e.Data != null) { lock (logRef) { logRef.WriteLine("[OUT] " + e.Data); logRef.Flush(); } Console.Out.WriteLine(e.Data); }
            };
            proc.ErrorDataReceived += delegate(object s, DataReceivedEventArgs e) {
                if (e.Data != null) { lock (logRef) { logRef.WriteLine("[ERR] " + e.Data); logRef.Flush(); } Console.Error.WriteLine(e.Data); }
            };
            proc.Start();
            proc.BeginOutputReadLine();
            proc.BeginErrorReadLine();
            proc.WaitForExit();

            log.WriteLine("[shim] exit=" + proc.ExitCode);
            log.Flush();
            return proc.ExitCode;
        } catch (Exception ex) {
            if (log != null) { log.WriteLine("[shim-error] " + ex.ToString()); log.Flush(); }
            Console.Error.WriteLine("Shim failed: " + ex.Message);
            return 1;
        } finally {
            if (log != null) log.Close();
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

$csc = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (-not (Test-Path $csc)) { $csc = "$env:WINDIR\Microsoft.NET\Framework\v4.0.30319\csc.exe" }

# Compile as GUI subsystem so the console window doesn't pop up in normal use.
# (We used /target:exe before which is the console subsystem.)
$out = Join-Path $rl 'RuneLite.exe'
& $csc /nologo /target:winexe /out:$out $shimSrcPath
Write-Output "Rebuilt shim v5 (wrapper via loadBuiltin): $out"
