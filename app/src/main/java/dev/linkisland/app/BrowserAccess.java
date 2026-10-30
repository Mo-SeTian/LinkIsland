package dev.linkisland.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class BrowserAccess {
    static final String ASK = "ask";
    static final class Browser {
        final String packageName, label;
        Browser(String packageName, String label) { this.packageName = packageName; this.label = label; }
    }
    static Intent probe() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org"))
            .addCategory(Intent.CATEGORY_BROWSABLE);
    }
    static List<Browser> installed(Context c) {
        LinkedHashMap<String, Browser> browsers = new LinkedHashMap<>();
        for (ResolveInfo info : c.getPackageManager().queryIntentActivities(probe(),
            PackageManager.MATCH_ALL | PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER)) {
            // A browser must handle generic web URLs, not just a specific site's App Links.
            if (info.filter != null && info.filter.countDataAuthorities() == 0 && info.filter.countDataPaths() == 0)
                browsers.put(info.activityInfo.packageName, new Browser(info.activityInfo.packageName,
                    info.loadLabel(c.getPackageManager()).toString()));
        }
        List<Browser> result = new ArrayList<>(browsers.values());
        result.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return result;
    }
    static String selection(Context c) { return Prefs.get(c).getString("browser", ""); }
    static String effectivePackage(Context c, List<Browser> browsers) {
        String choice = selection(c);
        if (ASK.equals(choice)) return "";
        for (Browser browser : browsers) if (browser.packageName.equals(choice)) return choice;
        ResolveInfo selected = c.getPackageManager().resolveActivity(probe(), PackageManager.MATCH_DEFAULT_ONLY);
        if (selected != null) for (Browser browser : browsers)
            if (browser.packageName.equals(selected.activityInfo.packageName)) return browser.packageName;
        return browsers.size() == 1 ? browsers.get(0).packageName : "";
    }
    static String label(Context c) {
        if (ASK.equals(selection(c))) return "浏览器（每次询问）";
        List<Browser> browsers = installed(c);
        String effective = effectivePackage(c, browsers);
        for (Browser browser : browsers) if (browser.packageName.equals(effective)) return browser.label;
        return "默认浏览器";
    }
}
