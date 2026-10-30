package dev.linkisland.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Prefs {
    static final int DEFAULT_RULES_REVISION = 3;
    static SharedPreferences get(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    static String rules(Context c) { return get(c).getString("rules", LinkRules.DEFAULTS); }
    static void ensureDefaultRules(Context c) {
        SharedPreferences p = get(c);
        int revision = p.getInt("default_rules_revision", 0);
        if (revision >= DEFAULT_RULES_REVISION) return;
        try {
            String rules = p.getString("rules", LinkRules.DEFAULTS);
            if (p.contains("rules")) {
                if (revision == 0) rules = LinkRules.addMissingDefaults(rules);
                else if (revision == 1) rules = LinkRules.addPinduoduoDefault(rules);
                rules = LinkRules.addCloudDefaults(rules);
            }
            p.edit().putString("rules", rules).putInt("default_rules_revision", DEFAULT_RULES_REVISION).apply();
        } catch (Exception e) { status(c, "默认规则更新失败 · 已保留原规则"); }
    }
    static int promptSeconds(Context c) { return PromptDuration.valid(get(c).getInt("prompt_seconds", 30)); }
    static void status(Context c, String status) {
        // Diagnostics intentionally exclude clipboard content, URLs and exception messages.
        String time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        get(c).edit().putString("status", time + "  " + status).apply();
    }
}
