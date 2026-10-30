package dev.linkisland.app;

import org.junit.Test;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public final class XhsLinksTest {
    private static final String SHORT = "https://xhslink.cn/o/test";
    private static final String NOTE = "https://www.xiaohongshu.com/discovery/item/0123456789abcdef01234567?xsec_token=TEST_SIGNATURE&xsec_source=app_share";
    private static final class Connection extends HttpURLConnection {
        final int status;
        final String location;
        boolean closed;
        Connection(URL url, int status, String location) { super(url); this.status = status; this.location = location; }
        @Override public void connect() { connected = true; }
        @Override public int getResponseCode() { return status; }
        @Override public String getHeaderField(String name) { return "Location".equalsIgnoreCase(name) ? location : null; }
        @Override public void disconnect() { closed = true; }
        @Override public boolean usingProxy() { return false; }
    }
    @Test public void onlyHandlesKnownHttpsShortLinksForXhs() {
        assertTrue(XhsLinks.supports(SHORT, "com.xingin.xhs"));
        assertTrue(XhsLinks.supports("https://xhslink.com/a/test", "com.xingin.xhs"));
        for (String url : List.of("https://xhslink.cn.evil.example/o/test", "http://xhslink.cn/o/test", "https://user@xhslink.cn/o/test", "https://xhslink.cn:444/o/test"))
            assertFalse(XhsLinks.supports(url, "com.xingin.xhs"));
        assertFalse(XhsLinks.supports(SHORT, "com.example.other"));
    }
    @Test public void expands302WithoutFetchingTheNoteOrDroppingSignedParameters() throws Exception {
        List<Connection> opened = new ArrayList<>();
        String result = XhsLinks.resolve(SHORT, url -> {
            Connection c = new Connection(url, 302, NOTE); opened.add(c); return c;
        });
        assertEquals(NOTE, result);
        assertEquals(1, opened.size());
        assertFalse(opened.get(0).getInstanceFollowRedirects());
        assertTrue(opened.get(0).closed);
        assertTrue(opened.get(0).getConnectTimeout() > 0);
        assertTrue(opened.get(0).getReadTimeout() > 0);
    }
    @Test public void handlesRelativeRedirects() throws Exception {
        int[] calls = {0};
        assertEquals(NOTE, XhsLinks.resolve(SHORT, url -> new Connection(url, 302, calls[0]++ == 0 ? "/o/next" : NOTE)));
        assertEquals(2, calls[0]);
    }
    @Test public void doesNotFollowUnrelatedPrivateOrDowngradedDestinations() {
        for (String next : List.of("https://example.org/note", "https://127.0.0.1/", "http://www.xiaohongshu.com/discovery/item/test", "intent://note", "https://www.xiaohongshu.com.evil.example/note")) {
            int[] calls = {0};
            assertThrows(IOException.class, () -> XhsLinks.resolve(SHORT, url -> {
                calls[0]++; return new Connection(url, 302, next);
            }));
            assertEquals(1, calls[0]);
        }
    }
    @Test public void rejectsLoopsAndNonRedirectResponses() {
        assertThrows(IOException.class, () -> XhsLinks.resolve(SHORT, url -> new Connection(url, 302, SHORT)));
        assertThrows(IOException.class, () -> XhsLinks.resolve(SHORT, url -> new Connection(url, 302, null)));
        assertThrows(IOException.class, () -> XhsLinks.resolve(SHORT, url -> new Connection(url, 200, null)));
    }
    @Test public void capsTheRedirectChain() {
        int[] calls = {0};
        assertThrows(IOException.class, () -> XhsLinks.resolve(SHORT, url -> new Connection(url, 302, "/o/hop" + ++calls[0])));
        assertEquals(4, calls[0]);
    }
    @Test public void nativeNoteFallbackPreservesEncodedQueryExactly() {
        assertEquals("xhsdiscover://item/0123456789abcdef01234567?xsec_token=TEST_SIGNATURE&xsec_source=app_share", XhsLinks.nativeNote(NOTE));
        String encoded = "https://www.xiaohongshu.com/explore/0123456789abcdef01234567?xsec_token=a%2Bb%2Fc%3D&source=a+b";
        assertEquals("xhsdiscover://item/0123456789abcdef01234567?xsec_token=a%2Bb%2Fc%3D&source=a+b", XhsLinks.nativeNote(encoded));
        assertNull(XhsLinks.nativeNote("https://www.xiaohongshu.com/user/profile/test"));
        assertNull(XhsLinks.nativeNote("https://example.org/discovery/item/0123456789abcdef01234567"));
    }
}
