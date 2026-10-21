package io.github.rebrowser.engine;

import android.app.Activity;
import android.os.Looper;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.permissions.ClipboardCoordinator;
import org.mozilla.geckoview.GeckoSession;

/** Own native selection ActionMode, exact SDK Selection, no JS-controlled menu/eval or raw text
 * export. All hide/navigation/source retirement conservatively invalidates the previous offer.
 * Not automatically installed; actual window/document owner supplies the real current fence. */
public final class SelectionDelegate implements GeckoSession.SelectionActionDelegate {
    private final Activity activity;private final GeckoSession session;private final ClipboardCoordinator clipboard;private ActionMode active;private ClipboardCoordinator.Offer offer;private boolean retired;
    public SelectionDelegate(Activity nativeActivity,GeckoSession bound,ComponentGate.DataEpochPin nativeSource,Runnable nativeDocumentFence){if(nativeActivity==null || bound==null)throw new IllegalArgumentException("Native selection owner required");activity=nativeActivity;session=bound;clipboard=new ClipboardCoordinator(activity,session,nativeSource,nativeDocumentFence);}
    @Override public void onShowActionRequest(GeckoSession actual,GeckoSession.SelectionActionDelegate.Selection selection){
        main();clear();if(retired || actual!=session || activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus())return;
        final ClipboardCoordinator.Offer exact;try{exact=clipboard.offerNativeSelection(actual,selection);}catch(RuntimeException unavailable){return;}offer=exact;
        try{ActionMode created=activity.startActionMode(new ActionMode.Callback(){
            @Override public boolean onCreateActionMode(ActionMode mode,Menu menu){int count=0;if(clipboard.offered(exact,ACTION_COPY)){menu.add(0,1,0,"复制");count++;}if(clipboard.offered(exact,ACTION_CUT)){menu.add(0,2,1,"剪切");count++;}if(clipboard.offered(exact,ACTION_PASTE)){menu.add(0,3,2,"粘贴");count++;}return count>0;}
            @Override public boolean onPrepareActionMode(ActionMode mode,Menu menu){return false;}
            @Override public boolean onActionItemClicked(ActionMode mode,MenuItem item){if(retired || offer!=exact || item==null)return false;String action=item.getItemId()==1?ACTION_COPY:item.getItemId()==2?ACTION_CUT:item.getItemId()==3?ACTION_PASTE:null;if(action==null)return false;try{clipboard.executeFromNativeMenu(exact,action);}catch(RuntimeException unknown){/* no retry or clipboard-success inference */}finally{mode.finish();}return true;}
            @Override public void onDestroyActionMode(ActionMode mode){if(offer==exact){clipboard.retire();offer=null;active=null;}}
        });if(offer!=exact){if(created!=null)created.finish();}else if(created==null){clipboard.retire();offer=null;}else active=created;}
        catch(RuntimeException unknown){clipboard.retire();offer=null;}
    }
    @Override public void onHideAction(GeckoSession actual,int reason){main();if(actual==session)clear();}
    public void fenceDocument(){main();clear();}
    public void retire(){main();retired=true;clear();}
    private void clear(){clipboard.retire();offer=null;ActionMode old=active;active=null;if(old!=null)old.finish();}
    private static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native selection UI thread required");}
}
