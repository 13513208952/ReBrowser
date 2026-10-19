package io.github.rebrowser.engine;

import static org.junit.Assert.*;
import io.github.rebrowser.browser.SearchEngines;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.Test;
import org.mozilla.geckoview.GeckoSession;

/** Actual static production classifier/display methods and synthetic pinned SDK DTO ctor.
 * No Gecko session, JNI, callback delivery, network, principal or Android navigation. */
public final class NavigationDelegatePolicyTest {
    private static String repeat(char c,int n){char[] value=new char[n];Arrays.fill(value,c);return new String(value);}
    @Test public void httpAndHttpsCurrentOrUnspecifiedTargetsAllowedStructurally(){
        for(int target:new int[]{0,1})for(String url:new String[]{"https://example.invalid/","http://localhost:8765/test?a=1#b","HTTPS://EXAMPLE.INVALID/path?x=%2F"})assertEquals(NavigationDelegate.Decision.ALLOW_WEB,NavigationDelegate.classifyBrowserLoad(url,target));
    }
    @Test public void onlyExactBlankInternalTargetAllowed(){
        assertEquals(NavigationDelegate.Decision.ALLOW_BLANK,NavigationDelegate.classifyBrowserLoad("about:blank",1));assertEquals(NavigationDelegate.Decision.ALLOW_BLANK,NavigationDelegate.classifyBrowserLoad("ABOUT:BLANK",0));assertEquals(NavigationDelegate.Decision.DENY_UNSUPPORTED_SCHEME,NavigationDelegate.classifyBrowserLoad("about:blank#x",1));assertEquals(NavigationDelegate.Decision.DENY_UNSUPPORTED_SCHEME,NavigationDelegate.classifyBrowserLoad("about:config",1));
    }
    @Test public void noNewWindowWithoutRealDocumentBoundUnopenedFactory(){
        assertEquals(NavigationDelegate.Decision.DENY_NEW_WINDOW,NavigationDelegate.classifyBrowserLoad("https://example.invalid/",2));assertEquals(NavigationDelegate.Decision.DENY_NEW_WINDOW,NavigationDelegate.classifyBrowserLoad("about:blank",2));
    }
    @Test public void unknownTargetsFailRatherThanFallThrough(){
        for(int target:new int[]{-1,3,255,Integer.MAX_VALUE})assertEquals(NavigationDelegate.Decision.DENY_MALFORMED,NavigationDelegate.classifyBrowserLoad("https://example.invalid/",target));
    }
    @Test public void unsupportedExternalAndUnimplementedWebSchemesNeverAutoLaunchOrSearch(){
        for(String uri:new String[]{"intent://app/#Intent;end","file:///private/data","content://provider/item","tel:10000","mailto:synthetic@example.invalid","javascript:1","data:text/html,synthetic","blob:https://example.invalid/id","resource://internal/page"})assertEquals(uri,NavigationDelegate.Decision.DENY_UNSUPPORTED_SCHEME,NavigationDelegate.classifyBrowserLoad(uri,1));
    }
    @Test public void malformedWebCredentialsAuthorityPortAndBackslashRejected(){
        for(String uri:new String[]{"https:opaque","http:///missing","https://user:pass@example.invalid/","https://example.invalid\\x/","https://example.invalid:65536/","https://%65xample.invalid/","https://example.invalid/%GG","https://[::1","https://example.invalid:abc/","https://example.invalid/a b"})assertEquals(uri,NavigationDelegate.Decision.DENY_MALFORMED,NavigationDelegate.classifyBrowserLoad(uri,1));
    }
    @Test public void unicodeAndPrivateNetworkRemainStructuralNotAuthorityOrDns(){
        for(String uri:new String[]{"https://bücher.example/中文","https://faß.example/","http://127.0.0.1/","https://[::1]:8443/","http://192.168.1.1/"})assertEquals(NavigationDelegate.Decision.ALLOW_WEB,NavigationDelegate.classifyBrowserLoad(uri,1));
    }
    @Test public void malformedExplicitInputsNeverBecomeSearchTargets(){
        for(String uri:new String[]{null,"","example words","//example.invalid/","https://","https://example.invalid/\nsecret","https://example.invalid/\ud800","https://example.invalid/\udc00"})assertEquals(NavigationDelegate.Decision.DENY_MALFORMED,NavigationDelegate.classifyBrowserLoad(uri,1));
    }
    @Test public void fixedMaximumCheckedWithoutGiantAllocationOrUriRewrite(){
        String prefix="https://example.invalid/?q=";String maximum=prefix+repeat('a',SearchEngines.MAX_URL_CHARS-prefix.length());assertEquals(NavigationDelegate.Decision.ALLOW_WEB,NavigationDelegate.classifyBrowserLoad(maximum,1));assertEquals(NavigationDelegate.Decision.DENY_MALFORMED,NavigationDelegate.classifyBrowserLoad(maximum+"x",1));String raw="HTTPS://EXAMPLE.INVALID:443/path?code=%2f";assertEquals(NavigationDelegate.Decision.ALLOW_WEB,NavigationDelegate.classifyBrowserLoad(raw,1));assertEquals("HTTPS://EXAMPLE.INVALID:443/path?code=%2f",raw);
    }
    @Test public void displayObservationBoundedAndNeverInventsUrlForInvalidData(){
        String raw="https://example.invalid/callback?code=synthetic";assertSame(raw,NavigationDelegate.displayLocation(raw));assertNull(NavigationDelegate.displayLocation(null));assertNull(NavigationDelegate.displayLocation(repeat('a',8193)));assertNull(NavigationDelegate.displayLocation("https://x/\u0000"));assertNull(NavigationDelegate.displayLocation("https://x/\ud800"));assertEquals("https://x/😀",NavigationDelegate.displayLocation("https://x/😀"));
    }
    @Test public void pinnedSdkDtoConstructorFlagsAreSyntheticNotActualLoadEvidence()throws Exception{
        Constructor<GeckoSession.NavigationDelegate.LoadRequest> constructor=GeckoSession.NavigationDelegate.LoadRequest.class.getDeclaredConstructor(String.class,String.class,int.class,int.class,boolean.class,boolean.class);constructor.setAccessible(true);
        GeckoSession.NavigationDelegate.LoadRequest redirect=constructor.newInstance("https://example.invalid/","https://opener.invalid/",1,0x800000,true,false);assertEquals(1,redirect.target);assertTrue(redirect.isRedirect);assertTrue(redirect.hasUserGesture);assertFalse(redirect.isDirectNavigation);assertEquals(NavigationDelegate.Decision.ALLOW_WEB,NavigationDelegate.classifyBrowserLoad(redirect.uri,redirect.target));
        GeckoSession.NavigationDelegate.LoadRequest popup=constructor.newInstance("https://example.invalid/",null,2,0,false,true);assertEquals(2,popup.target);assertTrue(popup.isDirectNavigation);assertEquals(NavigationDelegate.Decision.DENY_NEW_WINDOW,NavigationDelegate.classifyBrowserLoad(popup.uri,popup.target));
    }
    @Test public void observationHasNoPublicConstructorOrDocumentPrincipalPermissionGrant(){
        for(Constructor<?> c:NavigationDelegate.Observation.class.getDeclaredConstructors())assertFalse(Modifier.isPublic(c.getModifiers()));
        for(Method method:NavigationDelegate.Observation.class.getMethods())for(String forbidden:new String[]{"documentLease","originPrincipal","permissionGrant","jobResult","certificate"})assertFalse(method.getName().equals(forbidden));
    }
}
