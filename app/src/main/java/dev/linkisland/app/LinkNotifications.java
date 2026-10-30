package dev.linkisland.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import java.util.Map;
import org.json.JSONObject;

final class LinkNotifications {
    static final int PROMPT_ID = 101, SERVICE_ID = 100;
    static final String PROMPTS = "link_prompts", MONITOR = "clipboard_monitor";
    static void channels(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        NotificationChannel prompts = new NotificationChannel(PROMPTS, "链接跳转提示", NotificationManager.IMPORTANCE_HIGH);
        prompts.setDescription("复制链接后询问是否打开，30 秒自动消失");
        prompts.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        nm.createNotificationChannel(prompts);
        nm.createNotificationChannel(new NotificationChannel(MONITOR, "剪贴板监听状态", NotificationManager.IMPORTANCE_LOW));
    }
    static Notification service(Context c, String mode) {
        channels(c);
        PendingIntent home = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(c, 2, new Intent(c, MonitorService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(c, MONITOR).setSmallIcon(R.drawable.ic_link)
            .setContentTitle("链接岛 · " + mode).setContentText("监听复制事件 · 点击管理")
            .setContentIntent(home).setOngoing(true).setShowWhen(false)
            .addAction(new Notification.Action.Builder(null, "停止监听", stop).build()).build();
    }
    static void show(Context c, String url) {
        try {
            PromptSafety.cancel(c);
            channels(c);
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (!nm.areNotificationsEnabled() || nm.getNotificationChannel(PROMPTS).getImportance() == NotificationManager.IMPORTANCE_NONE) {
                Prefs.status(c, "提示被关闭 · 请开启通知权限和链接跳转提示通道");
                return;
            }
            LinkRules.Route route = LinkRules.resolve(url, LinkRules.parse(Prefs.rules(c)));
            Intent open = new Intent(c, OpenLinkActivity.class).putExtra("original", route.original)
                .putExtra("target", route.target).putExtra("package", route.packageName)
                // An empty-affinity task must never bring MainActivity above the source app.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            // Unique identity means an older tap can never be rebound to a newly copied link.
            open.setData(Uri.parse("linkisland:prompt/" + java.util.UUID.randomUUID()));
            PendingIntent pi = PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
            PendingIntent dismiss = PendingIntent.getBroadcast(c, 4, new Intent(c, DismissReceiver.class), PendingIntent.FLAG_IMMUTABLE);
            Notification.Action openAction = new Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_link), "打开", pi).build();
            Notification.Action dismissAction = new Notification.Action.Builder(null, "忽略", dismiss).build();
            String label = route.packageName.isEmpty() ? BrowserAccess.label(c) : route.label;
            String title = "使用 " + label + " 打开？";
            // Show only the hostname; query strings and clipboard text never appear in diagnostics.
            String host = URIHost(url);
            Icon targetIcon = Icon.createWithBitmap(TargetAppIcon.load(c, route.packageName));
            Notification.Builder builder = new Notification.Builder(c, PROMPTS)
                .setSmallIcon(R.drawable.ic_link).setLargeIcon(targetIcon).setContentTitle(title).setContentText(host)
                .setContentIntent(pi).setDeleteIntent(dismiss).setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true)
                .setTimeoutAfter(PromptSafety.TIMEOUT_MS).setShowWhen(false).setOnlyAlertOnce(false)
                .addAction(openAction).addAction(dismissAction);
            Notification ordinary = builder.build();
            Notification notification = ordinary;
            boolean useIsland = false;
            String fallback = "";
            if (Prefs.get(c).getBoolean("island", true)) {
                try {
                    Bundle extras = new Bundle(), actions = new Bundle(), pics = new Bundle();
                    actions.putParcelable("miui.focus.action_open", openAction);
                    actions.putParcelable("miui.focus.action_dismiss", dismissAction);
                    pics.putParcelable(IslandPayload.ICON, targetIcon);
                    boolean modern = android.provider.Settings.System.getInt(c.getContentResolver(), "notification_focus_protocol", 0) >= 3;
                    JSONObject payload = IslandPayload.create(label, host, modern);
                    if (IslandPayloadValidator.isSafe(payload, pics.keySet(), Map.of(
                        "miui.focus.action_open", openAction.title.toString(),
                        "miui.focus.action_dismiss", dismissAction.title.toString()))) {
                        extras.putString("miui.focus.param", payload.toString());
                        extras.putBundle("miui.focus.actions", actions);
                        extras.putBundle("miui.focus.pics", pics);
                        extras.putBoolean("miui.island.firstFloat", true);
                        extras.putBoolean("miui.enableFloat", true);
                        notification = Notification.Builder.recoverBuilder(c, ordinary).addExtras(extras).build();
                        useIsland = true;
                    } else {
                        fallback = " · 超级岛参数未通过检查，已暂停";
                        PromptSafety.disableIsland(c, "超级岛参数未通过检查 · 已改用普通通知");
                    }
                } catch (RuntimeException | org.json.JSONException e) {
                    fallback = " · 超级岛构建异常，已暂停";
                    PromptSafety.disableIsland(c, "超级岛构建异常 · 已改用普通通知");
                }
            }
            try {
                PromptSafety.arm(c, useIsland);
                nm.notify(PROMPT_ID, notification);
            } catch (RuntimeException e) {
                PromptSafety.cancel(c);
                if (!useIsland) throw e;
                PromptSafety.disableIsland(c, "超级岛通知发送异常 · 已改用普通通知");
                PromptSafety.arm(c, false);
                nm.notify(PROMPT_ID, ordinary);
                useIsland = false;
                fallback = " · 超级岛发送异常，已暂停";
            }
            Prefs.status(c, useIsland
                ? "已发送岛协议通知 · 实际展示请看手机，未授权时可能退化为普通通知"
                : "已发送普通通知" + fallback);
        } catch (Exception e) {
            try { PromptSafety.cancel(c); } catch (RuntimeException ignored) { }
            Prefs.status(c, "规则或通知处理失败 · " + ClipboardBridge.causeName(e));
        }
    }
    static String URIHost(String url) { return java.net.URI.create(url).getHost(); }
}
