package io.github.rebrowser.bridge;

import android.os.Looper;
import io.github.rebrowser.core.TaskCoordinator;
import io.github.rebrowser.engine.GeckoSessionOwner;
import io.github.rebrowser.engine.PartitionCatalog;
import java.net.URI;
import java.util.*;
import java.security.SecureRandom;
import org.json.JSONObject;
import org.mozilla.geckoview.*;

/** Exact SDK extension/session/content-script/top-level/port routing. No arbitrary page/native
 * commands: only fixed read-only observation requests after a REAL native task dispatch commit.
 * Parsed payloads remain page claims. Domain session owners/navigation fences and packaged bridge
 * assets are NOT activated by this component; general browser contexts are explicitly rejected.
 * Port loss/RESULT/CANCEL_STATUS never reports native-owner end or frees a request/stream slot. */
public final class ConnectorBroker implements WebExtension.MessageDelegate,WebExtension.PortDelegate {
    public interface Listener {void received(Request originalOrNull,DocumentLease.Received received);}
    public static final class Request {
        private final ConnectorBroker issuer;private final String id;private DocumentLease lease;private TaskCoordinator.Owner owner;private BridgeStreamReceiver stream;
        private boolean accepted,resultSeen,postUnknown,released;
        private Request(ConnectorBroker issuer,String id,DocumentLease lease,TaskCoordinator.Owner owner){this.issuer=issuer;this.id=id;this.lease=lease;this.owner=owner;}
        public String requestId(){return id;}public boolean nativePostOutcomeUnknown(){return postUnknown;}
    }
    private final GeckoSessionOwner.ConnectorContext context;private final BuiltinConnectorManager.Installed installed;private final WebExtension extension;private final GeckoSession session;private final PartitionCatalog.Domain domain;private final String nativeApp,action;private final Listener listener;
    private final Map<WebExtension.Port,DocumentLease> ports=new IdentityHashMap<>();private final Map<String,Request> requests=new LinkedHashMap<>();private final BridgeBudget.Streams streams=new BridgeBudget.Streams();private final BridgeBudget.Ingress ingress=new BridgeBudget.Ingress();private final SecureRandom random=new SecureRandom();
    private DocumentLease active;private WebExtension.Port activePort;private boolean retired,unknownPortClose;
    public ConnectorBroker(GeckoSessionOwner.ConnectorContext actualContext,BuiltinConnectorManager.Installed actualInstalled,Listener listener){
        main();if(actualContext==null || actualInstalled==null || listener==null)throw bad();context=actualContext;installed=actualInstalled;extension=installed.requireExtension();session=context.nativeSession();domain=context.domain();
        if(domain==PartitionCatalog.Domain.MUSIC){nativeApp="rebrowser_music";action=MusicConnector.ACTION;}else if(domain==PartitionCatalog.Domain.OUTLOOK){nativeApp="rebrowser_outlook";action=OutlookConnector.ACTION;}else throw bad();this.listener=listener;
        context.claimNativeBroker(this); // retain native lifetime claim BEFORE any SDK setter/callback
        try{session.getWebExtensionController().setMessageDelegate(extension,this,nativeApp);check();}catch(RuntimeException unknown){retired=true;throw unknown;}
    }
    @Override public GeckoResult<Object> onMessage(String app,Object raw,WebExtension.MessageSender sender){return null;} // No unleased one-shot/native eval path.
    @Override public void onConnect(WebExtension.Port port){
        main();try{check();if(active!=null){try{active.requireCurrent();}catch(RuntimeException stale){active=null;activePort=null;}}if(port==null || ports.size()>=16 || active!=null || port.sender==null || port.sender.webExtension!=extension || port.sender.session!=session || port.sender.environmentType!=WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT || !port.sender.isTopLevel() || !nativeApp.equals(port.name) || !allowedUrl(domain,port.sender.url))throw bad();
            final long navigation=context.navigationRevision();DocumentLease lease=new DocumentLease(port,context.sourcePin(),context.partitionRef(),context.sessionRef(),()->{check();if(context.navigationRevision()!=navigation)throw new SecurityException("Native connector navigation fenced");});
            ports.put(port,lease);active=lease;activePort=port;port.setDelegate(this);
        }catch(RuntimeException rejected){DocumentLease partial=ports.get(port);if(partial!=null){partial.retire();if(active==partial){active=null;activePort=null;}}closeRejected(port);}
    }
    @Override public void onPortMessage(Object raw,WebExtension.Port port){
        main();DocumentLease lease=ports.get(port);if(lease==null)return;
        try{check();lease.requirePort(port);if(!(raw instanceof String))throw bad();try(BridgeBudget.Envelope owned=ingress.retain((String)raw)){
            BridgeMessages.Message message=BridgeMessages.parse(owned.borrowed());
            if(message.kind()==BridgeMessages.Kind.HELLO){lease.spendHello(port,message);port.postMessage(new JSONObject(BridgeMessages.nativeReady(lease.nativeBinding(),action)));lease.readyPostReturned(port);return;}
            DocumentLease.Received received=lease.receive(port,message);Request original=matchRequest(lease,message);
            if(message.kind()==BridgeMessages.Kind.ACCEPTED){if(original.accepted)throw bad();original.accepted=true;}
            if(message.kind()==BridgeMessages.Kind.RESULT){if(original.resultSeen)throw bad();original.resultSeen=true;}
            // No auto settle/end/release or business-side commands from page data.
            listener.received(original,received);
        }}catch(Exception rejected){lease.retire();if(active==lease){active=null;activePort=null;}closeRejected(port);}
    }
    private Request matchRequest(DocumentLease lease,BridgeMessages.Message message){
        String id=null;switch(message.kind()){
            case ACCEPTED:id=message.accepted().requestId();break;case RESULT:id=message.result().requestId();break;case CANCEL_STATUS:id=message.cancelStatus().requestId();break;case STREAM_BEGIN:id=message.streamBegin().requestId();break;case ERROR:id=message.error().requestId();break;
            case STREAM_CHUNK:case STREAM_END:{String stream=message.kind()==BridgeMessages.Kind.STREAM_CHUNK?message.streamChunk().streamId():message.streamEnd().streamId();for(Request r:requests.values())if(!r.released && r.lease==lease && r.stream!=null && r.stream.streamId().equals(stream))return r;throw bad();}
            case EVENT:return null;default:throw bad();
        }
        if(id==null && message.kind()==BridgeMessages.Kind.ERROR)return null;Request r=requests.get(id);if(r==null || r.released || r.lease!=lease)throw bad();return r;
    }
    @Override public void onDisconnect(WebExtension.Port port){main();DocumentLease lease=ports.get(port);if(lease==null)return;lease.retire();if(active==lease){active=null;activePort=null;}} // Not document/native work end.
    public Request observe(TaskCoordinator.Owner nativeReservedOwner){
        check();if(nativeReservedOwner==null || active==null || activePort==null || requests.size()>=32 || streams.hasOwnedStream())throw bad();active.requireCurrent();if(active.state()!=DocumentLease.State.READY_OFFERED)throw bad();for(Request r:requests.values())if(!r.released)throw new IllegalStateException("Native connector still owns previous request");
        String id=null;for(int n=0;n<16;n++){byte[] b=new byte[16];random.nextBytes(b);String candidate="r_"+DocumentLease.hex(b);if(!requests.containsKey(candidate)){id=candidate;break;}}if(id==null)throw bad();
        Request request=new Request(this,id,active,nativeReservedOwner);nativeReservedOwner.claimCommittedNativeRoute(request);requests.put(id,request);
        try{active.requireCurrent();if(!nativeReservedOwner.mayPublishCurrentResult())throw new SecurityException("Native task source fenced");activePort.postMessage(new JSONObject(BridgeMessages.nativeObserve(active.nativeBinding(),id,action)));active.requireCurrent();}
        catch(Exception unknown){request.postUnknown=true;}return request; // Actual possible handoff remains owned; never second route.
    }
    public BridgeStreamReceiver admitObservationStream(Request request,DocumentLease.Received begin,long nativeMaximum){
        check();ownLive(request);if(nativeMaximum<0 || nativeMaximum>BridgeMessages.MAX_ENVELOPE_BYTES || request.stream!=null)throw bad();begin.requireLease(request.lease);request.stream=new BridgeStreamReceiver(request.lease,begin,request.id,"observation-json",nativeMaximum,streams);return request.stream;
    }
    public void grantStreamCredit(Request request,int bytes)throws Exception{check();ownLive(request);if(request.stream==null || request.lease!=active || activePort==null)throw bad();String frame=request.stream.creditBeforePost(bytes);try{activePort.postMessage(new JSONObject(frame));}catch(Exception unknown){request.postUnknown=true;throw unknown;}}
    /** Actual TaskCoordinator owner end only; owned polled chunks STILL keep shared byte/slot credit. */
    public void releaseAfterNativeOwnerEnded(Request request){
        main();ownLive(request);if(!request.owner.hasReportedActualDomainEnd())throw new IllegalStateException("Actual native connector owner has not ended");if(request.stream!=null){request.stream.close();request.stream.nativeOwnerEnded();}request.released=true;request.lease=null;request.owner=null;
    }
    public void retire(){main();retired=true;for(DocumentLease lease:ports.values())lease.retire();active=null;activePort=null;} // Do not release native requests, streams or runtime.
    public boolean hasUnknownNativePortClose(){main();return unknownPortClose;}
    private void ownLive(Request r){if(r==null || r.issuer!=this || r.released || requests.get(r.id)!=r)throw bad();}
    private void check(){main();if(retired)throw new SecurityException("Native connector broker retired");context.requireCurrent();if(installed.requireExtension()!=extension)throw bad();}
    private void closeRejected(WebExtension.Port port){if(port==null)return;try{port.disconnect();}catch(RuntimeException unknown){unknownPortClose=true;}}
    /** Native SDK sender URL only, NOT data from HELLO or a page-provided location. */
    static boolean allowedUrl(PartitionCatalog.Domain domain,String value){
        if(value==null || value.length()>8192)return false;try{URI uri=new URI(value);if(!"https".equals(uri.getScheme()) || uri.getRawUserInfo()!=null || uri.getHost()==null || uri.getPort()!=-1 && uri.getPort()!=443)return false;String host=uri.getHost().toLowerCase(Locale.ROOT);if(domain==PartitionCatalog.Domain.MUSIC)return host.equals("music.163.com");if(domain==PartitionCatalog.Domain.OUTLOOK)return host.equals("outlook.live.com") || host.equals("outlook.office.com") || host.equals("outlook.office365.com");return false;}catch(java.net.URISyntaxException invalid){return false;}
    }
    private static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native connector UI thread required");}
    private static IllegalArgumentException bad(){return new IllegalArgumentException("Invalid native connector route/request");}
}
