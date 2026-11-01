package dev.linkisland.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class JumpLogTest {
    @Test public void ruleDigestAttachesToItsOwnCopyAndNeverResurrectsClearedLogs() throws Exception {
        String old = JumpLog.begin("[]", "copy", "识别", "已识别", "https://example.org/private?token=hidden", 1, "pending");
        String key = LinkRules.parse(LinkRules.DEFAULTS).get(0).key;
        String updated = JumpLog.step(old, "copy", "匹配", "已命中", 2, "", key);
        assertEquals(key, new JSONArray(updated).getJSONObject(0).getString("rule_key"));
        assertFalse(updated.contains("/private")); assertFalse(updated.contains("hidden"));
        assertEquals(old, JumpLog.step(old, "another-copy", "匹配", "已命中", 2, "", key));
        assertEquals("[]", JumpLog.step("[]", "copy", "匹配", "已命中", 2, "", key));
    }
    @Test public void oneCopyRetainsItsFullFlowWithoutPrivateUrlComponents() throws Exception {
        String log = JumpLog.begin("[]", "copy-1", "识别", "已识别网页链接", "https://user:password@example.org/private/note?token=secret#fragment", 1000, "pending");
        log = JumpLog.step(log, "copy-1", "提示", "已发送提示", 1100, "");
        log = JumpLog.step(log, "copy-1", "点击", "已点击打开", 1200, "opening");
        log = JumpLog.step(log, "copy-1", "跳转", "已请求目标应用打开", 1300, "requested");
        JSONArray groups = new JSONArray(log); JSONObject group = groups.getJSONObject(0);
        assertEquals(1, groups.length()); assertEquals(4, group.getJSONArray("events").length());
        assertEquals("example.org", group.getString("host")); assertEquals("requested", group.getString("status"));
        for (String value : new String[]{"user", "password", "/private", "secret", "fragment"}) assertFalse(log.contains(value));
    }
    @Test public void separateCopiesOnTheSameHostNeverMergeAndLateActionsUpdateTheirOwnGroup() throws Exception {
        String log = JumpLog.begin("[]", "first", "识别", "第一条", "https://example.org/first", 1000, "pending");
        log = JumpLog.begin(log, "second", "识别", "第二条", "https://example.org/second", 1100, "pending");
        log = JumpLog.step(log, "first", "跳转", "第一条失败", 1500, "failed");
        JSONArray groups = new JSONArray(log);
        assertEquals(2, groups.length()); assertEquals("failed", groups.getJSONObject(0).getString("status"));
        assertEquals("pending", groups.getJSONObject(1).getString("status"));
        assertEquals("第二条", groups.getJSONObject(1).getString("detail"));
    }
    @Test public void recoveredFallbackStopsAppearingAsFinalFailureAndClearDoesNotResurrectEntries() throws Exception {
        String log = JumpLog.begin("[]", "id", "识别", "命中规则", null, 1000, "pending");
        log = JumpLog.step(log, "id", "跳转", "目标不支持", 1100, "failed");
        log = JumpLog.step(log, "id", "跳转", "已请求浏览器打开", 1200, "requested");
        assertEquals("requested", new JSONArray(log).getJSONObject(0).getString("status"));
        assertEquals("[]", JumpLog.step("[]", "id", "跳转", "迟到的操作", 2000, "failed"));
    }
    @Test public void boundsCopiesAndEvents() throws Exception {
        String log = "[]";
        for (int i = 0; i < 120; i++) log = JumpLog.begin(log, "id" + i, "识别", "结果 " + i, null, i * 2000L, "pending");
        JSONArray groups = new JSONArray(log); assertEquals(100, groups.length());
        assertEquals("id20", groups.getJSONObject(0).getString("id"));
        for (int i = 0; i < 30; i++) log = JumpLog.step(log, "id119", "跳转", "步骤 " + i, 300000 + i, "");
        groups = new JSONArray(log); assertEquals(20, groups.getJSONObject(99).getJSONArray("events").length());
    }
    @Test public void migratesLegacyRecordsWithoutGuessingWhichLinksBelongTogether() throws Exception {
        JSONArray old = new JSONArray().put(new JSONObject().put("time", 1000).put("stage", "识别").put("detail", "已识别").put("host", "example.org"))
            .put(new JSONObject().put("time", 1100).put("stage", "跳转").put("detail", "启动失败").put("host", "example.org"));
        JSONArray groups = new JSONArray(JumpLog.migrate(old.toString()));
        assertEquals(2, groups.length()); assertEquals("legacy", groups.getJSONObject(0).getString("status"));
        assertEquals("failed", groups.getJSONObject(1).getString("status"));
        assertEquals("example.org", groups.getJSONObject(0).getString("host"));
    }
}
