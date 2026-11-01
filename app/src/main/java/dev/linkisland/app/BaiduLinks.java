package dev.linkisland.app;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The official mobile share launcher removes the /s/1 prefix before passing surl. */
final class BaiduLinks {
    private static final Pattern SHARE = Pattern.compile("^/s/1([A-Za-z0-9_-]{4,128})/?$");
    static String nativeTarget(String url, String pkg, String sharedCode) {
        if (!"com.baidu.netdisk".equals(pkg) || !LinkRules.isWebUrl(url)) return null;
        URI uri = URI.create(url);
        if (!Set.of("pan.baidu.com", "yun.baidu.com").contains(uri.getHost().toLowerCase(Locale.ROOT))
            || (uri.getPort() != -1 && uri.getPort() != 443 && uri.getPort() != 80)) return null;
        Matcher match = SHARE.matcher(uri.getRawPath());
        if (!match.matches()) return null;
        String code = PanShareCodes.extract(null, url);
        if (code == null && PanShareCodes.valid(sharedCode)) code = sharedCode;
        return "bdnetdisk://n/action.SHARE_LINK?m_n_v=8.3.0&surl=" + match.group(1) + "&surlType=1"
            + (code == null ? "" : "&pwd=" + code);
    }
}
