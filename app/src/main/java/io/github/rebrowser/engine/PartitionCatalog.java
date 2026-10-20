package io.github.rebrowser.engine;

import io.github.rebrowser.bootstrap.ComponentGate;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Process-local native owner/context registry. No raw context/path/account ID from IPC.
 * Stable for this registry/epoch, NOT yet persisted across process death. Retired contexts
 * remain tombstones and occupy bounded metadata capacity; retirement is NOT byte erasure.
 * BrowserStore/epoch persistence and verified clear-data lifecycle remain separate.
 */
public final class PartitionCatalog {
    public enum Domain { BROWSER, OUTLOOK, MUSIC }
    public enum State { ACTIVE, FENCED, RETIRED }
    public static final class Lease {
        private final PartitionCatalog issuer;
        private final Domain domain;
        private final Object owner;
        private final String context;
        private State state=State.ACTIVE;
        private Lease(PartitionCatalog issuer,Domain domain,Object owner,String context){this.issuer=issuer;this.domain=domain;this.owner=owner;this.context=context;}
        public Domain domain(){return domain;}
        /** Opaque registry ref, never evidence of active scope or physical storage removal. */
        public String opaqueRef(){return context;}
    }
    private final ComponentGate gate;
    private final ComponentGate.ResourcePin sourceLifetime;
    private final Thread thread=Thread.currentThread();
    private final byte[] epoch;
    private final int capacity;
    private final Map<Object,Lease> owners=new IdentityHashMap<>();
    public PartitionCatalog(ComponentGate gate,byte[] nativeDataEpoch,int capacity){
        if(gate==null || nativeDataEpoch==null || nativeDataEpoch.length!=16 || capacity<1 || capacity>1024)throw invalid();
        this.gate=gate;sourceLifetime=gate.newResourcePin();epoch=nativeDataEpoch.clone();this.capacity=capacity;
    }
    public Lease create(Domain domain,Object nativeOwner,ComponentGate.BusinessPermit permit){
        access(permit);if(domain==null || nativeOwner==null)throw invalid();Lease known=owners.get(nativeOwner);
        if(known!=null){if(known.domain!=domain || known.state!=State.ACTIVE)throw new IllegalStateException("Native owner already fenced/assigned");return known;}
        if(owners.size()>=capacity)throw new IllegalStateException("Partition registry capacity reached");
        String context=null;
        for(int attempt=0;attempt<16;attempt++){String candidate=randomContext();boolean collision=false;for(Lease lease:owners.values())if(lease.context.equals(candidate)){collision=true;break;}if(!collision){context=candidate;break;}}
        if(context==null)throw new IllegalStateException("Partition identity collision");
        Lease result=new Lease(this,domain,nativeOwner,context);owners.put(nativeOwner,result);return result;
    }
    public String activeContextId(Lease lease,ComponentGate.BusinessPermit permit){requireActive(lease,permit);return lease.context;}
    public void requireActive(Lease lease,ComponentGate.BusinessPermit permit){access(permit);known(lease);if(lease.state!=State.ACTIVE)throw new SecurityException("Partition fenced/retired");}
    public State state(Lease lease,ComponentGate.BusinessPermit permit){access(permit);known(lease);return lease.state;}
    public void fence(Lease lease,ComponentGate.BusinessPermit permit){access(permit);known(lease);if(lease.state==State.RETIRED)throw new IllegalStateException("Partition already retired");lease.state=State.FENCED;}
    /** Admission retirement only; old work must STILL settle before erasure/quiescence claims. */
    public void retire(Lease lease,ComponentGate.BusinessPermit permit){access(permit);known(lease);if(lease.state!=State.FENCED)throw new IllegalStateException("Fence partition first");lease.state=State.RETIRED;}
    public void requireGate(ComponentGate expected){if(gate!=expected || Thread.currentThread()!=thread)throw invalid();}
    public boolean matchesEpoch(byte[] nativeExpected,ComponentGate.BusinessPermit permit){access(permit);return nativeExpected!=null && Arrays.equals(epoch,nativeExpected);}
    private void known(Lease lease){if(lease==null || lease.issuer!=this || owners.get(lease.owner)!=lease)throw invalid();}
    private void access(ComponentGate.BusinessPermit permit){if(Thread.currentThread()!=thread)throw new IllegalStateException("Partition owner thread required");sourceLifetime.checkOrBind(permit);}
    private static String randomContext(){byte[] b=new byte[16];new SecureRandom().nextBytes(b);StringBuilder out=new StringBuilder("ctx_");for(byte v:b){out.append("0123456789abcdef".charAt((v&255)>>>4));out.append("0123456789abcdef".charAt(v&15));}return out.toString();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native partition handle/binding");}
}
