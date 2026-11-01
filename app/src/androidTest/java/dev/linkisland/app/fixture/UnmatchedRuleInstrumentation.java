package dev.linkisland.app.fixture;

import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import java.lang.reflect.Method;
import java.util.Map;
import org.json.JSONArray;

/** A nonmatching copy must neither post a prompt nor disturb a previous matching copy. */
public final class UnmatchedRuleInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Context c = getTargetContext();
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        SharedPreferences history = c.getSharedPreferences("jump_log", Context.MODE_PRIVATE);
        Map<String, ?> saved = p.getAll(), savedHistory = history.getAll();
        Bundle result = new Bundle();
        Method cancel = null;
        try {
            ClassLoader loader = c.getClassLoader();
            Method show = Class.forName("dev.linkisland.app.LinkNotifications", true, loader).getDeclaredMethod("show", Context.class, String.class);
            show.setAccessible(true);
            cancel = Class.forName("dev.linkisland.app.PromptSafety", true, loader).getDeclaredMethod("cancel", Context.class);
            cancel.setAccessible(true); cancel.invoke(null, c);
            awaitAbsent(c);
            p.edit().putString("rules", "[]").putBoolean("island", false).putInt("prompt_seconds", 30).commit();
            show.invoke(null, c, "https://example.org/unmatched?private=fixture");
            if (prompt(c) != null || p.getBoolean("prompt_active", false)) throw new AssertionError("Empty rules posted a prompt");
            JSONArray logs = new JSONArray(history.getString("groups", "[]"));
            if (!logs.getJSONObject(logs.length() - 1).getString("status").equals("skipped")) throw new AssertionError("Not marked skipped");
            p.edit().putString("rules", "[{\"name\":\"已命中\",\"type\":\"domain\",\"pattern\":\"example.org\"}]").commit();
            show.invoke(null, c, "https://example.org/matched");
            long postedBy = android.os.SystemClock.uptimeMillis() + 5000;
            while (prompt(c) == null && android.os.SystemClock.uptimeMillis() < postedBy) Thread.sleep(25);
            String id = p.getString("prompt_log_id", "");
            long deadline = p.getLong("prompt_deadline", 0), posted = prompt(c).getPostTime();
            show.invoke(null, c, "https://other.example/unmatched");
            if (prompt(c) == null || prompt(c).getPostTime() != posted || !p.getString("prompt_log_id", "").equals(id)
                || p.getLong("prompt_deadline", 0) != deadline) throw new AssertionError("Unmatched copy disturbed previous prompt");
            cancel.invoke(null, c);
            awaitAbsent(c);
            p.edit().putString("rules", "[{\"type\":\"regex\",\"pattern\":\"^https?://\",\"enabled\":false}]").commit();
            show.invoke(null, c, "https://example.org/disabled");
            if (prompt(c) != null || p.getBoolean("prompt_active", false)) throw new AssertionError("Disabled browser posted a prompt");
            result.putString("result", "PASS empty/disabled rules stay silent; unmatched copy preserves existing prompt identity and deadline");
        } catch (Throwable error) { result.putString("test_error", error.getClass().getSimpleName()
            + (error instanceof AssertionError ? " · " + error.getMessage() : "")); }
        finally {
            try { if (cancel != null) cancel.invoke(null, c); } catch (Exception ignored) { }
            SharedPreferences.Editor edit = p.edit();
            for (String key : new String[]{"rules", "island", "prompt_seconds"}) {
                if (!saved.containsKey(key)) edit.remove(key);
                else if (key.equals("rules")) edit.putString(key, (String) saved.get(key));
                else if (key.equals("island")) edit.putBoolean(key, (Boolean) saved.get(key));
                else edit.putInt(key, (Integer) saved.get(key));
            }
            edit.commit();
            SharedPreferences.Editor logs = history.edit().clear();
            for (Map.Entry<String, ?> entry : savedHistory.entrySet()) if (entry.getValue() instanceof String) logs.putString(entry.getKey(), (String) entry.getValue());
            logs.commit();
        }
        finish(result.containsKey("test_error") ? 1 : 0, result);
    }
    private static StatusBarNotification prompt(Context c) {
        for (StatusBarNotification n : c.getSystemService(NotificationManager.class).getActiveNotifications()) if (n.getId() == 101) return n;
        return null;
    }
    private static void awaitAbsent(Context c) throws Exception {
        long end = android.os.SystemClock.uptimeMillis() + 5000;
        while (prompt(c) != null && android.os.SystemClock.uptimeMillis() < end) Thread.sleep(25);
        if (prompt(c) != null) throw new AssertionError("Cancelled prompt did not disappear");
    }
}
