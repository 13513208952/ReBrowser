package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;

/** Actual static settings directory model: display only, no repository/defaults/policy. */
public final class SettingsSectionsTest {
    @Test public void sevenFixedSectionsInStableChineseOrder(){
        List<SettingsSections.Section> sections = SettingsSections.sections();
        assertEquals(7, sections.size());
        String[] titles = {"通用", "外观", "搜索", "子应用", "网站权限", "来源安全", "关于"};
        SettingsSections.SectionId[] ids = {SettingsSections.SectionId.GENERAL, SettingsSections.SectionId.APPEARANCE,
                SettingsSections.SectionId.SEARCH, SettingsSections.SectionId.SUBAPPLICATIONS,
                SettingsSections.SectionId.SITE_PERMISSIONS, SettingsSections.SectionId.SOURCE_SAFETY, SettingsSections.SectionId.ABOUT};
        for (int i = 0; i < titles.length; i++) {
            assertEquals(ids[i], sections.get(i).id());
            assertEquals(titles[i], sections.get(i).title());
            assertFalse(sections.get(i).rows().isEmpty());
        }
    }
    @Test public void rowsAndSectionsHaveUniqueBoundedUiOnlyIds(){
        List<SettingsSections.Section> sections = SettingsSections.sections();
        java.util.Set<String> rowIds = new java.util.HashSet<>();
        int rows = 0;
        for (SettingsSections.Section section : sections) {
            for (SettingsSections.Row row : section.rows()) {
                assertTrue(row.id().startsWith("ui."));
                assertTrue(rowIds.add(row.id()));
                assertFalse(row.title().isEmpty());
                assertTrue(row.title().length() <= SettingsSections.MAX_TITLE_CHARS);
                rows++;
            }
        }
        assertTrue(rows <= SettingsSections.MAX_ROWS);
        assertEquals(8, rows);
    }
    @Test public void directoryIsImmutableAndDefensivelyCopied(){
        List<SettingsSections.Section> sections = SettingsSections.sections();
        assertThrows(UnsupportedOperationException.class, sections::clear);
        assertThrows(UnsupportedOperationException.class, () -> sections.get(0).rows().clear());
        assertSame(sections, SettingsSections.sections());
        assertEquals(sections, SettingsSections.sections());
    }
    @Test public void exactSectionLookupRejectsNullButNeverSelectsDefault(){
        assertSame(SettingsSections.sections().get(0), SettingsSections.require(SettingsSections.SectionId.GENERAL));
        assertSame(SettingsSections.sections().get(6), SettingsSections.require(SettingsSections.SectionId.ABOUT));
        assertThrows(IllegalArgumentException.class, () -> SettingsSections.require(null));
    }
    @Test public void unknownConfigurationIsNotReadableOrActionable(){
        assertEquals("未读取", SettingsSections.observedValueText(null));
        assertFalse(SettingsSections.actionable(null));
        assertEquals("已开启", SettingsSections.observedValueText(Boolean.TRUE));
        assertEquals("已关闭", SettingsSections.observedValueText(Boolean.FALSE));
        assertTrue(SettingsSections.actionable(Boolean.TRUE));
        assertTrue(SettingsSections.actionable(Boolean.FALSE));
    }
    @Test public void directoryNeverClaimsSavedAuthorizedOrSuccessfulState(){
        for (SettingsSections.Section section : SettingsSections.sections()) {
            assertFalse(section.title().contains("已"));
            for (SettingsSections.Row row : section.rows()) {
                for (String claim : new String[]{"已启用", "已授权", "已保存", "成功", "已登录"}) {
                    assertFalse(row.title().contains(claim));
                }
            }
        }
        assertEquals("未读取", SettingsSections.observedValueText(null));
    }
    @Test public void rowsCarryNoRunnableActivityOrStoredKeyTypes(){
        for (Method method : SettingsSections.Row.class.getDeclaredMethods()) {
            if(method.isSynthetic())continue; // --release8 private-nest accessor is not a public mutator
            assertTrue(method.getParameterCount() == 0);
            assertFalse(method.getName().startsWith("set"));
            assertFalse(method.getName().toLowerCase().contains("save"));
        }
        for (Field field : SettingsSections.Row.class.getDeclaredFields()) {
            if(!field.isSynthetic())assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()));
            for (String forbidden : new String[]{"Runnable", "android.", "Activity", "Context"}) {
                assertFalse(field.getType().getName().contains(forbidden));
            }
        }
        for (Field field : SettingsSections.class.getDeclaredFields()) {
            assertFalse(field.getType().getName().startsWith("android."));
        }
    }
    @Test public void sourceSafetyRowDoesNotReplaceFourStepConfirmation(){
        boolean found = false;
        for (SettingsSections.Section section : SettingsSections.sections()) {
            if (section.id() != SettingsSections.SectionId.SOURCE_SAFETY) continue;
            for (SettingsSections.Row row : section.rows()) {
                assertFalse(row.title().contains("信任"));
                assertFalse(row.title().contains("强行"));
                assertFalse(row.title().contains("销毁"));
                found = true;
            }
        }
        assertTrue(found);
    }
}
