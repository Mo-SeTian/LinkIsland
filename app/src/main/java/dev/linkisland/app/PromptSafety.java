package dev.linkisland.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** Cleanup is also scheduled with the system, so it survives loss of the app process. */
final class PromptSafety {
    private static PendingIntent cleanup(Context c) {
        return PendingIntent.getBroadcast(c, 5, new Intent(c, DismissReceiver.class)
            .setAction("dev.linkisland.app.EXPIRE_PROMPT"), PendingIntent.FLAG_IMMUTABLE);
    }
    static void arm(Context c, boolean island) {
        arm(c, island, Prefs.promptSeconds(c) * 1000L);
    }
    static void arm(Context c, boolean island, long timeoutMs) {
        boolean saved = Prefs.get(c).edit().putBoolean("prompt_active", true)
            .putBoolean("prompt_island", island)
            .putLong("prompt_deadline", SystemClock.elapsedRealtime() + timeoutMs).commit();
        if (!saved) throw new IllegalStateException("Cannot persist prompt cleanup state");
        // An inexact backup; NotificationManager's own timeout remains the primary expiry.
        c.getSystemService(AlarmManager.class).set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + timeoutMs, cleanup(c));
    }
    static void cancel(Context c) {
        // Remove the notification first, even if updating cleanup state later fails.
        c.getSystemService(android.app.NotificationManager.class).cancel(LinkNotifications.PROMPT_ID);
        c.getSystemService(AlarmManager.class).cancel(cleanup(c));
        Prefs.get(c).edit().remove("prompt_active").remove("prompt_island").remove("prompt_deadline").commit();
    }
    static void recover(Context c) {
        boolean interrupted = Prefs.get(c).getBoolean("prompt_active", false)
            && Prefs.get(c).getBoolean("prompt_island", false)
            && SystemClock.elapsedRealtime() < Prefs.get(c).getLong("prompt_deadline", 0);
        cancel(c);
        if (interrupted) disableIsland(c, "检测到上次提示异常中断 · 已清理并暂停超级岛");
    }
    static void disableIsland(Context c, String reason) {
        Prefs.status(c, reason);
        // This synchronous write also flushes the status snapshot before a crash exits.
        Prefs.get(c).edit().putBoolean("island", false).commit();
    }
}
