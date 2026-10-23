package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import io.github.rebrowser.browser.SearchEngines;
import org.junit.Test;

/** Pure new-tab model: fixed providers and bounded shortcut row. */
public final class NewTabViewD03Test {
    private static final io.github.rebrowser.settings.SettingsRepository.Snapshot SETTINGS=new io.github.rebrowser.settings.SettingsRepository.Snapshot(0,SearchEngines.Provider.BING,false,false);
    @Test public void fourFixedProvidersWithChineseLabels(){
        assertEquals(4,NewTabView.Model.providers().size());
        assertEquals("必应",NewTabView.Model.providerLabel(SearchEngines.Provider.BING));
        assertEquals("百度",NewTabView.Model.providerLabel(SearchEngines.Provider.BAIDU));
        assertEquals("DuckDuckGo",NewTabView.Model.providerLabel(SearchEngines.Provider.DUCKDUCKGO));
        assertEquals("Google",NewTabView.Model.providerLabel(SearchEngines.Provider.GOOGLE));
    }
    @Test public void shortcutsAreBoundedAndIdentityChecked(){
        NewTabView.Shortcut shortcut=new NewTabView.Shortcut(new Object(),"标题","example.com");
        NewTabView.Model model=new NewTabView.Model(SETTINGS,java.util.Arrays.asList(shortcut));
        assertEquals(1,model.shortcuts().size());
        assertFalse(model.empty());
        assertThrows(UnsupportedOperationException.class,()->model.shortcuts().clear());
        assertThrows(IllegalArgumentException.class,()->new NewTabView.Model(SETTINGS,java.util.Arrays.asList(shortcut,shortcut)));
        assertThrows(IllegalArgumentException.class,()->new NewTabView.Shortcut(new Object(),"","x"));
        assertThrows(IllegalArgumentException.class,()->new NewTabView.Shortcut(new Object(),"t","bad host"));
    }
    @Test public void shortcutLimitAndTextBounds(){
        java.util.List<NewTabView.Shortcut> shortcuts=new java.util.ArrayList<>();
        for(int i=0;i<NewTabView.MAX_SHORTCUTS;i++)shortcuts.add(new NewTabView.Shortcut(new Object(),"t"+i,"h"));
        assertEquals(NewTabView.MAX_SHORTCUTS,new NewTabView.Model(SETTINGS,shortcuts).shortcuts().size());
        shortcuts.add(new NewTabView.Shortcut(new Object(),"extra","h"));
        assertThrows(IllegalArgumentException.class,()->new NewTabView.Model(SETTINGS,shortcuts));
        assertThrows(IllegalArgumentException.class,()->new NewTabView.Shortcut(new Object(),"bad\nline","h"));
    }
}
