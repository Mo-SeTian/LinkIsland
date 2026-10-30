package dev.linkisland.app.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Test-only generic browser used to verify selection without changing the system default. */
public final class BrowserFixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setPadding(30, 100, 30, 30);
        text.setText("Received browser URL: " + getIntent().getData());
        setContentView(text);
    }
}
