package io.github.rebrowser.music;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.core.TaskCoordinator;
import io.github.rebrowser.engine.*;

/** One native music partition/session shared by UI and service, not an Activity-owned player.
 * No automatic URL/login/play/capture or session re-open. Current context IDs are process-local,
 * NOT qualified persistent-cookie restoration. Failed/closed owners remain fenced until restart. */
public final class MusicSessionOwner {
    private final ComponentGate gate;private final ComponentGate.DataEpochPin source;private final PartitionCatalog partitions;private final PartitionCatalog.Lease partition;private final GeckoSessionOwner sessions;private final Object domain=new Object();private GeckoSessionOwner.SessionRef session;private boolean attempted;
    public MusicSessionOwner(ComponentGate gate,GeckoRuntimeOwner actualRuntime,ComponentGate.BusinessPermit permit){if(gate==null || actualRuntime==null)throw new IllegalArgumentException("Native music runtime required");this.gate=gate;source=gate.captureDataEpoch(permit);partitions=new PartitionCatalog(gate,source.dataEpoch(),1);partition=partitions.create(PartitionCatalog.Domain.MUSIC,domain,permit);sessions=BrowserSessionHost.createBuiltinOwner(gate,actualRuntime,partitions,PartitionCatalog.Domain.MUSIC,domain);}
    public GeckoSessionOwner.SessionRef requireOrOpen(ComponentGate.BusinessPermit permit){check(permit);if(session!=null){sessions.requireOpen(session,permit);return session;}if(attempted)throw new IllegalStateException("Music session opening already attempted");attempted=true;GeckoSessionOwner.SessionRef opened=sessions.open(partition,domain,permit);check(permit);session=opened;return opened;}
    public GeckoSessionOwner.ConnectorContext bridgeContext(ComponentGate.BusinessPermit permit){return sessions.captureConnectorContext(requireOrOpen(permit),permit);}
    public void openOfficialEntry(TaskCoordinator.Owner nativeReservedTask,ComponentGate.BusinessPermit permit){check(permit);if(nativeReservedTask==null)throw new SecurityException("Native music navigation task required");GeckoSessionOwner.SessionRef ref=requireOrOpen(permit);nativeReservedTask.requireNativeDataSource(source);nativeReservedTask.claimCommittedNativeRoute(new Object());sessions.fenceConnectorDocument(ref);sessions.requireOpen(ref,permit).loadUri("https://music.163.com/");}
    public GeckoSessionOwner nativeSessions(ComponentGate.BusinessPermit permit){check(permit);return sessions;}
    public GeckoSessionOwner.StopSweep requestOwnedSessionClose(){return sessions.closeUndisplayedForDomain(PartitionCatalog.Domain.MUSIC);} // Not media/network actual-end proof.
    private void check(ComponentGate.BusinessPermit permit){source.requireBindings(gate,permit);partitions.requireActive(partition,permit);}
}
