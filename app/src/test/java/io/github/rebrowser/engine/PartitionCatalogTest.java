package io.github.rebrowser.engine;

import static org.junit.Assert.*;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.ProcessRole;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/** Process-local issued contexts only; no Gecko, storage erasure or durable restore claim. */
public final class PartitionCatalogTest {
    private static final class Fixture {
        final AtomicReference<ComponentGate.SourceState> source=new AtomicReference<>(ComponentGate.SourceState.TRUSTED);
        final ComponentGate gate=new ComponentGate(ProcessRole.MAIN,source::get);
        final ComponentGate.BusinessPermit permit=gate.requireBusiness();
        final PartitionCatalog catalog=new PartitionCatalog(gate,new byte[16],4);
    }
    @Test public void sameNativeOwnerSharesContextDifferentOwnersAndDomainsDoNot(){
        Fixture f=new Fixture();Object owner=new Object();PartitionCatalog.Lease first=f.catalog.create(PartitionCatalog.Domain.BROWSER,owner,f.permit);
        assertSame(first,f.catalog.create(PartitionCatalog.Domain.BROWSER,owner,f.permit));
        PartitionCatalog.Lease second=f.catalog.create(PartitionCatalog.Domain.BROWSER,new Object(),f.permit),mail=f.catalog.create(PartitionCatalog.Domain.OUTLOOK,new Object(),f.permit);
        assertNotEquals(first.opaqueRef(),second.opaqueRef());assertNotEquals(second.opaqueRef(),mail.opaqueRef());assertTrue(first.opaqueRef().matches("ctx_[0-9a-f]{32}"));
        assertThrows(IllegalStateException.class,()->f.catalog.create(PartitionCatalog.Domain.MUSIC,owner,f.permit));
    }
    @Test public void fenceBlocksReuseAndRetirementIsMonotonicNotErasure(){
        Fixture f=new Fixture();Object owner=new Object();PartitionCatalog.Lease lease=f.catalog.create(PartitionCatalog.Domain.BROWSER,owner,f.permit);String context=f.catalog.activeContextId(lease,f.permit);
        assertThrows(IllegalStateException.class,()->f.catalog.retire(lease,f.permit));f.catalog.fence(lease,f.permit);
        assertThrows(SecurityException.class,()->f.catalog.activeContextId(lease,f.permit));assertThrows(IllegalStateException.class,()->f.catalog.create(PartitionCatalog.Domain.BROWSER,owner,f.permit));
        f.catalog.retire(lease,f.permit);assertEquals(PartitionCatalog.State.RETIRED,f.catalog.state(lease,f.permit));assertEquals(context,lease.opaqueRef());
        assertThrows(IllegalStateException.class,()->f.catalog.fence(lease,f.permit));
    }
    @Test public void tombstonesStillConsumeBoundedRegistryCapacity(){
        Fixture f=new Fixture();for(int i=0;i<4;i++){PartitionCatalog.Lease l=f.catalog.create(PartitionCatalog.Domain.BROWSER,new Object(),f.permit);f.catalog.fence(l,f.permit);f.catalog.retire(l,f.permit);}
        assertThrows(IllegalStateException.class,()->f.catalog.create(PartitionCatalog.Domain.BROWSER,new Object(),f.permit));
    }
    @Test public void foreignLeasePermitAndSourceClosureCannotBecomeActive(){
        Fixture f=new Fixture(),other=new Fixture();PartitionCatalog.Lease foreign=other.catalog.create(PartitionCatalog.Domain.BROWSER,new Object(),other.permit);
        assertThrows(IllegalArgumentException.class,()->f.catalog.requireActive(foreign,f.permit));assertThrows(SecurityException.class,()->f.catalog.create(PartitionCatalog.Domain.BROWSER,new Object(),other.permit));
        f.source.set(ComponentGate.SourceState.LOCKED);assertThrows(SecurityException.class,()->f.catalog.create(PartitionCatalog.Domain.BROWSER,new Object(),f.permit));
    }
    @Test public void epochInputIsDefensivelyCopiedAndGateBindingsAreExact(){
        Fixture f=new Fixture();byte[] epoch=new byte[16];PartitionCatalog c=new PartitionCatalog(f.gate,epoch,4);epoch[0]=1;
        assertTrue(c.matchesEpoch(new byte[16],f.permit));assertFalse(c.matchesEpoch(epoch,f.permit));assertFalse(c.matchesEpoch(null,f.permit));
        assertThrows(IllegalArgumentException.class,()->c.requireGate(new Fixture().gate));
    }
    @Test public void ownerThreadIsEnforcedBeforeMutation()throws Exception{
        Fixture f=new Fixture();ExecutorService pool=Executors.newSingleThreadExecutor();
        try{assertTrue(pool.submit(()->{try{f.catalog.create(PartitionCatalog.Domain.BROWSER,new Object(),f.permit);return false;}catch(IllegalStateException expected){return true;}}).get(2,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
        assertNotNull(f.catalog.create(PartitionCatalog.Domain.BROWSER,new Object(),f.permit));
    }
}
