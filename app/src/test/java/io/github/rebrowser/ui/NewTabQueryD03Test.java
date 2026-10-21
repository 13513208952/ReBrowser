package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** New-tab query validation and offered-management model; no navigation occurs. */
public final class NewTabQueryD03Test {
    @Test public void blankOversizeAndBrokenUtf16AreRejectedLocally(){
        assertThrows(IllegalArgumentException.class,()->NewTabView.Query.require(null));
        assertThrows(IllegalArgumentException.class,()->NewTabView.Query.require("   "));
        assertThrows(IllegalArgumentException.class,()->NewTabView.Query.require(new String(new char[NewTabView.MAX_QUERY_CHARS+1]).replace('\0','a')));
        assertThrows(IllegalArgumentException.class,()->NewTabView.Query.require("\ud800"));
        assertEquals("abc",NewTabView.Query.require("  abc  ").trim());
    }
    @Test public void shortcutModelCarriesManageOfferAndRejectsDuplicates(){
        NewTabView.Shortcut shortcut=new NewTabView.Shortcut(new Object(),"标题","example.com");
        assertFalse(new NewTabView.Model(java.util.Arrays.asList(shortcut),false).manageOffered());
        assertTrue(new NewTabView.Model(java.util.Arrays.asList(shortcut)).manageOffered());
        assertThrows(IllegalArgumentException.class,()->new NewTabView.Model(java.util.Arrays.asList(shortcut,shortcut)));
        assertThrows(IllegalArgumentException.class,()->new NewTabView.Shortcut(new Object(),"t","例子.测试"));
    }
}
