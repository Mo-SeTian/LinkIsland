package dev.linkisland.app.fixture;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.lang.reflect.Method;

/** Prepares real notifications for external task/back-button regression checks; test APK only. */
public final class ReturnStackInstrumentation extends Instrumentation {
    private String scenario;
    private boolean hold;
    private static final String[] KEYS = {"island", "privileged_open", "browser", "rules"};
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        scenario = arguments.getString("scenario", "browser");
        hold = "true".equals(arguments.getString("hold"));
        start();
    }
    @Override public void onStart() {
        Context c = getTargetContext();
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        SharedPreferences saved = c.getSharedPreferences("return_stack_fixture", Context.MODE_PRIVATE);
        Bundle result = new Bundle();
        java.io.File ready = new java.io.File(c.getFilesDir(), "return_stack_ready");
        java.io.File done = new java.io.File(c.getFilesDir(), "return_stack_done");
        try {
            ready.delete(); done.delete();
            if ("restore".equals(scenario)) {
                if (saved.getBoolean("saved", false)) {
                    SharedPreferences.Editor edit = p.edit();
                    for (String key : KEYS) {
                        if (!saved.contains(key)) edit.remove(key);
                        else if ("island".equals(key) || "privileged_open".equals(key))
                            edit.putBoolean(key, saved.getBoolean(key, false));
                        else edit.putString(key, saved.getString(key, ""));
                    }
                    edit.commit(); saved.edit().clear().commit();
                }
                invoke(c, "dev.linkisland.app.PromptSafety", "cancel", new Class<?>[]{Context.class}, c);
            } else {
                if (!saved.getBoolean("saved", false)) {
                    SharedPreferences.Editor edit = saved.edit().clear().putBoolean("saved", true);
                    for (String key : KEYS) if (p.contains(key)) {
                        if ("island".equals(key) || "privileged_open".equals(key))
                            edit.putBoolean(key, p.getBoolean(key, false));
                        else edit.putString(key, p.getString(key, ""));
                    }
                    edit.commit();
                }
                String pkg = "missing".equals(scenario) ? "dev.linkisland.missing" : getContext().getPackageName();
                String rules = "app".equals(scenario) || "missing".equals(scenario)
                    ? "[{\"name\":\"任务栈测试\",\"type\":\"domain\",\"pattern\":\"example.org\",\"package\":\"" + pkg + "\"}]" : "[]";
                p.edit().putBoolean("island", false).putBoolean("privileged_open", false)
                    .putString("browser", "chooser".equals(scenario) ? "ask" : getContext().getPackageName())
                    .putString("rules", rules).commit();
                invoke(c, "dev.linkisland.app.LinkNotifications", "show",
                    new Class<?>[]{Context.class, String.class}, c, "https://example.org/return-stack-test");
                ready.createNewFile();
                if (hold) {
                    long deadline = android.os.SystemClock.elapsedRealtime() + 30_000;
                    while (!done.exists() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
                }
            }
            result.putString("scenario", scenario);
            finish(0, result);
        } catch (Exception e) {
            result.putString("test_error", e.getClass().getSimpleName());
            finish(1, result);
        } finally {
            ready.delete(); done.delete();
        }
    }
    private static void invoke(Context c, String name, String method, Class<?>[] types, Object... args) throws Exception {
        Method target = Class.forName(name, true, c.getClassLoader()).getDeclaredMethod(method, types);
        target.setAccessible(true); target.invoke(null, args);
    }
}
