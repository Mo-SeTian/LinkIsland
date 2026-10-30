package dev.linkisland.app.fixture;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** External-package clipboard and deep-link fixture, installed only with the androidTest APK. */
public final class CopyFixtureActivity extends Activity {
    private static ClipData originalClip;
    private static boolean clipSaved;
    private boolean focusActionDone;
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused || focusActionDone) return;
        String url = getIntent().getStringExtra("copy_on_focus");
        boolean restore = getIntent().getBooleanExtra("restore_clip", false);
        if (url == null && !restore) return;
        focusActionDone = true;
        getWindow().getDecorView().postDelayed(() -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            if (restore) {
                if (clipSaved) {
                    if (originalClip == null) clipboard.clearPrimaryClip();
                    else clipboard.setPrimaryClip(originalClip);
                    originalClip = null; clipSaved = false;
                }
                return;
            }
            // Keep the original clipboard in memory only; never log or persist its contents.
            if (!clipSaved) { originalClip = clipboard.getPrimaryClip(); clipSaved = true; }
            long wall = System.currentTimeMillis(), elapsed = SystemClock.elapsedRealtime();
            clipboard.setPrimaryClip(ClipData.newPlainText("latency fixture", url));
            getSharedPreferences("latency", MODE_PRIVATE).edit()
                .putString("copy_id", getIntent().getStringExtra("copy_id"))
                .putLong("copy_wall", wall).putLong("copy_elapsed", elapsed).commit();
        }, 100);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(30, 80, 30, 30);
        TextView target = new TextView(this);
        target.setText(getIntent().getData() == null ? "Copy fixture ready" : "Received: " + getIntent().getData());
        page.addView(target);
        copyButton(page, "Copy GitHub", "https://github.com/RikkaApps/Shizuku", false);
        copyButton(page, "Copy web", "https://example.org/link-island-test", false);
        copyButton(page, "Copy custom", "https://fixture.example/repo/42", false);
        copyButton(page, "Copy plain text", "just a test, no link", false);
        copyButton(page, "Copy sensitive URL", "https://example.org/sensitive-test", true);
        Button uri = new Button(this);
        uri.setText("Copy URI only");
        uri.setOnClickListener(v -> getSystemService(ClipboardManager.class)
            .setPrimaryClip(ClipData.newRawUri("fixture", Uri.parse("https://github.com/LinkSheet/LinkSheet"))));
        page.addView(uri);
        setContentView(page);
    }
    private void copyButton(LinearLayout page, String title, String text, boolean sensitive) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(v -> {
            ClipData clip = ClipData.newPlainText("fixture", text);
            if (sensitive) {
                PersistableBundle extras = new PersistableBundle();
                extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
                clip.getDescription().setExtras(extras);
            }
            getSystemService(ClipboardManager.class).setPrimaryClip(clip);
        });
        page.addView(button);
    }
}
