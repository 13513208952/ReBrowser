package io.github.rebrowser.entry;

import static org.junit.Assert.*;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.ProcessRole;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/** Pure startup routing/permit tests only; no Android process or source-continuity success inferred. */
public final class EntryContractsTest {
    @Test public void onlyExactPackageProcessIsPrimary(){
        assertEquals(ProcessRole.MAIN,ProcessRole.classify("io.github.rebrowser","io.github.rebrowser"));
        assertEquals(ProcessRole.UNKNOWN,ProcessRole.classify("io.github.rebrowser",null));
        assertEquals(ProcessRole.AUXILIARY,ProcessRole.classify("io.github.rebrowser","io.github.rebrowser2"));
        assertEquals(ProcessRole.AUXILIARY,ProcessRole.classify("io.github.rebrowser","io.github.rebrowser:other"));
    }
    @Test public void sdkChildrenAreNotDuplicateApplicationOwners(){
        for(String suffix:new String[]{"media","gpu_disable_art_image_","socket_disable_art_image_","tab_disable_art_image_0","isolatedTab_disable_art_image_49"}){
            assertEquals(ProcessRole.GECKO_CHILD,ProcessRole.classify("io.github.rebrowser","io.github.rebrowser:"+suffix));
            assertFalse(new ComponentGate(ProcessRole.GECKO_CHILD,()->ComponentGate.SourceState.TRUSTED).mayShowNativeShell());
        }
    }
    @Test public void nativeShellDoesNotGrantBusinessAccess(){
        for(ComponentGate.SourceState state:ComponentGate.SourceState.values()){
            ComponentGate gate=new ComponentGate(ProcessRole.MAIN,()->state);assertTrue(gate.mayShowNativeShell());
            if(state!=ComponentGate.SourceState.TRUSTED){try{gate.requireBusiness();fail();}catch(SecurityException expected){}}
        }
    }
    @Test public void cachedPermitRechecksGateAndCannotCrossOwners(){
        AtomicReference<ComponentGate.SourceState> state=new AtomicReference<>(ComponentGate.SourceState.TRUSTED);
        ComponentGate gate=new ComponentGate(ProcessRole.MAIN,state::get);ComponentGate.BusinessPermit permit=gate.requireBusiness();gate.checkCurrent(permit);
        ComponentGate other=new ComponentGate(ProcessRole.MAIN,()->ComponentGate.SourceState.TRUSTED);
        try{other.checkCurrent(permit);fail();}catch(SecurityException expected){}
        state.set(ComponentGate.SourceState.LOCKED);try{gate.checkCurrent(permit);fail();}catch(SecurityException expected){}
    }
    @Test public void ownApplicationRoutingDoesNotTreatCallerPackageAsAuthority(){
        assertEquals(ProcessRole.UNKNOWN,ProcessRole.classifyOwnApplication("other.app","other.app"));assertEquals(ProcessRole.MAIN,ProcessRole.classifyOwnApplication("io.github.rebrowser.development","io.github.rebrowser.development"));
        assertEquals(ProcessRole.StartupRoute.MAIN_NATIVE_OWNER,ProcessRole.MAIN.startupRoute());assertEquals(ProcessRole.StartupRoute.SDK_CHILD_NO_APP_SERVICES,ProcessRole.GECKO_CHILD.startupRoute());assertEquals(ProcessRole.StartupRoute.NONPRIMARY_NO_APP_SERVICES,ProcessRole.AUXILIARY.startupRoute());assertEquals(ProcessRole.StartupRoute.UNAVAILABLE_FAIL_CLOSED,ProcessRole.UNKNOWN.startupRoute());
    }
    @Test public void malformedProcessNamesNeverCreatePrimaryOrSdkOwners(){
        for(String name:new String[]{"", "io.github.rebrowser:","io.github.rebrowser::media","io.github.rebrowser\n","io.github.rebrowser:\u0000",new String(new char[513]).replace('\0','x')})assertEquals(ProcessRole.UNKNOWN,ProcessRole.classify("io.github.rebrowser",name));
    }
    @Test public void observedLockPermanentlyRetiresCachedPermitEvenAfterTrustedAgain(){
        AtomicReference<ComponentGate.SourceState> state=new AtomicReference<>(ComponentGate.SourceState.TRUSTED);ComponentGate gate=new ComponentGate(ProcessRole.MAIN,state::get);ComponentGate.BusinessPermit old=gate.requireBusiness();state.set(ComponentGate.SourceState.LOCKED);assertEquals(ComponentGate.SourceState.LOCKED,gate.sourceState());state.set(ComponentGate.SourceState.TRUSTED);assertThrows(SecurityException.class,()->gate.checkCurrent(old));gate.checkCurrent(gate.requireBusiness());
    }
    @Test public void nativeIncarnationCutoverRevokesWithoutIntermediateLockedObservation(){
        Object a=new Object(),b=new Object();AtomicReference<ComponentGate.SourceView> source=new AtomicReference<>(ComponentGate.SourceView.acceptedNative(a));ComponentGate gate=ComponentGate.forNativeSource(ProcessRole.MAIN,source::get);ComponentGate.BusinessPermit old=gate.requireBusiness();source.set(ComponentGate.SourceView.acceptedNative(b));assertThrows(SecurityException.class,()->gate.checkCurrent(old));gate.checkCurrent(gate.requireBusiness());
    }
    @Test public void sameNativeIncarnationSnapshotDoesNotRevokeEveryRead(){
        Object a=new Object();ComponentGate gate=ComponentGate.forNativeSource(ProcessRole.MAIN,()->ComponentGate.SourceView.acceptedNative(a));ComponentGate.BusinessPermit first=gate.requireBusiness();gate.checkCurrent(first);gate.checkSameContext(first,gate.requireBusiness());
    }
    @Test public void observedNativeAbaCannotReviveOldPermitOrPinnedResource(){
        Object a=new Object(),b=new Object();AtomicReference<ComponentGate.SourceView> source=new AtomicReference<>(ComponentGate.SourceView.acceptedNative(a));ComponentGate gate=ComponentGate.forNativeSource(ProcessRole.MAIN,source::get);ComponentGate.BusinessPermit old=gate.requireBusiness();ComponentGate.ResourcePin pin=gate.newResourcePin();pin.checkOrBind(old);source.set(ComponentGate.SourceView.acceptedNative(b));gate.sourceState();source.set(ComponentGate.SourceView.acceptedNative(a));ComponentGate.BusinessPermit current=gate.requireBusiness();assertThrows(SecurityException.class,()->gate.checkCurrent(old));assertThrows(SecurityException.class,()->pin.checkOrBind(current));
    }
    @Test public void newValidPermitCannotRetargetOldResourcePin(){
        AtomicReference<ComponentGate.SourceView> source=new AtomicReference<>(ComponentGate.SourceView.acceptedNative(new Object()));ComponentGate gate=ComponentGate.forNativeSource(ProcessRole.MAIN,source::get);ComponentGate.ResourcePin old=gate.newResourcePin();old.checkOrBind(gate.requireBusiness());source.set(ComponentGate.SourceView.acceptedNative(new Object()));ComponentGate.BusinessPermit fresh=gate.requireBusiness();assertThrows(SecurityException.class,()->old.checkOrBind(fresh));assertThrows(SecurityException.class,old::requireCurrent);gate.newResourcePin().checkOrBind(fresh);
    }
    @Test public void resourcePinsDoNotBorrowForeignGateOrUnboundAuthority(){
        ComponentGate a=new ComponentGate(ProcessRole.MAIN,()->ComponentGate.SourceState.TRUSTED),b=new ComponentGate(ProcessRole.MAIN,()->ComponentGate.SourceState.TRUSTED);ComponentGate.ResourcePin pin=a.newResourcePin();assertThrows(SecurityException.class,pin::requireCurrent);assertThrows(SecurityException.class,()->pin.checkOrBind(b.requireBusiness()));pin.checkOrBind(a.requireBusiness());pin.requireCurrent();
    }
    @Test public void sourceProviderReentryPoisonsInsteadOfReturningAValidPermit(){
        java.util.concurrent.atomic.AtomicReference<ComponentGate> ref=new java.util.concurrent.atomic.AtomicReference<>();ComponentGate gate=ComponentGate.forNativeSource(ProcessRole.MAIN,()->{ComponentGate self=ref.get();if(self!=null)self.sourceState();return ComponentGate.SourceView.acceptedNative(ref);});ref.set(gate);assertThrows(SecurityException.class,gate::requireBusiness);assertEquals(ComponentGate.SourceState.LOCKED,gate.sourceState());
    }
    @Test public void nativeSnapshotNeverTurnsMissingContextIntoTrust(){
        assertThrows(IllegalArgumentException.class,()->ComponentGate.SourceView.acceptedNative(null));assertThrows(IllegalArgumentException.class,()->ComponentGate.SourceView.untrusted(ComponentGate.SourceState.TRUSTED));ComponentGate child=ComponentGate.forNativeSource(ProcessRole.GECKO_CHILD,()->{throw new AssertionError("child must not read main source provider");});assertEquals(ComponentGate.SourceState.LOCKED,child.sourceState());
    }
    @Test public void newViewsForSameNativeContextStillPinSameLifetimeAcrossThreads()throws Exception{
        Object context=new Object();ComponentGate gate=ComponentGate.forNativeSource(ProcessRole.MAIN,()->ComponentGate.SourceView.acceptedNative(context));ComponentGate.ResourcePin pin=gate.newResourcePin();java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);try{java.util.List<java.util.concurrent.Future<?>> pending=new java.util.ArrayList<>();for(int i=0;i<32;i++)pending.add(pool.submit(()->pin.checkOrBind(gate.requireBusiness())));for(java.util.concurrent.Future<?> f:pending)f.get(3,java.util.concurrent.TimeUnit.SECONDS);pin.requireCurrent();}finally{pool.shutdown();assertTrue(pool.awaitTermination(3,java.util.concurrent.TimeUnit.SECONDS));}
    }
    @Test public void nullOrFailedSourceDecisionFailsClosed(){
        ComponentGate nil=new ComponentGate(ProcessRole.MAIN,()->null);assertEquals(ComponentGate.SourceState.LOCKED,nil.sourceState());
        ComponentGate failed=new ComponentGate(ProcessRole.MAIN,()->{throw new IllegalStateException("synthetic");});assertEquals(ComponentGate.SourceState.LOCKED,failed.sourceState());
    }
}
