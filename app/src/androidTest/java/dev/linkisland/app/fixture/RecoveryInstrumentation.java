package dev.linkisland.app.fixture;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/** Exercises actual MainActivity resume/start/stop, without requiring a privileged server. */
public final class RecoveryInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Context c = getTargetContext();
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        Map<String, ?> old = p.getAll();
        Activity activity = null;
        Bundle result = new Bundle();
        try {
            Class<?> service = Class.forName("dev.linkisland.app.MonitorService", true, c.getClassLoader());
            Method stop = service.getDeclaredMethod("stopListening", Context.class); stop.setAccessible(true);
            Field running = service.getDeclaredField("running"); running.setAccessible(true);
            for (int i = 0; i < 3; i++) {
                stop.invoke(null, c);
                waitRunning(running, false);
                boolean wanted = i != 1, automatic = i != 2;
                // Offline Shizuku leaves the real foreground service waiting for its bridge.
                p.edit().putBoolean("monitor_wanted", wanted).putBoolean("auto_restart", automatic).putInt("mode", 0).commit();
                Intent launch = new Intent().setClassName(c.getPackageName(), "dev.linkisland.app.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ActivityMonitor monitor = addMonitor("dev.linkisland.app.MainActivity", null, false);
                runOnMainSync(() -> c.startActivity(launch));
                activity = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor);
                if (activity == null) throw new AssertionError("MainActivity did not start");
                long focusDeadline = android.os.SystemClock.uptimeMillis() + 5000;
                while (!activity.hasWindowFocus() && android.os.SystemClock.uptimeMillis() < focusDeadline) Thread.sleep(50);
                if (!activity.hasWindowFocus()) throw new AssertionError("MainActivity did not resume");
                if (i == 0) waitRunning(running, true);
                else {
                    Thread.sleep(300);
                    if (running.getBoolean(null)) throw new AssertionError("Unwanted automatic start, case " + i);
                }
                stop.invoke(null, c);
                waitRunning(running, false);
                Activity closing = activity; runOnMainSync(closing::finish); activity = null;
            }
            result.putString("result", "PASS foreground reopen restores wanted listener; manual stop and disabled recovery stay stopped");
        } catch (Throwable error) {
            result.putString("test_error", error.getClass().getSimpleName());
        } finally {
            if (activity != null) { Activity closing = activity; runOnMainSync(closing::finish); }
            SharedPreferences.Editor edit = p.edit();
            for (String key : new String[]{"monitor_wanted", "auto_restart", "mode"}) {
                if (!old.containsKey(key)) edit.remove(key);
                else if ("mode".equals(key)) edit.putInt(key, (Integer) old.get(key));
                else edit.putBoolean(key, (Boolean) old.get(key));
            }
            edit.commit();
        }
        finish(result.containsKey("test_error") ? 1 : 0, result);
    }
    private static void waitRunning(Field field, boolean expected) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        while (field.getBoolean(null) != expected && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
        if (field.getBoolean(null) != expected) throw new AssertionError("Unexpected listener state");
    }
}
