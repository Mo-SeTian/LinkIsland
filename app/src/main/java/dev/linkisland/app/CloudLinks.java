package dev.linkisland.app;

import android.content.Context;
import android.content.pm.PackageManager;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline share routes verified against the providers' public mobile launchers. */
final class CloudLinks {
    private static final Pattern SHARE = Pattern.compile("^/s/([A-Za-z0-9_-]{4,128})/?$");
    private static final Set<String> ALI = Set.of("alipan.com", "aliyundrive.com");
    private static final Set<String> PAN123 = Set.of("123pan.com", "123pan.cn", "123684.com", "123865.com");
    private static final String QUARK = "com.quark.browser", QUARK_DISK = "com.quark.clouddrive";

    static String nativeTarget(String url, String pkg, String sharedCode) {
        URI uri = share(url);
        if (uri == null) return null;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        Matcher match = SHARE.matcher(uri.getRawPath());
        if (!match.matches()) return null;
        String id = match.group(1);
        String code = PanShareCodes.extract(null, url);
        if (code == null && PanShareCodes.valid(sharedCode)) code = sharedCode;
        if ("com.alicloud.databox".equals(pkg) && knownHost(host, ALI))
            return "smartdrive://share/browse?shareId=" + id + (code == null ? "" : "&sharePwd=" + code) + "&source_type=scheme";
        if ("com.mfcloudcalculate.networkdisk".equals(pkg) && knownHost(host, PAN123))
            return "pan://umeng.com/share/list?action=share_list&shareKey=" + id + (code == null ? "" : "&sharePwd=" + code)
                + "&_sdk_=umeng&_linkid_=usr1ke01qbgngpn9&_bizType_=ushare";
        return null;
    }

    static String targetPackage(Context c, String original, String target, String pkg) {
        // A custom rewrite and an explicitly available target always keep their original meaning.
        if (!original.equals(target)) return pkg;
        return targetPackage(target, pkg, candidate -> {
            try { return c.getPackageManager().getApplicationInfo(candidate, 0).enabled; }
            catch (PackageManager.NameNotFoundException e) { return false; }
        });
    }

    static String targetPackage(String url, String pkg, Predicate<String> installed) {
        if (!QUARK.equals(pkg) && !QUARK_DISK.equals(pkg)) return pkg;
        URI uri = share(url);
        if (uri == null || !knownHost(uri.getHost().toLowerCase(Locale.ROOT), Set.of("pan.quark.cn")) || installed.test(pkg)) return pkg;
        String alternative = QUARK.equals(pkg) ? QUARK_DISK : QUARK;
        return installed.test(alternative) ? alternative : pkg;
    }

    private static URI share(String url) {
        if (!LinkRules.isWebUrl(url)) return null;
        URI uri = URI.create(url);
        if ((uri.getPort() != -1 && uri.getPort() != 443 && uri.getPort() != 80)
            || !SHARE.matcher(uri.getRawPath()).matches()) return null;
        return uri;
    }
    private static boolean knownHost(String host, Set<String> hosts) {
        for (String known : hosts) if (host.equals(known) || host.equals("www." + known)) return true;
        return false;
    }
}
