package dev.linkisland.app;
oneway interface IClipboardCallback {
    void onLink(String url);
    void onStatus(String status);
    void onDiagnostics(String diagnostics);
    void onHeartbeat();
    void onRecognition(String result);
}
