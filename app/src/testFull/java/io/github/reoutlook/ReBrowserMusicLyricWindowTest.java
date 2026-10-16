package io.github.reoutlook;

import static org.junit.Assert.*;
import org.junit.Test;

public final class ReBrowserMusicLyricWindowTest {
    @Test public void singleButtonCyclesOffUnlockedLockedOffAndPermissionFailureStaysOff() {
        var s=new ReBrowserMusicLyricWindow(.5f,.25f);
        assertEquals(ReBrowserMusicLyricWindow.Mode.OFF,s.mode());assertFalse(s.cycle(false));
        assertTrue(s.cycle(true));assertEquals(ReBrowserMusicLyricWindow.Mode.UNLOCKED,s.mode());
        s.cycle(true);assertEquals(ReBrowserMusicLyricWindow.Mode.LOCKED,s.mode());
        s.cycle(false);assertEquals(ReBrowserMusicLyricWindow.Mode.OFF,s.mode()); // Closing never needs permission.
    }
    @Test public void phoneDragChangesOnlyYAndClampsBothEdges() {
        var s=new ReBrowserMusicLyricWindow(.9f,.5f);s.cycle(true);var p=s.place(400,800,360,100,false);
        assertEquals(20,p.x());assertEquals(350,p.y());
        p=s.drag(p,99999,-99999,400,800,false);assertEquals(20,p.x());assertEquals(0,p.y());
        p=s.drag(p,-99999,99999,400,800,false);assertEquals(20,p.x());assertEquals(700,p.y());assertEquals(.9f,s.xFraction(),0);
    }
    @Test public void tabletCanDragBothAxesRegardlessOfScreenOrientation() {
        for(int[] bounds:new int[][]{{1200,800},{800,1200}}) {
            var s=new ReBrowserMusicLyricWindow(.5f,.5f);s.cycle(true);var p=s.place(bounds[0],bounds[1],560,112,true);
            p=s.drag(p,99999,99999,bounds[0],bounds[1],true);
            assertEquals(bounds[0]-560,p.x());assertEquals(bounds[1]-112,p.y());
            p=s.drag(p,-99999,-99999,bounds[0],bounds[1],true);assertEquals(0,p.x());assertEquals(0,p.y());
        }
    }
    @Test public void lockedAndOffCannotMoveAndReopeningRetainsTheLastPosition() {
        var s=new ReBrowserMusicLyricWindow(.5f,.5f);s.cycle(true);var p=s.place(1200,800,560,112,true);
        p=s.drag(p,120,-90,1200,800,true);s.cycle(true);
        assertEquals(p,s.drag(p,900,900,1200,800,true));s.cycle(true);assertEquals(p,s.place(1200,800,560,112,true));
        assertEquals(p,s.drag(p,900,900,1200,800,true));s.cycle(true);assertEquals(p,s.place(1200,800,560,112,true));
    }
    @Test public void persistedPositionRestoresButNewRuntimeModeIsOff() {
        var s=new ReBrowserMusicLyricWindow(.5f,.5f);s.cycle(true);var p=s.place(1200,800,560,112,true);
        p=s.drag(p,90,100,1200,800,true);s.cycle(true);s.cycle(true);
        var restored=new ReBrowserMusicLyricWindow(s.xFraction(),s.yFraction());
        assertEquals(ReBrowserMusicLyricWindow.Mode.OFF,restored.mode());assertEquals(p,restored.place(1200,800,560,112,true));
    }
    @Test public void rotationsSmallWindowsAndOversizedContentAlwaysFit() {
        var random=new java.util.Random(163);var s=new ReBrowserMusicLyricWindow(1,1);s.cycle(true);
        for(int i=0;i<10000;i++) {
            int w=random.nextInt(2200)+1,h=random.nextInt(2200)+1;
            var p=s.place(w,h,random.nextInt(2400),random.nextInt(2400),true);
            p=s.drag(p,(random.nextDouble()-.5)*10000,(random.nextDouble()-.5)*10000,w,h,true);
            assertTrue(p.x()>=0&&p.y()>=0&&p.x()+p.width()<=w&&p.y()+p.height()<=h);
        }
    }
    @Test public void corruptOrNonfinitePositionsCannotEscapeBounds() {
        for(float f:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-10,10}) {
            var s=new ReBrowserMusicLyricWindow(f,f);s.cycle(true);var p=s.place(400,800,360,112,true);
            assertTrue(p.x()>=0&&p.x()<=40&&p.y()>=0&&p.y()<=688);
            assertEquals(p,s.drag(p,Double.NaN,10,400,800,true));
        }
    }
    @Test public void lockedWindowAlphaNeverExceedsAndroidObscuringLimit() {
        for(float max:new float[]{0,.1f,.6f,.8f,1})assertTrue(ReBrowserMusicLyricWindow.lockedAlpha(max)<=max);
        assertEquals(.75f,ReBrowserMusicLyricWindow.lockedAlpha(.8f),0);
        assertEquals(0,ReBrowserMusicLyricWindow.lockedAlpha(Float.NaN),0);
    }
}
