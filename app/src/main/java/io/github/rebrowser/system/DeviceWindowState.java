package io.github.rebrowser.system;

import android.app.Activity;
import io.github.rebrowser.engine.ViewportPolicy;

/**
 * READ-ONLY facts about the OWN native window. Geometry arithmetic is delegated to the
 * approved ViewportPolicy; this file only combines its snapshot with the own display
 * rotation and multi-window/PiP flags. There is NO global orientation/density/role/timeout
 * change, NO device model/brand/serial/Android-ID gate and NO substitute for a foreground,
 * gesture, owner-consent or target-reference proof. Unknown native facts stay unknown and
 * are never coerced to false.
 */
public final class DeviceWindowState {
    /** Display rotation only; it says nothing about portrait/landscape shape (see geometry). */
    public enum Rotation { ROTATION_0, ROTATION_90, ROTATION_180, ROTATION_270, UNKNOWN }

    /** Optional own-window facts; Boolean null means the native fact was unavailable. */
    public static final class Facts {
        private final Rotation rotation;
        private final Boolean multiWindow, pictureInPicture;
        private Facts(Rotation rotation, Boolean multiWindow, Boolean pictureInPicture) {
            this.rotation = rotation; this.multiWindow = multiWindow; this.pictureInPicture = pictureInPicture;
        }
        public Rotation rotation() { return rotation; }
        public Boolean multiWindow() { return multiWindow; }
        public Boolean pictureInPicture() { return pictureInPicture; }
        public boolean rotationKnown() { return rotation != Rotation.UNKNOWN; }
        public boolean multiWindowKnown() { return multiWindow != null; }
        public boolean pictureInPictureKnown() { return pictureInPicture != null; }
    }

    /** Immutable combined snapshot; holds no Activity/Window reference. */
    public static final class Snapshot {
        private final ViewportPolicy.Snapshot geometry;
        private final Facts facts;
        private Snapshot(ViewportPolicy.Snapshot geometry, Facts facts) { this.geometry = geometry; this.facts = facts; }
        public ViewportPolicy.Snapshot geometry() { return geometry; }
        public Facts facts() { return facts; }
        public Rotation rotation() { return facts.rotation; }
        public Boolean multiWindow() { return facts.multiWindow; }
        public Boolean pictureInPicture() { return facts.pictureInPicture; }
        public ViewportPolicy.WidthClass widthClass() { return geometry.widthClass(); }
        public boolean portraitShape() { return geometry.portrait(); }
    }

    private DeviceWindowState() {}

    /** Actual own-window read. Geometry failure is an explicit failure, never guessed facts. */
    public static Snapshot inspectOwnWindow(Activity activity) {
        if (activity == null || activity.isDestroyed()) throw invalid();
        ViewportPolicy.Snapshot geometry = ViewportPolicy.inspectOwnWindow(activity);
        Integer surfaceRotation = null;
        Boolean multiWindow = null, pictureInPicture = null;
        try {
            android.view.Display display = activity.getDisplay();
            if (display != null) surfaceRotation = display.getRotation();
        } catch (RuntimeException unavailable) {
            // rotation stays UNKNOWN
        }
        try { multiWindow = activity.isInMultiWindowMode(); } catch (RuntimeException unavailable) { /* stays unknown */ }
        try { pictureInPicture = activity.isInPictureInPictureMode(); } catch (RuntimeException unavailable) { /* stays unknown */ }
        return new Snapshot(geometry, new Facts(observedPlatformRotation(surfaceRotation), multiWindow, pictureInPicture));
    }

    /** Pure combination for HOST verification; multiWindow is required because geometry depends on it. */
    public static Snapshot observed(int widthPx, int heightPx, int horizontalInsets, int verticalInsets,
                                    int maximumWidthPx, float density, boolean multiWindow,
                                    Integer rotationDegrees, Boolean inPictureInPicture) {
        ViewportPolicy.Snapshot geometry = ViewportPolicy.observed(widthPx, heightPx, horizontalInsets, verticalInsets, maximumWidthPx, density, multiWindow);
        return new Snapshot(geometry, facts(rotationDegrees, Boolean.valueOf(multiWindow), inPictureInPicture));
    }

    /** Optional-only facts where the native window metrics may be unavailable. */
    public static Facts observedWindowFacts(Integer rotationDegrees, Boolean multiWindow, Boolean inPictureInPicture) {
        return facts(rotationDegrees, multiWindow, inPictureInPicture);
    }

    private static Facts facts(Integer degrees, Boolean multiWindow, Boolean pictureInPicture) {
        return new Facts(rotation(degrees), multiWindow, pictureInPicture);
    }

    /** Android Display returns Surface.ROTATION_* values 0/1/2/3, NOT degrees. */
    public static Rotation observedPlatformRotation(Integer surfaceRotation) {
        if (surfaceRotation == null) return Rotation.UNKNOWN;
        switch (surfaceRotation.intValue()) {
            case android.view.Surface.ROTATION_0: return Rotation.ROTATION_0;
            case android.view.Surface.ROTATION_90: return Rotation.ROTATION_90;
            case android.view.Surface.ROTATION_180: return Rotation.ROTATION_180;
            case android.view.Surface.ROTATION_270: return Rotation.ROTATION_270;
            default: throw invalid();
        }
    }

    private static Rotation rotation(Integer degrees) {
        if (degrees == null) return Rotation.UNKNOWN;
        switch (degrees.intValue()) {
            case 0: return Rotation.ROTATION_0;
            case 90: return Rotation.ROTATION_90;
            case 180: return Rotation.ROTATION_180;
            case 270: return Rotation.ROTATION_270;
            default: throw invalid();
        }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid own-window observation"); }
}
