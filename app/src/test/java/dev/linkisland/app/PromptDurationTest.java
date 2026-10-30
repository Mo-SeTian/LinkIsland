package dev.linkisland.app;

import java.util.Map;
import java.util.Set;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PromptDurationTest {
    @Test public void configuredLifetimeIsAppliedToBothIslandTemplatesAndPassesSafetyChecks() throws Exception {
        for (int seconds : PromptDuration.OPTIONS) for (boolean modern : new boolean[]{true, false}) {
            JSONObject payload = IslandPayload.create("目标应用", "example.org", modern, seconds);
            assertEquals(seconds, payload.getJSONObject("param_v2").getJSONObject("param_island").getInt("islandTimeout"));
            assertTrue(IslandPayloadValidator.isSafe(payload, Set.of(IslandPayload.ICON), Map.of(
                "miui.focus.action_open", "打开", "miui.focus.action_dismiss", "忽略")));
        }
    }
    @Test public void unsupportedOrUnlimitedDurationsKeepSafeDefault() {
        for (int seconds : new int[]{-1, 0, 1, 60, Integer.MAX_VALUE}) assertEquals(30, PromptDuration.valid(seconds));
    }
}
