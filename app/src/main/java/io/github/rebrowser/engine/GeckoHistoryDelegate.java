package io.github.rebrowser.engine;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/**
 * Read-only adapter for the pinned SDK HistoryDelegate. It stores NO URL, opens no HistoryStore,
 * never returns a fabricated "visited" answer and never turns an authenticated transient into a
 * committed history record. Without a native query backend the visited callbacks return null (the
 * SDK decides its own default) and the owner is told history is UNCONNECTED.
 */
public final class GeckoHistoryDelegate implements GeckoSession.HistoryDelegate {
    public enum Status { UNCONNECTED, OBSERVED }
    public interface Guard { boolean current(GeckoSession boundSession); }
    public interface Listener { void observed(Observation observation); }

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
    private long sequence;private boolean invalidated;
    private final Thread owner=Thread.currentThread();private boolean checking;

    public GeckoHistoryDelegate(GeckoSession exactSession,Guard guard,Listener listener){
        if(exactSession==null || guard==null || listener==null)throw new IllegalArgumentException("Exact session/guard/listener required");
        bound=exactSession;this.guard=guard;this.listener=listener;
    }

    /** No highlight decision is made by this adapter; null keeps the SDK default. */
    @Override public GeckoResult<Boolean> onVisited(GeckoSession session,String url,String lastVisitedUrl,int flags){return null;}

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

    public void invalidate(){thread();invalidated=true;}

    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Native history observation owner thread required");}
    private boolean eligible(GeckoSession session){
        thread();if(invalidated || checking || session!=bound)return false;checking=true;
        try{boolean current=guard.current(bound);return !invalidated && current;}catch(RuntimeException unavailable){return false;}finally{checking=false;}
    }
}
