# Prune unused code while retaining readable debug names and test entry points.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
-keep,allowoptimization class dev.linkisland.app.LinkNotifications {
    static void show(android.content.Context, java.lang.String);
    static void show(android.content.Context, java.lang.String, java.lang.String);
}
-keep,allowoptimization class dev.linkisland.app.PromptSafety {
    static void cancel(android.content.Context);
}
-keepclassmembers class dev.linkisland.app.MonitorService {
    static boolean running;
    dev.linkisland.app.IClipboardBridge launchBridge;
    static void stopListening(android.content.Context);
}
