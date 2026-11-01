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
        prompts.setDescription("复制链接后询问是否打开，按设置的停留时间自动消失");
        prompts.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        nm.createNotificationChannel(prompts);
        nm.createNotificationChannel(new NotificationChannel(MONITOR, "剪贴板监听状态", NotificationManager.IMPORTANCE_LOW));
    }
    static Notification service(Context c, String mode) {
        channels(c);
        PendingIntent home = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(c, 2, new Intent(c, MonitorService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(c, MONITOR).setSmallIcon(R.drawable.ic_link)
            .setContentTitle("链接岛 · " + mode).setContentText(MonitorService.paused(c)
                ? "链接提示已暂停 · 到期自动恢复" : "监听复制事件 · 点击管理")
            .setContentIntent(home).setOngoing(true).setShowWhen(false)
            .addAction(new Notification.Action.Builder(null, "停止监听", stop).build()).build();
    }
    static void show(Context c, String url) {
        show(c, url, PanShareCodes.extract(null, url));
    }
    static void show(Context c, String url, String code) {
        String id = JumpLog.begin(c, "识别", "已识别网页链接", url, "pending");
        try {
            LinkRules.Route route = LinkRules.resolve(url, Prefs.parsedRules(c));
            if (route == null) {
                JumpLog.step(c, id, "匹配", "未命中规则，未提示或跳转", "skipped");
                return;
            }
            String previous = Prefs.get(c).getString("prompt_log_id", "");
            if (Prefs.get(c).getBoolean("prompt_active", false))
                JumpLog.step(c, previous, "提示", "提示被新链接替换", "replaced");
            PromptSafety.cancel(c);
            channels(c);
            int seconds = Prefs.promptSeconds(c);
            String targetPackage = CloudLinks.targetPackage(c, route.original, route.target, route.packageName);
            String label = targetPackage.isEmpty() ? BrowserAccess.label(c) : route.label;
            if (!targetPackage.equals(route.packageName)) {
                label = c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(targetPackage, 0)).toString();
                JumpLog.step(c, id, "匹配", "原目标未安装或已停用，使用已安装的夸克应用", "");
            }
            JumpLog.matched(c, id, route.ruleKey, "命中规则「" + route.ruleName + "」，目标：" + label);
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (!nm.areNotificationsEnabled() || nm.getNotificationChannel(PROMPTS).getImportance() == NotificationManager.IMPORTANCE_NONE) {
                Prefs.status(c, "提示被关闭 · 请开启通知权限和链接跳转提示通道");
                JumpLog.step(c, id, "提示", "通知权限或提示通道关闭，未发送", "failed");
                return;
            }
            boolean hasCode = PanShareCodes.supports(url) && PanShareCodes.valid(code);
            Intent open = new Intent(c, OpenLinkActivity.class).putExtra("original", route.original)
                .putExtra("target", route.target).putExtra("package", targetPackage).putExtra("log_id", id)
                // An empty-affinity task must never bring MainActivity above the source app.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            if (hasCode) open.putExtra("share_code", code);
            // Unique identity means an older tap can never be rebound to a newly copied link.
            open.setData(Uri.parse("linkisland:prompt/" + java.util.UUID.randomUUID()));
            PendingIntent pi = PendingIntent.getActivity(c, 3, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
            PendingIntent dismiss = PendingIntent.getBroadcast(c, 4, new Intent(c, DismissReceiver.class)
                .putExtra("log_id", id).setData(Uri.parse("linkisland:dismiss/" + id)), PendingIntent.FLAG_IMMUTABLE);
            Notification.Action openAction = new Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_link), "打开", pi).build();
            Notification.Action dismissAction = new Notification.Action.Builder(null, "忽略", dismiss).build();
            Notification.Action copyAction = null;
            if (hasCode) {
                Intent copy = new Intent(c, CopyCodeReceiver.class).putExtra("log_id", id).putExtra("code", code)
                    .setData(Uri.parse("linkisland:copy-code/" + id)).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                PendingIntent copyIntent = PendingIntent.getBroadcast(c, 6, copy, PendingIntent.FLAG_IMMUTABLE);
                copyAction = new Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_copy), "复制提取码", copyIntent).build();
            }
            String title = "使用 " + label + " 打开？";
            // Show only the hostname; query strings and clipboard text never appear in diagnostics.
            String host = URIHost(url);
            Icon targetIcon = Icon.createWithBitmap(TargetAppIcon.load(c, targetPackage));
            Notification.Builder builder = new Notification.Builder(c, PROMPTS)
                .setSmallIcon(R.drawable.ic_link).setLargeIcon(targetIcon).setContentTitle(title).setContentText(host + (hasCode ? " · 含提取码" : ""))
                .setContentIntent(pi).setDeleteIntent(dismiss).setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true)
                .setTimeoutAfter(seconds * 1000L).setShowWhen(false).setOnlyAlertOnce(false)
                .addAction(openAction);
            if (copyAction != null) builder.addAction(copyAction);
            builder.addAction(dismissAction);
            Notification ordinary = builder.build();
            // Keep the pending choice when the user clears unrelated notifications.
            ordinary.flags |= Notification.FLAG_NO_CLEAR;
            Notification notification = ordinary;
            boolean useIsland = false;
            String fallback = "";
            if (Prefs.get(c).getBoolean("island", true)) {
                try {
                    Bundle extras = new Bundle(), actions = new Bundle(), pics = new Bundle();
                    actions.putParcelable("miui.focus.action_open", hasCode
                        ? new Notification.Action.Builder(IslandActionIcon.open(c), openAction.title, pi).build() : openAction);
                    actions.putParcelable("miui.focus.action_dismiss", dismissAction);
                    if (copyAction != null) actions.putParcelable("miui.focus.action_copy_code",
                        new Notification.Action.Builder(IslandActionIcon.copy(c), copyAction.title, copyAction.actionIntent).build());
                    pics.putParcelable(IslandPayload.ICON, targetIcon);
                    boolean modern = android.provider.Settings.System.getInt(c.getContentResolver(), "notification_focus_protocol", 0) >= 3;
                    JSONObject payload = IslandPayload.create(label, host, modern, seconds, hasCode);
                    Map<String, String> titles = new java.util.HashMap<>();
                    titles.put("miui.focus.action_open", openAction.title.toString());
                    titles.put("miui.focus.action_dismiss", dismissAction.title.toString());
                    if (copyAction != null) titles.put("miui.focus.action_copy_code", copyAction.title.toString());
                    if (IslandPayloadValidator.isSafe(payload, pics.keySet(), titles)) {
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
                PromptSafety.arm(c, useIsland, seconds * 1000L, id);
                nm.notify(PROMPT_ID, notification);
            } catch (RuntimeException e) {
                PromptSafety.cancel(c);
                if (!useIsland) throw e;
                PromptSafety.disableIsland(c, "超级岛通知发送异常 · 已改用普通通知");
                PromptSafety.arm(c, false, seconds * 1000L, id);
                nm.notify(PROMPT_ID, ordinary);
                useIsland = false;
                fallback = " · 超级岛发送异常，已暂停";
            }
            Prefs.status(c, useIsland
                ? "已发送岛协议通知 · 实际展示请看手机，未授权时可能退化为普通通知"
                : "已发送普通通知" + fallback);
            JumpLog.step(c, id, "提示", (useIsland ? "已提交超级岛通知" : "已发送普通通知" + fallback)
                + " · " + seconds + " 秒后自动消失", "");
        } catch (Exception e) {
            try { PromptSafety.cancel(c); } catch (RuntimeException ignored) { }
            Prefs.status(c, "规则或通知处理失败 · " + ClipboardBridge.causeName(e));
            JumpLog.step(c, id, "失败", "规则或通知处理失败 · " + ClipboardBridge.causeName(e), "failed");
        }
    }
    static String URIHost(String url) { return java.net.URI.create(url).getHost(); }
}
