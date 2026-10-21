package io.github.rebrowser.music;

import static org.junit.Assert.*;
import org.junit.Test;

/** Read-only projection + change intents; nothing is persisted or auto-played. */
public final class MusicPreferencesD03Test {
    private static MusicPreferences.Snapshot snapshot(){
        return MusicPreferences.snapshot(new MusicPreferences.Values(MusicPreferences.Source.OFFICIAL_WEB,MusicPreferences.Entry.MUSIC_HOME,false,0,"tok"),4);
    }
    @Test public void unknownAxesAndCorruptFieldsStayExplicit(){
        MusicPreferences.Snapshot snapshot=MusicPreferences.snapshot(new MusicPreferences.Values(MusicPreferences.Source.UNKNOWN,MusicPreferences.Entry.UNKNOWN,null,0,null),1);
        assertEquals("音源未知",snapshot.sourceLabel());
        assertEquals("恢复意图未读取",snapshot.resumeLabel());
        assertEquals(java.util.Arrays.asList("bad"),MusicPreferences.rejectedFields(java.util.Collections.singletonMap("bad",new String(new char[257]).replace('\0','x'))));
        assertThrows(IllegalArgumentException.class,()->new MusicPreferences.Values(null,MusicPreferences.Entry.MUSIC_HOME,null,0,null));
    }
    @Test public void changesCarryExactRevisionAndAreNotPersistence(){
        MusicPreferences.Snapshot snapshot=snapshot();
        MusicPreferences.Change change=MusicPreferences.changeEntry(snapshot,MusicPreferences.Entry.SEARCH);
        assertEquals(4,change.expectedRevision());
        assertEquals(MusicPreferences.Entry.SEARCH,change.values().entry());
        assertFalse(change.isPersisted());
        assertEquals(MusicPreferences.Source.OFFICIAL_WEB,change.values().source());
    }
    @Test public void unknownCannotBePersistedAndResumeDoesNotAuthorizePlayback(){
        assertThrows(IllegalArgumentException.class,()->MusicPreferences.changeSource(snapshot(),MusicPreferences.Source.UNKNOWN));
        MusicPreferences.Change resume=MusicPreferences.changeResume(snapshot(),true,12_000);
        assertEquals(Boolean.TRUE,resume.values().resumeIntent());
        assertEquals(12_000,resume.values().resumePositionMs());
        assertFalse(resume.isPersisted());
        assertThrows(IllegalArgumentException.class,()->MusicPreferences.changeResume(snapshot(),true,-1));
        assertEquals(0,MusicPreferences.clearQueue(snapshot()).values().lastQueueToken()==null?0:1);
    }

    @Test public void draftMergesMultipleAxesIntoOneChangeWithOriginalRevision(){
        MusicPreferences.Draft draft=new MusicPreferences.Draft(snapshot());
        assertNull(draft.setSource(MusicPreferences.Source.OFFICIAL_WEB));
        assertNull(draft.setEntry(MusicPreferences.Entry.SEARCH));
        assertNull(draft.setResume(true,5000));
        MusicPreferences.Change change=draft.merge();
        assertEquals(4,change.expectedRevision());
        assertEquals(MusicPreferences.Entry.SEARCH,change.values().entry());
        assertEquals(Boolean.TRUE,change.values().resumeIntent());
        assertEquals(5000,change.values().resumePositionMs());
        assertFalse(change.isPersisted());
        assertEquals("同一项存在冲突修改",draft.setEntry(MusicPreferences.Entry.MUSIC_HOME));
    }
    @Test public void draftRejectsUnknownAxesAndNoopIsNotDirty(){
        MusicPreferences.Draft draft=new MusicPreferences.Draft(snapshot());
        assertFalse(draft.isDirty());
        assertEquals("未知音源不能保存",draft.setSource(MusicPreferences.Source.UNKNOWN));
        assertEquals("未知入口不能保存",draft.setEntry(MusicPreferences.Entry.UNKNOWN));
        assertEquals("恢复位置超出范围",draft.setResume(true,-1));
        assertFalse(draft.isDirty());
        assertNull(draft.clearQueue());
        assertTrue(draft.isDirty()); // the base DID carry a queue token; clearing is a change
        MusicPreferences.Snapshot emptyQueue=MusicPreferences.snapshot(new MusicPreferences.Values(MusicPreferences.Source.OFFICIAL_WEB,MusicPreferences.Entry.MUSIC_HOME,null,0,null),2);
        assertFalse(new MusicPreferences.Draft(emptyQueue).isDirty());
    }
}
