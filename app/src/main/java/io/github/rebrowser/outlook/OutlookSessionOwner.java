package io.github.rebrowser.outlook;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.core.TaskCoordinator;
import io.github.rebrowser.engine.*;

/** One exact native Outlook partition/session. No WebView, UA spoof, login promise, copied
 * cookies, cross-account popup promotion or automatic restore/reopen. Native popup factory is
 * still required for real authentication popup closure; default delegates reject unsupported popups. */
public final class OutlookSessionOwner {
    private final ComponentGate gate;private final ComponentGate.DataEpochPin source;private final PartitionCatalog partitions;private final PartitionCatalog.Lease partition;private final GeckoSessionOwner sessions;private final Object domain=new Object();private GeckoSessionOwner.SessionRef session;private boolean attempted;
    public OutlookSessionOwner(ComponentGate gate,GeckoRuntimeOwner runtime,ComponentGate.BusinessPermit permit){if(gate==null || runtime==null)throw new IllegalArgumentException("Native Outlook runtime required");this.gate=gate;source=gate.captureDataEpoch(permit);partitions=new PartitionCatalog(gate,source.dataEpoch(),1);partition=partitions.create(PartitionCatalog.Domain.OUTLOOK,domain,permit);sessions=BrowserSessionHost.createBuiltinOwner(gate,runtime,partitions,PartitionCatalog.Domain.OUTLOOK,domain);}
    public GeckoSessionOwner.SessionRef requireOrOpen(ComponentGate.BusinessPermit permit){check(permit);if(session!=null){sessions.requireOpen(session,permit);return session;}if(attempted)throw new IllegalStateException("Outlook session opening already attempted");attempted=true;GeckoSessionOwner.SessionRef opened=sessions.open(partition,domain,permit);check(permit);session=opened;return opened;}
    public GeckoSessionOwner.ConnectorContext bridgeContext(ComponentGate.BusinessPermit permit){return sessions.captureConnectorContext(requireOrOpen(permit),permit);}
    public void openOfficialEntry(TaskCoordinator.Owner nativeReservedTask,OfficialMailEntry.Kind kind,ComponentGate.BusinessPermit permit){check(permit);if(nativeReservedTask==null || kind==null)throw new SecurityException("Native Outlook navigation task required");String url=OfficialMailEntry.require(kind).url();GeckoSessionOwner.SessionRef ref=requireOrOpen(permit);nativeReservedTask.claimCommittedNativeRoute(new Object());sessions.fenceConnectorDocument(ref);sessions.requireOpen(ref,permit).loadUri(url);}
    public GeckoSessionOwner nativeSessions(ComponentGate.BusinessPermit permit){check(permit);return sessions;}
    public GeckoSessionOwner.StopSweep requestOwnedSessionClose(){return sessions.closeUndisplayedForDomain(PartitionCatalog.Domain.OUTLOOK);} // Not mailbox persistence/actual native end.
    private void check(ComponentGate.BusinessPermit permit){source.requireBindings(gate,permit);partitions.requireActive(partition,permit);}
}
