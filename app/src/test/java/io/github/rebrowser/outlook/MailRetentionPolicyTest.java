package io.github.rebrowser.outlook;

import static org.junit.Assert.*;
import org.junit.Test;

/** Conservative classification: unknown provenance always keeps the object. */
public final class MailRetentionPolicyTest {
    @Test public void uniqueOriginalAndUnknownDerivativeAreAlwaysKept(){
        MailRetentionPolicy.Observation available=new MailRetentionPolicy.Observation(true,false,false);
        assertEquals(MailRetentionPolicy.Decision.KEEP,MailRetentionPolicy.classify(MailRetentionPolicy.ContentClass.UNIQUE_ORIGINAL,available));
        assertEquals(MailRetentionPolicy.Decision.UNKNOWN_KEEP,MailRetentionPolicy.classify(MailRetentionPolicy.ContentClass.DERIVED_UNKNOWN,available));
    }
    @Test public void rebuildableDerivativeNeedsPositiveEvidenceOnly(){
        assertEquals(MailRetentionPolicy.Decision.RECLAIM_CANDIDATE,MailRetentionPolicy.classify(MailRetentionPolicy.ContentClass.DERIVED_REBUILDABLE,new MailRetentionPolicy.Observation(true,false,false)));
        assertEquals(MailRetentionPolicy.Decision.UNKNOWN_KEEP,MailRetentionPolicy.classify(MailRetentionPolicy.ContentClass.DERIVED_REBUILDABLE,new MailRetentionPolicy.Observation(null,false,false)));
        assertEquals(MailRetentionPolicy.Decision.UNKNOWN_KEEP,MailRetentionPolicy.classify(MailRetentionPolicy.ContentClass.DERIVED_REBUILDABLE,new MailRetentionPolicy.Observation(false,false,false)));
    }
    @Test public void dependentsOrPendingWorkBlockReclaim(){
        assertEquals(MailRetentionPolicy.Decision.KEEP,MailRetentionPolicy.classify(MailRetentionPolicy.ContentClass.DERIVED_REBUILDABLE,new MailRetentionPolicy.Observation(true,true,false)));
        assertEquals(MailRetentionPolicy.Decision.KEEP,MailRetentionPolicy.classify(MailRetentionPolicy.ContentClass.DERIVED_REBUILDABLE,new MailRetentionPolicy.Observation(true,false,true)));
    }
    @Test public void unknownDependentsOrPendingWorkNeverBecomesReclaimCandidate(){
        assertEquals(MailRetentionPolicy.Decision.UNKNOWN_KEEP,MailRetentionPolicy.classify(
                MailRetentionPolicy.ContentClass.DERIVED_REBUILDABLE,new MailRetentionPolicy.Observation(true,null,false)));
        assertEquals(MailRetentionPolicy.Decision.UNKNOWN_KEEP,MailRetentionPolicy.classify(
                MailRetentionPolicy.ContentClass.DERIVED_REBUILDABLE,new MailRetentionPolicy.Observation(true,false,null)));
        assertEquals(MailRetentionPolicy.Decision.UNKNOWN_KEEP,MailRetentionPolicy.classify(
                MailRetentionPolicy.ContentClass.DERIVED_REBUILDABLE,new MailRetentionPolicy.Observation(true,null,null)));
        assertEquals(MailRetentionPolicy.Decision.KEEP,MailRetentionPolicy.classify(
                MailRetentionPolicy.ContentClass.DERIVED_REBUILDABLE,new MailRetentionPolicy.Observation(true,true,null)));
    }
    @Test public void labelsStateCandidateNotCompletion(){
        assertTrue(MailRetentionPolicy.label(MailRetentionPolicy.Decision.RECLAIM_CANDIDATE).contains("待原生"));
        assertThrows(IllegalArgumentException.class,()->MailRetentionPolicy.label(null));
        assertThrows(IllegalArgumentException.class,()->MailRetentionPolicy.classify(null,new MailRetentionPolicy.Observation(true,false,false)));
    }
}
