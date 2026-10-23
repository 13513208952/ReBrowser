package io.github.rebrowser.ui;
import org.junit.Test;
import static org.junit.Assert.*;
/** The original independent history controls, not runtime Android rendering. */
public final class HistoryControlsTest {
 @Test public void maintenanceDisablesOnlyTimedClearNotRecordingControl(){HistoryActivity.Controls c=new HistoryActivity.Controls(true,false,false);assertTrue(c.enabled(HistoryActivity.Control.MAINTAIN));assertFalse(c.enabled(HistoryActivity.Control.CLEAR_TIMED));assertTrue(c.enabled(HistoryActivity.Control.PAUSE_RECORDING));}
 @Test public void pauseDoesNotImplicitlyMaintainOrDisableTimedClear(){HistoryActivity.Controls c=new HistoryActivity.Controls(false,true,false);assertTrue(c.enabled(HistoryActivity.Control.CLEAR_TIMED));assertFalse(c.maintaining());assertTrue(c.paused());assertTrue(c.label(HistoryActivity.Control.PAUSE_RECORDING).contains("同步当前"));}
 @Test public void missingNativeStateAndPendingOperationNeverOfferControl(){for(HistoryActivity.Controls c:new HistoryActivity.Controls[]{new HistoryActivity.Controls(null,null,false),new HistoryActivity.Controls(false,null,false),new HistoryActivity.Controls(false,false,true)})for(HistoryActivity.Control op:HistoryActivity.Control.values())assertFalse(c.enabled(op));}
 @Test public void uiClearIsOneActionNotAStoredSwitch(){HistoryActivity.ControlIntent i=new HistoryActivity.ControlIntent(HistoryActivity.Control.CLEAR_TIMED,false,4);assertEquals(HistoryActivity.Control.CLEAR_TIMED,i.control());assertEquals(4,i.expectedRevision());assertThrows(IllegalArgumentException.class,()->new HistoryActivity.ControlIntent(null,true,1));assertThrows(IllegalArgumentException.class,()->new HistoryActivity.ControlIntent(HistoryActivity.Control.MAINTAIN,true,0));}
 @Test public void knownThreeLabelsPreserveExistingMeaning(){HistoryActivity.Controls c=new HistoryActivity.Controls(true,false,false);assertTrue(c.label(HistoryActivity.Control.MAINTAIN).contains("30分钟"));assertTrue(c.label(HistoryActivity.Control.CLEAR_TIMED).contains("有时限"));assertTrue(c.label(HistoryActivity.Control.PAUSE_RECORDING).contains("暂停历史记录"));}
 @Test public void loadingOlderRowsDoesNotStopAtHundredOrEvictRecords(){assertEquals(io.github.rebrowser.browser.HistoryStore.MAX_ENTRIES,HistoryAdapter.MAX_ROWS);assertTrue(HistoryAdapter.MAX_ROWS>100);}
}
