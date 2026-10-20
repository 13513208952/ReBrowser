package io.github.rebrowser.outlook;

import static org.junit.Assert.*;
import java.io.IOException;
import java.io.StringReader;
import org.junit.Test;

/** Bounded literal matching over an already-authorized Reader; no DB, decrypt or export. */
public final class MailSearchTest {
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
}
