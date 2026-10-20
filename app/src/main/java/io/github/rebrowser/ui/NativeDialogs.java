package io.github.rebrowser.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.EditText;
import android.text.InputFilter;
import java.lang.ref.WeakReference;

/** Native information/text editing ONLY. Never source-force/owner authentication or default consent. */
public final class NativeDialogs {
    public enum Decision { NONE, TEXT_ENTERED, CANCELLED, DISMISSED }
    public interface TextReceiver { void actualNativeText(String text); }
    public static final class Token {
        private final Lifecycle issuer;private boolean submitted,ended;private Decision decision=Decision.NONE;
        private Token(Lifecycle issuer){this.issuer=issuer;}
    }
    /** Real modal ownership reducer; unknown show/cancel is not actual dismissal. */
    public static final class Lifecycle {
        private final Thread thread=Thread.currentThread();private Token pending;private boolean retired;
        public Token begin(){check();if(retired || pending!=null)throw new IllegalStateException("Native modal still owned or retired");pending=new Token(this);return pending;}
        public void submitting(Token token){check();own(token);if(token.submitted)throw new IllegalStateException("Native modal already submitted");token.submitted=true;}
        public void decide(Token token,Decision decision){check();own(token);if(decision==null || decision==Decision.NONE)throw new IllegalArgumentException("Invalid native modal decision");if(token.decision==Decision.NONE)token.decision=decision;}
        public void submissionUnknown(Token token){check();if(token==null || token.issuer!=this)throw new SecurityException("Foreign native modal");/* retain, unless original actual dismiss already ended */}
        public boolean neverSubmitted(Token token){check();own(token);if(token.submitted)throw new IllegalStateException("Submitted native modal needs actual dismiss");token.ended=true;pending=null;return true;}
        public Decision actualDismissed(Token token){check();if(token==null || token.issuer!=this)throw new SecurityException("Foreign native modal");if(token.ended)return Decision.NONE;own(token);token.ended=true;pending=null;return retired?Decision.NONE:token.decision==Decision.NONE?Decision.DISMISSED:token.decision;}
        public boolean hasPending(){check();return pending!=null;}
        public void retireResults(){check();retired=true;}
        private void own(Token token){if(token==null || token.issuer!=this || token!=pending || token.ended)throw new SecurityException("Stale native modal");}
        private void check(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Native modal owner thread required");}
    }
    private final WeakReference<Activity> activity;private final Lifecycle lifecycle=new Lifecycle();private AlertDialog dialog;private Token pending;private Runnable actualDismissObserver;
    public NativeDialogs(Activity ownedWindow){BrowserToolbar.main();if(ownedWindow==null || ownedWindow.isDestroyed())throw new IllegalArgumentException("Missing native dialog window");activity=new WeakReference<>(ownedWindow);}
    /** Compiled minimal shell information; no data access, no positive action callback. */
    public void showInformation(String nativeMessage){show(null,null,nativeMessage,null);}
    public void editWorkspaceName(BrowserToolbar.Binding binding,String initial,TextReceiver receiver){if(binding==null || receiver==null)throw new IllegalArgumentException("Missing native text-edit owner");binding.check();show(binding,initial,"修改总标签名称",receiver);}
    private void show(BrowserToolbar.Binding binding,String initial,String message,TextReceiver receiver){
        BrowserToolbar.main();if(message==null || message.length()>1024 || (initial!=null && initial.length()>4096))throw new IllegalArgumentException("Native dialog text too large");
        Activity a=activity.get();if(a==null || a.isDestroyed() || a.isFinishing() || !a.hasWindowFocus())throw new IllegalStateException("Owned native dialog window unavailable");
        AlertDialog candidate=new AlertDialog.Builder(a).setTitle("ReBrowser").setMessage(message).create();
        EditText edit=receiver==null?null:new EditText(a);if(edit!=null){edit.setSingleLine(true);edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4096)});edit.setText(initial==null?"":initial);candidate.setView(edit);}
        final Token token=lifecycle.begin();final String[] text={null};pending=token;dialog=candidate;
        try{candidate.setButton(DialogInterface.BUTTON_POSITIVE,receiver==null?"知道了":"保存",(ignored,which)->{if(token!=pending)return;if(edit!=null){text[0]=edit.getText().toString();lifecycle.decide(token,Decision.TEXT_ENTERED);}else lifecycle.decide(token,Decision.DISMISSED);});
        if(receiver!=null)candidate.setButton(DialogInterface.BUTTON_NEGATIVE,"取消",(ignored,which)->{if(token==pending)lifecycle.decide(token,Decision.CANCELLED);});
        candidate.setOnCancelListener(ignored->{if(token==pending)lifecycle.decide(token,Decision.CANCELLED);});
        candidate.setOnDismissListener(ignored->{Decision result=lifecycle.actualDismissed(token);Runnable ended=null;if(token==pending){pending=null;dialog=null;ended=actualDismissObserver;actualDismissObserver=null;}if(ended!=null)try{ended.run();}catch(RuntimeException observerUnknown){/* actual dismissal already recorded; no fallback/retry */}String value=text[0];text[0]=null;if(edit!=null)edit.setText("");if(result==Decision.TEXT_ENTERED && binding!=null && receiver!=null){try{binding.check();receiver.actualNativeText(value);}catch(RuntimeException staleOrUnknown){/* no automatic retry, auth success or owner-chain completion */}}});
        if(binding!=null)binding.check();lifecycle.submitting(token);candidate.show();
        }catch(RuntimeException unknown){if(token.submitted)lifecycle.submissionUnknown(token);else if(!token.ended){if(pending==token){pending=null;dialog=null;}lifecycle.neverSubmitted(token);}throw unknown;}
    }
    /** Cancel request only; retain actual pending modal until original onDismiss. */
    public void cancelOwned(){BrowserToolbar.main();AlertDialog owned=dialog;if(owned!=null)try{owned.cancel();}catch(RuntimeException unknown){if(pending!=null)lifecycle.submissionUnknown(pending);}}
    /** Existing native Back owner waits for actual original dismissal, never cancel() return. */
    public boolean requestOwnedCancel(Runnable actualDismissed){BrowserToolbar.main();if(actualDismissed==null)throw new IllegalArgumentException("Missing native dismiss observer");if(pending==null)return false;if(actualDismissObserver!=null)throw new IllegalStateException("Native dismiss already observed");actualDismissObserver=actualDismissed;cancelOwned();return true;}
    public boolean hasPending(){return lifecycle.hasPending();}
    public void retireWindow(){BrowserToolbar.main();lifecycle.retireResults();cancelOwned();activity.clear();}
}
