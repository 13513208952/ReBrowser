package io.github.rebrowser.outlook;
import org.junit.Test;
import static org.junit.Assert.*;
/** Actual bounded native inert renderer methods, no Gecko/window/account/Android qualification. */
public final class OfflineMailRendererTest {
 @Test public void preservesTextAndBasicFormatting(){OfflineMailRenderer.Sanitized s=OfflineMailRenderer.sanitizeHtml("<p>Hello <b>world</b></p>");assertTrue(s.inertHtml().contains("<p>Hello <b>world</b></p>"));assertEquals("Hello world\n",s.plainText());}
 @Test public void stripsEveryAttributeIncludingSourceEventAndCss(){String s=OfflineMailRenderer.sanitizeHtml("<p onclick='alert(1)' style='background:url(https://evil)' id=x class=y>Hello</p>").inertHtml();assertTrue(s.endsWith("<p>Hello</p>\n</body></html>"));assertFalse(s.contains("https://evil"));assertFalse(s.contains("onclick"));}
 @Test public void linksAreOnlyInertText(){String s=OfflineMailRenderer.sanitizeHtml("<a href='javascript:evil()'>link</a><img src='https://secret/'>").inertHtml();assertTrue(s.endsWith("link</body></html>"));assertFalse(s.contains("javascript:"));assertFalse(s.contains("https://secret"));}
 @Test public void scriptsStylesFramesAndSvgContentsRemoved(){OfflineMailRenderer.Sanitized s=OfflineMailRenderer.sanitizeHtml("a<script>secret()</script><style>url(remote)</style><iframe>bad</iframe><svg><script>bad</script></svg>b");assertEquals("ab",s.plainText());assertFalse(s.inertHtml().contains("secret"));assertFalse(s.inertHtml().contains("remote"));}
 @Test public void activeDropIsAsciiCaseInsensitive(){assertEquals("ab",OfflineMailRenderer.sanitizeHtml("a<ScRiPt>bad</sCrIpT>b").plainText());}
 @Test public void unclosedActiveContainerDropsTail(){assertEquals("a",OfflineMailRenderer.sanitizeHtml("a<script>bad<p>do not expose</p>").plainText());}
 @Test public void commentsCannotReintroduceMarkup(){assertEquals("ab",OfflineMailRenderer.sanitizeHtml("a<!-- <script>bad</script> -->b").plainText());assertEquals("a",OfflineMailRenderer.sanitizeHtml("a<!--unterminated").plainText());}
 @Test public void escapedMarkupNeverBecomesTags(){OfflineMailRenderer.Sanitized s=OfflineMailRenderer.sanitizeHtml("&lt;script&gt;evil&lt;/script&gt;");assertEquals("<script>evil</script>",s.plainText());assertTrue(s.inertHtml().contains("&lt;script&gt;evil&lt;/script&gt;"));}
 @Test public void entitiesUnicodeAndAmpersandsDecodedThenEscaped(){OfflineMailRenderer.Sanitized s=OfflineMailRenderer.sanitizeHtml("A&amp;B &#x1F600; &quot; &apos; &unknown;");assertEquals("A&B 😀 \" ' &unknown;",s.plainText());assertTrue(s.inertHtml().contains("&amp;unknown;"));}
 @Test public void numericEntityCannotIntroduceControlOrSurrogate(){for(String s:new String[]{"&#0;","&#xD800;","&#x110000;","&#1;"})assertThrows(IllegalArgumentException.class,()->OfflineMailRenderer.sanitizeHtml(s));}
 @Test public void plainIsNotAnHtmlInterpreter(){OfflineMailRenderer.Sanitized s=OfflineMailRenderer.plainBody("<img src=x>&copy;");assertEquals("<img src=x>&copy;",s.plainText());assertTrue(s.inertHtml().contains("&lt;img src=x&gt;&amp;copy;"));}
 @Test public void cspDeniesEveryActiveRemoteChannel(){String s=OfflineMailRenderer.sanitizeHtml("").inertHtml();for(String token:new String[]{"default-src 'none'","script-src 'none'","img-src 'none'","connect-src 'none'","base-uri 'none'","form-action 'none'"})assertTrue(s.contains(token));}
 @Test public void brokenUtf16AndControlInputsRejected(){for(String s:new String[]{"\ud800","\udc00","x\u0000y","x\u0001y"})assertThrows(IllegalArgumentException.class,()->OfflineMailRenderer.sanitizeHtml(s));}
 @Test public void manyAmpersandsHaveBoundedLocalEntitySearch(){char[] c=new char[65536];java.util.Arrays.fill(c,'&');assertEquals(65536,OfflineMailRenderer.sanitizeHtml(new String(c)).plainText().length());}
 @Test public void malformedNoClosingBracketIsSingleEscapedText(){assertEquals("a<notclosed",OfflineMailRenderer.sanitizeHtml("a<notclosed").plainText());}
 @Test public void tagAndVisibleTextBudgetsAreEnforced(){StringBuilder b=new StringBuilder();for(int i=0;i<10001;i++)b.append("<br>");assertThrows(IllegalArgumentException.class,()->OfflineMailRenderer.sanitizeHtml(b.toString()));char[] c=new char[65537];java.util.Arrays.fill(c,'x');assertThrows(IllegalArgumentException.class,()->OfflineMailRenderer.plainBody(new String(c)));}
 @Test public void inputBoundAppliesBeforeTokenisation(){char[] c=new char[1024*1024+1];java.util.Arrays.fill(c,'x');assertThrows(IllegalArgumentException.class,()->OfflineMailRenderer.sanitizeHtml(new String(c)));}
 @Test public void formControlsAndTemplateNeverActivate(){assertEquals("beforeafter",OfflineMailRenderer.sanitizeHtml("before<form><input value=x>hidden</form><template>hidden</template>after").plainText());}
}
