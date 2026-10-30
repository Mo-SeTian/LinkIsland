package dev.linkisland.app;

import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Only the small legacy schema that we actually send is permitted. */
final class IslandPayloadValidator {
    private static final Set<String> ALLOWED = Set.of("protocol", "business", "ticker", "tickerPic",
        "aodTitle", "updatable", "enableFloat", "islandFirstFloat", "filterWhenNoPermission",
        "param_island", "baseInfo", "iconTextInfo", "bgInfo", "actions");
    static boolean isSafe(JSONObject payload, Set<String> pictures, Map<String, String> actions) {
        try {
            JSONObject p = payload.getJSONObject("param_v2");
            java.util.Iterator<String> keys = p.keys();
            while (keys.hasNext()) if (!ALLOWED.contains(keys.next())) return false;
            if (p.getInt("protocol") != 1 || !text(p, "ticker") || !text(p, "aodTitle")) return false;
            if (!pictures.contains(p.getString("tickerPic"))) return false;
            boolean modern = p.has("iconTextInfo");
            if (modern) {
                if (p.has("baseInfo")) return false;
                JSONObject background = p.getJSONObject("bgInfo");
                if (background.getInt("type") != 1 || !background.getString("colorBg").matches("#[0-9A-Fa-f]{6}")) return false;
                JSONObject info = p.getJSONObject("iconTextInfo");
                if (!text(info, "title") || !text(info, "content")) return false;
                JSONObject icon = info.getJSONObject("animIconInfo");
                if (icon.getInt("type") != 0 || !pictures.contains(icon.getString("src"))) return false;
            } else {
                JSONObject base = p.getJSONObject("baseInfo");
                if (base.getInt("type") != 1 || !text(base, "title") || !text(base, "content")) return false;
            }
            JSONObject island = p.getJSONObject("param_island");
            int seconds = island.getInt("islandTimeout");
            if (seconds < 1 || seconds > 30) return false;
            JSONObject big = island.getJSONObject("bigIslandArea");
            if (!picture(big.getJSONObject("imageTextInfoLeft").getJSONObject("picInfo"), pictures)
                || !text(big.getJSONObject("textInfo"), "title")
                || !picture(island.getJSONObject("smallIslandArea").getJSONObject("picInfo"), pictures)) return false;
            JSONArray refs = p.getJSONArray("actions");
            if (refs.length() < 1 || refs.length() > 2 || (modern && refs.length() != 1)) return false;
            for (int i = 0; i < refs.length(); i++) {
                JSONObject action = refs.getJSONObject(i);
                if (modern && (action.getInt("type") != 2 || !text(action, "actionTitle")
                    || !color(action, "actionTitleColor") || !color(action, "actionBgColor"))) return false;
                String title = actions.get(action.getString("action"));
                if (title == null || title.isBlank()) return false;
            }
            return true;
        } catch (org.json.JSONException e) { return false; }
    }
    private static boolean text(JSONObject json, String key) throws org.json.JSONException {
        Object value = json.get(key);
        return value instanceof String && !((String) value).isBlank() && ((String) value).length() <= 1024;
    }
    private static boolean color(JSONObject json, String key) throws org.json.JSONException {
        return json.getString(key).matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?");
    }
    private static boolean picture(JSONObject json, Set<String> keys) throws org.json.JSONException {
        return json.getInt("type") == 1 && keys.contains(json.getString("pic"));
    }
}
