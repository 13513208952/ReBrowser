package io.github.rebrowser.permissions;

import io.github.rebrowser.bootstrap.ComponentGate;
import java.util.*;

/** Native exact-request prompt lifetime registry. Metadata/answers are NOT permission grants.
 * Spend before SDK callback; wrong session/document/generation cannot retarget a pending result.
 * Retirement suppresses positive publication but does NOT prove an OS prompt/worker ended. */
public final class PendingPromptRegistry {
    public interface Completion {void complete(boolean allow);}
    public static final class Ticket {
        private final PendingPromptRegistry issuer;private final Object request,session,document;private final long generation;private final Completion completion;private boolean attempted,retired;
        private Ticket(PendingPromptRegistry issuer,Object request,Object session,Object document,long generation,Completion completion){this.issuer=issuer;this.request=request;this.session=session;this.document=document;this.generation=generation;this.completion=completion;}
    }
    private final ComponentGate.DataEpochPin source;private final Thread thread=Thread.currentThread();private final IdentityHashMap<Object,Ticket> issued=new IdentityHashMap<>();private final int maximum;private boolean completing;
    public PendingPromptRegistry(ComponentGate.DataEpochPin nativeSource,int maximum){if(nativeSource==null || maximum<1 || maximum>128)throw new IllegalArgumentException("Native prompt limits required");source=nativeSource;this.maximum=maximum;source.requireCurrent();}
    public Ticket register(Object actualSdkRequest,Object nativeSession,Object nativeDocument,long nativeGeneration,Completion completion){current();if(completing || actualSdkRequest==null || nativeSession==null || nativeDocument==null || nativeGeneration<0 || completion==null || issued.containsKey(actualSdkRequest) || issued.size()>=maximum)throw denied();Ticket t=new Ticket(this,actualSdkRequest,nativeSession,nativeDocument,nativeGeneration,completion);issued.put(actualSdkRequest,t);return t;}
    /** Positive decision must already have real native user/OS/domain authorization. */
    public void answer(Ticket ticket,Object exactSession,Object exactDocument,long generation,boolean allow){
        owner();own(ticket);if(ticket.attempted || completing)throw denied();ticket.attempted=true;
        boolean positive=false;try{source.requireCurrent();positive=allow && !ticket.retired && exactSession==ticket.session && exactDocument==ticket.document && generation==ticket.generation;}catch(RuntimeException stale){positive=false;}
        completing=true;try{ticket.completion.complete(positive);}finally{completing=false;} // Unknown callback still spent, never retry.
    }
    public void retireDocument(Object exactSession,Object exactDocument){owner();for(Ticket t:issued.values())if(t.session==exactSession && t.document==exactDocument)t.retired=true;}
    public boolean attempted(Ticket ticket){owner();own(ticket);return ticket.attempted;}
    public void denyAllOwned(){owner();for(Ticket t:new ArrayList<>(issued.values()))if(!t.attempted)answer(t,t.session,t.document,t.generation,false);}
    private void own(Ticket ticket){if(ticket==null || ticket.issuer!=this || issued.get(ticket.request)!=ticket)throw denied();}
    private void current(){owner();source.requireCurrent();}
    private void owner(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Native prompt owner thread required");}
    private static SecurityException denied(){return new SecurityException("Native prompt unavailable or spent");}
}
