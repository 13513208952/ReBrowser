package io.github.rebrowser.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.permissions.PendingPromptRegistry;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/** Bounded native JS alert/confirm/text UI, labelled as WEBSITE content not OS consent.
 * Every exact SDK prompt spends before result callback and is fenced on native document changes.
 * File/folder capture is denied: pinned FilePrompt.confirm(contentUri) may resolve/copy/traverse
 * outside our bounded upload-stage discipline, so picker selection is not blindly forwarded. */
public final class PromptDelegate implements GeckoSession.PromptDelegate {
    private interface Positive {PromptResponse response();}
    private final Activity activity;private final GeckoSession session;private final ComponentGate.DataEpochPin source;private final Runnable document;private final PendingPromptRegistry pending;private final Object documentToken=new Object();private long generation;private boolean retired;private Active active;
    private static final class Active {final BasePrompt prompt;final GeckoResult<PromptResponse> result;PendingPromptRegistry.Ticket ticket;AlertDialog dialog;EditText editor;Active(BasePrompt prompt,GeckoResult<PromptResponse> result){this.prompt=prompt;this.result=result;}}
    public PromptDelegate(Activity nativeActivity,GeckoSession boundSession,ComponentGate.DataEpochPin nativeSource,Runnable nativeCurrentDocument){if(nativeActivity==null || boundSession==null || nativeSource==null || nativeCurrentDocument==null)throw new IllegalArgumentException("Native prompt owner required");activity=nativeActivity;session=boundSession;source=nativeSource;document=nativeCurrentDocument;pending=new PendingPromptRegistry(source,128);}
    @Override public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession actual,AlertPrompt prompt){return display(actual,prompt,prompt.message,null,null);}
    @Override public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession actual,ButtonPrompt prompt){return display(actual,prompt,prompt.message,null,()->prompt.confirm(ButtonPrompt.Type.POSITIVE));}
    @Override public GeckoResult<PromptResponse> onTextPrompt(GeckoSession actual,TextPrompt prompt){return display(actual,prompt,prompt.message,prompt.defaultValue,()->{Active a=active;if(a==null || a.editor==null)throw new IllegalStateException("Native prompt editor retired");return prompt.confirm(bounded(a.editor.getText().toString(),4096));});}
    private GeckoResult<PromptResponse> display(GeckoSession actual,BasePrompt prompt,String message,String initial,Positive positive){
        main();if(prompt==null)throw new IllegalArgumentException("Native SDK prompt required");if(actual!=session || active!=null || !eligible())return dismiss(prompt);
        final Active a=new Active(prompt,new GeckoResult<PromptResponse>());active=a;
        try{String content=bounded(message==null?"":message,8192);final boolean text=prompt instanceof TextPrompt;
            a.ticket=pending.register(prompt,session,documentToken,generation,allow->{
                try{PromptResponse response;if(allow && eligible() && !prompt.isComplete())response=positive==null?prompt.dismiss():positive.response();else if(!prompt.isComplete())response=prompt.dismiss();else throw new IllegalStateException("Native SDK prompt already ended");a.result.complete(response);}catch(RuntimeException unknown){try{a.result.completeExceptionally(new IllegalStateException("Native prompt response unavailable"));}catch(RuntimeException alreadySettled){/* no retry */}}
                finally{clearView(a);}
            });
            prompt.setDelegate(new PromptInstanceDelegate(){@Override public void onPromptDismiss(BasePrompt exact){if(exact==prompt && active==a){try{answer(a,false);}catch(RuntimeException unknown){clearView(a);}}}});
            AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle("网页提示（非系统授权）").setMessage(content).setPositiveButton("确定",(d,w)->answer(a,true)).setOnCancelListener(d->answer(a,false));
            if(positive!=null)builder.setNegativeButton("取消",(d,w)->answer(a,false));
            if(text){a.editor=new EditText(activity);a.editor.setSaveEnabled(false);a.editor.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);a.editor.setSingleLine(true);a.editor.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4096)});a.editor.setText(bounded(initial==null?"":initial,4096));builder.setView(a.editor);}
            a.dialog=builder.create();a.dialog.show();
        }catch(RuntimeException unavailable){if(a.ticket!=null && !pending.attempted(a.ticket)){try{answer(a,false);}catch(RuntimeException unknown){clearView(a);}}else{clearView(a);if(!prompt.isComplete())try{a.result.complete(prompt.dismiss());}catch(RuntimeException unknown){}}}
        return a.result;
    }
    @Override public GeckoResult<PromptResponse> onFilePrompt(GeckoSession actual,FilePrompt prompt){return dismiss(prompt);}
    public void fenceDocument(){main();generation=next(generation);pending.retireDocument(session,documentToken);Active a=active;if(a!=null)try{answer(a,false);}catch(RuntimeException unknown){clearView(a);}}
    public void retire(){main();retired=true;fenceDocument();}
    private void answer(Active a,boolean positive){main();if(active!=a || a.ticket==null || pending.attempted(a.ticket))return;pending.answer(a.ticket,session,documentToken,generation,positive && eligible());}
    private void clearView(Active a){if(active==a)active=null;if(a.editor!=null){a.editor.setText("");a.editor=null;}AlertDialog dialog=a.dialog;a.dialog=null;if(dialog!=null)dialog.dismiss();}
    private boolean eligible(){try{source.requireCurrent();document.run();return !retired && !activity.isFinishing() && !activity.isDestroyed() && activity.hasWindowFocus();}catch(RuntimeException stale){return false;}}
    private static GeckoResult<PromptResponse> dismiss(BasePrompt prompt){if(prompt==null || prompt.isComplete())return GeckoResult.fromException(new IllegalStateException("Native prompt already completed"));try{return GeckoResult.fromValue(prompt.dismiss());}catch(RuntimeException unknown){return GeckoResult.fromException(new IllegalStateException("Native prompt result unknown"));}}
    private static String bounded(String s,int max){if(s==null || s.length()>max)throw new IllegalArgumentException("Native prompt text exceeds limit");for(int n=0;n<s.length();n++){char c=s.charAt(n);if(Character.isHighSurrogate(c)){if(++n>=s.length() || !Character.isLowSurrogate(s.charAt(n)))throw new IllegalArgumentException("Invalid prompt text");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Invalid prompt text");}return s;}
    private static long next(long n){if(n==Long.MAX_VALUE)throw new IllegalStateException("Native prompt generation exhausted");return n+1;}
    private static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native prompt UI thread required");}
}
