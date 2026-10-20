package io.github.rebrowser.engine;

import io.github.rebrowser.bootstrap.ComponentGate;
import java.io.IOException;
import java.io.InputStream;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebResponse;

/** One native session's content observations; no Activity, downloads, popup or document authority. */
public final class ContentDelegate implements GeckoSession.ContentDelegate {
    private final GeckoSession bound;private final GeckoSessionOwner owner;private final GeckoSessionOwner.SessionRef ref;private final ComponentGate gate;private final PartitionCatalog partitions;private final SessionPresentation presentation;private boolean retired,unknownResponseClose;
    ContentDelegate(GeckoSession bound,GeckoSessionOwner owner,GeckoSessionOwner.SessionRef ref,ComponentGate gate,PartitionCatalog partitions,SessionPresentation presentation){if(bound==null || owner==null || ref==null || gate==null || partitions==null || presentation==null)throw new IllegalArgumentException("Exact native content binding required");this.bound=bound;this.owner=owner;this.ref=ref;this.gate=gate;this.partitions=partitions;this.presentation=presentation;}
    @Override public void onTitleChange(GeckoSession actual,String title){if(eligible(actual))presentation.title(title);}
    @Override public void onFullScreen(GeckoSession actual,boolean fullScreen){if(eligible(actual))presentation.fullScreen(fullScreen);}
    @Override public void onCloseRequest(GeckoSession actual){if(eligible(actual))presentation.closeRequested();} // request, NEVER window-end acknowledgement
    @Override public void onCrash(GeckoSession actual){engineClosed(actual);}
    @Override public void onKill(GeckoSession actual){engineClosed(actual);}
    private void engineClosed(GeckoSession actual){GeckoSessionOwner.mainThread();if(actual!=bound || bound.isOpen())return;retire();owner.observeEngineClosed(ref);} // works while source locked, no reopen
    @Override public void onExternalResponse(GeckoSession actual,WebResponse response){
        GeckoSessionOwner.mainThread();
        // Ownership transfers even on late/foreign/stale/locked callbacks. No drain, read,
        // re-fetch or publication. Pinned SDK body.close dispatches native close to Gecko;
        // no reader is started here. This is NOT bulk ResponseStreamWorker IO on the UI lane.
        // JNI close latency/whole native network stop are NOT qualified or inferred.
        if(response!=null){boolean returnedClosed=false;try{returnedClosed=closeRejectedBody(response.body);}finally{if(!returnedClosed){unknownResponseClose=true;owner.observeUnknownRejectedBodyClose(ref);}}}
    }
    static boolean closeRejectedBody(InputStream body){if(body==null)return true;try{body.close();return true;}catch(IOException|RuntimeException unknown){return false;}}
    boolean hasUnknownResponseClose(){return unknownResponseClose;} // sticky uncertainty; no false quiescence receipt
    void retire(){GeckoSessionOwner.mainThread();retired=true;presentation.retire();}
    private boolean eligible(GeckoSession actual){GeckoSessionOwner.mainThread();if(retired || actual!=bound || !bound.isOpen())return false;try{ComponentGate.BusinessPermit permit=gate.requireBusiness();owner.requireOpen(ref,permit);partitions.requireActive(ref.partition(),permit);return true;}catch(SecurityException|IllegalArgumentException|IllegalStateException unavailable){return false;}}
}
