package io.github.rebrowser.outlook;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.data.NativeRecordCodec;
import java.util.*;

/** Bounded inert offline-mail preparation: every attribute/URL/form/media/active container is
 * removed; fixed CSP denies resources/scripts/forms/base, text is entity-decoded then escaped.
 * No WebView, JS/eval, remote-image loads or scripts/assets borrowed from official pages.
 * Exact original private BodyRequest gates native text fallback/HTML borrowing. This does NOT
 * authenticate account/body bytes or open a Gecko renderer; its private/no-JS session/native
 * resource/navigation/permission owner must qualify separately before HTML display is activated.
 */
public final class OfflineMailRenderer {
    public static final int MAX_INPUT_CHARS=1024*1024,MAX_TEXT_CHARS=65536,MAX_HTML_CHARS=512*1024,MAX_TAGS=10000;
    private static final Set<String> ALLOWED=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("p","div","span","br","hr","b","strong","i","em","u","s","pre","code","blockquote","ul","ol","li","table","thead","tbody","tfoot","tr","td","th","h1","h2","h3","h4","h5","h6")));
    private static final Set<String> DROP=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("script","style","iframe","object","embed","svg","math","template","noscript","head","title","form","button","textarea","select")));
    private static final String HEADER="<!doctype html><html><head><meta charset=\"utf-8\"><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'none'; style-src 'none'; img-src 'none'; connect-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'\"></head><body>";
    public static final class Sanitized {
        private final String html,plain;private Sanitized(String html,String plain){this.html=html;this.plain=plain;}
        public String inertHtml(){return html;}public String plainText(){return plain;}
    }
    public static final class Prepared implements AutoCloseable {
        private final ComponentGate.DataEpochPin source;private final OfflineMailActivity.BodyRequest recipient;private final String subject;private Sanitized body;
        private Prepared(ComponentGate.DataEpochPin source,OfflineMailActivity.BodyRequest recipient,String subject,Sanitized body){this.source=source;this.recipient=recipient;this.subject=subject;this.body=body;}
        public synchronized boolean deliverPlainFallback(OfflineMailActivity exactOwnedWindow){current(exactOwnedWindow);boolean offered=exactOwnedWindow.showBodyText(recipient,subject,body.plain);source.requireCurrent();body=null;return offered;}
        public synchronized String borrowInertHtmlForQualifiedNativeGecko(OfflineMailActivity exactOwnedWindow){current(exactOwnedWindow);return body.html;}
        private void current(OfflineMailActivity window){if(body==null)throw new IllegalStateException("Native prepared mail released");source.requireCurrent();recipient.requireCurrentBodyRecipient(window);source.requireCurrent();}
        @Override public synchronized void close(){body=null;} // Logical references only; not physical String/GC erasure.
    }
    public static Prepared prepareNativeBody(ComponentGate.DataEpochPin actualSource,OfflineMailActivity.BodyRequest originalRecipient,String nativeSubject,String nativeBody,boolean nativeBodyIsHtml){if(actualSource==null || originalRecipient==null || originalRecipient.attachment())throw invalid();actualSource.requireCurrent();String subject=NativeRecordCodec.validateText(nativeSubject,2048,true);if(subject.length()>512)throw invalid();Sanitized body=nativeBodyIsHtml?sanitizeHtml(nativeBody):plainBody(nativeBody);actualSource.requireCurrent();return new Prepared(actualSource,originalRecipient,subject,body);}
    public static Sanitized plainBody(String nativePlain){validate(nativePlain);Writer out=new Writer();out.html("<pre>");out.text(nativePlain,0,nativePlain.length());out.html("</pre>");return out.finish();}
    public static Sanitized sanitizeHtml(String nativeHtml){
        validate(nativeHtml);Writer out=new Writer();int at=0,tags=0;
        while(at<nativeHtml.length()){
            int open=nativeHtml.indexOf('<',at);if(open<0){out.entities(nativeHtml,at,nativeHtml.length());break;}out.entities(nativeHtml,at,open);
            if(nativeHtml.startsWith("<!--",open)){int stop=nativeHtml.indexOf("-->",open+4);if(stop<0)break;at=stop+3;continue;}
            int stop=nativeHtml.indexOf('>',open+1);if(stop<0){out.text(nativeHtml,open,nativeHtml.length());break;}if(++tags>MAX_TAGS)throw invalid();int p=open+1;boolean close=p<stop && nativeHtml.charAt(p)=='/';if(close)p++;int start=p;while(p<stop && asciiName(nativeHtml.charAt(p)) && p-start<32)p++;if(start==p || p-start>=32){at=stop+1;continue;}String tag=nativeHtml.substring(start,p).toLowerCase(Locale.ROOT);
            if(DROP.contains(tag)){
                if(!close){int closing=findClose(nativeHtml,tag,stop+1);if(closing<0)break;int closingEnd=nativeHtml.indexOf('>',closing);if(closingEnd<0)break;at=closingEnd+1;}else at=stop+1;continue;
            }
            if(ALLOWED.contains(tag)){out.html("<"+(close?"/":"")+tag+">");if(tag.equals("br") || tag.equals("hr") || close && !tag.equals("span") && !tag.equals("b") && !tag.equals("strong") && !tag.equals("i") && !tag.equals("em") && !tag.equals("u") && !tag.equals("s") && !tag.equals("code"))out.newline();}
            at=stop+1;
        }
        return out.finish();
    }
    private static int findClose(String s,String tag,int start){String needle="</"+tag;for(int at=start;at<=s.length()-needle.length();at++){if(s.charAt(at)=='<' && s.regionMatches(true,at,needle,0,needle.length())){int end=at+needle.length();if(end==s.length() || !asciiName(s.charAt(end)))return at;}}return -1;}
    private static boolean asciiName(char c){return c>='a' && c<='z' || c>='A' && c<='Z' || c>='0' && c<='9';}
    private static void validate(String s){if(s==null || s.length()>MAX_INPUT_CHARS)throw invalid();for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length() || !Character.isLowSurrogate(s.charAt(i)))throw invalid();}else if(Character.isLowSurrogate(c) || (c<32 && c!='\n' && c!='\r' && c!='\t') || c==127)throw invalid();}}
    private static final class Writer {
        final StringBuilder html=new StringBuilder(HEADER),plain=new StringBuilder();
        void html(String fixed){if(fixed.length()>MAX_HTML_CHARS-html.length())throw invalid();html.append(fixed);}
        void newline(){if(plain.length()>0 && plain.charAt(plain.length()-1)!='\n')cp('\n');}
        void text(String s,int start,int end){for(int p=start;p<end;){int c=s.codePointAt(p);cp(c);p+=Character.charCount(c);}}
        void entities(String s,int start,int end){for(int p=start;p<end;){int c=s.codePointAt(p),next=p+Character.charCount(c);if(c=='&'){int semi=-1;for(int q=p+1;q<end && q<=p+12;q++){if(s.charAt(q)==';'){semi=q;break;}}if(semi>p){int decoded=entity(s.substring(p+1,semi));if(decoded>=0){c=decoded;next=semi+1;}}}cp(c);p=next;}}
        void cp(int c){if(c<=0 || !Character.isValidCodePoint(c) || c>=0xd800 && c<=0xdfff || c<32 && c!='\n' && c!='\r' && c!='\t' || c==127)throw invalid();if(Character.charCount(c)>MAX_TEXT_CHARS-plain.length())throw invalid();plain.appendCodePoint(c);switch(c){case '&':html("&amp;");break;case '<':html("&lt;");break;case '>':html("&gt;");break;case '"':html("&quot;");break;case '\'':html("&#39;");break;default:if(Character.charCount(c)>MAX_HTML_CHARS-html.length())throw invalid();html.appendCodePoint(c);}}
        Sanitized finish(){html("</body></html>");return new Sanitized(html.toString(),plain.toString());}
        static int entity(String e){switch(e){case "amp":return '&';case "lt":return '<';case "gt":return '>';case "quot":return '"';case "apos":return '\'';case "nbsp":return 160;default:try{if(e.startsWith("#x") || e.startsWith("#X"))return Integer.parseInt(e.substring(2),16);if(e.startsWith("#"))return Integer.parseInt(e.substring(1),10);}catch(NumberFormatException invalid){}return -1;}}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Malformed/over-budget inert native mail body");}
    private OfflineMailRenderer(){}
}
