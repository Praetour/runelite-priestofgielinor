package com.vowtaker.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.RuneLite;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Self-updater for sideloaded builds. Polls a release manifest, stages a newer jar, and swaps it
 * in once the client exits so the next launch runs the new build.
 *
 * <p>This whole package is sideload-only and must be deleted before any Plugin Hub submission:
 * the hub distributes and updates plugins itself, and will reject code that fetches and installs
 * its own jar.
 */
@Singleton
public class VowTakerUpdater
{
    /** Release manifest. The /latest/ path always resolves to the newest release. */
    private static final String MANIFEST_URL =
        "https://github.com/Praetour/runelite-priestofgielinor/releases/latest/download/update-manifest.json";

    private static final String JAR_PREFIX = "VowTaker-";
    private static final long CONNECT_TIMEOUT_SEC = 15;
    private static final long READ_TIMEOUT_SEC = 120;

    @Inject
    private OkHttpClient httpClient;

    private volatile String pendingVersion;

    /** Version of the running build, read from the packaged plugin manifest. */
    public static String currentVersion()
    {
        try (InputStream in = VowTakerUpdater.class.getResourceAsStream("/runelite-plugin.properties"))
        {
            if (in == null) return "0.0.0";
            Properties p = new Properties();
            p.load(in);
            String v = p.getProperty("version");
            return v == null || v.trim().isEmpty() ? "0.0.0" : v.trim();
        }
        catch (IOException e)
        {
            return "0.0.0";
        }
    }

    public boolean hasPendingUpdate()
    {
        return pendingVersion != null;
    }

    public String getPendingVersion()
    {
        return pendingVersion;
    }

    /**
     * Checks for a newer release and stages it. Runs on the caller's thread, so callers should
     * hand this to a background executor. Never throws.
     *
     * @param notify receives a chat-friendly status line, or is left alone when already current
     */
    public void checkForUpdate(java.util.function.Consumer<String> notify)
    {
        try
        {
            clearStaleStaging();

            JsonObject manifest = fetchManifest();
            if (manifest == null) return;

            String latest = optString(manifest, "version");
            String url = optString(manifest, "url");
            if (latest == null || url == null) return;

            String current = currentVersion();
            if (compareVersions(latest, current) <= 0) return;

            Path staged = stagingDir().resolve(JAR_PREFIX + latest + ".jar");
            if (Files.exists(staged))
            {
                pendingVersion = latest;
                notify.accept("VowTaker: update " + latest + " is ready \u2014 restart your client to apply.");
                return;
            }

            Path temp = Files.createTempFile("vowtaker-update", ".jar");
            if (!download(url, temp))
            {
                Files.deleteIfExists(temp);
                return;
            }

            String expected = optString(manifest, "sha256");
            if (expected != null && !expected.equalsIgnoreCase(sha256(temp)))
            {
                Files.deleteIfExists(temp);
                notify.accept("VowTaker: update " + latest + " failed its checksum and was discarded.");
                return;
            }

            Files.createDirectories(staged.getParent());
            Files.move(temp, staged, StandardCopyOption.REPLACE_EXISTING);
            pendingVersion = latest;

            String notes = optString(manifest, "notes");
            notify.accept("VowTaker: update " + latest + " downloaded \u2014 it applies when you close the client."
                + (notes == null || notes.isEmpty() ? "" : " (" + notes + ")"));
        }
        catch (Exception e)
        {
            // Updating is best-effort; never let it interfere with play.
        }
    }

    /**
     * Hands the swap to a detached helper, because a running JVM holds its own plugin jar open on
     * Windows and cannot replace it in-process. The helper waits for the lock to drop, then swaps.
     */
    public void scheduleSwapOnExit()
    {
        if (pendingVersion == null) return;
        try
        {
            Path staged = stagingDir().resolve(JAR_PREFIX + pendingVersion + ".jar");
            if (!Files.exists(staged)) return;

            Path script = stagingDir().resolve("apply-update.ps1");
            Files.write(script, swapScript(staged, pendingVersion).getBytes(StandardCharsets.UTF_8));

            new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-File", script.toString())
                .directory(stagingDir().toFile())
                .start();
        }
        catch (Exception e)
        {
            // Swap will be retried on the next shutdown that sees a staged jar.
        }
    }

    /** Drops a staged jar once its version is the one actually running. */
    private void clearStaleStaging() throws IOException
    {
        Path dir = stagingDir();
        if (!Files.isDirectory(dir)) return;

        String current = currentVersion();
        File[] files = dir.toFile().listFiles((d, name) -> name.startsWith(JAR_PREFIX) && name.endsWith(".jar"));
        if (files == null) return;
        for (File f : files)
        {
            String version = f.getName().substring(JAR_PREFIX.length(), f.getName().length() - 4);
            if (compareVersions(version, current) <= 0)
            {
                Files.deleteIfExists(f.toPath());
            }
        }
    }

    private JsonObject fetchManifest() throws IOException
    {
        Request request = new Request.Builder()
            .url(MANIFEST_URL)
            .header("Cache-Control", "no-cache")
            .build();

        try (Response response = timeoutClient().newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null) return null;
            try (InputStreamReader reader = new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))
            {
                return new JsonParser().parse(reader).getAsJsonObject();
            }
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    private boolean download(String url, Path target) throws IOException
    {
        Request request = new Request.Builder().url(url).build();
        try (Response response = timeoutClient().newCall(request).execute())
        {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) return false;
            try (InputStream in = body.byteStream())
            {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }
    }

    private OkHttpClient timeoutClient()
    {
        return httpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build();
    }

    private static String sha256(Path file) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(file))
        {
            int read;
            while ((read = in.read(buffer)) > 0)
            {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest())
        {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    /** Numeric dot-separated compare. Returns &gt;0 when {@code a} is newer than {@code b}. */
    public static int compareVersions(String a, String b)
    {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++)
        {
            int l = i < left.length ? parseIntSafe(left[i]) : 0;
            int r = i < right.length ? parseIntSafe(right[i]) : 0;
            if (l != r) return Integer.compare(l, r);
        }
        return 0;
    }

    private static int parseIntSafe(String s)
    {
        try
        {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private static String optString(JsonObject o, String key)
    {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static Path stagingDir() throws IOException
    {
        File base = RuneLite.RUNELITE_DIR != null
            ? RuneLite.RUNELITE_DIR
            : new File(System.getProperty("user.home"), ".runelite");
        Path dir = base.toPath().resolve("vowtaker").resolve("updates");
        Files.createDirectories(dir);
        return dir;
    }

    private static Path pluginDir() throws IOException
    {
        File base = RuneLite.RUNELITE_DIR != null
            ? RuneLite.RUNELITE_DIR
            : new File(System.getProperty("user.home"), ".runelite");
        Path dir = base.toPath().resolve("sideloaded-plugins");
        Files.createDirectories(dir);
        return dir;
    }

    private static String swapScript(Path staged, String version) throws IOException
    {
        String target = pluginDir().toString();
        return String.join("\n",
            "$ErrorActionPreference = 'SilentlyContinue'",
            "$target = '" + escape(target) + "'",
            "$staged = '" + escape(staged.toString()) + "'",
            "# Keep the existing jar's filename. A launcher shim may reference it by exact name,",
            "# so renaming it on update would leave that shim pointing at a file that is gone.",
            "$existing = @(Get-ChildItem $target -Filter 'VowTaker*.jar' -File)",
            "$name = if ($existing.Count -gt 0) { $existing[0].Name } else { 'VowTaker-" + escape(version) + ".jar' }",
            "# Poll until the old jar is no longer held open, i.e. the client has exited.",
            "for ($i = 0; $i -lt 120; $i++) {",
            "    Start-Sleep -Seconds 1",
            "    $locked = $false",
            "    foreach ($f in Get-ChildItem $target -Filter 'VowTaker*.jar' -File) {",
            "        try { $s = [IO.File]::Open($f.FullName, 'Open', 'ReadWrite', 'None'); $s.Close() }",
            "        catch { $locked = $true; break }",
            "    }",
            "    if ($locked) { continue }",
            "    Get-ChildItem $target -Filter 'VowTaker*.jar' -File | Remove-Item -Force",
            "    Move-Item $staged (Join-Path $target $name) -Force",
            "    break",
            "}",
            "Remove-Item $MyInvocation.MyCommand.Path -Force");
    }

    private static String escape(String s)
    {
        return s.replace("'", "''");
    }
}
