package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** New-tab query validation and actual recent-visibility setting; no navigation occurs. */
public final class NewTabQueryD03Test {
    @Test public void blankOversizeAndBrokenUtf16AreRejectedLocally(){
        assertThrows(IllegalArgumentException.class,()->NewTabView.Query.require(null));
        assertThrows(IllegalArgumentException.class,()->NewTabView.Query.require("   "));
        assertThrows(IllegalArgumentException.class,()->NewTabView.Query.require(new String(new char[NewTabView.MAX_QUERY_CHARS+1]).replace('\0','a')));
        assertThrows(IllegalArgumentException.class,()->NewTabView.Query.require("\ud800"));
        assertEquals("abc",NewTabView.Query.require("  abc  ").trim());
    }
    @Test public void shortcutModelUsesRecentSettingNotInventedFavoriteManagement(){
        NewTabView.Shortcut shortcut=new NewTabView.Shortcut(new Object(),"标题","example.com");
        io.github.rebrowser.settings.SettingsRepository.Snapshot settings=new io.github.rebrowser.settings.SettingsRepository.Snapshot(0,io.github.rebrowser.browser.SearchEngines.Provider.BING,false,false);
        assertFalse(new NewTabView.Model(settings,java.util.Arrays.asList(shortcut)).empty());
        assertTrue(new NewTabView.Model(settings.withNewTab(settings.newTabBackground(),false),java.util.Arrays.asList(shortcut)).empty());
        assertThrows(IllegalArgumentException.class,()->new NewTabView.Model(settings,java.util.Arrays.asList(shortcut,shortcut)));
        assertThrows(IllegalArgumentException.class,()->new NewTabView.Shortcut(new Object(),"t","bad host"));
    }
}
