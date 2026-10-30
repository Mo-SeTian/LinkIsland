package dev.linkisland.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java rule engine. First matching rule wins; unmatched URLs are ignored. */
public final class LinkRules {
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"'，。；！？、【】]+", Pattern.CASE_INSENSITIVE);
    static final String BROWSER_PATTERN = "(?i)^https?://";
    public static final String DEFAULTS = """
[
    {"name":"GitHub","type":"domain","pattern":"github.com","package":"com.github.android","replacement":"","enabled":true},
    {"name":"哔哩哔哩","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:bilibili\\\\.com|b23\\\\.tv)(?::[0-9]+)?(?:[/?#]|$)","package":"tv.danmaku.bili","replacement":"","enabled":true,"description":"bilibili.com / b23.tv"},
    {"name":"知乎","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:zhihu\\\\.com)(?::[0-9]+)?(?:[/?#]|$)","package":"com.zhihu.android","replacement":"","enabled":true,"description":"zhihu.com"},
    {"name":"小红书","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:xiaohongshu\\\\.com|xhslink\\\\.com|xhslink\\\\.cn)(?::[0-9]+)?(?:[/?#]|$)","package":"com.xingin.xhs","replacement":"","enabled":true,"description":"xiaohongshu.com / xhslink.com / xhslink.cn"},
    {"name":"抖音","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:douyin\\\\.com|iesdouyin\\\\.com)(?::[0-9]+)?(?:[/?#]|$)","package":"com.ss.android.ugc.aweme","replacement":"","enabled":true,"description":"douyin.com / iesdouyin.com"},
    {"name":"微博","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:weibo\\\\.com|weibo\\\\.cn)(?::[0-9]+)?(?:[/?#]|$)","package":"com.sina.weibo","replacement":"","enabled":true,"description":"weibo.com / weibo.cn"},
    {"name":"淘宝 / 天猫","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:taobao\\\\.com|tmall\\\\.com|tb\\\\.cn)(?::[0-9]+)?(?:[/?#]|$)","package":"com.taobao.taobao","replacement":"","enabled":true,"description":"taobao.com / tmall.com / tb.cn"},
    {"name":"京东","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:jd\\\\.com)(?::[0-9]+)?(?:[/?#]|$)","package":"com.jingdong.app.mall","replacement":"","enabled":true,"description":"jd.com"},
    {"name":"酷安","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:coolapk\\\\.com)(?::[0-9]+)?(?:[/?#]|$)","package":"com.coolapk.market","replacement":"","enabled":true,"description":"coolapk.com"},
    {"name":"豆瓣","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:douban\\\\.com)(?::[0-9]+)?(?:[/?#]|$)","package":"com.douban.frodo","replacement":"","enabled":true,"description":"douban.com"},
    {"name":"网易云音乐","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:music\\\\.163\\\\.com)(?::[0-9]+)?(?:[/?#]|$)","package":"com.netease.cloudmusic","replacement":"","enabled":true,"description":"music.163.com"},
    {"name":"YouTube","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:youtube\\\\.com|youtu\\\\.be)(?::[0-9]+)?(?:[/?#]|$)","package":"com.google.android.youtube","replacement":"","enabled":true,"description":"youtube.com / youtu.be"},
    {"name":"拼多多","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:yangkeduo\\\\.com|pinduoduo\\\\.com)(?::[0-9]+)?(?:[/?#]|$)","package":"com.xunmeng.pinduoduo","replacement":"","enabled":true,"description":"yangkeduo.com / pinduoduo.com"},
    {"name":"阿里云盘","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:aliyundrive\\\\.com|alipan\\\\.com)(?::[0-9]+)?(?:[/?#]|$)","package":"com.alicloud.databox","replacement":"","enabled":true,"description":"aliyundrive.com / alipan.com"},
    {"name":"百度网盘","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:pan\\\\.baidu\\\\.com|yun\\\\.baidu\\\\.com)(?::[0-9]+)?(?:[/?#]|$)","package":"com.baidu.netdisk","replacement":"","enabled":true,"description":"pan.baidu.com / yun.baidu.com"},
    {"name":"蓝奏云","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:lanzou\\\\.com|lanzoui\\\\.com|lanzoux\\\\.com|lanzous\\\\.com|lanzoub\\\\.com)(?::[0-9]+)?(?:[/?#]|$)","package":"","replacement":"","enabled":true,"description":"lanzou.com / lanzoui.com / lanzoux.com / lanzous.com / lanzoub.com · 浏览器"},
    {"name":"蓝奏云优享版","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:ilanzou\\\\.com)(?::[0-9]+)?(?:[/?#]|$)","package":"com.ilanzou.app.disk","replacement":"","enabled":true,"description":"ilanzou.com"},
    {"name":"123 云盘","type":"regex","pattern":"(?i)^https?://(?:[a-z0-9-]+\\\\.)*(?:123pan\\\\.com|123pan\\\\.cn|123684\\\\.com|123865\\\\.com)(?::[0-9]+)?(?:[/?#]|$)","package":"com.mfcloudcalculate.networkdisk","replacement":"","enabled":true,"description":"123pan.com / 123pan.cn / 123684.com / 123865.com"},
    {"name":"网页浏览器","type":"regex","pattern":"(?i)^https?://","package":"","replacement":"","enabled":true,"description":"HTTP / HTTPS · 建议放在最后"}
]
        """;

    public static String extract(String text) {
        if (text == null || text.length() > 65536) return null;
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            String url = matcher.group();
            while (!url.isEmpty() && ".,;!?:）".indexOf(url.charAt(url.length() - 1)) >= 0)
                url = url.substring(0, url.length() - 1);
            while (url.endsWith(")") && count(url, ')') > count(url, '('))
                url = url.substring(0, url.length() - 1);
            while (url.endsWith("]") && count(url, ']') > count(url, '['))
                url = url.substring(0, url.length() - 1);
            if (isWebUrl(url)) return url;
        }
        return null;
    }

    private static long count(String text, char c) { return text.chars().filter(v -> v == c).count(); }

    public static boolean isWebUrl(String url) {
        if (url == null) return false;
        try {
            URI uri = URI.create(url);
            return url.length() <= 4096 && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }

    public static final class Rule {
        public final String name, type, pattern, packageName, replacement;
        public final boolean enabled;
        private final Pattern regex;
        public Rule(JSONObject json) {
            name = json.optString("name", "自定义规则").trim();
            type = json.optString("type", "contains");
            pattern = json.optString("pattern").trim();
            packageName = json.optString("package").trim();
            replacement = json.optString("replacement").trim();
            enabled = json.optBoolean("enabled", true);
            if (pattern.isEmpty()) throw new IllegalArgumentException("匹配内容不能为空");
            if (!List.of("domain", "contains", "regex").contains(type)) throw new IllegalArgumentException("未知匹配方式");
            if (!packageName.isEmpty() && !packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))
                throw new IllegalArgumentException("App 包名格式不正确");
            if (type.equals("domain") && !pattern.matches("[A-Za-z0-9.-]+"))
                throw new IllegalArgumentException("域名只填 github.com，不含协议和路径");
            regex = type.equals("regex") ? Pattern.compile(pattern) : null;
        }
        boolean matches(String url) {
            if (!enabled) return false;
            return switch (type) {
                case "domain" -> pattern.equalsIgnoreCase(URI.create(url).getHost());
                case "regex" -> regex.matcher(url).find();
                default -> url.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT));
            };
        }
        String rewrite(String url) {
            String result = replacement.isEmpty() ? url
                : regex != null ? regex.matcher(url).replaceFirst(replacement) : replacement.replace("{url}", url);
            URI uri = URI.create(result);
            String scheme = uri.getScheme();
            if (result.length() > 4096 || scheme == null || List.of("javascript", "file", "content", "intent", "data").contains(scheme.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("不支持的目标链接类型");
            if (packageName.isEmpty() && !isWebUrl(result))
                throw new IllegalArgumentException("自定义 scheme 必须指定目标 App");
            return result;
        }
    }

    public static List<Rule> parse(String json) throws Exception {
        JSONArray array = new JSONArray(json);
        if (array.length() > 100) throw new IllegalArgumentException("测试版最多支持 100 条规则");
        List<Rule> rules = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) rules.add(new Rule(array.getJSONObject(i)));
        return rules;
    }

    /** Preserve existing objects and their relative priority, including disabled ones. */
    static String addMissingDefaults(String existing) throws Exception { return addMissingDefaults(existing, 1, 13); }
    static String addPinduoduoDefault(String existing) throws Exception { return addMissingDefaults(existing, 12, 13); }
    static String addCloudDefaults(String existing) throws Exception { return addMissingDefaults(existing, 13, new JSONArray(DEFAULTS).length()); }
    private static String addMissingDefaults(String existing, int start, int end) throws Exception {
        parse(existing);
        JSONArray result = new JSONArray(existing), defaults = new JSONArray(DEFAULTS);
        java.util.Set<String> matches = new java.util.HashSet<>();
        for (int i = 0; i < result.length(); i++) {
            JSONObject rule = result.getJSONObject(i);
            matches.add(rule.optString("type", "contains") + "\n" + rule.optString("pattern"));
        }
        // GitHub existed before this revision: if absent, the user already deleted it.
        for (int i = start; i < end && result.length() < 100; i++) {
            JSONObject rule = defaults.getJSONObject(i);
            if (matches.add(rule.getString("type") + "\n" + rule.getString("pattern")))
                result = new JSONArray(appendRule(result.toString(), rule));
        }
        return result.toString();
    }

    /** Reorder original objects so edits, disabled state and metadata survive a drag. */
    static String reorder(String existing, List<Integer> order) throws Exception {
        JSONArray source = new JSONArray(existing), result = new JSONArray();
        if (order.size() != source.length() || new java.util.HashSet<>(order).size() != source.length())
            throw new IllegalArgumentException("规则顺序不完整");
        for (int index : order) result.put(source.getJSONObject(index));
        return result.toString();
    }

    static String appendRule(String existing, JSONObject rule) throws Exception {
        JSONArray source = new JSONArray(existing), result = new JSONArray();
        boolean inserted = false;
        for (int i = 0; i < source.length(); i++) {
            JSONObject current = source.getJSONObject(i);
            if (!inserted && current.optString("type").equals("regex")
                && current.optString("pattern").equals(BROWSER_PATTERN) && current.optString("package").isEmpty()) {
                result.put(rule); inserted = true;
            }
            result.put(current);
        }
        if (!inserted) result.put(rule);
        parse(result.toString());
        return result.toString();
    }

    public static final class Route {
        public final String original, target, packageName, label, ruleName;
        public final int ruleIndex;
        Route(String original, String target, String packageName, String label, String ruleName, int ruleIndex) {
            this.original = original; this.target = target; this.packageName = packageName; this.label = label; this.ruleName = ruleName; this.ruleIndex = ruleIndex;
        }
    }
    public static Route resolve(String url, List<Rule> rules) {
        if (!isWebUrl(url)) throw new IllegalArgumentException("请输入有效的 HTTP / HTTPS 链接");
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            if (rule.matches(url)) return new Route(url, rule.rewrite(url), rule.packageName,
                rule.packageName.isEmpty() ? "默认浏览器" : rule.name, rule.name, i);
        }
        return null;
    }
}
