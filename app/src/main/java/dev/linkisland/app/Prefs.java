package dev.linkisland.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Prefs {
    static SharedPreferences get(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    static String rules(Context c) { return get(c).getString("rules", LinkRules.DEFAULTS); }
    static void ensureDefaultRules(Context c) {
        SharedPreferences p = get(c);
        if (p.getInt("default_rules_revision", 0) >= 1) return;
        try {
            String rules = p.contains("rules") ? LinkRules.addMissingDefaults(p.getString("rules", "[]")) : LinkRules.DEFAULTS;
            p.edit().putString("rules", rules).putInt("default_rules_revision", 1).apply();
        } catch (Exception e) { status(c, "默认规则更新失败 · 已保留原规则"); }
    }
    static void status(Context c, String status) {
        // Diagnostics intentionally exclude clipboard content, URLs and exception messages.
        String time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        get(c).edit().putString("status", time + "  " + status).apply();
    }
}
