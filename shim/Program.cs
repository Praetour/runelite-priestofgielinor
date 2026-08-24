using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;

namespace VowTakerShim
{
    /// <summary>
    /// Stands in for RuneLite.exe so the VowTaker jar is loaded as a builtin plugin.
    ///
    /// RuneLite has no sideloading mechanism, so the only way to load a local jar is to be on the
    /// classpath and call ExternalPluginManager.loadBuiltin. This replays the java command the real
    /// launcher last used, appends the plugin jar, and swaps the main class for our wrapper.
    ///
    /// Being RuneLite.exe means the Jagex Launcher runs us too, and the child java process inherits
    /// the JX_ credential variables it sets.
    /// </summary>
    internal static class Program
    {
        private const string RealMainClass = "net.runelite.client.RuneLite";
        private const string WrapperMainClass = "com.vowtaker.launch.WrapperMain";
        private const string RunningMarker = "JvmLauncher - Running [";

        private static StreamWriter _log;

        private static int Main(string[] args)
        {
            string runeliteDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".runelite");

            try
            {
                Directory.CreateDirectory(Path.Combine(runeliteDir, "logs"));
                _log = new StreamWriter(Path.Combine(runeliteDir, "logs", "shim.log"), false) { AutoFlush = true };
                Log("start " + DateTime.Now.ToString("s"));

                string launcherLog = Path.Combine(runeliteDir, "logs", "launcher.log");
                if (!File.Exists(launcherLog))
                {
                    Log("no launcher.log yet - handing off to the real launcher");
                    return RunOriginal(args);
                }

                List<string> command = ParseLastCommand(launcherLog);
                if (command == null || command.Count < 3)
                {
                    Log("could not parse a java command - handing off to the real launcher");
                    return RunOriginal(args);
                }

                // A RuneLite update rewrites the repository jars, which makes the replayed classpath
                // stale. Fall back so the player still gets in; the fresh log fixes the next launch.
                if (!ClasspathIntact(command))
                {
                    Log("classpath is stale (client updated) - handing off to the real launcher");
                    return RunOriginal(args);
                }

                string jar = FindPluginJar(runeliteDir);
                if (jar == null)
                {
                    Log("no VowTaker jar found - handing off to the real launcher");
                    return RunOriginal(args);
                }
                Log("plugin=" + jar);

                if (!Rewrite(command, jar))
                {
                    Log("main class '" + RealMainClass + "' not in launcher command - handing off");
                    return RunOriginal(args);
                }

                return Launch(command, args);
            }
            catch (Exception ex)
            {
                Log("error: " + ex.Message);
                try
                {
                    return RunOriginal(args);
                }
                catch
                {
                    return 1;
                }
            }
            finally
            {
                _log?.Dispose();
            }
        }

        /// <summary>Pulls the token list out of the newest "Running [ ... ]" line.</summary>
        private static List<string> ParseLastCommand(string launcherLog)
        {
            string line = null;
            using (var fs = new FileStream(launcherLog, FileMode.Open, FileAccess.Read, FileShare.ReadWrite))
            using (var sr = new StreamReader(fs))
            {
                string current;
                while ((current = sr.ReadLine()) != null)
                {
                    if (current.Contains(RunningMarker)) line = current;
                }
            }
            if (line == null) return null;

            int open = line.IndexOf(RunningMarker, StringComparison.Ordinal) + RunningMarker.Length;
            int close = line.LastIndexOf(']');
            if (close <= open) return null;

            return line.Substring(open, close - open)
                .Split(new[] { ", " }, StringSplitOptions.None)
                .Select(t => t.Trim())
                .Where(t => t.Length > 0)
                .ToList();
        }

        private static bool ClasspathIntact(List<string> command)
        {
            int cp = command.IndexOf("-cp");
            if (cp < 0 || cp + 1 >= command.Count) return false;

            foreach (string entry in command[cp + 1].Split(';'))
            {
                if (entry.Length > 0 && !File.Exists(entry))
                {
                    Log("missing classpath entry: " + entry);
                    return false;
                }
            }
            return true;
        }

        private static string FindPluginJar(string runeliteDir)
        {
            string dir = Path.Combine(runeliteDir, "sideloaded-plugins");
            if (!Directory.Exists(dir)) return null;
            // Glob rather than a fixed name so plugin updates cannot orphan the shim.
            return Directory.GetFiles(dir, "VowTaker*.jar")
                .OrderByDescending(File.GetLastWriteTimeUtc)
                .FirstOrDefault();
        }

        private static bool Rewrite(List<string> command, string jar)
        {
            int main = command.IndexOf(RealMainClass);
            if (main < 0) return false;
            command[main] = WrapperMainClass;

            int cp = command.IndexOf("-cp");
            if (cp >= 0 && cp + 1 < command.Count)
            {
                command[cp + 1] = command[cp + 1] + ";" + jar;
            }

            // loadBuiltin refuses to run unless assertions are enabled.
            if (!command.Contains("-ea"))
            {
                command.Insert(main, "-ea");
            }
            return true;
        }

        private static int Launch(List<string> command, string[] extraArgs)
        {
            string java = command[0];
            IEnumerable<string> rest = command.Skip(1).Concat(extraArgs ?? new string[0]);

            var psi = new ProcessStartInfo(java, BuildArguments(rest))
            {
                UseShellExecute = false,
                WorkingDirectory = Path.GetDirectoryName(java) ?? Environment.CurrentDirectory
            };

            Log("java=" + java);
            Log("main=" + WrapperMainClass);

            using (Process p = Process.Start(psi))
            {
                if (p == null) return 1;
                p.WaitForExit();
                Log("exit=" + p.ExitCode);
                return p.ExitCode;
            }
        }

        private static string BuildArguments(IEnumerable<string> args)
        {
            var sb = new StringBuilder();
            foreach (string a in args)
            {
                if (sb.Length > 0) sb.Append(' ');
                if (a.IndexOf(' ') >= 0 && !a.StartsWith("\""))
                {
                    sb.Append('"').Append(a.TrimEnd('\\')).Append('"');
                }
                else
                {
                    sb.Append(a);
                }
            }
            return sb.ToString();
        }

        /// <summary>Runs the untouched launcher we displaced, so a failure never blocks play.</summary>
        private static int RunOriginal(string[] args)
        {
            string original = Path.Combine(
                Path.GetDirectoryName(typeof(Program).Assembly.Location) ?? ".", "RuneLite-original.exe");

            if (!File.Exists(original))
            {
                Log("RuneLite-original.exe is missing - nothing to fall back to");
                return 1;
            }

            var psi = new ProcessStartInfo(original, BuildArguments(args ?? new string[0]))
            {
                UseShellExecute = false,
                WorkingDirectory = Path.GetDirectoryName(original)
            };

            using (Process p = Process.Start(psi))
            {
                if (p == null) return 1;
                p.WaitForExit();
                return p.ExitCode;
            }
        }

        private static void Log(string message)
        {
            try
            {
                _log?.WriteLine("[shim] " + message);
            }
            catch
            {
                // Logging must never take the launcher down.
            }
        }
    }
}
