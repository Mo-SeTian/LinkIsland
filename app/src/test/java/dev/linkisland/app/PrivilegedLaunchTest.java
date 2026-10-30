package dev.linkisland.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PrivilegedLaunchTest {
    @Test public void acceptsOnlyScopedTargetsWithValidPackage() {
        assertTrue(PrivilegedLaunch.allowed("https://github.com/LinkSheet/LinkSheet", "com.github.android"));
        assertTrue(PrivilegedLaunch.allowed("islandfixture://repo/42", "dev.linkisland.app.test"));
        assertFalse(PrivilegedLaunch.allowed("https://example.org", ""));
        assertFalse(PrivilegedLaunch.allowed("https://example.org", "com.app;echo"));
        assertFalse(PrivilegedLaunch.allowed("https://example.org/" + "x".repeat(4096), "com.example.app"));
    }
    @Test public void blocksFileContentIntentAndExecutableSchemes() {
        for (String scheme : new String[]{"file", "content", "intent", "data", "javascript"})
            assertFalse(PrivilegedLaunch.allowed(scheme + ":test", "com.example.app"));
    }
}
