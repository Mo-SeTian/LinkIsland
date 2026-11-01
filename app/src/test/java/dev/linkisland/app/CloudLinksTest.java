package dev.linkisland.app;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

public final class CloudLinksTest {
    @Test public void aliUsesOfficialSharePageAndPreservesUrlOrTextCode() {
        for (String host : new String[]{"alipan.com", "www.alipan.com", "aliyundrive.com", "www.aliyundrive.com"}) {
            String url = "http://" + host + "/s/LinkIslandFixture24";
            assertEquals("smartdrive://share/browse?shareId=LinkIslandFixture24&sharePwd=a1B2&source_type=scheme",
                CloudLinks.nativeTarget(url + "?pwd=a%31B2", "com.alicloud.databox", "z9Y8"));
            assertEquals("smartdrive://share/browse?shareId=LinkIslandFixture24&sharePwd=z9Y8&source_type=scheme",
                CloudLinks.nativeTarget(url, "com.alicloud.databox", "z9Y8"));
            assertFalse(CloudLinks.nativeTarget(url, "com.alicloud.databox", null).contains("sharePwd"));
        }
    }
    @Test public void pan123UsesAndroidShareRouteRatherThanDesktopProtocol() {
        for (String host : new String[]{"123pan.com", "123pan.cn", "123684.com", "123865.com"}) {
            String url = "https://www." + host + "/s/LinkIsland-Fixture_24/?pwd=a1B2";
            assertEquals("pan://umeng.com/share/list?action=share_list&shareKey=LinkIsland-Fixture_24&sharePwd=a1B2&_sdk_=umeng&_linkid_=usr1ke01qbgngpn9&_bizType_=ushare",
                CloudLinks.nativeTarget(url, "com.mfcloudcalculate.networkdisk", "z9Y8"));
        }
        assertTrue(CloudLinks.nativeTarget("https://123pan.cn/s/LinkIslandFixture24", "com.mfcloudcalculate.networkdisk", "a1B2").contains("sharePwd=a1B2"));
        assertFalse(CloudLinks.nativeTarget("https://123pan.cn/s/LinkIslandFixture24", "com.mfcloudcalculate.networkdisk", "invalid code").contains("sharePwd"));
    }
    @Test public void quarkKeepsAvailableChosenAppAndOnlyUsesInstalledFamilyAlternative() {
        String url = "https://pan.quark.cn/s/LinkIslandFixture24?pwd=a1B2#keep";
        String browser = "com.quark.browser", disk = "com.quark.clouddrive";
        assertEquals(disk, CloudLinks.targetPackage(url, browser, Set.of(disk)::contains));
        assertEquals(browser, CloudLinks.targetPackage(url, disk, Set.of(browser)::contains));
        assertEquals(browser, CloudLinks.targetPackage(url, browser, Set.of(browser, disk)::contains));
        assertEquals(disk, CloudLinks.targetPackage(url, disk, Set.of(browser, disk)::contains));
        assertEquals(browser, CloudLinks.targetPackage(url, browser, p -> false));
        for (String other : new String[]{"https://example.org/s/LinkIslandFixture24", "https://pan.quark.cn/", "https://pan.quark.cn.evil.example/s/LinkIslandFixture24"})
            assertEquals(browser, CloudLinks.targetPackage(other, browser, Set.of(disk)::contains));
        assertEquals("com.example.custom", CloudLinks.targetPackage(url, "com.example.custom", p -> true));
    }
    @Test public void unrelatedHostsPackagesAndPagesNeverBecomeNativeShareOrHome() {
        for (String pkg : new String[]{"com.alicloud.databox", "com.mfcloudcalculate.networkdisk"}) {
            for (String url : new String[]{"https://evil.example/s/LinkIslandFixture24", "https://alipan.com.evil.example/s/LinkIslandFixture24",
                    "https://alipan.com@evil.example/s/LinkIslandFixture24", "https://alipan.com/", "https://alipan.com/s/id/other",
                    "https://alipan.com/s/id%2Fother", "https://alipan.com:9000/s/LinkIslandFixture24", "https://123pan.com/disk/main",
                    "https://123pan.com/s/LinkIslandFixture24/extra", "https://sub.123pan.com/s/LinkIslandFixture24", "pan://umeng.com/share/list"})
                assertNull(CloudLinks.nativeTarget(url, pkg, "a1B2"));
        }
        assertNull(CloudLinks.nativeTarget("https://alipan.com/s/LinkIslandFixture24", "com.example.other", "a1B2"));
        assertNull(CloudLinks.nativeTarget("https://123pan.com/s/LinkIslandFixture24", "com.alicloud.databox", "a1B2"));
    }
}
