package dev.linkisland.app.fixture;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import java.lang.reflect.Method;
import java.util.Map;
import org.json.JSONObject;

/** Clicks real PendingIntents; clipboard content is retained only in memory and never reported. */
public final class ShareCodeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Context c = getTargetContext();
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        SharedPreferences history = c.getSharedPreferences("jump_log", Context.MODE_PRIVATE);
        Map<String, ?> saved = p.getAll(), savedHistory = history.getAll();
        ClipboardManager clipboard = c.getSystemService(ClipboardManager.class);
        ClipData original = null;
        boolean clipboardSaved = false;
        Activity activity = null;
        Method cancel = null;
        Bundle result = new Bundle();
        String stage = "setup";
        try {
            ClassLoader loader = c.getClassLoader();
            Class<?> notifications = Class.forName("dev.linkisland.app.LinkNotifications", true, loader);
            Method show = notifications.getDeclaredMethod("show", Context.class, String.class, String.class); show.setAccessible(true);
            cancel = Class.forName("dev.linkisland.app.PromptSafety", true, loader).getDeclaredMethod("cancel", Context.class); cancel.setAccessible(true);
            Class<?> service = Class.forName("dev.linkisland.app.MonitorService", true, loader);
            Method stop = service.getDeclaredMethod("stopListening", Context.class); stop.setAccessible(true); stop.invoke(null, c);
            p.edit().putBoolean("auto_restart", false).putBoolean("monitor_wanted", false)
                .putString("rules", "[{\"name\":\"网盘测试\",\"type\":\"regex\",\"pattern\":\"(?i)^https?://\"}]")
                .putInt("prompt_seconds", 30).commit();
            Intent main = new Intent().setClassName(c.getPackageName(), "dev.linkisland.app.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(main);
            Activity window = activity;
            await(window::hasWindowFocus);
            original = clipboard.getPrimaryClip(); clipboardSaved = true;
            String url = "https://pan.baidu.com/s/link-island-fixture";
            for (boolean island : new boolean[]{false, true}) {
                stage = island ? "island copy" : "ordinary copy";
                p.edit().putBoolean("island", island).commit();
                clipboard.setPrimaryClip(ClipData.newPlainText("fixture", "before-copy"));
                post(show, c, url, "a1B2");
                check(text(clipboard).equals("before-copy"), "Prompt modified clipboard before a tap");
                StatusBarNotification posted = prompt(c);
                Notification.Action copy = copy(posted);
                check(copy != null && copy.actionIntent.isBroadcast(), "Missing copy broadcast action");
                check(posted.getNotification().actions.length == 3, "Unexpected action count");
                check((posted.getNotification().flags & Notification.FLAG_NO_CLEAR) != 0, "Clear protection lost");
                check(!posted.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT, "").toString().contains("a1B2"), "Code visible in notification text");
                if (island) {
                    JSONObject payload = new JSONObject(posted.getNotification().extras.getString("miui.focus.param"));
                    check(!payload.toString().contains("a1B2") && !payload.getJSONObject("param_v2").has("textButton"), "Unsafe or private island data");
                    check("pan.baidu.com".equals(payload.getJSONObject("param_v2").getJSONObject("iconTextInfo").getString("content")), "Island domain text is crowded by status labels");
                    Bundle actions = posted.getNotification().extras.getBundle("miui.focus.actions");
                    for (String name : new String[]{"open", "copy_code"}) {
                        Notification.Action action = actions.getParcelable("miui.focus.action_" + name);
                        check(action.getIcon().getType() == android.graphics.drawable.Icon.TYPE_BITMAP, "Island action is unpadded resource artwork");
                        android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(224, 224, android.graphics.Bitmap.Config.ARGB_8888);
                        android.graphics.drawable.Drawable drawable = action.getIcon().loadDrawable(c);
                        drawable.setBounds(0, 0, 224, 224); drawable.draw(new android.graphics.Canvas(image));
                        int minX = 224, minY = 224, maxX = 0, maxY = 0, whiteMinX = 224, whiteMaxX = 0;
                        for (int y = 0; y < 224; y++) for (int x = 0; x < 224; x++) {
                            int pixel = image.getPixel(x, y);
                            if (android.graphics.Color.alpha(pixel) > 0) { minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); }
                            if (android.graphics.Color.alpha(pixel) > 200 && android.graphics.Color.red(pixel) > 235
                                && android.graphics.Color.green(pixel) > 235 && android.graphics.Color.blue(pixel) > 235) { whiteMinX = Math.min(whiteMinX, x); whiteMaxX = Math.max(whiteMaxX, x); }
                        }
                        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(c.getCacheDir(), name + "-action-fixture.png"))) {
                            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
                        }
                        check(minX >= 24 && minY >= 24 && maxX < 200 && maxY < 200, "Missing action outer padding");
                        check(whiteMaxX > whiteMinX && whiteMaxX - whiteMinX < 90, "Action glyph fills the button");
                    }
                }
                String id = p.getString("prompt_log_id", ""); long deadline = p.getLong("prompt_deadline", 0);
                // The real BroadcastReceiver must copy while another task owns the foreground.
                runOnMainSync(() -> c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
                await(() -> !window.hasWindowFocus());
                copy.actionIntent.send(); Thread.sleep(300);
                check(!window.hasWindowFocus(), "Copy unexpectedly opened the main activity");
                runOnMainSync(() -> c.startActivity(main)); await(window::hasWindowFocus);
                await(() -> text(clipboard).equals("a1B2"));
                check(clipboard.getPrimaryClip().getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE"), "Copied code not marked sensitive");
                check(p.getBoolean("prompt_active", false) && id.equals(p.getString("prompt_log_id", ""))
                    && deadline == p.getLong("prompt_deadline", 0) && prompt(c).getPostTime() == posted.getPostTime(), "Copy replaced or extended prompt");

                stage = "stale and expired actions";
                post(show, c, url, "z9Y8");
                Notification.Action current = copy(prompt(c));
                clipboard.setPrimaryClip(ClipData.newPlainText("fixture", "current-marker"));
                copy.actionIntent.send(); Thread.sleep(150);
                check(text(clipboard).equals("current-marker"), "Replaced action overwrote clipboard");
                current.actionIntent.send(); await(() -> text(clipboard).equals("z9Y8"));
                clipboard.setPrimaryClip(ClipData.newPlainText("fixture", "expired-marker"));
                p.edit().putLong("prompt_deadline", SystemClock.elapsedRealtime() - 1).commit();
                current.actionIntent.send(); Thread.sleep(150);
                check(text(clipboard).equals("expired-marker"), "Expired action overwrote clipboard");
                cancel.invoke(null, c);
                current.actionIntent.send(); Thread.sleep(150);
                check(text(clipboard).equals("expired-marker"), "Cancelled action overwrote clipboard");
            }
            stage = "non-share and privacy";
            post(show, c, url, null); check(copy(prompt(c)) == null, "Missing code offered copy");
            post(show, c, "https://example.org/fixture", "a1B2"); check(copy(prompt(c)) == null, "Non-cloud URL offered password copy");
            check(!p.getAll().toString().contains("a1B2") && !p.getAll().toString().contains("z9Y8"), "Code saved in settings");
            check(!history.getAll().toString().contains("a1B2") && !history.getAll().toString().contains("z9Y8"), "Code saved in history");
            result.putString("result", "PASS copy only on tap; background write keeps source task; sensitive flag; old/expired/cancelled actions rejected; prompt lifetime unchanged; no code in logs or settings");
        } catch (Throwable error) { result.putString("test_error", stage + ": " + error.getClass().getSimpleName()
            + (error instanceof AssertionError ? " · " + error.getMessage() : "")); }
        finally {
            try { if (cancel != null) cancel.invoke(null, c); } catch (Exception ignored) { }
            if (clipboardSaved) { if (original == null) clipboard.clearPrimaryClip(); else clipboard.setPrimaryClip(original); }
            SharedPreferences.Editor edit = p.edit();
            for (String key : new String[]{"rules", "auto_restart", "monitor_wanted", "island", "prompt_seconds"}) {
                if (!saved.containsKey(key)) edit.remove(key);
                else if (saved.get(key) instanceof String) edit.putString(key, (String) saved.get(key));
                else if (saved.get(key) instanceof Boolean) edit.putBoolean(key, (Boolean) saved.get(key));
                else edit.putInt(key, (Integer) saved.get(key));
            }
            edit.commit();
            SharedPreferences.Editor logs = history.edit().clear();
            for (Map.Entry<String, ?> entry : savedHistory.entrySet()) if (entry.getValue() instanceof String) logs.putString(entry.getKey(), (String) entry.getValue());
            logs.commit();
            if (activity != null) { Activity closing = activity; runOnMainSync(closing::finish); }
        }
        finish(result.containsKey("test_error") ? 1 : 0, result);
    }
    private static Notification.Action copy(StatusBarNotification notification) {
        if (notification != null && notification.getNotification().actions != null)
            for (Notification.Action action : notification.getNotification().actions) if ("复制提取码".contentEquals(action.title)) return action;
        return null;
    }
    private static void post(Method show, Context c, String url, String code) throws Exception {
        StatusBarNotification previous = prompt(c);
        android.app.PendingIntent old = previous == null ? null : previous.getNotification().contentIntent;
        show.invoke(null, c, url, code);
        // NotificationManager queues posting; inspect this prompt rather than the preceding snapshot.
        await(() -> { StatusBarNotification current = prompt(c);
            return current != null && !java.util.Objects.equals(old, current.getNotification().contentIntent); });
    }
    private static StatusBarNotification prompt(Context c) {
        for (StatusBarNotification n : c.getSystemService(NotificationManager.class).getActiveNotifications()) if (n.getId() == 101) return n;
        return null;
    }
    private static String text(ClipboardManager manager) {
        ClipData clip = manager.getPrimaryClip();
        return clip == null || clip.getItemCount() == 0 || clip.getItemAt(0).getText() == null ? "" : clip.getItemAt(0).getText().toString();
    }
    private interface Condition { boolean ready() throws Exception; }
    private static void await(Condition condition) throws Exception {
        long end = SystemClock.uptimeMillis() + 5000;
        while (!condition.ready() && SystemClock.uptimeMillis() < end) Thread.sleep(25);
        check(condition.ready(), "Timed out waiting for state");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
