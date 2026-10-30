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

/** Requires an already-authorized Root or Shizuku server on the test device. */
public final class BridgeRecoveryInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Context c = getTargetContext();
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        boolean wanted = p.getBoolean("monitor_wanted", false);
        Bundle result = new Bundle();
        Method stop = null;
        String stage = "initial connection";
        try {
            ClassLoader loader = c.getClassLoader();
            Class<?> service = Class.forName("dev.linkisland.app.MonitorService", true, loader);
            Field bridge = service.getDeclaredField("launchBridge"); bridge.setAccessible(true);
            stop = service.getDeclaredMethod("stopListening", Context.class); stop.setAccessible(true);
            p.edit().putBoolean("monitor_wanted", true).commit();
            // Shell launch also works on ROMs that block an instrumented background activity start.
            try (android.os.ParcelFileDescriptor fd = getUiAutomation().executeShellCommand(
                    "am start -W -f 0x10008000 -n " + c.getPackageName() + "/dev.linkisland.app.MainActivity");
                    FileInputStream input = new FileInputStream(fd.getFileDescriptor())) {
                byte[] discard = new byte[256];
                while (input.read(discard) != -1) { }
            }
            Object original = awaitBridge(bridge, null, 30000);
            // Pause diagnostics; only lightweight heartbeats now keep the connection healthy.
            runOnMainSync(() -> c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            Thread.sleep(35000);
            stage = "background heartbeat";
            if (bridge.get(null) != original) throw new AssertionError("Background heartbeat caused a reconnect");
            Class<?> contract = Class.forName("dev.linkisland.app.IClipboardBridge", true, loader);
            long began = SystemClock.uptimeMillis();
            stage = "silent worker recovery";
            try { contract.getMethod("destroy").invoke(original); }
            catch (java.lang.reflect.InvocationTargetException e) {
                // Shizuku exits the worker process before the synchronous reply can return.
                if (!(e.getCause() instanceof android.os.DeadObjectException)) throw e;
            }
            Object restored = awaitBridge(bridge, original, 55000);
            Thread.sleep(2500);
            if (bridge.get(null) != restored) throw new AssertionError("Recovered bridge did not stay connected");
            result.putLong("silent_bridge_recovery_ms", SystemClock.uptimeMillis() - began);
            result.putString("result", "PASS background heartbeats remain healthy and stopped worker reconnects");
        } catch (Throwable error) {
            result.putString("test_error", stage + ": " + error.getClass().getSimpleName());
        } finally {
            try { if (stop != null) stop.invoke(null, c); } catch (Exception ignored) { }
            p.edit().putBoolean("monitor_wanted", wanted).commit();
        }
        finish(result.containsKey("test_error") ? 1 : 0, result);
    }
    private static Object awaitBridge(Field field, Object previous, long timeout) throws Exception {
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) {
            Object current = field.get(null);
            if (current != null && current != previous
                && ((android.os.IInterface) current).asBinder().isBinderAlive()) return current;
            Thread.sleep(100);
        }
        throw new AssertionError("Bridge did not reconnect");
    }
}
