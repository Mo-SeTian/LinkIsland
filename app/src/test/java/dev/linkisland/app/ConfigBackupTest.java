package dev.linkisland.app;

import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ConfigBackupTest {
    private JSONObject document() throws Exception { return ConfigBackup.create(LinkRules.DEFAULTS, Map.of(), "test"); }
    @Test public void exportsEffectiveDefaultsAndExcludesRuntimeStateAndUnknownFields() throws Exception {
        JSONObject backup = ConfigBackup.create(LinkRules.DEFAULTS, Map.of(
            "mode", 1, "prompt_seconds", 15, "monitor_wanted", true, "prompt_active", true,
            "clipboard_diagnostics", "PRIVATE_FIXTURE", "unknown_api_key", "TEST_KEY_ONLY"), "test");
        JSONObject restored = ConfigBackup.parse(backup.toString()), settings = restored.getJSONObject("settings");
        assertEquals(7, settings.length());
        assertEquals(1, settings.getInt("mode")); assertEquals(15, settings.getInt("prompt_seconds"));
        assertTrue(settings.getBoolean("auto_restart")); assertTrue(settings.getBoolean("island"));
        assertFalse(settings.getBoolean("hide_recent"));
        for (String forbidden : new String[]{"monitor_wanted", "prompt_active", "PRIVATE_FIXTURE", "TEST_KEY_ONLY", "unknown_api_key"})
            assertFalse(backup.toString().contains(forbidden));
        assertEquals(19, restored.getJSONArray("rules").length());
    }
    @Test public void mergeKeepsLocalPriorityAndDisabledEditsAndAppendsOnlyNewMatches() throws Exception {
        JSONArray rules = document().getJSONArray("rules");
        JSONObject localRule = new JSONObject(rules.getJSONObject(0).toString()).put("name", "本机修改").put("enabled", false);
        String current = new JSONArray().put(localRule).toString();
        JSONArray merged = new JSONArray(ConfigBackup.importRules(current, rules, true));
        assertEquals(19, merged.length()); assertEquals("本机修改", merged.getJSONObject(0).getString("name"));
        assertFalse(merged.getJSONObject(0).getBoolean("enabled"));
        assertEquals(merged.toString(), ConfigBackup.importRules(merged.toString(), rules, true));
        assertEquals(rules.toString(), ConfigBackup.importRules(current, rules, false));
    }
    @Test public void ignoresUnknownImportedSettingsAndRuleFields() throws Exception {
        JSONObject d = document(); d.getJSONObject("settings").put("monitor_wanted", true);
        d.getJSONArray("rules").getJSONObject(0).put("extra_private_field", "TEST_VALUE_ONLY");
        String result = ConfigBackup.parse(d.toString()).toString();
        assertFalse(result.contains("monitor_wanted")); assertFalse(result.contains("TEST_VALUE_ONLY"));
    }
    @Test public void mergeRespectsCaseInsensitiveMatchingWhenKeepingDisabledLocalRules() throws Exception {
        JSONArray local = new JSONArray().put(new JSONObject().put("type", "domain").put("pattern", "GitHub.COM").put("enabled", false))
            .put(new JSONObject().put("type", "contains").put("pattern", "REPO").put("enabled", false));
        JSONArray imported = new JSONArray().put(new JSONObject().put("type", "domain").put("pattern", "github.com"))
            .put(new JSONObject().put("type", "contains").put("pattern", "repo"));
        JSONArray merged = new JSONArray(ConfigBackup.importRules(local.toString(), imported, true));
        assertEquals(2, merged.length());
        assertFalse(merged.getJSONObject(0).getBoolean("enabled"));
        assertFalse(merged.getJSONObject(1).getBoolean("enabled"));
    }
    @Test public void rejectsWrongFormatVersionTypesAndUnsafeRules() throws Exception {
        for (int caseId = 0; caseId < 7; caseId++) {
            JSONObject d = document();
            switch (caseId) {
                case 0 -> d.put("version", 2);
                case 1 -> d.getJSONObject("settings").put("auto_restart", "true");
                case 2 -> d.getJSONObject("settings").put("prompt_seconds", 0);
                case 3 -> d.getJSONObject("settings").put("mode", 2);
                case 4 -> d.getJSONObject("settings").put("browser", "not a package");
                case 5 -> d.getJSONArray("rules").getJSONObject(0).put("enabled", "false");
                default -> d.getJSONArray("rules").getJSONObject(0).put("replacement", "javascript:alert(1)");
            }
            assertThrows(Exception.class, () -> ConfigBackup.parse(d.toString()));
        }
        assertThrows(Exception.class, () -> ConfigBackup.parse("not JSON"));
        assertThrows(Exception.class, () -> ConfigBackup.parse("x".repeat(ConfigBackup.MAX_BYTES + 1)));
    }
    @Test public void rejectsOversizedMergesWithoutDroppingRules() throws Exception {
        JSONArray local = new JSONArray();
        for (int i = 0; i < 100; i++) local.put(new JSONObject().put("type", "domain").put("pattern", "example" + i + ".org"));
        JSONArray imported = document().getJSONArray("rules");
        assertThrows(IllegalArgumentException.class, () -> ConfigBackup.importRules(local.toString(), imported, true));
        assertEquals(100, local.length());
        assertEquals(19, new JSONArray(ConfigBackup.importRules(local.toString(), imported, false)).length());
    }
    @Test public void rejectsInvalidRegexTooManyRulesAndOversizedUtf8() throws Exception {
        JSONObject invalid = document();
        invalid.getJSONArray("rules").getJSONObject(0).put("type", "regex").put("pattern", "[");
        assertThrows(Exception.class, () -> ConfigBackup.parse(invalid.toString()));
        JSONObject large = document().put("padding", "字".repeat(ConfigBackup.MAX_BYTES / 2));
        assertThrows(IllegalArgumentException.class, () -> ConfigBackup.parse(large.toString()));
        JSONArray rules = new JSONArray();
        for (int i = 0; i < 101; i++) rules.put(new JSONObject().put("pattern", "test" + i));
        JSONObject excess = document().put("rules", rules);
        assertThrows(IllegalArgumentException.class, () -> ConfigBackup.parse(excess.toString()));
    }
}
