package io.github.rebrowser.continuity;

import android.app.Application;
import android.os.Looper;
import android.os.SystemClock;
import io.github.rebrowser.bootstrap.StartupCoordinator;
import java.io.IOException;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Destructive CONTINUITY ONLY, never ordinary subapplication cleanup. Narrow safe COLD path:
 * real private FORCE2+FORCE3 consent, actual never-activated native startup fence, bounded exact
 * known epoch inventory, no shared/warm Gecko/unknown data, exact existing DATA_WRAP alias,
 * durable DESTRUCTION marker BEFORE key/file removal, actual alias-absence/empty verification.
 * No callback/DTO/Java close as native quiescence, no arbitrary paths/aliases, no fresh-install
 * inference or direct business unlock. Unsupported warm/shared namespaces fail BEFORE mutation.
 * Actual Android Keystore/PM/filesystem/power-loss/recovery require separate qualification.
 */
public final class EpochErasureCoordinator {
    public enum Observation { CONSENT_SPENT_LOCKED, PREPARED_PRESERVED, DESTRUCTION_RECORDED, EMPTY_VERIFIED_LOCKED, COMMIT_ATTEMPTED, COMMITTED_METADATA_ONLY, UNKNOWN_LOCKED }
    public static final class Run {
        private final EpochErasureCoordinator issuer;private final ContinuityCoordinator.Context context;private final StartupCoordinator.ColdContinuityFence cold;private final AtomicBoolean ioAttempted=new AtomicBoolean();volatile Observation observation=Observation.CONSENT_SPENT_LOCKED;LastGoodSourceStore.View current;private LastGoodSourceStore.SourceEpoch pending;private PrivateEpochManifest manifest;
        private Run(EpochErasureCoordinator issuer,ContinuityCoordinator.Context context,StartupCoordinator.ColdContinuityFence cold){this.issuer=issuer;this.context=context;this.cold=cold;current=context.nativeBaselineView();}
        public Observation observation(){return observation;} // Observation only, never a private erasure/source receipt.
    }
    public static final class VerifiedEmpty {
        final EpochErasureCoordinator issuer;final Run run;final Object newDirectoryKey;final long issued;final AtomicBoolean consumed=new AtomicBoolean();
        private VerifiedEmpty(EpochErasureCoordinator issuer,Run run,Object newDirectoryKey){this.issuer=issuer;this.run=run;this.newDirectoryKey=newDirectoryKey;issued=SystemClock.elapsedRealtime();}
    }
    private final Application application;private final StartupCoordinator startup;final LastGoodSourceStore sourceStore;private final ContinuityCoordinator.CurrentSourceGuard nativeSourceCurrent;private boolean began;
    public EpochErasureCoordinator(Application actualOwnApplication,StartupCoordinator actualCanonicalStartup,LastGoodSourceStore exactNativeBaselineStore,ContinuityCoordinator.CurrentSourceGuard actualCurrentCandidateSource){if(actualOwnApplication==null || actualCanonicalStartup==null || exactNativeBaselineStore==null || actualCurrentCandidateSource==null)throw new IllegalArgumentException("Native continuity/source owners required");application=actualOwnApplication;startup=actualCanonicalStartup;sourceStore=exactNativeBaselineStore;nativeSourceCurrent=actualCurrentCandidateSource;}
    /** Actual owned warning UI only. Spend final private consent BEFORE any async handoff. */
    public Run beginAfterNativeFinalConsent(ContinuityCoordinator originalFlow,ContinuityCoordinator.Consent actualFinalConsent){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native final owner-consent UI required");if(began || originalFlow==null || actualFinalConsent==null)throw new SecurityException("Native continuity attempt unavailable");began=true;ContinuityCoordinator.Context context=originalFlow.consumeConsentForNativeErasure(actualFinalConsent);StartupCoordinator.ColdContinuityFence cold=startup.fenceNeverActivatedMismatchForNativeRecovery();cold.requireStillCold(application);return new Run(this,context,cold);}
    public VerifiedEmpty executeExactColdErasureOnOwnedIo(Run exactRun)throws Exception{
        io();own(exactRun);if(!exactRun.ioAttempted.compareAndSet(false,true))throw new SecurityException("Native destructive IO route already attempted");try{checkPlatformSource(exactRun);sourceStore.requireNativeBootstrapPath(application.getDataDir().toPath().resolve("no_backup").resolve(LastGoodSourceStore.BOOTSTRAP_DIRECTORY));sourceStore.confirmUnchanged(exactRun.current);exactRun.manifest=PrivateEpochManifest.captureNativeCold(application,exactRun.context,exactRun.cold);check(exactRun);
            LastGoodSourceStore.Record old=exactRun.current.record();if(old.phase()==LastGoodSourceStore.Phase.STABLE){byte[] epoch=new byte[16];new SecureRandom().nextBytes(epoch);if(Arrays.equals(epoch,exactRun.context.oldEpoch()))throw new IOException("Native new-epoch collision; no retry");exactRun.pending=new LastGoodSourceStore.SourceEpoch(exactRun.context.candidateSignerSha256(),exactRun.context.candidateRootSha256(),epoch);exactRun.manifest.newEpochPath(epoch);exactRun.current=sourceStore.prepareReplacement(exactRun.current,exactRun.pending,fence(exactRun));}else if(old.phase()==LastGoodSourceStore.Phase.PREPARED_OLD_DATA_PRESERVED){exactRun.pending=old.pendingSource();exactRun.manifest.newEpochPath(exactRun.pending.dataEpoch());}else throw new IOException("Native continuity requires independent uncertain-phase recovery");exactRun.observation=Observation.PREPARED_PRESERVED;KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);String nextAlias=PrivateEpochManifest.nativeWrappingAlias(exactRun.pending.dataEpoch());if(keys.containsAlias(nextAlias) || keys.getKey(nextAlias,null)!=null)throw new GeneralSecurityException("Candidate epoch key already exists; no adoption");
            checkPlatformSource(exactRun);sourceStore.confirmUnchanged(exactRun.current);exactRun.manifest.requireUnchangedSnapshot();exactRun.current=sourceStore.markDestructionMayStart(exactRun.current,fence(exactRun));exactRun.observation=Observation.DESTRUCTION_RECORDED;
            // Only now is destruction permitted. These are actual provider calls, NOT receipts
            // reconstructed from LastGood phase metadata or client success/close observations.
            exactRun.manifest.requireUnchangedSnapshot();for(String alias:exactRun.manifest.nativeEpochKeyAliases()){check(exactRun);if(keys.containsAlias(alias))keys.deleteEntry(alias);if(keys.containsAlias(alias) || keys.getKey(alias,null)!=null)throw new GeneralSecurityException("Old epoch wrapping key absence not observed");}
            exactRun.manifest.deleteExactOwnedNodes(()->check(exactRun));Object emptyKey=exactRun.manifest.createFreshEmptyEpoch(exactRun.pending.dataEpoch(),()->check(exactRun));verifyActuallyEmpty(exactRun,emptyKey);exactRun.current=sourceStore.recordVerifiedEmptyEpoch(exactRun.current,fence(exactRun));verifyActuallyEmpty(exactRun,emptyKey);exactRun.observation=Observation.EMPTY_VERIFIED_LOCKED;return new VerifiedEmpty(this,exactRun,emptyKey);
        }catch(Exception|Error unknown){exactRun.observation=Observation.UNKNOWN_LOCKED;throw unknown;} // No rescue/erase/commit retry after unknown irreversible work.
    }
    void verifyActuallyEmpty(Run run,Object nativeNewDirectoryKey)throws Exception{check(run);run.manifest.verifyFreshEmptyEpoch(run.pending.dataEpoch(),nativeNewDirectoryKey);KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);String nextAlias=PrivateEpochManifest.nativeWrappingAlias(run.pending.dataEpoch());if(keys.containsAlias(nextAlias) || keys.getKey(nextAlias,null)!=null)throw new GeneralSecurityException("Fresh epoch key unexpectedly exists before provisioning");for(String alias:run.manifest.nativeEpochKeyAliases())if(keys.containsAlias(alias) || keys.getKey(alias,null)!=null)throw new GeneralSecurityException("Old epoch key unexpectedly present");check(run);}
    LastGoodSourceStore.NativeFence fence(Run run){return (expected,replacement)->{if(expected!=run.current)throw new IOException("Different native continuity ledger owner");try{check(run);}catch(RuntimeException changed){throw new IOException("Native continuity source fence failed",changed);}};}
    void checkPlatformSource(Run run)throws SigningIdentity.Unavailable{check(run);CompiledSourceIdentity compiled=CompiledSourceIdentity.current();if(!compiled.configured() || !compiled.requireRootSpkiSha256().equals(run.context.candidateRootSha256()) || !SigningIdentity.inspectOwn(application).currentCertificateSha256().equals(run.context.candidateSignerSha256()))throw new SecurityException("Native candidate package/source changed");check(run);}
    private void check(Run run){own(run);run.cold.requireStillCold(application);nativeSourceCurrent.checkCurrent(run.context);run.cold.requireStillCold(application);}
    private void own(Run run){if(run==null || run.issuer!=this)throw new SecurityException("Foreign native continuity run");}
    private static void io(){if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Native destructive continuity bounded safety IO required");}
}
