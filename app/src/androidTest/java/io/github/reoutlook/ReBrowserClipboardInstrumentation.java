package io.github.reoutlook;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Real WebView/direct-bridge regression in a debug-only isolated fixture, never user pages. */
public final class ReBrowserClipboardInstrumentation extends Instrumentation {
    private ReBrowserClipboardProbeActivity probe;
    private int assertions;
    @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
    @Override public void onStart() {
        Bundle result=new Bundle();boolean passed=false;
        try {
            probe=(ReBrowserClipboardProbeActivity)startActivitySync(new Intent(getTargetContext(),ReBrowserClipboardProbeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitReady();
            directDenied("clipboard-read");directDenied("clipboard-write");
            eval("results=[];document.getElementById('read').click();'ok'");denied(results(1).getJSONObject(0));
            resetClip();click("read");JSONObject read=results(1).getJSONObject(0);
            check(read.getBoolean("ok")&&read.getBoolean("matches"),"native-tap-read");
            directDenied("clipboard-read");
            click("write");JSONObject write=results(1).getJSONObject(0);
            check(write.getBoolean("ok"),"native-tap-write:"+write.optString("message"));
            runOnMainSync(()->{
                ClipboardManager c=(ClipboardManager)probe.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip=c.getPrimaryClip();check(clip!=null&&probe.written.contentEquals(clip.getItemAt(0).getText()),"controlled-write");
            });
            directDenied("clipboard-write");
            resetClip();keyClick("batch");JSONArray batch=results(2);
            check(batch.getJSONObject(0).getBoolean("ok"),"batch-first");denied(batch.getJSONObject(1));
            keyClick("prime");Thread.sleep(ReBrowserClipboardGesture.WINDOW_MS+200);directDenied("clipboard-read");
            keyClick("prime");runOnMainSync(()->{probe.ready=false;probe.webView.reload();});waitReady();directDenied("clipboard-read");
            check(probe.failure.isEmpty(),"fixture-state");passed=true;
        } catch(Throwable error) {result.putString("error",error.getClass().getSimpleName()+":"+safeMessage(error));}
        finally {
            if(probe!=null) {
                runOnMainSync(()->{probe.cleanupProbe();probe.finish();});
                long until=SystemClock.elapsedRealtime()+5_000;
                while(!probe.cleaned&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(50);
                if(!probe.cleaned||!probe.failure.isEmpty()) {passed=false;result.putString("cleanup","failed");result.putString("cleanupError",probe.failure);}
                else result.putString("cleanup","clipboard-and-preferences-restored; profile-deferred");
            }
        }
        result.putString("status",passed?"passed":"failed");result.putInt("assertions",assertions);
        finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private static String safeMessage(Throwable e) {
        // Only application-owned assertion labels; never export arbitrary page/clipboard values.
        return e instanceof AssertionError?String.valueOf(e.getMessage()):"fixture-error";
    }
    private void waitReady() throws Exception {
        long until=SystemClock.elapsedRealtime()+10_000;
        while(SystemClock.elapsedRealtime()<until) {
            if(!probe.failure.isEmpty())throw new AssertionError(probe.failure);
            if(probe.ready&&probe.prepared()&&probe.hasWindowFocus())return;
            Thread.sleep(50);
        }
        throw new AssertionError("fixture-ready-timeout");
    }
    private String eval(String script) throws Exception {
        CountDownLatch done=new CountDownLatch(1);String[] value=new String[1];
        runOnMainSync(()->probe.webView.evaluateJavascript(script,r->{value[0]=r;done.countDown();}));
        if(!done.await(5,TimeUnit.SECONDS))throw new AssertionError("fixture-eval-timeout");
        Object decoded=new JSONTokener(value[0]).nextValue();return decoded instanceof String?(String)decoded:String.valueOf(decoded);
    }
    private JSONArray results(int count) throws Exception {
        long until=SystemClock.elapsedRealtime()+5_000;
        do {
            JSONArray values=new JSONArray(eval("JSON.stringify(results)"));if(values.length()>=count)return values;
            Thread.sleep(50);
        } while(SystemClock.elapsedRealtime()<until);
        throw new AssertionError("fixture-result-timeout");
    }
    private void directDenied(String operation) throws Exception {
        if(!operation.equals("clipboard-read")&&!operation.equals("clipboard-write"))throw new AssertionError("fixture-operation");
        eval("results=[];direct('"+operation+"');'ok'");denied(results(1).getJSONObject(0));
    }
    private void denied(JSONObject value) throws Exception {
        check(!value.getBoolean("ok")&&"clipboard-user-gesture-required".equals(value.getString("message")),"no-input-denied");
    }
    private void click(String id) throws Exception {
        if(!java.util.Set.of("read","write","batch","prime").contains(id))throw new AssertionError("fixture-button");
        // System clipboard overlays on this device mark unrelated page taps as partially obscured.
        // Wait for their dismissal; never weaken the production obscured-input policy.
        SystemClock.sleep(4_500);
        android.util.Log.i("RBClipProbe","button="+id);
        JSONObject rect=new JSONObject(eval("results=[];JSON.stringify((()=>{const r=document.getElementById('"+id+
                "').getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,viewport:innerWidth}})())"));
        int[] location=new int[2];int[] width=new int[1];runOnMainSync(()->{probe.webView.getLocationOnScreen(location);width[0]=probe.webView.getWidth();});
        float scale=(float)(width[0]/rect.getDouble("viewport"));
        float x=location[0]+(float)rect.getDouble("x")*scale,y=location[1]+(float)rect.getDouble("y")*scale;
        long down=SystemClock.uptimeMillis();
        MotionEvent start=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0);start.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        sendPointerSync(start);start.recycle();SystemClock.sleep(50);
        MotionEvent end=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);end.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        sendPointerSync(end);end.recycle();
    }
    private void keyClick(String id) throws Exception {
        if(!java.util.Set.of("batch","prime").contains(id))throw new AssertionError("fixture-key-button");
        runOnMainSync(()->probe.webView.requestFocus());
        eval("results=[];document.getElementById('"+id+"').focus();'ok'");
        sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER);
    }
    private void resetClip() {
        runOnMainSync(()->((ClipboardManager)probe.getSystemService(Context.CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("ReBrowser security probe",probe.source)));
        SystemClock.sleep(7_000);
    }
    private void check(boolean condition,String label) {assertions++;if(!condition)throw new AssertionError(label);}
}
