package dev.linkisland.app;

import android.content.ClipData;
import android.content.ClipDescription;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.lang.reflect.Method;

/** Runs only in the privileged process, shared by Shizuku and libsu. */
public class ClipboardBridge extends IClipboardBridge.Stub {
    private final HandlerThread thread = new HandlerThread("link-clipboard");
    private final Handler handler;
    private IClipboardCallback callback;
    private IBinder.DeathRecipient death;
    private Object clipboard, listener;
    private Method remove;
    private ClipboardAccess access;
    private ClipboardChangeTracker changes;
    private int userId;
    private volatile int clientUid = -1;
    private boolean diagnosticsVisible;
    private int events, checks, prompts;
    private String result = "尚未读取";
    private String listenerStatus = "未连接";
    private static final int BASELINE = 0, EVENT = 1, POLL = 2, MANUAL = 3;
    private static final String LISTENER = "android.content.IOnPrimaryClipChangedListener";
    private final Runnable retryEarly = () -> capture(EVENT);
    private final Runnable retryLate = () -> capture(EVENT);
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (callback == null) return;
            capture(POLL);
            handler.postDelayed(this, 2000);
        }
    };
    private final Runnable onEvent = this::handleEvent;
    private void handleEvent() {
        if (callback == null) return;
        events++;
        handler.removeCallbacks(retryEarly);
        handler.removeCallbacks(retryLate);
        if (!capture(EVENT)) {
            handler.postDelayed(retryEarly, 150);
            handler.postDelayed(retryLate, 500);
        }
    }

    public ClipboardBridge() {
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override public void start(IClipboardCallback cb, int user) {
        final int ownerUid = Binder.getCallingUid();
        // Binder uses the process leader's effective UID, not the worker thread's UID.
        new Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                if (android.system.Os.geteuid() == 0) android.system.Os.seteuid(2000);
                startWorker(cb, user, ownerUid);
            } catch (Exception e) {
                try { cb.onStatus("监听初始化失败 · " + causeName(e)); }
                catch (RemoteException ignored) { }
            }
        });
    }

    private void startWorker(IClipboardCallback cb, int user, int ownerUid) {
        // Execute off the incoming Binder thread, so system services see root/shell identity.
        handler.post(() -> {
            cleanup();
            callback = cb;
            userId = user;
            clientUid = ownerUid;
            changes = new ClipboardChangeTracker(System.currentTimeMillis());
            try {
                // ClipboardService verifies that the Binder caller owns com.android.shell,
                // including in Root mode. Root UID with a shell package fails that check.
                if (android.system.Os.geteuid() == 0) android.system.Os.seteuid(2000);
                death = () -> handler.post(this::cleanup);
                cb.asBinder().linkToDeath(death, 0);
                IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "clipboard");
                Class<?> stub = Class.forName("android.content.IClipboard$Stub");
                clipboard = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
                access = new ClipboardAccess(clipboard, userId);
                Class<?> type = Class.forName(LISTENER);
                Binder listenerBinder = new Binder() {
                    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                        if (code == INTERFACE_TRANSACTION) {
                            if (reply != null) reply.writeString(LISTENER);
                            return true;
                        }
                        if (code == FIRST_CALL_TRANSACTION) {
                            data.enforceInterface(LISTENER);
                            handler.post(onEvent);
                            return true;
                        }
                        return super.onTransact(code, data, reply, flags);
                    }
                };
                listener = Class.forName(LISTENER + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, listenerBinder);
                capture(BASELINE);
                try {
                    Method add = access.listenerMethod("addPrimaryClipChangedListener", type);
                    remove = access.listenerMethod("removePrimaryClipChangedListener", type);
                    add.invoke(clipboard, access.arguments(add, listener));
                    listenerStatus = "事件 + 定时检查";
                } catch (ReflectiveOperationException e) {
                    listenerStatus = "事件注册失败，定时检查 · " + causeName(e);
                }
                report("监听已连接 · EUID " + android.system.Os.geteuid() + " · " + listenerStatus);
                diagnostics(true);
                handler.postDelayed(poll, 2000);
            } catch (Exception e) {
                report("监听初始化失败 · " + causeName(e));
                cleanup();
            }
        });
    }

    private boolean capture(int source) {
        if (callback == null || access == null) return false;
        checks++;
        try {
            ClipData clip = (ClipData) access.read();
            if (clip == null || clip.getItemCount() == 0) {
                result = "系统返回空剪贴板（可能为空或被限制）";
                diagnostics();
                if (source == MANUAL) report(result);
                return false;
            }
            ClipDescription desc = clip.getDescription();
            if (desc != null && desc.getExtras() != null
                && desc.getExtras().getBoolean("android.content.extra.IS_SENSITIVE", false)) {
                result = "系统标记为敏感内容，已跳过";
                diagnostics();
                if (source == MANUAL) report(result);
                return true;
            }
            String url = null;
            boolean hasText = false, hasUri = false;
            for (int i = 0; i < clip.getItemCount() && url == null; i++) {
                ClipData.Item item = clip.getItemAt(i);
                CharSequence text = item.getText();
                hasText |= text != null && text.length() > 0;
                if (text != null) url = LinkRules.extract(text.toString());
                hasUri |= item.getUri() != null;
                if (url == null && item.getUri() != null) url = LinkRules.extract(item.getUri().toString());
            }
            long timestamp = desc == null ? 0 : desc.getTimestamp();
            result = url != null ? "已识别网页链接"
                : hasText ? "已读到文本，但未识别 HTTP / HTTPS 链接"
                : hasUri ? "已读到 URI，但不是网页链接" : "已读到剪贴板，未包含文本或网页 URI";
            if (source == BASELINE) changes.baseline(url, timestamp);
            else if (changes.shouldPrompt(url, timestamp, android.os.SystemClock.elapsedRealtime(), source == EVENT, source == MANUAL)) {
                callback.onLink(url);
                prompts++;
            }
            diagnostics();
            if (source == MANUAL && url == null) report(result);
            return true;
        } catch (Exception e) {
            result = "读取失败 · " + causeName(e);
            diagnostics();
            if (source == MANUAL) report(result);
            return false;
        }
    }
    private void diagnostics() { diagnostics(false); }
    private void diagnostics(boolean force) {
        try {
            if (callback == null) return;
            if (diagnosticsVisible || force) callback.onDiagnostics("事件 " + events + " · 检查 " + checks + " · 提示 " + prompts
                + "\n" + result + "\n读取接口 " + (access == null ? "不可用" : access.signature()));
            else callback.onHeartbeat();
        } catch (RemoteException ignored) { cleanup(); }
    }

    static String causeName(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        return error.getClass().getSimpleName();
    }
    private void report(String text) {
        try { if (callback != null) callback.onStatus(text); } catch (RemoteException ignored) { cleanup(); }
    }
    private void cleanup() {
        handler.removeCallbacks(poll);
        handler.removeCallbacks(onEvent);
        handler.removeCallbacks(retryEarly);
        handler.removeCallbacks(retryLate);
        if (clipboard != null && listener != null && remove != null && access != null) {
            try { remove.invoke(clipboard, access.arguments(remove, listener)); } catch (Exception ignored) { }
        }
        if (callback != null && death != null) callback.asBinder().unlinkToDeath(death, 0);
        clientUid = -1;
        diagnosticsVisible = false;
        callback = null; death = null; clipboard = null; listener = null; access = null; remove = null; changes = null;
        events = 0; checks = 0; prompts = 0; result = "尚未读取"; listenerStatus = "未连接";
    }
    @Override public boolean launchLink(String target, String packageName) {
        if (Binder.getCallingUid() != clientUid) return false;
        return PrivilegedLaunch.open(target, packageName, userId);
    }
    @Override public void stop() { handler.post(this::cleanup); }
    @Override public void probe() { handler.post(() -> capture(MANUAL)); }
    @Override public void setDiagnosticsVisible(boolean visible) {
        if (Binder.getCallingUid() != clientUid) return;
        handler.post(() -> { diagnosticsVisible = visible; if (visible) diagnostics(true); });
    }
    @Override public void destroy() { handler.post(() -> { cleanup(); thread.quitSafely(); }); }
}
