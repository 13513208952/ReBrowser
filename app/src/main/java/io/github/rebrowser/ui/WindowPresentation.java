package io.github.rebrowser.ui;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import io.github.rebrowser.settings.SettingsRepository.WindowOrientation;
import io.github.rebrowser.settings.SettingsRepository.DeviceType;
import io.github.rebrowser.system.DeviceWindowState;
import android.graphics.Insets;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import java.lang.ref.WeakReference;

/** ONLY owned-window presentation. Never changes global rotation/timeouts/roles/volume/DND. */
public final class WindowPresentation implements AutoCloseable {
    public static final class Padding {
        private final int left,top,right,bottom;
        private Padding(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
        public int left(){return left;}public int top(){return top;}public int right(){return right;}public int bottom(){return bottom;}
    }
    public static Padding addOwnedInsets(int left,int top,int right,int bottom,int insetLeft,int insetTop,int insetRight,int insetBottom){
        int[] values={left,top,right,bottom,insetLeft,insetTop,insetRight,insetBottom};for(int n:values)if(n<0 || n>1048576)throw new IllegalArgumentException("Invalid own-window padding");return new Padding(left+insetLeft,top+insetTop,right+insetRight,bottom+insetBottom);
    }
    private final WeakReference<Activity> activity;private final WeakReference<Window> window;private WeakReference<View> content=new WeakReference<>(null);
    private Padding original;private boolean closed,fullscreen;private int previousBehavior;private Integer directionAttempt;
    public WindowPresentation(Activity ownedActivity){BrowserToolbar.main();if(ownedActivity==null || ownedActivity.isDestroyed() || ownedActivity.getWindow()==null)throw new IllegalArgumentException("Missing owned native window");activity=new WeakReference<>(ownedActivity);window=new WeakReference<>(ownedActivity.getWindow());}
    public void attachNativeContent(View ownedContent){
        BrowserToolbar.main();Window w=current();if(ownedContent==null || content.get()!=null)throw new IllegalStateException("Owned native content already attached");content=new WeakReference<>(ownedContent);original=new Padding(ownedContent.getPaddingLeft(),ownedContent.getPaddingTop(),ownedContent.getPaddingRight(),ownedContent.getPaddingBottom());
        w.setDecorFitsSystemWindows(false);WindowInsetsController controller=w.getInsetsController();if(controller!=null){int light=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;controller.setSystemBarsAppearance(light,light);}
        ownedContent.setOnApplyWindowInsetsListener((view,insets)->{if(closed || content.get()!=view)return insets;Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());Padding p=addOwnedInsets(original.left,original.top,original.right,original.bottom,bars.left,bars.top,bars.right,bars.bottom);view.setPadding(p.left,p.top,p.right,p.bottom);return insets;});ownedContent.requestApplyInsets();
    }
    /** Own chrome only: the approved new-tab photograph uses WHITE system foreground. */
    public void photoForeground(boolean photo){BrowserToolbar.main();Window w=current();WindowInsetsController controller=w.getInsetsController();if(controller!=null){int light=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;controller.setSystemBarsAppearance(photo?0:light,light);}w.setStatusBarColor(0x00000000);w.setNavigationBarColor(0x00000000);}
    public void enterOwnedFullscreen(BrowserToolbar.Binding nativeBinding){BrowserToolbar.main();if(nativeBinding==null)throw new IllegalArgumentException("Missing native fullscreen binding");nativeBinding.check();Window w=current();WindowInsetsController controller=w.getInsetsController();if(controller==null)throw new IllegalStateException("Owned window insets unavailable");if(!fullscreen){previousBehavior=controller.getSystemBarsBehavior();fullscreen=true;}controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsets.Type.systemBars());}
    /** Leaving is allowed after source locks; not owner consent and not an underlying task stop. */
    public void leaveOwnedFullscreen(){BrowserToolbar.main();if(!fullscreen)return;Window w=window.get();fullscreen=false;if(w!=null){WindowInsetsController controller=w.getInsetsController();if(controller!=null){controller.show(WindowInsets.Type.systemBars());controller.setSystemBarsBehavior(previousBehavior);}}}
    public boolean isOwnedFullscreen(){BrowserToolbar.main();return fullscreen;}
    /** Approved v1 own-window policy, not a System rotation setting. Legacy03 has no stored
     * direction: null uses the inherited fixed phone portrait policy without writing defaults. */
    public static int requestedDirectionFor(boolean tablet,boolean fullWindow,WindowOrientation mode){
        if(!fullWindow)return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        if(tablet)return ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
        return mode==WindowOrientation.FOLLOW_SYSTEM?ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED:
                mode==WindowOrientation.LANDSCAPE?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }
    public boolean manualDirectionAllowed(DeviceType choice){BrowserToolbar.main();current();Activity a=activity.get();return !tablet(a,choice) && !a.isInMultiWindowMode() && !a.isInPictureInPictureMode();}
    public boolean landscapeObserved(){BrowserToolbar.main();current();return activity.get().getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;}
    /** A request only, NEVER actual configuration/rotation completion. Same own-window
     * desired request is not repeated after an unknown call; a different explicit mode or
     * a newly created native window may make its own independent attempt. */
    public void applyObservedDirection(BrowserToolbar.Binding originalBinding,WindowOrientation mode,DeviceType choice){
        BrowserToolbar.main();if(originalBinding==null)throw new IllegalArgumentException("Native direction binding required");
        originalBinding.check();current();Activity a=activity.get();if(!a.hasWindowFocus())return;
        int wanted=requestedDirectionFor(tablet(a,choice),!a.isInMultiWindowMode() && !a.isInPictureInPictureMode(),mode);
        if(a.getRequestedOrientation()==wanted || directionAttempt!=null && directionAttempt.intValue()==wanted)return;
        originalBinding.check();directionAttempt=Integer.valueOf(wanted);a.setRequestedOrientation(wanted);
    }
    /** Fresh, verified original native user direction intent may make a new attempt. The
     * caller must already own the actual source/window; this method creates no such authority. */
    public void applyNativeDirectionIntent(BrowserToolbar.Binding original,WindowOrientation mode,DeviceType choice){
        BrowserToolbar.main();if(original==null || mode==null)throw new IllegalArgumentException("Native direction intent required");
        original.check();current();directionAttempt=null;applyObservedDirection(original,mode,choice);
    }
    private static boolean tablet(Activity a,DeviceType choice){return DeviceWindowState.inspectDevice(a,choice).tablet();}
    @Override public void close(){BrowserToolbar.main();if(closed)return;leaveOwnedFullscreen();closed=true;View view=content.get();if(view!=null){view.setOnApplyWindowInsetsListener(null);if(original!=null)view.setPadding(original.left,original.top,original.right,original.bottom);}content.clear();window.clear();activity.clear();}
    private Window current(){if(closed)throw new IllegalStateException("Owned native window retired");Activity a=activity.get();Window w=window.get();if(a==null || w==null || a.isDestroyed() || a.isFinishing() || a.getWindow()!=w)throw new IllegalStateException("Owned native window unavailable");return w;}
}
