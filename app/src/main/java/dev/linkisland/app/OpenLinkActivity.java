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
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        PromptSafety.cancel(this);
        original = getIntent().getStringExtra("original");
        String target = getIntent().getStringExtra("target"), pkg = getIntent().getStringExtra("package");
        if (!LinkRules.isWebUrl(original == null ? "" : original) || target == null) { finish(); return; }
        if (pkg == null || pkg.isEmpty()) { openBrowser(target); return; }
        requestOpen(new Intent(Intent.ACTION_VIEW, Uri.parse(target)).setPackage(pkg)
            .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), false);
    }
    private void unavailableTarget() {
        new AlertDialog.Builder(this).setTitle("目标 App 无法处理此链接")
            .setMessage("可能未安装，或不支持该页面。是否改用浏览器打开原始链接？")
            .setPositiveButton("浏览器打开", (d, w) -> openBrowser(original))
            .setNegativeButton("取消", (d, w) -> finish()).setOnCancelListener(d -> finish()).show();
    }
    private void openBrowser(String url) {
        if (!LinkRules.isWebUrl(url)) { finish(); return; }
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        List<BrowserAccess.Browser> browsers = BrowserAccess.installed(this);
        String selected = BrowserAccess.effectivePackage(this, browsers);
        if (!selected.isEmpty()) { launchBrowser(web.setPackage(selected)); return; }
        if (browsers.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("没有可用浏览器").setMessage("请先安装浏览器。")
                .setPositiveButton("知道了", (d, w) -> finish()).setOnCancelListener(d -> finish()).show();
            return;
        }
        String[] labels = browsers.stream().map(b -> b.label).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("选择浏览器").setItems(labels,
            (d, index) -> launchBrowser(web.setPackage(browsers.get(index).packageName)))
            .setOnCancelListener(d -> finish()).show();
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
                    if (opened) { Prefs.status(this, "已通过授权服务打开链接"); finish(); }
                    else normalOpen(intent, browser);
                });
            }, "link-launch").start();
        } else normalOpen(intent, browser);
    }
    private void normalOpen(Intent intent, boolean browser) {
        try { startActivity(intent); Prefs.status(this, browser ? "已请求浏览器打开链接" : "已请求目标 App 打开链接"); finish(); }
        catch (ActivityNotFoundException | SecurityException e) {
            if (browser) { Prefs.status(this, "浏览器启动失败"); finish(); }
            else unavailableTarget();
        }
    }
}
