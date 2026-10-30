package dev.linkisland.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** Cleanup is also scheduled with the system, so it survives loss of the app process. */
final class PromptSafety {
    private static PendingIntent cleanup(Context c, String id) {
        Intent intent = new Intent(c, DismissReceiver.class).setAction("dev.linkisland.app.EXPIRE_PROMPT");
        if (!id.isEmpty()) intent.putExtra("log_id", id).setData(android.net.Uri.parse("linkisland:expiry/" + id));
        return PendingIntent.getBroadcast(c, 5, intent, PendingIntent.FLAG_IMMUTABLE);
    }
    static void arm(Context c, boolean island) {
        arm(c, island, Prefs.promptSeconds(c) * 1000L);
    }
    static void arm(Context c, boolean island, long timeoutMs) { arm(c, island, timeoutMs, ""); }
    static void arm(Context c, boolean island, long timeoutMs, String id) {
        boolean saved = Prefs.get(c).edit().putBoolean("prompt_active", true)
            .putBoolean("prompt_island", island).putString("prompt_log_id", id)
            .putLong("prompt_deadline", SystemClock.elapsedRealtime() + timeoutMs).commit();
        if (!saved) throw new IllegalStateException("Cannot persist prompt cleanup state");
        // An inexact backup; NotificationManager's own timeout remains the primary expiry.
        c.getSystemService(AlarmManager.class).set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + timeoutMs, cleanup(c, id));
    }
    static void cancel(Context c) {
        // Remove the notification first, even if updating cleanup state later fails.
        c.getSystemService(android.app.NotificationManager.class).cancel(LinkNotifications.PROMPT_ID);
        PendingIntent expiry = cleanup(c, Prefs.get(c).getString("prompt_log_id", ""));
        c.getSystemService(AlarmManager.class).cancel(expiry);
        expiry.cancel();
        JumpLog.closePending(c, Prefs.get(c).getString("prompt_log_id", ""));
        Prefs.get(c).edit().remove("prompt_active").remove("prompt_island").remove("prompt_deadline").remove("prompt_log_id").commit();
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
