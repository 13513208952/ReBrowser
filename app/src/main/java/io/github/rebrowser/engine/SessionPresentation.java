package io.github.rebrowser.engine;

/** Bounded owner-thread SESSION observations, never document identity, grants or work-end proof. */
public final class SessionPresentation {
    public static final class Frame {
        private final boolean secure,certificateException;private final int securityMode;
        private final long sequence;private final String title,location;private final boolean back,forward,loading,fullScreen,closeRequested,retired;private final int progress;private final Boolean stoppedSuccessfully;
        private Frame(SessionPresentation m){secure=m.secure;certificateException=m.certificateException;securityMode=m.securityMode;sequence=m.sequence;title=m.title;location=m.location;back=m.back;forward=m.forward;loading=m.loading;fullScreen=m.fullScreen;closeRequested=m.closeRequested;retired=m.retired;progress=m.progress;stoppedSuccessfully=m.stoppedSuccessfully;}
        public boolean reportedSecure(){return secure;}
        public boolean reportedCertificateException(){return certificateException;}
        public int reportedSecurityMode(){return securityMode;}
        public long observationSequence(){return sequence;}
        public String reportedTitle(){return title;}
        public String reportedLocation(){return location;}
        public boolean reportedCanGoBack(){return back;}
        public boolean reportedCanGoForward(){return forward;}
        public boolean loadingObserved(){return loading;}
        public int reportedProgress(){return progress;}
        public Boolean reportedStopSuccess(){return stoppedSuccessfully;}
        public boolean reportedFullScreen(){return fullScreen;}
        public boolean closeWasRequested(){return closeRequested;}
        public boolean retired(){return retired;}
    }
    private boolean secure,certificateException;private int securityMode;
    private final Thread thread=Thread.currentThread();private long sequence;private String title,location;private boolean back,forward,loading,fullScreen,closeRequested,retired;private int progress;private Boolean stoppedSuccessfully;
    public SessionPresentation(){}
    public Frame snapshot(){check();return new Frame(this);}
    void title(String value){check();String text=bounded(value,4096);if(text==null || retired)return;if(next())title=text;}
    void location(String value){check();String text=bounded(value,8192);if(text==null || retired)return;if(next())location=text;}
    void back(boolean value){if(next())back=value;}
    void forward(boolean value){if(next())forward=value;}
    void start(){if(next()){loading=true;progress=0;stoppedSuccessfully=null;}}
    void progress(int value){check();if(value<0 || value>100)return;if(next())progress=Math.max(progress,value);}
    void stop(boolean success){if(next()){loading=false;stoppedSuccessfully=success;}} // NOT100%, NOT whole-chain end
    void security(boolean secure,boolean exception,int mode){if(next()){this.secure=secure;certificateException=exception;securityMode=mode;}}
    void fullScreen(boolean value){if(next())fullScreen=value;}
    void closeRequested(){if(next())closeRequested=true;} // request only; no auto-close/finish
    void retire(){check();if(retired)return;retired=true;secure=false;certificateException=false;securityMode=0;title=null;location=null;back=false;forward=false;loading=false;fullScreen=false;stoppedSuccessfully=null;if(sequence<Long.MAX_VALUE)sequence++;}
    private boolean next(){check();if(retired)return false;if(sequence==Long.MAX_VALUE){retire();return false;}sequence++;return true;}
    private void check(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Session presentation owner thread required");}
    private static String bounded(String value,int max){if(value==null || value.length()>max)return null;for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))return null;if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))return null;}else if(Character.isLowSurrogate(c))return null;}return value;}
}
