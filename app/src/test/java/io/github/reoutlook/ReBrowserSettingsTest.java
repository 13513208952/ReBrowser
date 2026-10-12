package io.github.reoutlook;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class ReBrowserSettingsTest {
    private Context context;
    private ReBrowserPreferences preferences;
    private ReBrowserSitePermissions permissions;
    private static final String PROFILE = "rebrowser_workspace_0123456789abcdef0123456789abcdef";

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        for (String name : new String[]{"rebrowser_preferences_v1", "rebrowser_site_permissions_v1"})
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
        preferences = new ReBrowserPreferences(context); permissions = new ReBrowserSitePermissions(context);
    }
    private ActivityController<ReBrowserSettingsActivity> activity(String section) {
        return Robolectric.buildActivity(ReBrowserSettingsActivity.class,
                new Intent(context, ReBrowserSettingsActivity.class).putExtra(ReBrowserSettings.EXTRA_SECTION, section)).setup();
    }
    private View root(ReBrowserSettingsActivity activity) { return activity.findViewById(android.R.id.content); }
    private View page(ReBrowserSettingsActivity activity, String section) { return root(activity).findViewWithTag(section); }
    private View find(View view, String value) {
        if (value.equals(view.getContentDescription()) || (view instanceof TextView && value.contentEquals(((TextView) view).getText()))) return view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup) view).getChildCount();i++) {
            View found=find(((ViewGroup) view).getChildAt(i),value); if(found!=null)return found;
        }
        return null;
    }
    private Switch toggle(View page, String title) {
        View titleView = find(page, title);
        if (titleView instanceof Switch) return (Switch) titleView;
        ViewGroup row = (ViewGroup) titleView.getParent().getParent();
        return (Switch) row.getChildAt(1);
    }
    private <T extends View> T type(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
            T found=type(((ViewGroup)view).getChildAt(i),type);if(found!=null)return found;
        }
        return null;
    }

    @Test public void threePagesArePeersWithControlsInTheCorrectPage() {
        try(ActivityController<ReBrowserSettingsActivity> c=activity("general")) {
            ReBrowserSettingsActivity a=c.get();
            assertEquals(View.VISIBLE,page(a,"general").getVisibility());
            assertNull(page(a,"personalization"));
            assertNull(find(root(a),"个性化设置"));
            assertNotNull(find(page(a,"general"),"主页网址"));
            assertNull(find(page(a,"general"),"JavaScript"));
            assertNull(find(page(a,"general"),"显示近期访问"));
            assertNull(find(root(a),"新标签页背景"));
            assertNull(find(root(a),"显示近期访问"));
            assertNotNull(find(page(a,"applications"),"ReOutlook"));
            assertNotNull(find(page(a,"isolation"),"JavaScript"));
            assertNotNull(find(page(a,"isolation"),"网站权限管理"));
            assertNotNull(find(page(a,"isolation"),"摄像头"));
            assertNull(find(page(a,"isolation"),"主页网址"));
            find(root(a),"应用管理设置").performClick();
            assertEquals(View.VISIBLE,page(a,"applications").getVisibility());
            find(root(a),"隔离与权限设置").performClick();
            assertEquals(View.VISIBLE,page(a,"isolation").getVisibility());
            assertEquals(View.GONE,page(a,"applications").getVisibility());
            find(root(a),"常规设置").performClick();
            assertEquals(View.VISIBLE,page(a,"general").getVisibility());
        }
    }
    @Test public void routingAndRecreationKeepTheSelectedSection() {
        try(ActivityController<ReBrowserSettingsActivity> c=activity("applications")) {
            c.recreate(); assertEquals(View.VISIBLE,page(c.get(),"applications").getVisibility());
        }
        try(ActivityController<ReBrowserSettingsActivity> c=activity("unknown")) {
            assertEquals(View.VISIBLE,page(c.get(),"general").getVisibility());
        }
    }
    @Test public void reorderedNativeSettingsHonorSectionWithoutLosingDrafts() {
        try(ActivityController<ReBrowserSettingsActivity> c=activity("general")) {
            type(page(c.get(),"general"),EditText.class).setText("https://example.com/uncommitted");
            c.newIntent(new Intent().putExtra(ReBrowserSettings.EXTRA_SECTION,"applications"));
            assertEquals(View.VISIBLE,page(c.get(),"applications").getVisibility());
            assertEquals("https://example.com/uncommitted",type(page(c.get(),"general"),EditText.class).getText().toString());
            assertFalse(c.get().isFinishing());
        }
    }
    @Test public void switchingSectionsKeepsAndSavesGeneralDrafts() {
        try(ActivityController<ReBrowserSettingsActivity> c=activity("general")) {
            EditText home=type(page(c.get(),"general"),EditText.class);
            Spinner engine=type(page(c.get(),"general"),Spinner.class);
            home.setText("https://example.com/home");engine.setSelection(3);
            find(root(c.get()),"应用管理设置").performClick();
            assertEquals("https://example.com/home",home.getText().toString());
            c.pause();
            assertEquals("https://example.com/home",preferences.homeUrl());
            assertEquals("google",preferences.searchEngine());
        }
    }
    @Test public void generalInputsSurviveRecreationWhileOnAnotherSection() {
        try(ActivityController<ReBrowserSettingsActivity> c=activity("general")) {
            type(page(c.get(),"general"),EditText.class).setText("https://example.com/draft");
            type(page(c.get(),"general"),Spinner.class).setSelection(3);
            find(root(c.get()),"应用管理设置").performClick();
            c.recreate();
            assertEquals(View.VISIBLE,page(c.get(),"applications").getVisibility());
            assertEquals("https://example.com/draft",type(page(c.get(),"general"),EditText.class).getText().toString());
            assertEquals(3,type(page(c.get(),"general"),Spinner.class).getSelectedItemPosition());
        }
    }
    @Test public void untouchedGeneralFieldsDoNotOverwriteExternalUpdates() {
        try(ActivityController<ReBrowserSettingsActivity> c=activity("isolation")) {
            preferences.setSearchEngine("baidu");preferences.setHomeUrl("https://example.com/new");
            c.pause();assertEquals("baidu",preferences.searchEngine());
            assertEquals("https://example.com/new",preferences.homeUrl());
        }
    }
    @Test public void resumingRefreshesUntouchedFieldsAfterAnAdministratorUpdate() {
        try(ActivityController<ReBrowserSettingsActivity> c=activity("general")) {
            c.pause();
            preferences.setSearchEngine("baidu");preferences.setHomeUrl("https://example.com/new");
            c.resume();
            assertEquals("https://example.com/new",type(page(c.get(),"general"),EditText.class).getText().toString());
            assertEquals(1,type(page(c.get(),"general"),Spinner.class).getSelectedItemPosition());
            c.pause();assertEquals("baidu",preferences.searchEngine());
        }
    }
    @Test public void removedPersonalizationPageDoesNotEraseExplicitOwnerChoices() throws Exception {
        preferences.setNewTabBackground("exclusive"); preferences.setNewTabRecentEnabled(false);
        try(ActivityController<ReBrowserSettingsActivity> c=activity("personalization")) {
            assertEquals(View.VISIBLE,page(c.get(),"general").getVisibility());
            assertNull(page(c.get(),"personalization"));
            c.pause();assertEquals("exclusive",preferences.newTabBackground());assertFalse(preferences.newTabRecentEnabled());
            JSONObject p=ReBrowserSettings.snapshot(context).getJSONObject("personalization");
            assertEquals(ReBrowserBackgroundAsset.resourceId()!=0,p.getBoolean("photoAvailable"));
            assertEquals(ReBrowserBackgroundAsset.resourceId()!=0?"exclusive":"white",p.getString("effectiveNewTabBackground"));
        }
    }
    @Test public void explicitWhiteAndHiddenRecentSurviveOpeningEveryRemainingPage() {
        preferences.setNewTabBackground("white");preferences.setNewTabRecentEnabled(false);
        for(String section:ReBrowserSettings.SECTIONS) {
            try(ActivityController<ReBrowserSettingsActivity> c=activity(section)) { c.recreate();c.pause(); }
            assertEquals("white",new ReBrowserPreferences(context).newTabBackground());
            assertFalse(new ReBrowserPreferences(context).newTabRecentEnabled());
        }
    }
    @Test public void existingBehaviorSwitchesUseTheirOriginalStores() {
        try(ActivityController<ReBrowserSettingsActivity> c=activity("isolation")) {
            toggle(page(c.get(),"isolation"),"JavaScript").setChecked(false);
            assertFalse(preferences.javascriptEnabled());
            toggle(page(c.get(),"isolation"),"允许第三方 Cookie").setChecked(false);
            assertFalse(preferences.thirdPartyCookiesEnabled());
            toggle(page(c.get(),"isolation"),"允许强行提升为主标签页").setChecked(true);
            assertTrue(preferences.forcePrimaryPromotionEnabled());
        }
    }
    @Test public void embeddedPermissionControlsKeepRevocationAndPermanentDenials() {
        long now=System.currentTimeMillis();
        assertTrue(permissions.grant(PROFILE,"https://example.com",ReBrowserSitePermissions.CLIPBOARD,ReBrowserSitePermissions.FIFTEEN_DAYS_MS,now));
        try(ActivityController<ReBrowserSettingsActivity> c=activity("isolation")) {
            View p=page(c.get(),"isolation");
            assertFalse(toggle(p,"摄像头").isChecked());
            toggle(p,"剪贴板").setChecked(false);
            assertFalse(permissions.capabilityEnabled(ReBrowserSitePermissions.CLIPBOARD));
            assertTrue(permissions.grants(now+1).isEmpty());
            assertNotNull(find(p,"目前没有有效的跨请求授权。单次授权不会保存。"));
            toggle(p,"剪贴板").setChecked(true);
            assertTrue(permissions.grants(now+1).isEmpty());
            assertNotNull(find(p,"网站通知"));assertNotNull(find(p,"后台推送"));assertNotNull(find(p,"网页运动传感器"));
            assertFalse(permissions.notificationsEnabled());assertFalse(permissions.backgroundPushEnabled());assertFalse(permissions.webSensorsEnabled());
        }
    }
    @Test public void individualGrantRevocationRemainsExactAndBackgroundPolicyUsesTheOriginalStore() {
        long now=System.currentTimeMillis();
        for(String origin:new String[]{"https://one.example","https://two.example"})
            assertTrue(permissions.grant(PROFILE,origin,ReBrowserSitePermissions.CLIPBOARD,ReBrowserSitePermissions.FIFTEEN_DAYS_MS,now));
        try(ActivityController<ReBrowserSettingsActivity> c=activity("isolation")) {
            View p=page(c.get(),"isolation");
            View title=find(p,"剪贴板 · https://one.example");
            ViewGroup row=(ViewGroup) title.getParent().getParent(); row.getChildAt(1).performClick();
            assertFalse(permissions.hasGrant(PROFILE,"https://one.example",ReBrowserSitePermissions.CLIPBOARD,now+1));
            assertTrue(permissions.hasGrant(PROFILE,"https://two.example",ReBrowserSitePermissions.CLIPBOARD,now+1));
            toggle(p,"允许后台运行").setChecked(false);assertFalse(permissions.backgroundRuntimeEnabled());
        }
    }
    @Test public void enablingOwnerCapabilityDoesNotCreateSiteOrAndroidGrants() {
        try(ActivityController<ReBrowserSettingsActivity> c=activity("isolation")) {
            toggle(page(c.get(),"isolation"),"摄像头").setChecked(true);
            assertTrue(permissions.capabilityEnabled(ReBrowserSitePermissions.CAMERA));
            assertTrue(permissions.grants(System.currentTimeMillis()).isEmpty());
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
                    context.checkSelfPermission(android.Manifest.permission.CAMERA));
        }
    }
    @Test public void settingsSnapshotOmitsHomeUrlAndSiteGrantIdentities() throws Exception {
        preferences.setHomeUrl("https://user:password@example.com/private?token=private-value");
        permissions.grant(PROFILE,"https://private-origin.example",ReBrowserSitePermissions.CLIPBOARD,ReBrowserSitePermissions.FIFTEEN_DAYS_MS,System.currentTimeMillis());
        String serialized=ReBrowserSettings.snapshot(context).toString();
        assertFalse(serialized.contains("password"));assertFalse(serialized.contains("private-value"));
        assertFalse(serialized.contains("private-origin"));assertFalse(serialized.contains(PROFILE));
        assertTrue(ReBrowserSettings.snapshot(context).getJSONObject("general").getBoolean("homeCustomized"));
        assertFalse(ReBrowserSettings.snapshot(context).getJSONObject("isolation").getBoolean("fullIncognitoImplemented"));
    }
    @Test public void legacyPermissionActivityRoutesToTheIsolationPeerPage() {
        try(ActivityController<ReBrowserSitePermissionsActivity> c=Robolectric.buildActivity(ReBrowserSitePermissionsActivity.class).create()) {
            Intent next=org.robolectric.Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertEquals(ReBrowserSettingsActivity.class.getName(),next.getComponent().getClassName());
            assertEquals("isolation",next.getStringExtra(ReBrowserSettings.EXTRA_SECTION));
            assertTrue(c.get().isFinishing());
        }
    }
}
