package io.github.rebrowser.outlook;

import static org.junit.Assert.*;
import java.io.IOException;
import java.io.StringReader;
import org.junit.Test;

/** Bounded literal matching over an already-authorized Reader; no DB, decrypt or export. */
public final class MailSearchD03Test {
    @Test public void literalMatchesAcrossArtificialChunkBoundary(){
        StringBuilder text=new StringBuilder();
        for(int i=0;i<MailSearch.CHUNK_CHARS-1;i++)text.append('a');
        text.append("中文关键词");
        MailSearch.Result result=search(text.toString(),"中文关键词",10);
        assertEquals(1,result.matches().size());
        assertEquals(MailSearch.CHUNK_CHARS-1,result.matches().get(0).offset());
        assertTrue(result.matches().get(0).snippet().contains("中文关键词"));
    }
    @Test public void literalOnlyNoRegexInterpretation(){
        MailSearch.Result result=search("a.c abc","a.c",10);
        assertEquals(1,result.matches().size());
        assertEquals(0,result.matches().get(0).offset());
    }
    @Test public void resultLimitTruncatesExplicitly(){
        MailSearch.Result result=search("xx xx xx xx","xx",2);
        assertEquals(2,result.matches().size());
        assertTrue(result.truncated());
    }
    @Test public void cancellationAndProgressFailuresAreReported(){
        assertThrows(IOException.class,()->search("abc","a",10,()->true));
        assertThrows(IOException.class,()->search(new java.io.Reader(){public int read(char[] b,int o,int l){return 0;}public void close(){}},  "a",10,()->false));
        assertThrows(IllegalArgumentException.class,()->search("abc","",10));
        assertThrows(IllegalArgumentException.class,()->search("abc",new String(new char[65]).replace('\0','a'),10));
    }
    @Test public void overlappingLiteralIsNotReEmittedAcrossReaderChunks()throws java.io.IOException{
        MailSearch.Result single=search(chunked("aaa",2),"aa",10,()->false);
        assertEquals(1,single.matches().size());
        assertEquals(0,single.matches().get(0).offset());

        MailSearch.Result two=search(chunked("aaaa",2),"aa",10,()->false);
        assertEquals(2,two.matches().size());
        assertEquals(0,two.matches().get(0).offset());
        assertEquals(2,two.matches().get(1).offset());
        MailSearch.Result crossing=search(chunked("aaa",1),"aa",10,()->false);
        assertEquals(1,crossing.matches().size());
        assertEquals(0,crossing.matches().get(0).offset());
    }
    private static java.io.Reader chunked(String input,int chunkSize){
        return new java.io.Reader(){
            int at;
            @Override public int read(char[] dest,int offset,int length){
                if(at>=input.length())return -1;
                int count=Math.min(Math.min(chunkSize,length),input.length()-at);
                input.getChars(at,at+count,dest,offset);
                at+=count;
                return count;
            }
            @Override public void close(){}
        };
    }
    @Test public void pagingIsDeterministicAndNeverRereads(){
        MailSearch.Result result=search("x x x","x",10);
        MailSearch.Page page=MailSearch.page(result,0,2);
        assertEquals(2,page.matches().size());
        assertTrue(page.more());
        assertEquals(2,page.nextIndex());
        MailSearch.Page last=MailSearch.page(result,2,2);
        assertFalse(last.more());
        assertThrows(IllegalArgumentException.class,()->MailSearch.page(result,4,2));
    }
    private static MailSearch.Result search(String text,String needle,int max){try{return MailSearch.search(new StringReader(text),needle,max,()->false);}catch(IOException impossible){throw new AssertionError(impossible);}}
    private static MailSearch.Result search(String text,String needle,int max,java.util.function.BooleanSupplier cancelled)throws IOException{return MailSearch.search(new StringReader(text),needle,max,cancelled);}
    private static MailSearch.Result search(java.io.Reader reader,String needle,int max,java.util.function.BooleanSupplier cancelled)throws IOException{return MailSearch.search(reader,needle,max,cancelled);}

    @Test public void kmpPreservesNonOverlappingSemanticsAndStopReasons() throws Exception {
        MailSearch.Result single=search("aaa","aa",10);
        assertEquals(1,single.matches().size());
        assertEquals(0,single.matches().get(0).offset());
        MailSearch.Result two=search("aaaa","aa",10);
        assertEquals(2,two.matches().size());
        assertEquals(2,two.matches().get(1).offset());
        assertEquals(MailSearch.StopReason.EOF,single.stopReason());
        MailSearch.Result limited=search("aaaaa","a",2);
        assertEquals(MailSearch.StopReason.RESULT_LIMIT,limited.stopReason());
        assertTrue(limited.truncated());
        assertEquals(2,limited.matches().size());
    }
    @Test public void readerIsClosedOnEveryPathAndBrokenUtf16Fails(){
        final boolean[] closed={false};
        java.io.Reader broken=new java.io.Reader(){
            public int read(char[] b,int o,int l){b[o]='\ud800';return 1;}
            public void close(){closed[0]=true;}
        };
        assertThrows(java.io.IOException.class,()->MailSearch.search(broken,"a",10,()->false));
        assertTrue(closed[0]);
        final boolean[] cancelledClosed={false};
        java.io.Reader cancel=new java.io.Reader(){
            public int read(char[] b,int o,int l){return -1;}
            public void close(){cancelledClosed[0]=true;}
        };
        assertThrows(java.io.IOException.class,()->MailSearch.search(cancel,"a",10,()->true));
        assertTrue(cancelledClosed[0]);
        final int[] reads={0};
        java.io.Reader zero=new java.io.Reader(){
            public int read(char[] b,int o,int l){reads[0]++;return 0;}
            public void close(){}
        };
        assertThrows(java.io.IOException.class,()->MailSearch.search(zero,"a",10,()->false));
        assertTrue(reads[0]<100);
    }
    @Test public void hardScanLimitNeverReadsOrMatchesBeyondBudget() throws Exception {
        final long[] produced={0};final int[] maxRequest={0};final boolean[] closed={false};
        java.io.Reader source=new java.io.Reader(){
            @Override public int read(char[] b,int off,int len){
                maxRequest[0]=Math.max(maxRequest[0],len);
                int n=Math.min(len,8192);
                java.util.Arrays.fill(b,off,off+n,'a');
                for(int i=0;i<n;i++)if(produced[0]+i>=MailSearch.MAX_SCANNED_CHARS)b[off+i]='X';
                produced[0]+=n;return n;
            }
            @Override public void close(){closed[0]=true;}
        };
        MailSearch.Result result=MailSearch.search(source,"X",10,()->false);
        assertEquals(MailSearch.StopReason.SCAN_LIMIT,result.stopReason());
        assertEquals(MailSearch.MAX_SCANNED_CHARS,result.scannedChars());
        assertEquals(MailSearch.MAX_SCANNED_CHARS,produced[0]);
        assertEquals(0,result.matches().size());
        assertTrue(closed[0]);
        assertTrue(maxRequest[0]<=MailSearch.CHUNK_CHARS);
    }
    @Test public void invalidArgumentsTransferReaderOwnershipAndCloseTimeCancellationWins() throws Exception {
        final boolean[] closed={false};
        java.io.Reader invalid=new java.io.StringReader("abc"){
            @Override public void close(){closed[0]=true;super.close();}
        };
        assertThrows(IllegalArgumentException.class,()->MailSearch.search(invalid,"",10,()->false));
        assertTrue(closed[0]);
        final boolean[] doneClosing={false};
        java.io.Reader reader=new java.io.StringReader("abc"){
            @Override public void close(){doneClosing[0]=true;super.close();}
        };
        assertThrows(java.io.InterruptedIOException.class,
                ()->MailSearch.search(reader,"x",10,()->doneClosing[0]));
        assertTrue(doneClosing[0]);
    }
    @Test public void resultCountAndSnippetAreBoundedExactly() throws Exception {
        MailSearch.Result two=search("aaaaa","a",2);
        assertEquals(2,two.scannedChars());
        String target="abcdefghijklmnopqrstuvwxabcdefghijklmnopqrstuvwxabcdefghijklmnopqrstuvwx";
        assertTrue(target.length()>64);
        String needle=target.substring(0,64);
        MailSearch.Result one=search(new String(new char[150]).replace('\0','z')+needle,needle,5);
        assertEquals(1,one.matches().size());
        assertTrue(one.matches().get(0).snippet().length()<=MailSearch.MAX_SNIPPET_CHARS);
    }
    @Test public void malformedNegativeReaderCountIsNotEof() {
        java.io.Reader invalid=new java.io.Reader(){
            public int read(char[] dst,int offset,int length){return -2;}
            public void close(){}
        };
        assertThrows(IOException.class,()->MailSearch.search(invalid,"x",10,()->false));
    }
}
