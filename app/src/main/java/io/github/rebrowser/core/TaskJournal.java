package io.github.rebrowser.core;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import io.github.rebrowser.bootstrap.ComponentGate;
import java.security.MessageDigest;
import java.security.SecureRandom;
import io.github.rebrowser.core.TaskSnapshot.Phase;
import io.github.rebrowser.core.TaskSnapshot.Work;
import io.github.rebrowser.core.TaskSnapshot.Effect;

/**
 * Native durable work/dispatch ledger in the dedicated v2 admin/task DB. No DB open,
 * effects, automatic retries, result publication or actual-resource-slot release here.
 * Native coordinator must serialize CURRENT action policy/grant/target with admission,
 * and recheck before external dispatch. Commit an intent BEFORE handing a command to
 * any external route. A ticket is an execution-owner capability, never an IPC input.
 * Source lock may prevent persistence; original owners must still settle actual work
 * in TaskCoordinator memory without publishing stale data. DB errors never prove stop.
 * SQLite owns writer serialization. No Java monitor is held while waiting for its write
 * lock: the idempotency bridge already holds that lock when registering a task.
 */
public final class TaskJournal {
    private static final String TABLE="task_journal_v1";
    private static final String RUN=newToken(); // process/classloader lifetime, NOT per journal instance
    private static final String CREATE="CREATE TABLE task_journal_v1 ("
        +"job_ref TEXT PRIMARY KEY NOT NULL CHECK(typeof(job_ref)='text' AND length(job_ref)=34 AND substr(job_ref,1,2)='j_' AND substr(job_ref,3) NOT GLOB '*[^0-9a-f]*'),"
        +"target_fp TEXT NOT NULL CHECK(typeof(target_fp)='text' AND length(target_fp)=64 AND target_fp NOT GLOB '*[^0-9a-f]*'),"
        +"data_epoch TEXT NOT NULL CHECK(typeof(data_epoch)='text' AND length(data_epoch)=32 AND data_epoch NOT GLOB '*[^0-9a-f]*'),"
        +"binding_sha256 BLOB NOT NULL CHECK(typeof(binding_sha256)='blob' AND length(binding_sha256)=32),"
        +"phase INTEGER NOT NULL CHECK(typeof(phase)='integer' AND phase BETWEEN 0 AND 6),"
        +"work INTEGER NOT NULL CHECK(typeof(work)='integer' AND work BETWEEN 0 AND 3),"
        +"effect INTEGER NOT NULL CHECK(typeof(effect)='integer' AND effect BETWEEN 0 AND 3),"
        +"owner_run TEXT NOT NULL CHECK(typeof(owner_run)='text' AND (owner_run='' OR (length(owner_run)=32 AND owner_run NOT GLOB '*[^0-9a-f]*'))),"
        +"execution_token TEXT NOT NULL CHECK(typeof(execution_token)='text' AND (execution_token='' OR (length(execution_token)=32 AND execution_token NOT GLOB '*[^0-9a-f]*'))),"
        +"revision INTEGER NOT NULL CHECK(typeof(revision)='integer' AND revision>=0),"
        +"CHECK((phase=0 AND work=0 AND effect=0 AND owner_run='' AND execution_token='' AND revision=0)"
        +" OR (phase=1 AND work=1 AND effect=0 AND owner_run<>'' AND execution_token<>'')"
        +" OR (phase=2 AND work=1 AND effect=1 AND owner_run<>'' AND execution_token<>'')"
        +" OR (phase=3 AND work IN(1,2,3) AND effect IN(2,3) AND owner_run<>'' AND execution_token<>'')"
        +" OR (phase=4 AND work=2 AND effect=0 AND ((owner_run='' AND execution_token='') OR (owner_run<>'' AND execution_token<>'')))"
        +" OR (phase=5 AND work IN(1,2,3) AND effect=1 AND owner_run<>'' AND execution_token<>'')"
        +" OR (phase=6 AND work=3 AND effect=0 AND owner_run<>'' AND execution_token<>'')))";
    private static final String INSERT_GUARD="CREATE TRIGGER task_no_replace BEFORE INSERT ON task_journal_v1 WHEN NEW.phase<>0 OR EXISTS(SELECT 1 FROM task_journal_v1 WHERE job_ref=NEW.job_ref) BEGIN SELECT RAISE(ABORT,'INVALID_TASK_RESERVATION'); END";
    private static final String DELETE_GUARD="CREATE TRIGGER task_no_delete BEFORE DELETE ON task_journal_v1 BEGIN SELECT RAISE(ABORT,'TASK_REPLAY_FORBIDDEN'); END";
    private static final String UPDATE_GUARD="CREATE TRIGGER task_monotonic BEFORE UPDATE ON task_journal_v1 WHEN "
        +"NEW.job_ref IS NOT OLD.job_ref OR NEW.target_fp IS NOT OLD.target_fp OR NEW.data_epoch IS NOT OLD.data_epoch OR NEW.binding_sha256 IS NOT OLD.binding_sha256"
        +" OR NEW.revision<>OLD.revision+1 OR typeof(OLD.revision+1)<>'integer'"
        +" OR (OLD.phase<>0 AND (NEW.owner_run IS NOT OLD.owner_run OR NEW.execution_token IS NOT OLD.execution_token))"
        +" OR (OLD.work=2 AND NEW.work<>2) OR (OLD.work=3 AND NEW.work NOT IN(2,3))"
        +" OR (OLD.phase=3 AND (NEW.phase<>3 OR NEW.effect<>OLD.effect))"
        +" OR NOT ((OLD.phase=0 AND NEW.phase IN(1,4)) OR (OLD.phase=1 AND NEW.phase IN(2,4,6))"
        +" OR (OLD.phase=2 AND NEW.phase IN(3,5)) OR (OLD.phase=3 AND NEW.phase=3)"
        +" OR (OLD.phase=5 AND NEW.phase IN(3,5)) OR (OLD.phase=6 AND NEW.phase=6))"
        +" BEGIN SELECT RAISE(ABORT,'NON_MONOTONIC_TASK'); END";
    private static final String RECOVER="UPDATE task_journal_v1 SET phase=CASE phase WHEN 1 THEN 6 WHEN 2 THEN 5 ELSE phase END,work=3,revision=revision+1 WHERE target_fp=? AND data_epoch=? AND work=1 AND owner_run<>?";
    private final SQLiteDatabase db;
    private final ComponentGate gate;
    private final String target,epoch;
    private volatile boolean initialized;

    /** Safe to expose only after fresh native result-scope authorization. No owner tokens. */
    public static final class Record {
        private final String job;
        private final byte[] binding;
        private final Phase phase;
        private final Work work;
        private final Effect effect;
        private final long revision;
        private Record(String job,byte[] binding,Phase phase,Work work,Effect effect,long revision){this.job=job;this.binding=binding.clone();this.phase=phase;this.work=work;this.effect=effect;this.revision=revision;}
        public String jobRef(){return job;}
        public byte[] bindingSha256(){return binding.clone();}
        public long revision(){return revision;}
        /** Publication remains suppressed until caller revalidates the current domain fence. */
        public TaskSnapshot snapshot(){return TaskSnapshot.fromNativeEvidence(phase,work,effect,TaskSnapshot.Durability.COMMITTED,TaskSnapshot.ClientWait.WAITING,false,false);}
    }
    /** Do not serialize, reconstruct from DB, or hand to watchers/UI/IPC. */
    public static final class ExecutionTicket {
        private final TaskJournal issuer;
        private final String job,token;
        private ExecutionTicket(TaskJournal issuer,String job,String token){this.issuer=issuer;this.job=job;this.token=token;}
        public String jobRef(){return job;}
    }
    public TaskJournal(SQLiteDatabase nativeDedicatedDatabase,ComponentGate gate,byte[] targetInstallSpkiSha256,byte[] dataEpoch){
        if(nativeDedicatedDatabase==null || gate==null)throw invalid();
        db=nativeDedicatedDatabase;this.gate=gate;target=hex(fixed(targetInstallSpkiSha256,32));epoch=hex(fixed(dataEpoch,16));
    }
    public static String[] schemaStatements(){return new String[]{CREATE,INSERT_GUARD,DELETE_GUARD,UPDATE_GUARD};}
    public static String lostProcessRecoveryStatement(){return RECOVER;}
    public void initialize(ComponentGate.BusinessPermit permit){
        initialized=false;begin(permit,false);
        try{
            if(db.getVersion()!=1)throw new IllegalStateException("Initialize the native administrator DB first");
            boolean exists;try(Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{TABLE})){exists=c.moveToFirst();}
            if(!exists)for(String sql:schemaStatements())db.execSQL(sql);
            requireSchema("table",TABLE,CREATE);requireSchema("trigger","task_no_replace",INSERT_GUARD);requireSchema("trigger","task_no_delete",DELETE_GUARD);requireSchema("trigger","task_monotonic",UPDATE_GUARD);
            commit(permit);
        }finally{db.endTransaction();}
        initialized=true;
    }
    private void requireSchema(String type,String name,String expected){try(Cursor c=db.rawQuery("SELECT sql FROM sqlite_master WHERE type=? AND name=?",new String[]{type,name})){if(!c.moveToFirst() || !expected.equals(c.getString(0)))throw new IllegalStateException("Unknown task journal schema");}}
    /** Internal bridge only. Both ledgers must use this exact handle/gate/native binding. */
    public void requireSameStore(SQLiteDatabase database,ComponentGate sourceGate,String targetFingerprint,String dataEpoch){
        if(!initialized || db!=database || gate!=sourceGate || !target.equals(targetFingerprint) || !epoch.equals(dataEpoch))throw new IllegalStateException("Task journal backing store mismatch");
    }
    /** Only inside native coordinator's reservation transaction; NOT a durable receipt yet. */
    public void reserveInCurrentTransaction(String job,byte[] bindingSha256,boolean existingJob,ComponentGate.BusinessPermit permit){
        gate.checkCurrent(permit);ready();if(!db.inTransaction())throw new IllegalStateException("Task reservation transaction required");validateJob(job);byte[] binding=fixed(bindingSha256,32);
        Record old=read(job);
        if(existingJob){if(old==null || !MessageDigest.isEqual(old.binding,binding))throw new IllegalStateException("Missing or conflicting original task");return;}
        if(old!=null)throw new IllegalStateException("Task already reserved");
        ContentValues v=new ContentValues();v.put("job_ref",job);v.put("target_fp",target);v.put("data_epoch",epoch);v.put("binding_sha256",binding);v.put("phase",0);v.put("work",0);v.put("effect",0);v.put("owner_run","");v.put("execution_token","");v.put("revision",0);db.insertOrThrow(TABLE,null,v);
    }
    /** Starts the whole owned execution chain, including preparatory workers; no effects yet. */
    public ExecutionTicket startWorkAfterAuthorization(String job,ComponentGate.BusinessPermit permit){
        validateJob(job);String token=newToken();begin(permit,true);
        try{ContentValues v=state(1,1,0);v.put("owner_run",RUN);v.put("execution_token",token);update(job,v,"phase=0",null);commit(permit);}finally{db.endTransaction();}
        return new ExecutionTicket(this,job,token);
    }
    /** Must return successfully BEFORE any selected external route receives the command. */
    public void commitDispatchIntentAfterAuthorization(ExecutionTicket ticket,ComponentGate.BusinessPermit permit){
        own(ticket);begin(permit,true);
        try{updateOwned(ticket,state(2,1,1),"phase=1 AND work=1");commit(permit);}finally{db.endTransaction();}
    }
    /** First trusted domain observation wins. Failure may include partial external effects. */
    public void observe(ExecutionTicket ticket,Effect observation,ComponentGate.BusinessPermit permit){
        own(ticket);if(observation!=Effect.OBSERVED_SUCCESS && observation!=Effect.OBSERVED_FAILURE)throw invalid();begin(permit,true);
        try{Record before=requireOwned(ticket);if(before.phase!=Phase.DISPATCH_INTENT && before.phase!=Phase.OUTCOME_UNKNOWN)throw new IllegalStateException("Effect outcome already fixed or no dispatch intent");updateOwned(ticket,state(3,before.work.code(),observation.code()),"phase IN(2,5)");commit(permit);}finally{db.endTransaction();}
    }
    /** Trusted route lost outcome certainty; NOT a client deadline and NOT actual end. */
    public void markOutcomeUnknown(ExecutionTicket ticket,ComponentGate.BusinessPermit permit){
        own(ticket);begin(permit,true);
        try{Record before=requireOwned(ticket);if(before.phase!=Phase.DISPATCH_INTENT)throw new IllegalStateException("No pending dispatch outcome");updateOwned(ticket,state(5,before.work.code(),1),"phase=2");commit(permit);}finally{db.endTransaction();}
    }
    /** Whole chain has REALLY ended, not Future.done/interrupt/port close/timeout. */
    public void actualWorkEnded(ExecutionTicket ticket,ComponentGate.BusinessPermit permit){
        own(ticket);begin(permit,true);
        try{
            Record before=requireOwned(ticket);if(before.work!=Work.RUNNING)throw new IllegalStateException("Actual end already recorded or owner lost");
            int phase=before.phase==Phase.WORK_STARTED?4:before.phase==Phase.DISPATCH_INTENT?5:before.phase.code();
            updateOwned(ticket,state(phase,2,before.effect.code()),"work=1");commit(permit);
        }finally{db.endTransaction();}
    }
    /** Queued task never handed to a worker: atomic with startWork's RESERVED transition. */
    public void endQueuedWithoutStartingAfterAuthorization(String job,ComponentGate.BusinessPermit permit){
        validateJob(job);begin(permit,true);try{update(job,state(4,2,0),"phase=0",null);commit(permit);}finally{db.endTransaction();}
    }
    /** No replay/slot release. Old running chain becomes UNKNOWN; observed outcome survives. */
    public int reconcileLostProcess(ComponentGate.BusinessPermit permit){
        begin(permit,true);int count;
        try{
            db.execSQL(RECOVER,new Object[]{target,epoch,RUN});
            try(Cursor c=db.rawQuery("SELECT changes()",null)){if(!c.moveToFirst())throw new IllegalStateException("Missing journal update count");count=c.getInt(0);}commit(permit);
        }finally{db.endTransaction();}
        return count;
    }
    public Record queryAfterAuthorization(String job,ComponentGate.BusinessPermit permit){
        validateJob(job);gate.checkCurrent(permit);ready();if(db.inTransaction())throw new IllegalStateException("Uncommitted task view forbidden");Record r=read(job);gate.checkCurrent(permit);return r;
    }
    private Record read(String job){
        try(Cursor c=db.query(TABLE,new String[]{"binding_sha256","phase","work","effect","revision"},"job_ref=? AND target_fp=? AND data_epoch=?",new String[]{job,target,epoch},null,null,null,"1")){
            return c.moveToFirst()?new Record(job,c.getBlob(0),Phase.fromCode(c.getInt(1)),Work.fromCode(c.getInt(2)),Effect.fromCode(c.getInt(3)),c.getLong(4)):null;
        }
    }
    private Record requireOwned(ExecutionTicket t){
        try(Cursor c=db.rawQuery("SELECT 1 FROM task_journal_v1 WHERE job_ref=? AND target_fp=? AND data_epoch=? AND owner_run=? AND execution_token=?",new String[]{t.job,target,epoch,RUN,t.token})){if(!c.moveToFirst())throw new IllegalStateException("Lost execution owner");}
        Record r=read(t.job);if(r==null)throw new IllegalStateException("Missing task");return r;
    }
    private void updateOwned(ExecutionTicket t,ContentValues values,String condition){
        update(t.job,values,"owner_run=? AND execution_token=? AND "+condition,new String[]{RUN,t.token});
    }
    private void update(String job,ContentValues values,String condition,String[] extra){
        // revision compare/update occurs under this SQLite write transaction; never replace rows.
        Record old=read(job);if(old==null || old.revision==Long.MAX_VALUE)throw new IllegalStateException("Missing task or exhausted revision");values.put("revision",old.revision+1);
        String[] args=new String[3+(extra==null?0:extra.length)];args[0]=job;args[1]=target;args[2]=epoch;if(extra!=null)System.arraycopy(extra,0,args,3,extra.length);
        if(db.update(TABLE,values,"job_ref=? AND target_fp=? AND data_epoch=? AND "+condition,args)!=1)throw new IllegalStateException("Stale task transition");
    }
    private void begin(ComponentGate.BusinessPermit permit,boolean requireInitialized){gate.checkCurrent(permit);if(requireInitialized)ready();if(db.inTransaction())throw new IllegalStateException("Nested journal transaction forbidden");db.beginTransactionNonExclusive();}
    private void commit(ComponentGate.BusinessPermit permit){gate.checkCurrent(permit);db.setTransactionSuccessful();}
    private void ready(){if(!initialized)throw new IllegalStateException("Task journal not initialized");}
    private void own(ExecutionTicket t){if(t==null || t.issuer!=this)throw new SecurityException("Wrong native execution ticket");}
    private static ContentValues state(int phase,int work,int effect){ContentValues v=new ContentValues();v.put("phase",phase);v.put("work",work);v.put("effect",effect);return v;}
    private static void validateJob(String job){if(job==null || !job.matches("j_[0-9a-f]{32}"))throw invalid();}
    private static String newToken(){byte[] b=new byte[16];new SecureRandom().nextBytes(b);return hex(b);}
    private static String hex(byte[] b){StringBuilder out=new StringBuilder(b.length*2);for(byte v:b){out.append("0123456789abcdef".charAt((v&255)>>>4));out.append("0123456789abcdef".charAt(v&15));}return out.toString();}
    private static byte[] fixed(byte[] b,int n){if(b==null || b.length!=n)throw invalid();return b.clone();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native task journal input");}
}
