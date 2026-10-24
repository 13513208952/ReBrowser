package io.github.rebrowser.engine;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/**
 * Actual bound HistoryDelegate visit producer. Only native top-level web visits (not redirect
 * sources, failed loads or frames) reach the optional browser-owned IO sink. The adapter retains
 * no database/Activity; private-cookie history projection holds at most bounded current URIs, does not claim a queued write committed, and returns no fabricated
 * visited-link CSS answers. Three-argument observation-only consumers remain UNCONNECTED.
 */
public final class GeckoHistoryDelegate implements GeckoSession.HistoryDelegate {
    public enum Status { UNCONNECTED, OBSERVED }
    public interface Guard { boolean current(GeckoSession boundSession); }
    public interface Listener { void observed(Observation observation); }
    /** Actual bound SDK visit, not a DOM/payload claim or a request-completion receipt. */
    public interface VisitSink {void visited(String nativeUrl);}

    /** URL-free summary: counts and a transient classification only. */
    public static final class Observation {
        private final Status status;private final int entryCount,currentIndex;private final boolean titleTopLevelOnly;private final long sequence;
        Observation(Status status,int entryCount,int currentIndex,boolean titleTopLevelOnly,long sequence){
            this.status=status;this.entryCount=entryCount;this.currentIndex=currentIndex;this.titleTopLevelOnly=titleTopLevelOnly;this.sequence=sequence;
        }
        public Status status(){return status;}
        public int entryCount(){return entryCount;}
        public int currentIndex(){return currentIndex;}
        /** The history list carries titles only for top-level documents; display hint only. */
        public boolean titleTopLevelOnly(){return titleTopLevelOnly;}
        public long sequence(){return sequence;}
    }

    private final GeckoSession bound;private final Guard guard;private final Listener listener;
    private final VisitSink visits;private final PrivateSessionVisits privateVisits;
    private long sequence;private boolean invalidated;
    private final Thread owner=Thread.currentThread();private boolean checking;

    public GeckoHistoryDelegate(GeckoSession exactSession,Guard guard,Listener listener){this(exactSession,guard,listener,null);}
    public GeckoHistoryDelegate(GeckoSession exactSession,Guard guard,Listener listener,VisitSink nativeHistory){this(exactSession,guard,listener,nativeHistory,false);}
    GeckoHistoryDelegate(GeckoSession exactSession,Guard guard,Listener listener,VisitSink nativeHistory,boolean privateCookieJar){
        if(exactSession==null || guard==null || listener==null)throw new IllegalArgumentException("Exact session/guard/listener required");
        bound=exactSession;this.guard=guard;this.listener=listener;visits=nativeHistory;privateVisits=privateCookieJar && visits!=null?new PrivateSessionVisits(visits):null;
    }

    /** No highlight decision is made by this adapter; null keeps the SDK default. */
    @Override public GeckoResult<Boolean> onVisited(GeckoSession session,String url,String lastVisitedUrl,int flags){
        if(privateVisits==null && visits!=null && eligible(session) && acceptsVisit(url,flags))try{visits.visited(url);}catch(RuntimeException unavailable){/* optional history must not stop browsing */}
        return null; // No fabricated visited-link CSS result or IO-completion acknowledgement.
    }
    public static boolean acceptsVisit(String url,int flags){
        int known=VISIT_TOP_LEVEL|VISIT_REDIRECT_TEMPORARY|VISIT_REDIRECT_PERMANENT|VISIT_REDIRECT_SOURCE|VISIT_REDIRECT_SOURCE_PERMANENT|VISIT_UNRECOVERABLE_ERROR;
        if((flags&VISIT_TOP_LEVEL)==0 || (flags&~known)!=0 || (flags&(VISIT_REDIRECT_SOURCE|VISIT_REDIRECT_SOURCE_PERMANENT|VISIT_UNRECOVERABLE_ERROR))!=0)return false;
        try{io.github.rebrowser.browser.SearchEngines.requireWebUrl(url);return true;}catch(IllegalArgumentException invalid){return false;}
    }

    /** No fabricated visited answer; a real backend must be wired by the native owner. */
    @Override public GeckoResult<boolean[]> getVisited(GeckoSession session,String[] urls){return null;}

    @Override public void onHistoryStateChange(GeckoSession session,GeckoSession.HistoryDelegate.HistoryList historyList){
        if(!eligible(session) || historyList==null)return;
        int count;
        try{count=historyList.size();}catch(RuntimeException unavailable){return;}
        if(count<0 || count>1000)return;
        int index;
        try{index=historyList.getCurrentIndex();}catch(RuntimeException unavailable){return;}
        if(index< -1 || index>=Math.max(1,count))return;
        if(sequence==Long.MAX_VALUE){invalidated=true;return;}
        if(!eligible(session))return;sequence++;
        boolean titlesTopLevelOnly=count>0;
        Observation observation=new Observation(Status.UNCONNECTED,count,index,titlesTopLevelOnly,sequence);
        try{listener.observed(observation);}catch(RuntimeException ignored){/* observation only */}
    }

    void nativePageStart(String url){if(privateVisits!=null && eligible(bound))privateVisits.start(url);}
    void nativeLocation(String url){if(privateVisits!=null && eligible(bound))privateVisits.location(url);}
    void nativeContentPainted(){if(privateVisits!=null && eligible(bound))privateVisits.painted();}
    void nativeLoadError(){if(privateVisits!=null && eligible(bound))privateVisits.error();}
    void nativePageStop(boolean success){if(privateVisits!=null && eligible(bound))privateVisits.stop(success);}
    public void invalidate(){thread();invalidated=true;if(privateVisits!=null)privateVisits.clear();}

    /** Gecko's private cookie jars suppress global onVisited. They are NOT this product's
     * history-pause mode. Private session-state updates may be empty or delayed. Use actual
     * top-level location plus native PageStart/contentful-paint or PageStop(success), not synthetic VISIT flags,
     * DOM/user payload, serialized SessionState or a document-authority lease. Redirect
     * sources/failures do not record; reload/back/fragment visits remain distinct. */
    static final class PrivateSessionVisits {
        private final VisitSink sink;private String current,lastUri;private long load,lastLoad=-1;private boolean loading,successful,painted,failed,started,retired;
        PrivateSessionVisits(VisitSink sink){this.sink=sink;}
        void start(String reportedStart){if(retired)return;if(load==Long.MAX_VALUE){clear();return;}load++;String start=web(reportedStart);started=start!=null;loading=true;successful=false;painted=false;failed=false;if(!java.util.Objects.equals(start,current))current=null;}
        void location(String uri){if(retired)return;current=web(uri);emit();}
        void painted(){if(retired || failed)return;painted=true;emit();} // content seen, not full rendering/work end
        void error(){if(retired)return;failed=true;painted=false;successful=false;}
        void stop(boolean success){if(retired)return;loading=false;successful=success;emit();}
        private void emit(){if(retired || failed || !started || !painted && (loading || !successful) || current==null || lastLoad==load && current.equals(lastUri))return;lastLoad=load;lastUri=current;try{sink.visited(current);}catch(RuntimeException unavailable){/* optional recording cannot break browsing; no blind retry */}}
        void clear(){retired=true;current=null;lastUri=null;successful=false;painted=false;loading=false;}
        private static String web(String uri){try{return io.github.rebrowser.browser.SearchEngines.requireWebUrl(uri).url();}catch(IllegalArgumentException invalid){return null;}}
    }

    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Native history observation owner thread required");}
    private boolean eligible(GeckoSession session){
        thread();if(invalidated || checking || session!=bound)return false;checking=true;
        try{boolean current=guard.current(bound);return !invalidated && current;}catch(RuntimeException unavailable){return false;}finally{checking=false;}
    }
}
