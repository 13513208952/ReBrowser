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
    @Test public void nullOrFailedSourceDecisionFailsClosed(){
        ComponentGate nil=new ComponentGate(ProcessRole.MAIN,()->null);assertEquals(ComponentGate.SourceState.LOCKED,nil.sourceState());
        ComponentGate failed=new ComponentGate(ProcessRole.MAIN,()->{throw new IllegalStateException("synthetic");});assertEquals(ComponentGate.SourceState.LOCKED,failed.sourceState());
    }
}
