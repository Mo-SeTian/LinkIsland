package dev.linkisland.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.util.List;

public final class OpenLinkActivity extends Activity {
    private String original;
    private boolean expanded;
    private boolean pinduoduoTried;
    private String nativeFallback;
    private Thread resolver;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        PromptSafety.cancel(this);
        original = getIntent().getStringExtra("original");
        String target = getIntent().getStringExtra("target"), pkg = getIntent().getStringExtra("package");
        if (!LinkRules.isWebUrl(original == null ? "" : original) || target == null) { finish(); return; }
        JumpLog.add(this, "跳转", "已点击打开", original);
        if (pkg == null || pkg.isEmpty()) { openBrowser(target); return; }
        requestOpen(new Intent(Intent.ACTION_VIEW, Uri.parse(target)).setPackage(pkg)
            .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), false);
    }
    private void unavailableTarget() {
        unavailableTarget("可能未安装，或不支持该页面。是否改用浏览器打开原始链接？");
    }
    private void unavailableTarget(String message) {
        record("目标 App 无法处理链接，等待选择浏览器");
        new AlertDialog.Builder(this).setTitle("目标 App 无法处理此链接")
            .setMessage(message)
            .setPositiveButton("浏览器打开", (d, w) -> openBrowser(original))
            .setNegativeButton("取消", (d, w) -> cancelOpen()).setOnCancelListener(d -> cancelOpen()).show();
    }
    private void openBrowser(String url) {
        if (!LinkRules.isWebUrl(url)) { finish(); return; }
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        List<BrowserAccess.Browser> browsers = BrowserAccess.installed(this);
        String selected = BrowserAccess.effectivePackage(this, browsers);
        if (!selected.isEmpty()) { launchBrowser(web.setPackage(selected)); return; }
        if (browsers.isEmpty()) {
            record("没有可用浏览器");
            new AlertDialog.Builder(this).setTitle("没有可用浏览器").setMessage("请先安装浏览器。")
                .setPositiveButton("知道了", (d, w) -> finish()).setOnCancelListener(d -> finish()).show();
            return;
        }
        String[] labels = browsers.stream().map(b -> b.label).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("选择浏览器").setItems(labels,
            (d, index) -> launchBrowser(web.setPackage(browsers.get(index).packageName)))
            .setOnCancelListener(d -> cancelOpen()).show();
    }
    private void launchBrowser(Intent intent) { requestOpen(intent, true); }
    private void requestOpen(Intent intent, boolean browser) {
        android.content.pm.ResolveInfo resolved = getPackageManager().resolveActivity(intent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY | android.content.pm.PackageManager.MATCH_ALL);
        if (Prefs.get(this).getBoolean("privileged_open", false) && MonitorService.running
            && resolved != null && resolved.activityInfo.exported) {
            new Thread(() -> {
                boolean opened = MonitorService.launch(intent.getDataString(), intent.getPackage());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (opened) { record("已通过授权服务请求打开 · " + intent.getPackage()); finish(); }
                    else normalOpen(intent, browser);
                });
            }, "link-launch").start();
        } else normalOpen(intent, browser);
    }
    private void normalOpen(Intent intent, boolean browser) {
        try { startActivity(intent); record((browser ? "已请求浏览器打开链接" : "已请求目标 App 打开链接") + " · " + intent.getPackage()); finish(); }
        catch (ActivityNotFoundException | SecurityException e) {
            if (browser) { record("浏览器启动失败"); finish(); }
            else if (nativeFallback != null) {
                String target = nativeFallback; nativeFallback = null;
                requestOpen(new Intent(intent).setData(Uri.parse(target)), false);
            } else if (!pinduoduoTried && targetInstalled(intent.getPackage())
                && PddLinks.nativeTarget(intent.getDataString(), intent.getPackage()) != null) {
                pinduoduoTried = true;
                record("正在通过拼多多原生链接打开");
                requestOpen(new Intent(intent).setData(Uri.parse(PddLinks.nativeTarget(intent.getDataString(), intent.getPackage()))), false);
            } else if (!expanded && XhsLinks.supports(intent.getDataString(), intent.getPackage()) && targetInstalled(intent.getPackage())) {
                expandShortLink(intent);
            } else unavailableTarget();
        }
    }
    private boolean targetInstalled(String pkg) {
        try { return getPackageManager().getApplicationInfo(pkg, 0).enabled; }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { return false; }
    }
    private void expandShortLink(Intent intent) {
        expanded = true;
        record("正在解析小红书短链接…");
        android.widget.Toast.makeText(this, "正在解析小红书短链接…", android.widget.Toast.LENGTH_SHORT).show();
        resolver = new Thread(() -> {
            try {
                String target = XhsLinks.resolve(intent.getDataString());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    nativeFallback = XhsLinks.nativeNote(target);
                    requestOpen(new Intent(intent).setData(Uri.parse(target)), false);
                });
            } catch (java.io.IOException e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    record("小红书短链解析失败 · " + e.getClass().getSimpleName());
                    unavailableTarget("小红书已安装，但短链接解析失败。请检查网络后重试，或使用浏览器打开原始链接。");
                });
            }
        }, "xhs-link-resolve");
        resolver.start();
    }
    private void record(String detail) {
        Prefs.status(this, detail);
        JumpLog.add(this, "跳转", detail, original);
    }
    private void cancelOpen() { record("已取消跳转"); finish(); }
    @Override public void onDestroy() {
        if (resolver != null) resolver.interrupt();
        super.onDestroy();
    }
}
