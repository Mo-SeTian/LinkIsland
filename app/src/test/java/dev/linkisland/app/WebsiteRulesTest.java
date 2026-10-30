package dev.linkisland.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class WebsiteRulesTest {
    @Test public void pastedLinksBecomeDistinctHosts() {
        assertEquals(List.of("github.com", "pan.quark.cn", "example.org"), WebsiteRules.hosts(
            " https://GitHub.com/owner/repo?tab=files#readme，pan.quark.cn/s/demo\nexample.org;github.com "));
    }
    @Test public void websitesRouteWithoutExposingRegexAndRespectBoundaries() throws Exception {
        JSONObject rule = new JSONObject().put("package", "com.example.app");
        WebsiteRules.write(rule, "https://example.org/s/share?pwd=test,other.example", true);
        List<LinkRules.Rule> rules = LinkRules.parse(new JSONArray().put(rule).toString());
        assertEquals("com.example.app", LinkRules.resolve("https://m.example.org/s/share?pwd=test", rules).packageName);
        assertEquals("com.example.app", LinkRules.resolve("http://other.example/a", rules).packageName);
        for (String url : new String[]{"https://example.org.evil.test/a", "https://evil-example.org/a", "https://evil.test/?next=example.org"})
            assertNull(LinkRules.resolve(url, rules));
        WebsiteRules.Websites decoded = WebsiteRules.read(rule);
        assertEquals("example.org\nother.example", decoded.text); assertTrue(decoded.subdomains);
    }
    @Test public void exactWebsiteDoesNotIncludeSubdomains() throws Exception {
        JSONObject rule = new JSONObject();
        WebsiteRules.write(rule, "https://github.com/repo", false);
        assertEquals("domain", rule.getString("type")); assertEquals("github.com", rule.getString("pattern"));
        List<LinkRules.Rule> rules = LinkRules.parse(new JSONArray().put(rule).toString());
        assertNotNull(LinkRules.resolve("https://github.com/repo", rules));
        assertNull(LinkRules.resolve("https://gist.github.com/repo", rules));
        WebsiteRules.write(rule, "github.com,example.org", false);
        assertNotNull(LinkRules.resolve("https://example.org/a", LinkRules.parse(new JSONArray().put(rule).toString())));
        assertNull(LinkRules.resolve("https://www.example.org/a", LinkRules.parse(new JSONArray().put(rule).toString())));
    }
    @Test public void existingPresetsRoundTripWithoutChangingMatching() throws Exception {
        JSONArray rules = new JSONArray(LinkRules.DEFAULTS);
        for (int i = 0; i < rules.length() - 1; i++) {
            JSONObject old = rules.getJSONObject(i), edited = new JSONObject(old.toString());
            WebsiteRules.Websites decoded = WebsiteRules.read(old);
            assertNotNull(old.getString("name"), decoded);
            WebsiteRules.write(edited, decoded.text, decoded.subdomains);
            assertEquals(old.toString(), edited.toString());
        }
        assertNull(WebsiteRules.read(rules.getJSONObject(rules.length() - 1)));
    }
    @Test public void arbitraryRegexIsNeverSilentlySimplified() throws Exception {
        for (String pattern : new String[]{"example.*", "^https://example.org/video/([0-9]+)",
            "(?i)^https?://(?:example\\.org|.*)(?::[0-9]+)?(?:[/?#]|$)"})
            assertNull(WebsiteRules.read(new JSONObject().put("type", "regex").put("pattern", pattern)));
        assertNull(WebsiteRules.read(new JSONObject().put("type", "contains").put("pattern", "example")));
    }
    @Test public void invalidWebsiteInputHasFriendlyErrors() {
        for (String input : new String[]{"", "ftp://example.org/a", "https://user:password@example.org", "example.*", "https://"}) {
            try { WebsiteRules.hosts(input); fail("Accepted invalid input"); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("网址或域名")); }
        }
    }
}
