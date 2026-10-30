package dev.linkisland.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class DismissReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String active = Prefs.get(c).getString("prompt_log_id", "");
        String id = intent.getStringExtra("log_id");
        if (id == null ? !active.isEmpty() : !id.equals(active)) return;
        if (Prefs.get(c).getBoolean("prompt_active", false)) {
            boolean expired = android.os.SystemClock.elapsedRealtime() >= Prefs.get(c).getLong("prompt_deadline", 0);
            JumpLog.step(c, active, "提示", expired ? "提示已自动消失" : "已忽略提示", expired ? "expired" : "ignored");
        }
        PromptSafety.cancel(c);
    }
}
