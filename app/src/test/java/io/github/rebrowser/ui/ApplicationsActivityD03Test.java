package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import io.github.rebrowser.subapps.SubapplicationRegistry;
import org.junit.Test;

/** Enablement is a reported projection; absence means unknown, never "disabled". */
public final class ApplicationsActivityD03Test {
    @Test public void missingStateIsUnknownNotDisabled(){
        ApplicationsActivity.Projection projection=new ApplicationsActivity.Projection(java.util.Collections.emptyMap(),1);
        assertEquals(ApplicationsActivity.Enablement.UNKNOWN,projection.state(SubapplicationRegistry.require("music")));
        assertEquals("启用状态未知",ApplicationsActivity.Projection.label(ApplicationsActivity.Enablement.UNKNOWN));
    }
    @Test public void reportedStatesMapToExactLabels(){
        java.util.Map<String,ApplicationsActivity.Enablement> states=new java.util.HashMap<>();
        states.put("outlook",ApplicationsActivity.Enablement.REPORTED_ENABLED);
        states.put("music",ApplicationsActivity.Enablement.REPORTED_DISABLED);
        ApplicationsActivity.Projection projection=new ApplicationsActivity.Projection(states,4);
        assertEquals(ApplicationsActivity.Enablement.REPORTED_ENABLED,projection.state(SubapplicationRegistry.require("outlook")));
        assertEquals("已停用（报告）",ApplicationsActivity.Projection.label(ApplicationsActivity.Enablement.REPORTED_DISABLED));
        assertEquals(4,projection.revision());
    }
    @Test public void unknownSubapplicationAndNullStateRejected(){
        java.util.Map<String,ApplicationsActivity.Enablement> states=new java.util.HashMap<>();
        states.put("browser",ApplicationsActivity.Enablement.REPORTED_ENABLED);
        assertThrows(IllegalArgumentException.class,()->new ApplicationsActivity.Projection(states,1));
        states.clear();states.put("music",null);
        assertThrows(IllegalArgumentException.class,()->new ApplicationsActivity.Projection(states,1));
        assertThrows(IllegalArgumentException.class,()->ApplicationsActivity.Projection.label(null));
    }
}
