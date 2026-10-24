package io.github.rebrowser.engine;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.concurrent.*;
/** Actual request/UI ownership reducer only; no SDK prompt response or Android window. */
public final class NativePromptLifetimeTest {
 @Test public void requestWithoutShowEndsOnlyAfterResponseAttempt(){PromptDelegate.Lifetime lifetime=new PromptDelegate.Lifetime();assertFalse(lifetime.ended());lifetime.attemptingResponse();assertTrue(lifetime.ended());assertThrows(IllegalStateException.class,lifetime::attemptingResponse);}
 @Test public void responseAttemptDoesNotPretendShownWindowDismissed(){PromptDelegate.Lifetime lifetime=new PromptDelegate.Lifetime();lifetime.submittingShow();lifetime.attemptingResponse();assertTrue(lifetime.responseAttempted());assertFalse(lifetime.ended());lifetime.actualDismissed();assertTrue(lifetime.ended());}
 @Test public void actualUiDismissAloneDoesNotPretendSdkAnswerAttempted(){PromptDelegate.Lifetime lifetime=new PromptDelegate.Lifetime();lifetime.submittingShow();lifetime.actualDismissed();assertFalse(lifetime.ended());lifetime.attemptingResponse();assertTrue(lifetime.ended());}
 @Test public void unknownShowRemainsOwnedAndCannotShowAnother(){PromptDelegate.Lifetime lifetime=new PromptDelegate.Lifetime();lifetime.submittingShow();assertTrue(lifetime.showAttempted());assertFalse(lifetime.ended());assertThrows(IllegalStateException.class,lifetime::submittingShow);lifetime.attemptingResponse();assertFalse(lifetime.ended());}
 @Test public void lateShowAfterResponseRejected(){PromptDelegate.Lifetime lifetime=new PromptDelegate.Lifetime();lifetime.attemptingResponse();assertThrows(IllegalStateException.class,lifetime::submittingShow);}
 @Test public void actualDismissIsIdempotentNotSecondResponse(){PromptDelegate.Lifetime lifetime=new PromptDelegate.Lifetime();lifetime.submittingShow();lifetime.attemptingResponse();lifetime.actualDismissed();lifetime.actualDismissed();assertTrue(lifetime.ended());assertThrows(IllegalStateException.class,lifetime::attemptingResponse);}
 @Test public void webpageResponseWaitsActualDismissAndFocusNotButtonReturn(){PromptDelegate.Lifetime lifetime=new PromptDelegate.Lifetime();lifetime.submittingShow();lifetime.attemptingResponse();assertFalse(lifetime.mayPublish(false,true));lifetime.actualDismissed();assertFalse(lifetime.mayPublish(false,false));assertTrue(lifetime.mayPublish(false,true));assertTrue(lifetime.mayPublish(true,false));}
 @Test public void anotherThreadCannotSpendPrompt()throws Exception{PromptDelegate.Lifetime lifetime=new PromptDelegate.Lifetime();ExecutorService worker=Executors.newSingleThreadExecutor();try{assertTrue(worker.submit(()->{try{lifetime.attemptingResponse();return false;}catch(IllegalStateException expected){return true;}}).get(3,TimeUnit.SECONDS));assertFalse(lifetime.responseAttempted());}finally{worker.shutdown();assertTrue(worker.awaitTermination(3,TimeUnit.SECONDS));}}
}
