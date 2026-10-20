package io.github.rebrowser.subapps;

import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;

/** Actual fixed directory model only: no Android state, no persisted enablement, no opener. */
public final class SubapplicationRegistryTest {
    @Test public void containsExactlyOutlookAndMusicInStableOrder(){
        List<SubapplicationRegistry.Descriptor> all = SubapplicationRegistry.all();
        assertEquals(2, all.size());
        assertEquals("outlook", all.get(0).localId());
        assertEquals("Outlook", all.get(0).displayName());
        assertEquals("music", all.get(1).localId());
        assertEquals("音乐", all.get(1).displayName());
    }
    @Test public void localIdentifiersAreUniqueAndBounded(){
        List<SubapplicationRegistry.Descriptor> all = SubapplicationRegistry.all();
        assertNotEquals(all.get(0).localId(), all.get(1).localId());
        for (SubapplicationRegistry.Descriptor descriptor : all) {
            assertTrue(descriptor.localId().length() <= SubapplicationRegistry.MAX_LOCAL_ID_CHARS);
            assertTrue(descriptor.description().length() <= SubapplicationRegistry.MAX_TEXT_CHARS);
            assertFalse(descriptor.description().isEmpty());
        }
    }
    @Test public void exactLookupReturnsSameImmutableEntry(){
        assertSame(SubapplicationRegistry.all().get(0), SubapplicationRegistry.require("outlook"));
        assertSame(SubapplicationRegistry.all().get(1), SubapplicationRegistry.require("music"));
        assertEquals(SubapplicationRegistry.Route.OUTLOOK_NATIVE_ENTRY, SubapplicationRegistry.require("outlook").route());
        assertEquals(SubapplicationRegistry.Route.MUSIC_NATIVE_ENTRY, SubapplicationRegistry.require("music").route());
    }
    @Test public void unknownBlankCaseVariantAndOverlongIdsNeverDefault(){
        for (String bad : new String[]{null, "", "Outlook", "MUSIC", "browser", " music", "music ", "outlook2",
                new String(new char[SubapplicationRegistry.MAX_LOCAL_ID_CHARS + 1]).replace('\0', 'a')}) {
            assertThrows(IllegalArgumentException.class, () -> SubapplicationRegistry.require(bad));
        }
    }
    @Test public void controlCharactersAndBrokenSurrogatesAreRejectedOnLookup(){
        assertThrows(IllegalArgumentException.class, () -> SubapplicationRegistry.require("out\nlook"));
        assertThrows(IllegalArgumentException.class, () -> SubapplicationRegistry.require("out\u0000look"));
        assertThrows(IllegalArgumentException.class, () -> SubapplicationRegistry.require("\ud800"));
        assertThrows(IllegalArgumentException.class, () -> SubapplicationRegistry.require("\udc00"));
    }
    @Test public void directoryAndEntriesCannotBeMutated(){
        List<SubapplicationRegistry.Descriptor> all = SubapplicationRegistry.all();
        assertSame(all, SubapplicationRegistry.all());
        assertThrows(UnsupportedOperationException.class, all::clear);
        assertThrows(UnsupportedOperationException.class, () -> all.remove(0));
        for (Method method : SubapplicationRegistry.Descriptor.class.getDeclaredMethods()) {
            if(method.isSynthetic())continue; // javac --release8 emits private-nest access bridges
            assertEquals(0, method.getParameterCount());
            assertFalse(method.getName().startsWith("set"));
        }
    }
    @Test public void directoryExposesNoEnablementAccountOrOpenOperation(){
        for (Method method : SubapplicationRegistry.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            for (String forbidden : new String[]{"enable", "disable", "login", "account", "configured", "launch", "open", "start"}) {
                assertFalse("unexpected stateful/opening method " + method.getName(), name.contains(forbidden));
            }
        }
        for (Field field : SubapplicationRegistry.Descriptor.class.getDeclaredFields()) {
            if(!field.isSynthetic())assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()));
            assertNotEquals(boolean.class, field.getType());
            assertNotEquals(Boolean.class, field.getType());
        }
    }
    @Test public void registryHasNoAndroidDependencyOrStoredPreferences(){
        assertEquals(2, SubapplicationRegistry.Route.values().length);
        for (Field field : SubapplicationRegistry.class.getDeclaredFields()) {
            assertFalse(field.getType().getName().startsWith("android."));
        }
        for (SubapplicationRegistry.Descriptor descriptor : SubapplicationRegistry.all()) {
            String text = descriptor.displayName() + descriptor.description();
            for (String claim : new String[]{"已启用", "已登录", "已授权", "已保存"}) {
                assertFalse(text.contains(claim));
            }
        }
    }
}
