package io.github.rebrowser.control;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import io.github.rebrowser.authority.AdminProofVerifier;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.core.TaskJournal;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Native SQLite immutable request->job ledger, not task execution or result authority.
 * Caller owns a DEDICATED v2 private admin database opened only after the source gate.
 * Never pass a mailbox/legacy DB or accept a path/SQL/permit from IPC. Fresh proof for
 * same content returns same job; changed content conflicts. No timeout/lease cleanup.
 * TaskJournal reservation shares this exact commit. Native coordinator must still recheck
 * policy/target/owner grants, and serialize admission before effects. This class never
 * opens a DB, executes an effect, closes caller DB, clears unknown schema or auto-retries.
 */
public final class IdempotencyStore {
    private static final String TABLE="admin_requests_v1";
    private static final String CREATE="CREATE TABLE admin_requests_v1 ("
        +"principal_fp TEXT NOT NULL CHECK(typeof(principal_fp)='text' AND length(principal_fp)=64 AND principal_fp NOT GLOB '*[^0-9a-f]*'),"
        +"target_fp TEXT NOT NULL CHECK(typeof(target_fp)='text' AND length(target_fp)=64 AND target_fp NOT GLOB '*[^0-9a-f]*'),"
        +"data_epoch TEXT NOT NULL CHECK(typeof(data_epoch)='text' AND length(data_epoch)=32 AND data_epoch NOT GLOB '*[^0-9a-f]*'),"
        +"request_id TEXT NOT NULL CHECK(typeof(request_id)='text' AND length(request_id)=32 AND request_id NOT GLOB '*[^0-9a-f]*'),"
        +"content_sha256 BLOB NOT NULL CHECK(typeof(content_sha256)='blob' AND length(content_sha256)=32),"
        +"job_ref TEXT NOT NULL UNIQUE CHECK(typeof(job_ref)='text' AND length(job_ref)=34 AND substr(job_ref,1,2)='j_' AND substr(job_ref,3) NOT GLOB '*[^0-9a-f]*'),"
        +"PRIMARY KEY(principal_fp,target_fp,data_epoch,request_id))";
    private static final String NO_UPDATE="CREATE TRIGGER admin_request_no_update BEFORE UPDATE ON admin_requests_v1 BEGIN SELECT RAISE(ABORT,'IMMUTABLE_REQUEST_BINDING'); END";
    private static final String NO_DELETE="CREATE TRIGGER admin_request_no_delete BEFORE DELETE ON admin_requests_v1 BEGIN SELECT RAISE(ABORT,'REQUEST_REUSE_FORBIDDEN'); END";
    private static final String NO_REPLACE="CREATE TRIGGER admin_request_no_replace BEFORE INSERT ON admin_requests_v1 WHEN EXISTS(SELECT 1 FROM admin_requests_v1 WHERE principal_fp=NEW.principal_fp AND target_fp=NEW.target_fp AND data_epoch=NEW.data_epoch AND request_id=NEW.request_id OR job_ref=NEW.job_ref) BEGIN SELECT RAISE(ABORT,'REQUEST_ALREADY_BOUND'); END";
    private static final String WHERE="principal_fp=? AND target_fp=? AND data_epoch=? AND request_id=?";
    private final SQLiteDatabase database;
    private final ComponentGate gate;
    private final ComponentGate.ResourcePin sourceLifetime;
    private final String target,epoch;
    private final int maxJobs;
    private boolean initialized;
    private final SecureRandom random=new SecureRandom();

    public static final class ContentKey {
        private final String principal,target,epoch,request;
        private final byte[] content;
        private ContentKey(String principal,String target,String epoch,String request,byte[] content){this.principal=principal;this.target=target;this.epoch=epoch;this.request=request;this.content=content;}
        public String principalFingerprint(){return principal;}
        public String targetFingerprint(){return target;}
        public String dataEpoch(){return epoch;}
        public String requestId(){return request;}
        public byte[] contentSha256(){return content.clone();}
    }
    public static final class Reservation {
        private final String job;
        private final boolean existing;
        private Reservation(String job,boolean existing){this.job=job;this.existing=existing;}
        public String jobRef(){return job;}
        public boolean existingJob(){return existing;}
    }
    public static final class Conflict extends Exception {private Conflict(){super("REQUEST_ID_CONTENT_CONFLICT");}}
    public static final class ResourceLimit extends Exception {private ResourceLimit(){super("ADMIN_JOB_LEDGER_FULL");}}

    public IdempotencyStore(SQLiteDatabase nativeDedicatedDatabase,ComponentGate gate,
            byte[] nativeTargetInstallSpkiSha256,byte[] nativeDataEpoch,int maxJobs,ComponentGate.BusinessPermit nativeOpeningPermit){
        if(nativeDedicatedDatabase==null || gate==null || maxJobs<1 || maxJobs>1_000_000)throw invalid();
        this.database=nativeDedicatedDatabase;this.gate=gate;sourceLifetime=gate.newResourcePin();sourceLifetime.checkOrBind(nativeOpeningPermit);target=hex(fixed(nativeTargetInstallSpkiSha256,32));epoch=hex(fixed(nativeDataEpoch,16));this.maxJobs=maxJobs;
    }
    public static String[] schemaStatements(){return new String[]{CREATE,NO_UPDATE,NO_DELETE,NO_REPLACE};}
    public synchronized void initialize(ComponentGate.BusinessPermit permit){
        initialized=false;sourceLifetime.checkOrBind(permit);
        if(database.inTransaction())throw new IllegalStateException("Nested administrator transaction forbidden");
        database.beginTransactionNonExclusive();
        try{
            sourceLifetime.checkOrBind(permit);int version=database.getVersion();
            if(version!=0 && version!=1)throw new IllegalStateException("Unsupported administrator journal schema");
            if(version==0){for(String statement:schemaStatements())database.execSQL(statement);database.setVersion(1);}
            Set<String> actual=new HashSet<>();try(Cursor c=database.rawQuery("PRAGMA table_info(admin_requests_v1)",null)){while(c.moveToNext())actual.add(c.getString(c.getColumnIndexOrThrow("name")));}
            if(!actual.equals(new HashSet<>(Arrays.asList("principal_fp","target_fp","data_epoch","request_id","content_sha256","job_ref"))))throw new IllegalStateException("Administrator journal schema mismatch");
            requireSchema("table",TABLE,CREATE);
            requireSchema("trigger","admin_request_no_update",NO_UPDATE);
            requireSchema("trigger","admin_request_no_delete",NO_DELETE);
            requireSchema("trigger","admin_request_no_replace",NO_REPLACE);
            sourceLifetime.checkOrBind(permit);database.setTransactionSuccessful();
        }finally{database.endTransaction();}
        sourceLifetime.checkOrBind(permit);initialized=true;
    }
    private void requireSchema(String type,String name,String expected){
        try(Cursor c=database.rawQuery("SELECT sql FROM sqlite_master WHERE type=? AND name=?",new String[]{type,name})){
            if(!c.moveToFirst() || !expected.equals(c.getString(0)))throw new IllegalStateException("Administrator journal schema mismatch");
        }
    }
    /** Invoke only from native authorized ControlChallengeStore.Admission.reserveJob. */
    public synchronized Reservation reserveAfterAuthorization(ControlChallengeStore.CommitFence fence,ComponentGate.BusinessPermit permit,TaskJournal journal)throws Exception{
        if(fence==null)throw invalid();AdminProofVerifier.KeyProof proof=fence.proof();
        sourceLifetime.checkOrBind(permit);fence.checkCurrent();if(!initialized || journal==null)throw new IllegalStateException("Administrator/task journal not initialized");
        if(database.inTransaction())throw new IllegalStateException("Nested administrator transaction forbidden");
        journal.requireSameStore(database,gate,target,epoch);ContentKey key=contentKey(proof);
        if(!target.equals(key.target) || !epoch.equals(key.epoch))throw new SecurityException("Wrong native target/epoch");
        String[] args={key.principal,key.target,key.epoch,key.request};Reservation reservation;
        database.beginTransactionNonExclusive();
        try{
            sourceLifetime.checkOrBind(permit);fence.checkCurrent();
            try(Cursor c=database.query(TABLE,new String[]{"job_ref","content_sha256"},WHERE,args,null,null,null,"1")){
                if(c.moveToFirst()){
                    if(!MessageDigest.isEqual(key.content,c.getBlob(1)))throw new Conflict();
                    reservation=new Reservation(c.getString(0),true);
                }else{
                    try(Cursor count=database.rawQuery("SELECT COUNT(*) FROM admin_requests_v1",null)){if(!count.moveToFirst() || count.getLong(0)>=maxJobs)throw new ResourceLimit();}
                    byte[] id=new byte[16];random.nextBytes(id);String job="j_"+hex(id);
                    ContentValues values=new ContentValues();values.put("principal_fp",key.principal);values.put("target_fp",key.target);values.put("data_epoch",key.epoch);values.put("request_id",key.request);values.put("content_sha256",key.content);values.put("job_ref",job);
                    database.insertOrThrow(TABLE,null,values);reservation=new Reservation(job,false);
                }
            }
            journal.reserveInCurrentTransaction(reservation.job,bindingSha256(key),reservation.existing,permit);
            fence.checkCurrent();sourceLifetime.checkOrBind(permit);database.setTransactionSuccessful();
        }finally{database.endTransaction();} // commit failure is UNKNOWN to caller: challenge stays spent
        sourceLifetime.checkOrBind(permit);return reservation;
    }
    /** Fresh nonce/signature/grant/policy revisions are not immutable operation content. */
    public static ContentKey contentKey(AdminProofVerifier.KeyProof proof){
        if(proof==null)throw invalid();ControlEnvelope f=proof.request().frame();
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
            out.write("ReBrowser.Job.Content\0".getBytes(StandardCharsets.US_ASCII));
            for(ControlEnvelope.Field field:new ControlEnvelope.Field[]{ControlEnvelope.Field.ACTION_ID,ControlEnvelope.Field.SCOPE_REF,ControlEnvelope.Field.ARGS_BYTES}){byte[] value=f.bytes(field);out.writeInt(value.length);out.write(value);}
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return new ContentKey(proof.compiledPrincipal().spkiSha256(),hex(f.bytes(ControlEnvelope.Field.TARGET_INSTALL_SPKI_SHA256)),hex(f.bytes(ControlEnvelope.Field.DATA_EPOCH)),hex(f.bytes(ControlEnvelope.Field.REQUEST_ID)),digest);
        }catch(IOException impossible){throw new IllegalStateException("In-memory request encoding failed");}
        catch(NoSuchAlgorithmException unavailable){throw new IllegalStateException("SHA-256 unavailable");}
    }
    /** Fixed-width native key components plus original operation digest; no proof-lifetime fields. */
    private static byte[] bindingSha256(ContentKey key){
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update("ReBrowser.Job.Binding\0".getBytes(StandardCharsets.US_ASCII));
            for(String part:new String[]{key.principal,key.target,key.epoch,key.request})digest.update(part.getBytes(StandardCharsets.US_ASCII));
            return digest.digest(key.content);
        }catch(NoSuchAlgorithmException unavailable){throw new IllegalStateException("SHA-256 unavailable");}
    }
    private static String hex(byte[] value){char[] out=new char[value.length*2];String digits="0123456789abcdef";for(int i=0;i<value.length;i++){out[2*i]=digits.charAt((value[i]&255)>>>4);out[2*i+1]=digits.charAt(value[i]&15);}return new String(out);}
    private static byte[] fixed(byte[] value,int length){if(value==null || value.length!=length)throw invalid();return value.clone();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native idempotency input");}
}
