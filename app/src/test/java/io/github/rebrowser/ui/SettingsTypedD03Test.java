package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** Typed settings directory + edit session; nothing is persisted. */
public final class SettingsTypedD03Test {
    @Test public void fixedSearchOptionsAreBoundedAndExplicit(){
        SettingsSections.Row search=SettingsSections.requireRow("ui.row.search.engine");
        assertEquals(SettingsSections.ValueKind.FIXED_OPTION,search.kind());
        assertEquals(4,search.options().size());
        assertEquals("bing",search.options().get(0).value());
        assertNull(search.findOption("unsupported"));
        assertEquals("未读取（不支持的选项）",SettingsSections.observedOptionText("nope",search.options()));
        assertThrows(IllegalArgumentException.class,()->SettingsSections.requireRow("ui.row.missing"));
        assertEquals(SettingsSections.ValueKind.READ_ONLY,SettingsSections.requireRow("ui.row.source.safety").kind());
    }
    @Test public void editSessionValidatesThenSubmitsOnce(){
        SettingsActivity.EditSession session=new SettingsActivity.EditSession(4);
        assertThrows(IllegalArgumentException.class,()->session.setBoolean("ui.row.source.safety",true)); // generic navigation is not a boolean preference
        assertEquals("不支持的选项，未提交",session.chooseOption("ui.row.search.engine","nope"));
        assertNull(session.chooseOption("ui.row.search.engine","google"));
        assertTrue(session.isDirty());
        java.util.List<SettingsActivity.Change> batch=session.beginAttempt();
        assertEquals(1,batch.size());
        assertEquals(4,batch.get(0).expectedRevision());
        assertTrue(session.beginAttempt().isEmpty());
        assertEquals("更改已提交，等待原生重新读取",session.chooseOption("ui.row.search.engine","bing"));
        assertFalse(session.cancel());
    }
    @Test public void unknownObservationStaysInertAndChangeShapeIsChecked(){
        SettingsActivity.Observed observed=new SettingsActivity.Observed(java.util.Collections.singletonMap("ui.row.general.recent",Boolean.TRUE),1);
        assertNull(observed.value("ui.row.search.engine"));
        assertNull(observed.option("ui.row.search.engine"));
        assertThrows(IllegalArgumentException.class,()->new SettingsActivity.Change("ui.row.general.recent",SettingsSections.ValueKind.BOOLEAN,null,null,1));
        assertThrows(IllegalArgumentException.class,()->new SettingsActivity.Change("ui.row.search.engine",SettingsSections.ValueKind.FIXED_OPTION,null,"nope",1));
        assertTrue(new SettingsActivity.Change("ui.row.search.engine",SettingsSections.ValueKind.FIXED_OPTION,null,"bing",3).expectedRevision()==3);
    }
}
