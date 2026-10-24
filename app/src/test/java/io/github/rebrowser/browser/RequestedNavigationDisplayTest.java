package io.github.rebrowser.browser;
import static org.junit.Assert.*;
import io.github.rebrowser.engine.SessionPresentation;
import java.lang.reflect.Method;
import org.junit.Test;
/** Production display reducer with synthetic HOST events; no document/Android authority. */
public final class RequestedNavigationDisplayTest {
 private void location(SessionPresentation model,String value)throws Exception{Method m=SessionPresentation.class.getDeclaredMethod("location",String.class);m.setAccessible(true);m.invoke(model,value);}
 @Test public void freshEngineBootstrapBlankCannotEraseRequestedWebUrl()throws Exception{SessionPresentation model=new SessionPresentation();long before=model.snapshot().reportedLocationSequence();location(model,"about:blank");assertFalse(BrowserCoordinator.nativeLocationSettlesRequest(SearchEngines.requireWebUrl("https://example.com/"),before,model.snapshot()));}
 @Test public void actualNewNonblankLocationCanSettleDisplayIntentIncludingRedirect()throws Exception{SessionPresentation model=new SessionPresentation();long before=model.snapshot().reportedLocationSequence();location(model,"https://redirect.example/landing");assertTrue(BrowserCoordinator.nativeLocationSettlesRequest(SearchEngines.requireWebUrl("https://example.com/"),before,model.snapshot()));}
 @Test public void unchangedLocationOrNoNewObservationDoesNotComplete()throws Exception{SessionPresentation model=new SessionPresentation();location(model,"https://example.com/");assertFalse(BrowserCoordinator.nativeLocationSettlesRequest(SearchEngines.requireWebUrl("https://example.com/"),model.snapshot().reportedLocationSequence(),model.snapshot()));}
 @Test public void explicitNativeBlankMayCompleteAtActuallyReportedBlank()throws Exception{SessionPresentation model=new SessionPresentation();location(model,"about:blank");assertTrue(BrowserCoordinator.nativeLocationSettlesRequest(SearchEngines.newTab(),0,model.snapshot()));}
}
