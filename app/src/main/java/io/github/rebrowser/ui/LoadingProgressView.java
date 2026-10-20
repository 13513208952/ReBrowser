package io.github.rebrowser.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/** Owned reported progress only. UI animation/hiding NEVER means domain task/login/storage end. */
public final class LoadingProgressView extends View {
    public static final class LoadRef {
        private final Model issuer;private final Object session,document;
        private LoadRef(Model issuer,Object session,Object document){this.issuer=issuer;this.session=session;this.document=document;}
    }
    /** Actual presentation reducer; token provenance remains native adapter's responsibility. */
    public static final class Model {
        private final Thread thread=Thread.currentThread();private LoadRef current;private int progress;private boolean stopped;
        public LoadRef begin(Object exactNativeSession,Object nativeEstablishedDocument){check();if(exactNativeSession==null || nativeEstablishedDocument==null)throw invalid();current=new LoadRef(this,exactNativeSession,nativeEstablishedDocument);progress=0;stopped=false;return current;}
        public boolean reported(LoadRef ref,int value){check();if(!owns(ref) || stopped)return false;if(value<0 || value>100)throw invalid();if(value<progress)return false;progress=value;return true;}
        public boolean pageStopped(LoadRef ref){check();if(!owns(ref) || stopped)return false;stopped=true;return true;}
        public boolean owns(LoadRef ref){check();return ref!=null && ref.issuer==this && current==ref;}
        public boolean completed(LoadRef ref){check();return owns(ref) && progress==100;}
        public int reportedPercent(){check();return progress;}
        public void retire(){check();current=null;progress=0;stopped=true;}
        private void check(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Progress presentation owner thread required");}
    }
    private final Model model=new Model();private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private BrowserToolbar.Binding binding;private LoadRef current;private ValueAnimator movement;private float displayed;private Runnable fade;
    private static final int[] COLORS={0xfff59e42,0xffe1698b,0xff8b5cf6};
    public LoadingProgressView(Context context){super(context);BrowserToolbar.main();setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setVisibility(INVISIBLE);}
    /** Never derive this token by reacquiring the latest document for an old SDK callback. */
    public LoadRef beginReportedLoad(BrowserToolbar.Binding nativeWindow,Object exactSession,Object actualNativeDocument){BrowserToolbar.main();if(nativeWindow==null)throw invalid();nativeWindow.check();unbind();binding=nativeWindow;current=model.begin(exactSession,actualNativeDocument);displayed=0;setAlpha(1);setVisibility(VISIBLE);invalidate();return current;}
    public void report(LoadRef capturedNativeLoad,int reportedPercent){BrowserToolbar.main();if(!allowed(capturedNativeLoad))return;if(!model.reported(capturedNativeLoad,reportedPercent))return;cancelMotion();final LoadRef captured=current;final ValueAnimator animator=ValueAnimator.ofFloat(displayed,reportedPercent/100f);movement=animator;animator.setDuration(180);animator.addUpdateListener(value->{if(movement==animator && current==captured && allowed(captured)){displayed=(float)value.getAnimatedValue();invalidate();}});animator.start();if(model.completed(captured)){fade=()->{if(current==captured && allowed(captured))setVisibility(INVISIBLE);};postDelayed(fade,260);}}
    /** Page stop hides presentation; no guessed100%, no resource-slot/checkpoint acknowledgment. */
    public void pageStopped(LoadRef captured){BrowserToolbar.main();if(!allowed(captured) || !model.pageStopped(captured))return;cancelMotion();setVisibility(INVISIBLE);}
    public void unbind(){BrowserToolbar.main();cancelMotion();binding=null;current=null;model.retire();displayed=0;setAlpha(1);setVisibility(INVISIBLE);invalidate();}
    private boolean allowed(LoadRef ref){if(ref!=current || binding==null || !model.owns(ref))return false;try{binding.check();return ref==current;}catch(SecurityException stale){unbind();return false;}}
    private void cancelMotion(){if(fade!=null){removeCallbacks(fade);fade=null;}if(movement!=null){ValueAnimator old=movement;movement=null;old.removeAllUpdateListeners();old.removeAllListeners();old.cancel();}animate().cancel();}
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){super.onSizeChanged(width,height,oldWidth,oldHeight);paint.setShader(new LinearGradient(0,0,Math.max(1,width),0,COLORS,null,Shader.TileMode.CLAMP));}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawRoundRect(0,0,getWidth()*displayed,getHeight(),getHeight()/2f,getHeight()/2f,paint);}
    @Override protected void onDetachedFromWindow(){unbind();super.onDetachedFromWindow();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native reported progress binding");}
}
