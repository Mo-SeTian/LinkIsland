package dev.linkisland.app;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;
import rikka.shizuku.Shizuku;

public final class MonitorService extends Service {
    static volatile boolean running;
    static volatile String diagnostics = "";
    static Runnable onDiagnosticsChanged;
    private static volatile IClipboardBridge launchBridge;
    private final Handler main = new Handler(Looper.getMainLooper());
    private IClipboardBridge bridge;
    private String mode;
    private boolean bound, alive;
    private int attempts, generation;
    private long heartbeat;
    private final Runnable pauseFinished = this::updatePause;
    private final Runnable heartbeatReceived = () -> {
        if (alive && bridge != null) heartbeat = android.os.SystemClock.uptimeMillis();
    };
    private final Runnable retry = this::connect;
    private final Runnable timeout = () -> { if (alive) reconnect("监听连接超时"); };
    private final Runnable health = new Runnable() {
        @Override public void run() {
            if (!alive || bridge == null) return;
            updatePause();
            if (MonitorPolicy.heartbeatExpired(heartbeat, android.os.SystemClock.uptimeMillis())) reconnect("监听心跳超时");
            else main.postDelayed(this, 10_000);
        }
    };
    static void disarm(Context c) { Prefs.get(c).edit().putBoolean("monitor_wanted", false).remove("pause_until").commit(); }
    static void stopListening(Context c) { disarm(c); c.stopService(new Intent(c, MonitorService.class)); }
    static boolean paused(Context c) {
        return MonitorPolicy.pauseRemaining(Prefs.get(c).getLong("pause_until", 0), System.currentTimeMillis()) > 0;
    }
    private void updatePause() {
        main.removeCallbacks(pauseFinished);
        long until = Prefs.get(this).getLong("pause_until", 0);
        if (until == 0) return;
        long remaining = MonitorPolicy.pauseRemaining(until, System.currentTimeMillis());
        if (remaining > 0) main.postDelayed(pauseFinished, remaining);
        else {
            Prefs.get(this).edit().remove("pause_until").apply();
            Prefs.status(this, "临时暂停已结束 · 继续监听");
            updateNotification();
        }
    }
    private void updateNotification() {
        getSystemService(android.app.NotificationManager.class).notify(LinkNotifications.SERVICE_ID,
            LinkNotifications.service(this, "root".equals(mode) ? "Root" : "Shizuku"));
        MonitorTile.refresh(this);
    }
    static boolean launch(String target, String pkg) {
        IClipboardBridge remote = launchBridge;
        if (remote == null) return false;
        try { return remote.launchLink(target, pkg); } catch (Exception e) { return false; }
    }
    static void observeDiagnostics(Runnable observer) {
        onDiagnosticsChanged = observer;
        IClipboardBridge remote = launchBridge;
        if (remote != null) try { remote.setDiagnosticsVisible(observer != null); } catch (Exception ignored) { }
    }
    private boolean automatic() { return Prefs.get(this).getBoolean("auto_restart", true); }
    private int restartMode() { return automatic() ? START_STICKY : START_NOT_STICKY; }
    private Shizuku.UserServiceArgs args() {
        return new Shizuku.UserServiceArgs(new ComponentName(this, ShizukuClipboardService.class))
            .daemon(false).processNameSuffix("clipboard").version(BuildConfig.VERSION_CODE);
    }
    private final IClipboardCallback callback = new IClipboardCallback.Stub() {
        @Override public void onHeartbeat() { main.post(heartbeatReceived); }
        @Override public void onRecognition(String result) {
            main.post(() -> { if (alive && !paused(MonitorService.this)) JumpLog.recognition(MonitorService.this, result); });
        }
        @Override public void onLink(String url) { receivedLink(url, null); }
        @Override public void onLinkWithCode(String url, String code) { receivedLink(url, code); }
        @Override public void onDiagnostics(String diagnostics) {
            main.post(() -> {
                if (!alive || bridge == null) return;
                heartbeat = android.os.SystemClock.uptimeMillis();
                MonitorService.diagnostics = diagnostics;
                if (onDiagnosticsChanged != null) onDiagnosticsChanged.run();
            });
        }
        @Override public void onStatus(String status) {
            main.post(() -> {
                if (!alive) return;
                if (!status.startsWith("收到链接")) Prefs.status(MonitorService.this, status);
                if (status.startsWith("监听已连接")) {
                    attempts = 0; main.removeCallbacks(timeout); main.removeCallbacks(health);
                    heartbeat = android.os.SystemClock.uptimeMillis();
                    main.postDelayed(health, 10_000);
                    observeDiagnostics(onDiagnosticsChanged);
                }
                if (status.startsWith("监听初始化失败")) { disarm(MonitorService.this); stopSelf(); }
            });
        }
    };
    private void receivedLink(String url, String code) { main.post(() -> {
        if (!alive) return;
        updatePause();
        if (!paused(this)) LinkNotifications.show(this, url, code);
    }); }
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!alive) return;
            bridge = IClipboardBridge.Stub.asInterface(binder);
            launchBridge = bridge;
            try { bridge.start(callback, getApplicationInfo().uid / 100000); }
            catch (Exception e) { reconnect("启动监听失败"); }
        }
        @Override public void onServiceDisconnected(ComponentName name) { if (alive && bound) reconnect("权限服务已断开"); }
        @Override public void onBindingDied(ComponentName name) { if (alive && bound) reconnect("权限绑定失效"); }
    };
    @Override public void onCreate() { super.onCreate(); alive = true; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            disarm(this); Prefs.status(this, "监听已停止"); stopSelf(); return START_NOT_STICKY;
        }
        if (running) updatePause();
        if (intent != null && ("PAUSE".equals(intent.getAction()) || "CONTINUE".equals(intent.getAction()))) {
            if (!running || !Prefs.get(this).getBoolean("monitor_wanted", false)) { stopSelf(); return START_NOT_STICKY; }
            if ("PAUSE".equals(intent.getAction())) {
                int minutes = intent.getIntExtra("minutes", 15);
                if (minutes != 5 && minutes != 15 && minutes != 60) return restartMode();
                Prefs.get(this).edit().putLong("pause_until", System.currentTimeMillis() + minutes * 60_000L).commit();
                PromptSafety.cancel(this);
                Prefs.status(this, "链接提示已暂停 " + minutes + " 分钟 · 到期自动恢复");
            } else {
                Prefs.get(this).edit().remove("pause_until").commit();
                main.removeCallbacks(pauseFinished);
                Prefs.status(this, "已继续监听");
            }
            updatePause(); updateNotification();
            return restartMode();
        }
        if ((intent == null || "RESUME".equals(intent.getAction()))
            && !MonitorPolicy.shouldResume(Prefs.get(this).getBoolean("monitor_wanted", false), automatic())) {
            stopSelf(); return START_NOT_STICKY;
        }
        if (intent != null && "CHECK".equals(intent.getAction())) {
            if (bridge != null) {
                try { bridge.probe(); } catch (Exception e) { reconnect("检查失败"); }
            } else { Prefs.status(this, "权限服务尚未连接"); if (!running) stopSelf(); }
            return restartMode();
        }
        if (intent != null && "OPTIONS".equals(intent.getAction())) {
            if (!running) stopSelf();
            else if (!automatic() && attempts > 0 && bridge == null) { disarm(this); stopSelf(); }
            return restartMode();
        }
        if (running) return restartMode();
        mode = Prefs.get(this).getInt("mode", 0) == 1 ? "root" : "shizuku";
        startForeground(LinkNotifications.SERVICE_ID, LinkNotifications.service(this, "root".equals(mode) ? "Root" : "Shizuku"));
        running = true;
        Prefs.get(this).edit().putBoolean("monitor_wanted", true).commit();
        updatePause();
        MonitorTile.refresh(this);
        connect();
        return restartMode();
    }
    private void connect() {
        if (!alive) return;
        int ticket = ++generation;
        main.removeCallbacks(retry); main.removeCallbacks(timeout);
        Prefs.get(this).edit().putString("clipboard_diagnostics", "等待权限服务连接…").apply();
        diagnostics = "等待权限服务连接…";
        Prefs.status(this, attempts == 0 ? "正在连接权限服务…" : "正在自动恢复监听…");
        main.postDelayed(timeout, 30_000);
        if ("root".equals(mode)) {
            Shell.getShell(shell -> main.post(() -> {
                if (!alive || ticket != generation) return;
                if (!shell.isRoot()) { disarm(this); Prefs.status(this, "Root 未授权或不可用"); stopSelf(); return; }
                try { RootService.bind(new Intent(this, RootClipboardService.class), connection); bound = true; }
                catch (RuntimeException e) { reconnect("Root 连接失败"); }
            }));
        } else {
            try {
                if (!Shizuku.pingBinder()) { reconnect("等待 Shizuku 服务恢复"); return; }
                if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    disarm(this); Prefs.status(this, "Shizuku 未授权 · 请重新授权"); stopSelf(); return;
                }
                Shizuku.bindUserService(args(), connection); bound = true;
            } catch (RuntimeException e) { reconnect("Shizuku 连接失败"); }
        }
    }
    private void reconnect(String reason) {
        main.removeCallbacks(timeout); main.removeCallbacks(retry);
        disconnect();
        PromptSafety.cancel(this);
        if (!automatic()) { disarm(this); Prefs.status(this, reason + " · 自动恢复未开启"); stopSelf(); return; }
        attempts++;
        long delay = MonitorPolicy.retryDelay(attempts);
        Prefs.status(this, reason + " · " + delay / 1000 + " 秒后重试");
        main.postDelayed(retry, delay);
    }
    private void disconnect() {
        generation++; heartbeat = 0; main.removeCallbacks(health);
        IClipboardBridge old = bridge; bridge = null; launchBridge = null;
        try { if (old != null) old.stop(); } catch (Exception ignored) { }
        if (bound) {
            bound = false;
            try {
                if ("root".equals(mode)) RootService.unbind(connection);
                else Shizuku.unbindUserService(args(), connection, true);
            } catch (RuntimeException ignored) { }
        }
    }
    @Override public void onTaskRemoved(Intent rootIntent) {
        PromptSafety.cancel(this);
        if (MonitorPolicy.shouldResume(Prefs.get(this).getBoolean("monitor_wanted", false), automatic()))
            Prefs.status(this, "任务已关闭 · 后台继续监听");
        else { disarm(this); Prefs.status(this, "任务已关闭 · 监听已停止"); stopSelf(); }
    }
    @Override public void onDestroy() {
        alive = false; running = false; main.removeCallbacksAndMessages(null);
        diagnostics = "监听已停止";
        disconnect();
        PromptSafety.cancel(this);
        Prefs.get(this).edit().putString("clipboard_diagnostics", "监听已停止").apply();
        MonitorTile.refresh(this);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
