package dev.linkisland.app;

import org.junit.Test;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public final class ClipboardAccessTest {
    public static final class MiuiClipboard {
        final List<String> calls = new ArrayList<>();
        Object vendorResult = "vendor clip";
        Object standardResult = "standard clip";
        public Object getPrimaryClip(String pkg, String tag, int user, int device, boolean userOperate) {
            assertEquals("com.android.shell", pkg);
            assertNull(tag); assertEquals(10, user); assertEquals(0, device); assertTrue(userOperate);
            calls.add("miui");
            return vendorResult;
        }
        public Object getPrimaryClip(String pkg, String tag, int user, int device) {
            calls.add("aosp");
            return standardResult;
        }
    }
    @Test public void prefersVendorUserOperateOverload() throws Exception {
        MiuiClipboard clipboard = new MiuiClipboard();
        assertEquals("vendor clip", new ClipboardAccess(clipboard, 10).read());
        assertEquals(List.of("miui"), clipboard.calls);
    }
    @Test public void triesOtherKnownSignatureWhenVendorReturnsNull() throws Exception {
        MiuiClipboard clipboard = new MiuiClipboard();
        clipboard.vendorResult = null;
        assertEquals("standard clip", new ClipboardAccess(clipboard, 10).read());
        assertEquals(List.of("miui", "aosp"), clipboard.calls);
    }
    public static final class BlockedClipboard {
        public Object getPrimaryClip(String pkg, String tag, int user, int device) {
            throw new SecurityException("Synthetic restriction");
        }
    }
    @Test(expected = InvocationTargetException.class) public void keepsAccessFailureSeparateFromEmptyClip() throws Exception {
        new ClipboardAccess(new BlockedClipboard(), 0).read();
    }
    @Test public void representsEmptyClipboardWithoutCallingItAnInvalidLink() throws Exception {
        MiuiClipboard clipboard = new MiuiClipboard();
        clipboard.vendorResult = null; clipboard.standardResult = null;
        assertNull(new ClipboardAccess(clipboard, 10).read());
    }
    @Test public void reusedReaderAlwaysReadsTheCurrentClip() throws Exception {
        MiuiClipboard clipboard = new MiuiClipboard();
        ClipboardAccess access = new ClipboardAccess(clipboard, 10);
        assertEquals("vendor clip", access.read());
        clipboard.vendorResult = "next clip";
        assertEquals("next clip", access.read());
        clipboard.vendorResult = null; clipboard.standardResult = null;
        assertNull(access.read());
        assertEquals(List.of("miui", "miui", "miui", "aosp"), clipboard.calls);
    }
    public static final class UserClipboard {
        public Object getPrimaryClip(String pkg, int user) { return user; }
    }
    @Test public void cachedArgumentsRemainSpecificToEachUser() throws Exception {
        UserClipboard clipboard = new UserClipboard();
        ClipboardAccess owner = new ClipboardAccess(clipboard, 0);
        ClipboardAccess secondary = new ClipboardAccess(clipboard, 10);
        assertEquals(0, owner.read()); assertEquals(10, secondary.read());
        assertEquals(0, owner.read()); assertEquals(10, secondary.read());
    }
}
