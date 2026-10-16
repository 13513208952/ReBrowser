package io.github.reoutlook;

/** Pure overlay state/geometry. Position is independent from mode; a new runtime starts OFF. */
final class ReBrowserMusicLyricWindow {
    enum Mode {
        OFF, UNLOCKED, LOCKED;
        Mode next() { return values()[(ordinal() + 1) % values().length]; }
        String label() { return switch (this) { case OFF -> "关闭"; case UNLOCKED -> "可拖动"; case LOCKED -> "锁定穿透"; }; }
    }
    record Placement(int x, int y, int width, int height) {}
    private Mode mode = Mode.OFF;
    private float xFraction, yFraction;
    ReBrowserMusicLyricWindow(float x, float y) { xFraction = fraction(x, .5f); yFraction = fraction(y, .25f); }
    Mode mode() { return mode; }
    boolean cycle(boolean allowed) {
        if (mode == Mode.OFF && !allowed) return false;
        mode = mode.next(); return true;
    }
    void off() { mode = Mode.OFF; }
    float xFraction() { return xFraction; }
    float yFraction() { return yFraction; }
    Placement place(int availableWidth, int availableHeight, int desiredWidth, int desiredHeight, boolean tablet) {
        int w = Math.max(0, Math.min(availableWidth, desiredWidth));
        int h = Math.max(0, Math.min(availableHeight, desiredHeight));
        int dx = Math.max(0, availableWidth - w), dy = Math.max(0, availableHeight - h);
        return new Placement(tablet ? Math.round(xFraction * dx) : dx / 2, Math.round(yFraction * dy), w, h);
    }
    Placement drag(Placement start, double dx, double dy, int availableWidth, int availableHeight, boolean tablet) {
        if (mode != Mode.UNLOCKED || !Double.isFinite(dx) || !Double.isFinite(dy)) return start;
        int maxX = Math.max(0, availableWidth - start.width), maxY = Math.max(0, availableHeight - start.height);
        if (tablet && maxX > 0) xFraction = (float) (Math.max(0, Math.min(maxX, start.x + dx)) / maxX);
        if (maxY > 0) yFraction = (float) (Math.max(0, Math.min(maxY, start.y + dy)) / maxY);
        return place(availableWidth, availableHeight, start.width, start.height, tablet);
    }
    static float lockedAlpha(float maximum) {
        return Float.isFinite(maximum) ? Math.max(0, Math.min(.75f, maximum)) : 0;
    }
    private static float fraction(float value, float fallback) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : fallback;
    }
}
