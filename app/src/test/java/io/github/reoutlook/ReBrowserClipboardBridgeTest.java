package io.github.reoutlook;

import static org.junit.Assert.*;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewFeature;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowSystemClock;

/** Calls the actual native bridge handler directly, bypassing all navigator wrappers. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, shadows=ReBrowserClipboardBridgeTest.Features.class, instrumentedPackages="androidx.webkit")
public final class ReBrowserClipboardBridgeTest {
    private static final String ORIGIN="https://clipboard.example";
    private static final String PROFILE="rebrowser_workspace_0123456789abcdef0123456789abcdef";
    @Implements(value=WebViewFeature.class,isInAndroidSdk=false)
    public static final class Features {
        @Implementation public static boolean isFeatureSupported(String feature) {return false;}
    }
    public static final class Host extends Activity {
        int clipboardLookups;
        boolean focused=true;
        @Override public boolean hasWindowFocus() {return focused;}
        @Override public Object getSystemService(String name) {
            if(Context.CLIPBOARD_SERVICE.equals(name)) clipboardLookups++;
            return super.getSystemService(name);
        }
    }
    private static final class PageView extends WebView {
        boolean focused=true;
        PageView(Context context) {super(context);}
        @Override public String getUrl() {return ORIGIN+"/page";}
        @Override public boolean hasFocus() {return focused;}
    }
    private static final class Reply extends JavaScriptReplyProxy {
        JSONObject value;
        @Override public void postMessage(String message) {try {value=new JSONObject(message);} catch(Exception e) {throw new AssertionError(e);}}
        @Override public void postMessage(byte[] message) {throw new AssertionError("unexpected binary reply");}
    }
    private ActivityController<Host> host;
    private Host activity;
    private FrameLayout container;
    private PageView view;
    private Object page;
    private Class<?> pageType;
    private ReBrowserWebController controller;
    private ReBrowserSitePermissions permissions;
    private ClipboardManager clipboard;
    private final List<ReBrowserPermissionRequest> prompts=new ArrayList<>();

    @Before public void setup() throws Exception {
        host=Robolectric.buildActivity(Host.class).setup();activity=host.get();
        activity.getSharedPreferences("rebrowser_site_permissions_v1",Context.MODE_PRIVATE).edit().clear().commit();
        permissions=new ReBrowserSitePermissions(activity);
        ReBrowserPreferences prefs=new ReBrowserPreferences(activity);
        ReBrowserWebController.Listener listener=(ReBrowserWebController.Listener)Proxy.newProxyInstance(
                getClass().getClassLoader(),new Class<?>[]{ReBrowserWebController.Listener.class},(proxy,method,args)->{
                    if(method.getName().equals("onSitePermissionRequested"))prompts.add((ReBrowserPermissionRequest)args[0]);
                    return null;
                });
        controller=new ReBrowserWebController(activity,prefs,permissions,new ReBrowserFullscreenController(activity,prefs),listener,1);
        container=new FrameLayout(activity);activity.setContentView(container);controller.attachContainer(container);
        view=new PageView(activity);container.addView(view);container.layout(0,0,500,800);
        // The shadow provider does not implement WebView's delegated setFrame.
        org.robolectric.util.ReflectionHelpers.setField(view,"mRight",500);
        org.robolectric.util.ReflectionHelpers.setField(view,"mBottom",800);
        pageType=Class.forName("io.github.reoutlook.ReBrowserWebController$PageBinding");
        Constructor<?> ctor=pageType.getDeclaredConstructor(String.class,String.class,ReBrowserStore.Tab.class,WebView.class,long.class);
        ctor.setAccessible(true);page=ctor.newInstance("workspace",PROFILE,new ReBrowserStore.Tab("tab","test",ORIGIN+"/page"),view,1L);
        ((Map<Object,Object>)field(controller,"pagesByTabId")).put("tab",page);
        ((Map<Object,Object>)field(controller,"pagesByView")).put(view,page);
        Method install=ReBrowserWebController.class.getDeclaredMethod("installClipboardInputPolicy",pageType);
        install.setAccessible(true);install.invoke(controller,page);
        clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("test","local-sentinel"));activity.clipboardLookups=0;
        assertTrue(view.isShown());
    }
    @After public void teardown() {if(host!=null)host.close();}
    private static Object field(Object object,String name) throws Exception {
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private void grant() {assertTrue(permissions.grant(PROFILE,ORIGIN,ReBrowserSitePermissions.CLIPBOARD,ReBrowserSitePermissions.FIFTEEN_DAYS_MS,System.currentTimeMillis()));}
    private void tap() throws Exception {
        Method eligible=ReBrowserWebController.class.getDeclaredMethod("clipboardPageEligible",pageType);
        eligible.setAccessible(true);assertTrue("page eligibility",(Boolean)eligible.invoke(controller,page));
        assertTrue("positive input bounds "+view.getWidth()+"x"+view.getHeight(),view.getWidth()>20 && view.getHeight()>20);
        long now=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
            MotionEvent e=ReBrowserClipboardGestureTest.event(now,action,20,20,InputDevice.SOURCE_TOUCHSCREEN,0,1,0);
            view.dispatchTouchEvent(e);e.recycle();
        }
        Object gate=field(page,"clipboardGesture");
        assertTrue("native observer must arm a tap; context="+field(gate,"contextGeneration"),
                (Long)field(gate,"armedAt")>=0);
    }
    private Reply send(String operation) throws Exception {return send(operation,ORIGIN,true);}
    private Reply send(String operation,String origin,boolean mainFrame) throws Exception {
        Reply reply=new Reply();JSONObject request=new JSONObject().put("id","42").put("operation",operation)
                .put("text","replacement").put("userGesture",true).put("userActivation",true);
        Method m=ReBrowserWebController.class.getDeclaredMethod("handlePermissionMessage",pageType,WebView.class,
                WebMessageCompat.class,Uri.class,boolean.class,JavaScriptReplyProxy.class);
        m.setAccessible(true);m.invoke(controller,page,view,new WebMessageCompat(request.toString()),Uri.parse(origin),mainFrame,reply);
        return reply;
    }
    private void denied(Reply reply) throws Exception {
        assertNotNull(reply.value);assertFalse(reply.value.getBoolean("ok"));assertEquals(0,activity.clipboardLookups);
        assertFalse(reply.value.has("value"));
    }
    private WebViewClient nativeClient() throws Exception {
        Class<?> clientType=Class.forName("io.github.reoutlook.ReBrowserWebController$BrowserClient");
        Constructor<?> ctor=clientType.getDeclaredConstructor(ReBrowserWebController.class,pageType);ctor.setAccessible(true);
        return (WebViewClient)ctor.newInstance(controller,page);
    }
    private void navigate() throws Exception {nativeClient().onPageStarted(view,ORIGIN+"/next",null);}
    @Test public void fifteenDayGrantCannotReadThroughDirectBridgeWithoutNativeInput() throws Exception {
        grant();denied(send("clipboard-read"));assertTrue(prompts.isEmpty());
    }
    @Test public void fifteenDayGrantCannotWriteOrForgeGestureThroughDirectBridge() throws Exception {
        grant();denied(send("clipboard-write"));assertEquals("local-sentinel",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        assertTrue(prompts.isEmpty());
    }
    @Test public void missingInputCannotEvenOpenANewPermissionPrompt() throws Exception {
        denied(send("clipboard-read"));assertTrue(prompts.isEmpty());
    }
    @Test public void nativeTapAllowsExactlyOneOperationNotAReadWriteBatch() throws Exception {
        grant();tap();Reply first=send("clipboard-read");assertTrue(first.value.getBoolean("ok"));
        assertEquals("local-sentinel",first.value.getString("value"));assertEquals(1,activity.clipboardLookups);
        activity.clipboardLookups=0;denied(send("clipboard-write"));
        assertEquals("local-sentinel",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    }
    @Test public void nativeTapAlsoAllowsOneWrite() throws Exception {
        grant();tap();assertTrue(send("clipboard-write").value.getBoolean("ok"));
        assertEquals("replacement",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        activity.clipboardLookups=0;denied(send("clipboard-read"));
    }
    @Test public void nativeBrowserClientKeyCallbackGrantsOneCreditOnlyToFocusedPage() throws Exception {
        grant();long now=SystemClock.uptimeMillis();WebViewClient client=nativeClient();
        assertFalse(client.shouldOverrideKeyEvent(view,new android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_ENTER,0)));
        assertTrue(send("clipboard-read").value.getBoolean("ok"));activity.clipboardLookups=0;
        client.shouldOverrideKeyEvent(view,new android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_ENTER,1));
        denied(send("clipboard-read"));view.focused=false;
        client.shouldOverrideKeyEvent(view,new android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_ENTER,0));
        denied(send("clipboard-read"));
    }
    @Test public void expiredInputCannotUseCachedGrant() throws Exception {
        grant();tap();ShadowSystemClock.advanceBy(Duration.ofMillis(ReBrowserClipboardGesture.WINDOW_MS+1));denied(send("clipboard-read"));
    }
    @Test public void navigationAndFocusLossRevokeCreditEvenWhenOriginAndGrantAreUnchanged() throws Exception {
        grant();tap();navigate();denied(send("clipboard-read"));
        tap();controller.onWindowFocusChanged(false);denied(send("clipboard-read"));
    }
    @Test public void backgroundAndTabSwitchCannotReplayCreditAfterReturning() throws Exception {
        grant();tap();controller.onPause();
        Field foreground=ReBrowserWebController.class.getDeclaredField("foreground");foreground.setAccessible(true);foreground.set(controller,true);
        denied(send("clipboard-read"));
        tap();controller.showNativePage(new View(activity));container.removeAllViews();container.addView(view);
        denied(send("clipboard-read"));
    }
    @Test public void frameOrOriginMismatchCannotUseARealTap() throws Exception {
        grant();tap();denied(send("clipboard-read",ORIGIN,false));
        denied(send("clipboard-read","https://other.example",true));assertTrue(prompts.isEmpty());
    }
    @Test public void onePendingOperationMayWaitForExplicitNativeGrantButCannotAuthorizeAnother() throws Exception {
        tap();Reply pending=send("clipboard-read");assertNull(pending.value);assertEquals(1,prompts.size());
        ShadowSystemClock.advanceBy(Duration.ofSeconds(8));assertTrue(prompts.get(0).approve(ReBrowserSitePermissions.FIFTEEN_DAYS_MS));
        assertTrue(pending.value.getBoolean("ok"));activity.clipboardLookups=0;denied(send("clipboard-read"));
    }
    @Test public void navigationCancelsPendingOperationAndStaleNativeApproval() throws Exception {
        tap();Reply pending=send("clipboard-read");navigate();assertFalse(prompts.get(0).approve(ReBrowserSitePermissions.FIFTEEN_DAYS_MS));denied(pending);
    }
    @Test public void focusLossCancelsPendingOperationAndStaleNativeApproval() throws Exception {
        tap();Reply pending=send("clipboard-read");controller.onWindowFocusChanged(false);
        assertFalse(prompts.get(0).approve(ReBrowserSitePermissions.FIFTEEN_DAYS_MS));denied(pending);
    }
}
