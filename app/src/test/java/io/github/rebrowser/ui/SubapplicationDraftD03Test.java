package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** Bounded subapplication draft and atomic batch intent. */
public final class SubapplicationDraftD03Test {
    private static SubapplicationSettingsActivity.Options options(){
        java.util.Set<String> domain=new java.util.LinkedHashSet<>(java.util.Arrays.asList("a","b"));
        return new SubapplicationSettingsActivity.Options("music",java.util.Arrays.asList(
                new SubapplicationSettingsActivity.Option("src","音源",domain,"a")),3);
    }
    @Test public void draftMergesEditsIntoOneBatchAndCancelsOnlyUndelivered(){
        SubapplicationSettingsActivity.Draft draft=new SubapplicationSettingsActivity.Draft(options());
        assertEquals("不支持的选项值，未加入",draft.set("src","z"));
        assertNull(draft.set("src","b"));
        assertTrue(draft.isDirty());
        java.util.Map<String,String> pending=draft.pending();
        assertEquals("b",pending.get("src"));
        assertThrows(UnsupportedOperationException.class,()->pending.put("src","a"));
        SubapplicationSettingsActivity.Batch batch=draft.beginAttempt();
        assertEquals(3,batch.expectedRevision());
        assertEquals("music",batch.subapplicationId());
        assertEquals("b",batch.changes().get("src"));
        assertFalse(batch.isPersisted());
        assertFalse(draft.cancel());
        assertEquals("更改已提交，等待原生重新读取",draft.set("src","a"));
    }
    @Test public void batchAndOptionsRejectUnboundedOrDuplicateInput(){
        assertThrows(IllegalArgumentException.class,()->new SubapplicationSettingsActivity.Batch("music",1,java.util.Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class,()->new SubapplicationSettingsActivity.Batch("music",1,null));
        java.util.Map<String,String> tooMany=new java.util.LinkedHashMap<>();
        for(int i=0;i<SubapplicationSettingsActivity.MAX_OPTIONS+1;i++)tooMany.put("o"+i,"v");
        assertThrows(IllegalArgumentException.class,()->new SubapplicationSettingsActivity.Batch("music",1,tooMany));
        java.util.Set<String> domain=java.util.Set.of("a");
        assertThrows(IllegalArgumentException.class,()->new SubapplicationSettingsActivity.Option("src","音源",domain,"other"));
        assertThrows(IllegalArgumentException.class,()->new SubapplicationSettingsActivity.Option("bad token","音源",domain,null));
    }
}
