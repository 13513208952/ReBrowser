package io.github.rebrowser.core;

import static org.junit.Assert.*;
import io.github.rebrowser.core.TaskSnapshot.*;
import org.junit.Test;

public final class TaskSnapshotTest {
    private static TaskSnapshot snapshot(Phase p,Work w,Effect e){return TaskSnapshot.fromNativeEvidence(p,w,e,Durability.COMMITTED,ClientWait.WAITING,false,true);}
    @Test public void allPhaseWorkEffectCombinationsAreExplicit(){
        int accepted=0;
        for(Phase p:Phase.values())for(Work w:Work.values())for(Effect e:Effect.values()){
            boolean allowed=(p==Phase.RESERVED&&w==Work.NOT_STARTED&&e==Effect.NOT_DISPATCHED)
                ||(p==Phase.WORK_STARTED&&w==Work.RUNNING&&e==Effect.NOT_DISPATCHED)
                ||(p==Phase.DISPATCH_INTENT&&w==Work.RUNNING&&e==Effect.UNKNOWN)
                ||(p==Phase.OUTCOME_OBSERVED&&w!=Work.NOT_STARTED&&(e==Effect.OBSERVED_SUCCESS||e==Effect.OBSERVED_FAILURE))
                ||(p==Phase.ENDED_BEFORE_DISPATCH&&w==Work.ENDED&&e==Effect.NOT_DISPATCHED)
                ||(p==Phase.OUTCOME_UNKNOWN&&w!=Work.NOT_STARTED&&e==Effect.UNKNOWN)
                ||(p==Phase.OWNER_LOST_BEFORE_DISPATCH&&w==Work.UNKNOWN&&e==Effect.NOT_DISPATCHED);
            try{snapshot(p,w,e);assertTrue(allowed);accepted++;}catch(IllegalArgumentException bad){assertFalse(allowed);}
        }
        assertEquals(14,accepted);
    }
    @Test public void timeoutDetachCancelAndStaleDocumentDoNotEndWork(){
        TaskSnapshot initial=snapshot(Phase.DISPATCH_INTENT,Work.RUNNING,Effect.UNKNOWN);
        TaskSnapshot changed=initial.withClientWait(ClientWait.DEADLINE_EXCEEDED).withCancellationRequested().withoutPublication().withClientWait(ClientWait.DETACHED);
        assertFalse(changed.actualWorkEnded());assertEquals(Work.RUNNING,changed.work());assertEquals(Effect.UNKNOWN,changed.effect());assertEquals(Durability.COMMITTED,changed.durability());assertFalse(changed.currentForPublication());assertTrue(changed.cancellationRequested());
        assertEquals(ClientWait.WAITING,initial.clientWait());assertFalse(initial.cancellationRequested());assertTrue(initial.currentForPublication());
    }
    @Test public void observedSuccessIsNotWholeChainEndAndEndIsNotEffectSuccess(){
        assertFalse(snapshot(Phase.OUTCOME_OBSERVED,Work.RUNNING,Effect.OBSERVED_SUCCESS).actualWorkEnded());
        TaskSnapshot ended=snapshot(Phase.OUTCOME_UNKNOWN,Work.ENDED,Effect.UNKNOWN);assertTrue(ended.actualWorkEnded());assertEquals(Effect.UNKNOWN,ended.effect());
        assertFalse(snapshot(Phase.OWNER_LOST_BEFORE_DISPATCH,Work.UNKNOWN,Effect.NOT_DISPATCHED).actualWorkEnded());
    }
    @Test public void durabilityAndClientWaitAreIndependentOfExecution(){
        for(Durability d:Durability.values())for(ClientWait wait:ClientWait.values()){
            TaskSnapshot s=TaskSnapshot.fromNativeEvidence(Phase.OUTCOME_OBSERVED,Work.ENDED,Effect.OBSERVED_FAILURE,d,wait,true,false);
            assertTrue(s.actualWorkEnded());assertEquals(d,s.durability());assertEquals(wait,s.clientWait());assertEquals(Effect.OBSERVED_FAILURE,s.effect());
        }
    }
    @Test public void codesAreStableAndUnknownEnumsAndNullViewsFailClosed(){
        assertEquals(0,Phase.RESERVED.code());assertEquals(6,Phase.OWNER_LOST_BEFORE_DISPATCH.code());assertEquals(2,Work.ENDED.code());assertEquals(3,Effect.OBSERVED_FAILURE.code());
        for(Phase p:Phase.values())assertSame(p,Phase.fromCode(p.code()));for(Work w:Work.values())assertSame(w,Work.fromCode(w.code()));for(Effect e:Effect.values())assertSame(e,Effect.fromCode(e.code()));
        assertThrows(IllegalArgumentException.class,()->Phase.fromCode(7));assertThrows(IllegalArgumentException.class,()->Work.fromCode(-1));assertThrows(IllegalArgumentException.class,()->Effect.fromCode(4));
        assertThrows(IllegalArgumentException.class,()->snapshot(Phase.RESERVED,Work.RUNNING,Effect.NOT_DISPATCHED));assertThrows(IllegalArgumentException.class,()->snapshot(Phase.RESERVED,null,Effect.NOT_DISPATCHED));
        assertThrows(IllegalArgumentException.class,()->snapshot(Phase.RESERVED,Work.NOT_STARTED,Effect.NOT_DISPATCHED).withClientWait(null));
    }
}
