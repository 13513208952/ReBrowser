package io.github.rebrowser.permissions;

import io.github.rebrowser.bootstrap.ComponentGate;
import java.util.*;
import java.util.function.LongSupplier;

/** Native exact SDK-request/document prompt + expiring decision-HINT orchestration. Current
 * pinned Gecko finite grant/actual sensor stop pipeline is NOT qualified: even ALLOW_ONCE_INTENT
 * finishes with a real DENY, explicitly reported as unavailable rather than silently persisting
 * EXPIRE_NEVER. No native OS prompt/permission, sensor, location or storage access is launched.
 * Native SDK adapter must supply actual captured request/session/document/generation + guard,
 * never page origin/metadata as authority. This is not yet installed into the deny-only delegate. */
public final class PermissionCoordinator {
    public interface NativeRequestGuard {void requireExactCurrentRequest(Object session,Object document,long generation);}
    public enum State { AWAITING_NATIVE_CHOICE, DENIAL_CALLBACK_RETURNED, DENIAL_CALLBACK_UNKNOWN }
    public enum ChoiceOutcome { DENIED, ALLOW_INTENT_RECORDED_FINITE_NATIVE_GRANT_UNAVAILABLE }
    public static final class Request {
        private final PermissionCoordinator issuer;private final Object session,document;private final long generation,expires;private final SiteGrantStore.NativeOrigin origin;private final SiteGrantStore.Capability capability;private final NativeRequestGuard guard;private PendingPromptRegistry.Ticket ticket;private State state=State.AWAITING_NATIVE_CHOICE;private boolean choiceAttempted;
        private Request(PermissionCoordinator issuer,Object session,Object document,long generation,long expires,SiteGrantStore.NativeOrigin origin,SiteGrantStore.Capability capability,NativeRequestGuard guard){this.issuer=issuer;this.session=session;this.document=document;this.generation=generation;this.expires=expires;this.origin=origin;this.capability=capability;this.guard=guard;}
        public String candidateOriginForNativeDisplay(){issuer.currentRequest(this);return origin.displayOrigin();}
        public State state(){issuer.owner();issuer.own(this);return state;}
    }
    private final ComponentGate.DataEpochPin source;private final LongSupplier clock;private final SiteGrantStore hints;private final PendingPromptRegistry prompts;private final Thread thread=Thread.currentThread();private final Set<Request> requests=Collections.newSetFromMap(new IdentityHashMap<Request,Boolean>());private long last=-1;private boolean retired,deciding;
    public PermissionCoordinator(ComponentGate.DataEpochPin actualNativeSource,LongSupplier nativeElapsedRealtime){if(actualNativeSource==null || nativeElapsedRealtime==null)throw new IllegalArgumentException("Native permission source/clock required");source=actualNativeSource;clock=nativeElapsedRealtime;source.requireCurrent();hints=new SiteGrantStore(source,clock);prompts=new PendingPromptRegistry(source,64);}
    public Request offerActualSdkRequest(Object actualSdkRequest,Object nativeSession,Object nativeDocument,long nativeGeneration,Object exactNativePartition,String actualNativeRequestUri,SiteGrantStore.Capability capability,NativeRequestGuard nativeGuard,PendingPromptRegistry.Completion actualSdkCompletion){
        long now=now();if(retired || deciding || requests.size()>=64 || nativeSession==null || nativeDocument==null || nativeGeneration<0 || capability==null || nativeGuard==null || actualSdkCompletion==null || now>Long.MAX_VALUE-30_000)throw denied();nativeGuard.requireExactCurrentRequest(nativeSession,nativeDocument,nativeGeneration);SiteGrantStore.NativeOrigin origin=SiteGrantStore.fromNativeRequest(exactNativePartition,actualNativeRequestUri);Request request=new Request(this,nativeSession,nativeDocument,nativeGeneration,now+30_000,origin,capability,nativeGuard);request.ticket=prompts.register(actualSdkRequest,nativeSession,nativeDocument,nativeGeneration,actualSdkCompletion);requests.add(request);return request;
    }
    /** Native UI must hold the original Request, never reacquire a fresh latest-document token. */
    public ChoiceOutcome chooseFromNativeWindow(Request request,SiteGrantStore.Choice choice){currentRequest(request);if(deciding || choice==null || request.choiceAttempted)throw denied();request.choiceAttempted=true;deciding=true;try{hints.rememberNativeChoice(request.origin,request.capability,choice,choice==SiteGrantStore.Choice.ALLOW_ONCE_INTENT?30_000:300_000);answerDenied(request);return choice==SiteGrantStore.Choice.ALLOW_ONCE_INTENT?ChoiceOutcome.ALLOW_INTENT_RECORDED_FINITE_NATIVE_GRANT_UNAVAILABLE:ChoiceOutcome.DENIED;}finally{deciding=false;}}
    public SiteGrantStore.Decision currentDecisionHint(Request exactRequest){currentRequest(exactRequest);return hints.lookupHint(exactRequest.origin,exactRequest.capability);}
    /** Expiry suppresses answers and requests actual SDK denial; not proof of active sensor stop. */
    public void expireOwnedPrompts(){long current=now();denyMatching(r->current>=r.expires);}
    public void retireNativeDocument(Object exactSession,Object exactDocument){owner();prompts.retireDocument(exactSession,exactDocument);denyMatching(r->r.session==exactSession && r.document==exactDocument);}
    public void retireAll(){owner();retired=true;hints.close();denyMatching(r->true);}
    private void denyMatching(java.util.function.Predicate<Request> selection){RuntimeException first=null;for(Request r:new ArrayList<>(requests))if(r.state==State.AWAITING_NATIVE_CHOICE && selection.test(r)){r.choiceAttempted=true;try{answerDenied(r);}catch(RuntimeException unknown){if(first==null)first=unknown;else first.addSuppressed(unknown);}}if(first!=null)throw first;}
    private void answerDenied(Request r){owner();own(r);if(r.state!=State.AWAITING_NATIVE_CHOICE)return;r.state=State.DENIAL_CALLBACK_UNKNOWN;try{prompts.answer(r.ticket,r.session,r.document,r.generation,false);r.state=State.DENIAL_CALLBACK_RETURNED;}catch(RuntimeException unknown){r.state=State.DENIAL_CALLBACK_UNKNOWN;throw unknown;}}
    private void currentRequest(Request r){long current=now();own(r);if(retired || deciding || r.state!=State.AWAITING_NATIVE_CHOICE || r.choiceAttempted || current>=r.expires)throw denied();r.guard.requireExactCurrentRequest(r.session,r.document,r.generation);source.requireCurrent();}
    private void own(Request r){if(r==null || r.issuer!=this || !requests.contains(r))throw denied();}
    private long now(){owner();source.requireCurrent();long n=clock.getAsLong();if(n<0 || n<last){retired=true;throw denied();}last=n;return n;}
    private void owner(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Native permission UI owner required");}
    private static SecurityException denied(){return new SecurityException("Native permission request unavailable/retired");}
}
