package dev.linkisland.app;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/** Friendly website input stored in the existing domain/regex rule format. */
final class WebsiteRules {
    private static final String PREFIX = "(?i)^https?://";
    private static final String SUBDOMAINS = "(?:[a-z0-9-]+\\.)*";
    private static final String SUFFIX = ")(?::[0-9]+)?(?:[/?#]|$)";

    static List<String> hosts(String input) {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        for (String item : input.trim().split("[\\s,，;；]+")) {
            if (item.isEmpty()) continue;
            try {
                URI uri = URI.create(item.contains("://") ? item : "https://" + item);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getRawUserInfo() != null || uri.getHost() == null || !uri.getHost().matches("[A-Za-z0-9.-]+"))
                    throw new IllegalArgumentException();
                hosts.add(uri.getHost().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("请填写网址或域名，例如 github.com；多个网站用逗号或换行分开");
            }
        }
        if (hosts.isEmpty()) throw new IllegalArgumentException("请先填写一个网址或域名，例如 github.com");
        return new ArrayList<>(hosts);
    }

    static void write(JSONObject rule, String input, boolean subdomains) throws org.json.JSONException {
        List<String> hosts = hosts(input);
        if (hosts.size() == 1 && !subdomains) {
            rule.put("type", "domain").put("pattern", hosts.get(0));
        } else {
            List<String> escaped = new ArrayList<>();
            for (String host : hosts) escaped.add(host.replace(".", "\\."));
            rule.put("type", "regex").put("pattern", PREFIX + (subdomains ? SUBDOMAINS : "")
                + "(?:" + String.join("|", escaped) + SUFFIX);
        }
    }

    static final class Websites {
        final String text;
        final boolean subdomains;
        Websites(List<String> hosts, boolean subdomains) {
            this.text = String.join("\n", hosts); this.subdomains = subdomains;
        }
    }

    /** Decode only our literal host grammar; arbitrary regex must stay in advanced mode. */
    static Websites read(JSONObject rule) {
        String type = rule.optString("type"), pattern = rule.optString("pattern");
        try {
            if (type.equals("domain")) return new Websites(hosts(pattern), false);
            if (!type.equals("regex")) return null;
            boolean subdomains = pattern.startsWith(PREFIX + SUBDOMAINS);
            String start = PREFIX + (subdomains ? SUBDOMAINS : "") + "(?:";
            if (!pattern.startsWith(start) || !pattern.endsWith(SUFFIX)) return null;
            String body = pattern.substring(start.length(), pattern.length() - SUFFIX.length());
            List<String> result = new ArrayList<>();
            for (String part : body.split("\\|", -1)) {
                String host = part.replace("\\.", ".");
                List<String> normalized = hosts(host);
                if (normalized.size() != 1 || !host.equals(normalized.get(0)) || !part.equals(host.replace(".", "\\."))) return null;
                result.add(host);
            }
            return new Websites(result, subdomains);
        } catch (IllegalArgumentException e) { return null; }
    }
}
