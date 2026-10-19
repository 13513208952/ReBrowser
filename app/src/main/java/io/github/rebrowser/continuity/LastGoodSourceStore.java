package io.github.rebrowser.continuity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Small native no-backup bootstrap METADATA ledger, IO lane only. Never opens business
 * data or grants consent/trust/unlock. A marker's absence is NOT proof of a fresh install.
 * Mutation methods are package-private for the future native continuity coordinator,
 * which must provide actual current source/epoch/owner-auth/erasure evidence and serialize
 * source changes with replacement. A checksum is corruption detection, NOT authentication.
 *
 * No AtomicFile.finishWrite/void receipt assumption: force file, atomic rename, force
 * directory and read back, or fail/unknown. Android filesystem/power-loss qualification
 * remains required; an unsupported provider fails closed, with NO non-atomic fallback.
 * Once any mutation throws its outcome may be unknown; inspect before further decisions.
 */
public final class LastGoodSourceStore {
    public enum Phase { STABLE, PREPARED_OLD_DATA_PRESERVED, DESTRUCTION_MAY_HAVE_STARTED, EMPTY_EPOCH_VERIFIED }
    public enum Kind { MISSING, CURRENT, UNFINISHED_WRITE, CORRUPT }
    public static final int FRAME_BYTES=216;
    private static final int CONTENT_BYTES=184;
    private static final long MAGIC=0x5242535243303100L; // RBSRC01 NUL
    private static final String MARKER="source-continuity.v1", STAGE="source-continuity.pending", LOCK="source-continuity.lock";
    /** Native public identity metadata only; constructing this is NOT admission. */
    public static final class SourceEpoch {
        private final byte[] signer,root,epoch;
        public SourceEpoch(String signingSha256,String rootSha256,byte[] nativeEpoch){
            signer=unhex(signingSha256);root=unhex(rootSha256);
            if(nativeEpoch==null || nativeEpoch.length!=16)throw invalid();epoch=nativeEpoch.clone();
        }
        private SourceEpoch(ByteBuffer input){signer=new byte[32];root=new byte[32];epoch=new byte[16];input.get(signer).get(root).get(epoch);}
        public String signingSha256(){return hex(signer);}
        public String rootSha256(){return hex(root);}
        public byte[] dataEpoch(){return epoch.clone();}
        private void write(ByteBuffer output){output.put(signer).put(root).put(epoch);}
        private boolean samePair(SourceEpoch other){return Arrays.equals(signer,other.signer) && Arrays.equals(root,other.root);}
    }
    /** Stored native claims only. Even EMPTY_EPOCH_VERIFIED is NOT an erasure receipt. */
    public static final class Record {
        private final long revision;
        private final Phase phase;
        private final SourceEpoch baseline,pending;
        private Record(long revision,Phase phase,SourceEpoch baseline,SourceEpoch pending){
            if(revision<1 || phase==null || baseline==null || (phase==Phase.STABLE)!=(pending==null))throw invalid();
            if(pending!=null && (baseline.samePair(pending) || Arrays.equals(baseline.epoch,pending.epoch)))throw invalid();
            this.revision=revision;this.phase=phase;this.baseline=baseline;this.pending=pending;
        }
        public long revision(){return revision;}
        public Phase phase(){return phase;}
        public SourceEpoch lastGood(){return baseline;}
        public SourceEpoch pendingSource(){if(pending==null)throw new IllegalStateException("No pending source");return pending;}
    }
    /** Issuer-bound exact disk snapshot, NOT a business permit or proof of recoverability. */
    public static final class View {
        private final LastGoodSourceStore issuer;
        private final Kind kind;
        private final Record record;
        private final byte[] marker,stage;
        private View(LastGoodSourceStore issuer,Kind kind,Record record,byte[] marker,byte[] stage){this.issuer=issuer;this.kind=kind;this.record=record;this.marker=marker;this.stage=stage;}
        public Kind kind(){return kind;}
        public boolean hasBaselineMetadata(){return record!=null;}
        public Record record(){if(record==null)throw new IllegalStateException("No valid source metadata");return record;}
    }
    interface NativeFence {
        /** Read-only native checks after lock and after staging IO; NEVER consume grants/tickets.
         * Replacement may be null only when discarding an uncommitted initial metadata stage. */
        void checkCurrent(View expected,Record replacement)throws IOException;
    }
    private final Path directory,marker,stage,lock;
    private final Object directoryKey;
    /** Native existing private no-backup directory, NEVER an Intent/control/file-picker path. */
    public LastGoodSourceStore(Path nativePrivateBootstrapDirectory)throws IOException{
        if(nativePrivateBootstrapDirectory==null || !nativePrivateBootstrapDirectory.isAbsolute())throw invalid();directory=nativePrivateBootstrapDirectory.normalize();
        BasicFileAttributes attributes=Files.readAttributes(directory,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attributes.isDirectory() || attributes.fileKey()==null)throw new IOException("Identifiable native bootstrap directory required");
        directoryKey=attributes.fileKey();marker=directory.resolve(MARKER);stage=directory.resolve(STAGE);lock=directory.resolve(LOCK);
    }
    public View inspect()throws IOException{try(Locked held=lock()){return readLocked();}}
    // These operations record coordinator decisions; they DO NOT implement their authority.
    View seedVerifiedFresh(View missing,SourceEpoch accepted,NativeFence fence)throws IOException{
        requireView(missing);if(missing.kind!=Kind.MISSING)throw conflict();return replace(missing,new Record(1,Phase.STABLE,accepted,null),fence);
    }
    View prepareReplacement(View current,SourceEpoch candidate,NativeFence fence)throws IOException{
        Record old=requirePhase(current,Phase.STABLE);return replace(current,new Record(next(old),Phase.PREPARED_OLD_DATA_PRESERVED,old.baseline,candidate),fence);
    }
    View abandonPrepared(View current,NativeFence fence)throws IOException{
        Record old=requirePhase(current,Phase.PREPARED_OLD_DATA_PRESERVED);return replace(current,new Record(next(old),Phase.STABLE,old.baseline,null),fence);
    }
    View markDestructionMayStart(View current,NativeFence fence)throws IOException{return advance(current,Phase.PREPARED_OLD_DATA_PRESERVED,Phase.DESTRUCTION_MAY_HAVE_STARTED,fence);}
    View recordVerifiedEmptyEpoch(View current,NativeFence fence)throws IOException{return advance(current,Phase.DESTRUCTION_MAY_HAVE_STARTED,Phase.EMPTY_EPOCH_VERIFIED,fence);}
    View commitReplacement(View current,NativeFence fence)throws IOException{
        Record old=requirePhase(current,Phase.EMPTY_EPOCH_VERIFIED);return replace(current,new Record(next(old),Phase.STABLE,old.pending,null),fence);
    }
    /** Explicit cleanup of only the exact uncommitted metadata stage, not old app data. */
    View discardUncommittedStage(View expected,NativeFence fence)throws IOException{
        requireView(expected);if(expected.kind!=Kind.UNFINISHED_WRITE || fence==null)throw conflict();
        try(Locked held=lock();FileChannel namespace=openDirectory()){
            namespace.force(true);compare(expected,readLocked());fence.checkCurrent(expected,expected.record);
            // readLocked already rejected symlink/nonregular/oversized stage and compare binds its exact bytes.
            Files.delete(stage);namespace.force(true);return readLocked();
        }
    }
    private View advance(View current,Phase from,Phase to,NativeFence fence)throws IOException{
        Record old=requirePhase(current,from);return replace(current,new Record(next(old),to,old.baseline,old.pending),fence);
    }
    private View replace(View expected,Record replacement,NativeFence fence)throws IOException{
        requireView(expected);if(fence==null || (expected.kind!=Kind.MISSING && expected.kind!=Kind.CURRENT))throw conflict();byte[] bytes=encode(replacement);
        try(Locked held=lock();FileChannel namespace=openDirectory()){
            namespace.force(true);compare(expected,readLocked());fence.checkCurrent(expected,replacement);
            Set<OpenOption> options=new HashSet<>();options.add(StandardOpenOption.CREATE_NEW);options.add(StandardOpenOption.WRITE);options.add(LinkOption.NOFOLLOW_LINKS);
            try(FileChannel output=FileChannel.open(stage,options,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))){
                ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())if(output.write(buffer)<=0)throw new IOException("No source metadata write progress");output.force(true);
            }
            fence.checkCurrent(expected,replacement); // waiting/IO cannot preserve old authorization
            Files.move(stage,marker,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            namespace.force(true);
            View result=readLocked();if(result.kind!=Kind.CURRENT || !Arrays.equals(bytes,result.marker))throw new IOException("Source metadata commit outcome unknown");return result;
        } // Never delete uncertain stage or overwrite old marker as a catch/fallback path.
    }
    private Record requirePhase(View current,Phase phase){requireView(current);if(current.kind!=Kind.CURRENT || current.record.phase!=phase)throw conflict();return current.record;}
    private void requireView(View view){if(view==null || view.issuer!=this)throw conflict();}
    private void compare(View expected,View actual){if(expected.kind!=actual.kind || !Arrays.equals(expected.marker,actual.marker) || !Arrays.equals(expected.stage,actual.stage))throw conflict();}
    private View readLocked()throws IOException{
        requireDirectory();
        Blob main=readSmall(marker),pending=readSmall(stage);Record parsed=null;
        if(main.bytes!=null)try{parsed=decode(main.bytes);}catch(IllegalArgumentException malformed){/* do not trust, repair, seed or reveal bytes */}
        Kind kind=main.unsafe || pending.unsafe || (main.bytes!=null && parsed==null)?Kind.CORRUPT:
            pending.bytes!=null?Kind.UNFINISHED_WRITE:parsed==null?Kind.MISSING:Kind.CURRENT;
        return new View(this,kind,parsed,main.bytes,pending.bytes);
    }
    private static final class Blob {
        final byte[] bytes;final boolean unsafe;
        Blob(byte[] bytes,boolean unsafe){this.bytes=bytes;this.unsafe=unsafe;}
    }
    private Blob readSmall(Path file)throws IOException{
        BasicFileAttributes attributes;try{attributes=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}catch(NoSuchFileException absent){return new Blob(null,false);}
        if(!attributes.isRegularFile() || attributes.size()>FRAME_BYTES)return new Blob(null,true);
        try(FileChannel input=FileChannel.open(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            ByteBuffer buffer=ByteBuffer.allocate(FRAME_BYTES+1);while(buffer.hasRemaining()){int n=input.read(buffer);if(n<0)break;if(n==0)throw new IOException("No source metadata read progress");}
            if(buffer.position()>FRAME_BYTES)return new Blob(null,true);return new Blob(Arrays.copyOf(buffer.array(),buffer.position()),false);
        }
    }
    private void requireDirectory()throws IOException{
        BasicFileAttributes attributes=Files.readAttributes(directory,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attributes.isDirectory() || !directoryKey.equals(attributes.fileKey()))throw new IOException("Native bootstrap directory changed");
    }
    private FileChannel openDirectory()throws IOException{requireDirectory();return FileChannel.open(directory,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS);}
    private Locked lock()throws IOException{
        requireDirectory();
        try{if(!Files.readAttributes(lock,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isRegularFile())throw new IOException("Invalid source metadata lock");}catch(NoSuchFileException absent){/* create only this fixed native lock */}
        FileChannel channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        try{FileLock acquired=channel.tryLock();if(acquired==null)throw new IOException("Source metadata busy");return new Locked(channel,acquired);}
        catch(IOException|RuntimeException failure){try{channel.close();}catch(IOException close){failure.addSuppressed(close);}if(failure instanceof OverlappingFileLockException)throw new IOException("Source metadata busy",failure);throw failure;}
    }
    private static final class Locked implements AutoCloseable {
        final FileChannel channel;final FileLock lock;
        Locked(FileChannel channel,FileLock lock){this.channel=channel;this.lock=lock;}
        @Override public void close()throws IOException{try(FileChannel opened=channel;FileLock held=lock){/* both release even on failure */}}
    }
    static byte[] encode(Record value){
        ByteBuffer out=ByteBuffer.allocate(FRAME_BYTES);out.putLong(MAGIC).putInt(1).putInt(code(value.phase)).putLong(value.revision);value.baseline.write(out);if(value.pending!=null)value.pending.write(out);else out.put(new byte[80]);out.put(digest(Arrays.copyOf(out.array(),CONTENT_BYTES)));return out.array();
    }
    static Record decode(byte[] bytes){
        if(bytes==null || bytes.length!=FRAME_BYTES || !MessageDigest.isEqual(digest(Arrays.copyOf(bytes,CONTENT_BYTES)),Arrays.copyOfRange(bytes,CONTENT_BYTES,FRAME_BYTES)))throw invalid();
        ByteBuffer in=ByteBuffer.wrap(bytes);if(in.getLong()!=MAGIC || in.getInt()!=1)throw invalid();Phase phase=phase(in.getInt());long revision=in.getLong();SourceEpoch old=new SourceEpoch(in),pending;
        if(phase==Phase.STABLE){byte[] empty=new byte[80];in.get(empty);if(!Arrays.equals(empty,new byte[80]))throw invalid();pending=null;}else pending=new SourceEpoch(in);
        return new Record(revision,phase,old,pending);
    }
    private static int code(Phase phase){switch(phase){case STABLE:return 0;case PREPARED_OLD_DATA_PRESERVED:return 1;case DESTRUCTION_MAY_HAVE_STARTED:return 2;case EMPTY_EPOCH_VERIFIED:return 3;default:throw invalid();}}
    private static Phase phase(int code){switch(code){case 0:return Phase.STABLE;case 1:return Phase.PREPARED_OLD_DATA_PRESERVED;case 2:return Phase.DESTRUCTION_MAY_HAVE_STARTED;case 3:return Phase.EMPTY_EPOCH_VERIFIED;default:throw invalid();}}
    private static long next(Record old){if(old.revision==Long.MAX_VALUE)throw new IllegalStateException("Source metadata revision exhausted");return old.revision+1;}
    private static byte[] digest(byte[] value){try{return MessageDigest.getInstance("SHA-256").digest(value);}catch(NoSuchAlgorithmException unavailable){throw new IllegalStateException("SHA-256 unavailable");}}
    private static byte[] unhex(String text){if(text==null || text.length()!=64)throw invalid();byte[] result=new byte[32];for(int i=0;i<64;i++){char c=text.charAt(i);int value=c>='0' && c<='9'?c-'0':c>='a' && c<='f'?c-'a'+10:-1;if(value<0)throw invalid();result[i/2]|=value<<(i%2==0?4:0);}return result;}
    private static String hex(byte[] bytes){char[] chars=new char[bytes.length*2],digits="0123456789abcdef".toCharArray();for(int i=0;i<bytes.length;i++){chars[2*i]=digits[(bytes[i]&255)>>>4];chars[2*i+1]=digits[bytes[i]&15];}return new String(chars);}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native source metadata");}
    private static IllegalStateException conflict(){return new IllegalStateException("Stale, foreign or invalid source metadata transition");}
}
