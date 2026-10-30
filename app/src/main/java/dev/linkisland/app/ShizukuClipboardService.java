package dev.linkisland.app;

public final class ShizukuClipboardService extends ClipboardBridge {
    public ShizukuClipboardService() { super(); }
    @Override public void destroy() { super.destroy(); System.exit(0); }
}
