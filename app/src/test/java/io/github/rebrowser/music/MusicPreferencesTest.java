package io.github.rebrowser.music;

import static org.junit.Assert.*;
import org.junit.Test;

/** Read-only projection + change intents; nothing is persisted or auto-played. */
public final class MusicPreferencesTest {
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
}
