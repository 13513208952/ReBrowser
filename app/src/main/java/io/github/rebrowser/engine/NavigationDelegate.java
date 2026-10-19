package io.github.rebrowser.engine;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.browser.SearchEngines;
import java.util.List;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebRequestError;

/**
 * Browser-domain native navigation boundary for ONE actual session. No loadUri/Intent,
 * search fallback, certificate exception or fabricated document/principal authority.
 * Initial conservative web/blank policy is NOT full data/blob/reader/site compatibility.
 * Popup is deliberately denied until a real current-document-bound UNOPENED factory is
 * integrated; regular GeckoSessionOwner.open is not a valid onNewSession factory.
 */
public final class NavigationDelegate implements GeckoSession.NavigationDelegate {
    public enum Decision { ALLOW_WEB, ALLOW_BLANK, DENY_MALFORMED, DENY_UNSUPPORTED_SCHEME, DENY_NEW_WINDOW }
    public enum Event { LOCATION, BACK_CAPABILITY, FORWARD_CAPABILITY, LOAD_ERROR, BLOCKED_LOAD, BLOCKED_NEW_WINDOW }
    public static final class Observation {
        private final long sequence;
        private final Event event;
        private final String url;
        private final Boolean value;
        private final Decision decision;
        private Observation(long sequence,Event event,String url,Boolean value,Decision decision){this.sequence=sequence;this.event=event;this.url=url;this.value=value;this.decision=decision;}
        public long observationSequence(){return sequence;}
        public Event event(){return event;}
        /** Display-only native UI data, may contain sensitive site state; no ordinary IPC/log export. */
        public String reportedLocation(){return url;}
        /** SDK-reported gesture for LOCATION, history capability for BACK/FORWARD; not owner consent. */
        public Boolean reportedFlag(){return value;}
        public Decision blockedDecision(){return decision;}
    }
    public interface NativeObserver {
        /** Weak/native window/service target only; never strongly capture Activity in process-owned delegate. */
        void observed(Observation event);
    }
    private final GeckoSession bound;
    private final ComponentGate gate;
    private final PartitionCatalog partitions;
    private final PartitionCatalog.Lease partition;
    private final NativeObserver observer;
    private long sequence;
    private boolean invalidated;
    public NavigationDelegate(GeckoSession actualSession,ComponentGate gate,PartitionCatalog partitions,PartitionCatalog.Lease partition,NativeObserver observer){
        GeckoSessionOwner.mainThread();
        if(actualSession==null || gate==null || partitions==null || partition==null || observer==null || partition.domain()!=PartitionCatalog.Domain.BROWSER)throw new IllegalArgumentException("Native browser navigation binding required");
        partitions.requireGate(gate);bound=actualSession;this.gate=gate;this.partitions=partitions;this.partition=partition;this.observer=observer;
    }
    @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession actual,GeckoSession.NavigationDelegate.LoadRequest request){return loadDecision(actual,request);}
    @Override public GeckoResult<AllowOrDeny> onSubframeLoadRequest(GeckoSession actual,GeckoSession.NavigationDelegate.LoadRequest request){return loadDecision(actual,request);}
    private GeckoResult<AllowOrDeny> loadDecision(GeckoSession actual,GeckoSession.NavigationDelegate.LoadRequest request){
        if(!eligible(actual))return GeckoResult.fromValue(AllowOrDeny.DENY); // null means ALLOW in pinned SDK
        Decision decision=request==null?Decision.DENY_MALFORMED:classifyBrowserLoad(request.uri,request.target);
        if(decision!=Decision.ALLOW_WEB && decision!=Decision.ALLOW_BLANK){emit(Event.BLOCKED_LOAD,null,null,decision);return GeckoResult.fromValue(AllowOrDeny.DENY);}
        // Observer is never an authorization callback; repeat native source/partition check before allowing.
        return GeckoResult.fromValue(eligible(actual)?AllowOrDeny.ALLOW:AllowOrDeny.DENY);
    }
    @Override public void onLocationChange(GeckoSession actual,String url,List<GeckoSession.PermissionDelegate.ContentPermission> permissions,Boolean hasUserGesture){
        if(!eligible(actual))return;
        // Do not retain/iterate native permissions here or convert them into grants/document leases.
        emit(Event.LOCATION,displayLocation(url),Boolean.TRUE.equals(hasUserGesture),null);
    }
    @Override public void onCanGoBack(GeckoSession actual,boolean canGoBack){if(eligible(actual))emit(Event.BACK_CAPABILITY,null,canGoBack,null);}
    @Override public void onCanGoForward(GeckoSession actual,boolean canGoForward){if(eligible(actual))emit(Event.FORWARD_CAPABILITY,null,canGoForward,null);}
    @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession actual,String informationalUri){
        if(eligible(actual))emit(Event.BLOCKED_NEW_WINDOW,null,null,Decision.DENY_NEW_WINDOW);
        return null; // Native parent/document/popup ownership not implemented: no new session or blind load.
    }
    @Override public GeckoResult<String> onLoadError(GeckoSession actual,String failedUri,WebRequestError error){
        if(eligible(actual))emit(Event.LOAD_ERROR,null,null,null);
        return null; // halt; never embed untrusted URI into HTML or invoke certificate/HTTPS bypass methods
    }
    public void invalidate(){GeckoSessionOwner.mainThread();invalidated=true;}
    private boolean eligible(GeckoSession actual){
        GeckoSessionOwner.mainThread();if(invalidated || actual!=bound || !bound.isOpen())return false;
        try{ComponentGate.BusinessPermit permit=gate.requireBusiness();partitions.requireActive(partition,permit);return true;}
        catch(SecurityException|IllegalArgumentException stale){return false;}
    }
    private void emit(Event event,String url,Boolean value,Decision decision){
        if(sequence==Long.MAX_VALUE){invalidated=true;return;}
        Observation observation=new Observation(++sequence,event,url,value,decision);
        try{observer.observed(observation);}catch(RuntimeException ignoredObserverFailure){/* no alternate load/IPC/log/retry */}
    }
    /** Structural classification ONLY. Original URI stays with Gecko; no rewrite or second load. */
    public static Decision classifyBrowserLoad(String uri,int target){
        if(target!=TARGET_WINDOW_NONE && target!=TARGET_WINDOW_CURRENT && target!=TARGET_WINDOW_NEW)return Decision.DENY_MALFORMED;
        if(!validText(uri,SearchEngines.MAX_URL_CHARS) || uri.isEmpty())return Decision.DENY_MALFORMED;
        Decision decision;
        if("about:blank".equalsIgnoreCase(uri))decision=Decision.ALLOW_BLANK;
        else{
            int colon=uri.indexOf(':');if(colon<1)return Decision.DENY_MALFORMED;
            String scheme=uri.substring(0,colon);
            if(!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))return Decision.DENY_UNSUPPORTED_SCHEME;
            try{SearchEngines.requireWebUrl(uri);decision=Decision.ALLOW_WEB;}catch(IllegalArgumentException malformed){return Decision.DENY_MALFORMED;}
        }
        return target==TARGET_WINDOW_NEW?Decision.DENY_NEW_WINDOW:decision;
    }
    static String displayLocation(String uri){return validText(uri,8192)?uri:null;}
    private static boolean validText(String value,int maximum){
        if(value==null || value.length()>maximum)return false;
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))return false;if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))return false;}else if(Character.isLowSurrogate(c))return false;}return true;
    }
}
