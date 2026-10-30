package dev.linkisland.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PddLinksTest {
    @Test public void nativeFallbackPreservesRawPageAndParameters() {
        assertEquals("pinduoduo://com.xunmeng.pinduoduo/goods2.html?ps=example%2Bvalue&x=a%20b#item",
            PddLinks.nativeTarget("https://mobile.yangkeduo.com/goods2.html?ps=example%2Bvalue&x=a%20b#item", "com.xunmeng.pinduoduo"));
    }
    @Test public void nativeFallbackIsRestrictedToSelectedAppAndItsDomains() {
        assertNull(PddLinks.nativeTarget("https://mobile.yangkeduo.com/a", "com.example.other"));
        assertNull(PddLinks.nativeTarget("https://yangkeduo.com.evil.example/a", "com.xunmeng.pinduoduo"));
        assertNull(PddLinks.nativeTarget("https://example.org/?next=yangkeduo.com", "com.xunmeng.pinduoduo"));
        assertNull(PddLinks.nativeTarget("pinduoduo://com.xunmeng.pinduoduo/a", "com.xunmeng.pinduoduo"));
    }
}
