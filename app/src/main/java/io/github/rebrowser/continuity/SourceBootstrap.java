package io.github.rebrowser.continuity;

import android.app.Application;
import android.os.SystemClock;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native IO-only exact STABLE source resume. No fresh seed/rescue/erasure/business IO. */
public final class SourceBootstrap {
    public enum Status { UNCONFIGURED, MISSING_BASELINE, UNCERTAIN_METADATA, RECOVERY_REQUIRED, SOURCE_MISMATCH, ACCEPTED_STABLE, UNAVAILABLE }
    public static final long MAX_TRANSFER_AGE_MS=30_000;
    public static final class AcceptedContext {
        private final byte[] epoch;private final String signer,root;
        private AcceptedContext(LastGoodSourceStore.SourceEpoch baseline){epoch=baseline.dataEpoch();signer=baseline.signingSha256();root=baseline.rootSha256();}
        public byte[] dataEpoch(){return epoch.clone();}
        public String signingSha256(){return signer;}
        public String rootSha256(){return root;}
    }
    public static final class Inspection {
        private final Object application;private final Status status;private final long started;
        private final AcceptedContext accepted;private final AtomicBoolean transferred=new AtomicBoolean();
        private Inspection(Object application,Status status,long started,AcceptedContext accepted){this.application=application;this.status=status;this.started=started;this.accepted=accepted;}
        public Status status(){return status;}
        /** Exact own native app, monotonic <=30s, one attempt spent BEFORE handoff; not IPC input. */
        public AcceptedContext takeAccepted(Application nativeApplication){return transfer(nativeApplication,SystemClock.elapsedRealtime());}
        private AcceptedContext transfer(Object nativeApplication,long now){
            if(!transferred.compareAndSet(false,true))throw new SecurityException("Native source inspection already transferred");
            if(application!=nativeApplication || accepted==null || status!=Status.ACCEPTED_STABLE || now<started || now-started>MAX_TRANSFER_AGE_MS)throw new SecurityException("Native source inspection expired/unavailable");
            return accepted;
        }
    }
    /** No PM/filesystem IO when compiled Root missing. Directory/store always native-owned, not IPC. */
    public static Inspection inspectOwn(Application nativeApplication,LastGoodSourceStore nativeStore){
        CompiledSourceIdentity root=CompiledSourceIdentity.current();
        if(!root.configured())return new Inspection(nativeApplication,Status.UNCONFIGURED,0,null);
        long start=SystemClock.elapsedRealtime();
        if(nativeApplication==null || nativeStore==null)return new Inspection(nativeApplication,Status.UNAVAILABLE,start,null);
        try{
            nativeStore.requireNativeBootstrapPath(nativeApplication.getNoBackupFilesDir().toPath().resolve(LastGoodSourceStore.BOOTSTRAP_DIRECTORY));
            SigningIdentity.OwnInspection before=SigningIdentity.inspectOwnForContinuity(nativeApplication);
            LastGoodSourceStore.View view=nativeStore.inspect();
            SigningIdentity.OwnInspection after=SigningIdentity.inspectOwnForContinuity(nativeApplication);
            LastGoodSourceStore.ReadReceipt exact=nativeStore.confirmUnchanged(view);
            return evaluateNative(nativeApplication,root,before,after,exact,start,SystemClock.elapsedRealtime());
        }catch(SigningIdentity.Unavailable|IOException|RuntimeException unavailable){return new Inspection(nativeApplication,Status.UNAVAILABLE,start,null);}
    }
    /** PRIVATE native provenance composition; reflected SYNTHETIC fixtures are NOT OS evidence. */
    private static Inspection evaluateNative(Object app,CompiledSourceIdentity root,SigningIdentity.OwnInspection before,SigningIdentity.OwnInspection after,LastGoodSourceStore.ReadReceipt exact,long start,long now){
        if(app==null || root==null || !root.configured())return new Inspection(app,Status.UNCONFIGURED,start,null);
        if(start<0 || now<start || now-start>MAX_TRANSFER_AGE_MS || before==null || after==null || !before.belongsTo(app) || !after.belongsTo(app) || !before.sameInstallation(after) || exact==null)return new Inspection(app,Status.UNAVAILABLE,start,null);
        LastGoodSourceStore.View view=exact.snapshot();
        if(view.kind()==LastGoodSourceStore.Kind.MISSING)return new Inspection(app,Status.MISSING_BASELINE,start,null);
        if(view.kind()!=LastGoodSourceStore.Kind.CURRENT || !view.hasBaselineMetadata())return new Inspection(app,Status.UNCERTAIN_METADATA,start,null);
        LastGoodSourceStore.Record record=view.record();
        if(record.phase()!=LastGoodSourceStore.Phase.STABLE)return new Inspection(app,Status.RECOVERY_REQUIRED,start,null);
        LastGoodSourceStore.SourceEpoch baseline=record.lastGood();
        // Actual current signer only. Android lineage membership alone needs explicit rescue path.
        if(root.compareBaselineRoot(baseline)!=CompiledSourceIdentity.RootRelation.MATCH || !after.identity().currentCertificateSha256().equals(baseline.signingSha256()))return new Inspection(app,Status.SOURCE_MISMATCH,start,null);
        return new Inspection(app,Status.ACCEPTED_STABLE,start,new AcceptedContext(baseline));
    }
    private SourceBootstrap(){}
}
