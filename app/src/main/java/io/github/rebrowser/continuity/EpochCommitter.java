package io.github.rebrowser.continuity;

import android.os.Looper;
import android.os.SystemClock;

/** One native commit of ACTUAL private verified-empty receipt, never LastGood phase/DTO as proof.
 * Repeats exact source/actual filesystem/new-directory/key-absence checks and real ledger CAS
 * immediately before AND after replacement; no non-atomic fallback, timeout retry or trust setter.
 * Returned View is stored metadata ONLY. Startup remains locked; a new native source inspection
 * must independently admit the STABLE signer/compiled Root/epoch on a subsequent native launch.
 * Native fresh-key/default provisioning, warm/shared-Gecko erasure and power-loss qualification
 * are separate held responsibilities; this does not claim instant business usability or flash wipe.
 */
public final class EpochCommitter {
    private final EpochErasureCoordinator actualNativeOwner;
    public EpochCommitter(EpochErasureCoordinator actualNativeErasureOwner){if(actualNativeErasureOwner==null)throw new IllegalArgumentException("Native destructive continuity owner required");actualNativeOwner=actualNativeErasureOwner;}
    public LastGoodSourceStore.View commitActualVerifiedEmptyOnOwnedIo(EpochErasureCoordinator.VerifiedEmpty actualPrivateReceipt)throws Exception{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Native continuity commit bounded IO required");if(actualPrivateReceipt==null || actualPrivateReceipt.issuer!=actualNativeOwner || !actualPrivateReceipt.consumed.compareAndSet(false,true))throw new SecurityException("Native verified-empty receipt unavailable or spent");EpochErasureCoordinator.Run run=actualPrivateReceipt.run;run.observation=EpochErasureCoordinator.Observation.COMMIT_ATTEMPTED;
        try{long now=SystemClock.elapsedRealtime();if(now<actualPrivateReceipt.issued || now-actualPrivateReceipt.issued>=30_000)throw new SecurityException("Native empty-epoch commit receipt expired");actualNativeOwner.verifyActuallyEmpty(run,actualPrivateReceipt.newDirectoryKey);actualNativeOwner.checkPlatformSource(run);actualNativeOwner.sourceStore.confirmUnchanged(run.current);
            LastGoodSourceStore.View committed=actualNativeOwner.sourceStore.commitReplacement(run.current,actualNativeOwner.fence(run));run.current=committed;actualNativeOwner.verifyActuallyEmpty(run,actualPrivateReceipt.newDirectoryKey);actualNativeOwner.checkPlatformSource(run);if(committed.kind()!=LastGoodSourceStore.Kind.CURRENT || committed.record().phase()!=LastGoodSourceStore.Phase.STABLE)throw new SecurityException("Native source cutover outcome unavailable");run.observation=EpochErasureCoordinator.Observation.COMMITTED_METADATA_ONLY;return committed;
        }catch(Exception|Error unknown){run.observation=EpochErasureCoordinator.Observation.UNKNOWN_LOCKED;throw unknown;} // Spend survives every failure/unknown, including atomic-move/readback outcome.
    }
}
