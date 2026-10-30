package dev.linkisland.app;

final class MonitorPolicy {
    static long retryDelay(int attempt) {
        long[] delays = {2_000, 5_000, 10_000, 30_000, 60_000};
        return delays[Math.max(0, Math.min(attempt - 1, delays.length - 1))];
    }
    static boolean shouldResume(boolean wanted, boolean automatic) { return wanted && automatic; }
    static boolean heartbeatExpired(long last, long now) { return last > 0 && now - last >= 30_000; }
}
