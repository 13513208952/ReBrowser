package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** Bounded option projection; unknown observed values cannot be acted on. */
public final class SubapplicationSettingsActivityTest {
    private static java.util.Set<String> allowed(String... values){return new java.util.LinkedHashSet<>(java.util.Arrays.asList(values));}
    @Test public void optionsValidateDomainAndObservedValue(){
        SubapplicationSettingsActivity.Option option=new SubapplicationSettingsActivity.Option("src","音源",allowed("官方网页"),"官方网页");
        assertEquals("官方网页",option.observed());
        SubapplicationSettingsActivity.Options options=new SubapplicationSettingsActivity.Options("music",java.util.Arrays.asList(option),3);
        assertEquals(3,options.revision());
        assertEquals("music",options.subapplicationId());
        assertThrows(IllegalArgumentException.class,()->new SubapplicationSettingsActivity.Option("src","音源",allowed("官方网页"),"其他"));
        assertThrows(IllegalArgumentException.class,()->new SubapplicationSettingsActivity.Option("src","音源",java.util.Collections.emptySet(),null));
        assertThrows(IllegalArgumentException.class,()->new SubapplicationSettingsActivity.Option("bad token","x",allowed("a"),null));
    }
    @Test public void duplicateTokensAndUnknownSubapplicationRejected(){
        SubapplicationSettingsActivity.Option one=new SubapplicationSettingsActivity.Option("a","A",allowed("x"),null);
        SubapplicationSettingsActivity.Option two=new SubapplicationSettingsActivity.Option("a","B",allowed("y"),null);
        assertThrows(IllegalArgumentException.class,()->new SubapplicationSettingsActivity.Options("music",java.util.Arrays.asList(one,two),1));
        assertThrows(IllegalArgumentException.class,()->new SubapplicationSettingsActivity.Options("browser",java.util.Arrays.asList(one),1));
        assertThrows(IllegalArgumentException.class,()->new SubapplicationSettingsActivity.Options("music",java.util.Arrays.asList(one),0));
    }
}
