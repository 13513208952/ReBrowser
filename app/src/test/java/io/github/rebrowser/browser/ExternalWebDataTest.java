package io.github.rebrowser.browser;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.concurrent.*;
/** Untrusted external URL DATA parsing/duplicate-attempt latch only. No Android/source/Actor
 * grant or workspace/session is created by this model. Actual entry must pass native gates. */
public final class ExternalWebDataTest {
 @Test public void exactFullWebQueryAndFragmentSurviveSingleAttempt(){ExternalNavigation.PendingWebTarget data=new ExternalNavigation.PendingWebTarget("https://cn.bing.com/search?q=one%20two#section");assertFalse(data.attempted());SearchEngines.Target target=data.claimNativeDispatchAttempt();assertEquals(SearchEngines.Kind.WEB,target.kind());assertEquals("https://cn.bing.com/search?q=one%20two#section",target.url());assertTrue(data.attempted());}
 @Test public void attemptedDataCannotBeRedispatchedOnResume(){ExternalNavigation.PendingWebTarget data=new ExternalNavigation.PendingWebTarget("https://www.baidu.com/");data.claimNativeDispatchAttempt();assertThrows(IllegalStateException.class,data::claimNativeDispatchAttempt);}
 @Test public void unsupportedOrCredentialsDoNotBecomeSearchOrNativeGrant(){for(String raw:new String[]{"javascript:alert(1)","file:///private/data","content://provider/path","intent://host/#Intent;scheme=https;end","data:text/html,test","about:blank","https://u:p@example.test/","search these words"})assertThrows(raw,IllegalArgumentException.class,()->new ExternalNavigation.PendingWebTarget(raw));}
 @Test public void missingWhitespaceAndControlReject(){for(String raw:new String[]{null,"","https://www.baidu.com/\n","https://www.baidu.com/a b","https://www.baidu.com/\\x"})assertThrows(IllegalArgumentException.class,()->new ExternalNavigation.PendingWebTarget(raw));}
 @Test public void externalBudgetDoesNotAcceptOversizeEvenWhenOmniboxParserCould(){String base="https://www.baidu.com/";String exact=base+"x".repeat(ExternalNavigation.MAX_EXTERNAL_URL_CHARS-base.length());assertEquals(ExternalNavigation.MAX_EXTERNAL_URL_CHARS,new ExternalNavigation.PendingWebTarget(exact).claimNativeDispatchAttempt().url().length());assertThrows(IllegalArgumentException.class,()->new ExternalNavigation.PendingWebTarget(exact+"x"));}
 @Test public void foreignThreadCannotSpendNativeWindowOwnedData()throws Exception{ExternalNavigation.PendingWebTarget data=new ExternalNavigation.PendingWebTarget("https://cn.bing.com/");ExecutorService worker=Executors.newSingleThreadExecutor();try{assertTrue(worker.submit(()->{try{data.claimNativeDispatchAttempt();return false;}catch(IllegalStateException expected){return true;}}).get(3,TimeUnit.SECONDS));assertFalse(data.attempted());}finally{worker.shutdown();assertTrue(worker.awaitTermination(3,TimeUnit.SECONDS));}}
}
