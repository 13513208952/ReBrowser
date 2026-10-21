package io.github.rebrowser.outlook;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Bounded LINEAR literal search (streaming KMP) over a Reader the native owner already authorized.
 * Reader ownership transfers at entry and it is closed on every exit path. Cancellation is checked
 * before and after reads and after close; a reader that never progresses fails instead of spinning.
 * Case-sensitive, non-overlapping semantics are preserved; snippets never split a surrogate pair,
 * UTF-16 validity is checked incrementally, and scan-limit, result-limit and clean EOF are distinct.
 */
public final class MailSearch {
    public static final int CHUNK_CHARS = 8192;
    public static final int MAX_NEEDLE_CHARS = 64;
    public static final int MAX_RESULTS = 200;
    public static final long MAX_SCANNED_CHARS = 8L * 1024 * 1024;
    public static final int MAX_SNIPPET_CHARS = 120;
    private static final int MAX_ZERO_READS = 64;
    private static final int SNIPPET_TAIL = MAX_SNIPPET_CHARS / 2 + MAX_NEEDLE_CHARS + MAX_SNIPPET_CHARS / 2 + 2;

    /** Why the scan stopped; absence beyond the stop is not proof. */
    public enum StopReason { EOF, RESULT_LIMIT, SCAN_LIMIT }

    public static final class Match {
        private final long offset;private final String snippet;
        Match(long offset,String snippet){this.offset=offset;this.snippet=snippet;}
        public long offset(){return offset;}
        public String snippet(){return snippet;}
    }
    public static final class Result {
        private final List<Match> matches;private final StopReason stopReason;private final long scannedChars,readChars;
        private Result(List<Match> matches,StopReason stopReason,long scanned,long read){this.matches=Collections.unmodifiableList(matches);this.stopReason=stopReason;scannedChars=scanned;readChars=read;}
        public List<Match> matches(){return matches;}
        /** RESULT_LIMIT or SCAN_LIMIT mean truncated; EOF means the reader was exhausted. */
        public boolean truncated(){return stopReason!=StopReason.EOF;}
        public StopReason stopReason(){return stopReason;}
        public long scannedChars(){return scannedChars;}
        /** Actual Reader-delivered characters, including unused tail of last result-limited chunk. */
        public long readChars(){return readChars;}
    }
    public static final class Page {
        private final List<Match> matches;private final int nextIndex;private final boolean more;
        Page(List<Match> matches,int nextIndex,boolean more){this.matches=Collections.unmodifiableList(matches);this.nextIndex=nextIndex;this.more=more;}
        public List<Match> matches(){return matches;}
        public int nextIndex(){return nextIndex;}
        public boolean more(){return more;}
    }

    /**
     * Search the authorized reader. Ownership transfers: the reader is always closed, including
     * on invalid arguments, cancellation and read failure (close failure is reported).
     */
    public static Result search(Reader authorizedReader,String literal,int maxResults,BooleanSupplier cancelled) throws IOException {
        if(authorizedReader==null)throw new IllegalArgumentException("Authorized reader required");
        // The caller transfers ownership at entry. Even bad arguments must not leak that reader.
        Result completed;
        try(Reader reader=authorizedReader){
            if(cancelled==null || literal==null || literal.isEmpty() || literal.length()>MAX_NEEDLE_CHARS)
                throw new IllegalArgumentException("Invalid literal or cancellation source");
            text(literal);
            if(maxResults<1 || maxResults>MAX_RESULTS)throw new IllegalArgumentException("Invalid result limit");
            completed=scan(reader,literal,maxResults,cancelled);
        }
        // Closing can block; cancellation received DURING close must also suppress publication.
        checkCancelled(cancelled);
        return completed;
    }

    private static Result scan(Reader reader,String needle,int maxResults,BooleanSupplier cancelled) throws IOException {
        int[] failure=failure(needle);
        List<Match> matches=new ArrayList<>();
        char[] buffer=new char[CHUNK_CHARS];
        StringBuilder tail=new StringBuilder(SNIPPET_TAIL+1);
        long position=0,nextEligible=0,delivered=0;
        int matched=0,zeroReads=0;
        boolean pendingHighSurrogate=false;
        StopReason stop=StopReason.EOF;
        while(true){
            checkCancelled(cancelled);
            if(position>=MAX_SCANNED_CHARS){stop=StopReason.SCAN_LIMIT;break;}
            int request=(int)Math.min((long)buffer.length,MAX_SCANNED_CHARS-position);
            int read=reader.read(buffer,0,request);
            checkCancelled(cancelled);
            if(read== -1)break;
            if(read< -1 || read>request)throw new IOException("Invalid reader count");
            if(read==0){if(++zeroReads>MAX_ZERO_READS)throw new IOException("Reader made no progress");continue;}
            zeroReads=0;delivered+=read;
            for(int i=0;i<read;i++){
                char c=buffer[i];
                if(pendingHighSurrogate){
                    if(!Character.isLowSurrogate(c))throw new IOException("Broken UTF-16 in scanned text");
                    pendingHighSurrogate=false;
                }else if(Character.isHighSurrogate(c))pendingHighSurrogate=true;
                else if(Character.isLowSurrogate(c))throw new IOException("Broken UTF-16 in scanned text");
                tail.append(c);
                if(tail.length()>SNIPPET_TAIL)tail.delete(0,tail.length()-SNIPPET_TAIL);
                while(matched>0 && c!=needle.charAt(matched))matched=failure[matched-1];
                if(c==needle.charAt(matched))matched++;
                if(matched==needle.length()){
                    long start=position-needle.length()+1;
                    if(start>=nextEligible){
                        matches.add(new Match(start,snippet(tail,position,start,needle.length())));
                        nextEligible=start+needle.length();
                        if(matches.size()>=maxResults){position++;stop=StopReason.RESULT_LIMIT;break;}
                    }
                    matched=failure[matched-1];
                }
                position++;
            }
            if(stop==StopReason.RESULT_LIMIT)break;
            if(position>=MAX_SCANNED_CHARS){stop=StopReason.SCAN_LIMIT;break;}
        }
        if(pendingHighSurrogate)throw new IOException("Unpaired UTF-16 high surrogate at scan boundary");
        checkCancelled(cancelled);
        return new Result(matches,stop,position,delivered);
    }

    /** Deterministic paging over an already computed bounded result; no re-read. */
    public static Page page(Result result,int startIndex,int pageSize){
        if(result==null || startIndex<0 || pageSize<1 || pageSize>MAX_RESULTS || startIndex>result.matches.size())throw new IllegalArgumentException("Invalid result page");
        int end=Math.min(result.matches.size(),startIndex+pageSize);
        return new Page(new ArrayList<>(result.matches.subList(startIndex,end)),end,end<result.matches.size());
    }

    /** KMP failure table: bounded by the needle length, no allocation per input character. */
    static int[] failure(String needle){
        int[] table=new int[needle.length()];
        int k=0;
        for(int i=1;i<needle.length();i++){
            while(k>0 && needle.charAt(i)!=needle.charAt(k))k=table[k-1];
            if(needle.charAt(i)==needle.charAt(k))k++;
            table[i]=k;
        }
        return table;
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if(cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())throw new InterruptedIOException("Mail search cancelled");
    }

    /** Snippet from the rolling tail; boundaries never split a surrogate pair. */
    private static String snippet(StringBuilder tail,long position,long start,int needleLength){
        long tailStart=position-tail.length()+1;
        long wantStart=Math.max(0L,start-MAX_SNIPPET_CHARS/2);
        long wantEnd=start+needleLength+MAX_SNIPPET_CHARS/2;
        int from=(int)Math.max(0,wantStart-tailStart);
        int to=(int)Math.min(tail.length(),wantEnd-tailStart);
        if(to-from>MAX_SNIPPET_CHARS)from=to-MAX_SNIPPET_CHARS;
        if(from>0 && from<tail.length() && Character.isLowSurrogate(tail.charAt(from)))from++;
        if(to<tail.length() && Character.isLowSurrogate(tail.charAt(to)))to--;
        if(to<from)to=from;
        StringBuilder cleaned=new StringBuilder(to-from);
        for(int i=from;i<to;i++){
            char c=tail.charAt(i);
            cleaned.append(Character.isISOControl(c) && c!='\n' && c!='\t'?' ':c);
        }
        return cleaned.toString();
    }
    private static void text(String value){
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16 needle");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16 needle");}
    }
    private MailSearch(){}
}
