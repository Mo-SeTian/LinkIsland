package dev.linkisland.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class DismissReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (Prefs.get(c).getBoolean("prompt_active", false)) {
            boolean expired = android.os.SystemClock.elapsedRealtime() >= Prefs.get(c).getLong("prompt_deadline", 0);
            JumpLog.add(c, "提示", expired ? "提示已自动消失" : "已忽略提示", null);
        }
        PromptSafety.cancel(c);
    }
}
