package dev.linkisland.app;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class IslandPayloadTest {
    @Test public void summarySeparatesIconAndOpenLabel() throws Exception {
        JSONObject island = IslandPayload.create("GitHub", "github.com")
            .getJSONObject("param_v2").getJSONObject("param_island");
        JSONObject summary = island.getJSONObject("bigIslandArea");
        assertEquals(IslandPayload.ICON, summary.getJSONObject("imageTextInfoLeft")
            .getJSONObject("picInfo").getString("pic"));
        assertEquals("打开", summary.getJSONObject("textInfo").getString("title"));
        assertEquals(IslandPayload.ICON, island.getJSONObject("smallIslandArea").getJSONObject("picInfo").getString("pic"));
    }
    @Test public void expandedCardHasLeftIconTwoLinesAndOneExplicitOpenAction() throws Exception {
        JSONObject params = IslandPayload.create("GitHub", "github.com").getJSONObject("param_v2");
        assertFalse(params.has("textButton"));
        assertFalse(params.has("baseInfo"));
        JSONObject info = params.getJSONObject("iconTextInfo");
        assertEquals("GitHub", info.getString("title"));
        assertEquals("github.com", info.getString("content"));
        assertEquals(IslandPayload.ICON, info.getJSONObject("animIconInfo").getString("src"));
        assertEquals(1, params.getJSONArray("actions").length());
        JSONObject action = params.getJSONArray("actions").getJSONObject(0);
        assertEquals(2, action.getInt("type"));
        assertEquals("打开", action.getString("actionTitle"));
        assertEquals("miui.focus.action_open", action.getString("action"));
    }
    @Test public void olderProtocolKeepsLegacyActionReferences() throws Exception {
        JSONObject params = IslandPayload.create("GitHub", "github.com", false).getJSONObject("param_v2");
        assertFalse(params.has("textButton"));
        assertFalse(params.has("iconTextInfo"));
        assertEquals("GitHub", params.getJSONObject("baseInfo").getString("title"));
        assertEquals(2, params.getJSONArray("actions").length());
        assertEquals("miui.focus.action_dismiss", params.getJSONArray("actions").getJSONObject(1).getString("action"));
    }
}
