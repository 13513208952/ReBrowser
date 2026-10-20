package io.github.rebrowser.engine;

import android.app.Activity;
import android.graphics.Insets;
import android.graphics.Rect;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

/** Read-only OWN window geometry. Does not set global orientation, density, UA or engine zoom. */
public final class ViewportPolicy {
    public enum WidthClass { COMPACT, MEDIUM, EXPANDED }
    public static final class Snapshot {
        private final int widthDp,heightDp,maximumWidthDp;private final boolean multiWindow;private final WidthClass widthClass;
        private Snapshot(int width,int height,int maximum,boolean multi){widthDp=width;heightDp=height;maximumWidthDp=maximum;multiWindow=multi;widthClass=width<600?WidthClass.COMPACT:width<840?WidthClass.MEDIUM:WidthClass.EXPANDED;}
        public int widthDp(){return widthDp;}
        public int heightDp(){return heightDp;}
        public WidthClass widthClass(){return widthClass;}
        public boolean portrait(){return heightDp>=widthDp;}
        public boolean narrowWindowOnLargeDisplay(){return multiWindow && maximumWidthDp>=840 && widthDp<840;}
        public int panelWidthDp(){return Math.min(widthDp,Math.min(360,Math.max(240,widthDp-32)));}
        public boolean useSideBySideOverview(){return widthClass==WidthClass.EXPANDED && !narrowWindowOnLargeDisplay();}
    }
    public static Snapshot inspectOwnWindow(Activity activity){
        if(activity==null || activity.isDestroyed())throw invalid();
        WindowManager manager=activity.getSystemService(WindowManager.class);if(manager==null)throw new IllegalStateException("Own window metrics unavailable");
        WindowMetrics current=manager.getCurrentWindowMetrics(),maximum=manager.getMaximumWindowMetrics();
        Rect bounds=current.getBounds();Insets bars=current.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        return observed(bounds.width(),bounds.height(),bars.left+bars.right,bars.top+bars.bottom,maximum.getBounds().width(),activity.getResources().getDisplayMetrics().density,activity.isInMultiWindowMode());
    }
    /** Synthetic observations test policy arithmetic, not Android metrics/layout qualification. */
    public static Snapshot observed(int widthPx,int heightPx,int horizontalInsets,int verticalInsets,int maximumWidthPx,float density,boolean multiWindow){
        if(widthPx<1 || heightPx<1 || maximumWidthPx<1 || horizontalInsets<0 || verticalInsets<0 || horizontalInsets>=widthPx || verticalInsets>=heightPx || !Float.isFinite(density) || density<=0 || density>16)throw invalid();
        long width=(long)Math.floor((widthPx-(long)horizontalInsets)/density),height=(long)Math.floor((heightPx-(long)verticalInsets)/density),maximum=(long)Math.floor(maximumWidthPx/density);
        if(width<1 || height<1 || width>32768 || height>32768 || maximum<width || maximum>32768)throw invalid();
        return new Snapshot((int)width,(int)height,(int)maximum,multiWindow);
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid own-window geometry");}
    private ViewportPolicy(){}
}
