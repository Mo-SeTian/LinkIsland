package dev.linkisland.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class LinkRulesTest {
    @Test public void extractsSharedTextAndTrimsPunctuation() {
        assertEquals("https://github.com/owner/repo", LinkRules.extract("看看这个 https://github.com/owner/repo。"));
        assertEquals("https://example.org/a_(b)", LinkRules.extract("(https://example.org/a_(b))"));
        assertNull(LinkRules.extract("验证码 123456"));
        assertNull(LinkRules.extract("https://user:pass@example.org"));
    }
    @Test public void githubRoutesByExactHost() throws Exception {
        List<LinkRules.Rule> rules = LinkRules.parse(LinkRules.DEFAULTS);
        assertEquals("com.github.android", LinkRules.resolve("https://github.com/owner/repo", rules).packageName);
        assertEquals("", LinkRules.resolve("https://github.com.evil.example/a", rules).packageName);
        assertEquals("", LinkRules.resolve("https://example.org/?next=github.com", rules).packageName);
    }
    @Test public void fallsBackToBrowserAndPreservesUrl() throws Exception {
        String url = "https://example.org/a?q=a%20b#section";
        LinkRules.Route result = LinkRules.resolve(url, LinkRules.parse(LinkRules.DEFAULTS));
        assertEquals(url, result.target);
        assertEquals("", result.packageName);
    }
    @Test public void rewritesRegexCaptures() throws Exception {
        String json = """
            [{"name":"视频","type":"regex","pattern":"^https://example.org/video/([0-9]+)$",
              "package":"com.example.player","replacement":"player://video/$1"}]
            """;
        assertEquals("player://video/42", LinkRules.resolve("https://example.org/video/42", LinkRules.parse(json)).target);
    }
    @Test public void honorsPriorityAndDisabledRules() throws Exception {
        String json = """
            [{"type":"contains","pattern":"example","package":"com.disabled.app","enabled":false},
             {"type":"contains","pattern":"EXAMPLE","package":"com.first.app"},
             {"type":"domain","pattern":"example.org","package":"com.second.app"}]
            """;
        assertEquals("com.first.app", LinkRules.resolve("https://example.org", LinkRules.parse(json)).packageName);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsMalformedRegex() throws Exception {
        LinkRules.parse("[{\"type\":\"regex\",\"pattern\":\"[\"}]");
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsUnsafeScheme() throws Exception {
        LinkRules.resolve("https://example.org", LinkRules.parse(
            "[{\"type\":\"contains\",\"pattern\":\"example\",\"package\":\"com.example.app\",\"replacement\":\"javascript:alert(1)\"}]"));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsCustomSchemeWithoutApp() throws Exception {
        LinkRules.resolve("https://example.org", LinkRules.parse(
            "[{\"type\":\"contains\",\"pattern\":\"example\",\"replacement\":\"player://video/1\"}]"));
    }
    @Test public void presetsCoverCommonLinksAndKeepOriginalUrl() throws Exception {
        String[][] examples = {
            {"https://www.bilibili.com/video/BV1test", "tv.danmaku.bili"},
            {"https://b23.tv/test", "tv.danmaku.bili"},
            {"https://zhuanlan.zhihu.com/p/123", "com.zhihu.android"},
            {"https://www.xiaohongshu.com/explore/123", "com.xingin.xhs"},
            {"https://xhslink.cn/a/test", "com.xingin.xhs"},
            {"https://v.douyin.com/test/", "com.ss.android.ugc.aweme"},
            {"https://www.iesdouyin.com/share/video/123", "com.ss.android.ugc.aweme"},
            {"https://m.weibo.cn/status/123", "com.sina.weibo"},
            {"https://e.tb.cn/h.test", "com.taobao.taobao"},
            {"https://detail.tmall.com/item.htm?id=123", "com.taobao.taobao"},
            {"https://item.m.jd.com/product/123.html", "com.jingdong.app.mall"},
            {"https://www.coolapk.com/feed/123", "com.coolapk.market"},
            {"https://movie.douban.com/subject/123", "com.douban.frodo"},
            {"https://music.163.com/song?id=123", "com.netease.cloudmusic"},
            {"https://youtu.be/test", "com.google.android.youtube"},
            {"HTTPS://WWW.BILIBILI.COM/video/test", "tv.danmaku.bili"}
        };
        List<LinkRules.Rule> rules = LinkRules.parse(LinkRules.DEFAULTS);
        assertEquals(13, rules.size());
        for (String[] pair : examples) {
            LinkRules.Route route = LinkRules.resolve(pair[0], rules);
            assertEquals(pair[0], pair[1], route.packageName);
            assertEquals(pair[0], route.target);
        }
    }
    @Test public void presetsDoNotMatchHostImpersonationOrQueryText() throws Exception {
        List<LinkRules.Rule> rules = LinkRules.parse(LinkRules.DEFAULTS);
        for (String url : new String[]{"https://bilibili.com.evil.example/a", "https://evilbilibili.com/a",
            "https://example.org/?next=https://www.bilibili.com/video/1", "https://xhslink.cn.evil.example/a"})
            assertEquals(url, "", LinkRules.resolve(url, rules).packageName);
    }
    @Test public void defaultUpgradePreservesPriorityEditsAndDisabledState() throws Exception {
        String old = "[{\"name\":\"我的规则\",\"type\":\"contains\",\"pattern\":\"bilibili\",\"package\":\"com.custom.app\"},"
            + "{\"name\":\"停用的 GitHub\",\"type\":\"domain\",\"pattern\":\"github.com\",\"enabled\":false}]";
        String upgraded = LinkRules.addMissingDefaults(old);
        List<LinkRules.Rule> rules = LinkRules.parse(upgraded);
        assertEquals("我的规则", rules.get(0).name);
        assertFalse(rules.get(1).enabled);
        assertEquals("com.custom.app", LinkRules.resolve("https://www.bilibili.com/video/1", rules).packageName);
        assertEquals("", LinkRules.resolve("https://github.com/test", rules).packageName);
        assertEquals(upgraded, LinkRules.addMissingDefaults(upgraded));
    }
    @Test public void upgradeDoesNotResurrectDeletedOriginalRuleOrExceedLimit() throws Exception {
        assertEquals(12, LinkRules.parse(LinkRules.addMissingDefaults("[]")).size());
        org.json.JSONArray old = new org.json.JSONArray();
        for (int i = 0; i < 100; i++) old.put(new org.json.JSONObject()
            .put("type", "domain").put("pattern", "example" + i + ".org"));
        assertEquals(100, LinkRules.parse(LinkRules.addMissingDefaults(old.toString())).size());
    }
    @Test public void pinduoduoMatchesGoodsAndKeepsShareParameters() throws Exception {
        List<LinkRules.Rule> rules = LinkRules.parse(LinkRules.DEFAULTS);
        for (String url : new String[]{"https://mobile.yangkeduo.com/goods2.html?ps=example",
            "https://mobile.pinduoduo.com/goods.html?goods_id=123"}) {
            LinkRules.Route route = LinkRules.resolve(url, rules);
            assertEquals("com.xunmeng.pinduoduo", route.packageName);
            assertEquals("拼多多", route.ruleName);
            assertEquals(url, route.target);
        }
        for (String url : new String[]{"https://yangkeduo.com.evil.example/a", "https://example.org/?next=yangkeduo.com"})
            assertEquals("", LinkRules.resolve(url, rules).packageName);
    }
    @Test public void pinduoduoUpgradeOnlyAddsNewPresetAndKeepsDisabledEdits() throws Exception {
        String old = "[{\"name\":\"我的规则\",\"type\":\"contains\",\"pattern\":\"goods2\",\"package\":\"com.custom.app\"}]";
        String upgraded = LinkRules.addPinduoduoDefault(old);
        List<LinkRules.Rule> rules = LinkRules.parse(upgraded);
        assertEquals(2, rules.size());
        assertEquals("我的规则", rules.get(0).name);
        assertEquals("拼多多", rules.get(1).name);
        assertEquals("com.custom.app", LinkRules.resolve("https://mobile.yangkeduo.com/goods2.html", rules).packageName);
        assertEquals(upgraded, LinkRules.addPinduoduoDefault(upgraded));
        org.json.JSONArray disabled = new org.json.JSONArray(upgraded);
        disabled.getJSONObject(1).put("enabled", false).put("name", "我的拼多多");
        String saved = disabled.toString();
        assertEquals(saved, LinkRules.addPinduoduoDefault(saved));
    }

}
