package dev.linkisland.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URI;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** A bounded local history grouped by explicit copy IDs; never stores full links. */
final class JumpLog {
    static SharedPreferences preferences(Context c) { return c.getSharedPreferences("jump_log", Context.MODE_PRIVATE); }
    static synchronized String read(Context c) {
        SharedPreferences p = preferences(c);
        if (p.contains("groups")) return p.getString("groups", "[]");
        String groups = migrate(p.getString("entries", "[]"));
        p.edit().putString("groups", groups).remove("entries").apply();
        return groups;
    }
    static synchronized void clear(Context c) { preferences(c).edit().putString("groups", "[]").remove("entries").apply(); }
    static synchronized String begin(Context c, String stage, String detail, String url, String status) {
        String id = UUID.randomUUID().toString();
        save(c, begin(read(c), id, stage, detail, url, System.currentTimeMillis(), status));
        return id;
    }
    static synchronized void step(Context c, String id, String stage, String detail, String status) {
        String old = read(c), updated = step(old, id, stage, detail, System.currentTimeMillis(), status);
        if (!old.equals(updated)) save(c, updated);
    }
    static synchronized void matched(Context c, String id, String key, String detail) {
        String old = read(c), updated = step(old, id, "匹配", detail, System.currentTimeMillis(), "", key);
        if (!old.equals(updated)) save(c, updated);
    }
    static synchronized void recognition(Context c, String detail) {
        try {
            JSONArray groups = new JSONArray(read(c));
            JSONObject last = groups.optJSONObject(groups.length() - 1);
            long elapsed = last == null ? Long.MAX_VALUE : System.currentTimeMillis() - last.optLong("updated");
            if (last != null && last.optString("host").isEmpty() && detail.equals(last.optString("detail"))
                && elapsed >= 0 && elapsed < 1000) return;
        } catch (org.json.JSONException ignored) { }
        begin(c, "识别", detail, null, detail.startsWith("读取失败") ? "failed" : "skipped");
    }
    static synchronized void closePending(Context c, String id) {
        if (id.isEmpty()) return;
        try {
            JSONArray groups = new JSONArray(read(c));
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.getJSONObject(i);
                if (id.equals(group.optString("id")) && "pending".equals(group.optString("status"))) {
                    step(c, id, "提示", "提示已撤销", "cancelled");
                    return;
                }
            }
        } catch (org.json.JSONException ignored) { }
    }
    private static void save(Context c, String value) { preferences(c).edit().putString("groups", value).apply(); }

    static String begin(String old, String id, String stage, String detail, String url, long time, String status) {
        try {
            JSONArray groups = new JSONArray(old), result = new JSONArray();
            for (int i = Math.max(0, groups.length() - 99); i < groups.length(); i++) result.put(groups.get(i));
            JSONObject group = new JSONObject().put("id", id).put("time", time).put("host", host(url)).put("events", new JSONArray());
            addEvent(group, stage, detail, time, status);
            result.put(group);
            return result.toString();
        } catch (org.json.JSONException e) { return old; }
    }
    static String step(String old, String id, String stage, String detail, long time, String status) {
        return step(old, id, stage, detail, time, status, "");
    }
    static String step(String old, String id, String stage, String detail, long time, String status, String ruleKey) {
        if (id == null || id.isEmpty()) return old;
        try {
            JSONArray groups = new JSONArray(old);
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.getJSONObject(i);
                if (!id.equals(group.optString("id"))) continue;
                addEvent(group, stage, detail, time, status);
                if (!ruleKey.isEmpty()) group.put("rule_key", ruleKey);
                return groups.toString();
            }
        } catch (org.json.JSONException ignored) { }
        // A cleared or evicted history entry must not reappear after a late tap.
        return old;
    }
    private static void addEvent(JSONObject group, String stage, String detail, long time, String status) throws org.json.JSONException {
        JSONArray events = group.getJSONArray("events"), bounded = new JSONArray();
        for (int i = Math.max(0, events.length() - 19); i < events.length(); i++) bounded.put(events.get(i));
        bounded.put(new JSONObject().put("time", time).put("stage", stage).put("detail", detail));
        group.put("events", bounded).put("updated", time).put("detail", detail);
        if (!status.isEmpty()) group.put("status", status);
    }
    static String migrate(String old) {
        try {
            JSONArray entries = new JSONArray(old);
            String result = "[]";
            for (int i = Math.max(0, entries.length() - 100); i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                String detail = entry.optString("detail");
                String state = detail.contains("失败") || detail.contains("无法处理") || detail.contains("未发送") ? "failed" : "legacy";
                result = begin(result, "legacy-" + i, entry.optString("stage"), detail, null, entry.optLong("time"), state);
                JSONArray groups = new JSONArray(result);
                groups.getJSONObject(groups.length() - 1).put("host", host("https://" + entry.optString("host")));
                result = groups.toString();
            }
            return result;
        } catch (org.json.JSONException e) { return "[]"; }
    }
    private static String host(String url) {
        if (url != null) try { String value = URI.create(url).getHost(); if (value != null) return value; }
        catch (IllegalArgumentException ignored) { }
        return "";
    }
}
