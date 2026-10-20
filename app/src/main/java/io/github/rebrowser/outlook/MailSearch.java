package io.github.rebrowser.outlook;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Bounded literal UTF-16-offset search. Reader ownership transfers at entry, closes on ALL exits.
 * No database/account/file open, decryption/export or task-end authority. Cancellation is checked
 * after potentially blocking read and before result publication; caller retains its native work
 * owner through read AND close (neither timeout nor a requested cancel proves either ended).
 */
public final class MailSearch {
 public static final int CHUNK_CHARS=8192,MAX_NEEDLE_CHARS=64,MAX_RESULTS=200,MAX_SNIPPET_CHARS=120;
 public static final long MAX_SCANNED_CHARS=8L*1024*1024;
 public static final class Match {private final long offset;private final String snippet;Match(long o,String s){offset=o;snippet=s;}public long offset(){return offset;}public String snippet(){return snippet;}}
 public static final class Result {private final List<Match> matches;private final boolean truncated;private final long scannedChars;private Result(List<Match> m,boolean t,long n){matches=Collections.unmodifiableList(m);truncated=t;scannedChars=n;}public List<Match> matches(){return matches;}public boolean truncated(){return truncated;}public long scannedChars(){return scannedChars;}}
 public static final class Page {private final List<Match> matches;private final int nextIndex;private final boolean more;Page(List<Match> m,int n,boolean b){matches=Collections.unmodifiableList(m);nextIndex=n;more=b;}public List<Match> matches(){return matches;}public int nextIndex(){return nextIndex;}public boolean more(){return more;}}
 private MailSearch(){}
 public static Result search(Reader ownedReader,String needle,int maxResults,BooleanSupplier cancelled)throws IOException {
  Result result;
  try(Reader source=ownedReader){
   if(source==null || needle==null || cancelled==null || needle.isEmpty() || needle.length()>MAX_NEEDLE_CHARS || maxResults<1 || maxResults>MAX_RESULTS)throw new IllegalArgumentException("Invalid owned literal search");
   validNeedle(needle);List<Match> matches=new ArrayList<>();char[] buffer=new char[CHUNK_CHARS];StringBuilder window=new StringBuilder(CHUNK_CHARS+MAX_NEEDLE_CHARS);
   long consumed=0,windowStart=0,nextEligible=0;boolean truncated=false,pendingHigh=false;
   while(true){
    check(cancelled);int requested=(int)Math.min(CHUNK_CHARS,MAX_SCANNED_CHARS-consumed+1);int n=source.read(buffer,0,requested);check(cancelled);
    if(n< -1 || n>requested)throw new IOException("Invalid Reader count");
    if(n== -1){if(pendingHigh)throw new IOException("Truncated UTF16 input");break;}
    if(n==0)throw new IOException("Reader made no progress");
    int accepted=(int)Math.min(n,MAX_SCANNED_CHARS-consumed);
    for(int i=0;i<accepted;i++){char c=buffer[i];if(pendingHigh){if(!Character.isLowSurrogate(c))throw new IOException("Broken UTF16 input");pendingHigh=false;}else if(Character.isLowSurrogate(c))throw new IOException("Broken UTF16 input");else if(Character.isHighSurrogate(c))pendingHigh=true;}
    window.append(buffer,0,accepted);consumed+=accepted;
    int from=0;
    while(true){int index=window.indexOf(needle,from);if(index<0)break;long absolute=windowStart+index;if(absolute<nextEligible){from=index+1;continue;}matches.add(new Match(absolute,snippet(window,index,needle.length())));nextEligible=absolute+needle.length();if(matches.size()>=maxResults){truncated=true;break;}from=index+needle.length();}
    if(truncated || accepted<n){truncated=true;break;}
    int keep=Math.min(window.length(),needle.length()-1);window.delete(0,window.length()-keep);windowStart=consumed-window.length();
   }
   check(cancelled);result=new Result(matches,truncated,consumed);
  }
  check(cancelled);return result;
 }
 public static Page page(Result result,int start,int size){if(result==null || start<0 || start>result.matches.size() || size<1 || size>MAX_RESULTS)throw new IllegalArgumentException("Invalid result page");int end=Math.min(result.matches.size(),start+size);return new Page(new ArrayList<>(result.matches.subList(start,end)),end,end<result.matches.size());}
 private static String snippet(CharSequence s,int at,int length){int start=Math.max(0,at-(MAX_SNIPPET_CHARS-length)/2),end=Math.min(s.length(),start+MAX_SNIPPET_CHARS);if(start>0 && Character.isLowSurrogate(s.charAt(start)))start++;if(end>start && Character.isHighSurrogate(s.charAt(end-1)))end--;StringBuilder out=new StringBuilder(end-start);for(int i=start;i<end;i++){char c=s.charAt(i);out.append(Character.isISOControl(c) && c!='\n' && c!='\t'?' ':c);}return out.toString();}
 private static void check(BooleanSupplier cancelled)throws IOException{if(cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())throw new IOException("Search cancelled");}
 private static void validNeedle(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length() || !Character.isLowSurrogate(s.charAt(i)))throw new IllegalArgumentException("Broken UTF16 needle");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16 needle");}}
}
