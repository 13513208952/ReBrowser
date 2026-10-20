package io.github.rebrowser.review;
import static org.junit.Assert.*;
import io.github.rebrowser.bridge.*;
import io.github.rebrowser.outlook.*;
import io.github.rebrowser.music.*;
import io.github.rebrowser.crypto.ObjectAead;
import io.github.rebrowser.lyrics.*;
import io.github.rebrowser.system.*;
import io.github.rebrowser.settings.PersonalizationPolicy;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
/** Main-review production regressions. Public synthetic payloads only, no native SDK/OS/keys/site. */
public final class D02ParserRegressionTest {
 private static final String HEX=new String(new char[64]).replace('\0','a');
 private static String binding(){return "{\"dataEpoch\":\""+HEX.substring(0,32)+"\",\"partitionRef\":\"p\",\"partitionGeneration\":1,\"sessionRef\":\"s\",\"sessionGeneration\":1,\"documentLease\":\""+HEX+"\",\"portLeaseToken\":\""+HEX+"\"}";}
 private static String prefix(String kind){return "{\"protocol\":\"rebrowser.connector/1\",\"kind\":\""+kind+"\",\"binding\":"+binding()+",";}
 private static String chunk(int bytes,String encoded){return prefix("STREAM_CHUNK")+"\"streamId\":\"b_"+HEX.substring(0,32)+"\",\"sequence\":0,\"rawBytes\":"+bytes+",\"base64\":\""+encoded+"\"}";}
 @Test public void full32KiBChunkIsNotBlockedBy8KiBStringLimit(){String b64=Base64.getEncoder().encodeToString(new byte[32768]);assertEquals(32768,BridgeMessages.parse(chunk(32768,b64)).streamChunk().rawBytes());}
 @Test public void decodedSizeAndCanonicalBase64Required(){assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(chunk(2,"AA==")));assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(chunk(1,"AB==")));assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(chunk(0,"")));}
 @Test public void ordinaryPayloadStillBoundedAt8192(){String large=new String(new char[8193]).replace('\0','x');assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(prefix("EVENT")+"\"eventSequence\":0,\"eventType\":\"t\",\"resourceRef\":\"r\",\"payload\":\""+large+"\"}"));}
 @Test public void commandArgsAreRequiredNotOptional(){assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(prefix("COMMAND")+"\"requestId\":\"r_"+HEX.substring(0,32)+"\",\"action\":\"a\"}"));}
 @Test public void readyUnverifiedButStillTyped(){assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(prefix("READY")+"\"limits\":[],\"supportedActions\":[\"a\"]}"));assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(prefix("READY")+"\"limits\":{},\"supportedActions\":[\"a\",\"a\"]}"));}
 @Test public void zeroProgressChunksCannotConsumeCredit(){assertThrows(BridgeBudget.Rejected.class,()->BridgeBudget.chunk(0));}
 @Test public void mailHeaderDoesNotInventSmallerOriginalLengthWireLimit(){byte[] e=new byte[16],s=new byte[32];MailObjectFormat.Header header=MailObjectFormat.header(MailObjectFormat.Codec.ZLIB,MailObjectFormat.Purpose.BODY,1,e,e,e,Long.MAX_VALUE,1,s);assertEquals(Long.MAX_VALUE,MailObjectFormat.decode(MailObjectFormat.encode(header)).originalBytes());ObjectAead.Metadata real=ObjectAead.catalogMetadata(new ObjectAead.Limits(Long.MAX_VALUE,1),ObjectAead.Codec.ZLIB,ObjectAead.Purpose.BODY,1,e,e,e,Long.MAX_VALUE,1,s);assertArrayEquals(real.headerBytes(),MailObjectFormat.encode(header));}
 @Test public void snippetsAreActuallyBoundedTo120()throws Exception{String needle=new String(new char[64]).replace('\0','N');String input=new String(new char[100]).replace('\0','a')+needle+new String(new char[100]).replace('\0','z');MailSearch.Result r=MailSearch.search(new StringReader(input),needle,2,()->false);assertTrue(r.matches().get(0).snippet().contains(needle));assertTrue(r.matches().get(0).snippet().length()<=120);}
 private static Reader owned(String value,AtomicBoolean closed){return new StringReader(value){@Override public void close(){closed.set(true);super.close();}};}
 @Test public void searchOwnsReaderEvenOnInvalidInput(){AtomicBoolean closed=new AtomicBoolean();assertThrows(IllegalArgumentException.class,()->MailSearch.search(owned("a",closed),"",1,()->false));assertTrue(closed.get());}
 @Test public void cancelDuringReadCannotPublishResults(){AtomicBoolean cancelled=new AtomicBoolean(),closed=new AtomicBoolean();Reader r=new Reader(){public int read(char[] b,int o,int n){b[o]='a';cancelled.set(true);return 1;}public void close(){closed.set(true);}};assertThrows(IOException.class,()->MailSearch.search(r,"a",1,cancelled::get));assertTrue(closed.get());}
 @Test public void cancelDuringCloseCannotPublishResults(){AtomicBoolean cancelled=new AtomicBoolean();Reader r=new StringReader("a"){@Override public void close(){cancelled.set(true);super.close();}};assertThrows(IOException.class,()->MailSearch.search(r,"a",1,cancelled::get));}
 @Test public void malformedReaderUtf16AndCountsRejected(){assertThrows(IOException.class,()->MailSearch.search(new StringReader("\ud800"),"x",1,()->false));assertThrows(IOException.class,()->MailSearch.search(new StringReader("\udc00"),"x",1,()->false));Reader r=new Reader(){public int read(char[] b,int o,int n){return n+1;}public void close(){}};assertThrows(IOException.class,()->MailSearch.search(r,"x",1,()->false));}
 @Test public void searchChunkShapesMatchLiteralReference()throws Exception{Random random=new Random(187);for(int turn=0;turn<40;turn++){StringBuilder s=new StringBuilder();for(int i=0;i<80;i++)s.append(random.nextBoolean()?"中":"a");String input=s.toString(),needle=turn%2==0?"aa":"中a";List<Long> expected=new ArrayList<>();for(int at=0;;){int next=input.indexOf(needle,at);if(next<0)break;expected.add((long)next);at=next+needle.length();}for(int size:new int[]{1,2,7,8192}){Reader r=chunked(input,size);MailSearch.Result result=MailSearch.search(r,needle,200,()->false);List<Long> actual=new ArrayList<>();for(MailSearch.Match m:result.matches())actual.add(m.offset());assertEquals(expected,actual);}}}
 private static Reader chunked(String s,int chunk){return new Reader(){int at;public int read(char[] b,int o,int n){if(at==s.length())return -1;int c=Math.min(chunk,Math.min(n,s.length()-at));s.getChars(at,at+c,b,o);at+=c;return c;}public void close(){}};}
 @Test public void surrogatePairCanCrossReaderBlocks()throws Exception{assertEquals(1,MailSearch.search(chunked("a😀z",1),"😀",10,()->false).matches().get(0).offset());}
 @Test public void unsupportedLyricsNotReportedAsEmpty(){assertEquals(MusicLyrics.Status.UNSUPPORTED,MusicLyrics.parseLrc("not timed or raw unsupported format").status());assertEquals(MusicLyrics.Status.EMPTY,MusicLyrics.parseLrc("").status());}
 @Test public void negativeRestoredOverlayCoordinatesClampIntoWindow(){LyricPositionStore.Record r=new LyricPositionStore.Record(-10,-20,500,500,160,160,1);assertEquals(0,LyricPositionStore.restore(r,500,500,160,160).x());assertThrows(IllegalArgumentException.class,()->LyricPositionStore.restore(null,0,500,160,160));}
 @Test public void lockedOverlayCannotStartNativeDragIntent(){LyricOverlayController c=new LyricOverlayController();c.apply(LyricOverlayController.Event.SHOW,0,0);c.apply(LyricOverlayController.Event.LOCK,0,0);assertThrows(IllegalStateException.class,()->c.apply(LyricOverlayController.Event.DRAG_START,0,0));}
 @Test public void preservingExistingMediaIsNotPermissionForNewBackgroundAllocations(){assertFalse(MemoryPressurePolicy.advise(true,false,false,true,false,true,true,false).allowNewBackgroundAllocations());}
 @Test public void recommendationNeverOffersSyntheticHttpChooser(){assertEquals(1,DefaultBrowserFlow.RequestKind.values().length);}
 @Test public void smallImageNeverSilentlyUpscaled(){PersonalizationPolicy.Crop c=PersonalizationPolicy.centerCrop(10,10,360,780,3);assertTrue(c.outputWidth()<=c.cropWidth());assertTrue(c.outputHeight()<=c.cropHeight());assertThrows(IllegalArgumentException.class,()->PersonalizationPolicy.relativeLuminance(0x00ffffff));}
 @Test public void preferenceProjectionCollectionIsBounded(){Map<String,String> fields=new HashMap<>();for(int i=0;i<17;i++)fields.put("f"+i,"x");assertThrows(IllegalArgumentException.class,()->MusicPreferences.rejectedFields(fields));}
 @Test public void observedClockCannotMoveBackWithinGeneration(){MusicState s=new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.UNKNOWN,MusicState.Focus.UNOBSERVED,"","",null,0,-1,1,10);assertThrows(IllegalArgumentException.class,()->s.observe(MusicState.Playback.PAUSED,0,-1,9));}
}
