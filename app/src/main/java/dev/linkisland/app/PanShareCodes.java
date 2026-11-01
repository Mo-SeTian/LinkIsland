package dev.linkisland.app;

import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Offline access-code recognition; never treats arbitrary website passwords as share codes. */
final class PanShareCodes {
    private static final Set<String> HOSTS = Set.of("aliyundrive.com", "alipan.com", "pan.baidu.com", "yun.baidu.com",
        "lanzou.com", "lanzoui.com", "lanzoux.com", "lanzous.com", "lanzoub.com", "ilanzou.com",
        "123pan.com", "123pan.cn", "123684.com", "123865.com", "pan.quark.cn", "cloud.189.cn",
        "pan.xunlei.com", "115.com", "115cdn.com");
    private static final Set<String> PARAMETERS = Set.of("pwd", "password", "passcode", "accesscode", "access_code");
    private static final Pattern URL_START = Pattern.compile("https?://", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9]{4,8}");
    private static final String VALUE = "\\s*(?:[:：=]|是|为|為)?\\s*[【\\[（(]?\\s*([A-Za-z0-9]{4,8})(?![A-Za-z0-9])";
    private static final Pattern EXPLICIT = Pattern.compile("(?:提取[码碼]|访问[码碼]|訪問[码碼]|提取密[码碼]|访问密[码碼]|訪問密[码碼])" + VALUE);
    private static final Pattern PASSWORD = Pattern.compile("密[码碼]" + VALUE);
    private static final Pattern OTHER_PASSWORD = Pattern.compile("(?:解\\s*[压壓]|[压壓]\\s*[缩縮]|登[录錄入])\\s*$");
    static boolean valid(String code) { return code != null && CODE.matcher(code).matches(); }
    static boolean supports(String url) {
        if (!LinkRules.isWebUrl(url)) return false;
        String host = URI.create(url).getHost().toLowerCase(Locale.ROOT);
        for (String known : HOSTS) if (host.equals(known) || host.endsWith("." + known)) return true;
        return false;
    }
    static String extract(String text, String url) {
        if (!supports(url)) return null;
        Set<String> codes = new LinkedHashSet<>();
        URI uri = URI.create(url);
        parameters(uri.getRawQuery(), codes);
        String fragment = uri.getRawFragment();
        if (fragment != null && fragment.contains("=")) parameters(fragment, codes);
        if (text != null && text.length() <= 65536) {
            Matcher links = URL_START.matcher(text);
            if (links.find() && !links.find()) {
                // Explicit extraction/access labels take precedence over generic passwords.
                Set<String> labelled = new LinkedHashSet<>();
                String outsideUrl = text.replace(url, "");
                collect(EXPLICIT, outsideUrl, labelled);
                if (labelled.isEmpty()) collect(PASSWORD, outsideUrl, labelled);
                codes.addAll(labelled);
            }
        }
        return codes.size() == 1 ? codes.iterator().next() : null;
    }
    private static void collect(Pattern pattern, String text, Set<String> codes) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (pattern == PASSWORD && OTHER_PASSWORD.matcher(text.substring(Math.max(0, matcher.start() - 16), matcher.start())).find()) continue;
            codes.add(matcher.group(1));
        }
    }
    private static void parameters(String query, Set<String> codes) {
        if (query == null) return;
        for (String entry : query.split("&")) {
            int split = entry.indexOf('=');
            if (split < 0) continue;
            try {
                String key = URLDecoder.decode(entry.substring(0, split), "UTF-8").toLowerCase(Locale.ROOT);
                String value = URLDecoder.decode(entry.substring(split + 1), "UTF-8");
                if (PARAMETERS.contains(key) && valid(value)) codes.add(value);
            } catch (IllegalArgumentException | java.io.UnsupportedEncodingException ignored) { }
        }
    }
}
