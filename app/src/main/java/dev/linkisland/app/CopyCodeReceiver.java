package dev.linkisland.app;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.widget.Toast;

/** Only the current, unexpired prompt may modify the clipboard after a user tap. */
public final class CopyCodeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String id = intent.getStringExtra("log_id"), code = intent.getStringExtra("code");
        android.content.SharedPreferences p = Prefs.get(c);
        if (id == null || id.isEmpty() || !id.equals(p.getString("prompt_log_id", "")) || !p.getBoolean("prompt_active", false)
            || SystemClock.elapsedRealtime() >= p.getLong("prompt_deadline", 0) || !PanShareCodes.valid(code)) return;
        try {
            ClipData clip = ClipData.newPlainText("网盘提取码", code);
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
            clip.getDescription().setExtras(extras);
            c.getSystemService(ClipboardManager.class).setPrimaryClip(clip);
            JumpLog.step(c, id, "提取码", "已复制提取码", "");
            if (android.os.Build.VERSION.SDK_INT <= 32) Toast.makeText(c, "提取码已复制", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            JumpLog.step(c, id, "提取码", "复制未成功，请重试或手动复制", "");
            Toast.makeText(c, "未能复制提取码，请重试", Toast.LENGTH_SHORT).show();
        }
    }
}
