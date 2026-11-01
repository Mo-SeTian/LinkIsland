package dev.linkisland.app.fixture;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.json.JSONArray;

/** Requires an authorized bridge and a public HTTP link in the test clipboard. */
public final class PauseInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Context c = getTargetContext();
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        SharedPreferences history = c.getSharedPreferences("jump_log", Context.MODE_PRIVATE);
        Map<String, ?> old = p.getAll(), oldHistory = history.getAll();
        Bundle result = new Bundle();
        Method stop = null;
        String stage = "connect";
        try {
            ClassLoader loader = c.getClassLoader();
            Class<?> service = Class.forName("dev.linkisland.app.MonitorService", true, loader);
            Class<?> contract = Class.forName("dev.linkisland.app.IClipboardBridge", true, loader);
            Field bridge = service.getDeclaredField("launchBridge"); bridge.setAccessible(true);
            Field running = service.getDeclaredField("running"); running.setAccessible(true);
            stop = service.getDeclaredMethod("stopListening", Context.class); stop.setAccessible(true);
            p.edit().putBoolean("monitor_wanted", true).putBoolean("auto_restart", true).putBoolean("island", false).commit();
            try (android.os.ParcelFileDescriptor fd = getUiAutomation().executeShellCommand(
                    "am start -W -n " + c.getPackageName() + "/dev.linkisland.app.MainActivity");
                    FileInputStream in = new FileInputStream(fd.getFileDescriptor())) {
                byte[] discard = new byte[256]; while (in.read(discard) != -1) { }
            }
            Object first = awaitBridge(bridge, null);
            stage = "pause suppression";
            command(c, "PAUSE", 5);
            await(() -> p.getLong("pause_until", 0) > System.currentTimeMillis());
            long deadline = p.getLong("pause_until", 0);
            String before = history.getString("groups", "[]");
            contract.getMethod("probe").invoke(first);
            Thread.sleep(700); waitForIdleSync();
            check(before.equals(history.getString("groups", "[]")), "Paused probe wrote history");
            check(!p.getBoolean("prompt_active", false), "Paused probe posted prompt");
            check(bridge.get(null) == first, "Pause reconnected bridge");

            stage = "recovery during pause";
            long recoveryStart = SystemClock.uptimeMillis();
            try { contract.getMethod("destroy").invoke(first); }
            catch (java.lang.reflect.InvocationTargetException e) {
                if (!(e.getCause() instanceof android.os.DeadObjectException)) throw e;
            }
            Object restored = awaitBridge(bridge, first);
            Thread.sleep(500);
            check(p.getLong("pause_until", 0) == deadline, "Recovery lost pause deadline");
            check(p.getBoolean("monitor_wanted", false), "Recovery lost wanted state");
            result.putLong("paused_bridge_recovery_ms", SystemClock.uptimeMillis() - recoveryStart);

            stage = "timer expiry";
            // Shorten only the test deadline, then exercise the actual production scheduler.
            p.edit().putLong("pause_until", System.currentTimeMillis() + 500).commit();
            command(c, "CHECK", 0);
            await(() -> !p.contains("pause_until"));
            check(bridge.get(null) == restored, "Expiry reconnected bridge");
            contract.getMethod("probe").invoke(restored);
            await(() -> p.getBoolean("prompt_active", false));
            JSONArray logs = new JSONArray(history.getString("groups", "[]"));
            check(logs.getJSONObject(logs.length() - 1).optString("rule_key").matches("[0-9a-f]{64}"), "Missing log rule digest");

            stage = "manual continue and stop";
            command(c, "PAUSE", 15); await(() -> p.contains("pause_until"));
            command(c, "CONTINUE", 0); await(() -> !p.contains("pause_until"));
            check(bridge.get(null) == restored, "Continue reconnected bridge");
            command(c, "PAUSE", 60); await(() -> p.contains("pause_until"));
            stop.invoke(null, c); await(() -> !running.getBoolean(null));
            check(!p.getBoolean("monitor_wanted", false) && !p.contains("pause_until"), "Stop left automatic resume armed");
            result.putString("result", "PASS pause suppresses prompts/history; worker recovers while paused; expiry and continue retain bridge; manual stop disarms resume");
        } catch (Throwable error) { result.putString("test_error", stage + ": " + error.getClass().getSimpleName()); }
        finally {
            try { if (stop != null) stop.invoke(null, c); } catch (Exception ignored) { }
            SharedPreferences.Editor edit = p.edit();
            for (String key : new String[]{"monitor_wanted", "auto_restart", "island", "pause_until"}) {
                if (!old.containsKey(key)) edit.remove(key);
                else if (old.get(key) instanceof Boolean) edit.putBoolean(key, (Boolean) old.get(key));
                else edit.putLong(key, (Long) old.get(key));
            }
            edit.commit();
            SharedPreferences.Editor logs = history.edit().clear();
            for (Map.Entry<String, ?> entry : oldHistory.entrySet())
                if (entry.getValue() instanceof String) logs.putString(entry.getKey(), (String) entry.getValue());
            logs.commit();
        }
        finish(result.containsKey("test_error") ? 1 : 0, result);
    }
    private static void command(Context c, String action, int minutes) {
        c.startService(new Intent().setClassName(c.getPackageName(), "dev.linkisland.app.MonitorService")
            .setAction(action).putExtra("minutes", minutes));
    }
    private interface Condition { boolean ready() throws Exception; }
    private static void await(Condition condition) throws Exception {
        long end = SystemClock.uptimeMillis() + 5000;
        while (!condition.ready() && SystemClock.uptimeMillis() < end) Thread.sleep(25);
        check(condition.ready(), "Timed out waiting for state");
    }
    private static Object awaitBridge(Field field, Object old) throws Exception {
        long end = SystemClock.uptimeMillis() + 55000;
        while (SystemClock.uptimeMillis() < end) {
            Object value = field.get(null);
            if (value != null && value != old && ((android.os.IInterface) value).asBinder().isBinderAlive()) return value;
            Thread.sleep(50);
        }
        throw new AssertionError("Bridge unavailable");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
