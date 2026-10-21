package io.github.rebrowser.outlook;

import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import io.github.rebrowser.crypto.CryptoEncoding;
import io.github.rebrowser.crypto.ObjectAead;
import io.github.rebrowser.data.NativeRecordCodec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Existing dedicated per-account native SQLite only. No path argument/open, schema creation,
 * migration, fresh-install inference, remote fetch, SQL from IPC or account-binding from a page.
 * Caller owns DB lifetime. Fixed APK schema SHA + exact sqlite_master/foreign-key/native-account
 * checks precede access. Native caller supplies genuine current domain/Actor/task policy fences.
 * Actual Android transactions/power-loss/latest-global-policy atomicity remain qualification gates. */
public final class MailDatabase {
    public interface NativeFence {void requireCurrent();}
    public interface Write<T>{T apply(Transaction transaction)throws Exception;}
    public static final class Summary {
        private final String key,subject,sender;private final long time,revision;
        private Summary(String key,String subject,String sender,long time,long revision){this.key=key;this.subject=subject;this.sender=sender;this.time=time;this.revision=revision;}
        public String officialItemKey(){return key;}public String subject(){return subject;}public String sender(){return sender;}public long receivedUtcMs(){return time;}public long localRevision(){return revision;}
    }
    public static final class Reservation {
        private final MailDatabase issuer;private final byte[] objectId;private boolean consumed,committed;
        private Reservation(MailDatabase issuer,byte[] objectId){this.issuer=issuer;this.objectId=objectId.clone();}
        public byte[] nativeObjectId(){return objectId.clone();}
        void requireDatabase(MailDatabase expected){issuer.scope.requireCurrent();if(issuer!=expected || !committed)throw denied();}
        /** Store uses this ONCE before encryption; never a public nonce/key or publication receipt. */
        void consumeFor(ObjectAead.WritePlan plan){issuer.scope.requireCurrent();if(!committed || consumed || plan==null || !MessageDigest.isEqual(objectId,plan.metadata().objectId()))throw denied();consumed=true;}
    }
    public final class Transaction {
        private final Thread owner=Thread.currentThread();private final NativeFence fence;private boolean active=true;private final List<Reservation> reservations=new ArrayList<>();private final List<MailObjectStore.Prepared> adopted=new ArrayList<>();
        private Transaction(NativeFence fence){this.fence=fence;}
        private void current(){if(!active || Thread.currentThread()!=owner || !db.inTransaction())throw denied();check(fence);}
        public Reservation reserveObject(ObjectAead.WritePlan nativePlan){current();if(nativePlan==null || reservations.size()>=100)throw denied();MailObjectFormat.Header h=MailObjectFormat.decode(nativePlan.metadata().headerBytes());if(!MessageDigest.isEqual(h.dataEpoch(),scope.dataEpoch()) || !MessageDigest.isEqual(h.accountId(),scope.accountId()))throw denied();ContentValues v=new ContentValues();v.put("object_id",h.objectId());v.put("use_state",0);db.insertOrThrow("object_reservations",null,v);Reservation reservation=new Reservation(MailDatabase.this,h.objectId());reservations.add(reservation);return reservation;}
        public void adoptPreparedRawObject(MailObjectStore.Prepared prepared){current();if(prepared==null || adopted.size()>=100)throw denied();prepared.claim(MailDatabase.this);MailObjectFormat.Header h=MailObjectFormat.decode(prepared.metadata.headerBytes());if(!MessageDigest.isEqual(h.accountId(),scope.accountId()) || !MessageDigest.isEqual(h.dataEpoch(),scope.dataEpoch()) || h.codec()!=MailObjectFormat.Codec.RAW)throw denied();ContentValues v=new ContentValues();v.put("object_id",h.objectId());v.put("original_digest",h.originalSha256());v.put("purpose",h.purpose().code);v.put("codec",h.codec().code);v.put("account_key_version",h.keyVersion());v.put("original_bytes",h.originalBytes());v.put("compressed_bytes",h.packedBytes());v.put("stored_bytes",h.storedBytes());v.put("storage_kind",prepared.inline==null?1:0);if(prepared.inline==null)v.putNull("inline_payload");else v.put("inline_payload",prepared.inline);if(prepared.fileKey==null)v.putNull("owned_file_key");else v.put("owned_file_key",prepared.fileKey);v.put("object_state",1);v.put("refcount",0);db.insertOrThrow("content_objects",null,v);adopted.add(prepared);}
        /** Only admitted native official-content producers call this, NEVER CandidatePage directly. */
        public void putSummary(String officialKey,String serverRevision,String subject,String sender,long receivedUtc,long expectedLocalRevision,long syncGeneration){
            current();text(officialKey,1024,false);text(serverRevision,4096,true);text(subject,2048,true);text(sender,1024,true);if(receivedUtc<0 || expectedLocalRevision<0 || expectedLocalRevision==Long.MAX_VALUE || syncGeneration<1)throw denied();
            ContentValues v=new ContentValues();v.put("server_revision",serverRevision);v.put("subject",subject);v.put("sender_summary",sender);v.put("received_millis",receivedUtc);v.put("sync_generation",syncGeneration);v.put("local_revision",expectedLocalRevision+1);
            if(expectedLocalRevision==0){v.put("flags",0);v.put("official_item_key",officialKey);db.insertOrThrow("mail_items",null,v);}else if(db.update("mail_items",v,"official_item_key=? AND local_revision=?",new String[]{officialKey,Long.toString(expectedLocalRevision)})!=1)throw denied();
        }
        public void commitCursor(String stream,String nativeOpaqueCursor,long expectedRevision,long newRevision){current();text(stream,64,false);text(nativeOpaqueCursor,4096,true);if(expectedRevision<0 || newRevision<=expectedRevision)throw denied();ContentValues v=new ContentValues();v.put("opaque_cursor",nativeOpaqueCursor);v.put("committed_revision",newRevision);v.put("account_binding_revision",scope.bindingRevision());v.put("data_epoch",scope.dataEpoch());
            boolean exists;try(Cursor c=db.query("sync_state",new String[]{"committed_revision"},"stream_key=?",new String[]{stream},null,null,null,"1")){exists=c.moveToFirst();if(exists && c.getLong(0)!=expectedRevision)throw denied();}
            if(exists){if(db.update("sync_state",v,"stream_key=? AND committed_revision=?",new String[]{stream,Long.toString(expectedRevision)})!=1)throw denied();}else{if(expectedRevision!=0)throw denied();v.put("stream_key",stream);db.insertOrThrow("sync_state",null,v);}
        }
        public int reconcileReferenceCounts(int maximumObjects){current();if(maximumObjects<1 || maximumObjects>100_000)throw denied();try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM content_objects",null)){if(!c.moveToFirst() || c.getLong(0)>maximumObjects)throw denied();}db.execSQL("UPDATE content_objects SET refcount=coalesce((SELECT actual_refs FROM actual_object_references WHERE actual_object_references.object_id=content_objects.object_id),0)");try(Cursor c=db.rawQuery("SELECT changes()",null)){if(!c.moveToFirst())throw denied();return c.getInt(0);}}
    }
    private static final String SCHEMA_SHA256="3372a3ac8bfb8079e0e9d9aaaff058aba2ecf1909a4dfc61c8fab85efc79ec28";
    private final SQLiteDatabase db;private final MailAccountCatalog.PersistenceScope scope;private final AtomicBoolean operation=new AtomicBoolean();
    private MailDatabase(SQLiteDatabase db,MailAccountCatalog.PersistenceScope scope){this.db=db;this.scope=scope;}
    public static MailDatabase openExisting(Application nativeApplication,SQLiteDatabase nativeDedicatedDatabase,MailAccountCatalog.PersistenceScope actualNativeScope,NativeFence policy)throws IOException{
        if(nativeApplication==null || nativeDedicatedDatabase==null || actualNativeScope==null || policy==null)throw new IllegalArgumentException("Existing native per-account DB required");io();actualNativeScope.requireCurrent();policy.requireCurrent();if(nativeDedicatedDatabase.inTransaction() || nativeDedicatedDatabase.getVersion()!=1)throw denied();nativeDedicatedDatabase.setForeignKeyConstraintsEnabled(true);Map<String,String> expected=loadFixedSchema(nativeApplication);Map<String,String> actual=new TreeMap<>();
        try(Cursor c=nativeDedicatedDatabase.rawQuery("SELECT type,name,CASE WHEN length(sql)<=65536 THEN sql ELSE NULL END FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%'",null)){while(c.moveToNext()){if(actual.size()>=128 || c.isNull(2))throw denied();actual.put(c.getString(0)+":"+c.getString(1),c.getString(2));}}
        if(!actual.equals(expected))throw new IOException("Unknown native mail schema");MailDatabase result=new MailDatabase(nativeDedicatedDatabase,actualNativeScope);result.check(policy);return result;
    }
    public <T>T writeAfterNativeAuthorization(NativeFence policy,Write<T> work)throws Exception{io();if(work==null)throw denied();begin(policy);Transaction transaction=new Transaction(policy);T value;try{check(policy);value=work.apply(transaction);check(policy);db.setTransactionSuccessful();}finally{transaction.active=false;try{db.endTransaction();}finally{operation.set(false);}}check(policy);for(Reservation reservation:transaction.reservations)reservation.committed=true;for(MailObjectStore.Prepared prepared:transaction.adopted)prepared.finishKnownDatabaseCommit();return value;} // Unknown commit never authorizes replay.
    public List<Summary> queryPage(NativeFence policy,int offset,int limit){io();if(offset<0 || offset>100_000 || limit<1 || limit>100)throw denied();if(!operation.compareAndSet(false,true))throw denied();try{check(policy);if(db.inTransaction())throw denied();List<Summary> rows=new ArrayList<>();String[] projection={"official_item_key","CASE WHEN length(subject)<=512 THEN subject ELSE NULL END","CASE WHEN length(sender_summary)<=256 THEN sender_summary ELSE NULL END","received_millis","local_revision"};try(Cursor c=db.query("mail_items",projection,null,null,null,null,"received_millis DESC,official_item_key",offset+","+limit)){while(c.moveToNext()){if(c.isNull(1) || c.isNull(2))throw denied();String key=text(c.getString(0),1024,false),subject=text(c.getString(1),2048,true),sender=text(c.getString(2),1024,true);if(c.getLong(3)<0 || c.getLong(4)<1)throw denied();rows.add(new Summary(key,subject,sender,c.getLong(3),c.getLong(4)));}}check(policy);return Collections.unmodifiableList(rows);}finally{operation.set(false);}}
    public void requireCurrentScope(NativeFence policy){io();check(policy);}
    private void begin(NativeFence policy){check(policy);if(!operation.compareAndSet(false,true))throw denied();try{if(db.inTransaction())throw denied();db.beginTransactionNonExclusive();}catch(RuntimeException failure){operation.set(false);throw failure;}}
    private void check(NativeFence policy){if(policy==null)throw denied();scope.requireCurrent();policy.requireCurrent();try(Cursor c=db.rawQuery("SELECT data_epoch,native_account_id,binding_revision FROM account_meta WHERE singleton=1 AND typeof(data_epoch)='blob' AND length(data_epoch)=16 AND typeof(native_account_id)='blob' AND length(native_account_id)=16",null)){if(!c.moveToFirst() || !MessageDigest.isEqual(c.getBlob(0),scope.dataEpoch()) || !MessageDigest.isEqual(c.getBlob(1),scope.accountId()) || c.getLong(2)!=scope.bindingRevision())throw denied();}try(Cursor c=db.rawQuery("PRAGMA foreign_keys",null)){if(!c.moveToFirst() || c.getInt(0)!=1)throw denied();}scope.requireCurrent();policy.requireCurrent();}
    private static Map<String,String> loadFixedSchema(Application application)throws IOException{
        byte[] raw;try(InputStream input=application.getAssets().open("sql/mail-v1.sql");ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] b=new byte[4096];int zero=0;for(;;){int n=input.read(b);if(n<0)break;if(n==0){if(++zero>16)throw new IOException("Schema input stalled");continue;}zero=0;if(n>65536-output.size())throw new IOException("Schema source too large");output.write(b,0,n);}raw=output.toByteArray();}if(!SCHEMA_SHA256.equals(CryptoEncoding.sha256Hex(raw)))throw new IOException("Unrecognized APK mail schema source");String source=new String(raw,StandardCharsets.UTF_8);Map<String,String> definitions=new TreeMap<>();for(String block:source.split("-- rebrowser:statement",-1)){StringBuilder clean=new StringBuilder();for(String line:block.split("\\n"))if(!line.trim().startsWith("--"))clean.append(line).append('\n');String sql=clean.toString().trim();if(!sql.startsWith("CREATE "))continue;while(sql.endsWith(";"))sql=sql.substring(0,sql.length()-1).trim();java.util.regex.Matcher m=java.util.regex.Pattern.compile("^CREATE (?:UNIQUE )?(TABLE|INDEX|TRIGGER|VIEW) ([a-z_]+)\\b").matcher(sql);if(!m.find() || definitions.put(m.group(1).toLowerCase(Locale.ROOT)+":"+m.group(2),sql)!=null)throw new IOException("Invalid fixed schema definition");}if(definitions.size()!=48)throw new IOException("Incomplete fixed schema");return definitions;
    }
    private static String text(String s,int max,boolean empty){return NativeRecordCodec.validateText(s,max,empty);}
    private static void io(){if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Native mail DB owned IO lane required");}
    private static SecurityException denied(){return new SecurityException("Native mail DB scope/revision unavailable");}
}
