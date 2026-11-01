package dev.linkisland.app;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RuleSupportTest {
    private static JSONObject rule(String name, String type, String pattern) throws Exception {
        return new JSONObject().put("name", name).put("type", type).put("pattern", pattern).put("package", "");
    }
    @Test public void cacheReusesImmutableSnapshotAndInvalidatesEveryRuleMutation() throws Exception {
        LinkRules.Cache cache = new LinkRules.Cache();
        JSONArray json = new JSONArray().put(rule("网站", "domain", "example.org"));
        List<LinkRules.Rule> first = cache.get(json.toString());
        assertSame(first, cache.get(new String(json.toString())));
        assertThrows(UnsupportedOperationException.class, () -> first.clear());
        json.getJSONObject(0).put("enabled", false);
        List<LinkRules.Rule> disabled = cache.get(json.toString());
        assertNotSame(first, disabled);
        assertNull(LinkRules.resolve("https://example.org", disabled));
        assertNotNull(LinkRules.resolve("https://example.org", first));
        assertTrue(cache.get("[]").isEmpty());
        assertEquals(23, cache.get(LinkRules.DEFAULTS).size());
        assertEquals(0, LinkRules.resolve("https://github.com/a/b", cache.get(LinkRules.DEFAULTS)).ruleIndex);
        String reordered = LinkRules.reorder(LinkRules.DEFAULTS, java.util.stream.IntStream.range(0, 23).map(i -> 22 - i).boxed().toList());
        assertEquals("网页浏览器", LinkRules.resolve("https://github.com/a/b", cache.get(reordered)).ruleName);
    }
    @Test public void malformedUpdatesNeverFallBackToStaleRules() throws Exception {
        LinkRules.Cache cache = new LinkRules.Cache();
        List<LinkRules.Rule> valid = cache.get(LinkRules.DEFAULTS);
        assertThrows(Exception.class, () -> cache.get("invalid"));
        assertThrows(Exception.class, () -> cache.get("invalid"));
        assertSame(valid, cache.get(LinkRules.DEFAULTS));
    }
    @Test public void conflictsRespectPriorityEnabledStateAndExactMatchingConditions() throws Exception {
        JSONObject browser = rule("浏览器", "regex", LinkRules.BROWSER_PATTERN);
        JSONObject website = rule("网站", "domain", "example.org");
        JSONArray json = new JSONArray().put(browser).put(website);
        List<String> warnings = LinkRules.conflicts(LinkRules.parse(json.toString()));
        assertEquals(1, warnings.size()); assertTrue(warnings.get(0).contains("第 2 条"));
        assertTrue(warnings.get(0).contains("所有网页"));
        browser.put("enabled", false);
        assertTrue(LinkRules.conflicts(LinkRules.parse(json.toString())).isEmpty());
        json.put(rule("重复", "domain", "EXAMPLE.ORG"));
        warnings = LinkRules.conflicts(LinkRules.parse(json.toString()));
        assertEquals(1, warnings.size()); assertTrue(warnings.get(0).contains("条件相同"));
        website.put("enabled", false);
        assertTrue(LinkRules.conflicts(LinkRules.parse(json.toString())).isEmpty());
        assertTrue(LinkRules.conflicts(LinkRules.parse(LinkRules.DEFAULTS)).isEmpty());
    }
    @Test public void previewListsOverlapsWithoutRewritingOrChangingFirstMatch() throws Exception {
        JSONArray json = new JSONArray().put(rule("具体", "domain", "example.org"))
            .put(rule("正则", "regex", "example\\.org").put("replacement", "$9"))
            .put(rule("浏览器", "regex", LinkRules.BROWSER_PATTERN))
            .put(rule("停用", "contains", "example").put("enabled", false));
        List<LinkRules.Rule> rules = LinkRules.parse(json.toString());
        assertEquals(List.of(0, 1, 2), LinkRules.matchingRules("https://example.org/path", rules));
        assertEquals("具体", LinkRules.resolve("https://example.org/path", rules).ruleName);
    }
    @Test public void logIdentitySurvivesSortingRenamingAndDisablingButNeverGuessesDuplicates() throws Exception {
        JSONObject website = rule("网站", "domain", "example.org");
        List<LinkRules.Rule> original = LinkRules.parse(new JSONArray().put(website).toString());
        String key = original.get(0).key;
        website.put("name", "新名字").put("enabled", false);
        JSONArray moved = new JSONArray().put(rule("浏览器", "regex", LinkRules.BROWSER_PATTERN)).put(website);
        assertEquals(1, LinkRules.findRule(key, LinkRules.parse(moved.toString())));
        moved.put(new JSONObject(website.toString()));
        assertEquals(-1, LinkRules.findRule(key, LinkRules.parse(moved.toString())));
        website.put("pattern", "other.example");
        assertEquals(-1, LinkRules.findRule(key, LinkRules.parse(new JSONArray().put(website).toString())));
        assertEquals(-1, LinkRules.findRule("", original));
        assertTrue(key.matches("[0-9a-f]{64}")); assertFalse(key.contains("example"));
    }
}
