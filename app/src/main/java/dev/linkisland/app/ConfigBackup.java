package dev.linkisland.app;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Portable rules and ordinary preferences only; never runtime state, permissions or logs. */
final class ConfigBackup {
    static final int MAX_BYTES = 1024 * 1024;
    static final Set<String> BOOLEAN_KEYS = Set.of("island", "hide_recent", "auto_restart", "privileged_open");
    private static final String PACKAGE = "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+";

    static JSONObject create(String rules, Map<String, ?> preferences, String appVersion) throws Exception {
        JSONObject settings = new JSONObject();
        for (String key : BOOLEAN_KEYS) settings.put(key, preferences.containsKey(key) ? preferences.get(key)
            : key.equals("island") || key.equals("auto_restart"));
        settings.put("mode", preferences.containsKey("mode") ? preferences.get("mode") : 0);
        settings.put("browser", preferences.containsKey("browser") ? preferences.get("browser") : "");
        settings.put("prompt_seconds", preferences.containsKey("prompt_seconds") ? preferences.get("prompt_seconds") : 30);
        JSONObject backup = new JSONObject().put("format", "LinkIslandBackup").put("version", 1)
            .put("rules", new JSONArray(rules)).put("settings", settings);
        return parse(backup.toString()).put("appVersion", appVersion).put("createdAt", System.currentTimeMillis());
    }
    static JSONObject parse(String text) throws Exception {
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("备份文件过大");
        JSONObject document = new JSONObject(text);
        if (!"LinkIslandBackup".equals(document.optString("format")) || !(document.opt("version") instanceof Integer)
            || document.getInt("version") != 1) throw new IllegalArgumentException("不支持的备份格式或版本");
        JSONArray source = document.getJSONArray("rules"), rules = new JSONArray();
        if (source.length() > 100) throw new IllegalArgumentException("备份最多支持 100 条规则");
        for (int i = 0; i < source.length(); i++) {
            JSONObject raw = source.getJSONObject(i), rule = new JSONObject();
            if (!(raw.opt("pattern") instanceof String)) throw new IllegalArgumentException("规则缺少匹配内容");
            for (String key : new String[]{"name", "type", "pattern", "package", "replacement", "description"}) {
                if (!raw.has(key)) continue;
                if (!(raw.get(key) instanceof String)) throw new IllegalArgumentException("规则字段类型不正确");
                rule.put(key, raw.getString(key));
            }
            if (raw.has("enabled")) {
                if (!(raw.get("enabled") instanceof Boolean)) throw new IllegalArgumentException("规则启用状态不正确");
                rule.put("enabled", raw.getBoolean("enabled"));
            }
            if (rule.optString("replacement").trim().matches("(?is)^(javascript|file|content|intent|data):.*"))
                throw new IllegalArgumentException("规则包含不支持的目标协议");
            rules.put(rule);
        }
        LinkRules.parse(rules.toString());
        JSONObject rawSettings = document.getJSONObject("settings"), settings = new JSONObject();
        for (String key : BOOLEAN_KEYS) if (rawSettings.has(key)) {
            if (!(rawSettings.get(key) instanceof Boolean)) throw new IllegalArgumentException("设置字段类型不正确");
            settings.put(key, rawSettings.getBoolean(key));
        }
        for (String key : new String[]{"mode", "prompt_seconds"}) if (rawSettings.has(key)) {
            if (!(rawSettings.get(key) instanceof Integer)) throw new IllegalArgumentException("设置数值不正确");
            int value = rawSettings.getInt(key);
            if (key.equals("mode") ? value != 0 && value != 1 : PromptDuration.valid(value) != value)
                throw new IllegalArgumentException("设置数值超出范围");
            settings.put(key, value);
        }
        if (rawSettings.has("browser")) {
            if (!(rawSettings.get("browser") instanceof String)) throw new IllegalArgumentException("浏览器设置不正确");
            String browser = rawSettings.getString("browser");
            if (!browser.isEmpty() && !browser.equals("ask") && !browser.matches(PACKAGE))
                throw new IllegalArgumentException("浏览器包名不正确");
            settings.put("browser", browser);
        }
        return new JSONObject().put("format", "LinkIslandBackup").put("version", 1).put("rules", rules).put("settings", settings);
    }
    static String importRules(String current, JSONArray imported, boolean merge) throws Exception {
        if (!merge) { LinkRules.parse(imported.toString()); return imported.toString(); }
        JSONArray result = new JSONArray(current);
        Set<String> existing = new HashSet<>();
        for (int i = 0; i < result.length(); i++) existing.add(identity(result.getJSONObject(i)));
        for (int i = 0; i < imported.length(); i++) {
            JSONObject rule = imported.getJSONObject(i);
            if (existing.add(identity(rule))) result.put(rule);
        }
        if (result.length() > 100) throw new IllegalArgumentException("合并后超过 100 条规则，请选择覆盖或先精简规则");
        LinkRules.parse(result.toString());
        return result.toString();
    }
    private static String identity(JSONObject rule) {
        String type = rule.optString("type", "contains"), pattern = rule.optString("pattern").trim();
        if (!type.equals("regex")) pattern = pattern.toLowerCase(java.util.Locale.ROOT);
        return type + "\n" + pattern;
    }
}
