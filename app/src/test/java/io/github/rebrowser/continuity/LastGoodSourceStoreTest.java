package io.github.rebrowser.continuity;

import static org.junit.Assert.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.Test;

/** Actual bounded HOST filesystem only; toy fingerprints/epochs and synthetic native fences.
 * No Android filesystem, signing/Root trust, owner authentication or erasure is exercised. */
public final class LastGoodSourceStoreTest {
    private static final String A=repeat('a'),B=repeat('b'),R=repeat('c'),S=repeat('d');
    private static final LastGoodSourceStore.NativeFence FIXTURE_ONLY=(expected,replacement)->{};
    private static String repeat(char c){char[] value=new char[64];Arrays.fill(value,c);return new String(value);}
    private static LastGoodSourceStore.SourceEpoch pair(String signer,String root,int epoch){byte[] e=new byte[16];Arrays.fill(e,(byte)epoch);return new LastGoodSourceStore.SourceEpoch(signer,root,e);}
    private static final class Fixture implements AutoCloseable {
        final Path root,dir,marker,stage;
        final LastGoodSourceStore store;
        Fixture()throws IOException{
            Path work=Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath();assertFalse(work.startsWith(Paths.get("/tmp")));
            root=Files.createTempDirectory(work,"last-good-host-");dir=Files.createDirectory(root.resolve("ledger"));marker=dir.resolve("source-continuity.v1");stage=dir.resolve("source-continuity.pending");store=new LastGoodSourceStore(dir);
        }
        LastGoodSourceStore.View seed()throws IOException{return store.seedVerifiedFresh(store.inspect(),pair(A,R,1),FIXTURE_ONLY);}
        @Override public void close()throws IOException{
            List<Path> paths=new ArrayList<>();try(Stream<Path> walked=Files.walk(root)){walked.forEach(paths::add);}paths.sort(Comparator.reverseOrder());for(Path p:paths)Files.delete(p);
        }
    }
    @Test public void missingIsOnlyMetadataAbsenceAndNeverInventsTrustOrReadsOtherFiles()throws Exception{
        try(Fixture f=new Fixture()){
            Path old=f.dir.resolve("old-business-data");Files.write(old,new byte[]{7,8,9});LastGoodSourceStore.View view=f.store.inspect();assertEquals(LastGoodSourceStore.Kind.MISSING,view.kind());assertFalse(view.hasBaselineMetadata());assertThrows(IllegalStateException.class,view::record);
            assertThrows(IOException.class,()->f.store.seedVerifiedFresh(view,pair(A,R,1),(expected,replacement)->{throw new IOException("synthetic old-data inventory rejects seed");}));
            assertFalse(Files.exists(f.marker));assertArrayEquals(new byte[]{7,8,9},Files.readAllBytes(old));
        }
    }
    @Test public void nativeFreshSeedPersistsAndReopensWithDefensiveMetadata()throws Exception{
        try(Fixture f=new Fixture()){
            LastGoodSourceStore.View seeded=f.seed();assertEquals(LastGoodSourceStore.Kind.CURRENT,seeded.kind());assertEquals(1,seeded.record().revision());assertEquals(A,seeded.record().lastGood().signingSha256());assertEquals(R,seeded.record().lastGood().rootSha256());
            byte[] changed=seeded.record().lastGood().dataEpoch();changed[0]=9;assertEquals(1,seeded.record().lastGood().dataEpoch()[0]);assertEquals(216,Files.size(f.marker));
            LastGoodSourceStore.View reopened=new LastGoodSourceStore(f.dir).inspect();assertEquals(A,reopened.record().lastGood().signingSha256());assertEquals(LastGoodSourceStore.Phase.STABLE,reopened.record().phase());assertFalse(Files.exists(f.stage));assertThrows(IllegalStateException.class,()->seeded.record().pendingSource());
        }
    }
    @Test public void abnormalCandidateNeverReplacesAAndCanBeAbandonedBeforeDestruction()throws Exception{
        try(Fixture f=new Fixture()){
            LastGoodSourceStore.View pending=f.store.prepareReplacement(f.seed(),pair(B,S,2),FIXTURE_ONLY);
            assertEquals(A,pending.record().lastGood().signingSha256());assertEquals(R,pending.record().lastGood().rootSha256());assertEquals(B,pending.record().pendingSource().signingSha256());assertEquals(1,pending.record().lastGood().dataEpoch()[0]);
            assertEquals(A,new LastGoodSourceStore(f.dir).inspect().record().lastGood().signingSha256());LastGoodSourceStore.View rescued=f.store.abandonPrepared(pending,FIXTURE_ONLY);assertEquals(A,rescued.record().lastGood().signingSha256());assertEquals(3,rescued.record().revision());assertEquals(LastGoodSourceStore.Phase.STABLE,rescued.record().phase());
        }
    }
    @Test public void onlyExplicitVerifiedEmptyTransitionCommitsCandidateAndNewEpoch()throws Exception{
        try(Fixture f=new Fixture()){
            LastGoodSourceStore.View prepared=f.store.prepareReplacement(f.seed(),pair(B,S,2),FIXTURE_ONLY);
            assertThrows(IllegalStateException.class,()->f.store.commitReplacement(prepared,FIXTURE_ONLY));
            LastGoodSourceStore.View mayStart=f.store.markDestructionMayStart(prepared,FIXTURE_ONLY);assertEquals(A,mayStart.record().lastGood().signingSha256());assertThrows(IllegalStateException.class,()->f.store.abandonPrepared(mayStart,FIXTURE_ONLY));assertThrows(IllegalStateException.class,()->f.store.commitReplacement(mayStart,FIXTURE_ONLY));
            LastGoodSourceStore.View empty=f.store.recordVerifiedEmptyEpoch(mayStart,FIXTURE_ONLY);assertEquals(A,empty.record().lastGood().signingSha256());LastGoodSourceStore.View accepted=f.store.commitReplacement(empty,FIXTURE_ONLY);assertEquals(B,accepted.record().lastGood().signingSha256());assertEquals(S,accepted.record().lastGood().rootSha256());assertEquals(2,accepted.record().lastGood().dataEpoch()[0]);assertEquals(5,accepted.record().revision());assertEquals(LastGoodSourceStore.Phase.STABLE,accepted.record().phase());
        }
    }
    @Test public void staleForeignAndAbaViewsCannotOverwrite()throws Exception{
        try(Fixture f=new Fixture()){
            LastGoodSourceStore.View first=f.seed();LastGoodSourceStore.View foreign=new LastGoodSourceStore(f.dir).inspect();assertThrows(IllegalStateException.class,()->f.store.prepareReplacement(foreign,pair(B,S,2),FIXTURE_ONLY));
            LastGoodSourceStore.View pending=f.store.prepareReplacement(first,pair(B,S,2),FIXTURE_ONLY);assertThrows(IllegalStateException.class,()->f.store.prepareReplacement(first,pair(B,S,3),FIXTURE_ONLY));f.store.abandonPrepared(pending,FIXTURE_ONLY);
            assertThrows(IllegalStateException.class,()->f.store.prepareReplacement(first,pair(B,S,2),FIXTURE_ONLY));assertEquals(3,f.store.inspect().record().revision());
        }
    }
    @Test public void candidateMustChangeSourcePairAndUseDifferentEpoch()throws Exception{
        try(Fixture f=new Fixture()){
            LastGoodSourceStore.View view=f.seed();assertThrows(IllegalArgumentException.class,()->f.store.prepareReplacement(view,pair(A,R,2),FIXTURE_ONLY));assertThrows(IllegalArgumentException.class,()->f.store.prepareReplacement(view,pair(B,S,1),FIXTURE_ONLY));
            assertThrows(IllegalArgumentException.class,()->new LastGoodSourceStore.SourceEpoch(A.toUpperCase(Locale.ROOT),R,new byte[16]));assertThrows(IllegalArgumentException.class,()->new LastGoodSourceStore.SourceEpoch(A,R,new byte[15]));assertEquals(1,f.store.inspect().record().revision());
        }
    }
    @Test public void fenceRepeatedAfterRealStagingIoAndFailuresPreserveBaseline()throws Exception{
        try(Fixture f=new Fixture()){
            LastGoodSourceStore.View view=f.seed();AtomicInteger calls=new AtomicInteger();
            assertThrows(IOException.class,()->f.store.prepareReplacement(view,pair(B,S,2),(expected,replacement)->{
                if(calls.incrementAndGet()==2){assertEquals(216,Files.size(f.stage));Set<PosixFilePermission> modes=Files.getPosixFilePermissions(f.stage);assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE),modes);throw new IOException("synthetic source revoked after IO");}
            }));
            assertEquals(2,calls.get());LastGoodSourceStore.View unfinished=f.store.inspect();assertEquals(LastGoodSourceStore.Kind.UNFINISHED_WRITE,unfinished.kind());assertEquals(A,unfinished.record().lastGood().signingSha256());assertEquals(1,unfinished.record().revision());
            assertThrows(IllegalStateException.class,()->f.store.prepareReplacement(unfinished,pair(B,S,3),FIXTURE_ONLY));LastGoodSourceStore.View recovered=f.store.discardUncommittedStage(unfinished,FIXTURE_ONLY);assertEquals(LastGoodSourceStore.Kind.CURRENT,recovered.kind());assertEquals(A,recovered.record().lastGood().signingSha256());
        }
    }
    @Test public void failedInitialCommitNeverConvertsPendingWriteIntoFreshInstall()throws Exception{
        try(Fixture f=new Fixture()){
            AtomicInteger calls=new AtomicInteger();assertThrows(IOException.class,()->f.store.seedVerifiedFresh(f.store.inspect(),pair(A,R,1),(e,r)->{if(calls.incrementAndGet()==2)throw new IOException("synthetic before rename failure");}));
            LastGoodSourceStore.View unfinished=f.store.inspect();assertEquals(LastGoodSourceStore.Kind.UNFINISHED_WRITE,unfinished.kind());assertFalse(unfinished.hasBaselineMetadata());assertThrows(IllegalStateException.class,()->f.store.seedVerifiedFresh(unfinished,pair(B,S,2),FIXTURE_ONLY));assertEquals(LastGoodSourceStore.Kind.MISSING,f.store.discardUncommittedStage(unfinished,FIXTURE_ONLY).kind());
        }
    }
    @Test public void stageDiscardBindsExactBytesAndCannotDeleteUnknownLargeFiles()throws Exception{
        try(Fixture f=new Fixture()){
            f.seed();Files.write(f.stage,new byte[]{1});LastGoodSourceStore.View view=f.store.inspect();Files.write(f.stage,new byte[]{2});assertThrows(IllegalStateException.class,()->f.store.discardUncommittedStage(view,FIXTURE_ONLY));assertArrayEquals(new byte[]{2},Files.readAllBytes(f.stage));
            Files.write(f.stage,new byte[217]);assertEquals(LastGoodSourceStore.Kind.CORRUPT,f.store.inspect().kind());assertThrows(IllegalStateException.class,()->f.store.discardUncommittedStage(f.store.inspect(),FIXTURE_ONLY));assertEquals(217,Files.size(f.stage));
        }
    }
    @Test public void symlinkMarkerStageDirectoryAndLockAreNeverFollowed()throws Exception{
        try(Fixture f=new Fixture()){
            Path outside=Files.write(f.root.resolve("untouched"),new byte[]{8,9});Files.createSymbolicLink(f.marker,outside);assertEquals(LastGoodSourceStore.Kind.CORRUPT,f.store.inspect().kind());assertThrows(IllegalStateException.class,()->f.store.seedVerifiedFresh(f.store.inspect(),pair(A,R,1),FIXTURE_ONLY));Files.delete(f.marker);
            f.seed();Files.createSymbolicLink(f.stage,outside);assertEquals(LastGoodSourceStore.Kind.CORRUPT,f.store.inspect().kind());Files.delete(f.stage);
            Path alias=f.root.resolve("alias");Files.createSymbolicLink(alias,f.dir);assertThrows(IOException.class,()->new LastGoodSourceStore(alias));
            Path lock=f.dir.resolve("source-continuity.lock");Files.delete(lock);Files.createSymbolicLink(lock,outside);assertThrows(IOException.class,f.store::inspect);assertArrayEquals(new byte[]{8,9},Files.readAllBytes(outside));
        }
    }
    @Test public void actualOverlappingFileLockFailsFastWithoutChangingMarker()throws Exception{
        try(Fixture f=new Fixture()){
            f.seed();byte[] original=Files.readAllBytes(f.marker);Path lock=f.dir.resolve("source-continuity.lock");
            try(FileChannel owner=FileChannel.open(lock,StandardOpenOption.WRITE);FileLock held=owner.lock()){
                assertThrows(IOException.class,f.store::inspect);assertThrows(IOException.class,()->new LastGoodSourceStore(f.dir).inspect());assertArrayEquals(original,Files.readAllBytes(f.marker));
            }
            assertEquals(LastGoodSourceStore.Kind.CURRENT,f.store.inspect().kind());
        }
    }
    @Test public void reentrantNativeFenceCannotDeadlockOrSneakAnotherMutation()throws Exception{
        try(Fixture f=new Fixture()){
            LastGoodSourceStore.View view=f.seed();assertThrows(IOException.class,()->f.store.prepareReplacement(view,pair(B,S,2),(e,r)->f.store.inspect()));assertEquals(1,f.store.inspect().record().revision());assertFalse(Files.exists(f.stage));
        }
    }
    @Test public void corruptTruncatedOversizedAndZeroByteMarkersNeverSeedOrRepair()throws Exception{
        try(Fixture f=new Fixture()){
            byte[] valid=LastGoodSourceStore.encode(f.seed().record());List<byte[]> malformed=new ArrayList<>();malformed.add(new byte[0]);malformed.add(Arrays.copyOf(valid,215));malformed.add(Arrays.copyOf(valid,217));byte[] bad=valid.clone();bad[24]^=1;malformed.add(bad);
            for(byte[] value:malformed){Files.write(f.marker,value);LastGoodSourceStore.View corrupt=f.store.inspect();assertEquals(LastGoodSourceStore.Kind.CORRUPT,corrupt.kind());assertFalse(corrupt.hasBaselineMetadata());assertThrows(IllegalStateException.class,()->f.store.seedVerifiedFresh(corrupt,pair(B,S,2),FIXTURE_ONLY));assertArrayEquals(value,Files.readAllBytes(f.marker));}
        }
    }
    private static byte[] checksum(byte[] frame)throws Exception{System.arraycopy(MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(frame,184)),0,frame,184,32);return frame;}
    @Test public void validChecksumDoesNotRelaxMagicVersionPhaseRevisionOrStablePadding()throws Exception{
        try(Fixture f=new Fixture()){
            byte[] valid=LastGoodSourceStore.encode(f.seed().record());List<byte[]> bad=new ArrayList<>();byte[] frame=valid.clone();frame[0]^=1;bad.add(frame);frame=valid.clone();ByteBuffer.wrap(frame).putInt(8,2);bad.add(frame);frame=valid.clone();ByteBuffer.wrap(frame).putInt(12,4);bad.add(frame);frame=valid.clone();ByteBuffer.wrap(frame).putLong(16,0);bad.add(frame);frame=valid.clone();ByteBuffer.wrap(frame).putLong(16,-1);bad.add(frame);frame=valid.clone();frame[104]=1;bad.add(frame);
            for(byte[] value:bad){byte[] resigned=checksum(value);assertThrows(IllegalArgumentException.class,()->LastGoodSourceStore.decode(resigned));}
        }
    }
    @Test public void pendingFrameCannotReuseOldEpochOrSourcePairEvenWithFreshChecksum()throws Exception{
        try(Fixture f=new Fixture()){
            byte[] frame=LastGoodSourceStore.encode(f.store.prepareReplacement(f.seed(),pair(B,S,2),FIXTURE_ONLY).record());byte[] sameEpoch=frame.clone();System.arraycopy(frame,88,sameEpoch,168,16);checksum(sameEpoch);assertThrows(IllegalArgumentException.class,()->LastGoodSourceStore.decode(sameEpoch));byte[] samePair=frame.clone();System.arraycopy(frame,24,samePair,104,64);checksum(samePair);assertThrows(IllegalArgumentException.class,()->LastGoodSourceStore.decode(samePair));
        }
    }
    @Test public void replacedNativeDirectoryDoesNotLookLikeFreshInstallationToLiveOwner()throws Exception{
        try(Fixture f=new Fixture()){
            f.seed();Path retired=f.root.resolve("retired-ledger");Files.move(f.dir,retired);Files.createDirectory(f.dir);
            assertThrows(IOException.class,f.store::inspect);assertFalse(Files.exists(f.dir.resolve("source-continuity.lock")));assertEquals(216,Files.size(retired.resolve("source-continuity.v1")));
        }
    }
    @Test public void exhaustedRevisionCannotWrapOrCreateAReplacementStage()throws Exception{
        try(Fixture f=new Fixture()){
            byte[] frame=LastGoodSourceStore.encode(f.seed().record());ByteBuffer.wrap(frame).putLong(16,Long.MAX_VALUE);Files.write(f.marker,checksum(frame));LastGoodSourceStore.View view=f.store.inspect();assertEquals(Long.MAX_VALUE,view.record().revision());assertThrows(IllegalStateException.class,()->f.store.prepareReplacement(view,pair(B,S,2),FIXTURE_ONLY));assertFalse(Files.exists(f.stage));
        }
    }
}
