package io.github.rebrowser.engine;

/** Bounded owner-thread SESSION observations, never document identity, grants or work-end proof. */
public final class SessionPresentation {
    public interface NativeObserver {void changed(Frame frame);}
    /** Native REPORTED_START identity for a progress bar only. It is NOT a document principal,
     * bridge lease, task-end receipt or authority to run page code. */
    public static final class LoadObservation {private LoadObservation(){}}
    public static final class Frame {
        private final boolean secure,certificateException,loadError;private final int securityMode;
        private final long sequence,locationSequence,fullScreenSequence;private final String title,location;private final boolean back,forward,loading,fullScreen,closeRequested,retired;private final int progress;private final Boolean stoppedSuccessfully;private final LoadObservation load;
        private Frame(SessionPresentation m){loadError=m.loadError;load=m.load;secure=m.secure;certificateException=m.certificateException;securityMode=m.securityMode;sequence=m.sequence;locationSequence=m.locationSequence;fullScreenSequence=m.fullScreenSequence;title=m.title;location=m.location;back=m.back;forward=m.forward;loading=m.loading;fullScreen=m.fullScreen;closeRequested=m.closeRequested;retired=m.retired;progress=m.progress;stoppedSuccessfully=m.stoppedSuccessfully;}
        public boolean reportedLoadError(){return loadError;}
        public boolean reportedSecure(){return secure;}
        public boolean reportedCertificateException(){return certificateException;}
        public int reportedSecurityMode(){return securityMode;}
        public long observationSequence(){return sequence;}
        public String reportedTitle(){return title;}
        public String reportedLocation(){return location;}public long reportedLocationSequence(){return locationSequence;}
        public boolean reportedCanGoBack(){return back;}
        public boolean reportedCanGoForward(){return forward;}
        public boolean loadingObserved(){return loading;}
        public int reportedProgress(){return progress;}
        public Boolean reportedStopSuccess(){return stoppedSuccessfully;}
        public boolean reportedFullScreen(){return fullScreen;}
        public long reportedFullScreenSequence(){return fullScreenSequence;}
        public boolean closeWasRequested(){return closeRequested;}
        public boolean retired(){return retired;}
        public LoadObservation reportedLoad(){return load;}
    }
    private boolean secure,certificateException,loadError;private int securityMode;
    private final Thread thread=Thread.currentThread();private long sequence,locationSequence,fullScreenSequence;private String title,location;private boolean back,forward,loading,fullScreen,closeRequested,retired;private int progress;private Boolean stoppedSuccessfully;private LoadObservation load;private NativeObserver observer;private boolean observing;
    public SessionPresentation(){}
    public Frame snapshot(){check();return new Frame(this);}
    /** Fixed native bundle/domain adapter only. Never install an Activity/View as the process
     * observer; the browser domain separately owns weak native-window subscriptions. */
    void installNativeObserver(NativeObserver observer){check();if(this.observer!=null || observer==null || retired)throw new IllegalStateException("Native presentation observer already bound");this.observer=observer;}
    void title(String value){check();String text=bounded(value,4096);if(text==null || retired)return;if(next()){title=text;signal();}}
    void location(String value){check();String text=bounded(value,8192);if(text==null || retired)return;if(next()){location=text;locationSequence=sequence;signal();}}
    void back(boolean value){if(next()){back=value;signal();}}
    void forward(boolean value){if(next()){forward=value;signal();}}
    void start(){if(next()){loadError=false;loading=true;progress=0;stoppedSuccessfully=null;load=new LoadObservation();signal();}}
    void progress(int value){check();if(value<0 || value>100)return;if(next()){progress=Math.max(progress,value);signal();}}
    void stop(boolean success){if(next()){loading=false;stoppedSuccessfully=success;signal();}} // NOT100%, NOT whole-chain end
    void loadError(){if(next()){loadError=true;signal();}} // error observation only, NOT whole-work end
    void security(boolean secure,boolean exception,int mode){if(next()){this.secure=secure;certificateException=exception;securityMode=mode;signal();}}
    void fullScreen(boolean value){if(next()){fullScreen=value;fullScreenSequence=sequence;signal();}}
    /** Caller must supply the ORIGINAL actual bound session's frame. No SDK return, unrelated
     * progress/location, retirement or another session establishes that fullscreen has exited. */
    public static boolean nativeFullscreenExitObserved(long before,Frame original){return before>0 && original!=null && !original.retired() && original.reportedFullScreenSequence()>before && !original.reportedFullScreen();}
    void closeRequested(){if(next()){closeRequested=true;signal();}} // request only; no auto-close/finish
    void retire(){check();if(retired)return;retired=true;loadError=false;secure=false;certificateException=false;securityMode=0;title=null;location=null;back=false;forward=false;loading=false;fullScreen=false;stoppedSuccessfully=null;load=null;if(sequence<Long.MAX_VALUE)sequence++;signal();observer=null;}
    private void signal(){if(observer==null || observing)return;observing=true;try{observer.changed(new Frame(this));}catch(RuntimeException unavailable){/* display publication failure is not native effect failure or work end */}finally{observing=false;}}
    private boolean next(){check();if(retired)return false;if(sequence==Long.MAX_VALUE){retire();return false;}sequence++;return true;}
    private void check(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Session presentation owner thread required");}
    private static String bounded(String value,int max){if(value==null || value.length()>max)return null;for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))return null;if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))return null;}else if(Character.isLowSurrogate(c))return null;}return value;}
}
