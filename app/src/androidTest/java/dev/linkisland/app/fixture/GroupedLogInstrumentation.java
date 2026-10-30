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
import org.json.JSONArray;
import org.json.JSONObject;

public final class GroupedLogInstrumentation extends Instrumentation {
    private boolean keepHistory;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments); keepHistory = "true".equals(arguments.getString("keepHistory")); start();
    }
    @Override public void onStart() {
        Context c = getTargetContext();
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        SharedPreferences history = c.getSharedPreferences("jump_log", Context.MODE_PRIVATE);
        Map<String, ?> old = p.getAll(), oldHistory = history.getAll();
        Bundle result = new Bundle();
        Method cancel = null;
        try {
            Method show = Class.forName("dev.linkisland.app.LinkNotifications", true, c.getClassLoader())
                .getDeclaredMethod("show", Context.class, String.class);
            show.setAccessible(true);
            cancel = Class.forName("dev.linkisland.app.PromptSafety", true, c.getClassLoader()).getDeclaredMethod("cancel", Context.class);
            cancel.setAccessible(true);
            p.edit().putBoolean("island", false).putBoolean("monitor_wanted", false).putInt("prompt_seconds", 30).putString("rules", "[]").commit();
            history.edit().clear().putString("groups", "[]").commit();
            show.invoke(null, c, "https://example.org/first?private=fixture-one");
            Notification first = prompt(c, null);
            String firstId = p.getString("prompt_log_id", "");
            show.invoke(null, c, "https://example.org/second?private=fixture-two");
            Notification second = prompt(c, first);
            String secondId = p.getString("prompt_log_id", "");
            if (firstId.equals(secondId)) throw new AssertionError("Copy IDs reused");
            first.actions[1].actionIntent.send();
            c.sendBroadcast(new android.content.Intent().setClassName(c, "dev.linkisland.app.DismissReceiver"));
            Thread.sleep(300);
            prompt(c, null);
            if (!secondId.equals(p.getString("prompt_log_id", ""))) throw new AssertionError("Old Ignore canceled new prompt");
            second.actions[1].actionIntent.send();
            Thread.sleep(300);
            JSONArray groups = new JSONArray(history.getString("groups", "[]"));
            if (groups.length() != 2 || !groups.getJSONObject(0).getString("status").equals("replaced")
                || !groups.getJSONObject(1).getString("status").equals("ignored")) throw new AssertionError("Incorrect grouping or final status");
            for (StatusBarNotification n : c.getSystemService(NotificationManager.class).getActiveNotifications())
                if (n.getId() == 101) throw new AssertionError("Current Ignore did not remove prompt");
            p.edit().putString("rules", "invalid JSON").commit();
            show.invoke(null, c, "https://example.org/third?private=fixture-three");
            groups = new JSONArray(history.getString("groups", "[]"));
            JSONObject failure = groups.getJSONObject(groups.length() - 1);
            if (!failure.getString("status").equals("failed")) throw new AssertionError("Failure not marked for filtering");
            if (groups.toString().contains("fixture-one") || groups.toString().contains("/first") || groups.toString().contains("private="))
                throw new AssertionError("Private link components stored");
            result.putString("result", "PASS copy grouping, same-host isolation, stale Ignore protection, final failure and sanitized history");
        } catch (Throwable error) {
            result.putString("test_error", error.getClass().getSimpleName());
        } finally {
            try { if (cancel != null) cancel.invoke(null, c); } catch (Exception ignored) { }
            SharedPreferences.Editor edit = p.edit();
            for (String key : new String[]{"island", "monitor_wanted", "prompt_seconds", "rules"}) {
                if (!old.containsKey(key)) edit.remove(key);
                else if (key.equals("rules")) edit.putString(key, (String) old.get(key));
                else if (key.equals("prompt_seconds")) edit.putInt(key, (Integer) old.get(key));
                else edit.putBoolean(key, (Boolean) old.get(key));
            }
            edit.commit();
            if (!keepHistory) {
                SharedPreferences.Editor logs = history.edit().clear();
                for (Map.Entry<String, ?> entry : oldHistory.entrySet()) if (entry.getValue() instanceof String)
                    logs.putString(entry.getKey(), (String) entry.getValue());
                logs.commit();
            }
        }
        finish(result.containsKey("test_error") ? 1 : 0, result);
    }
    private static Notification prompt(Context c, Notification previous) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 3000;
        do {
            for (StatusBarNotification n : c.getSystemService(NotificationManager.class).getActiveNotifications())
                if (n.getId() == 101 && (previous == null || !n.getNotification().contentIntent.equals(previous.contentIntent))) return n.getNotification();
            Thread.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Prompt missing");
    }
}
