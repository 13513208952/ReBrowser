package io.github.rebrowser.engine;

import io.github.rebrowser.bootstrap.ComponentGate;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

/**
 * Activity-owned one-view binding. Process session owner retains only an opaque token,
 * never this wrapper/View/Activity. Detach is not close, pause, checkpoint or settlement.
 * Browser domain explicitly hides/closes its sessions; music service may retain active
 * ownership while UI detaches. All visibility/foreground facts come from native owners.
 */
public final class SessionDisplayBinding {
    private final GeckoView view;
    private final GeckoSessionOwner owner;
    private final Object token=new Object();
    private GeckoSessionOwner.SessionRef bound;
    public SessionDisplayBinding(GeckoView view,GeckoSessionOwner owner){GeckoSessionOwner.mainThread();if(view==null || owner==null)throw new IllegalArgumentException("Native view/session owner required");this.view=view;this.owner=owner;}
    public void attach(GeckoSessionOwner.SessionRef ref,ComponentGate.BusinessPermit permit){
        GeckoSessionOwner.mainThread();owner.requireOpen(ref,permit);
        if(bound==ref){requireOwnDisplay();return;}
        if(bound!=null)detach();
        if(view.getSession()!=null)throw new IllegalStateException("View already belongs to another binding");
        GeckoSession session=owner.claimDisplay(ref,token,permit);bound=ref;
        try{view.setSession(session);}catch(RuntimeException failure){
            try{if(view.getSession()==session)detach();else{owner.releaseDisplay(ref,token);bound=null;}}
            catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    public void updateVisibility(boolean visible,boolean focused,boolean keepActiveWhileHidden,ComponentGate.BusinessPermit permit){
        GeckoSessionOwner.mainThread();if(focused && !visible)throw new IllegalArgumentException("Hidden display cannot be focused");requireOwnDisplay();
        GeckoSession session=owner.requireOpen(bound,permit);session.setFocused(visible&&focused);session.setActive(visible||keepActiveWhileHidden);
    }
    /** Native browser safe-stop path; no fresh site login/source-unlock required. */
    public void hideOwnedSession(){GeckoSessionOwner.mainThread();requireOwnDisplay();owner.hide(bound);}
    public void detach(){
        GeckoSessionOwner.mainThread();if(bound==null)return;GeckoSession expected=requireOwnDisplay();
        if(expected.isOpen())expected.setFocused(false);
        GeckoSession released=view.releaseSession();if(released!=expected)throw new IllegalStateException("Wrong released display session");
        owner.releaseDisplay(bound,token);bound=null;
        // Explicitly do NOT call shutdown(), close(), setActive(false), or assume async flush committed.
    }
    public boolean isAttached(){GeckoSessionOwner.mainThread();return bound!=null;}
    private GeckoSession requireOwnDisplay(){
        if(bound==null)throw new IllegalStateException("No owned display");GeckoSession session=owner.cleanupSession(bound);
        if(view.getSession()!=session)throw new IllegalStateException("Display replaced outside native owner");return session;
    }
}
