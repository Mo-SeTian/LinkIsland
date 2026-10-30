package dev.linkisland.app;

import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class IslandPayloadValidatorTest {
    private static final Set<String> PICTURES = Set.of(IslandPayload.ICON);
    private static final Map<String, String> ACTIONS = Map.of(
        "miui.focus.action_open", "打开", "miui.focus.action_dismiss", "忽略");
    private JSONObject valid() throws Exception { return IslandPayload.create("GitHub", "github.com"); }
    @Test public void acceptsKnownTemplateWithResolvedResources() throws Exception {
        assertTrue(IslandPayloadValidator.isSafe(valid(), PICTURES, ACTIONS));
    }
    @Test public void rejectsPreviouslyCrashingAndUnknownModules() throws Exception {
        for (String field : new String[]{"textButton", "unknownFutureModule"}) {
            JSONObject json = valid();json.getJSONObject("param_v2").put(field, new JSONObject());
            assertFalse(IslandPayloadValidator.isSafe(json, PICTURES, ACTIONS));
        }
    }
    @Test public void rejectsNullOrMissingText() throws Exception {
        JSONObject json = valid();json.getJSONObject("param_v2").getJSONObject("iconTextInfo").put("title", JSONObject.NULL);
        assertFalse(IslandPayloadValidator.isSafe(json, PICTURES, ACTIONS));
        json = valid();json.getJSONObject("param_v2").getJSONObject("iconTextInfo").remove("content");
        assertFalse(IslandPayloadValidator.isSafe(json, PICTURES, ACTIONS));
    }
    @Test public void rejectsUnresolvedPicturesAndActions() throws Exception {
        assertFalse(IslandPayloadValidator.isSafe(valid(), Set.of(), ACTIONS));
        assertFalse(IslandPayloadValidator.isSafe(valid(), PICTURES, Map.of()));
        assertFalse(IslandPayloadValidator.isSafe(valid(), PICTURES,
            Map.of("miui.focus.action_open", "", "miui.focus.action_dismiss", "忽略")));
    }
    @Test public void rejectsUnsupportedTypesAndLongerLifetime() throws Exception {
        JSONObject json = valid();json.getJSONObject("param_v2").getJSONObject("iconTextInfo").getJSONObject("animIconInfo").put("type", 3);
        assertFalse(IslandPayloadValidator.isSafe(json, PICTURES, ACTIONS));
        json = valid();json.getJSONObject("param_v2").getJSONObject("param_island").put("islandTimeout", 300);
        assertFalse(IslandPayloadValidator.isSafe(json, PICTURES, ACTIONS));
    }
    @Test public void rejectsOversizedTextRatherThanSendingItToTheOemRenderer() throws Exception {
        assertFalse(IslandPayloadValidator.isSafe(IslandPayload.create("x".repeat(1100), "github.com"), PICTURES, ACTIONS));
    }
    @Test public void rejectsMissingActionTextAndMultiplePrimaryButtons() throws Exception {
        JSONObject json = valid();json.getJSONObject("param_v2").getJSONArray("actions").getJSONObject(0).remove("actionTitle");
        assertFalse(IslandPayloadValidator.isSafe(json, PICTURES, ACTIONS));
        json = valid();json.getJSONObject("param_v2").getJSONArray("actions").getJSONObject(0).put("actionBgColor", "not a color");
        assertFalse(IslandPayloadValidator.isSafe(json, PICTURES, ACTIONS));
        json = valid();json.getJSONObject("param_v2").getJSONArray("actions").put(new JSONObject().put("action", "miui.focus.action_dismiss"));
        assertFalse(IslandPayloadValidator.isSafe(json, PICTURES, ACTIONS));
        assertTrue(IslandPayloadValidator.isSafe(IslandPayload.create("GitHub", "github.com", false), PICTURES, ACTIONS));
    }
}
