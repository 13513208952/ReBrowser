package io.github.reoutlook;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ReBrowserMusicInitializationTest {
    @Test public void expiryIsOnceForCurrentInitialization() {
        ReBrowserMusicInitialization i=new ReBrowserMusicInitialization();long token=i.start();
        assertFalse(i.expired());assertTrue(i.expire(token,false,false));assertTrue(i.expired());
        assertFalse(i.expire(token,false,false));
    }
    @Test public void readyRuntimeIsNeverExpiredOrStopped() {
        ReBrowserMusicInitialization i=new ReBrowserMusicInitialization();long token=i.start();
        assertFalse(i.expire(token,true,false));assertFalse(i.expired());
    }
    @Test public void actualOfficialLoginInputHasNoInitializationDeadline() {
        ReBrowserMusicInitialization i=new ReBrowserMusicInitialization();long token=i.start();
        assertFalse(i.expire(token,false,true));assertFalse(i.expired());
    }
    @Test public void reloadRestartsBudgetAndRejectsOldTimer() {
        ReBrowserMusicInitialization i=new ReBrowserMusicInitialization();long old=i.start();
        i.expire(old,false,false);long next=i.start();assertNotEquals(old,next);
        assertFalse(i.expired());assertFalse(i.expire(old,false,false));assertFalse(i.expired());
        assertTrue(i.expire(next,false,false));
    }
    @Test public void expiryDoesNotPretendAnEvaluationWasCancelled() {
        ReBrowserMusicEvaluation evaluation=new ReBrowserMusicEvaluation();int request=evaluation.begin();
        ReBrowserMusicInitialization i=new ReBrowserMusicInitialization();i.expire(i.start(),false,false);
        for(int n=0;n<10_000;n++)assertEquals(0,evaluation.begin());
        assertTrue(evaluation.finish(request));
    }
}
