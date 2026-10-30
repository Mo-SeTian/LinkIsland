# Instantiated by the privileged service loader in another process.
-keep class dev.linkisland.app.RootClipboardService { public <init>(); }
-keep class dev.linkisland.app.ShizukuClipboardService { public <init>(); }

# Keep both ends of the cross-process Binder contract stable.
-keep interface dev.linkisland.app.IClipboardBridge { *; }
-keep class dev.linkisland.app.IClipboardBridge$* { *; }
-keep interface dev.linkisland.app.IClipboardCallback { *; }
-keep class dev.linkisland.app.IClipboardCallback$* { *; }
