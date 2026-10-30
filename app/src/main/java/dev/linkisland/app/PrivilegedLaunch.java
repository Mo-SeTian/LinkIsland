package dev.linkisland.app;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Called only in the already authorized Root/Shizuku service, following a user's notification tap. */
final class PrivilegedLaunch {
    static boolean allowed(String target, String pkg) {
        if (target == null || target.length() > 4096 || pkg == null
            || !pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) return false;
        try {
            String scheme = URI.create(target).getScheme();
            return scheme != null && !List.of("javascript", "file", "content", "intent", "data")
                .contains(scheme.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) { return false; }
    }
    static boolean open(String target, String pkg, int userId) {
        if (!allowed(target, pkg) || userId < 0) return false;
        Process process = null;
        try {
            // Separate argv entries: clipboard text is never interpreted as shell code.
            process = new ProcessBuilder("/system/bin/am", "start", "--user", Integer.toString(userId),
                "-a", "android.intent.action.VIEW", "-c", "android.intent.category.BROWSABLE",
                "-d", target, "-p", pkg).redirectErrorStream(true).start();
            if (!process.waitFor(8, TimeUnit.SECONDS)) return false;
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[512];
            int count;
            while (bytes.size() < 4096 && (count = process.getInputStream().read(chunk, 0, Math.min(chunk.length, 4096 - bytes.size()))) > 0)
                bytes.write(chunk, 0, count);
            String output = bytes.toString(java.nio.charset.StandardCharsets.UTF_8.name());
            for (String line : output.split("\\R")) if (line.startsWith("Error") || line.contains("Exception")) return false;
            return process.exitValue() == 0;
        } catch (Exception e) { return false; }
        finally { if (process != null) process.destroy(); }
    }
}
