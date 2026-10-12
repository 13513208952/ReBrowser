package io.github.reoutlook;

import static org.junit.Assert.*;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public final class ReBrowserPlatformTest {
    private Context context;
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("rebrowser_preferences_v1",Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("rebrowser_workspace_store_v1",Context.MODE_PRIVATE).edit().clear().commit();
        File[] files=ReBrowserApkProvider.directory(context).listFiles();
        if(files!=null)for(File f:files)f.delete();
    }
    private View find(View view,String text) {
        if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            View found=find(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;
        }
        return null;
    }
    @Test public void webEntryHandlesHttpAndHttpsButCannotCarryCommandsOrFileUris() {
        for(String url:List.of("https://example.com/path?q=x#fragment","http://example.com/")) {
            Intent untrusted=new Intent(Intent.ACTION_VIEW,Uri.parse(url)).putExtra("adminRequest","forged")
                    .putExtra("_authentication","administrator-key").addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            Intent browser=ReBrowserExternalNavigation.browserIntent(context,untrusted);
            assertEquals(ReBrowserActivity.class.getName(),browser.getComponent().getClassName());
            assertEquals(ReBrowserExternalNavigation.ACTION_OPEN_LINK,browser.getAction());
            assertEquals(1,browser.getExtras().size());assertNull(browser.getData());assertNull(browser.getClipData());assertNull(browser.getSelector());
            assertEquals(0,browser.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            assertEquals(url,ReBrowserExternalNavigation.consume(browser));assertNull(ReBrowserExternalNavigation.consume(browser));
        }
        for(String url:List.of("javascript:alert(1)","file:///data/private.apk.1","content://private/item","https://user:password@example.com/","https://example.com/\n"))
            assertNull(ReBrowserExternalNavigation.browserIntent(context,new Intent(Intent.ACTION_VIEW,Uri.parse(url))));
        assertNull(ReBrowserExternalNavigation.browserIntent(context,new Intent(Intent.ACTION_SEND,Uri.parse("https://example.com/"))));
    }
    @Test public void publicWebTrampolineOnlyForwardsTheSanitizedIntent() {
        try(ActivityController<ReBrowserLinkActivity> c=Robolectric.buildActivity(ReBrowserLinkActivity.class,
                new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com/")).putExtra("adminRequest","forged")).create()) {
            Intent next=org.robolectric.Shadows.shadowOf(c.get()).getNextStartedActivity();
            assertNotNull(next);assertEquals(1,next.getExtras().size());assertTrue(c.get().isFinishing());
        }
    }
    private ResolveInfo settingsHandler(String pkg, int flags) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = pkg;
        info.activityInfo.name = pkg + ".DefaultAppListActivity";
        info.activityInfo.exported = true; info.activityInfo.enabled = true;
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.applicationInfo.flags = flags;
        info.isDefault = true;
        return info;
    }
    @Test public void defaultBrowserButtonRemainsManualEnabledAndLastInGeneralWhenAlreadyHeld() throws Exception {
        org.robolectric.Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(
                ReBrowserDefaultBrowser.settingsIntent(), settingsHandler("com.android.permissioncontroller", ApplicationInfo.FLAG_SYSTEM));
        RoleManager roles=context.getSystemService(RoleManager.class);
        org.robolectric.Shadows.shadowOf(roles).addAvailableRole(RoleManager.ROLE_BROWSER);
        for(boolean held:new boolean[]{false,true}) {
            if(held)org.robolectric.Shadows.shadowOf(roles).addHeldRole(RoleManager.ROLE_BROWSER);
            try(ActivityController<ReBrowserSettingsActivity> c=Robolectric.buildActivity(ReBrowserSettingsActivity.class).setup()) {
                assertNull(org.robolectric.Shadows.shadowOf(c.get()).getNextStartedActivity());
                View root=c.get().findViewById(android.R.id.content);
                Button button=(Button)find(root,"选择默认浏览器");assertNotNull(button);assertTrue(button.isEnabled());
                ViewGroup parent=(ViewGroup)button.getParent();assertSame(button,parent.getChildAt(parent.getChildCount()-1));
                assertNull(find(root,"隔离边界"));
                button.performClick();
                Intent opened=org.robolectric.Shadows.shadowOf(c.get()).getNextStartedActivity();
                assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,opened.getAction());
                assertEquals("com.android.permissioncontroller",opened.getComponent().getPackageName());
                assertNull(opened.getPackage());
                assertNull(opened.getExtras());assertEquals(held,roles.isRoleHeld(RoleManager.ROLE_BROWSER));
            }
        }
        assertFalse(ReBrowserDefaultBrowser.snapshot(context).getBoolean("administratorCanSet"));
    }
    @Test public void defaultSettingsIntentUsesTheSystemPreferredHandlerWithoutAnOemPackageLock() {
        Intent action = ReBrowserDefaultBrowser.settingsIntent();
        assertNull(action.getPackage()); assertNull(action.getComponent()); assertNull(action.getExtras());
        org.robolectric.Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(action,
                settingsHandler("com.android.permissioncontroller", ApplicationInfo.FLAG_SYSTEM));
        Intent resolved = ReBrowserDefaultBrowser.systemIntent(context, action);
        assertEquals("com.android.permissioncontroller", resolved.getComponent().getPackageName());
        assertNull(action.getComponent());
    }
    @Test public void defaultSettingsRejectsThirdPartyHandlersEvenWhenTheyAdvertiseTheSystemAction() {
        Intent action = ReBrowserDefaultBrowser.settingsIntent();
        org.robolectric.Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(action,
                settingsHandler("third.party.settings", 0));
        assertThrows(android.content.ActivityNotFoundException.class,
                () -> ReBrowserDefaultBrowser.systemIntent(context, action));
    }
    @Test @Config(sdk=29) public void olderSystemSettingsHandlerRemainsSupported() {
        Intent action = ReBrowserDefaultBrowser.settingsIntent();
        org.robolectric.Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(action,
                settingsHandler("com.android.settings", ApplicationInfo.FLAG_SYSTEM));
        assertEquals("com.android.settings", ReBrowserDefaultBrowser.systemIntent(context, action).getComponent().getPackageName());
    }
    @Test public void coldLinkInitializationDoesNotCreateAnExtraBlankWorkspaceOrReusePersistentProfile() {
        ReBrowserStore store=new ReBrowserStore(context);
        ReBrowserWorkspaceController first=new ReBrowserWorkspaceController(store);
        ReBrowserStore.Workspace fresh=first.initialize("https://example.com/");
        assertNotNull(fresh);assertEquals(1,first.activeWorkspaces().size());
        assertEquals("https://example.com/",fresh.activeTab().url);
        assertTrue(first.lockAsSecondary(fresh));first.saveAll();
        ReBrowserWorkspaceController restored=new ReBrowserWorkspaceController(store);
        assertNull(restored.initialize("https://external.example/"));
        ReBrowserStore.Workspace incoming=restored.createTemporary("https://external.example/");
        assertNotEquals(fresh.profileName,incoming.profileName);assertEquals(ReBrowserStore.Level.TEMPORARY,incoming.level);
        assertEquals("https://example.com/",restored.activeWorkspaces().get(0).activeTab().url);
    }
    @Test public void renamedFileSourceCannotReadPrivateFilesRemoteUrlsOrOwnProviders() {
        for(String uri:List.of("file:///data/user/0/io.github.reoutlook/files/mail.db.1","https://example.com/app.apk.1",
                "content://io.github.reoutlook.maintenance/file.apk.1","content://0@io.github.reoutlook.apk-handoff/file.apk.1"))
            assertThrows(IOException.class,()->ReBrowserRenamedApk.source(context,new Intent(Intent.ACTION_VIEW,Uri.parse(uri))));
        assertThrows(IOException.class,()->ReBrowserRenamedApk.source(context,new Intent(Intent.ACTION_SEND)));
    }
    @Test public void stagingCopiesBytesWithoutModifyingSourceAndRejectsEmptyOrOversizedFiles() throws Exception {
        byte[] original="APK byte-preserving transfer".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        File copied=ReBrowserRenamedApk.copy(context,new ByteArrayInputStream(original));
        assertArrayEquals(original,Files.readAllBytes(copied.toPath()));
        int before=ReBrowserApkProvider.directory(context).listFiles().length;
        assertThrows(IOException.class,()->ReBrowserRenamedApk.copy(context,new ByteArrayInputStream(new byte[0])));
        assertThrows(IOException.class,()->ReBrowserRenamedApk.copy(context,new ByteArrayInputStream(original),4));
        assertEquals(before,ReBrowserApkProvider.directory(context).listFiles().length);
        assertFalse(ReBrowserRenamedApk.isApk(context,copied));
    }
    @Test public void handoffProviderOnlyReadsOneFreshRandomApkAndRejectsWriteOrTraversal() throws Exception {
        File file=ReBrowserRenamedApk.copy(context,new ByteArrayInputStream(new byte[]{1,2,3}));
        Uri uri=ReBrowserApkProvider.uri(file);assertEquals(file,ReBrowserApkProvider.file(context,uri));
        for(String invalid:List.of("content://io.github.reoutlook.apk-handoff/../../mail.db","content://io.github.reoutlook.apk-handoff/not-random.apk",
                uri+"?path=mail.db",uri+"#x"))
            assertThrows(java.io.FileNotFoundException.class,()->ReBrowserApkProvider.file(context,Uri.parse(invalid)));
        ReBrowserApkProvider provider=Robolectric.buildContentProvider(ReBrowserApkProvider.class).create().get();
        assertThrows(java.io.FileNotFoundException.class,()->provider.openFile(uri,"w"));
        try(android.database.Cursor cursor=provider.query(uri,null,null,null,null)) {
            assertTrue(cursor.moveToFirst());assertEquals("package.apk",cursor.getString(0));assertEquals(3,cursor.getLong(1));
        }
        file.setLastModified(System.currentTimeMillis()-ReBrowserApkProvider.RETENTION_MS-1);
        assertThrows(java.io.FileNotFoundException.class,()->ReBrowserApkProvider.file(context,uri));
        ReBrowserApkProvider.cleanup(context);assertFalse(file.exists());
    }
    @Test public void platformRegistrationMatchesWebAndRenamedFilesButNotOrdinaryFiles() throws Exception {
        for(String url:List.of("http://example.com/","https://example.com/")) {
            List<ResolveInfo> handlers=context.getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE),0);
            assertTrue(handlers.stream().anyMatch(r->r.activityInfo.name.equals(ReBrowserLinkActivity.class.getName())));
        }
        for(String path:List.of("app.apk.1","release.v0.4.4.apk.1","document.1")) {
            List<ResolveInfo> handlers=context.getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://sender.example/files/"+path),"application/octet-stream"),0);
            assertTrue(path,handlers.stream().anyMatch(r->r.activityInfo.name.equals(ReBrowserRenamedApkActivity.class.getName())));
        }
        List<ResolveInfo> ordinary=context.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://sender.example/files/photo.jpg"),"image/jpeg"),0);
        assertFalse(ordinary.stream().anyMatch(r->r.activityInfo.name.equals(ReBrowserRenamedApkActivity.class.getName())));
        assertFalse(context.getPackageManager().getActivityInfo(new android.content.ComponentName(context,ReBrowserActivity.class),0).exported);
        assertFalse(context.getPackageManager().getProviderInfo(new android.content.ComponentName(context,ReBrowserApkProvider.class),0).exported);
    }
    @Test @Config(sdk=29) public void olderAndroidAlsoRegistersCommonDotOnePathsWithoutTheSuffixApi() throws Exception {
        platformRegistrationMatchesWebAndRenamedFilesButNotOrdinaryFiles();
    }
    @Test public void installerHandoffHasOnlyApkTypeReadGrantAndExplicitSystemTarget() throws Exception {
        File file=ReBrowserRenamedApk.copy(context,new ByteArrayInputStream(new byte[]{1,2,3}));
        Intent expected=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(ReBrowserApkProvider.uri(file),ReBrowserApkProvider.APK_TYPE);
        ResolveInfo system=new ResolveInfo();system.activityInfo=new ActivityInfo();system.activityInfo.name="system.Installer";
        system.activityInfo.packageName="com.android.packageinstaller";system.activityInfo.enabled=true;system.activityInfo.exported=true;
        system.activityInfo.applicationInfo=new ApplicationInfo();system.activityInfo.applicationInfo.flags=ApplicationInfo.FLAG_SYSTEM;
        org.robolectric.Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(expected,system);
        Intent handoff=ReBrowserRenamedApk.installerIntent(context,file);
        assertEquals(ReBrowserApkProvider.APK_TYPE,handoff.getType());assertEquals("com.android.packageinstaller",handoff.getComponent().getPackageName());
        assertEquals(ReBrowserApkProvider.uri(file),handoff.getClipData().getItemAt(0).getUri());
        assertEquals(0,handoff.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION);assertNull(handoff.getExtras());
    }
    @Test public void onlyExportedEnabledSystemInstallerCandidatesAreAccepted() {
        ResolveInfo info=new ResolveInfo();info.activityInfo=new ActivityInfo();info.activityInfo.applicationInfo=new ApplicationInfo();
        info.activityInfo.enabled=true;info.activityInfo.exported=true;info.activityInfo.packageName="com.android.packageinstaller";
        assertFalse(ReBrowserRenamedApk.systemInstaller(info));
        info.activityInfo.applicationInfo.flags=ApplicationInfo.FLAG_SYSTEM;assertTrue(ReBrowserRenamedApk.systemInstaller(info));
        info.activityInfo.packageName="system.appstore";assertFalse(ReBrowserRenamedApk.systemInstaller(info));
        info.activityInfo.packageName="com.android.packageinstaller";
        info.activityInfo.exported=false;assertFalse(ReBrowserRenamedApk.systemInstaller(info));
        info.activityInfo.exported=true;info.activityInfo.enabled=false;assertFalse(ReBrowserRenamedApk.systemInstaller(info));
    }
}
