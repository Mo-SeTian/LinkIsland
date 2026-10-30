package dev.linkisland.app;

import java.net.URI;
import java.util.Locale;

final class PddLinks {
    static String nativeTarget(String url, String packageName) {
        if (!"com.xunmeng.pinduoduo".equals(packageName) || !LinkRules.isWebUrl(url)) return null;
        URI uri = URI.create(url);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!(host.equals("yangkeduo.com") || host.endsWith(".yangkeduo.com")
            || host.equals("pinduoduo.com") || host.endsWith(".pinduoduo.com"))) return null;
        // The official web launcher prefixes its page path with this native origin.
        String path = uri.getRawPath();
        return "pinduoduo://com.xunmeng.pinduoduo" + (path.isEmpty() ? "/" : path)
            + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
            + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
    }
}
