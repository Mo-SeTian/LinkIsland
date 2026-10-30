package dev.linkisland.app.fixture;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import java.lang.reflect.Method;
import java.util.Map;

public final class PromptDurationInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Context c = getTargetContext();
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        Map<String, ?> old = p.getAll();
        Bundle result = new Bundle();
        Method cancel = null;
        try {
            ClassLoader loader = c.getClassLoader();
            Method show = Class.forName("dev.linkisland.app.LinkNotifications", true, loader)
                .getDeclaredMethod("show", Context.class, String.class);
            show.setAccessible(true);
            cancel = Class.forName("dev.linkisland.app.PromptSafety", true, loader).getDeclaredMethod("cancel", Context.class);
            cancel.setAccessible(true);
            p.edit().putString("rules", "[{\"name\":\"浏览器测试\",\"type\":\"regex\",\"pattern\":\"^https?://\"}]").putBoolean("island", false).putBoolean("monitor_wanted", false).commit();
            NotificationManager manager = c.getSystemService(NotificationManager.class);
            for (int seconds : new int[]{30, 15, 10, 5}) for (boolean island : new boolean[]{false, true}) {
                p.edit().putInt("prompt_seconds", seconds).putBoolean("island", island).commit();
                long began = SystemClock.elapsedRealtime();
                show.invoke(null, c, "https://example.org/private-path?token=fixture-secret");
                StatusBarNotification prompt = null;
                for (StatusBarNotification n : manager.getActiveNotifications()) if (n.getId() == 101) prompt = n;
                if (prompt == null || prompt.getNotification().getTimeoutAfter() != seconds * 1000L)
                    throw new AssertionError("Incorrect notification timeout");
                if ((prompt.getNotification().flags & Notification.FLAG_NO_CLEAR) == 0 || prompt.isClearable())
                    throw new AssertionError("Prompt can be cleared by Clear all");
                long expected = began + seconds * 1000L, deadline = p.getLong("prompt_deadline", 0);
                if (deadline < expected || deadline - expected > 2000) throw new AssertionError("Incorrect cleanup deadline");
            }
            long deadline = SystemClock.elapsedRealtime() + 8000;
            boolean active;
            do {
                Thread.sleep(100);
                active = false;
                for (StatusBarNotification n : manager.getActiveNotifications()) if (n.getId() == 101) active = true;
            } while (active && SystemClock.elapsedRealtime() < deadline);
            if (active) throw new AssertionError("Five-second prompt did not expire");
            p.edit().putInt("prompt_seconds", 30).commit();
            show.invoke(null, c, "https://example.org/dismiss-test");
            for (StatusBarNotification n : manager.getActiveNotifications()) if (n.getId() == 101)
                n.getNotification().actions[1].actionIntent.send();
            Thread.sleep(500);
            for (StatusBarNotification n : manager.getActiveNotifications()) if (n.getId() == 101)
                throw new AssertionError("Ignore did not remove protected prompt");
            String log = c.getSharedPreferences("jump_log", Context.MODE_PRIVATE).getString("groups", "[]");
            if (!log.contains("example.org") || log.contains("private-path") || log.contains("fixture-secret"))
                throw new AssertionError("Unsafe or missing recognition log");
            result.putString("result", "PASS ordinary/island Clear-all flags; all timeouts; five-second expiry; Ignore and sanitized logs");
        } catch (Throwable error) {
            result.putString("test_error", error.getClass().getSimpleName());
        } finally {
            try { if (cancel != null) cancel.invoke(null, c); } catch (Exception ignored) { }
            SharedPreferences.Editor edit = p.edit();
            for (String key : new String[]{"prompt_seconds", "island", "monitor_wanted", "rules"}) {
                if (!old.containsKey(key)) edit.remove(key);
                else if (key.equals("rules")) edit.putString(key, (String) old.get(key));
                else if (key.equals("prompt_seconds")) edit.putInt(key, (Integer) old.get(key));
                else edit.putBoolean(key, (Boolean) old.get(key));
            }
            edit.commit();
        }
        finish(result.containsKey("test_error") ? 1 : 0, result);
    }
}
