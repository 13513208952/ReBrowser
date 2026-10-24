package io.github.rebrowser.permissions;

import android.app.Activity;
import android.os.Looper;
import android.os.SystemClock;
import io.github.rebrowser.bootstrap.ComponentGate;
import org.mozilla.geckoview.GeckoSession;

/** Actual native selection/menu one-use route ONLY. Never reads Android's clipboard directly,
 * never accepts JS gesture flags/screen coordinates/admin proof as a user gesture and never
 * persists a clipboard permission. SDK execution is a request, NOT clipboard-write/read success.
 * Privileged/private-data export needs its own Actor path; this class exposes none. */
public final class ClipboardCoordinator {
    public static final class Offer {
        private final ClipboardCoordinator issuer;private final GeckoSession.SelectionActionDelegate.Selection selection;private final long issued;private boolean spent,retired;
        private Offer(ClipboardCoordinator issuer,GeckoSession.SelectionActionDelegate.Selection selection,long issued){this.issuer=issuer;this.selection=selection;this.issued=issued;}
    }
    private final Activity activity;private final GeckoSession session;private final ComponentGate.DataEpochPin source;private final Runnable nativeCurrentDocument;private Offer current;private long last=-1;
    public ClipboardCoordinator(Activity nativeActivity,GeckoSession exactSession,ComponentGate.DataEpochPin nativeSource,Runnable nativeDocumentFence){if(nativeActivity==null || exactSession==null || nativeSource==null || nativeDocumentFence==null)throw denied();activity=nativeActivity;session=exactSession;source=nativeSource;nativeCurrentDocument=nativeDocumentFence;main();}
    /** Called from the exact native SDK onShowActionRequest, not decoded page metadata. */
    public Offer offerNativeSelection(GeckoSession actualSession,GeckoSession.SelectionActionDelegate.Selection actualSelection){main();check();if(actualSession!=session || actualSelection==null || actualSelection.availableActions==null || actualSelection.availableActions.size()>32)throw denied();retire();current=new Offer(this,actualSelection,now());return current;}
    public boolean offered(Offer offer,String action){main();own(offer);return !offer.spent && !offer.retired && now()-offer.issued<30_000 && allowed(action,offer.selection) && offer.selection.isActionAvailable(action);}
    /** Invoked only by the actual owner ActionMode.onActionItemClicked callback. */
    public void executeFromNativeMenu(Offer offer,String action){main();check();own(offer);if(!offered(offer,action) || !activity.hasWindowFocus() || activity.isFinishing() || activity.isDestroyed() || now()-offer.issued>=30_000)throw denied();offer.spent=true;check();offer.selection.execute(action);} // Spend before SDK/JNI, no retry on unknown.
    public void retire(){main();if(current!=null)current.retired=true;current=null;}
    private void own(Offer offer){if(offer==null || offer.issuer!=this || offer!=current)throw denied();}
    private void check(){source.requireCurrent();nativeCurrentDocument.run();source.requireCurrent();now();}
    private long now(){long n=SystemClock.elapsedRealtime();if(n<0 || n<last){retire();throw denied();}last=n;return n;}
    private static boolean allowed(String action,GeckoSession.SelectionActionDelegate.Selection s){if(GeckoSession.SelectionActionDelegate.ACTION_COPY.equals(action) || GeckoSession.SelectionActionDelegate.ACTION_CUT.equals(action))return (s.flags&GeckoSession.SelectionActionDelegate.FLAG_IS_PASSWORD)==0;return GeckoSession.SelectionActionDelegate.ACTION_PASTE.equals(action) || GeckoSession.SelectionActionDelegate.ACTION_PASTE_AS_PLAIN_TEXT.equals(action);}
    private static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native clipboard menu thread required");}
    private static SecurityException denied(){return new SecurityException("Native clipboard selection unavailable or spent");}
}
