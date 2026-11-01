package dev.linkisland.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BaiduLinksTest {
    private static final String PKG = "com.baidu.netdisk";
    private static final String URL = "https://pan.baidu.com/s/1LinkIsland_Fixture-23";
    @Test public void matchesOfficialShareSchemeAndRemovesOnlyTheLeadingOne() {
        assertEquals("bdnetdisk://n/action.SHARE_LINK?m_n_v=8.3.0&surl=LinkIsland_Fixture-23&surlType=1", BaiduLinks.nativeTarget(URL, PKG, null));
        assertEquals("bdnetdisk://n/action.SHARE_LINK?m_n_v=8.3.0&surl=1234&surlType=1", BaiduLinks.nativeTarget("https://yun.baidu.com/s/11234", PKG, null));
    }
    @Test public void preservesUrlCodeAndUsesRecognizedTextCodeWhenTheUrlHasNone() {
        assertEquals("bdnetdisk://n/action.SHARE_LINK?m_n_v=8.3.0&surl=LinkIsland_Fixture-23&surlType=1&pwd=a1B2", BaiduLinks.nativeTarget(URL + "?pwd=a%31B2&tracking=fixture", PKG, "z9Y8"));
        assertTrue(BaiduLinks.nativeTarget(URL, PKG, "a1B2").endsWith("&pwd=a1B2"));
        assertFalse(BaiduLinks.nativeTarget(URL, PKG, "invalid code").contains("pwd="));
    }
    @Test public void rejectsUnrelatedHostsPackagesAndUnsupportedPages() {
        assertNull(BaiduLinks.nativeTarget(URL, "com.example.other", null));
        for (String url : new String[]{"https://pan.baidu.com.evil.example/s/1fixture", "https://evil.example/?url=" + URL,
                "https://pan.baidu.com@evil.example/s/1fixture", "https://pan.baidu.com/disk/main", "https://pan.baidu.com/s/no-leading-one",
                "https://pan.baidu.com/s/1fixture%2Fother", "https://pan.baidu.com:9000/s/1fixture", "bdnetdisk://n/action.SHARE_LINK?surl=fixture"})
            assertNull(BaiduLinks.nativeTarget(url, PKG, null));
    }
}
