package io.github.rebrowser.data;

import android.app.Application;
import android.os.Looper;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.ProcessRole;
import io.github.rebrowser.core.ResourceBudgets;
import io.github.rebrowser.crypto.DataKeyVault;
import io.github.rebrowser.crypto.PrivateRecordSealer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Synchronous bounded encrypted record IO in an EXISTING native no-backup epoch directory.
 * NO directory/key seed/repair, arbitrary path, export, deletion, retry or private-session
 * checkpoint policy. Native domain authorization must precede reference creation and access.
 * File force -> fresh source fence -> ATOMIC_MOVE -> directory force/readback, or unknown.
 * No Android filesystem/power-loss or complete writer/source-cutover qualification claimed.
 */
public final class PrivateRecordStore {
    public static final String EPOCHS_DIRECTORY="owned-epochs-v1",RECORDS_DIRECTORY="private-records-v1";
    public static final int MAX_RECORDS=3201; // 64workspaces *50tabs + one retained browser catalog
    public static final long MAX_OWNED_DISK_BYTES=128L*1024*1024;
    public enum Kind { MISSING, CURRENT, UNFINISHED_WRITE }
    public static final class Reference {
        private final PrivateRecordStore issuer;private final PrivateRecordSealer.Purpose purpose;private final byte[] scope;private final String name;
        private Reference(PrivateRecordStore issuer,PrivateRecordSealer.Purpose purpose,byte[] scope){this.issuer=issuer;this.purpose=purpose;this.scope=scope;name="p"+purpose.code()+"_"+hex(scope)+".v1";}
        public PrivateRecordSealer.Purpose purpose(){return purpose;}
        /** Metadata identifier only, not a path, source/actor grant or serialized target authority. */
        public String opaqueRef(){return name;}
    }
    /** Exact issuer/ref/disk digest CAS stamp; metadata is NOT consent, latest or durability proof. */
    public static final class ReadStamp {
        private final PrivateRecordStore issuer;private final Reference reference;private final Kind kind;private final long revision;private final byte[] digest;
        private ReadStamp(PrivateRecordStore issuer,Reference ref,Kind kind,long revision,byte[] digest){this.issuer=issuer;reference=ref;this.kind=kind;this.revision=revision;this.digest=digest;}
        public Kind kind(){return kind;}public long revision(){return revision;}
    }
    /** Caller owns sensitive plaintext lifetime; close scrubs this copy, not all JVM/provider copies. */
    public static final class ReadRecord implements AutoCloseable {
        private final ReadStamp stamp;private byte[] plaintext;private ResourceBudgets.Allocation credit;
        private ReadRecord(ReadStamp stamp,byte[] plaintext,ResourceBudgets.Allocation credit){this.stamp=stamp;this.plaintext=plaintext;this.credit=credit;}
        public ReadStamp stamp(){return stamp;}
        /** Caller separately owns/budgets each returned copy; close only clears this record's buffer. */
        public synchronized byte[] plaintextCopy(){if(plaintext==null)throw new IllegalStateException("No owned private plaintext");return plaintext.clone();}
        @Override public synchronized void close(){if(plaintext!=null){Arrays.fill(plaintext,(byte)0);plaintext=null;}if(credit!=null){credit.releaseAfterOwnedAllocationFreed();credit=null;}}
    }
    private final ComponentGate gate;private final ComponentGate.ResourcePin lifetime;private final byte[] epoch;private final DataKeyVault vault;
    private final Path[] directories;private final Object[] directoryKeys;private final Path directory,lockPath;private Object lockKey;
    private final boolean nativePlatform;private final AtomicBoolean operation=new AtomicBoolean();
    private final ResourceBudgets.ByteBudget plaintextBudget=new ResourceBudgets.ByteBudget(4L*1024*1024,1);
    private final java.util.Map<String,Reference> references=new java.util.LinkedHashMap<>();
    private PrivateRecordStore(Path[] nativeDirectories,ComponentGate gate,byte[] nativeEpoch,DataKeyVault vault,boolean nativePlatform)throws IOException {
        if(nativeDirectories==null || nativeDirectories.length<1 || gate==null || nativeEpoch==null || nativeEpoch.length!=16 || vault==null)throw invalid();
        this.gate=gate;lifetime=gate.newResourcePin();epoch=nativeEpoch.clone();this.vault=vault;this.nativePlatform=nativePlatform;directories=nativeDirectories.clone();directoryKeys=new Object[directories.length];
        for(int i=0;i<directories.length;i++){if(directories[i]==null || !directories[i].isAbsolute())throw invalid();directories[i]=directories[i].normalize();if(i>0 && !directories[i].getParent().equals(directories[i-1]))throw invalid();BasicFileAttributes a=attrs(directories[i]);if(!a.isDirectory() || a.fileKey()==null)throw new IOException("Existing identifiable private epoch directories required");directoryKeys[i]=a.fileKey();}
        directory=directories[directories.length-1];lockPath=directory.resolve(".private-records.lock");
    }
    public static PrivateRecordStore openExisting(Application application,ComponentGate gate,byte[] nativeEpoch,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException {
        if(application==null || gate==null || nativeEpoch==null || nativeEpoch.length!=16)throw invalid();ioThread();byte[] epoch=nativeEpoch.clone();
        if(ProcessRole.classifyOwnApplication(application.getPackageName(),Application.getProcessName()).startupRoute()!=ProcessRole.StartupRoute.MAIN_NATIVE_OWNER)throw new SecurityException("Primary native private record owner required");
        gate.requireDataEpoch(permit,epoch);ComponentGate.ResourcePin pin=gate.newResourcePin();pin.checkEpochOrBind(permit,epoch);
        Path noBackup=application.getNoBackupFilesDir().toPath(),epochs=noBackup.resolve(EPOCHS_DIRECTORY),own=epochs.resolve(hex(epoch)),records=own.resolve(RECORDS_DIRECTORY);
        DataKeyVault vault=DataKeyVault.openExisting(application,gate,epoch,permit);
        PrivateRecordStore result=new PrivateRecordStore(new Path[]{noBackup,epochs,own,records},gate,epoch,vault,true);pin.checkEpochOrBind(permit,epoch);result.check(permit);return result;
    }
    public synchronized Reference reference(PrivateRecordSealer.Purpose purpose,byte[] nativeResolvedScope,ComponentGate.BusinessPermit permit){check(permit);if(purpose==null || nativeResolvedScope==null || nativeResolvedScope.length!=16)throw invalid();Reference proposed=new Reference(this,purpose,nativeResolvedScope.clone());Reference known=references.get(proposed.name);if(known!=null)return known;if(references.size()>=MAX_RECORDS)throw new IllegalStateException("Private record reference budget reached");references.put(proposed.name,proposed);return proposed;}
    public void requireBindings(ComponentGate expectedGate,byte[] nativeEpoch,ComponentGate.BusinessPermit permit){if(expectedGate!=gate || !Arrays.equals(epoch,nativeEpoch))throw invalid();check(permit);}
    public ReadRecord read(Reference ref,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException {
        begin(permit);ReadRecord result=null;ResourceBudgets.Allocation credit=null;try{own(ref);try(Locked held=lock()){
            check(permit);Raw raw=raw(ref);check(permit);if(raw.kind!=Kind.CURRENT){result=new ReadRecord(stamp(ref,raw,0),null,null);return result;}
            credit=plaintextBudget.tryReserve(ref.purpose.maximumPlaintextBytes());if(credit==null)throw new IllegalStateException("Native private plaintext still owned");
            long revision=DataKeyVault.unverifiedRevision(raw.bytes);PrivateRecordSealer.Binding binding=binding(ref,revision);byte[] plaintext=vault.open(binding,raw.bytes,permit);
            try{requireDirectories();check(permit);result=new ReadRecord(stamp(ref,raw,revision),plaintext,credit);credit=null;plaintext=null;return result;}
            finally{if(plaintext!=null)Arrays.fill(plaintext,(byte)0);}
        }}catch(IOException|GeneralSecurityException|RuntimeException|Error failure){if(result!=null)result.close();if(credit!=null)credit.releaseAfterOwnedAllocationFreed();throw failure;}finally{operation.set(false);}
    }
    /** Explicit native domain write, NOT automatic restore/repair. No success on unknown commit. */
    public ReadStamp write(ReadStamp expected,byte[] plaintext,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException {
        begin(permit);try{
            if(expected==null || expected.issuer!=this || expected.kind==Kind.UNFINISHED_WRITE)throw invalid();Reference ref=expected.reference;own(ref);
            if(plaintext==null || plaintext.length>ref.purpose.maximumPlaintextBytes())throw invalid();
            try(Locked held=lock()){
                check(permit);compare(expected,raw(ref));long revision=expected.kind==Kind.MISSING?1:next(expected.revision);
                byte[] encrypted=vault.seal(binding(ref,revision),plaintext,permit);checkDiskAdmission(ref,encrypted.length);check(permit);Path target=directory.resolve(ref.name),stage=directory.resolve(ref.name+".pending");
                try(FileChannel file=FileChannel.open(stage,java.util.Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))){
                    BasicFileAttributes a=attrs(stage);if(!a.isRegularFile() || a.fileKey()==null)throw new IOException("Unsafe private record stage");
                    ByteBuffer buffer=ByteBuffer.wrap(encrypted);int zeros=0;while(buffer.hasRemaining()){int written=file.write(buffer);if(written==0){if(++zeros>16)throw new IOException("Private record stage stalled");}else zeros=0;}file.force(true);
                    BasicFileAttributes after=attrs(stage);if(!a.fileKey().equals(after.fileKey()) || after.size()!=encrypted.length)throw new IOException("Private record stage changed");
                }
                requireDirectories();check(permit);compare(expected,rawTarget(ref));requireDirectories();check(permit);
                Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                try(FileChannel parent=FileChannel.open(directory,StandardOpenOption.READ)){parent.force(true);}
                requireDirectories();Raw current=raw(ref);if(current.kind!=Kind.CURRENT || !MessageDigest.isEqual(digest(encrypted),digest(current.bytes)))throw new IOException("Private record commit readback mismatch");check(permit);
                return stamp(ref,current,revision);
            }
        }finally{operation.set(false);} // failure preserves uncertain stage/target; NEVER auto-clean/retry
    }
    /** Observation only. Zero alone is NOT all-process/SDK/key/erasure quiescence proof. */
    public boolean hasOwnedIoInFlight(){return operation.get();}
    private void begin(ComponentGate.BusinessPermit permit){check(permit);if(!operation.compareAndSet(false,true))throw new IllegalStateException("Private record IO already owned");try{check(permit);}catch(RuntimeException failure){operation.set(false);throw failure;}}
    private void check(ComponentGate.BusinessPermit permit){if(nativePlatform)ioThread();lifetime.checkEpochOrBind(permit,epoch);vault.requireBindings(gate,epoch,permit);}
    private void own(Reference ref){if(ref==null || ref.issuer!=this)throw invalid();}
    private PrivateRecordSealer.Binding binding(Reference ref,long revision){return new PrivateRecordSealer.Binding(ref.purpose,epoch,ref.scope,revision);}
    private void checkDiskAdmission(Reference ref,int stageBytes)throws IOException {
        requireDirectories();long used=0;java.util.Set<String> names=new java.util.HashSet<>();int nodes=0;
        try(java.nio.file.DirectoryStream<Path> files=Files.newDirectoryStream(directory)){
            for(Path file:files){if(++nodes>MAX_RECORDS*2+1)throw new IOException("Private record object budget reached");String name=file.getFileName().toString();BasicFileAttributes a=attrs(file);if(!a.isRegularFile() || a.fileKey()==null)throw new IOException("Unknown private record directory object");
                if(name.equals(".private-records.lock"))continue;
                if(!name.matches("p[12]_[0-9a-f]{32}\\.v1(\\.pending)?"))throw new IOException("Unexpected private record namespace object");
                names.add(name.endsWith(".pending")?name.substring(0,name.length()-8):name);if(names.size()>MAX_RECORDS || a.size()<0 || a.size()>MAX_OWNED_DISK_BYTES-used)throw new IOException("Private record disk budget reached");used+=a.size();
            }
        }
        if(!names.contains(ref.name) && names.size()>=MAX_RECORDS)throw new IOException("Private record object budget reached");
        if(stageBytes>MAX_OWNED_DISK_BYTES-used)throw new IOException("Private record stage disk budget reached");
    }
    private ReadStamp stamp(Reference ref,Raw raw,long revision){return new ReadStamp(this,ref,raw.kind,revision,raw.bytes==null?null:digest(raw.bytes));}
    private void compare(ReadStamp expected,Raw current)throws IOException {if(expected.kind!=current.kind || !Arrays.equals(expected.digest,current.bytes==null?null:digest(current.bytes)))throw new IOException("Private record compare-and-swap conflict");}
    private Raw raw(Reference ref)throws IOException {requireDirectories();if(exists(directory.resolve(ref.name+".pending")))return new Raw(Kind.UNFINISHED_WRITE,null);return rawTarget(ref);}
    private Raw rawTarget(Reference ref)throws IOException {byte[] bytes=readBounded(directory.resolve(ref.name),ref.purpose.maximumPlaintextBytes()+DataKeyVault.ENVELOPE_OVERHEAD);return new Raw(bytes==null?Kind.MISSING:Kind.CURRENT,bytes);}
    private static final class Raw {final Kind kind;final byte[] bytes;Raw(Kind kind,byte[] bytes){this.kind=kind;this.bytes=bytes;}}
    private byte[] readBounded(Path path,int maximum)throws IOException {
        BasicFileAttributes before;try{before=attrs(path);}catch(NoSuchFileException missing){return null;}
        if(!before.isRegularFile() || before.fileKey()==null || before.size()<DataKeyVault.ENVELOPE_OVERHEAD || before.size()>maximum)throw new IOException("Unsafe or oversized private record");
        byte[] bytes=new byte[(int)before.size()];
        try(FileChannel file=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer buffer=ByteBuffer.wrap(bytes);int zeros=0;while(buffer.hasRemaining()){int n=file.read(buffer);if(n<0)throw new IOException("Truncated private record");if(n==0){if(++zeros>16)throw new IOException("Private record read stalled");}else zeros=0;}
            if(file.read(ByteBuffer.allocate(1))!=-1)throw new IOException("Private record length changed");
        }
        BasicFileAttributes after=attrs(path);if(!before.fileKey().equals(after.fileKey()) || before.size()!=after.size())throw new IOException("Private record file changed");return bytes;
    }
    private Locked lock()throws IOException {
        requireDirectories();
        if(!exists(lockPath)){try(FileChannel ignored=FileChannel.open(lockPath,java.util.Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))){}catch(java.nio.file.FileAlreadyExistsException raced){/* validate exact object below */}}
        BasicFileAttributes before=attrs(lockPath);if(!before.isRegularFile() || before.fileKey()==null || before.size()!=0)throw new IOException("Unsafe private record lock");
        if(lockKey==null)lockKey=before.fileKey();else if(!lockKey.equals(before.fileKey()))throw new IOException("Private record lock replaced");
        FileChannel channel=FileChannel.open(lockPath,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);FileLock held=null;
        try{try{held=channel.tryLock();}catch(OverlappingFileLockException busy){throw new IOException("Private record lock busy",busy);}if(held==null)throw new IOException("Private record lock busy");if(!before.fileKey().equals(attrs(lockPath).fileKey()))throw new IOException("Private record lock changed");requireDirectories();return new Locked(channel,held);}
        catch(IOException|RuntimeException|Error failure){try{if(held!=null)held.release();}catch(IOException cleanup){failure.addSuppressed(cleanup);}try{channel.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    private static final class Locked implements AutoCloseable {final FileChannel channel;final FileLock held;Locked(FileChannel channel,FileLock held){this.channel=channel;this.held=held;}public void close()throws IOException{try{held.release();}finally{channel.close();}}}
    private void requireDirectories()throws IOException {for(int i=0;i<directories.length;i++){BasicFileAttributes a=attrs(directories[i]);if(!a.isDirectory() || !directoryKeys[i].equals(a.fileKey()))throw new IOException("Private epoch directory replaced");}}
    private static BasicFileAttributes attrs(Path path)throws IOException{return Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    private static boolean exists(Path path)throws IOException{try{attrs(path);return true;}catch(NoSuchFileException missing){return false;}}
    private static byte[] digest(byte[] data){try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static String hex(byte[] data){StringBuilder out=new StringBuilder();for(byte b:data)out.append("0123456789abcdef".charAt((b&255)>>>4)).append("0123456789abcdef".charAt(b&15));return out.toString();}
    private static long next(long revision){if(revision==Long.MAX_VALUE)throw new IllegalStateException("Private record revision exhausted");return revision+1;}
    private static void ioThread(){if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Private records require an owned IO lane");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native private record reference/stamp");}
}
