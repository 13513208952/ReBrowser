package io.github.rebrowser.engine;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
/** Actual p2 decoder only, synthetic future-producer bytes; no SessionState capture/restore claim. */
public final class SessionCheckpointStoreTest {
 private static byte[] wire(byte[] body)throws Exception{byte[] engine="158.0.20261007201254".getBytes(StandardCharsets.US_ASCII);ByteBuffer b=ByteBuffer.allocate(56+engine.length+body.length).order(ByteOrder.BIG_ENDIAN);b.putLong(0x524243484b303100L).putShort((short)1).putShort((short)engine.length).put(engine).put(new byte[16]).put(new byte[16]).putLong(1).putInt(body.length).put(body);return b.array();}
 private static byte[] good()throws Exception{return wire("{\"x\":\"😀\"}".getBytes(StandardCharsets.UTF_8));}
 @Test public void exactOpaqueUtf8BytesRetainedWithoutSdkSerialization()throws Exception{byte[] raw=good();ByteBuffer in=ByteBuffer.wrap(raw);assertArrayEquals("{\"x\":\"😀\"}".getBytes(StandardCharsets.UTF_8),SessionCheckpointStore.decodeOpaque(in,new byte[16],new byte[16]));assertEquals(0,in.position());}
 @Test public void badMagicAndVersionReject()throws Exception{byte[] raw=good();raw[0]^=1;assertThrows(IllegalArgumentException.class,()->SessionCheckpointStore.decodeOpaque(ByteBuffer.wrap(raw),new byte[16],new byte[16]));byte[] v=good();v[9]=2;assertThrows(IllegalArgumentException.class,()->SessionCheckpointStore.decodeOpaque(ByteBuffer.wrap(v),new byte[16],new byte[16]));}
 @Test public void changedEngineIsNotSilentlyRestored()throws Exception{byte[] raw=good();raw[12]='2';assertThrows(IllegalArgumentException.class,()->SessionCheckpointStore.decodeOpaque(ByteBuffer.wrap(raw),new byte[16],new byte[16]));}
 @Test public void wrongWorkspaceOrTabCannotRetargetCheckpoint()throws Exception{byte[] id=new byte[16];id[0]=9;byte[] raw=good();assertThrows(IllegalArgumentException.class,()->SessionCheckpointStore.decodeOpaque(ByteBuffer.wrap(raw),id,new byte[16]));assertThrows(IllegalArgumentException.class,()->SessionCheckpointStore.decodeOpaque(ByteBuffer.wrap(raw),new byte[16],id));}
 @Test public void allTruncationsAndTailRejected()throws Exception{byte[] raw=good();for(int i=0;i<raw.length;i++){byte[] cut=Arrays.copyOf(raw,i);assertThrows(IllegalArgumentException.class,()->SessionCheckpointStore.decodeOpaque(ByteBuffer.wrap(cut),new byte[16],new byte[16]));}assertThrows(IllegalArgumentException.class,()->SessionCheckpointStore.decodeOpaque(ByteBuffer.wrap(Arrays.copyOf(raw,raw.length+1)),new byte[16],new byte[16]));}
 @Test public void invalidUtf8RejectedWithoutRepair()throws Exception{for(byte[] b:new byte[][]{{(byte)0xc0,(byte)0x80},{(byte)0xed,(byte)0xa0,(byte)0x80},{(byte)0xf4,(byte)0x90,(byte)0x80,(byte)0x80}}){byte[] raw=wire(b);assertThrows(IllegalArgumentException.class,()->SessionCheckpointStore.decodeOpaque(ByteBuffer.wrap(raw),new byte[16],new byte[16]));}}
 @Test public void utf8ValidatorCrossesSmallScratchBoundaries()throws Exception{StringBuilder s=new StringBuilder("{\"x\":\"");for(int i=0;i<6000;i++)s.append("😀");s.append("\"}");byte[] body=s.toString().getBytes(StandardCharsets.UTF_8);assertArrayEquals(body,SessionCheckpointStore.decodeOpaque(ByteBuffer.wrap(wire(body)),new byte[16],new byte[16]));}
 @Test public void overRecordBoundRejectedBeforeDecode()throws Exception{assertThrows(IllegalArgumentException.class,()->SessionCheckpointStore.decodeOpaque(ByteBuffer.wrap(new byte[2*1024*1024+1]),new byte[16],new byte[16]));}
 @Test public void nullWrongLengthIdsCannotBecomeScopes()throws Exception{byte[] raw=good();assertThrows(IllegalArgumentException.class,()->SessionCheckpointStore.decodeOpaque(ByteBuffer.wrap(raw),null,new byte[16]));assertThrows(IllegalArgumentException.class,()->SessionCheckpointStore.decodeOpaque(ByteBuffer.wrap(raw),new byte[16],new byte[15]));}
 @Test public void noPublicConstructorOrCaptureApi(){assertEquals(0,SessionCheckpointStore.ReadView.class.getConstructors().length);for(java.lang.reflect.Method m:SessionCheckpointStore.class.getMethods())assertFalse(m.getName().equals("capture"));}
}
