package dev.linkisland.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ClipboardChangeTrackerTest {
    private static final String LINK = "https://github.com/LinkSheet/LinkSheet";

    @Test public void detectsCopyByPollingWithoutAnySystemEvent() {
        ClipboardChangeTracker tracker = new ClipboardChangeTracker(1000);
        tracker.baseline(null, 900);
        assertTrue(tracker.shouldPrompt(LINK, 1100, 2000, false, false));
        assertFalse(tracker.shouldPrompt(LINK, 1100, 4000, false, false));
        assertFalse(tracker.shouldPrompt(LINK, 1100, 6000, false, false));
    }
    @Test public void eventAndPollForSameCopyOnlyPromptOnce() {
        ClipboardChangeTracker tracker = new ClipboardChangeTracker(1000);
        assertTrue(tracker.shouldPrompt(LINK, 1100, 2000, true, false));
        assertFalse(tracker.shouldPrompt(LINK, 1100, 4000, false, false));
        assertFalse(tracker.shouldPrompt(LINK, 1100, 6000, true, false));
    }
    @Test public void repeatingCopyWithNewTimestampPromptsAgain() {
        ClipboardChangeTracker tracker = new ClipboardChangeTracker(1000);
        assertTrue(tracker.shouldPrompt(LINK, 1100, 2000, false, false));
        assertTrue(tracker.shouldPrompt(LINK, 4000, 5000, false, false));
    }
    @Test public void neverPromptsForOldClipWhenFirstReadWasBlocked() {
        ClipboardChangeTracker tracker = new ClipboardChangeTracker(1000);
        assertFalse(tracker.shouldPrompt(LINK, 900, 2000, false, false));
        assertFalse(tracker.shouldPrompt(LINK, 900, 4000, false, false));
    }
    @Test public void explicitProbeCanPromptForUnchangedClip() {
        ClipboardChangeTracker tracker = new ClipboardChangeTracker(1000);
        tracker.baseline(LINK, 900);
        assertTrue(tracker.shouldPrompt(LINK, 900, 2000, false, true));
        assertFalse(tracker.shouldPrompt(LINK, 900, 4000, false, false));
    }
    @Test public void missingTimestampStillTracksChangesAfterBaseline() {
        ClipboardChangeTracker tracker = new ClipboardChangeTracker(1000);
        tracker.baseline(null, 0);
        assertTrue(tracker.shouldPrompt(LINK, 0, 2000, false, false));
        assertFalse(tracker.shouldPrompt(LINK, 0, 4000, false, false));
    }
}
