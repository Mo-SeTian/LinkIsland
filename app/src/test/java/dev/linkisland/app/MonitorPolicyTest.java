package dev.linkisland.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class MonitorPolicyTest {
    @Test public void onlyResumesWhenUserWantsItAndRecoveryIsEnabled() {
        assertTrue(MonitorPolicy.shouldResume(true, true));
        assertFalse(MonitorPolicy.shouldResume(false, true));
        assertFalse(MonitorPolicy.shouldResume(true, false));
        assertFalse(MonitorPolicy.shouldResume(false, false));
    }
    @Test public void reconnectsBackOffAndCapAtOneMinute() {
        assertEquals(2000, MonitorPolicy.retryDelay(1));
        assertEquals(5000, MonitorPolicy.retryDelay(2));
        assertEquals(10000, MonitorPolicy.retryDelay(3));
        assertEquals(30000, MonitorPolicy.retryDelay(4));
        assertEquals(60000, MonitorPolicy.retryDelay(5));
        assertEquals(60000, MonitorPolicy.retryDelay(100));
    }
    @Test public void heartbeatExpiresAfterThirtyAwakeSeconds() {
        assertFalse(MonitorPolicy.heartbeatExpired(1000, 30_999));
        assertTrue(MonitorPolicy.heartbeatExpired(1000, 31_000));
        assertTrue(MonitorPolicy.heartbeatExpired(1000, 60_000));
    }
    @Test public void uninitializedOrFutureHeartbeatDoesNotTriggerRecovery() {
        assertFalse(MonitorPolicy.heartbeatExpired(0, 60_000));
        assertFalse(MonitorPolicy.heartbeatExpired(2000, 1000));
    }
}
