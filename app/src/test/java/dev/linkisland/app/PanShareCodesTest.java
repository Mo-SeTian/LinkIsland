package dev.linkisland.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PanShareCodesTest {
    private static final String URL = "https://pan.baidu.com/s/fixture";
    @Test public void recognizesLabelledShareTextAndPreservesCase() {
        for (String label : new String[]{"提取码：", "访问码:", "提取密码=", "訪問碼是", "密码：", "提取码【", "提取码（"})
            assertEquals("a1B2", PanShareCodes.extract("分享链接 " + URL + "\n" + label + " a1B2）", URL));
        assertEquals("ab12CD34", PanShareCodes.extract(URL + " 提取码 ab12CD34", URL));
    }
    @Test public void onlyRecognizesKnownCloudHostsWithDomainBoundaries() {
        for (String host : new String[]{"alipan.com", "aliyundrive.com", "pan.baidu.com", "yun.baidu.com", "abc.lanzou.com",
                "www.lanzoui.com", "lanzoux.com", "lanzous.com", "lanzoub.com", "ilanzou.com", "123pan.com", "123pan.cn",
                "123684.com", "123865.com", "pan.quark.cn", "cloud.189.cn", "pan.xunlei.com", "115.com", "115cdn.com"}) {
            String url = "https://" + host + "/s/fixture";
            assertEquals("a1B2", PanShareCodes.extract(url + " 提取码：a1B2", url));
        }
        for (String url : new String[]{"https://github.com/a/b", "https://pan.baidu.com.evil.example/s/fixture",
                "https://evil.example/?next=pan.baidu.com", "https://pan.baidu.com@evil.example/s/fixture", "not a url"})
            assertNull(PanShareCodes.extract(url + " 密码 a1B2", url));
    }
    @Test public void readsExplicitCodeParametersWithoutMistakingShareIdentifiersForPasswords() {
        assertEquals("a1B2", PanShareCodes.extract(null, URL + "?pwd=a%31B2"));
        assertEquals("a1B2", PanShareCodes.extract(null, URL + "#password=a1B2"));
        assertEquals("a1B2", PanShareCodes.extract(null, URL + "?accessCode=a1B2"));
        assertNull(PanShareCodes.extract(null, URL + "?code=a1B2"));
        assertNull(PanShareCodes.extract(null, URL + "#a1B2"));
        assertNull(PanShareCodes.extract(null, URL + "?pwd=too-long-code"));
        assertNull(PanShareCodes.extract(null, URL + "?pwd=%invalid"));
    }
    @Test public void ignoresArchiveAndLoginPasswordsAndUnlabelledNumbers() {
        for (String label : new String[]{"解压密码", "解压 密码", "解 壓 密碼", "压缩密码", "登录密码"})
            assertNull(PanShareCodes.extract(URL + " " + label + "：a1B2", URL));
        assertEquals("a1B2", PanShareCodes.extract(URL + " 解压密码：z9Y8，提取码：a1B2", URL));
        assertNull(PanShareCodes.extract(URL + " 分享时间 2026 文件大小 1234", URL));
        assertNull(PanShareCodes.extract(URL + " 提取码：123456789", URL));
        assertNull(PanShareCodes.extract(URL + " 提取码：12", URL));
    }
    @Test public void refusesAmbiguousCodesAndDoesNotAssignAnotherLinksCode() {
        assertNull(PanShareCodes.extract(URL + " 提取码：a1B2\n访问码：z9Y8", URL));
        String keyed = URL + "?pwd=a1B2";
        assertNull(PanShareCodes.extract(keyed + " 提取码 z9Y8", keyed));
        assertEquals("a1B2", PanShareCodes.extract(keyed + " 提取码 a1B2", keyed));
        assertNull(PanShareCodes.extract(URL + "\nhttps://alipan.com/s/other 提取码 a1B2", URL));
        assertEquals("a1B2", PanShareCodes.extract(keyed + "\nhttps://alipan.com/s/other 提取码 z9Y8", keyed));
        assertNull(PanShareCodes.extract(null, URL + "?pwd=a1B2&pwd=z9Y8"));
    }
    @Test public void neverTreatsWordsInsideAUrlAsLabelledShareText() {
        String url = URL + "?description=提取码a1B2";
        assertNull(PanShareCodes.extract(url, url));
        assertNull(PanShareCodes.extract("x".repeat(65537) + URL + " 提取码a1B2", URL));
        assertNull(PanShareCodes.extract("提取码a1B2", URL));
    }
}
