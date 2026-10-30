package dev.linkisland.app;

final class PromptDuration {
    static final int[] OPTIONS = {5, 10, 15, 30};
    static int valid(int seconds) {
        for (int option : OPTIONS) if (option == seconds) return seconds;
        return 30;
    }
}
