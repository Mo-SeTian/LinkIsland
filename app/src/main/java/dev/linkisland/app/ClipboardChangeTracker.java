package dev.linkisland.app;

import java.util.Objects;

/** Prevents polling from repeatedly prompting for the same clipboard snapshot. */
final class ClipboardChangeTracker {
    private final long startedAt;
    private String observedUrl, emittedUrl;
    private long observedTimestamp, emittedTimestamp, emittedAt;
    private boolean observed;

    ClipboardChangeTracker(long startedAt) { this.startedAt = startedAt; }
    void baseline(String url, long timestamp) {
        observedUrl = url;
        observedTimestamp = timestamp;
        observed = true;
    }
    boolean shouldPrompt(String url, long timestamp, long elapsed, boolean event, boolean manual) {
        boolean changed = !observed || !Objects.equals(url, observedUrl) || timestamp != observedTimestamp;
        boolean fresh = timestamp > 0 ? timestamp >= startedAt : observed;
        baseline(url, timestamp);
        if (url == null) return false;
        if (!event && !manual && (!changed || !fresh)) return false;
        if (!manual && Objects.equals(url, emittedUrl)
            && ((timestamp > 0 && timestamp == emittedTimestamp) || elapsed - emittedAt <= 1000)) return false;
        emittedUrl = url; emittedTimestamp = timestamp; emittedAt = elapsed;
        return true;
    }
}
