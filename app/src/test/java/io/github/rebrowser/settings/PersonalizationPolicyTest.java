package io.github.rebrowser.settings;

import static org.junit.Assert.*;
import org.junit.Test;

/** Pure geometry/contrast suggestions; no URI, grant or file access. */
public final class PersonalizationPolicyTest {
    @Test public void centerCropKeepsAspectAndStaysInsideSource(){
        PersonalizationPolicy.Crop crop=PersonalizationPolicy.centerCrop(4000,3000,360,780,3f);
        assertTrue(crop.cropX()>=0 && crop.cropY()>=0);
        assertTrue(crop.cropX()+crop.cropWidth()<=4000);
        assertTrue(crop.cropY()+crop.cropHeight()<=3000);
        assertEquals(1080,crop.outputWidth());
        assertEquals(2340,crop.outputHeight());
    }
    @Test public void invalidSizesAndDensityRejected(){
        assertThrows(IllegalArgumentException.class,()->PersonalizationPolicy.centerCrop(0,10,360,780,3f));
        assertThrows(IllegalArgumentException.class,()->PersonalizationPolicy.centerCrop(9000,9000,360,780,3f));
        assertThrows(IllegalArgumentException.class,()->PersonalizationPolicy.centerCrop(10,10,360,780,0f));
        assertThrows(IllegalArgumentException.class,()->PersonalizationPolicy.centerCrop(10,10,360,780,Float.NaN));
    }
    @Test public void textToneUsesContrastNotGuesswork(){
        assertEquals(PersonalizationPolicy.TextTone.LIGHT,PersonalizationPolicy.textToneFor(0xff101014));
        assertEquals(PersonalizationPolicy.TextTone.DARK,PersonalizationPolicy.textToneFor(0xfffafafa));
        assertTrue(PersonalizationPolicy.contrastRatio(0xffffffff,0xff000000)>20);
        assertEquals(1.0,PersonalizationPolicy.contrastRatio(0xff123456,0xff123456),0.0001);
    }
    @Test public void externalArtworkStaysUnavailableWithoutNativeAuthorization(){
        assertTrue(PersonalizationPolicy.externalArtworkHint(false).contains("不可用"));
        assertTrue(PersonalizationPolicy.externalArtworkHint(true).contains("授权"));
    }
}
