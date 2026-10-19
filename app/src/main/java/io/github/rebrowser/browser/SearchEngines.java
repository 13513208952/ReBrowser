package io.github.rebrowser.browser;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Explicit native search/address targets. No network, DNS lookup, Android Intent or authority.
 * Structural URL checks are NOT an origin/whitelist/login proof: actual origin comes from
 * Gecko's native principal. Unicode host is preserved for Gecko IDNA, never silently mapped
 * with Java's older IDN rules. Browser display/security UI must use the actual engine result.
 */
public final class SearchEngines {
    public static final int MAX_INPUT_CHARS=8192,MAX_URL_CHARS=32768;
    public enum Provider {
        BING("bing","https://cn.bing.com/search?q="), BAIDU("baidu","https://www.baidu.com/s?wd="),
        DUCKDUCKGO("duckduckgo","https://duckduckgo.com/?q="), GOOGLE("google","https://www.google.com/search?q=");
        private final String id,prefix;
        Provider(String id,String prefix){this.id=id;this.prefix=prefix;}
        public String id(){return id;}
        public static Provider fromId(String id){for(Provider provider:values())if(provider.id.equals(id))return provider;throw invalid();}
    }
    public enum Kind { WEB, SEARCH, NEW_TAB }
    public static final class Target {
        private final String url;
        private final Kind kind;
        private Target(String url,Kind kind){this.url=url;this.kind=kind;}
        public String url(){return url;}
        public Kind kind(){return kind;}
    }
    private static final Pattern SCHEME=Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*:.*",Pattern.DOTALL);
    private SearchEngines(){}
    public static Target newTab(){return new Target("about:blank",Kind.NEW_TAB);}
    public static Target defaultHome(){return requireWebUrl("https://cn.bing.com/");}
    /** User explicitly submitted omnibox input; malformed explicit URLs never leak to search. */
    public static Target resolveInput(String input,Provider provider){
        if(provider==null)throw invalid();if(input==null)return defaultHome();text(input,MAX_INPUT_CHARS);String clean=input.trim();
        if(clean.isEmpty())return defaultHome();if(clean.equalsIgnoreCase("about:blank"))return newTab();
        String lower=clean.toLowerCase(Locale.ROOT);
        if(lower.startsWith("http:") || lower.startsWith("https:"))return requireWebUrl(clean);
        if(clean.startsWith("//"))return requireWebUrl("https:"+clean);
        if(hostPortInput(clean))return requireWebUrl("https://"+clean);
        if(SCHEME.matcher(clean).matches())throw invalid();
        if(!hasWhitespace(clean) && (clean.indexOf('.')>=0 || clean.startsWith("[") || clean.equalsIgnoreCase("localhost")))return requireWebUrl("https://"+clean);
        return search(clean,provider);
    }
    /** Explicit search is allowed to search text that would be an unsupported address scheme. */
    public static Target search(String query,Provider provider){
        if(query==null || provider==null)throw invalid();text(query,MAX_INPUT_CHARS);String value=query.trim();if(value.isEmpty())throw invalid();
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);StringBuilder encoded=new StringBuilder();
        for(byte signed:bytes){int b=signed&255;if(b>='a'&&b<='z' || b>='A'&&b<='Z' || b>='0'&&b<='9' || b=='-' || b=='_' || b=='.' || b=='~')encoded.append((char)b);else{encoded.append('%');encoded.append("0123456789ABCDEF".charAt(b>>>4));encoded.append("0123456789ABCDEF".charAt(b&15));}}
        String url=provider.prefix+encoded;if(url.length()>MAX_URL_CHARS)throw invalid();return new Target(url,Kind.SEARCH);
    }
    /** Strict external/admin URL input: no whitespace trimming, search fallback or non-web scheme. */
    public static Target requireWebUrl(String input){
        if(input==null)throw invalid();text(input,MAX_URL_CHARS);if(input.isEmpty() || hasWhitespace(input) || input.indexOf('\\')>=0)throw invalid();
        try{
            URI uri=new URI(input);String scheme=uri.getScheme();if(scheme==null)throw invalid();scheme=scheme.toLowerCase(Locale.ROOT);
            if((!scheme.equals("https")&&!scheme.equals("http")) || uri.isOpaque())throw invalid();
            String authority=uri.getRawAuthority();if(authority==null || authority.isEmpty() || authority.indexOf('@')>=0 || authority.indexOf('%')>=0)throw invalid();
            String host,port="";
            if(authority.startsWith("[")){
                int end=authority.indexOf(']');if(end<0 || uri.getHost()==null)throw invalid();host=authority.substring(0,end+1).toLowerCase(Locale.ROOT);String tail=authority.substring(end+1);
                if(!tail.isEmpty()){if(!tail.startsWith(":"))throw invalid();port=port(tail.substring(1),scheme);}
            }else{
                int colon=authority.indexOf(':');if(colon>=0){if(authority.indexOf(':',colon+1)>=0)throw invalid();host=authority.substring(0,colon);port=port(authority.substring(colon+1),scheme);}else host=authority;
                validateHost(host);boolean ascii=true;for(int i=0;i<host.length();i++)if(host.charAt(i)>127){ascii=false;break;}if(ascii)host=host.toLowerCase(Locale.ROOT);
            }
            String path=uri.getRawPath();if(path==null || path.isEmpty())path="/";
            String suffix=path+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery())+(uri.getRawFragment()==null?"":"#"+uri.getRawFragment());
            String result=scheme+"://"+host+port+new URI(suffix).toASCIIString();if(result.length()>MAX_URL_CHARS)throw invalid();return new Target(result,Kind.WEB);
        }catch(URISyntaxException malformed){throw invalid();}
    }
    private static void validateHost(String host){
        if(host.isEmpty() || host.length()>253)throw invalid();String body=host.endsWith(".")?host.substring(0,host.length()-1):host;if(body.isEmpty())throw invalid();
        for(String label:body.split("\\.",-1)){
            if(label.isEmpty() || label.codePointCount(0,label.length())>63 || label.startsWith("-") || label.endsWith("-"))throw invalid();
            for(int at=0;at<label.length();){int cp=label.codePointAt(at);at+=Character.charCount(cp);int type=Character.getType(cp);if(cp!='-' && !Character.isLetterOrDigit(cp) && type!=Character.NON_SPACING_MARK && type!=Character.COMBINING_SPACING_MARK)throw invalid();}
        }
        if(body.matches("[0-9.]+")){
            String[] parts=body.split("\\.",-1);if(parts.length!=4)throw invalid();for(String part:parts){if(part.length()>3 || part.length()>1&&part.startsWith("0") || Integer.parseInt(part)>255)throw invalid();}
        }
    }
    private static String port(String value,String scheme){
        if(value.isEmpty() || value.length()>5)throw invalid();for(int i=0;i<value.length();i++)if(value.charAt(i)<'0'||value.charAt(i)>'9')throw invalid();
        int port=Integer.parseInt(value);if(port>65535)throw invalid();return port==(scheme.equals("https")?443:80)?"":":"+port;
    }
    private static boolean hostPortInput(String input){
        if(hasWhitespace(input))return false;int colon=input.indexOf(':');if(colon<1)return false;String host=input.substring(0,colon);if(!host.contains(".")&&!host.equalsIgnoreCase("localhost"))return false;
        int slash=input.indexOf('/',colon+1),query=input.indexOf('?',colon+1),fragment=input.indexOf('#',colon+1);int end=input.length();if(slash>=0)end=Math.min(end,slash);if(query>=0)end=Math.min(end,query);if(fragment>=0)end=Math.min(end,fragment);
        String port=input.substring(colon+1,end);if(port.isEmpty())return false;for(int i=0;i<port.length();i++)if(port.charAt(i)<'0'||port.charAt(i)>'9')return false;return true;
    }
    private static boolean hasWhitespace(String input){for(int i=0;i<input.length();i++)if(Character.isWhitespace(input.charAt(i))||Character.isSpaceChar(input.charAt(i)))return true;return false;}
    private static void text(String value,int limit){
        if(value.length()>limit)throw invalid();for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw invalid();if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw invalid();}else if(Character.isLowSurrogate(c))throw invalid();}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid or unsupported native navigation input");}
}
