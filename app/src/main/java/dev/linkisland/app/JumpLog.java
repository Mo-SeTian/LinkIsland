package dev.linkisland.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URI;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local, bounded event history. Clipboard text, URL paths and query strings are never stored. */
final class JumpLog {
    static SharedPreferences preferences(Context c) { return c.getSharedPreferences("jump_log", Context.MODE_PRIVATE); }
    static String read(Context c) { return preferences(c).getString("entries", "[]"); }
    static void clear(Context c) { preferences(c).edit().remove("entries").apply(); }
    static synchronized void add(Context c, String stage, String detail, String url) {
        String old = read(c), updated = append(old, stage, detail, url, System.currentTimeMillis());
        if (!old.equals(updated)) preferences(c).edit().putString("entries", updated).apply();
    }
    static String append(String old, String stage, String detail, String url, long time) {
        try {
            String host = "";
            if (url != null) try {
                String parsed = URI.create(url).getHost();
                if (parsed != null) host = parsed;
            } catch (IllegalArgumentException ignored) { }
            JSONArray entries = new JSONArray(old);
            JSONObject last = entries.optJSONObject(entries.length() - 1);
            if (last != null && stage.equals(last.optString("stage")) && detail.equals(last.optString("detail"))
                && host.equals(last.optString("host")) && time >= last.optLong("time") && time - last.optLong("time") < 1000) return old;
            JSONArray result = new JSONArray();
            for (int i = Math.max(0, entries.length() - 99); i < entries.length(); i++) result.put(entries.get(i));
            result.put(new JSONObject().put("time", time).put("stage", stage).put("detail", detail).put("host", host));
            return result.toString();
        } catch (org.json.JSONException e) { return "[]"; }
    }
}
