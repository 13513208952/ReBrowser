package io.github.rebrowser.browser;

import static org.junit.Assert.*;
import org.junit.Test;

/** Actual Java URL/query code, no network or claim about Gecko/DNS/principal equivalence. */
public final class SearchEnginesTest {
    private static String url(String input){return SearchEngines.requireWebUrl(input).url();}
    private static void bad(String input){assertThrows(IllegalArgumentException.class,()->url(input));}
    @Test public void fourSearchProvidersEncodeUtf8AndDoNotAddCallerQueryParameters(){
        for(SearchEngines.Provider p:SearchEngines.Provider.values()){
            SearchEngines.Target target=SearchEngines.search("猫 &?#=+\ud83d\ude03",p);assertEquals(SearchEngines.Kind.SEARCH,target.kind());assertTrue(target.url().contains("%E7%8C%AB%20%26%3F%23%3D%2B%F0%9F%98%83"));assertFalse(target.url().contains("&?#"));assertSame(p,SearchEngines.Provider.fromId(p.id()));
        }
        assertThrows(IllegalArgumentException.class,()->SearchEngines.Provider.fromId("unknown"));
        assertTrue(SearchEngines.search("C:\\private",SearchEngines.Provider.BING).url().contains("%5C"));
    }
    @Test public void emptyInputUsesApprovedDefaultAndBlankIsNotExternalWeb(){
        assertEquals("https://cn.bing.com/",SearchEngines.resolveInput(null,SearchEngines.Provider.BING).url());
        assertEquals("https://cn.bing.com/",SearchEngines.resolveInput("  ",SearchEngines.Provider.BING).url());assertEquals(SearchEngines.Kind.NEW_TAB,SearchEngines.resolveInput("about:blank",SearchEngines.Provider.BING).kind());
        bad("about:blank");bad("about:config");
    }
    @Test public void userAddressesAndExplicitSearchAreDistinct(){
        assertEquals("https://example.com/a",SearchEngines.resolveInput(" example.com/a ",SearchEngines.Provider.BING).url());
        assertEquals("https://example.com:8443/",SearchEngines.resolveInput("example.com:8443",SearchEngines.Provider.BING).url());assertEquals("https://localhost:8080/",SearchEngines.resolveInput("localhost:8080",SearchEngines.Provider.BING).url());
        assertEquals(SearchEngines.Kind.SEARCH,SearchEngines.resolveInput("hello world",SearchEngines.Provider.GOOGLE).kind());assertEquals("https://example.com/",SearchEngines.resolveInput("//example.com",SearchEngines.Provider.BING).url());
    }
    @Test public void strictExternalWebRejectsSchemesUserinfoWhitespaceBackslashAndEncodedAuthority(){
        for(String input:new String[]{"file:///private","javascript:alert(1)","intent://x","data:text/html,x","https:example.com"," https://example.com/","https://example.com/ ","https://u:p@example.com/","https://example.com%40evil.invalid/","https://example.com\\@evil.invalid/","https://example.com/\n","https://example.com/\u00a0","https:///missing"})bad(input);
    }
    @Test public void unicodeHostIsPreservedForEngineIdnaNotOldJavaIdnMapping(){
        String value=url("https://faß.de/中文?q=猫#页");assertTrue(value.startsWith("https://faß.de/"));assertFalse(value.contains("fass.de"));assertTrue(value.contains("%E4%B8%AD%E6%96%87"));assertEquals(value,url(value));
        assertEquals("https://例子.测试/",url("https://例子.测试"));
        bad("https://example\uff20evil.invalid/");bad("https://example\uff1a443/");bad("https://bad_host.invalid/");
    }
    @Test public void ipLiteralsAreSyntaxOnlyNoDnsOrPrivateNetworkBan(){
        assertEquals("http://127.0.0.1/",url("http://127.0.0.1"));assertEquals("https://[2001:db8::1]/",url("https://[2001:db8::1]"));assertEquals("http://[::1]:8080/",url("http://[::1]:8080"));
        for(String input:new String[]{"http://127.1/","http://256.1.1.1/","http://0127.0.0.1/","http://[not-ip]/","http://[fe80::1%25eth0]/","http://::1/"})bad(input);
    }
    @Test public void portsCanonicalizeDefaultsAndRejectOverflowOrAmbiguity(){
        assertEquals("https://example.com/",url("HTTPS://EXAMPLE.COM:443"));assertEquals("http://example.com/",url("http://example.com:00080"));assertEquals("https://example.com:0/",url("https://example.com:0/"));
        for(String port:new String[]{"","65536","-1","+80","80x","123456","80:90"})bad("https://example.com:"+port+"/");
    }
    @Test public void malformedExplicitUrlsNeverFallBackToLeakingTheirTextToSearch(){
        for(String value:new String[]{"https://user:secret@example.com","https://bad host/secret","file:///private/file","javascript:alert(1)","https://example.com/%zz","mailto:owner@example.com","owner@example.com"})assertThrows(IllegalArgumentException.class,()->SearchEngines.resolveInput(value,SearchEngines.Provider.BING));
        assertEquals(SearchEngines.Kind.SEARCH,SearchEngines.search("javascript:example",SearchEngines.Provider.BING).kind());
    }
    @Test public void percentEscapesFragmentsAndDotPathsAreNotDecodedOrReserializedLossily(){
        String expected="https://example.com/a/../b%2f?q=%26%3d#part%23";assertEquals(expected,url(expected));assertEquals(expected,url(url(expected)));
        assertEquals("https://example.com./",url("https://EXAMPLE.COM."));bad("https://x..invalid/");bad("https://-x.invalid/");bad("https://x-.invalid/");
    }
    @Test public void invalidUtf16ControlsAndSizeLimitsFailClosed(){
        bad("https://x.invalid/\ud800");bad("https://x.invalid/\udc00");bad("https://x.invalid/\u0000");
        StringBuilder large=new StringBuilder();for(int i=0;i<32769;i++)large.append('a');bad("https://x.invalid/"+large);
        assertThrows(IllegalArgumentException.class,()->SearchEngines.search(large.toString(),SearchEngines.Provider.BING));assertThrows(IllegalArgumentException.class,()->SearchEngines.search("",SearchEngines.Provider.BING));
        assertThrows(IllegalArgumentException.class,()->SearchEngines.resolveInput("x",null));
    }
    @Test public void unicodeExpansionRemainsBoundedAndCanonicalCanBeRevalidated(){
        StringBuilder path=new StringBuilder("https://example.com/");for(int i=0;i<1500;i++)path.append('中');String target=url(path.toString());assertTrue(target.length()>8192);assertTrue(target.length()<=SearchEngines.MAX_URL_CHARS);assertEquals(target,url(target));
    }
}
