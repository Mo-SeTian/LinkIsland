package dev.linkisland.app;
import dev.linkisland.app.IClipboardCallback;
interface IClipboardBridge {
    void destroy() = 16777114;
    void start(IClipboardCallback callback, int userId) = 1;
    void stop() = 2;
    void probe() = 3;
    boolean launchLink(String target, String packageName) = 4;
    oneway void setDiagnosticsVisible(boolean visible) = 5;
}
