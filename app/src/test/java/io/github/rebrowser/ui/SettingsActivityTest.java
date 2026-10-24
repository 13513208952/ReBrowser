package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** Static settings directory composition; values stay unknown until the owner observes them. */
public final class SettingsActivityTest {
    @Test public void everySectionAndRowIsUniqueAndBounded(){
        java.util.Set<String> ids=SettingsActivity.Rows.ids();
        assertEquals(SettingsActivity.Rows.count(),ids.size());
        assertEquals(3,SettingsActivity.Rows.sections().size());
        assertTrue(ids.contains("ui.row.source.safety"));
        assertTrue(SettingsActivity.Rows.count()<=SettingsActivity.MAX_ROWS);
    }
    @Test public void unobservedValuesStayInert(){
        SettingsActivity.Observed observed=new SettingsActivity.Observed(java.util.Collections.singletonMap("ui.row.general.recent",Boolean.TRUE),2);
        assertEquals(Boolean.TRUE,observed.value("ui.row.general.recent"));
        assertNull(observed.value("ui.row.search.engine"));
        assertFalse(SettingsSections.actionable(observed.value("ui.row.search.engine")));
        assertEquals("未读取",SettingsSections.observedValueText(null));
        assertEquals("已开启",SettingsSections.observedValueText(Boolean.TRUE));
    }
    @Test public void observedMapIsDefensiveAndRevisionRequired(){
        java.util.Map<String,Boolean> raw=new java.util.HashMap<>();
        raw.put("ui.row.general.recent",Boolean.FALSE);
        SettingsActivity.Observed observed=new SettingsActivity.Observed(raw,1);
        raw.put("ui.row.general.recent",Boolean.TRUE);
        assertEquals(Boolean.FALSE,observed.value("ui.row.general.recent"));
        assertThrows(IllegalArgumentException.class,()->new SettingsActivity.Observed(java.util.Collections.emptyMap(),0));
        assertThrows(IllegalArgumentException.class,()->new SettingsActivity.Observed(null,1));
    }
}
