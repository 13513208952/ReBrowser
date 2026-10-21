package io.github.rebrowser.music;

import static org.junit.Assert.*;
import org.junit.Test;

/** Pure signature/sampling plan; no Android decode or network access. */
public final class MusicArtworkTest {
    private static byte[] jpeg(){byte[] bytes=new byte[16];bytes[0]=(byte)0xff;bytes[1]=(byte)0xd8;bytes[2]=(byte)0xff;return bytes;}
    private static byte[] png(){byte[] bytes=new byte[16];bytes[0]=(byte)0x89;bytes[1]='P';bytes[2]='N';bytes[3]='G';return bytes;}
    private static byte[] webp(){byte[] bytes=new byte[16];bytes[0]='R';bytes[1]='I';bytes[2]='F';bytes[3]='F';bytes[8]='W';bytes[9]='E';bytes[10]='B';bytes[11]='P';return bytes;}
    @Test public void signatureSniffingIsExact(){
        assertEquals(MusicArtwork.Format.JPEG,MusicArtwork.sniff(jpeg()));
        assertEquals(MusicArtwork.Format.PNG,MusicArtwork.sniff(png()));
        assertEquals(MusicArtwork.Format.WEBP,MusicArtwork.sniff(webp()));
        assertEquals(MusicArtwork.Format.UNKNOWN,MusicArtwork.sniff(new byte[16]));
        assertEquals(MusicArtwork.Format.UNKNOWN,MusicArtwork.sniff(null));
    }
    @Test public void samplingKeepsDecodedPixelsWithinBudget(){
        MusicArtwork.Plan plan=MusicArtwork.plan(4000,4000);
        assertTrue(plan.decodedPixels()<=MusicArtwork.MAX_DECODED_PIXELS);
        assertEquals(4,plan.sampleSize());
        assertEquals(1,MusicArtwork.plan(800,600).sampleSize());
        assertThrows(IllegalArgumentException.class,()->MusicArtwork.plan(0,10));
        assertThrows(IllegalArgumentException.class,()->MusicArtwork.plan(5000,10));
    }
    @Test public void generationSupersedesEarlierDecodeRequests(){
        MusicArtwork artwork=new MusicArtwork();
        assertEquals(1,artwork.begin(1));
        assertEquals(5,artwork.begin(5));
        assertEquals(5,artwork.latestGeneration());
        assertEquals(0,artwork.begin(3)); // cannot retarget old bytes by returning latest generation
        assertEquals(0,artwork.begin(5));
        assertThrows(IllegalArgumentException.class,()->artwork.begin(0));
        assertThrows(IllegalArgumentException.class,()->artwork.decodeOwned(null,5));
        assertThrows(IllegalArgumentException.class,()->artwork.decodeOwned(new byte[MusicArtwork.MAX_ENCODED_BYTES+1],5));
    }
}
