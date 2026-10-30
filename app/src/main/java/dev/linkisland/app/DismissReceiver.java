package dev.linkisland.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class DismissReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        PromptSafety.cancel(c);
    }
}
