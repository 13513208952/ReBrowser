package io.github.rebrowser.control;

import io.github.rebrowser.authority.AdminProofVerifier;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Bounded native-server challenges, exact signed-byte matching and one-process admission.
 * NOT a durable job journal/dispatcher or target enrollment. Native callbacks must perform
 * live source/target/policy/disable/scope/grant/action checks and durable idempotent job
 * reservation; never run domain effects inside them. Dispatch only after successful return.
 * A reservation exception leaves the nonce SPENT: outcome may be unknown; use fresh proof
 * to inspect the original request/job, never replay a second route. Server restart changes
 * its nonce; this does not reset TaskCoordinator's actual work budgets or durable jobs.
 */
public final class ControlChallengeStore {
    public static final long LIFETIME_MILLIS = 30_000;
    public static final int MAX_RETAINED_FRAME_BYTES = 1_048_576;
    private final LongSupplier elapsedRealtime;
    private final SecureRandom random = new SecureRandom();
    private final byte[] serverNonce = new byte[32];
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private final int capacity, perPrincipalCapacity;
    private Context context;
    private long lastTime = -1;
    private int retainedFrameBytes;
    private boolean closed, admitting;

    /** Trusted native installation/epoch/policy snapshot, NEVER taken from client claims. */
    public static final class Context {
        private final byte[] target, epoch, policy;
        private final long disableRevision;
        public Context(byte[] targetInstallSpkiSha256, byte[] dataEpoch, byte[] policySha256, long disableRevision) {
            if (disableRevision < 0) throw invalidInput();
            target = copy(targetInstallSpkiSha256,32); epoch = copy(dataEpoch,16); policy = copy(policySha256,32);
            this.disableRevision = disableRevision;
        }
    }

    /** Native-resolved fields after action schema/target selection, not an authorized action. */
    public static final class Template {
        private final String principal, action, scope, ownerGrant;
        private final byte[] requestId, delegation, args;
        public Template(String principalKeyId, String actionId, String scopeRef, String ownerGrantRef,
                byte[] requestId, byte[] delegationSha256, byte[] originalArgsBytes) {
            principal=ascii(principalKeyId,128,false);action=ascii(actionId,128,false);
            scope=ascii(scopeRef,256,false);ownerGrant=ascii(ownerGrantRef,128,true);
            this.requestId=copy(requestId,16);delegation=copy(delegationSha256,32);
            if(originalArgsBytes==null || originalArgsBytes.length==0 || originalArgsBytes.length>ControlEnvelope.MAX_ARGS_BYTES)throw invalidInput();
            args=originalArgsBytes.clone();
        }
    }

    public enum Failure { CLOSED, RESOURCE_LIMIT, INVALID_CHALLENGE, EXPIRED, REPLAY, REENTRANT_ADMISSION, CLOCK_UNAVAILABLE }
    public static final class Rejected extends Exception {
        private final Failure failure;
        private Rejected(Failure failure) { super(failure.name()); this.failure=failure; }
        public Failure failure() { return failure; }
    }
    public interface Admission<R> {
        /** Repeatable bounded native policy READ recheck; no effects, grant consumption or slow IO. */
        void authorize(AdminProofVerifier.KeyProof proof) throws Exception;
        /** Reserve under native policy/transaction fence; check fence inside transaction before commit. No effects. */
        R reserveJob(CommitFence fence) throws Exception;
    }
    /** Native callback capability valid only during this exact admission, never an IPC token. */
    public static final class CommitFence {
        private final ControlChallengeStore owner;
        private final AdminProofVerifier.KeyProof proof;
        private final Pending entry;
        private final Admission<?> admission;
        private final Thread admissionThread=Thread.currentThread();
        private boolean active=true,checked,checking;
        private CommitFence(ControlChallengeStore owner,AdminProofVerifier.KeyProof proof,Pending entry,Admission<?> admission){this.owner=owner;this.proof=proof;this.entry=entry;this.admission=admission;}
        public AdminProofVerifier.KeyProof proof(){return proof;}
        /** Native ephemeral Actor access cannot turn checked failures into a positive receipt. */
        public void checkCurrentUnchecked(){try{checkCurrent();}catch(RuntimeException failure){throw failure;}catch(Exception unavailable){throw new SecurityException("Current challenge fence unavailable");}}
        /** Final current policy and deadline recheck, after DB lock/IO and before marking success. */
        public void checkCurrent()throws Exception{
            if(Thread.currentThread()!=admissionThread)throw new Rejected(Failure.INVALID_CHALLENGE);
            synchronized(owner){
                if(!active || !owner.admitting)throw new Rejected(Failure.INVALID_CHALLENGE);
                if(checking)throw new Rejected(Failure.REENTRANT_ADMISSION);
                checking=true;
                try{
                    owner.active();admission.authorize(proof);owner.active();
                    if(owner.now()>=entry.deadline)throw new Rejected(Failure.EXPIRED);
                    String key=CryptoEncoding.base64Url(proof.request().frame().bytes(ControlEnvelope.Field.CHALLENGE_ID));
                    if(owner.pending.get(key)!=entry || !entry.spent)throw new Rejected(Failure.INVALID_CHALLENGE);
                    checked=true;
                }finally{checking=false;}
            }
        }
    }
    private static final class Pending {
        final byte[] signedFrame;
        final long deadline;
        final String principal;
        boolean spent;
        Pending(byte[] signedFrame,long deadline,String principal){this.signedFrame=signedFrame;this.deadline=deadline;this.principal=principal;}
    }

    public ControlChallengeStore(Context nativeContext, LongSupplier elapsedRealtime, int capacity, int perPrincipalCapacity) {
        if(nativeContext==null || elapsedRealtime==null || capacity<1 || capacity>256
                || perPrincipalCapacity<1 || perPrincipalCapacity>capacity)throw invalidInput();
        this.context=nativeContext;this.elapsedRealtime=elapsedRealtime;this.capacity=capacity;this.perPrincipalCapacity=perPrincipalCapacity;
        random.nextBytes(serverNonce);
    }

    /** Return exact unsigned frame for helper to sign; client cannot choose/extend server fields. */
    public synchronized ControlEnvelope issue(Template template) throws Rejected {
        active();if(admitting)throw new Rejected(Failure.REENTRANT_ADMISSION);
        if(template==null)throw invalidInput();long now=now();prune(now);
        int same=0;
        for(Pending p:pending.values())if(p.principal.equals(template.principal))same++;
        if(pending.size()>=capacity || same>=perPrincipalCapacity)throw new Rejected(Failure.RESOURCE_LIMIT);
        if(now>Long.MAX_VALUE-LIFETIME_MILLIS)throw new Rejected(Failure.CLOCK_UNAVAILABLE);
        byte[] id=new byte[16],nonce=new byte[32];String key=null;
        for(int attempt=0;attempt<16;attempt++){
            random.nextBytes(id);String candidate=CryptoEncoding.base64Url(id);
            if(!pending.containsKey(candidate)){key=candidate;break;}
        }
        if(key==null)throw new Rejected(Failure.RESOURCE_LIMIT);
        random.nextBytes(nonce);long deadline=now+LIFETIME_MILLIS;
        EnumMap<ControlEnvelope.Field,byte[]> f=new EnumMap<>(ControlEnvelope.Field.class);
        f.put(ControlEnvelope.Field.PROTOCOL,bytes("rebrowser.control/1"));f.put(ControlEnvelope.Field.APPLICATION_ID,bytes("io.github.rebrowser"));
        f.put(ControlEnvelope.Field.PRINCIPAL_KEY_ID,bytes(template.principal));f.put(ControlEnvelope.Field.TARGET_INSTALL_SPKI_SHA256,context.target);
        f.put(ControlEnvelope.Field.DATA_EPOCH,context.epoch);f.put(ControlEnvelope.Field.SERVER_INSTANCE_NONCE,serverNonce);
        f.put(ControlEnvelope.Field.COMPILED_POLICY_SHA256,context.policy);f.put(ControlEnvelope.Field.DISABLE_REVISION,u63(context.disableRevision));
        f.put(ControlEnvelope.Field.CHALLENGE_ID,id);f.put(ControlEnvelope.Field.NONCE,nonce);f.put(ControlEnvelope.Field.DEADLINE_ELAPSED_MS,u63(deadline));
        f.put(ControlEnvelope.Field.REQUEST_ID,template.requestId);f.put(ControlEnvelope.Field.ACTION_ID,bytes(template.action));f.put(ControlEnvelope.Field.SCOPE_REF,bytes(template.scope));
        f.put(ControlEnvelope.Field.OWNER_GRANT_REF,bytes(template.ownerGrant));f.put(ControlEnvelope.Field.DELEGATION_SHA256,template.delegation);f.put(ControlEnvelope.Field.ARGS_BYTES,template.args);
        ControlEnvelope frame=ControlEnvelope.fromFields(f);byte[] signed=frame.signedBytes();
        if(signed.length>MAX_RETAINED_FRAME_BYTES-retainedFrameBytes)throw new Rejected(Failure.RESOURCE_LIMIT);
        pending.put(key,new Pending(signed,deadline,template.principal));retainedFrameBytes+=signed.length;return frame;
    }

    /**
     * Serialize authorization, final freshness check and consume-before-job-reservation.
     * Native coordinator still owns durable cross-store atomicity/recovery; this monitor
     * alone does NOT establish SQLite+policy atomicity or authorize an external effect.
     */
    public synchronized <R> R admit(AdminProofVerifier.KeyProof proof, Admission<R> nativeAdmission) throws Exception {
        active();if(admitting)throw new Rejected(Failure.REENTRANT_ADMISSION);
        if(proof==null || nativeAdmission==null)throw invalidInput();
        Pending entry=match(proof,now());admitting=true;
        try{
            nativeAdmission.authorize(proof); // exception: no consumption, no job reservation
            if(match(proof,now())!=entry)throw new Rejected(Failure.INVALID_CHALLENGE);
            entry.spent=true;CommitFence fence=new CommitFence(this,proof,entry,nativeAdmission);
            try{
                R receipt=nativeAdmission.reserveJob(fence); // unknown/error remains spent; NEVER auto-retry effect
                active();
                if(receipt==null || !fence.checked)throw new IllegalStateException("Native admission did not reserve/fence a job");
                return receipt;
            }finally{fence.active=false;}
        }finally{admitting=false;}
    }
    private Pending match(AdminProofVerifier.KeyProof proof,long now) throws Rejected {
        active();ControlEnvelope frame=proof.request().frame();String key=CryptoEncoding.base64Url(frame.bytes(ControlEnvelope.Field.CHALLENGE_ID));
        Pending entry=pending.get(key);
        if(entry==null || !entry.principal.equals(proof.compiledPrincipal().keyId())
                || !MessageDigest.isEqual(entry.signedFrame,frame.signedBytes()))throw new Rejected(Failure.INVALID_CHALLENGE);
        if(entry.spent)throw new Rejected(Failure.REPLAY);
        if(now>=entry.deadline)throw new Rejected(Failure.EXPIRED);
        return entry;
    }
    /** Native policy/epoch/disable update invalidates pending frames; no client reset method. */
    public synchronized void replaceNativeContext(Context next) throws Rejected {
        active();if(admitting)throw new Rejected(Failure.REENTRANT_ADMISSION);
        if(next==null)throw invalidInput();context=next;pending.clear();retainedFrameBytes=0;
    }
    public synchronized void close() { closed=true;pending.clear();retainedFrameBytes=0; }
    private long now() throws Rejected {
        final long value;
        try{value=elapsedRealtime.getAsLong();}catch(RuntimeException failed){throw new Rejected(Failure.CLOCK_UNAVAILABLE);}
        if(value<0 || value<lastTime){closed=true;pending.clear();retainedFrameBytes=0;throw new Rejected(Failure.CLOCK_UNAVAILABLE);}
        lastTime=value;return value;
    }
    private void prune(long now) {
        Iterator<Pending> it=pending.values().iterator();
        while(it.hasNext()) { Pending p=it.next();if(now>=p.deadline){retainedFrameBytes-=p.signedFrame.length;it.remove();} }
    }
    private void active() throws Rejected {if(closed)throw new Rejected(Failure.CLOSED);}
    private static byte[] copy(byte[] v,int n){if(v==null || v.length!=n)throw invalidInput();return v.clone();}
    private static String ascii(String v,int max,boolean empty){if(v==null || v.length()>max || (!empty&&v.isEmpty()))throw invalidInput();for(int i=0;i<v.length();i++)if(v.charAt(i)<33||v.charAt(i)>126)throw invalidInput();return v;}
    private static byte[] bytes(String v){return v.getBytes(StandardCharsets.US_ASCII);}
    private static byte[] u63(long v){return ByteBuffer.allocate(8).putLong(v).array();}
    private static IllegalArgumentException invalidInput(){return new IllegalArgumentException("Invalid native challenge input");}
}
