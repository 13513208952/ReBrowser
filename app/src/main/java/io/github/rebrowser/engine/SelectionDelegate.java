package io.github.rebrowser.engine;

import android.app.Activity;
import android.os.Looper;
import android.view.ActionMode;
import android.view.MenuItem;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.permissions.ClipboardCoordinator;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.BasicSelectionActionDelegate;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/** VIEW-owned source-fenced SDK floating selection menu. Never retained beyond its owned
 * display binding; actual SDK selection + actual native menu click, not a JS gesture DTO.
 * Copy/cut/paste use the canonical one-use native clipboard coordinator. No direct clipboard
 * read/export or persistent web clipboard grant. External text processing remains pending. */
public final class SelectionDelegate extends BasicSelectionActionDelegate {
    public static final int MAX_SELECTION_CHARS=32768;
    private final GeckoSession bound;private final ComponentGate.DataEpochPin source;private final Runnable nativeWindow;private final ClipboardCoordinator clipboard;
    private ClipboardCoordinator.Offer offer;private boolean retired,fenced,clearing;private ActionMode finishing;private Runnable actualDestroyed,expiry;
    public SelectionDelegate(Activity actualActivity,GeckoSession exactSession,ComponentGate.DataEpochPin actualSource,Runnable requireExactWindow){super(actualActivity);if(exactSession==null || actualSource==null || requireExactWindow==null)throw new IllegalArgumentException("Exact native selection owner required");bound=exactSession;source=actualSource;nativeWindow=requireExactWindow;clipboard=new ClipboardCoordinator(actualActivity,exactSession,actualSource,requireExactWindow);enableExternalActions(false);}
    @Override public void onShowActionRequest(GeckoSession actual,Selection selection){main();if(clearing)return;if(actual!=bound || !eligible() || !mActivity.hasWindowFocus() || selection==null || !validSelectionText(selection.text)){fenceDocument();return;}try{cancelExpiry();offer=clipboard.offerNativeSelection(actual,selection);ClipboardCoordinator.Offer original=offer;fenced=false;super.onShowActionRequest(actual,selection);if(offer==original && mActionMode!=null){expiry=()->{if(offer==original)fenceDocument();};if(!mActivity.getWindow().getDecorView().postDelayed(expiry,30_000))fenceDocument();}}catch(RuntimeException unknown){fenceDocument();}}
    @Override protected boolean isActionAvailable(String action){if(retired || fenced || mSelection==null)return false;if(clipboardAction(action)){try{return offer!=null && clipboard.offered(offer,action);}catch(RuntimeException stale){return false;}}return super.isActionAvailable(action);}
    @Override protected boolean performAction(String action,MenuItem item){main();if(!eligible() || fenced || mSession!=bound || mSelection==null || !validSelectionText(mSelection.text))return false;try{if(clipboardAction(action)){if(offer==null)return false;clipboard.executeFromNativeMenu(offer,action);if(ACTION_COPY.equals(action))clearSelection();return true;}if(ACTION_SELECT_ALL.equals(action))return super.performAction(action,item);return false;}catch(RuntimeException unknown){fenceDocument();return false;}}
    @Override public void onHideAction(GeckoSession actual,int reason){main();if(actual==bound)super.onHideAction(actual,reason);}
    @Override public void onDestroyActionMode(ActionMode mode){main();super.onDestroyActionMode(mode);cancelExpiry();clipboard.retire();offer=null;Runnable observer=null;if(finishing==mode){finishing=null;observer=actualDestroyed;actualDestroyed=null;}if(observer!=null)try{observer.run();}catch(RuntimeException stale){/* actual UI ended, no fallback */}}
    /** Native SDK document/start/stop/view loss is NEGATIVE only, not a new document lease. */
    public void fenceDocument(){main();if(clearing)return;clearing=true;try{cancelExpiry();fenced=true;clipboard.retire();offer=null;Selection previous=mSelection;mSelection=null;if(previous!=null)try{if(previous.isActionAvailable(ACTION_COLLAPSE_TO_END))previous.collapseToEnd();else if(previous.isActionAvailable(ACTION_UNSELECT))previous.unselect();else previous.hide();}catch(RuntimeException unknownNativeHide){/* negative cleanup is not a native-work end receipt */}ActionMode mode=mActionMode;if(mode!=null)mode.finish();}finally{clearing=false;}}
    public void retire(){main();retired=true;fenceDocument();}
    public boolean isRetired(){main();return retired;}
    public boolean hasPendingMode(){main();return mActionMode!=null;}
    public boolean requestOwnedCancel(Runnable destroyed){main();if(destroyed==null)throw new IllegalArgumentException("Actual selection end observer required");ActionMode mode=mActionMode;if(mode==null)return false;if(actualDestroyed!=null)throw new IllegalStateException("Selection end already observed");finishing=mode;actualDestroyed=destroyed;fenceDocument();return true;}
    /** Do NOT inherit BasicSelectionActionDelegate's positive web clipboard prompt route.
     * A user clicking the native paste menu is separate from a web permission request. */
    @Override public GeckoResult<AllowOrDeny> onShowClipboardPermissionRequest(GeckoSession actual,ClipboardPermission request){return GeckoResult.fromValue(AllowOrDeny.DENY);}
    private void cancelExpiry(){Runnable previous=expiry;expiry=null;if(previous!=null)mActivity.getWindow().getDecorView().removeCallbacks(previous);}
    private boolean eligible(){try{if(retired || !bound.isOpen() || mActivity.isFinishing() || mActivity.isDestroyed())return false;source.requireCurrent();nativeWindow.run();source.requireCurrent();return true;}catch(RuntimeException stale){return false;}}
    private static boolean clipboardAction(String action){return ACTION_COPY.equals(action) || ACTION_CUT.equals(action) || ACTION_PASTE.equals(action) || ACTION_PASTE_AS_PLAIN_TEXT.equals(action);}
    public static boolean validSelectionText(String text){if(text==null || text.length()>MAX_SELECTION_CHARS)return false;for(int n=0;n<text.length();n++){char c=text.charAt(n);if(Character.isHighSurrogate(c)){if(++n>=text.length() || !Character.isLowSurrogate(text.charAt(n)))return false;}else if(Character.isLowSurrogate(c))return false;}return true;}
    private static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native selection UI thread required");}
}
