package io.github.rebrowser.engine;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.browser.BrowserModels.TabRef;
import java.util.IdentityHashMap;
import java.util.Map;
import org.mozilla.geckoview.GeckoSession;

/** Actual temporary-browser delegate graph. No Activity retention or caller-provided platform adapter. */
public final class BrowserSessionHost implements GeckoSessionOwner.Initializer {
    private final ComponentGate gate;private final PartitionCatalog partitions;private final int capacity;
    private PartitionCatalog.Domain domain=PartitionCatalog.Domain.BROWSER;private Object builtinToken;
    public interface BrowserHistory {void visited(TabRef tab,String url);void titleChanged(TabRef tab,String url,String title);default void sessionReported(TabRef tab,SessionPresentation.Frame frame){}}
    private BrowserHistory history;
    private GeckoSessionOwner owner;
    private final Map<GeckoSessionOwner.SessionRef,Bundle> bundles=new IdentityHashMap<>();
    private BrowserSessionHost(ComponentGate gate,PartitionCatalog partitions,int capacity){this.gate=gate;this.partitions=partitions;this.capacity=capacity;}
    public static GeckoSessionOwner createTemporaryOwner(ComponentGate gate,GeckoRuntimeOwner runtime,PartitionCatalog partitions,int capacity){return createTemporaryOwner(gate,runtime,partitions,capacity,null);}
    public static GeckoSessionOwner createTemporaryOwner(ComponentGate gate,GeckoRuntimeOwner runtime,PartitionCatalog partitions,int capacity,BrowserHistory nativeHistory){
        GeckoSessionOwner.mainThread();if(gate==null || runtime==null || partitions==null || capacity<1 || capacity>64)throw new IllegalArgumentException("Bounded native browser graph required");
        BrowserSessionHost host=new BrowserSessionHost(gate,partitions,capacity);GeckoSessionOwner sessions=new GeckoSessionOwner(gate,runtime,partitions,host,capacity);host.owner=sessions;host.history=nativeHistory;return sessions;
    }
    /** Fixed built-in domain graph, separate from browser/private workspaces. Cookie-context
     * persistence/finite-grant reconciliation remain unqualified; all permissions still denied. */
    public static GeckoSessionOwner createBuiltinOwner(ComponentGate gate,GeckoRuntimeOwner runtime,PartitionCatalog partitions,PartitionCatalog.Domain domain,Object nativeDomainOwner){
        GeckoSessionOwner.mainThread();if(gate==null || runtime==null || partitions==null || nativeDomainOwner==null || domain==null || domain==PartitionCatalog.Domain.BROWSER)throw new IllegalArgumentException("Native built-in session domain required");BrowserSessionHost host=new BrowserSessionHost(gate,partitions,1);host.domain=domain;host.builtinToken=nativeDomainOwner;GeckoSessionOwner sessions=new GeckoSessionOwner(gate,runtime,partitions,host,1);host.owner=sessions;return sessions;
    }
    @Override public void reconcileBeforeOpen(PartitionCatalog.Lease partition,ComponentGate.BusinessPermit permit){
        GeckoSessionOwner.mainThread();partitions.requireActive(partition,permit);
        if(partition.domain()!=domain || (domain==PartitionCatalog.Domain.BROWSER?partition.retention()!=PartitionCatalog.Retention.PRIVATE_SESSION:partition.retention()!=PartitionCatalog.Retention.PROCESS_LOCAL_CATALOG))throw new SecurityException("Wrong native domain/session retention");
        // No restore/grant import: brand-new process catalog contexts only. This is NOT
        // reconciliation/revocation of existing stored permissions or finite grant support.
    }
    @Override public void installDelegates(GeckoSession session,GeckoSessionOwner.SessionRef ref,PartitionCatalog.Lease partition,Object token){
        GeckoSessionOwner.mainThread();if(owner==null || (domain==PartitionCatalog.Domain.BROWSER?!(token instanceof TabRef):token!=builtinToken) || partition.domain()!=domain || ref.partition()!=partition || session.isOpen() || bundles.containsKey(ref) || bundles.size()>=capacity)throw new IllegalStateException("Exact unopened browser allocation required");
        partitions.requireOwner(partition,domain==PartitionCatalog.Domain.BROWSER?((TabRef)token).workspace():builtinToken,gate.requireBusiness());
        Bundle bundle=new Bundle(session,ref,partition,token);bundles.put(ref,bundle); // retain before ANY SDK setter
        session.setPermissionDelegate(new PermissionDelegate());
        session.setContentDelegate(bundle.content);session.setNavigationDelegate(bundle.navigation);session.setProgressDelegate(bundle.progress);session.setPromptDelegate(bundle.prompts);
        if(domain==PartitionCatalog.Domain.BROWSER && history!=null){
            TabRef tab=(TabRef)token;
            GeckoHistoryDelegate delegate=new GeckoHistoryDelegate(session,actual->{try{owner.requireOpen(ref,gate.requireBusiness());return actual==session;}catch(RuntimeException retired){return false;}},ignored->{},url->{history.visited(tab,url);String title=bundle.model.snapshot().reportedTitle();if(title!=null)history.titleChanged(tab,url,title);},partition.retention()==PartitionCatalog.Retention.PRIVATE_SESSION);
            bundle.events.history=delegate;session.setHistoryDelegate(delegate);bundle.content.installBrowserHistoryPaint(delegate::nativeContentPainted);
            bundle.content.installBrowserHistoryTitle((url,title)->history.titleChanged(tab,url,title));
        }
    }
    @Override public void retireDelegates(GeckoSession session,GeckoSessionOwner.SessionRef ref){
        GeckoSessionOwner.mainThread();Bundle b=bundles.get(ref);if(b==null)return;if(b.session!=session)throw new SecurityException("Foreign native session retirement");
        owner.fenceConnectorDocument(ref);b.prompts.retire();if(session.getSelectionActionDelegate() instanceof SelectionDelegate)((SelectionDelegate)session.getSelectionActionDelegate()).retire();b.navigation.invalidate();b.progress.invalidate();if(b.events.history!=null)b.events.history.invalidate();b.content.retire();bundles.remove(ref);
        // Do not unset ContentDelegate: late rejected bodies STILL must close. The SDK
        // retains the retired callback, which cannot publish positive state or reopen.
        // Unknown body-close/native writers require separate runtime quiescence, never a
        // successful metadata-map removal receipt. No engine-stop API is minted here.
    }
    @Override public SessionPresentation.Frame presentation(GeckoSessionOwner.SessionRef ref){GeckoSessionOwner.mainThread();Bundle b=bundles.get(ref);if(b==null)throw new IllegalStateException("Retired session presentation");return b.model.snapshot();}
    @Override public PromptDelegate promptDelegate(GeckoSessionOwner.SessionRef ref){GeckoSessionOwner.mainThread();Bundle bundle=bundles.get(ref);if(bundle==null)throw new IllegalStateException("Retired native prompt session");return bundle.prompts;}
    /** Private real producer-consumer adapter, not an injectable source/auth/platform provider. */
    private static final class Events implements NavigationDelegate.NativeObserver,ProgressDelegate.NativeObserver {
        private final SessionPresentation model;private final Runnable fenceDocument;private Events(SessionPresentation model,Runnable nativeDocumentFence){if(model==null || nativeDocumentFence==null)throw new IllegalArgumentException("Native document fence required");this.model=model;fenceDocument=nativeDocumentFence;}
        private GeckoHistoryDelegate history;
        public void observed(NavigationDelegate.Observation event){switch(event.event()){
            case LOCATION:fenceDocument.run();model.location(event.reportedLocation());if(history!=null)history.nativeLocation(event.reportedLocation());break;
            case LOAD_ERROR:model.loadError();if(history!=null)history.nativeLoadError();break;
            case BACK_CAPABILITY:model.back(Boolean.TRUE.equals(event.reportedFlag()));break;
            case FORWARD_CAPABILITY:model.forward(Boolean.TRUE.equals(event.reportedFlag()));break;
            default:break; // errors/blocked loads never establish actual documents
        }}
        public void loading(ProgressDelegate.Loading event){switch(event.event()){
            case REPORTED_START:fenceDocument.run();if(history!=null)history.nativePageStart(event.reportedStartUrl());model.start();break;
            case REPORTED_PROGRESS:model.progress(event.reportedProgress());break;
            case REPORTED_STOP:model.stop(Boolean.TRUE.equals(event.reportedStopSuccess()));if(history!=null)history.nativePageStop(Boolean.TRUE.equals(event.reportedStopSuccess()));break;
        }}
        public void security(ProgressDelegate.Security observed){model.security(observed.reportedSecure(),observed.reportedException(),observed.securityMode());}
        public boolean checkpoint(ProgressDelegate.SensitiveCheckpoint offer){return false;} // no writer/serialization/restore receipt
    }
    private final class Bundle {
        final Events events;final GeckoSession session;final SessionPresentation model=new SessionPresentation();final NavigationDelegate navigation;final ProgressDelegate progress;final ContentDelegate content;final PromptDelegate prompts;
        Bundle(GeckoSession session,GeckoSessionOwner.SessionRef ref,PartitionCatalog.Lease partition,Object token){
            this.session=session;content=new ContentDelegate(session,owner,ref,gate,partitions,model);
            if(domain==PartitionCatalog.Domain.BROWSER && history!=null && token instanceof TabRef)model.installNativeObserver(frame->history.sessionReported((TabRef)token,frame));
            prompts=new PromptDelegate(session,gate.captureDataEpoch(gate.requireBusiness()),()->owner.requireOpen(ref,gate.requireBusiness()));
            events=new Events(model,()->{owner.fenceConnectorDocument(ref);prompts.fenceDocument();if(session.getSelectionActionDelegate() instanceof SelectionDelegate)((SelectionDelegate)session.getSelectionActionDelegate()).fenceDocument();});
            navigation=new NavigationDelegate(session,gate,partitions,partition,events);
            progress=new ProgressDelegate(session,gate,partitions,partition,token,events);
        }
    }
}
