package io.github.rebrowser.download;

import static org.junit.Assert.*;
import io.github.rebrowser.core.Cancellation;
import java.security.MessageDigest;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class ResponseStreamWorkerTest {
    private static final class Source extends InputStream {
        final byte[] data;
        int at,closed,bulk,single,maxRequested;
        boolean failClose;
        Source(byte[] data){this.data=data;}
        @Override public int read(){single++;throw new AssertionError("No single-byte read: pinned GeckoInputStream reads a four-byte int here");}
        @Override public int read(byte[] b,int off,int len){bulk++;maxRequested=Math.max(maxRequested,len);if(at==data.length)return -1;int n=Math.min(len,Math.min(997,data.length-at));System.arraycopy(data,at,b,off,n);at+=n;return n;}
        @Override public void close()throws IOException{closed++;if(failClose)throw new IOException("synthetic input close failure");}
    }
    private static final class Stage extends ByteArrayOutputStream {
        int closed;
        boolean failClose,failFlush;
        @Override public void flush()throws IOException{if(failFlush)throw new IOException("synthetic flush failure");}
        @Override public void close()throws IOException{closed++;if(failClose)throw new IOException("synthetic stage close failure");}
    }
    private static Cancellation.Requester cancellation(){return Cancellation.create().requester();}
    @Test public void exactMultiChunkOriginalBytesDigestAndBothClosed()throws Exception{
        byte[] data=new byte[1_000_003];for(int i=0;i<data.length;i++)data[i]=(byte)(i*31);Source source=new Source(data);Stage stage=new Stage();
        ResponseStreamWorker.CopiedStage result=ResponseStreamWorker.copyOwned(source,stage,data.length,data.length,cancellation());
        assertEquals(data.length,result.byteCount());assertArrayEquals(data,stage.toByteArray());assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(data),result.originalSha256());assertEquals(1,source.closed);assertEquals(1,stage.closed);assertEquals(0,source.single);assertTrue(source.maxRequested<=32768);
        byte[] changed=result.originalSha256();changed[0]^=1;assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(data),result.originalSha256());
    }
    @Test public void emptyAndExactQuotaEofProbeUseBulkReadOnly()throws Exception{
        Source empty=new Source(new byte[0]);Stage stage=new Stage();assertEquals(0,ResponseStreamWorker.copyOwned(empty,stage,0,0,cancellation()).byteCount());assertEquals(1,empty.maxRequested);assertEquals(0,empty.single);
        Source one=new Source(new byte[]{-1});assertEquals(1,ResponseStreamWorker.copyOwned(one,new Stage(),1,-1,cancellation()).byteCount());assertEquals(0,one.single);assertEquals(2,one.bulk);
    }
    @Test public void quotaAndAdvertisedLengthExcessCannotReturnOrWriteBeyondLimit(){
        for(long expected:new long[]{-1,2}){Source source=new Source(new byte[]{1,2,3});Stage stage=new Stage();assertThrows(IOException.class,()->ResponseStreamWorker.copyOwned(source,stage,2,expected,cancellation()));assertTrue(stage.size()<=2);assertEquals(1,source.closed);assertEquals(1,stage.closed);assertEquals(0,source.single);}
    }
    @Test public void shortBodyLeavesOnlyUnpublishedTaintedPrefix(){
        Source source=new Source(new byte[]{1,2});Stage stage=new Stage();assertThrows(IOException.class,()->ResponseStreamWorker.copyOwned(source,stage,3,3,cancellation()));assertArrayEquals(new byte[]{1,2},stage.toByteArray());assertEquals(1,source.closed);assertEquals(1,stage.closed);
    }
    @Test public void malformedNativeLimitsStillCloseTransferredStreams(){
        for(long[] limits:new long[][]{{-1,-1},{2,3},{2,-2},{ResponseStreamWorker.MAX_COPY_BYTES+1,-1}}){Source source=new Source(new byte[]{1});Stage stage=new Stage();assertThrows(IllegalArgumentException.class,()->ResponseStreamWorker.copyOwned(source,stage,limits[0],limits[1],cancellation()));assertEquals(1,source.closed);assertEquals(1,stage.closed);assertEquals(0,source.bulk);}
    }
    @Test public void readWriteFlushAndCloseFailuresNeverProduceSuccess()throws Exception{
        Source source=new Source(new byte[]{1,2});Stage stage=new Stage();stage.failFlush=true;assertThrows(IOException.class,()->ResponseStreamWorker.copyOwned(source,stage,2,2,cancellation()));assertEquals(1,source.closed);assertEquals(1,stage.closed);
        Source closeFail=new Source(new byte[]{1});closeFail.failClose=true;Stage both=new Stage();both.failClose=true;
        IOException closeError=assertThrows(IOException.class,()->ResponseStreamWorker.copyOwned(closeFail,both,1,1,cancellation()));assertEquals(1,closeError.getSuppressed().length);assertEquals(1,closeFail.closed);assertEquals(1,both.closed);
        Source writes=new Source(new byte[]{1});OutputStream badOutput=new OutputStream(){public void write(int b)throws IOException{throw new IOException("synthetic write failure");}};
        assertThrows(IOException.class,()->ResponseStreamWorker.copyOwned(writes,badOutput,1,1,cancellation()));assertEquals(1,writes.closed);
        InputStream reads=new InputStream(){public int read(){throw new AssertionError();}public int read(byte[] b,int o,int n)throws IOException{throw new IOException("synthetic read failure");}};Stage closed=new Stage();assertThrows(IOException.class,()->ResponseStreamWorker.copyOwned(reads,closed,1,1,cancellation()));assertEquals(1,closed.closed);
    }
    @Test public void cancelledBeforeReadDoesNotTouchBytesButClosesBoth(){
        Cancellation cancel=Cancellation.create();cancel.requester().requestCancellation();Source source=new Source(new byte[]{1});Stage stage=new Stage();
        assertThrows(InterruptedIOException.class,()->ResponseStreamWorker.copyOwned(source,stage,1,1,cancel.requester()));assertEquals(0,source.bulk);assertEquals(0,stage.size());assertEquals(1,source.closed);assertEquals(1,stage.closed);
    }
    @Test public void ignoredInterruptAndFutureDoneDoNotMeanCopyHasEnded()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),ended=new CountDownLatch(1);Cancellation cancel=Cancellation.create();Stage stage=new Stage();AtomicReference<Throwable> failure=new AtomicReference<>();
        InputStream source=new InputStream(){public int read(){throw new AssertionError();}public int read(byte[] b,int o,int n){entered.countDown();while(true){try{release.await();break;}catch(InterruptedException ignored){}}b[o]=1;return 1;}};
        FutureTask<Void> task=new FutureTask<>(()->{try{ResponseStreamWorker.copyOwned(source,stage,1,1,cancel.requester());}catch(Throwable error){failure.set(error);}finally{ended.countDown();}return null;});Thread worker=new Thread(task,"owned-copy-test");worker.start();
        try{assertTrue(entered.await(2,TimeUnit.SECONDS));cancel.requester().requestCancellation();assertTrue(task.cancel(true));assertTrue(task.isDone());assertEquals(1,ended.getCount());assertEquals(0,stage.closed);assertTrue(worker.isAlive());}
        finally{release.countDown();worker.join(3000);assertFalse(worker.isAlive());}
        assertEquals(0,stage.size());assertEquals(1,stage.closed);assertTrue(failure.get() instanceof InterruptedIOException);
    }
    @Test public void zeroProgressDoesNotSpinOrFallbackToSingleRead(){
        InputStream source=new InputStream(){public int read(){throw new AssertionError();}public int read(byte[] b,int o,int n){return 0;}};Stage stage=new Stage();assertThrows(IOException.class,()->ResponseStreamWorker.copyOwned(source,stage,1,-1,cancellation()));assertEquals(1,stage.closed);
    }
    @Test public void ignoredBodyIsClosedWithoutReadingAndFailureRemainsExplicit()throws Exception{
        Source source=new Source(new byte[]{1,2,3});ResponseStreamWorker.discardOwned(source);assertEquals(0,source.bulk);assertEquals(0,source.single);assertEquals(1,source.closed);
        Source bad=new Source(new byte[0]);bad.failClose=true;assertThrows(IOException.class,()->ResponseStreamWorker.discardOwned(bad));
    }
}
