package io.github.rebrowser.system;

import android.app.Activity;
import io.github.rebrowser.engine.ViewportPolicy;
import io.github.rebrowser.settings.SettingsRepository.DeviceType;
import android.util.DisplayMetrics;
import android.graphics.Rect;
import android.view.WindowManager;

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

    public enum DeviceKind { UNKNOWN, PHONE, TABLET }
    /** Read-only physical-DPI heuristic plus explicit app presentation correction. Never a
     * hardware attestation/whitelist/source/principal. UNKNOWN facts remain visible even when
     * inherited AUTO presentation uses its clearly labelled phone fallback. */
    public static final class DeviceClassification {
        private final DeviceType choice;private final DeviceKind automatic,effective;private final double diagonal;
        private DeviceClassification(DeviceType choice,DeviceKind automatic,DeviceKind effective,double diagonal){this.choice=choice;this.automatic=automatic;this.effective=effective;this.diagonal=diagonal;}
        public DeviceType choice(){return choice;}public DeviceKind automatic(){return automatic;}public DeviceKind effective(){return effective;}
        public boolean tablet(){return effective==DeviceKind.TABLET;}
        public boolean physicalKnown(){return automatic!=DeviceKind.UNKNOWN;}public double diagonalInches(){return diagonal;}
        public boolean phoneFallback(){return choice==DeviceType.AUTO && automatic==DeviceKind.UNKNOWN;}
        public String source(){return choice!=DeviceType.AUTO?"manual":physicalKnown()?"physical-dpi":"phone-fallback";}
        public String label(){String physical=physicalKnown()?String.format(java.util.Locale.ROOT,"原生 DPI 推算 %.2f 英寸",diagonal):"物理 DPI 信息未读取";return physical+"；当前"+(tablet()?"平板":"手机")+"模式（"+(choice!=DeviceType.AUTO?"手动纠正":phoneFallback()?"自动回退，可手动纠正":"自动识别")+"）";}
    }
    public static DeviceClassification classifyDevice(DeviceType choice,int maximumWidth,int maximumHeight,float xdpi,float ydpi){
        DeviceType requested=choice==null?DeviceType.AUTO:choice;double diagonal=Double.NaN;
        if(maximumWidth>0 && maximumWidth<=65536 && maximumHeight>0 && maximumHeight<=65536 && Float.isFinite(xdpi) && Float.isFinite(ydpi) && xdpi>0 && ydpi>0)
            diagonal=Math.hypot(maximumWidth/(double)xdpi,maximumHeight/(double)ydpi);
        boolean known=Double.isFinite(diagonal) && diagonal>0;
        DeviceKind automatic=known?(diagonal>=7.0?DeviceKind.TABLET:DeviceKind.PHONE):DeviceKind.UNKNOWN;
        DeviceKind effective=requested==DeviceType.TABLET?DeviceKind.TABLET:requested==DeviceType.PHONE?DeviceKind.PHONE:automatic==DeviceKind.UNKNOWN?DeviceKind.PHONE:automatic;
        return new DeviceClassification(requested,automatic,effective,diagonal);
    }
    /** Maximum OWN display bounds, not current split/IME/window dp width or webpage UA. */
    public static DeviceClassification inspectDevice(Activity activity,DeviceType choice){
        if(activity==null || activity.isDestroyed())throw invalid();int width=0,height=0;float xdpi=Float.NaN,ydpi=Float.NaN;
        try{DisplayMetrics metrics=activity.getResources().getDisplayMetrics();xdpi=metrics.xdpi;ydpi=metrics.ydpi;
            WindowManager manager=activity.getSystemService(WindowManager.class);if(manager!=null){Rect bounds=manager.getMaximumWindowMetrics().getBounds();width=bounds.width();height=bounds.height();}
        }catch(RuntimeException unavailable){/* keep exact unknown facts; manual choice still works */}
        return classifyDevice(choice,width,height,xdpi,ydpi);
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
