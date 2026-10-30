package dev.linkisland.app;

import android.content.Intent;
import android.os.IBinder;
import com.topjohnwu.superuser.ipc.RootService;

public final class RootClipboardService extends RootService {
    private final ClipboardBridge bridge = new ClipboardBridge();
    @Override public IBinder onBind(Intent intent) { return bridge; }
    @Override public void onDestroy() { bridge.destroy(); super.onDestroy(); }
}
