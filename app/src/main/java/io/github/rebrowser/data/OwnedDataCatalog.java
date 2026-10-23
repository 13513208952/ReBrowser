package io.github.rebrowser.data;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.core.ResourceBudgets;
import io.github.rebrowser.crypto.PrivateRecordSealer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.EnumMap;

/** Exact fixed browser-metadata subset of epoch-owned records. Never accepts paths, URIs or
 * caller-chosen scopes. Not a COMPLETE epoch manifest, erasure inventory, seed or data authority.
 * Native domain authorization precedes access. Missing is NOT fresh/latest/empty configuration.
 * One decoded root credit survives read return until close; external retained values/copies are
 * separately caller-owned. No automatic retry, save, restore, schema repair or cleanup. */
public final class OwnedDataCatalog {
    public enum Family {
        BOOKMARKS(PrivateRecordSealer.Purpose.BOOKMARKS,"RB-BOOKMARKS-001"),
        HISTORY(PrivateRecordSealer.Purpose.HISTORY,"RB-HISTORY-00001"),
        SETTINGS(PrivateRecordSealer.Purpose.SETTINGS,"RB-SETTINGS-0001");
        final PrivateRecordSealer.Purpose purpose;final byte[] scope;
        Family(PrivateRecordSealer.Purpose purpose,String scope){this.purpose=purpose;this.scope=scope.getBytes(StandardCharsets.US_ASCII);if(this.scope.length!=16)throw new AssertionError("Fixed native scope");}
    }
    public static final class View<T> implements AutoCloseable {
        private final OwnedDataCatalog issuer;private final Family family;private final NativeRecordCodec.Codec<T> codec;private final PrivateRecordStore.ReadStamp stamp;
        private T value;private ResourceBudgets.Allocation credit;private boolean closed,attempted,dispatchAttempted;
        private View(OwnedDataCatalog issuer,Family family,NativeRecordCodec.Codec<T> codec,PrivateRecordStore.ReadStamp stamp,T value,ResourceBudgets.Allocation credit){this.issuer=issuer;this.family=family;this.codec=codec;this.stamp=stamp;this.value=value;this.credit=credit;}
        public synchronized PrivateRecordStore.Kind kind(){open();return stamp.kind();}
        public synchronized long recordRevision(){open();return stamp.revision();}
        public synchronized T value(ComponentGate.BusinessPermit permit){open();issuer.check(permit);if(stamp.kind()!=PrivateRecordStore.Kind.CURRENT || value==null)throw new IllegalStateException("Authenticated native metadata unavailable");return value;}
        private void open(){if(closed)throw new IllegalStateException("Native metadata view closed");}
        private synchronized void writeAttempt(OwnedDataCatalog owner){open();if(owner!=issuer || attempted || dispatchAttempted || stamp.kind()==PrivateRecordStore.Kind.UNFINISHED_WRITE)throw NativeRecordCodec.bad();attempted=true;}
        public synchronized void beginNativeHandoff(OwnedDataCatalog owner,ComponentGate.BusinessPermit permit)throws IOException{open();if(owner!=issuer || dispatchAttempted || attempted)throw NativeRecordCodec.bad();dispatchAttempted=true;issuer.requireExact(this,permit);}
        @Override public synchronized void close(){if(closed)return;closed=true;value=null;if(credit!=null){credit.releaseAfterOwnedAllocationFreed();credit=null;}}
    }
    private final PrivateRecordStore records;private final ComponentGate gate;private final ComponentGate.DataEpochPin source;private final EnumMap<Family,PrivateRecordStore.Reference> refs=new EnumMap<>(Family.class);
    private final ResourceBudgets.ByteBudget decoded=new ResourceBudgets.ByteBudget(16L*1024*1024,1);
    private OwnedDataCatalog(PrivateRecordStore records,ComponentGate gate,ComponentGate.DataEpochPin source){this.records=records;this.gate=gate;this.source=source;}
    public static OwnedDataCatalog openExisting(PrivateRecordStore records,ComponentGate gate,ComponentGate.BusinessPermit permit){if(records==null || gate==null)throw NativeRecordCodec.bad();ComponentGate.DataEpochPin pin=gate.captureDataEpoch(permit);records.requireBindings(gate,pin.dataEpoch(),permit);OwnedDataCatalog owner=new OwnedDataCatalog(records,gate,pin);for(Family family:Family.values())owner.refs.put(family,records.reference(family.purpose,family.scope,permit));owner.check(permit);return owner;}
    public <T>View<T> read(Family family,NativeRecordCodec.Codec<T> codec,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{
        check(permit);if(family==null || codec==null)throw NativeRecordCodec.bad();ResourceBudgets.Allocation credit=decoded.tryReserve(16L*1024*1024);if(credit==null)throw new IllegalStateException("Prior decoded native metadata still owned");
        try(PrivateRecordStore.ReadRecord read=records.read(refs.get(family),permit)){
            T value=read.stamp().kind()==PrivateRecordStore.Kind.CURRENT?read.readOwnedPlaintext(codec::decode):null;check(permit);View<T> view=new View<>(this,family,codec,read.stamp(),value,credit);credit=null;return view;
        }finally{if(credit!=null)credit.releaseAfterOwnedAllocationFreed();}
    }
    /** Explicit native write of intended COMPLETE metadata against exact prior disk stamp.
     * MISSING may be explicitly written, but no default/automatic fresh install is inferred. */
    public <T>long compareAndWrite(View<T> expected,T intended,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{
        check(permit);if(expected==null || intended==null)throw NativeRecordCodec.bad();expected.writeAttempt(this);byte[] plaintext=null;
        try{plaintext=expected.codec.encode(intended);if(plaintext==null || plaintext.length>expected.family.purpose.maximumPlaintextBytes())throw NativeRecordCodec.bad();check(permit);return records.write(expected.stamp,plaintext,permit).revision();}finally{if(plaintext!=null)Arrays.fill(plaintext,(byte)0);}
    }
    public void requireFamily(View<?> view,Family family,NativeRecordCodec.Codec<?> codec){if(view==null || view.issuer!=this || view.family!=family || view.codec!=codec)throw NativeRecordCodec.bad();synchronized(view){view.open();}}
    public void requireExact(View<?> view,ComponentGate.BusinessPermit permit)throws IOException{if(view==null || view.issuer!=this)throw NativeRecordCodec.bad();synchronized(view){view.open();check(permit);records.requireCurrentStamp(view.stamp,permit);check(permit);}}
    /** Capture on the native IO owner; the returned epoch pin permits cheap source checks on UI. */
    public ComponentGate.DataEpochPin captureDataEpoch(ComponentGate.BusinessPermit permit){check(permit);return gate.captureDataEpoch(permit);}
    public void requireBindings(ComponentGate expected,ComponentGate.BusinessPermit permit){if(expected!=gate)throw NativeRecordCodec.bad();check(permit);}
    private void check(ComponentGate.BusinessPermit permit){source.requireBindings(gate,permit);records.requireBindings(gate,source.dataEpoch(),permit);}
}
