package dev.linkisland.app.fixture;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Method;

/** Explicit fault injection for our own target process; never included in the main APK. */
public final class PromptFaultInstrumentation extends Instrumentation {
    private String mode;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mode = arguments.getString("mode", "invalid");
        start();
    }
    @Override public void onStart() {
        new Handler(Looper.getMainLooper()).post(() -> {
            Context c = getTargetContext();
            SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
            String oldRules = p.getString("rules", null);
            try {
                p.edit().putBoolean("island", true).commit();
                if ("invalid".equals(mode)) {
                    String rule = "[{\"name\":\"" + "x".repeat(1100)
                        + "\",\"type\":\"domain\",\"pattern\":\"example.org\",\"package\":\"com.github.android\"}]";
                    p.edit().putString("rules", rule).commit();
                }
                Class<?> notifications = Class.forName("dev.linkisland.app.LinkNotifications", true, c.getClassLoader());
                Method show = notifications.getDeclaredMethod("show", Context.class, String.class);
                show.setAccessible(true);
                show.invoke(null, c, "https://example.org/link-island-safety-test");
                if ("invalid".equals(mode)) {
                    Bundle result = new Bundle();
                    result.putBoolean("island_disabled", !p.getBoolean("island", true));
                    result.putString("status", p.getString("status", ""));
                    if (oldRules == null) p.edit().remove("rules").commit();
                    else p.edit().putString("rules", oldRules).commit();
                    finish(0, result);
                } else new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if ("java-crash".equals(mode)) throw new IllegalStateException("Intentional LinkIsland test crash");
                    android.os.Process.killProcess(android.os.Process.myPid());
                }, 500);
            } catch (Exception e) {
                Bundle result = new Bundle();result.putString("test_error", e.getClass().getSimpleName());finish(1, result);
            } finally {
                if (oldRules == null) p.edit().remove("rules").commit();
                else p.edit().putString("rules", oldRules).commit();
            }
        });
    }
}
