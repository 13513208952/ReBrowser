package io.github.rebrowser.continuity;

import static org.junit.Assert.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import org.junit.Test;

/** Actual controller/private ticket/bridge methods, PRIVATE reflected SYNTHETIC platform.
 * Never constructs AndroidPlatform/Activity or invokes OS authentication. Not system evidence. */
public final class OwnerAuthenticationControlTest {
    private static final String A=repeat('a'),B=repeat('b'),R=repeat('c'),S=repeat('d');
    private static String repeat(char c){char[] b=new char[64];Arrays.fill(b,c);return new String(b);}
    private static OwnerAuthentication.Binding binding(){return new OwnerAuthentication.Binding(new byte[16],B,S,ContinuityState.Confirmation.FORCE_2);}
    private static final class SyntheticPlatform implements OwnerAuthentication.Platform {
        final Thread owner=Thread.currentThread();long time=1000;boolean foreground=true,clockBroken,securityBroken,buildBroken,submitBroken,cancelBroken,cancelAllocationBroken;
        OwnerAuthentication.SecurityState security=OwnerAuthentication.SecurityState.SECURE;
        int builds,submits,cancels;SyntheticPrompt latest;boolean terminalDuringCancel;OwnerAuthentication.AuthKind successDuringSubmit;
        @Override public void requireOwnerThread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("synthetic owner thread");}
        @Override public long elapsedMs(){if(clockBroken)throw new IllegalStateException("synthetic clock unavailable");return time;}
        @Override public boolean foregroundReady(){return foreground;}
        @Override public OwnerAuthentication.SecurityState securityState(){if(securityBroken)throw new IllegalStateException("synthetic lock query error");return security;}
        @Override public OwnerAuthentication.NativeCancel newCancellation(){if(cancelAllocationBroken)throw new IllegalStateException("synthetic cancellation allocation");return ()->{cancels++;if(cancelBroken)throw new IllegalStateException("synthetic cancel unknown");if(terminalDuringCancel && latest!=null)latest.error(OwnerAuthentication.NativeError.CANCELLED);};}
        @Override public OwnerAuthentication.NativePrompt buildPrompt(ContinuityState.Confirmation step){builds++;if(buildBroken)throw new IllegalStateException("synthetic prompt unavailable");return latest=new SyntheticPrompt(this,step);}
    }
    private static final class SyntheticPrompt implements OwnerAuthentication.NativePrompt {
        final SyntheticPlatform platform;final ContinuityState.Confirmation step;OwnerAuthentication.NativeListener listener;
        SyntheticPrompt(SyntheticPlatform p,ContinuityState.Confirmation s){platform=p;step=s;}
        @Override public void authenticate(OwnerAuthentication.NativeCancel c,OwnerAuthentication.NativeListener l){platform.submits++;listener=l;if(platform.successDuringSubmit!=null)listener.success(platform.successDuringSubmit);if(platform.submitBroken)throw new IllegalStateException("synthetic ambiguous submission");}
        void success(OwnerAuthentication.AuthKind kind){listener.success(kind);}
        void error(OwnerAuthentication.NativeError error){listener.error(error);}
    }
    private static OwnerAuthentication syntheticOwner(SyntheticPlatform p)throws Exception{
        Constructor<OwnerAuthentication> ctor=OwnerAuthentication.class.getDeclaredConstructor(OwnerAuthentication.Platform.class);assertTrue(Modifier.isPrivate(ctor.getModifiers()));ctor.setAccessible(true);return ctor.newInstance(p);
    }
    private static final class Fixture implements AutoCloseable {
        final SyntheticPlatform p=new SyntheticPlatform();final OwnerAuthentication auth;final Path dir;final LastGoodSourceStore store;final ContinuityCoordinator.Context context;final Object window;
        boolean current=true;
        Fixture()throws Exception{
            auth=syntheticOwner(p);Field field=OwnerAuthentication.class.getDeclaredField("nativeWindow");field.setAccessible(true);window=field.get(auth);
            Path work=Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath();assertFalse(work.startsWith(Paths.get("/tmp")));dir=Files.createTempDirectory(work,"owner-control-host-");store=new LastGoodSourceStore(dir);byte[] epoch=new byte[16];epoch[0]=1;LastGoodSourceStore.View v=store.seedVerifiedFresh(store.inspect(),new LastGoodSourceStore.SourceEpoch(A,R,epoch),(a,b)->{});context=ContinuityCoordinator.Context.fromNativeMetadata(v,B,S);
        }
        ContinuityCoordinator flow(){return new ContinuityCoordinator(context,window,c->{if(!current)throw new SecurityException("synthetic source revoked");},p::elapsedMs);}
        @Override public void close()throws Exception{p.cancelBroken=false;auth.close();List<Path> paths=new ArrayList<>();try(Stream<Path> s=Files.walk(dir)){s.forEach(paths::add);}paths.sort(Comparator.reverseOrder());for(Path path:paths)Files.delete(path);}
    }
    private static void ordinary(ContinuityCoordinator f){ContinuityCoordinator.View v=f.view();f.confirmOrdinary(v.revision(),v.step());}
    private static ContinuityCoordinator.OwnerAttempt nextOwner(ContinuityCoordinator f){ContinuityCoordinator.View v=f.view();return f.beginOwnerConfirmation(v.revision(),v.step());}
    private static ContinuityCoordinator.OwnerAttempt force2(ContinuityCoordinator f){ordinary(f);ordinary(f);return nextOwner(f);}
    @Test public void foregroundWaitDoesNotStartOrConsumeBinding()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();OwnerAuthentication auth=syntheticOwner(p);OwnerAuthentication.Binding b=binding();AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();p.foreground=false;assertEquals(OwnerAuthentication.Start.WAIT_FOREGROUND,auth.begin(b,result::set));assertNull(result.get());assertEquals(0,p.builds);assertFalse(auth.hasPendingOsRequest());p.foreground=true;assertEquals(OwnerAuthentication.Start.PROMPT_SUBMITTED,auth.begin(b,result::set));p.latest.error(OwnerAuthentication.NativeError.CANCELLED);
    }
    @Test public void positiveNoLockMintsOneUseTicketWithoutBuildingPrompt()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();p.security=OwnerAuthentication.SecurityState.NO_LOCK;OwnerAuthentication auth=syntheticOwner(p);OwnerAuthentication.Binding b=binding();AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();assertEquals(OwnerAuthentication.Start.TERMINAL_DELIVERED,auth.begin(b,result::set));assertEquals(OwnerAuthentication.Outcome.NO_LOCK_CONFIGURED,result.get().outcome());assertEquals(0,p.builds);assertFalse(auth.hasPendingOsRequest());assertEquals(OwnerAuthentication.Evidence.OS_CONFIRMED_NO_LOCK,result.get().ticket().consume(b));assertThrows(SecurityException.class,()->result.get().ticket().consume(b));assertThrows(IllegalArgumentException.class,()->auth.begin(b,result::set));
    }
    @Test public void unknownNullAndErrorSecurityNeverBecomeNoLockExemption()throws Exception{
        for(int mode=0;mode<3;mode++){
            SyntheticPlatform p=new SyntheticPlatform();p.security=mode==0?OwnerAuthentication.SecurityState.UNKNOWN:null;p.securityBroken=mode==2;OwnerAuthentication auth=syntheticOwner(p);AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();auth.begin(binding(),result::set);assertEquals(OwnerAuthentication.Outcome.UNAVAILABLE,result.get().outcome());assertFalse(result.get().hasTicket());assertEquals(0,p.submits);assertFalse(auth.hasPendingOsRequest());
        }
    }
    @Test public void promptConstructionFailureIsNotAuthenticationOrNoLock()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();p.buildBroken=true;OwnerAuthentication auth=syntheticOwner(p);AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();assertEquals(OwnerAuthentication.Start.TERMINAL_DELIVERED,auth.begin(binding(),result::set));assertEquals(OwnerAuthentication.Outcome.UNAVAILABLE,result.get().outcome());assertFalse(result.get().hasTicket());assertEquals(0,p.submits);assertFalse(auth.hasPendingOsRequest());
    }
    @Test public void cancellationAllocationFailureDoesNotStrandStartingOrStartBinding()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();p.cancelAllocationBroken=true;OwnerAuthentication auth=syntheticOwner(p);OwnerAuthentication.Binding b=binding();assertThrows(IllegalStateException.class,()->auth.begin(b,r->{}));assertFalse(auth.hasPendingOsRequest());p.cancelAllocationBroken=false;assertEquals(OwnerAuthentication.Start.PROMPT_SUBMITTED,auth.begin(b,r->{}));p.latest.error(OwnerAuthentication.NativeError.CANCELLED);
    }
    @Test public void securePromptAndPrivateTicketMustWaitOwnForeground()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();OwnerAuthentication auth=syntheticOwner(p);OwnerAuthentication.Binding b=binding();AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();assertEquals(OwnerAuthentication.Start.PROMPT_SUBMITTED,auth.begin(b,result::set));assertTrue(auth.hasPendingOsRequest());assertNull(result.get());p.latest.success(OwnerAuthentication.AuthKind.DEVICE_CREDENTIAL);assertEquals(OwnerAuthentication.Outcome.VERIFIED,result.get().outcome());assertFalse(auth.hasPendingOsRequest());assertEquals(OwnerAuthentication.Evidence.SYSTEM_AUTHENTICATED,result.get().ticket().consume(b));
    }
    @Test public void unknownOrNullAuthenticationKindFailsWithoutTicket()throws Exception{
        for(OwnerAuthentication.AuthKind kind:new OwnerAuthentication.AuthKind[]{OwnerAuthentication.AuthKind.UNKNOWN,null}){SyntheticPlatform p=new SyntheticPlatform();OwnerAuthentication auth=syntheticOwner(p);AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();auth.begin(binding(),result::set);p.latest.success(kind);assertEquals(OwnerAuthentication.Outcome.FAILED,result.get().outcome());assertFalse(result.get().hasTicket());assertFalse(auth.hasPendingOsRequest());}
    }
    @Test public void changedOrUnknownNoLockStateAtConsumptionRejectsAndSpendsTicket()throws Exception{
        for(OwnerAuthentication.SecurityState changed:new OwnerAuthentication.SecurityState[]{OwnerAuthentication.SecurityState.SECURE,OwnerAuthentication.SecurityState.UNKNOWN}){SyntheticPlatform p=new SyntheticPlatform();p.security=OwnerAuthentication.SecurityState.NO_LOCK;OwnerAuthentication auth=syntheticOwner(p);OwnerAuthentication.Binding b=binding();AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();auth.begin(b,result::set);p.security=changed;assertThrows(SecurityException.class,()->result.get().ticket().consume(b));p.security=OwnerAuthentication.SecurityState.NO_LOCK;assertThrows(SecurityException.class,()->result.get().ticket().consume(b));}
    }
    @Test public void wrongBindingCannotRecoverSpentTicketWithCorrectBinding()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();p.security=OwnerAuthentication.SecurityState.NO_LOCK;OwnerAuthentication auth=syntheticOwner(p);OwnerAuthentication.Binding original=binding();AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();auth.begin(original,result::set);assertThrows(SecurityException.class,()->result.get().ticket().consume(binding()));assertThrows(SecurityException.class,()->result.get().ticket().consume(original));
    }
    @Test public void expiryAndRollbackRejectNativeTickets()throws Exception{
        for(long delta:new long[]{30_001,-1}){SyntheticPlatform p=new SyntheticPlatform();p.security=OwnerAuthentication.SecurityState.NO_LOCK;OwnerAuthentication auth=syntheticOwner(p);OwnerAuthentication.Binding b=binding();AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();auth.begin(b,result::set);p.time+=delta;assertThrows(SecurityException.class,()->result.get().ticket().consume(b));}
    }
    @Test public void cancelAndCloseDoNotSettleActiveRequestBeforeTerminalCallback()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();OwnerAuthentication auth=syntheticOwner(p);AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();auth.begin(binding(),result::set);auth.close();assertTrue(auth.hasPendingOsRequest());assertEquals(1,p.cancels);assertNull(result.get());assertThrows(IllegalStateException.class,()->auth.begin(binding(),r->{}));p.latest.success(OwnerAuthentication.AuthKind.BIOMETRIC);assertEquals(OwnerAuthentication.Outcome.CANCELLED,result.get().outcome());assertFalse(result.get().hasTicket());assertFalse(auth.hasPendingOsRequest());
    }
    @Test public void unknownSubmissionKeepsOriginalNativeOwnerUntilActualError()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();p.submitBroken=true;OwnerAuthentication auth=syntheticOwner(p);AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();assertEquals(OwnerAuthentication.Start.SUBMISSION_UNKNOWN,auth.begin(binding(),result::set));assertEquals(OwnerAuthentication.Outcome.SUBMISSION_UNKNOWN,result.get().outcome());assertFalse(result.get().hasTicket());assertTrue(auth.hasPendingOsRequest());assertThrows(IllegalStateException.class,()->auth.begin(binding(),r->{}));p.latest.error(OwnerAuthentication.NativeError.CANCELLED);assertFalse(auth.hasPendingOsRequest());assertEquals(OwnerAuthentication.Outcome.CANCELLED,result.get().outcome());
    }
    @Test public void synchronousSuccessThenSubmissionThrowKeepsFirstActualTerminalOutcome()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();p.successDuringSubmit=OwnerAuthentication.AuthKind.BIOMETRIC;p.submitBroken=true;OwnerAuthentication auth=syntheticOwner(p);OwnerAuthentication.Binding b=binding();AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();assertEquals(OwnerAuthentication.Start.TERMINAL_DELIVERED,auth.begin(b,result::set));assertEquals(OwnerAuthentication.Outcome.VERIFIED,result.get().outcome());assertEquals(OwnerAuthentication.Evidence.SYSTEM_AUTHENTICATED,result.get().ticket().consume(b));assertFalse(auth.hasPendingOsRequest());assertEquals(0,p.cancels);
    }
    @Test public void terminalCallbackDuringCancellationSettlesWithoutSpuriousUnknownOutcome()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();p.submitBroken=true;p.terminalDuringCancel=true;OwnerAuthentication auth=syntheticOwner(p);AtomicInteger count=new AtomicInteger();AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();assertEquals(OwnerAuthentication.Start.TERMINAL_DELIVERED,auth.begin(binding(),r->{count.incrementAndGet();result.set(r);}));assertEquals(1,count.get());assertEquals(OwnerAuthentication.Outcome.CANCELLED,result.get().outcome());assertFalse(auth.hasPendingOsRequest());assertFalse(result.get().hasTicket());
    }
    @Test public void cancelFailureAlsoCannotReleaseUnknownRequest()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();p.submitBroken=true;p.cancelBroken=true;OwnerAuthentication auth=syntheticOwner(p);AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();assertEquals(OwnerAuthentication.Start.SUBMISSION_UNKNOWN,auth.begin(binding(),result::set));assertTrue(auth.hasPendingOsRequest());assertNull(result.get());p.cancelBroken=false;p.latest.error(OwnerAuthentication.NativeError.CANCELLED);assertFalse(auth.hasPendingOsRequest());
    }
    @Test public void actualTerminalSuccessWithUnavailableClockFailsButSettlesOwner()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();OwnerAuthentication auth=syntheticOwner(p);AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();auth.begin(binding(),result::set);p.clockBroken=true;p.latest.success(OwnerAuthentication.AuthKind.DEVICE_CREDENTIAL);assertEquals(OwnerAuthentication.Outcome.FAILED,result.get().outcome());assertFalse(result.get().hasTicket());assertFalse(auth.hasPendingOsRequest());
    }
    @Test public void lateDuplicateCallbacksCannotMintSecondOutcome()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();OwnerAuthentication auth=syntheticOwner(p);AtomicInteger count=new AtomicInteger();AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();auth.begin(binding(),r->{count.incrementAndGet();result.set(r);});p.latest.error(OwnerAuthentication.NativeError.FAILED);p.latest.success(OwnerAuthentication.AuthKind.BIOMETRIC);p.latest.error(OwnerAuthentication.NativeError.CANCELLED);assertEquals(1,count.get());assertEquals(OwnerAuthentication.Outcome.FAILED,result.get().outcome());
    }
    @Test public void observerExceptionAfterActualEndDoesNotLeaveFalseOsWorkPending()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();OwnerAuthentication auth=syntheticOwner(p);auth.begin(binding(),r->{throw new IllegalStateException("synthetic UI observer failure");});assertThrows(IllegalStateException.class,()->p.latest.success(OwnerAuthentication.AuthKind.BIOMETRIC));assertFalse(auth.hasPendingOsRequest());
    }
    @Test public void wrongThreadCannotSettleOrConsumeNativeTicket()throws Exception{
        SyntheticPlatform p=new SyntheticPlatform();OwnerAuthentication auth=syntheticOwner(p);OwnerAuthentication.Binding b=binding();AtomicReference<OwnerAuthentication.Result> result=new AtomicReference<>();auth.begin(b,result::set);AtomicReference<Throwable> error=new AtomicReference<>();Thread wrong=new Thread(()->{try{p.latest.success(OwnerAuthentication.AuthKind.BIOMETRIC);}catch(Throwable t){error.set(t);}});wrong.start();wrong.join(2000);assertTrue(error.get() instanceof IllegalStateException);assertTrue(auth.hasPendingOsRequest());p.latest.success(OwnerAuthentication.AuthKind.BIOMETRIC);error.set(null);Thread other=new Thread(()->{try{result.get().ticket().consume(b);}catch(Throwable t){error.set(t);}});other.start();other.join(2000);assertTrue(error.get() instanceof IllegalStateException);assertEquals(OwnerAuthentication.Evidence.SYSTEM_AUTHENTICATED,result.get().ticket().consume(b));
    }
    @Test public void twoRealControllerBridgeAttemptsNeedSeparatePrivateTicketConsumption()throws Exception{
        try(Fixture f=new Fixture()){
            ContinuityCoordinator flow=f.flow();ContinuityCoordinator.OwnerAttempt second=force2(flow);OwnerAuthentication.ContinuityListener listener=()->{};f.auth.beginContinuityVerification(second,listener);assertEquals(ContinuityState.Confirmation.FORCE_2,f.p.latest.step);f.p.foreground=false;f.p.latest.success(OwnerAuthentication.AuthKind.DEVICE_CREDENTIAL);assertFalse(f.auth.tryConsumeContinuityVerification());assertEquals(ContinuityCoordinator.Stage.WAITING_TICKET,flow.view().stage());f.p.foreground=true;assertTrue(f.auth.tryConsumeContinuityVerification());assertEquals(ContinuityState.Confirmation.FORCE_3,flow.view().step());assertFalse(f.auth.hasPendingContinuityVerification());
            ContinuityCoordinator.OwnerAttempt third=nextOwner(flow);assertNotSame(second,third);f.auth.beginContinuityVerification(third,listener);assertEquals(ContinuityState.Confirmation.FORCE_3,f.p.latest.step);f.p.latest.success(OwnerAuthentication.AuthKind.BIOMETRIC);assertTrue(f.auth.tryConsumeContinuityVerification());assertNotNull(flow.availableConsent());assertEquals(2,f.p.builds);assertEquals(1,f.store.inspect().record().revision());
        }
    }
    @Test public void nativeBridgeRejectsForeignWindowBeforePrompt()throws Exception{
        try(Fixture f=new Fixture()){
            ContinuityCoordinator foreign=new ContinuityCoordinator(f.context,new Object(),c->{},f.p::elapsedMs);ContinuityCoordinator.OwnerAttempt a=force2(foreign);assertThrows(IllegalArgumentException.class,()->f.auth.beginContinuityVerification(a,()->{}));assertEquals(0,f.p.builds);assertFalse(f.auth.hasPendingContinuityVerification());
        }
    }
    @Test public void nativeBridgeWaitForegroundRevocationClearsOnlyKnownUnstartedSlot()throws Exception{
        try(Fixture f=new Fixture()){
            ContinuityCoordinator flow=f.flow();ContinuityCoordinator.OwnerAttempt a=force2(flow);OwnerAuthentication.ContinuityListener listener=()->{};f.p.foreground=false;assertEquals(OwnerAuthentication.Start.WAIT_FOREGROUND,f.auth.beginContinuityVerification(a,listener));assertTrue(f.auth.hasPendingContinuityVerification());assertFalse(flow.view().hasPendingNativeOwnerWork());f.current=false;f.p.foreground=true;assertThrows(SecurityException.class,()->f.auth.beginContinuityVerification(a,listener));assertFalse(f.auth.hasPendingContinuityVerification());assertFalse(f.auth.hasPendingOsRequest());assertEquals(0,f.p.builds);
        }
    }
    @Test public void nativeBridgeUnknownCancellationRetainsActualOldWorkAfterRetirement()throws Exception{
        try(Fixture f=new Fixture()){
            ContinuityCoordinator flow=f.flow();ContinuityCoordinator.OwnerAttempt a=force2(flow);f.p.submitBroken=true;assertEquals(OwnerAuthentication.Start.SUBMISSION_UNKNOWN,f.auth.beginContinuityVerification(a,()->{}));f.auth.cancel();assertEquals(ContinuityCoordinator.Stage.RETIRED,flow.view().stage());assertTrue(flow.view().hasPendingNativeOwnerWork());assertTrue(f.auth.hasPendingContinuityVerification());f.p.latest.error(OwnerAuthentication.NativeError.CANCELLED);assertFalse(f.auth.hasPendingOsRequest());assertFalse(f.auth.hasPendingContinuityVerification());assertFalse(flow.view().hasPendingNativeOwnerWork());assertThrows(IllegalStateException.class,flow::availableConsent);
        }
    }
    @Test public void nativeBridgeCurrentSourceRevokedBeforeConsumeCannotAdvanceForce2()throws Exception{
        try(Fixture f=new Fixture()){
            ContinuityCoordinator flow=f.flow();f.auth.beginContinuityVerification(force2(flow),()->{});f.p.latest.success(OwnerAuthentication.AuthKind.DEVICE_CREDENTIAL);f.current=false;assertThrows(SecurityException.class,f.auth::tryConsumeContinuityVerification);assertEquals(ContinuityCoordinator.Stage.RETIRED,flow.view().stage());assertFalse(f.auth.hasPendingContinuityVerification());assertEquals(1,f.store.inspect().record().revision());
        }
    }
    @Test public void nativeBridgeNoLockRecheckAndRetiredTicketNeverYieldConsent()throws Exception{
        try(Fixture f=new Fixture()){
            f.p.security=OwnerAuthentication.SecurityState.NO_LOCK;ContinuityCoordinator flow=f.flow();f.auth.beginContinuityVerification(force2(flow),()->{});f.p.security=OwnerAuthentication.SecurityState.SECURE;assertThrows(SecurityException.class,f.auth::tryConsumeContinuityVerification);assertEquals(ContinuityCoordinator.Stage.RETIRED,flow.view().stage());
            f.p.security=OwnerAuthentication.SecurityState.NO_LOCK;ContinuityCoordinator other=f.flow();f.auth.beginContinuityVerification(force2(other),()->{});other.retire();f.p.foreground=false;assertFalse(f.auth.tryConsumeContinuityVerification());assertFalse(f.auth.hasPendingContinuityVerification());
        }
    }
    @Test public void bridgeObserverExceptionRetainsTicketNotAlternatePrompt()throws Exception{
        try(Fixture f=new Fixture()){
            ContinuityCoordinator flow=f.flow();OwnerAuthentication.ContinuityListener listener=()->{throw new IllegalStateException("synthetic observer");};f.auth.beginContinuityVerification(force2(flow),listener);assertThrows(IllegalStateException.class,()->f.p.latest.success(OwnerAuthentication.AuthKind.BIOMETRIC));assertEquals(ContinuityCoordinator.Stage.WAITING_TICKET,flow.view().stage());assertTrue(f.auth.hasPendingContinuityVerification());assertThrows(IllegalStateException.class,f.auth::tryConsumeContinuityVerification);assertEquals(ContinuityState.Confirmation.FORCE_3,flow.view().step());assertFalse(f.auth.hasPendingContinuityVerification());assertEquals(1,f.p.builds);
        }
    }
    @Test public void productionRootUnconfiguredFactoryCannotStartSyntheticOrRealSourceFlow()throws Exception{
        try(Fixture f=new Fixture()){
            assertFalse(CompiledSourceIdentity.current().configured());assertThrows(SecurityException.class,()->f.auth.newContinuityFlow(f.context,c->{}));assertEquals(0,f.p.builds);assertFalse(f.auth.hasPendingOsRequest());
        }
    }
    @Test public void noPublicPlatformInjectionConstructorExists(){
        Constructor<?>[] publicConstructors=OwnerAuthentication.class.getConstructors();assertEquals(1,publicConstructors.length);assertArrayEquals(new Class<?>[]{android.app.Activity.class},publicConstructors[0].getParameterTypes());
    }
}
