package dev.linkisland.app;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolve only a user's failed XHS short-link tap; never called by clipboard monitoring. */
final class XhsLinks {
    private static final Set<String> SHORT_HOSTS = Set.of("xhslink.cn", "xhslink.com");
    private static final Set<String> NOTE_HOSTS = Set.of("www.xiaohongshu.com", "xiaohongshu.com");
    private static final Pattern NOTE = Pattern.compile("^/(?:discovery/item|explore)/([a-fA-F0-9]{24})/?$");
    interface Connections { HttpURLConnection open(URL url) throws IOException; }

    static boolean supports(String url, String pkg) {
        return "com.xingin.xhs".equals(pkg) && safe(url, SHORT_HOSTS);
    }
    private static boolean safe(String url, Set<String> hosts) {
        if (!LinkRules.isWebUrl(url)) return false;
        URI uri = URI.create(url);
        return "https".equalsIgnoreCase(uri.getScheme()) && (uri.getPort() == -1 || uri.getPort() == 443)
            && hosts.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT));
    }
    static String resolve(String original) throws IOException {
        return resolve(original, url -> (HttpURLConnection) url.openConnection());
    }
    static String resolve(String original, Connections connections) throws IOException {
        if (!safe(original, SHORT_HOSTS)) throw new IOException("Unsupported short-link origin");
        String current = original;
        Set<String> visited = new HashSet<>();
        long deadline = System.nanoTime() + 8_000_000_000L;
        for (int hop = 0; hop < 4; hop++) {
            if (!visited.add(current)) throw new IOException("Redirect loop");
            HttpURLConnection connection = connections.open(URI.create(current).toURL());
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setConnectTimeout(remaining(deadline));
                connection.setReadTimeout(remaining(deadline));
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36");
                connection.connect();
                connection.setReadTimeout(remaining(deadline));
                int status = connection.getResponseCode();
                String location = connection.getHeaderField("Location");
                if (!Set.of(301, 302, 303, 307, 308).contains(status) || location == null)
                    throw new IOException("No supported redirect");
                String next;
                try { next = URI.create(current).resolve(location).toString(); }
                catch (IllegalArgumentException e) { throw new IOException("Invalid redirect"); }
                if (safe(next, NOTE_HOSTS)) return next; // Keep the raw signed query; do not fetch the note body.
                if (!safe(next, SHORT_HOSTS)) throw new IOException("Unrelated redirect destination");
                current = next;
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many redirects");
    }
    private static int remaining(long deadline) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
        long ms = (deadline - System.nanoTime()) / 1_000_000;
        if (ms <= 0) throw new SocketTimeoutException();
        return (int) Math.min(4000, ms);
    }
    static String nativeNote(String url) {
        if (!safe(url, NOTE_HOSTS)) return null;
        URI uri = URI.create(url);
        Matcher match = NOTE.matcher(uri.getPath());
        if (!match.matches()) return null;
        return "xhsdiscover://item/" + match.group(1) + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }
}
