package dev.linkisland.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Official OS3 template 15: one icon/text block and one primary text action. */
final class IslandPayload {
    static final String ICON = "miui.focus.pic_target";
    static JSONObject create(String label, String host) throws JSONException { return create(label, host, true); }
    static JSONObject create(String label, String host, boolean modern) throws JSONException {
        return create(label, host, modern, 30);
    }
    static JSONObject create(String label, String host, boolean modern, int seconds) throws JSONException {
        JSONObject picture = new JSONObject().put("type", 1).put("pic", ICON);
        JSONObject island = new JSONObject().put("islandProperty", 2).put("islandTimeout", PromptDuration.valid(seconds))
            .put("bigIslandArea", new JSONObject()
                .put("imageTextInfoLeft", new JSONObject().put("type", 1).put("picInfo", picture))
                .put("textInfo", new JSONObject().put("title", "打开")))
            .put("smallIslandArea", new JSONObject().put("picInfo", picture));
        JSONObject params = new JSONObject().put("protocol", 1).put("business", "link_open_confirmation")
            .put("ticker", "打开 " + label).put("tickerPic", ICON).put("aodTitle", "有待确认的链接")
            .put("updatable", false).put("enableFloat", true).put("islandFirstFloat", true)
            .put("filterWhenNoPermission", false).put("param_island", island);
        if (modern) {
            params.put("bgInfo", new JSONObject().put("type", 1).put("colorBg", "#1C1C20"))
                .put("iconTextInfo", new JSONObject().put("title", label).put("content", host)
                .put("colorTitle", "#FFFFFF").put("colorTitleDark", "#FFFFFF")
                .put("colorContent", "#ADADB5").put("colorContentDark", "#ADADB5")
                .put("animIconInfo", new JSONObject().put("type", 0).put("src", ICON)))
                .put("actions", new JSONArray().put(new JSONObject().put("type", 2)
                    .put("action", "miui.focus.action_open").put("actionTitle", "打开")
                    .put("actionTitleColor", "#FFFFFF").put("actionTitleColorDark", "#FFFFFF")
                    .put("actionBgColor", "#287EFF").put("clickWithCollapse", true)));
        } else {
            params.put("baseInfo", new JSONObject().put("type", 1).put("title", label).put("content", host))
                .put("actions", new JSONArray().put(new JSONObject().put("action", "miui.focus.action_open"))
                    .put(new JSONObject().put("action", "miui.focus.action_dismiss")));
        }
        return new JSONObject().put("param_v2", params);
    }
}
