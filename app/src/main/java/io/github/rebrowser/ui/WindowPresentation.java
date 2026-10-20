package io.github.rebrowser.ui;

import android.app.Activity;
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
    private Padding original;private boolean closed,fullscreen;private int previousBehavior;
    public WindowPresentation(Activity ownedActivity){BrowserToolbar.main();if(ownedActivity==null || ownedActivity.isDestroyed() || ownedActivity.getWindow()==null)throw new IllegalArgumentException("Missing owned native window");activity=new WeakReference<>(ownedActivity);window=new WeakReference<>(ownedActivity.getWindow());}
    public void attachNativeContent(View ownedContent){
        BrowserToolbar.main();Window w=current();if(ownedContent==null || content.get()!=null)throw new IllegalStateException("Owned native content already attached");content=new WeakReference<>(ownedContent);original=new Padding(ownedContent.getPaddingLeft(),ownedContent.getPaddingTop(),ownedContent.getPaddingRight(),ownedContent.getPaddingBottom());
        w.setDecorFitsSystemWindows(false);WindowInsetsController controller=w.getInsetsController();if(controller!=null){int light=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;controller.setSystemBarsAppearance(light,light);}
        ownedContent.setOnApplyWindowInsetsListener((view,insets)->{if(closed || content.get()!=view)return insets;Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());Padding p=addOwnedInsets(original.left,original.top,original.right,original.bottom,bars.left,bars.top,bars.right,bars.bottom);view.setPadding(p.left,p.top,p.right,p.bottom);return insets;});ownedContent.requestApplyInsets();
    }
    public void enterOwnedFullscreen(BrowserToolbar.Binding nativeBinding){BrowserToolbar.main();if(nativeBinding==null)throw new IllegalArgumentException("Missing native fullscreen binding");nativeBinding.check();Window w=current();WindowInsetsController controller=w.getInsetsController();if(controller==null)throw new IllegalStateException("Owned window insets unavailable");if(!fullscreen){previousBehavior=controller.getSystemBarsBehavior();fullscreen=true;}controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsets.Type.systemBars());}
    /** Leaving is allowed after source locks; not owner consent and not an underlying task stop. */
    public void leaveOwnedFullscreen(){BrowserToolbar.main();if(!fullscreen)return;Window w=window.get();fullscreen=false;if(w!=null){WindowInsetsController controller=w.getInsetsController();if(controller!=null){controller.show(WindowInsets.Type.systemBars());controller.setSystemBarsBehavior(previousBehavior);}}}
    public boolean isOwnedFullscreen(){BrowserToolbar.main();return fullscreen;}
    @Override public void close(){BrowserToolbar.main();if(closed)return;leaveOwnedFullscreen();closed=true;View view=content.get();if(view!=null){view.setOnApplyWindowInsetsListener(null);if(original!=null)view.setPadding(original.left,original.top,original.right,original.bottom);}content.clear();window.clear();activity.clear();}
    private Window current(){if(closed)throw new IllegalStateException("Owned native window retired");Activity a=activity.get();Window w=window.get();if(a==null || w==null || a.isDestroyed() || a.isFinishing() || a.getWindow()!=w)throw new IllegalStateException("Owned native window unavailable");return w;}
}
