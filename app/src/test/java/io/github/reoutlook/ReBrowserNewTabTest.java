package io.github.reoutlook;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ReBrowserNewTabTest {
    private Context context;
    private ReBrowserPreferences preferences;
    private ReBrowserHistory history;
    private ReBrowserStore.Workspace workspace;
    private final AtomicLong now = new AtomicLong(10_000_000);
    private final List<String> opened = new ArrayList<>();

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("rebrowser_preferences_v1", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("rebrowser_workspace_store_v1", Context.MODE_PRIVATE).edit().clear().commit();
        context.deleteDatabase("rebrowser_history.db");
        preferences = new ReBrowserPreferences(context);
        history = new ReBrowserHistory(context, now::get); history.setPaused(false);
        workspace = new ReBrowserStore.Workspace("a".repeat(32), "Test", ReBrowserStore.Level.TEMPORARY);
        workspace.tabs.add(new ReBrowserStore.Tab("b".repeat(32), "Current", "https://example.com/"));
        workspace.activeTabId = workspace.tabs.get(0).id;
    }
    @After public void finish() { history.setPaused(false); history.close(); }
    private ReBrowserNewTabView view() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ReBrowserNewTabView view = new ReBrowserNewTabView(activity, preferences, history,
                value -> opened.add(preferences.navigationUrl(value)));
        activity.setContentView(view); return view;
    }
    private View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View result = find(((ViewGroup) view).getChildAt(i), description); if (result != null) return result;
        }
        return null;
    }
    private long visit(String url, String title) {
        return history.record(workspace, workspace.activeTab(), url, title);
    }

    @Test public void backgroundDefaultsAndFlavorFallbackDoNotLoseRememberedChoice() {
        assertEquals(ReBrowserBackgroundAsset.resourceId() == 0 ? "white" : "exclusive", preferences.newTabBackground()); assertTrue(preferences.newTabRecentEnabled());
        assertEquals(ReBrowserBackgroundAsset.resourceId() != 0, preferences.exclusiveBackgroundEnabled());
        preferences.setNewTabBackground("exclusive");
        assertEquals(ReBrowserBackgroundAsset.resourceId() != 0, preferences.exclusiveBackgroundEnabled());
        preferences.setNewTabBackground("bad-value"); assertEquals("exclusive", preferences.newTabBackground());
        ReBrowserPreferences restored = new ReBrowserPreferences(context);
        assertEquals("exclusive", restored.newTabBackground());
        preferences.setNewTabRecentEnabled(false); assertFalse(restored.newTabRecentEnabled());
    }
    @Test public void blankDetectionIsExactAndDoesNotTurnWebPagesIntoNativePages() {
        assertTrue(ReBrowserNewTabView.isBlank("about:blank")); assertTrue(ReBrowserNewTabView.isBlank("ABOUT:BLANK"));
        assertFalse(ReBrowserNewTabView.isBlank(null)); assertFalse(ReBrowserNewTabView.isBlank(""));
        assertFalse(ReBrowserNewTabView.isBlank("about:blank#frame")); assertFalse(ReBrowserNewTabView.isBlank("https://example.com/"));
    }
    @Test public void outerWorkspaceStartsBlankButChildAndHomepageRemainSeparate() {
        ReBrowserWorkspaceController controller = new ReBrowserWorkspaceController(new ReBrowserStore(context));
        controller.initialize(ReBrowserPreferences.NEW_TAB_URL);
        assertEquals("about:blank", controller.activeWorkspace().activeTab().url);
        ReBrowserStore.Workspace another = controller.createTemporary(ReBrowserPreferences.NEW_TAB_URL);
        assertEquals("about:blank", another.activeTab().url);
        ReBrowserStore.Tab child = controller.addTab(another, preferences.homeUrl());
        assertEquals(preferences.homeUrl(), child.url);
        controller.closeTab(another, child, preferences.homeUrl());
        ReBrowserWorkspaceController.TabCloseResult reset = controller.closeTab(another,
                another.activeTab(), preferences.homeUrl());
        assertTrue(reset.resetOnly);
        assertEquals(preferences.homeUrl(), another.activeTab().url);
        assertFalse(ReBrowserNewTabView.isBlank(another.activeTab().url));
    }
    @Test public void blankMetadataSurvivesPersistentWorkspaceRestoration() {
        ReBrowserStore store = new ReBrowserStore(context);
        ReBrowserWorkspaceController controller = new ReBrowserWorkspaceController(store);
        controller.initialize("about:blank");
        assertTrue(controller.lockAsSecondary(controller.activeWorkspace()));
        ReBrowserWorkspaceController restored = new ReBrowserWorkspaceController(store);
        restored.initialize("about:blank");
        assertEquals("about:blank", restored.activeWorkspace().activeTab().url);
        assertEquals(controller.activeWorkspace().profileName, restored.activeWorkspace().profileName);
    }
    @Test public void engineNameAndNavigationReuseActualSettings() {
        assertEquals("Bing", preferences.searchEngineName());
        assertEquals("https://example.com/a", preferences.navigationUrl("example.com/a"));
        assertEquals(preferences.homeUrl(), preferences.navigationUrl(" "));
        assertEquals("http://example.com/", preferences.navigationUrl("http://example.com/"));
        for (String engine : List.of("bing", "baidu", "google", "duckduckgo")) {
            preferences.setSearchEngine(engine);
            assertEquals(preferences.searchUrl("a b"), preferences.navigationUrl("a b"));
        }
    }
    @Test public void submitAndKeyboardActionNavigateButBlankInputDoesNot() {
        ReBrowserNewTabView view = view();
        EditText input = (EditText) find(view, "新标签页搜索或输入网址");
        View submit = find(view, "提交搜索或网址");
        submit.performClick(); assertTrue(opened.isEmpty());
        input.setText("example.com/path"); submit.performClick();
        assertEquals("https://example.com/path", opened.get(0));
        input.setText("native test"); input.onEditorAction(EditorInfo.IME_ACTION_GO);
        assertEquals(preferences.searchUrl("native test"), opened.get(1));
        assertFalse(input.hasFocus());
    }
    @Test public void engineChangesInvalidateNativePresentationWithoutChangingHome() {
        ReBrowserNewTabView first = view(); String home = preferences.homeUrl();
        assertNotNull(find(first, "默认搜索引擎：Bing")); assertTrue(first.matchesPreferences());
        preferences.setSearchEngine("google"); assertFalse(first.matchesPreferences());
        assertNotNull(find(view(), "默认搜索引擎：Google")); assertEquals(home, preferences.homeUrl());
    }
    @Test public void recentFrameContainsOnlyShortcutsWithoutHeadingsOrFutureControls() {
        visit("https://example.com/", "Entry");
        ReBrowserNewTabView view = view();
        assertNotNull(find(view, "Entry，example.com"));
        assertNull(find(view, "查看全部历史记录"));
        assertNull(find(view, "设置新标签页背景和近期访问"));
        assertFalse(hasText(view, "近期访问"));
        assertFalse(hasText(view, "全部"));
        assertFalse(hasText(view, "自定义新标签页"));
    }
    @Test public void allEnginesUseTheirOwnPackagedIconsAndCompleteLogos() {
        java.util.Set<Integer> icons = new java.util.HashSet<>(), logos = new java.util.HashSet<>();
        for (String engine : List.of("bing", "baidu", "google", "duckduckgo")) {
            preferences.setSearchEngine(engine);
            ReBrowserNewTabView view = view();
            android.widget.ImageView logo = (android.widget.ImageView) find(view,
                    "默认搜索引擎：" + preferences.searchEngineName());
            android.widget.ImageView icon = (android.widget.ImageView) find(view,
                    preferences.searchEngineName() + " 搜索");
            int logoId = org.robolectric.Shadows.shadowOf(logo.getDrawable()).getCreatedFromResId();
            int iconId = org.robolectric.Shadows.shadowOf(icon.getDrawable()).getCreatedFromResId();
            assertEquals(ReBrowserSearchEngineAssets.logo(engine, preferences.exclusiveBackgroundEnabled()), logoId);
            assertEquals(ReBrowserSearchEngineAssets.icon(engine), iconId);
            assertNull(logo.getImageTintList()); assertNull(icon.getImageTintList());
            assertNotNull(logo.getDrawable()); assertNotNull(icon.getDrawable());
            assertFalse(hasText(view, preferences.searchEngineName()));
            logos.add(logoId); icons.add(iconId);
        }
        assertEquals(4, logos.size()); assertEquals(4, icons.size());
    }
    @Test public void googleUsesItsProvidedLightLogoOnlyOnActualPhotoBackground() {
        preferences.setSearchEngine("google"); preferences.setNewTabBackground("exclusive");
        android.widget.ImageView logo = (android.widget.ImageView) find(view(), "默认搜索引擎：Google");
        assertEquals(ReBrowserSearchEngineAssets.logo("google", preferences.exclusiveBackgroundEnabled()),
                org.robolectric.Shadows.shadowOf(logo.getDrawable()).getCreatedFromResId());
        assertNotEquals(ReBrowserSearchEngineAssets.logo("google", true),
                ReBrowserSearchEngineAssets.logo("google", false));
        assertEquals(ReBrowserSearchEngineAssets.logo("bing", false),
                ReBrowserSearchEngineAssets.logo("bing", true));
    }
    @Test public void allEngineLogosAreDisplayedWithoutAnAddedWhitePanel() {
        for (String background : List.of("white", "exclusive")) {
            preferences.setNewTabBackground(background);
            for (String engine : List.of("bing", "baidu", "google", "duckduckgo")) {
                preferences.setSearchEngine(engine);
                android.widget.ImageView logo = (android.widget.ImageView) find(view(),
                        "默认搜索引擎：" + preferences.searchEngineName());
                assertNull(logo.getBackground());
                assertNull(((View) logo.getParent()).getBackground());
                assertNull(((View) logo.getParent().getParent()).getBackground());
                assertNull(logo.getImageTintList());
            }
        }
    }
    @Test public void packagedBrandGraphicsAreImagesNotRecreatedLiveText() throws Exception {
        java.util.Set<Integer> resources = new java.util.HashSet<>();
        for (String engine : List.of("bing", "baidu", "google", "duckduckgo")) {
            resources.add(ReBrowserSearchEngineAssets.icon(engine));
            resources.add(ReBrowserSearchEngineAssets.logo(engine, false));
            resources.add(ReBrowserSearchEngineAssets.logo(engine, true));
        }
        assertEquals(9, resources.size());
        for (int id : resources) try (java.io.InputStream input = context.getResources().openRawResource(id)) {
            byte[] header = input.readNBytes(12);
            assertEquals("RIFF", new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
            assertEquals("WEBP", new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII));
        }
    }
    @Test public void noHistoryDoesNotRenderAnEmptyInstructionCard() {
        ReBrowserNewTabView view = view();
        android.widget.FrameLayout frame = (android.widget.FrameLayout) view.getChildAt(0);
        android.widget.LinearLayout body = (android.widget.LinearLayout) frame.getChildAt(0);
        assertEquals(3, body.getChildCount());
        assertEquals(View.GONE, body.getChildAt(2).getVisibility());
        assertFalse(hasText(view, "访问网页后，最近的页面会出现在这里"));
    }
    private boolean hasText(View view, String text) {
        if (view instanceof android.widget.TextView
                && text.contentEquals(((android.widget.TextView) view).getText())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            if (hasText(((ViewGroup) view).getChildAt(i), text)) return true;
        }
        return false;
    }
    @Test public void recentReadsDeduplicateFullUrlsAndUseNewestTitleAndTimestampTie() {
        long first = visit("https://example.com/a", "Old");
        visit("https://example.com/b", "B");
        long newest = visit("https://example.com/a", "New");
        List<ReBrowserHistory.Entry> entries = history.recentEntries(8);
        assertEquals(2, entries.size()); assertEquals(newest, entries.get(0).id);
        assertEquals("New", entries.get(0).title);
        assertEquals(3, history.entries(0, 50).size()); assertNotNull(history.find(first));
        now.decrementAndGet(); visit("https://example.com/a", "Older clock");
        assertEquals(newest, history.recentEntries(8).get(0).id);
    }
    @Test public void recentExpiryMaintainAndPauseKeepExistingRules() {
        visit("https://example.com/timed", "Timed");
        workspace.level = ReBrowserStore.Level.SECONDARY; visit("https://example.com/durable", "Durable");
        now.addAndGet(ReBrowserHistory.RETENTION_MS);
        assertEquals(1, history.recentEntries(8).size());
        history.setPaused(true); assertEquals(1, history.recentEntries(8).size());
        assertEquals(0, visit("https://example.com/paused", "Paused"));
        history.setPaused(false); workspace.level = ReBrowserStore.Level.TEMPORARY;
        visit("https://example.com/held", "Held"); history.setMaintaining(true);
        now.addAndGet(ReBrowserHistory.RETENTION_MS); assertEquals(2, history.recentEntries(8).size());
    }
    @Test public void staleRecentShortcutDoesNotResurrectOrNavigateDeletedVisit() {
        long id = visit("https://example.com/", "Entry"); ReBrowserNewTabView view = view();
        View shortcut = find(view, "Entry，example.com"); assertNotNull(shortcut);
        history.delete(id); shortcut.performClick(); assertTrue(opened.isEmpty());
    }
    @Test public void recentShortcutOpensInCurrentEnvironmentUsingOnlyUrl() {
        visit("https://example.com/path", "Entry"); ReBrowserNewTabView view = view();
        find(view, "Entry，example.com").performClick();
        assertEquals(List.of("https://example.com/path"), opened);
    }
    @Test public void disablingRecentDoesNotDeleteHistoryOrRenderItsShortcuts() {
        visit("https://example.com/", "Entry"); preferences.setNewTabRecentEnabled(false);
        assertNull(find(view(), "Entry，example.com")); assertEquals(1, history.entries(0, 10).size());
    }
    @Test public void recentLimitAffectsPresentationNotHistoryRetention() {
        for (int i = 0; i < 40; i++) visit("https://example.com/" + i, "Item " + i);
        assertEquals(8, history.recentEntries(8).size()); assertEquals(24, history.recentEntries(999).size());
        assertEquals(40, history.entries(0, 100).size());
    }
    @Test public void migrationAddsOnlyAnIndexAndPreservesHistoryAndMaintain() {
        history.close();
        try (SQLiteDatabase legacy = context.openOrCreateDatabase("rebrowser_history.db", Context.MODE_PRIVATE, null)) {
            legacy.execSQL("CREATE TABLE visits (id INTEGER PRIMARY KEY AUTOINCREMENT, workspace_id TEXT NOT NULL,"
                    + "tab_id TEXT NOT NULL,title TEXT NOT NULL,url TEXT NOT NULL,source_title TEXT NOT NULL,"
                    + "visited_at INTEGER NOT NULL,expires_at INTEGER)");
            legacy.execSQL("CREATE TABLE settings(name TEXT PRIMARY KEY,value INTEGER NOT NULL)");
            legacy.execSQL("INSERT INTO settings VALUES('maintain',1)");
            legacy.execSQL("INSERT INTO visits VALUES(7,'workspace','tab','Saved','https://example.com/','Source',1,2)");
            legacy.setVersion(1);
        }
        history = new ReBrowserHistory(context, now::get);
        assertTrue(history.maintaining()); assertEquals("Saved", history.find(7).title);
        assertEquals(1, history.recentEntries(8).size());
        try (SQLiteDatabase db = context.openOrCreateDatabase("rebrowser_history.db", Context.MODE_PRIVATE, null);
                Cursor index = db.rawQuery("SELECT name FROM sqlite_master WHERE name='visits_url_order'", null)) {
            assertEquals(2, db.getVersion()); assertTrue(index.moveToFirst());
        }
    }
    @Test public void photoCropStaysCenteredAndTopAnchoredAcrossPhoneAndTablets() {
        for (int[] target : new int[][]{{1080,2400},{2048,2732},{2732,2048},{3200,1800},{400,900}}) {
            Rect crop = ReBrowserNewTabBackground.crop(3324,2493,target[0],target[1]);
            assertEquals(0, crop.top); assertTrue(crop.bottom <= 2493); assertTrue(crop.right <= 3324);
            assertTrue(Math.abs((crop.left + crop.right) - 3324) <= 1);
            assertEquals((double)target[0]/target[1], (double)crop.width()/crop.height(), .002);
        }
    }
    @Test public void extremeViewportNeverProducesEmptyOrOutOfBoundsCrop() {
        Rect tall = ReBrowserNewTabBackground.crop(3324,2493,1,100000);
        assertEquals(1,tall.width()); assertEquals(2493,tall.height());
        Rect wide = ReBrowserNewTabBackground.crop(3324,2493,100000,1);
        assertEquals(3324,wide.width()); assertEquals(1,wide.height());
    }
}
