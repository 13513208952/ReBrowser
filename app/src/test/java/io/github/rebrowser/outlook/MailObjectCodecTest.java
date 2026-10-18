package io.github.rebrowser.outlook;

import static org.junit.Assert.*;

import io.github.rebrowser.crypto.ObjectAead;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import org.junit.Test;

/** HOST exact-byte codec and public-dummy-key staged pipeline, not mailbox/DB/site tests. */
public final class MailObjectCodecTest {
    private static final MailObjectCodec.Limits LIMIT=new MailObjectCodec.Limits(1024*1024,1024*1024);
    private interface Checked{void run()throws Exception;}
    private static byte[] sha(byte[] b)throws Exception{return MessageDigest.getInstance("SHA-256").digest(b);}
    private static void rejected(Checked action)throws Exception{try{action.run();fail("invalid encoding/limit must fail");}catch(IOException|IllegalArgumentException expected){}}
    private static final class Stages{
        final ByteArrayOutputStream raw=new ByteArrayOutputStream(),packed=new ByteArrayOutputStream();
        final MailObjectCodec.Prepared prepared;
        Stages(byte[] bytes,MailObjectCodec.Limits limits,MailObjectCodec.Level level)throws Exception{prepared=MailObjectCodec.prepare(new ByteArrayInputStream(bytes),raw,packed,limits,level,()->false);}
        byte[] selected(){return prepared.codec()==ObjectAead.Codec.RAW?raw.toByteArray():packed.toByteArray();}
    }
    private static byte[] decode(ObjectAead.Codec codec,long original,byte[] packed,byte[] digest)throws Exception{
        ByteArrayOutputStream stage=new ByteArrayOutputStream();MailObjectCodec.decode(codec,original,packed.length,digest,LIMIT,new ByteArrayInputStream(packed),stage,()->false);return stage.toByteArray();
    }
    private static byte[] compress(byte[] bytes,boolean dictionary)throws Exception{
        Deflater d=new Deflater(9);ByteArrayOutputStream out=new ByteArrayOutputStream();
        try{if(dictionary)d.setDictionary("synthetic-dictionary".getBytes(StandardCharsets.UTF_8));try(DeflaterOutputStream stream=new DeflaterOutputStream(out,d)){stream.write(bytes);}}finally{d.end();}return out.toByteArray();
    }
    private static long framed(long n){return 124+n+20*Math.max(1,(n+32767)/32768);}

    @Test public void losslessOriginalBytesNeverStripHtmlUnicodeOrWhitespace()throws Exception{
        byte[] bytes="  <html>\n<script>synthetic only</script>合成😀\0</html> \r\n".getBytes(StandardCharsets.UTF_8);
        Stages s=new Stages(bytes,LIMIT,MailObjectCodec.Level.DENSE);
        assertArrayEquals(bytes,s.raw.toByteArray());assertArrayEquals(sha(bytes),s.prepared.originalSha256());
        assertArrayEquals(bytes,decode(s.prepared.codec(),bytes.length,s.selected(),s.prepared.originalSha256()));
        byte[] copy=s.prepared.originalSha256();copy[0]^=1;assertArrayEquals(sha(bytes),s.prepared.originalSha256());
    }

    @Test public void emptySmallAndRandomExpansionKeepRaw()throws Exception{
        for(int n:new int[]{0,1,12,512,32768,32769}){
            byte[] bytes=new byte[n];new Random(n).nextBytes(bytes);Stages s=new Stages(bytes,LIMIT,MailObjectCodec.Level.BALANCED);
            assertEquals(ObjectAead.Codec.RAW,s.prepared.codec());assertArrayEquals(bytes,s.selected());assertArrayEquals(bytes,decode(s.prepared.codec(),n,s.selected(),s.prepared.originalSha256()));
        }
    }

    @Test public void framingInclusiveSavingThresholdExactlyControlsSelection()throws Exception{
        for(MailObjectCodec.Level level:MailObjectCodec.Level.values())for(int tail:new int[]{0,32,100,300,1000,4000}){
            byte[] bytes=new byte[4096];new Random(123).nextBytes(bytes);Arrays.fill(bytes,bytes.length-tail,bytes.length,(byte)0);
            Stages s=new Stages(bytes,LIMIT,level);assertTrue(s.prepared.candidateComplete());
            long raw=framed(bytes.length),candidate=framed(s.packed.size()),min=Math.max(32,(raw+19)/20);
            assertEquals(raw-candidate>=min?ObjectAead.Codec.ZLIB:ObjectAead.Codec.RAW,s.prepared.codec());
            assertArrayEquals(bytes,decode(s.prepared.codec(),bytes.length,s.selected(),s.prepared.originalSha256()));
        }
    }

    @Test public void compressibleBoundariesUseZlibAndRoundTrip()throws Exception{
        for(int n:new int[]{128,32768,32769,65537,262144}){
            byte[] bytes=new byte[n];Stages s=new Stages(bytes,LIMIT,MailObjectCodec.Level.DENSE);
            assertEquals(ObjectAead.Codec.ZLIB,s.prepared.codec());assertEquals(MailObjectCodec.Selection.FRAMED_SAVING,s.prepared.selection());
            assertEquals(s.prepared.packedBytes(),s.packed.size());assertArrayEquals(bytes,decode(s.prepared.codec(),n,s.selected(),s.prepared.originalSha256()));
        }
    }

    @Test public void candidateCapFallsBackWithoutOverrunButCannotTruncateRawToFit()throws Exception{
        byte[] bytes=new byte[32];new Random(4).nextBytes(bytes);
        Stages s=new Stages(bytes,new MailObjectCodec.Limits(32,32),MailObjectCodec.Level.DENSE);
        assertEquals(ObjectAead.Codec.RAW,s.prepared.codec());assertFalse(s.prepared.candidateComplete());
        assertEquals(MailObjectCodec.Selection.CANDIDATE_BUDGET,s.prepared.selection());assertTrue(s.packed.size()<=32);assertArrayEquals(bytes,s.raw.toByteArray());
        rejected(()->new Stages(new byte[]{1},new MailObjectCodec.Limits(1,0),MailObjectCodec.Level.DENSE));
        rejected(()->new Stages(new byte[33],new MailObjectCodec.Limits(32,32),MailObjectCodec.Level.DENSE));
    }

    @Test public void truncatedTrailingDictionaryAndInvalidZlibAreRejected()throws Exception{
        byte[] bytes=new byte[1000],good=compress(bytes,false);
        for(int n=0;n<good.length;n++){byte[] cut=Arrays.copyOf(good,n);rejected(()->decode(ObjectAead.Codec.ZLIB,bytes.length,cut,sha(bytes)));}
        rejected(()->decode(ObjectAead.Codec.ZLIB,bytes.length,Arrays.copyOf(good,good.length+1),sha(bytes)));
        rejected(()->decode(ObjectAead.Codec.ZLIB,bytes.length,compress(bytes,true),sha(bytes)));
        rejected(()->decode(ObjectAead.Codec.ZLIB,0,new byte[]{1,2,3},sha(new byte[0])));
        byte[] concatenated=new byte[good.length*2];System.arraycopy(good,0,concatenated,0,good.length);System.arraycopy(good,0,concatenated,good.length,good.length);
        rejected(()->decode(ObjectAead.Codec.ZLIB,bytes.length,concatenated,sha(bytes)));
    }

    @Test public void outputBoundAndDigestFailuresLeaveOnlyUnpublishedStaging()throws Exception{
        byte[] bytes=new byte[65537],packed=compress(bytes,false);ByteArrayOutputStream stage=new ByteArrayOutputStream();
        rejected(()->MailObjectCodec.decode(ObjectAead.Codec.ZLIB,10,packed.length,sha(bytes),LIMIT,new ByteArrayInputStream(packed),stage,()->false));assertEquals(0,stage.size());
        ByteArrayOutputStream late=new ByteArrayOutputStream();
        rejected(()->MailObjectCodec.decode(ObjectAead.Codec.ZLIB,bytes.length,packed.length,new byte[32],LIMIT,new ByteArrayInputStream(packed),late,()->false));
        assertEquals(bytes.length,late.size()); // tainted whole stage despite complete inflate; no publish success
        rejected(()->decode(ObjectAead.Codec.RAW,1,new byte[2],sha(new byte[2])));
    }

    @Test public void declaredLimitsAreCheckedBeforeReadOrAllocation()throws Exception{
        rejected(()->new MailObjectCodec.Limits(-1,0));rejected(()->new MailObjectCodec.Limits(0,-1));
        rejected(()->new MailObjectCodec.Limits(Long.MAX_VALUE,1));
        InputStream never=new InputStream(){public int read(){throw new AssertionError("limit must precede read");}};
        rejected(()->MailObjectCodec.decode(ObjectAead.Codec.ZLIB,100,1,new byte[32],new MailObjectCodec.Limits(1,1),never,new ByteArrayOutputStream(),()->false));
        rejected(()->MailObjectCodec.decode(ObjectAead.Codec.ZLIB,1,100,new byte[32],new MailObjectCodec.Limits(1,1),never,new ByteArrayOutputStream(),()->false));
    }

    @Test public void zeroReadFallbackDoesNotOwnOrFlushCallerStreams()throws Exception{
        byte[] bytes={1,2,3};AtomicBoolean closed=new AtomicBoolean(),flushed=new AtomicBoolean();
        InputStream source=new ByteArrayInputStream(bytes){@Override public int read(byte[] b,int off,int len){return 0;}@Override public void close(){closed.set(true);}};
        ByteArrayOutputStream raw=new ByteArrayOutputStream(){@Override public void close(){closed.set(true);}@Override public void flush(){flushed.set(true);}},candidate=new ByteArrayOutputStream();
        MailObjectCodec.Prepared p=MailObjectCodec.prepare(source,raw,candidate,LIMIT,MailObjectCodec.Level.DENSE,()->false);
        assertArrayEquals(bytes,raw.toByteArray());assertFalse(closed.get());assertFalse(flushed.get());
        InputStream zeros=new ByteArrayInputStream(raw.toByteArray()){@Override public int read(byte[] b,int off,int len){return 0;}};
        ByteArrayOutputStream decoded=new ByteArrayOutputStream();MailObjectCodec.decode(ObjectAead.Codec.RAW,3,3,p.originalSha256(),LIMIT,zeros,decoded,()->false);assertArrayEquals(bytes,decoded.toByteArray());
        rejected(()->MailObjectCodec.prepare(new ByteArrayInputStream(bytes),raw,raw,LIMIT,MailObjectCodec.Level.DENSE,()->false));
    }

    @Test public void cancellationIsCooperativeAndNeverAnAutomaticPublication()throws Exception{
        rejected(()->MailObjectCodec.prepare(new ByteArrayInputStream(new byte[0]),new ByteArrayOutputStream(),new ByteArrayOutputStream(),LIMIT,MailObjectCodec.Level.DENSE,()->true));
        byte[] bytes=new byte[65537],packed=compress(bytes,false);AtomicBoolean stop=new AtomicBoolean();
        ByteArrayOutputStream stage=new ByteArrayOutputStream(){@Override public void write(byte[] b,int off,int len){super.write(b,off,len);stop.set(true);}};
        rejected(()->MailObjectCodec.decode(ObjectAead.Codec.ZLIB,bytes.length,packed.length,sha(bytes),LIMIT,new ByteArrayInputStream(packed),stage,stop::get));
        assertTrue(stage.size()>0);assertTrue(stage.size()<bytes.length);
    }

    @Test public void privateHostPrepareSealOpenDecodePipelineValidatesOriginal()throws Exception{
        for(int length:new int[]{0,1,32769,65537}){
            byte[] bytes=new byte[length];if(length==1)bytes[0]=7;Stages stages=new Stages(bytes,LIMIT,MailObjectCodec.Level.DENSE);
            byte[] PUBLIC_DUMMY_DEK=new byte[32];Arrays.fill(PUBLIC_DUMMY_DEK,(byte)0x11);
            ObjectAead.WritePlan plan=ObjectAead.freshWrite(new ObjectAead.Limits(1024*1024,1024*1024),stages.prepared.codec(),ObjectAead.Purpose.BODY,1,new byte[16],new byte[16],length,stages.prepared.packedBytes(),stages.prepared.originalSha256());
            ByteArrayOutputStream PRIVATE_CIPHER=new ByteArrayOutputStream();ObjectAead.seal(plan,PUBLIC_DUMMY_DEK,new ByteArrayInputStream(stages.selected()),PRIVATE_CIPHER,()->false);
            ByteArrayOutputStream PRIVATE_PACKED=new ByteArrayOutputStream();ObjectAead.open(plan.metadata(),PUBLIC_DUMMY_DEK,new ByteArrayInputStream(PRIVATE_CIPHER.toByteArray()),PRIVATE_PACKED,()->false);
            assertArrayEquals(bytes,decode(stages.prepared.codec(),length,PRIVATE_PACKED.toByteArray(),stages.prepared.originalSha256()));
            // No SQLite refs/cursor, current epoch/fence or public/export destination is involved.
        }
    }
}
