package dev.linkisland.app;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** A user-invoked pause/resume switch; never requests privileged access on its own. */
public final class MonitorTile extends TileService {
    static void refresh(Context c) { requestListeningState(c, new ComponentName(c, MonitorTile.class)); }
    @Override public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean running = MonitorService.running, paused = MonitorService.paused(this);
        tile.setState(running && !paused ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("链接岛");
        tile.setSubtitle(!running ? "未开启" : paused ? "已暂停 · 点按继续" : "点按暂停 15 分钟");
        tile.updateTile();
    }
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // Intent overload is required on API 31–33.
    @Override public void onClick() {
        if (MonitorService.running) {
            startService(new Intent(this, MonitorService.class).setAction(MonitorService.paused(this) ? "CONTINUE" : "PAUSE")
                .putExtra("minutes", 15));
        } else {
            Intent home = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (android.os.Build.VERSION.SDK_INT >= 34)
                startActivityAndCollapse(PendingIntent.getActivity(this, 10, home, PendingIntent.FLAG_IMMUTABLE));
            else startActivityAndCollapse(home);
        }
    }
}
