package dev.linkisland.app;

import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

public final class JumpLogTest {
    @Test public void storesOnlyHostAndOutcomeWithoutPrivateUrlComponents() throws Exception {
        String log = JumpLog.append("[]", "识别", "命中拼多多", "https://user:password@example.org/private/note?token=secret#fragment", 1000);
        assertEquals("example.org", new JSONArray(log).getJSONObject(0).getString("host"));
        for (String value : new String[]{"user", "password", "/private", "secret", "fragment"}) assertFalse(log.contains(value));
    }
    @Test public void boundsHistoryAndKeepsNewestEntriesInOrder() throws Exception {
        String log = "[]";
        for (int i = 0; i < 120; i++) log = JumpLog.append(log, "跳转", "结果 " + i, null, i * 2000L);
        JSONArray entries = new JSONArray(log);
        assertEquals(100, entries.length());
        assertEquals("结果 20", entries.getJSONObject(0).getString("detail"));
        assertEquals("结果 119", entries.getJSONObject(99).getString("detail"));
    }
    @Test public void suppressesRapidRetryDuplicatesButAllowsLaterCopies() throws Exception {
        String log = JumpLog.append("[]", "识别", "系统返回空剪贴板", null, 1000);
        assertEquals(log, JumpLog.append(log, "识别", "系统返回空剪贴板", null, 1500));
        assertEquals(2, new JSONArray(JumpLog.append(log, "识别", "系统返回空剪贴板", null, 2500)).length());
    }
}
