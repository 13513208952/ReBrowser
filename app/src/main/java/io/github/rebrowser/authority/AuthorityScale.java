package io.github.rebrowser.authority;

/** APK policy's engineering scale, not levels asserted by incoming requests. */
public final class AuthorityScale {
    public static final int MIN = 0;
    public static final int HIGH_BOUNDARY = 5;
    public static final int HIGHEST = 9;
    public static final int ROOT = 10;
    private AuthorityScale() {}

    public static void requireLevel(int level) {
        if (level < MIN || level > ROOT) throw new IllegalArgumentException("Invalid authority level");
    }
    /** Ordinary control is >=; other source/scope/grant/platform gates still apply. */
    public static boolean meetsThreshold(int effectiveLevel, int threshold) {
        requireLevel(effectiveLevel); requireLevel(threshold); return effectiveLevel >= threshold;
    }
    /** Root is never disableable; disable is strictly greater, unlike ordinary control. */
    public static boolean mayDisableLevel(int actorLevel, int targetLevel) {
        requireLevel(actorLevel); requireLevel(targetLevel); return targetLevel != ROOT && actorLevel > targetLevel;
    }
    public static boolean isLowDelegationLevel(int level) {
        requireLevel(level); return level < HIGH_BOUNDARY;
    }
}
