package io.github.rebrowser.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.permissions.PendingPromptRegistry;
import java.lang.ref.WeakReference;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoSession;

/** Original native JS request UI, NOT OS consent, permission or document authority. Session
 * owners retain only a weak native-window adapter; owned dialogs live until actual dismissal.
 * Native navigation events are NEGATIVE fences, never fabricated committed-document leases.
 * File/capture/password/other prompt types remain denied until their real business integration. */
public final class PromptDelegate implements GeckoSession.PromptDelegate {
    /** In-process actual selected/visible native session/window only; never an Intent/provider. */
    public interface Window {Activity requireCurrentPromptWindow();default void presentationChanged(){}}
    private interface Positive {PromptResponse response(Active request);}
    /** UI/request axes are independent. SDK response attempt or dismiss() return alone is not
     * actual window dismissal; an unknown show stays owned instead of opening another dialog. */
    public static final class Lifetime {
        private final Thread owner=Thread.currentThread();private boolean show,response,dismissed;
        public void submittingShow(){check();if(show || response || dismissed)throw new IllegalStateException("Prompt show spent");show=true;}
        public void attemptingResponse(){check();if(response)throw new IllegalStateException("Prompt response spent");response=true;}
        public void actualDismissed(){check();dismissed=true;}
        public boolean ended(){check();return response && (!show || dismissed);}
        public boolean showAttempted(){check();return show;}public boolean responseAttempted(){check();return response;}
        public boolean mayPublish(boolean originalWindowGone,boolean nativeWindowFocused){check();return ended() && (originalWindowGone || nativeWindowFocused);}
        private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Prompt owner thread required");}
    }
    private final GeckoSession session;private final ComponentGate.DataEpochPin source;private final Runnable nativeSessionCurrent;private final PendingPromptRegistry pending;private final Object documentToken=new Object();private long generation;private boolean retired;private Active active;private WeakReference<Window> window=new WeakReference<>(null);
    private static final class Active {final BasePrompt prompt;final GeckoResult<PromptResponse> result=new GeckoResult<>();final Lifetime lifetime=new Lifetime();PendingPromptRegistry.Ticket ticket;AlertDialog dialog;EditText editor;Positive positive;String answerText;boolean closeRequested,allowRequested,published;Runnable dismissObserver;Active(BasePrompt prompt,Positive positive){this.prompt=prompt;this.positive=positive;}}
    public PromptDelegate(GeckoSession boundSession,ComponentGate.DataEpochPin actualSource,Runnable requireOriginalNativeSession){if(boundSession==null || actualSource==null || requireOriginalNativeSession==null)throw new IllegalArgumentException("Exact native prompt session required");session=boundSession;source=actualSource;nativeSessionCurrent=requireOriginalNativeSession;pending=new PendingPromptRegistry(source,128);}
    public void attachNativeWindow(Window exactWindow){main();if(exactWindow==null)throw new IllegalArgumentException("Native prompt window required");Window previous=window.get();if(previous!=null && previous!=exactWindow)throw new IllegalStateException("Original prompt window still attached");source.requireCurrent();nativeSessionCurrent.run();window=new WeakReference<>(exactWindow); /* visibility is checked freshly at request/show/answer, not while a native panel covers the page */}
    /** Negative cleanup stays available after source/window loss, never positive permission. */
    public void detachNativeWindow(Window original){main();Window current=window.get();if(current!=null && current!=original)throw new SecurityException("Foreign prompt window");window.clear();fenceDocument();}
    @Override public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession actual,AlertPrompt prompt){return display(actual,prompt,prompt==null?null:prompt.message,null,null);}
    @Override public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession actual,ButtonPrompt prompt){return display(actual,prompt,prompt==null?null:prompt.message,null,a->prompt.confirm(ButtonPrompt.Type.POSITIVE));}
    @Override public GeckoResult<PromptResponse> onTextPrompt(GeckoSession actual,TextPrompt prompt){return display(actual,prompt,prompt==null?null:prompt.message,prompt==null?null:prompt.defaultValue,a->prompt.confirm(bounded(a.answerText,4096)));}
    @Override public GeckoResult<PromptResponse> onBeforeUnloadPrompt(GeckoSession actual,BeforeUnloadPrompt prompt){return display(actual,prompt,"此网页请求确认离开。确定将继续导航；取消将留在当前网页。",null,a->prompt.confirm(AllowOrDeny.ALLOW));}
    @Override public GeckoResult<PromptResponse> onRepostConfirmPrompt(GeckoSession actual,RepostConfirmPrompt prompt){return display(actual,prompt,"继续将重新提交此前的表单数据，可能导致重复操作。是否继续？",null,a->prompt.confirm(AllowOrDeny.ALLOW));}
    // Pinned PromptCollection.sys.mjs maps a dismissed/null result to !!result?.allow=false
    // for both navigation confirmations. Cancel/background/source loss NEVER call confirm(null),
    // because the SDK treats a null AllowOrDeny argument as ALLOW, not a negative response.
    private GeckoResult<PromptResponse> display(GeckoSession actual,BasePrompt prompt,String message,String initial,Positive positive){
        main();if(prompt==null)throw new IllegalArgumentException("Native SDK prompt required");if(actual!=session || prompt.isComplete() || active!=null)return dismiss(prompt);Activity activity=currentWindow();if(activity==null || !activity.hasWindowFocus())return dismiss(prompt);
        Active a=new Active(prompt,positive);active=a;
        try{String content=bounded(message==null?"":message,8192);boolean text=prompt instanceof TextPrompt;
            a.ticket=pending.register(prompt,session,documentToken,generation,allow->respond(a,allow));
            prompt.setDelegate(new PromptInstanceDelegate(){@Override public void onPromptDismiss(BasePrompt original){if(original==prompt && active==a){a.allowRequested=false;answer(a,false);publishWhenReady(a);}}});
            if(active!=a || a.lifetime.responseAttempted())return a.result;
            AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle("网页提示（非系统授权）").setMessage(content).setPositiveButton("确定",(d,w)->answer(a,true)).setOnCancelListener(d->answer(a,false));
            if(positive!=null)builder.setNegativeButton("取消",(d,w)->answer(a,false));
            if(text){a.editor=new EditText(activity);a.editor.setContentDescription("网页提示输入");a.editor.setSaveEnabled(false);a.editor.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);a.editor.setSingleLine(true);a.editor.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4096)});a.editor.setText(bounded(initial==null?"":initial,4096));builder.setView(a.editor);}
            AlertDialog dialog=builder.create();a.dialog=dialog;dialog.setOnDismissListener(d->{a.lifetime.actualDismissed();clearEditor(a);a.dialog=null;if(!a.lifetime.responseAttempted())answer(a,false);publishWhenReady(a);notifyWindow();Runnable observer=a.dismissObserver;a.dismissObserver=null;if(observer!=null)try{observer.run();}catch(RuntimeException unknownObserver){/* actual dismiss already recorded; no retry */}});
            if(currentWindow()!=activity)throw new SecurityException("Original prompt window changed");a.lifetime.submittingShow();dialog.show();notifyWindow();
        }catch(RuntimeException unavailable){if(a.ticket!=null && !pending.attempted(a.ticket))answer(a,false);else if(!a.lifetime.responseAttempted()){a.lifetime.attemptingResponse();closeView(a);publishWhenReady(a);}}
        return a.result;
    }
    private void respond(Active a,boolean allow){a.lifetime.attemptingResponse();a.allowRequested=allow;if(allow && a.editor!=null)try{a.answerText=bounded(a.editor.getText().toString(),4096);}catch(RuntimeException invalid){a.allowRequested=false;}closeView(a);publishWhenReady(a);}
    /** Resume synchronous webpage JS only AFTER actual old dialog dismissal and the original
     * native window's focus return. Otherwise a second legitimate chained prompt arrives while
     * the previous modal still owns focus and is falsely rejected. No timeout/positive retry. */
    private void publishWhenReady(Active a){if(a.published)return;Activity original=currentWindow();if(!a.lifetime.mayPublish(original==null,original!=null && original.hasWindowFocus()))return;a.published=true;try{if(a.prompt.isComplete())throw new IllegalStateException("Native request already ended");PromptResponse response=a.allowRequested && original!=null && a.positive!=null?a.positive.response(a):a.prompt.dismiss();finish(a);a.result.complete(response);}catch(RuntimeException unknown){completeUnknown(a);}finally{a.answerText=null;a.positive=null;finish(a);}}
    public void onNativeWindowFocused(){main();Active a=active;if(a!=null)publishWhenReady(a);}
    private void answer(Active a,boolean allow){main();if(active!=a || a.ticket==null || pending.attempted(a.ticket))return;pending.answer(a.ticket,session,documentToken,generation,allow && currentWindow()!=null);}
    private void completeUnknown(Active a){try{a.result.completeExceptionally(new IllegalStateException("Native prompt response unavailable"));}catch(RuntimeException alreadyAttempted){/* no retry */}}
    private void closeView(Active a){clearEditor(a);if(!a.lifetime.showAttempted()){a.dialog=null;return;}if(a.dialog!=null && !a.closeRequested){a.closeRequested=true;try{a.dialog.dismiss();}catch(RuntimeException unknown){/* retain owned view until actual dismissal, no second positive request */}}}
    private void clearEditor(Active a){if(a.editor!=null){a.editor.setText("");a.editor=null;}}
    private void finish(Active a){if(active==a && a.lifetime.ended() && a.published)active=null;}
    /** Eligibility deliberately does NOT require Activity.hasWindowFocus while the OWN dialog
     * is showing: Android transfers focus to that dialog. Native foreground/selection guard
     * remains required at response; initial show additionally requires actual window focus. */
    private void notifyWindow(){Window current=window.get();if(current!=null)try{current.presentationChanged();}catch(RuntimeException retiredWindow){/* observation only; no retry */}}
    private Activity currentWindow(){try{if(retired)return null;source.requireCurrent();nativeSessionCurrent.run();Window current=window.get();if(current==null)return null;Activity activity=current.requireCurrentPromptWindow();return activity==null || activity.isFinishing() || activity.isDestroyed()?null:activity;}catch(RuntimeException stale){return null;}}
    public void fenceDocument(){main();generation=next(generation);pending.retireDocument(session,documentToken);Active a=active;if(a!=null){a.allowRequested=false;answer(a,false);publishWhenReady(a);}}
    public void retire(){main();retired=true;window.clear();fenceDocument();}
    public boolean hasPending(){main();return active!=null && !active.lifetime.ended();}
    public boolean requestOwnedCancel(Runnable actualDismissed){main();if(actualDismissed==null)throw new IllegalArgumentException("Actual dismiss observer required");Active a=active;if(a==null || a.lifetime.ended())return false;if(a.dismissObserver!=null)throw new IllegalStateException("Prompt dismissal already observed");a.dismissObserver=actualDismissed;answer(a,false);if(a.lifetime.ended() && !a.lifetime.showAttempted()){a.dismissObserver=null;actualDismissed.run();}return true;}
    @Override public GeckoResult<PromptResponse> onFilePrompt(GeckoSession actual,FilePrompt prompt){return dismiss(prompt);}
    private static GeckoResult<PromptResponse> dismiss(BasePrompt prompt){if(prompt==null || prompt.isComplete())return GeckoResult.fromException(new IllegalStateException("Native prompt already completed"));try{return GeckoResult.fromValue(prompt.dismiss());}catch(RuntimeException unknown){return GeckoResult.fromException(new IllegalStateException("Native prompt result unknown"));}}
    private static String bounded(String text,int max){if(text==null || text.length()>max)throw new IllegalArgumentException("Native prompt text exceeds limit");for(int n=0;n<text.length();n++){char c=text.charAt(n);if(Character.isHighSurrogate(c)){if(++n>=text.length() || !Character.isLowSurrogate(text.charAt(n)))throw new IllegalArgumentException("Invalid prompt text");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Invalid prompt text");}return text;}
    private static long next(long value){if(value==Long.MAX_VALUE)throw new IllegalStateException("Native prompt generation exhausted");return value+1;}
    private static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native prompt UI thread required");}
}
