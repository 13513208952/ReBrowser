package io.github.rebrowser.outlook;

import static org.junit.Assert.*;
import org.junit.Test;

/** Pure RBMOBJ1 header/framing codec; authentication and key derivation stay elsewhere. */
public final class MailObjectFormatTest {
    private static byte[] bytes(int length,int seed){byte[] value=new byte[length];for(int i=0;i<length;i++)value[i]=(byte)(seed+i);return value;}
    private static MailObjectFormat.Header header(MailObjectFormat.Codec codec,long original,long packed){
        return MailObjectFormat.header(codec,MailObjectFormat.Purpose.BODY,3,bytes(16,1),bytes(16,2),bytes(16,3),original,packed,bytes(32,4));
    }
    @Test public void headerIsExactly124BytesWithDocumentedFieldOrder(){
        byte[] encoded=MailObjectFormat.encode(header(MailObjectFormat.Codec.ZLIB,100,40));
        assertEquals(124,encoded.length);
        assertEquals('R',encoded[0]);assertEquals('B',encoded[1]);assertEquals('M',encoded[2]);assertEquals('O',encoded[3]);
        assertEquals('B',encoded[4]);assertEquals('J',encoded[5]);assertEquals('1',encoded[6]);assertEquals(0,encoded[7]);
        assertEquals(0,encoded[8]);assertEquals(1,encoded[9]);
        assertEquals(0,encoded[10]);assertEquals(1,encoded[11]);
        assertEquals(0,encoded[12]);assertEquals(1,encoded[13]);
        assertEquals((byte)3,encoded[19]);
    }
    @Test public void frameCountAndStoredLengthUseCeilingWithEmptySingleFrame(){
        assertEquals(1,MailObjectFormat.frames(0));
        assertEquals(1,MailObjectFormat.frames(1));
        assertEquals(1,MailObjectFormat.frames(32_768));
        assertEquals(2,MailObjectFormat.frames(32_769));
        MailObjectFormat.Header empty=header(MailObjectFormat.Codec.RAW,0,0);
        assertEquals(1,empty.frameCount());
        assertEquals(124L+20,empty.storedBytes());
        MailObjectFormat.Header full=header(MailObjectFormat.Codec.ZLIB,40_000,32_769);
        assertEquals(2,full.frameCount());
        assertEquals(124L+32_769+2*20,full.storedBytes());
    }
    @Test public void rawCodecRequiresEqualLengths(){
        assertThrows(IllegalArgumentException.class,()->header(MailObjectFormat.Codec.RAW,100,99));
        assertNotNull(header(MailObjectFormat.Codec.ZLIB,100,99));
        assertThrows(IllegalArgumentException.class,()->MailObjectFormat.encode(null));
    }
    @Test public void decodeRoundTripsAndRejectsTampering(){
        MailObjectFormat.Header original=header(MailObjectFormat.Codec.ZLIB,1000,400);
        byte[] encoded=MailObjectFormat.encode(original);
        MailObjectFormat.Header decoded=MailObjectFormat.decode(encoded);
        assertTrue(MailObjectFormat.matchesCatalog(decoded,original));
        assertEquals(1000,decoded.originalBytes());
        assertEquals(400,decoded.packedBytes());
        assertEquals(3,decoded.keyVersion());
        byte[] tampered=encoded.clone();tampered[91]=0;
        assertThrows(IllegalArgumentException.class,()->MailObjectFormat.decode(tampered));
        byte[] wrongVersion=encoded.clone();wrongVersion[9]=2;
        assertThrows(IllegalArgumentException.class,()->MailObjectFormat.decode(wrongVersion));
        byte[] wrongFlags=encoded.clone();wrongFlags[11]=0;
        assertThrows(IllegalArgumentException.class,()->MailObjectFormat.decode(wrongFlags));
        byte[] wrongFrame=encoded.clone();wrongFrame[87]=(byte)0x80;
        assertThrows(IllegalArgumentException.class,()->MailObjectFormat.decode(wrongFrame));
        assertThrows(IllegalArgumentException.class,()->MailObjectFormat.decode(new byte[123]));
    }
    @Test public void catalogComparisonIsFieldExactNotHeaderTrust(){
        MailObjectFormat.Header original=header(MailObjectFormat.Codec.RAW,10,10);
        MailObjectFormat.Header other=MailObjectFormat.header(MailObjectFormat.Codec.RAW,MailObjectFormat.Purpose.ATTACHMENT,3,bytes(16,1),bytes(16,2),bytes(16,3),10,10,bytes(32,4));
        assertFalse(MailObjectFormat.matchesCatalog(original,other));
        assertFalse(MailObjectFormat.matchesCatalog(original,null));
        assertEquals(original.storedBytes(),MailObjectFormat.requireStoredBytes(original,original.storedBytes()));
        assertThrows(IllegalArgumentException.class,()->MailObjectFormat.requireStoredBytes(original,original.storedBytes()-1));
    }
}
