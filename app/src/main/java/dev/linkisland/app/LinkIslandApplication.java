package dev.linkisland.app;

import android.app.Application;

public final class LinkIslandApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        // Shizuku/libsu may load our classes in a privileged process with a different UID.
        if (android.os.Process.myUid() != getApplicationInfo().uid) return;
        try {
            if (Prefs.get(this).getBoolean("monitor_wanted", false)) {
                java.util.List<android.app.ApplicationExitInfo> exits = getSystemService(android.app.ActivityManager.class)
                    .getHistoricalProcessExitReasons(getPackageName(), 0, 1);
                if (!exits.isEmpty()) {
                    android.app.ApplicationExitInfo exit = exits.get(0);
                    if (exit.getTimestamp() > Prefs.get(this).getLong("monitor_started_at", 0)
                        && (exit.getReason() == android.app.ApplicationExitInfo.REASON_CRASH
                            || exit.getReason() == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE)) MonitorService.disarm(this);
                }
            }
            PromptSafety.recover(this);
        }
        catch (RuntimeException e) { PromptSafety.disableIsland(this, "提示恢复失败 · 已暂停超级岛"); }
        Thread.UncaughtExceptionHandler system = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                MonitorService.disarm(this);
                PromptSafety.cancel(this);
                PromptSafety.disableIsland(this, "应用异常退出 · 已撤销提示并暂停超级岛");
            } catch (RuntimeException ignored) {
                // Continue to Android's crash handler; never swallow or restart the crashing thread.
            } finally {
                if (system != null) system.uncaughtException(thread, error);
            }
        });
    }
}
