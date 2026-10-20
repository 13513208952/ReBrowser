package io.github.rebrowser.engine;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.browser.BrowserModels.TabRef;
import java.util.IdentityHashMap;
import java.util.Map;
import org.mozilla.geckoview.GeckoSession;

/** Actual temporary-browser delegate graph. No Activity retention or caller-provided platform adapter. */
public final class BrowserSessionHost implements GeckoSessionOwner.Initializer {
    private final ComponentGate gate;private final PartitionCatalog partitions;private final int capacity;
    private GeckoSessionOwner owner;
    private final Map<GeckoSessionOwner.SessionRef,Bundle> bundles=new IdentityHashMap<>();
    private BrowserSessionHost(ComponentGate gate,PartitionCatalog partitions,int capacity){this.gate=gate;this.partitions=partitions;this.capacity=capacity;}
    public static GeckoSessionOwner createTemporaryOwner(ComponentGate gate,GeckoRuntimeOwner runtime,PartitionCatalog partitions,int capacity){
        GeckoSessionOwner.mainThread();if(gate==null || runtime==null || partitions==null || capacity<1 || capacity>64)throw new IllegalArgumentException("Bounded native browser graph required");
        BrowserSessionHost host=new BrowserSessionHost(gate,partitions,capacity);GeckoSessionOwner sessions=new GeckoSessionOwner(gate,runtime,partitions,host,capacity);host.owner=sessions;return sessions;
    }
    @Override public void reconcileBeforeOpen(PartitionCatalog.Lease partition,ComponentGate.BusinessPermit permit){
        GeckoSessionOwner.mainThread();partitions.requireActive(partition,permit);
        if(partition.domain()!=PartitionCatalog.Domain.BROWSER || partition.retention()!=PartitionCatalog.Retention.PRIVATE_SESSION)throw new SecurityException("Only explicit fresh private browser contexts supported");
        // No restore/grant import: brand-new process catalog contexts only. This is NOT
        // reconciliation/revocation of existing stored permissions or finite grant support.
    }
    @Override public void installDelegates(GeckoSession session,GeckoSessionOwner.SessionRef ref,PartitionCatalog.Lease partition,Object token){
        GeckoSessionOwner.mainThread();if(owner==null || !(token instanceof TabRef) || ref.partition()!=partition || session.isOpen() || bundles.containsKey(ref) || bundles.size()>=capacity)throw new IllegalStateException("Exact unopened browser allocation required");
        partitions.requireOwner(partition,((TabRef)token).workspace(),gate.requireBusiness());
        Bundle bundle=new Bundle(session,ref,partition,token);bundles.put(ref,bundle); // retain before ANY SDK setter
        session.setPermissionDelegate(new PermissionDelegate());
        session.setContentDelegate(bundle.content);session.setNavigationDelegate(bundle.navigation);session.setProgressDelegate(bundle.progress);
    }
    @Override public void retireDelegates(GeckoSession session,GeckoSessionOwner.SessionRef ref){
        GeckoSessionOwner.mainThread();Bundle b=bundles.get(ref);if(b==null)return;if(b.session!=session)throw new SecurityException("Foreign native session retirement");
        b.navigation.invalidate();b.progress.invalidate();b.content.retire();bundles.remove(ref);
        // Do not unset ContentDelegate: late rejected bodies STILL must close. The SDK
        // retains the retired callback, which cannot publish positive state or reopen.
        // Unknown body-close/native writers require separate runtime quiescence, never a
        // successful metadata-map removal receipt. No engine-stop API is minted here.
    }
    @Override public SessionPresentation.Frame presentation(GeckoSessionOwner.SessionRef ref){GeckoSessionOwner.mainThread();Bundle b=bundles.get(ref);if(b==null)throw new IllegalStateException("Retired session presentation");return b.model.snapshot();}
    /** Private real producer-consumer adapter, not an injectable source/auth/platform provider. */
    private static final class Events implements NavigationDelegate.NativeObserver,ProgressDelegate.NativeObserver {
        private final SessionPresentation model;private Events(SessionPresentation model){this.model=model;}
        public void observed(NavigationDelegate.Observation event){switch(event.event()){
            case LOCATION:model.location(event.reportedLocation());break;
            case BACK_CAPABILITY:model.back(Boolean.TRUE.equals(event.reportedFlag()));break;
            case FORWARD_CAPABILITY:model.forward(Boolean.TRUE.equals(event.reportedFlag()));break;
            default:break; // errors/blocked loads never establish actual documents
        }}
        public void loading(ProgressDelegate.Loading event){switch(event.event()){
            case REPORTED_START:model.start();break;
            case REPORTED_PROGRESS:model.progress(event.reportedProgress());break;
            case REPORTED_STOP:model.stop(Boolean.TRUE.equals(event.reportedStopSuccess()));break;
        }}
        public void security(ProgressDelegate.Security observed){model.security(observed.reportedSecure(),observed.reportedException(),observed.securityMode());}
        public boolean checkpoint(ProgressDelegate.SensitiveCheckpoint offer){return false;} // no writer/serialization/restore receipt
    }
    private final class Bundle {
        final GeckoSession session;final SessionPresentation model=new SessionPresentation();final NavigationDelegate navigation;final ProgressDelegate progress;final ContentDelegate content;
        Bundle(GeckoSession session,GeckoSessionOwner.SessionRef ref,PartitionCatalog.Lease partition,Object token){
            this.session=session;content=new ContentDelegate(session,owner,ref,gate,partitions,model);
            Events events=new Events(model);
            navigation=new NavigationDelegate(session,gate,partitions,partition,events);
            progress=new ProgressDelegate(session,gate,partitions,partition,token,events);
        }
    }
}
