package io.github.rebrowser.lyrics;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import io.github.rebrowser.ui.BrowserToolbar;

/**
 * Native lyric panel VIEW with a drag model and lock/passthrough LABELS. It is a normal in-app
 * view: it never adds itself to the system window, changes global flags or obtains overlay
 * permission, and the "穿透" label is only shown after the native window owner confirms the mode.
 * All texts/listeners are cleared on unbind.
 */
public final class LyricOverlayWindow extends FrameLayout {
    public static final int MAX_LINE_CHARS = 512;
    public static final int MAX_COORD = 32_768;

    /** Pure drag arithmetic: clamped origin, single-pointer only, cancel on multi-touch. */
    public static final class DragModel {
        private final int displayWidthDp,displayHeightDp,panelWidthDp,panelHeightDp;
        private boolean active,cancelled;private int startX,startY,originX,originY;
        public DragModel(int displayWidthDp,int displayHeightDp,int panelWidthDp,int panelHeightDp){
            if(displayWidthDp<1 || displayHeightDp<1 || displayWidthDp>MAX_COORD || displayHeightDp>MAX_COORD)throw new IllegalArgumentException("Invalid display size");
            if(panelWidthDp<1 || panelHeightDp<1 || panelWidthDp>displayWidthDp || panelHeightDp>displayHeightDp)throw new IllegalArgumentException("Invalid panel size");
            this.displayWidthDp=displayWidthDp;this.displayHeightDp=displayHeightDp;this.panelWidthDp=panelWidthDp;this.panelHeightDp=panelHeightDp;
        }
        public void start(int pointerCount,int panelOriginX,int panelOriginY,int touchX,int touchY){
            if(pointerCount!=1)throw new IllegalArgumentException("Single-pointer drag only");
            active=true;cancelled=false;
            originX=clampX(coord(panelOriginX));originY=clampY(coord(panelOriginY));
            startX=coord(touchX);startY=coord(touchY);
        }
        public void move(int pointerCount,int x,int y){
            if(!active)throw new IllegalStateException("Drag not started");
            if(cancelled)return;
            if(pointerCount!=1){cancel();return;}
            originX=clampX(originX+(coord(x)-startX));originY=clampY(originY+(coord(y)-startY));
            startX=coord(x);startY=coord(y);
        }
        public void end(){if(!active)throw new IllegalStateException("Drag not started");active=false;}
        /** Cancelling is idempotent even after a second finger already aborted the drag. */
        public void cancel(){cancelled=true;active=false;}
        public boolean cancelled(){return cancelled;}
        public boolean active(){return active;}
        public int x(){return originX;}
        public int y(){return originY;}
        private int clampX(int value){return Math.min(Math.max(0,value),displayWidthDp-panelWidthDp);}
        private int clampY(int value){return Math.min(Math.max(0,value),displayHeightDp-panelHeightDp);}
        private static int coord(int value){if(value< -MAX_COORD || value>MAX_COORD)throw new IllegalArgumentException("Coordinate out of range");return value;}
    }

    /** Plain display projection supplied by the official lyric owner; never HTML. */
    public static final class Display {
        private final String currentLine,nextLine;private final LyricOverlayController.Mode mode;
        public Display(String currentLine,String nextLine,LyricOverlayController.Mode mode){
            this.currentLine=text(currentLine,MAX_LINE_CHARS);this.nextLine=text(nextLine,MAX_LINE_CHARS);
            if(mode==null)throw new IllegalArgumentException("Missing overlay mode");
            this.mode=mode;
        }
        public String currentLine(){return currentLine;}
        public String nextLine(){return nextLine;}
        public LyricOverlayController.Mode mode(){return mode;}
        public String modeLabel(){switch(mode){case VISIBLE_LOCKED:return "已锁定（仅模式）";case VISIBLE_UNLOCKED:return "已解锁";default:return "已关闭";}}
    }

    private final TextView current,next,modeLabel;
    private DragModel drag;
    private BrowserToolbar.Binding binding;private Object frame;
    private int x,y;private LyricOverlayController.Mode mode=LyricOverlayController.Mode.CLOSED;

    public LyricOverlayWindow(Context context){
        super(context);
        ownerThread();
        setMinimumHeight(dp(96));
        current=new TextView(context);current.setTextSize(18);
        next=new TextView(context);next.setTextSize(14);
        modeLabel=new TextView(context);modeLabel.setTextSize(11);
        android.widget.LinearLayout lines=new android.widget.LinearLayout(context);lines.setOrientation(android.widget.LinearLayout.VERTICAL);lines.addView(current);lines.addView(next);lines.addView(modeLabel);addView(lines);
        setContentDescription("歌词浮层（应用内）");
    }

    public void bind(BrowserToolbar.Binding binding,Display display,DragModel model){
        ownerThread();
        if(binding==null || display==null || model==null)throw new IllegalArgumentException("Missing lyric overlay binding");
        binding.check();
        unbind();
        this.binding=binding;this.drag=model;this.frame=new Object();this.mode=display.mode();
        current.setText(display.currentLine());
        next.setText(display.nextLine());
        modeLabel.setText(display.modeLabel()+" · 拖动调整位置");
        current.setContentDescription("当前歌词：" + display.currentLine());
        next.setContentDescription("下一句：" + display.nextLine());
        setOnTouchListener(mode==LyricOverlayController.Mode.VISIBLE_UNLOCKED?(view,event)->handleTouch(event):null);
    }

    /** Called ONLY by the native window owner after it actually applied a drag origin. */
    public void showAppliedOrigin(int appliedX,int appliedY){
        ownerThread();
        if(frame==null || binding==null)return;
        try{binding.check();}catch(SecurityException | IllegalStateException stale){return;}
        if(drag==null)throw new IllegalStateException("No current drag geometry");
        drag.start(1,appliedX,appliedY,0,0);x=drag.x();y=drag.y();drag.cancel();
        setTranslationX(x*getResources().getDisplayMetrics().density);
        setTranslationY(y*getResources().getDisplayMetrics().density);
    }

    public void unbind(){
        ownerThread();
        if(drag!=null)drag.cancel();binding=null;drag=null;frame=null;x=0;y=0;mode=LyricOverlayController.Mode.CLOSED;
        setOnTouchListener(null);
        current.setText("");next.setText("");modeLabel.setText("");
        current.setContentDescription(null);next.setContentDescription(null);modeLabel.setContentDescription(null);
        setTranslationX(0);setTranslationY(0);
    }
    @Override protected void onDetachedFromWindow(){unbind();super.onDetachedFromWindow();}

    /** Local drag intent only; global/system window placement remains with the native owner. */
    boolean handleTouch(MotionEvent event){
        DragModel model=drag;BrowserToolbar.Binding captured=binding;Object exactFrame=frame;
        if(event==null || model==null || captured==null || exactFrame==null || mode!=LyricOverlayController.Mode.VISIBLE_UNLOCKED)return false;
        try{
            captured.check();
            if(frame!=exactFrame || binding!=captured)return false;
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    model.start(event.getPointerCount(),x,y,pointerDp(event.getRawX()),pointerDp(event.getRawY()));
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_POINTER_UP:
                    model.cancel();return true;
                case MotionEvent.ACTION_MOVE:
                    if(!model.active())return true; // a cancelled multi-touch gesture stays cancelled
                    model.move(event.getPointerCount(),pointerDp(event.getRawX()),pointerDp(event.getRawY()));
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!model.active())return true;
                    model.end();
                    setContentDescription(model.cancelled()?"拖动已取消":"拖动待原生应用（" + model.x() + "," + model.y() + "）");
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    model.cancel();return true;
                default:return false;
            }
        }catch(RuntimeException invalidGesture){
            model.cancel(); // never call end() on an already cancelled/inactive gesture
            return false;
        }
    }
    private int pointerDp(float px){float density=getResources().getDisplayMetrics().density;if(!Float.isFinite(px) || !Float.isFinite(density) || density<=0)throw new IllegalArgumentException("Invalid native pointer units");double dp=px/density;if(dp< -MAX_COORD || dp>MAX_COORD)throw new IllegalArgumentException("Pointer out of bounds");return (int)Math.round(dp);}
    int appliedX(){return x;}
    int appliedY(){return y;}
    private static String text(String value,int max){
        if(value==null)return "";
        if(value.length()>max)throw new IllegalArgumentException("Unbounded lyric text");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c) && c!='\n')throw new IllegalArgumentException("Control in lyric text");if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}
        return value;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static void ownerThread(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Native UI owner thread required");}
}
