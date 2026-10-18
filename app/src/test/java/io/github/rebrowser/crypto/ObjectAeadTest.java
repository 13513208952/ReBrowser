package io.github.rebrowser.crypto;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.Test;

/** Fixed PUBLIC dummy DEK/account values; no real key, mailbox or Android provider tests. */
public final class ObjectAeadTest {
    private static final byte[] EPOCH=new byte[16], ACCOUNT=fill(16,0x22), DEK=fill(32,0x11);
    private static final BooleanSupplier LIVE=()->false;
    private static final ObjectAead.Limits LIMIT=new ObjectAead.Limits(8*1024*1024,8*1024*1024);
    private interface Checked {void run() throws Exception;}
    private static byte[] fill(int n,int value){byte[] b=new byte[n];Arrays.fill(b,(byte)value);return b;}
    private static byte[] sha(byte[] bytes)throws Exception{return MessageDigest.getInstance("SHA-256").digest(bytes);}
    private static ObjectAead.WritePlan plan(byte[] plain)throws Exception{return ObjectAead.freshWrite(LIMIT,ObjectAead.Codec.RAW,ObjectAead.Purpose.BODY,1,EPOCH,ACCOUNT,plain.length,plain.length,sha(plain));}
    private static byte[] seal(ObjectAead.WritePlan p,byte[] packed)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();ObjectAead.seal(p,DEK,new ByteArrayInputStream(packed),out,LIVE);return out.toByteArray();}
    private static byte[] open(ObjectAead.Metadata m,byte[] wire)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();ObjectAead.open(m,DEK,new ByteArrayInputStream(wire),out,LIVE);return out.toByteArray();}
    private static void rejected(Checked c)throws Exception{try{c.run();fail("invalid crypto object must fail");}catch(IOException|GeneralSecurityException|IllegalArgumentException expected){}}

    @Test public void emptyAndAllChunkBoundariesAuthenticate()throws Exception{
        for(int len:new int[]{0,1,32767,32768,32769,65536,65537}){
            byte[] data=fill(len,0x35);ObjectAead.WritePlan p=plan(data);byte[] wire=seal(p,data);
            assertEquals(124L+len+20L*Math.max(1,(len+32767)/32768),wire.length);
            assertEquals(wire.length,p.metadata().framedBytes());assertArrayEquals(data,open(p.metadata(),wire));
            assertArrayEquals(p.metadata().headerBytes(),Arrays.copyOf(wire,124));
        }
    }

    @Test public void everyPurposeAndUnsignedKeyVersionRoundTrip()throws Exception{
        byte[] data=fill(111,0x23);
        for(ObjectAead.Purpose purpose:ObjectAead.Purpose.values())for(long version:new long[]{1,0xffff_ffffL}){
            ObjectAead.WritePlan p=ObjectAead.freshWrite(LIMIT,ObjectAead.Codec.RAW,purpose,version,EPOCH,ACCOUNT,data.length,data.length,sha(data));
            assertArrayEquals(data,open(p.metadata(),seal(p,data)));
        }
    }

    @Test public void metadataAndObjectIdsAreImmutableAndFresh()throws Exception{
        byte[] data={1,2,3},epoch=EPOCH.clone(),account=ACCOUNT.clone(),digest=sha(data);
        ObjectAead.WritePlan p=ObjectAead.freshWrite(LIMIT,ObjectAead.Codec.RAW,ObjectAead.Purpose.BODY,1,epoch,account,3,3,digest);
        byte[] original=p.metadata().headerBytes();Arrays.fill(epoch,(byte)1);Arrays.fill(account,(byte)1);Arrays.fill(digest,(byte)1);
        byte[] copy=p.metadata().headerBytes();Arrays.fill(copy,(byte)1);byte[] id=p.metadata().objectId();Arrays.fill(id,(byte)1);
        assertArrayEquals(original,p.metadata().headerBytes());assertFalse(Arrays.equals(p.metadata().objectId(),plan(data).metadata().objectId()));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();OutputStream mutating=new OutputStream(){
            @Override public void write(int b){bytes.write(b);}
            @Override public void write(byte[] b){bytes.write(b,0,b.length);Arrays.fill(b,(byte)0);}
        };
        ObjectAead.seal(p,DEK,new ByteArrayInputStream(data),mutating,LIVE);
        assertArrayEquals(original,p.metadata().headerBytes());assertArrayEquals(data,open(p.metadata(),bytes.toByteArray()));
        assertArrayEquals(fill(32,0x11),DEK);
    }

    @Test public void writePlanIsOneShotEvenFailedOrCancelled()throws Exception{
        byte[] data={1};ObjectAead.WritePlan p=plan(data);seal(p,data);rejected(()->seal(p,data));
        ObjectAead.WritePlan shortInput=plan(data);rejected(()->seal(shortInput,new byte[0]));rejected(()->seal(shortInput,data));
        ObjectAead.WritePlan cancelled=plan(data);ByteArrayOutputStream empty=new ByteArrayOutputStream();
        rejected(()->ObjectAead.seal(cancelled,DEK,new ByteArrayInputStream(data),empty,()->true));
        assertEquals(0,empty.size());rejected(()->seal(cancelled,data));
        ObjectAead.WritePlan failedSink=plan(data);OutputStream broken=new OutputStream(){public void write(int b)throws IOException{throw new IOException("synthetic sink failure");}};
        rejected(()->ObjectAead.seal(failedSink,DEK,new ByteArrayInputStream(data),broken,LIVE));rejected(()->seal(failedSink,data));
    }

    @Test public void concurrentSealOfOnePlanHasOnlyOneWinner()throws Exception{
        ObjectAead.WritePlan p=plan(new byte[0]);ExecutorService threads=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        Callable<Boolean> writer=()->{assertTrue(start.await(2,TimeUnit.SECONDS));try{seal(p,new byte[0]);return true;}catch(IllegalArgumentException used){return false;}};
        try{Future<Boolean> a=threads.submit(writer),b=threads.submit(writer);start.countDown();assertTrue(a.get(3,TimeUnit.SECONDS)^b.get(3,TimeUnit.SECONDS));}
        finally{threads.shutdownNow();assertTrue(threads.awaitTermination(3,TimeUnit.SECONDS));}
    }

    @Test public void invalidLimitsScopeAndKeyInputsFailWithoutLengthAllocations()throws Exception{
        rejected(()->new ObjectAead.Limits(-1,0));rejected(()->new ObjectAead.Limits(1,-1));
        rejected(()->new ObjectAead.Limits(1,0xffff_ffffL*32768+1));
        for(long version:new long[]{0,0x1_0000_0000L})rejected(()->ObjectAead.freshWrite(LIMIT,ObjectAead.Codec.RAW,ObjectAead.Purpose.BODY,version,EPOCH,ACCOUNT,0,0,sha(new byte[0])));
        rejected(()->ObjectAead.freshWrite(LIMIT,ObjectAead.Codec.RAW,ObjectAead.Purpose.BODY,1,EPOCH,ACCOUNT,1,0,sha(new byte[0])));
        rejected(()->ObjectAead.freshWrite(LIMIT,ObjectAead.Codec.RAW,ObjectAead.Purpose.BODY,1,new byte[15],ACCOUNT,0,0,sha(new byte[0])));
        rejected(()->ObjectAead.freshWrite(new ObjectAead.Limits(0,0),ObjectAead.Codec.RAW,ObjectAead.Purpose.BODY,1,EPOCH,ACCOUNT,1,1,sha(new byte[0])));
        ObjectAead.WritePlan p=plan(new byte[0]);rejected(()->ObjectAead.seal(p,new byte[31],new ByteArrayInputStream(new byte[0]),new ByteArrayOutputStream(),LIVE));
        long huge=0xffff_ffffL*32768;ObjectAead.Metadata m=ObjectAead.catalogMetadata(new ObjectAead.Limits(Long.MAX_VALUE,huge),ObjectAead.Codec.ZLIB,ObjectAead.Purpose.BODY,1,EPOCH,ACCOUNT,new byte[16],Long.MAX_VALUE,huge,new byte[32]);
        assertEquals(124L+huge+20L*0xffff_ffffL,m.framedBytes());
        rejected(()->ObjectAead.open(m,DEK,new ByteArrayInputStream(new byte[0]),new ByteArrayOutputStream(),LIVE));
    }

    @Test public void headerScopeAndWrongKeyAreRejectedBeforePayloadOutput()throws Exception{
        byte[] data=fill(40,7);ObjectAead.WritePlan p=plan(data);byte[] good=seal(p,data);
        for(int i=0;i<124;i++){byte[] bad=good.clone();bad[i]^=1;ByteArrayOutputStream stage=new ByteArrayOutputStream();rejected(()->ObjectAead.open(p.metadata(),DEK,new ByteArrayInputStream(bad),stage,LIVE));assertEquals(0,stage.size());}
        ByteArrayOutputStream stage=new ByteArrayOutputStream();rejected(()->ObjectAead.open(p.metadata(),fill(32,0x12),new ByteArrayInputStream(good),stage,LIVE));assertEquals(0,stage.size());
        ObjectAead.WritePlan another=plan(data);rejected(()->open(another.metadata(),good));
    }

    @Test public void truncationTrailingBadLengthsAndFrameSwapsAreRejected()throws Exception{
        byte[] data=fill(3,1);ObjectAead.WritePlan p=plan(data);byte[] good=seal(p,data);
        for(int n=0;n<good.length;n++){byte[] cut=Arrays.copyOf(good,n);rejected(()->open(p.metadata(),cut));}
        rejected(()->open(p.metadata(),Arrays.copyOf(good,good.length+1)));
        for(int length:new int[]{-1,Integer.MAX_VALUE,0,16,32785}){byte[] bad=good.clone();ByteBuffer.wrap(bad).putInt(124,length);rejected(()->open(p.metadata(),bad));}
        byte[] large=fill(65536,3);ObjectAead.WritePlan two=plan(large);byte[] wire=seal(two,large),swap=wire.clone();int frame=32768+20;
        System.arraycopy(wire,124+frame,swap,124,frame);System.arraycopy(wire,124,swap,124+frame,frame);rejected(()->open(two.metadata(),swap));
        rejected(()->seal(plan(data),new byte[]{1,1,1,1}));
    }

    @Test public void lateTagFailureLeavesOnlyTaintedPrivatePrefixNotAValidObject()throws Exception{
        byte[] data=fill(32769,9);ObjectAead.WritePlan p=plan(data);byte[] bad=seal(p,data);bad[bad.length-1]^=1;
        ByteArrayOutputStream PRIVATE_UNPUBLISHED=new ByteArrayOutputStream();
        rejected(()->ObjectAead.open(p.metadata(),DEK,new ByteArrayInputStream(bad),PRIVATE_UNPUBLISHED,LIVE));
        assertEquals(32768,PRIVATE_UNPUBLISHED.size());assertArrayEquals(Arrays.copyOf(data,32768),PRIVATE_UNPUBLISHED.toByteArray());
        // Caller must abandon this prefix, never publish it as an object/read/export success.
    }

    @Test public void rawDigestIsCheckedButZlibStillNeedsSeparateCodecVerification()throws Exception{
        byte[] data={1,2};ObjectAead.WritePlan wrong=ObjectAead.freshWrite(LIMIT,ObjectAead.Codec.RAW,ObjectAead.Purpose.BODY,1,EPOCH,ACCOUNT,2,2,new byte[32]);
        ByteArrayOutputStream tainted=new ByteArrayOutputStream();rejected(()->ObjectAead.seal(wrong,DEK,new ByteArrayInputStream(data),tainted,LIVE));rejected(()->open(wrong.metadata(),tainted.toByteArray()));
        ObjectAead.WritePlan packed=ObjectAead.freshWrite(LIMIT,ObjectAead.Codec.ZLIB,ObjectAead.Purpose.BODY,1,EPOCH,ACCOUNT,100,2,new byte[32]);
        assertArrayEquals(data,open(packed.metadata(),seal(packed,data))); // opaque packed bytes, NOT a valid zlib body
    }

    @Test public void zeroReadFallbackAndCallerStreamOwnership()throws Exception{
        byte[] data={1,2,3};ObjectAead.WritePlan p=plan(data);AtomicBoolean closed=new AtomicBoolean(),flushed=new AtomicBoolean();
        InputStream zeros=new ByteArrayInputStream(data){@Override public int read(byte[] b,int off,int len){return 0;}@Override public void close(){closed.set(true);}};
        ByteArrayOutputStream out=new ByteArrayOutputStream(){@Override public void close(){closed.set(true);}@Override public void flush(){flushed.set(true);}};
        ObjectAead.seal(p,DEK,zeros,out,LIVE);assertFalse(closed.get());assertFalse(flushed.get());assertArrayEquals(data,open(p.metadata(),out.toByteArray()));
    }

    @Test public void cooperativeCancellationAfterFirstFrameDoesNotValidateWholeOutput()throws Exception{
        byte[] data=fill(32769,3);ObjectAead.WritePlan p=plan(data);byte[] wire=seal(p,data);AtomicBoolean stop=new AtomicBoolean();
        ByteArrayOutputStream stage=new ByteArrayOutputStream(){@Override public void write(byte[] b,int off,int len){super.write(b,off,len);stop.set(true);}};
        rejected(()->ObjectAead.open(p.metadata(),DEK,new ByteArrayInputStream(wire),stage,stop::get));assertEquals(32768,stage.size());
    }

    private static int sample(long offset){return (int)((offset*13)^(offset>>>8))&255;}
    @Test public void fourMiBGeneratedSourceUsesBoundedFramesAndPrivateDiskStaging()throws Exception{
        final int bytes=4*1024*1024;MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[32768];
        for(int at=0;at<bytes;at+=buffer.length){for(int i=0;i<buffer.length;i++)buffer[i]=(byte)sample(at+i);digest.update(buffer);}
        ObjectAead.WritePlan p=ObjectAead.freshWrite(LIMIT,ObjectAead.Codec.RAW,ObjectAead.Purpose.ATTACHMENT,1,EPOCH,ACCOUNT,bytes,bytes,digest.digest());
        InputStream source=new InputStream(){long at;@Override public int read(){return at==bytes?-1:sample(at++);}@Override public int read(byte[] b,int off,int len){assertTrue(len<=32768);if(at==bytes)return -1;int n=(int)Math.min(len,bytes-at);for(int i=0;i<n;i++)b[off+i]=(byte)sample(at++);return n;}};
        Path staged=Files.createTempFile("rb-object-", ".synthetic");
        try{
            try(OutputStream out=Files.newOutputStream(staged)){ObjectAead.seal(p,DEK,source,out,LIVE);}assertEquals(p.metadata().framedBytes(),Files.size(staged));
            final long[] read={0};OutputStream verify=new OutputStream(){@Override public void write(int b){assertEquals(sample(read[0]++),b&255);}@Override public void write(byte[] b,int off,int len){assertTrue(len<=32768);for(int i=0;i<len;i++)assertEquals(sample(read[0]++),b[off+i]&255);}};
            try(InputStream input=Files.newInputStream(staged)){ObjectAead.open(p.metadata(),DEK,input,verify,LIVE);}assertEquals(bytes,read[0]);
        }finally{Files.delete(staged);} // only this exact owned synthetic file, no application cleanup
    }
}
