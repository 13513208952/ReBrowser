package io.github.rebrowser.engine;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.browser.*;
import io.github.rebrowser.core.ResourceBudgets;
import io.github.rebrowser.crypto.PrivateRecordSealer;
import io.github.rebrowser.data.PrivateRecordStore;
import java.io.IOException;
import java.nio.*;
import java.nio.charset.*;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/** Existing encrypted p2 checkpoint reader, exact authenticated retained-metadata/tab identity,
 * engine/version/length/UTF8/EOF checks and retained-byte credit. No automatic restore, native
 * refs/current document/account/latest claims or arbitrary IPC scope. Deliberately NO capture or
 * write-from-SessionState API: pinned SDK toString() fully materializes before an output bound.
 * A qualified bounded native capture producer + native restore lane remain separate blockers.
 * Record absence is NOT fresh/empty permission to seed. Not invoked at startup. */
public final class SessionCheckpointStore {
    private static final long MAGIC=0x524243484b303100L; // RBCHK01\0
    public static final int MAX_RECORD_BYTES=2*1024*1024;
    private static final String ENGINE="158.0.20261007201254";
    public static final class ReadView implements AutoCloseable {
        private final SessionCheckpointStore issuer;private final BrowserStore.ReadView catalog;private final PrivateRecordStore.Kind kind;private final long revision;private byte[] opaqueState;private ResourceBudgets.Allocation credit;private boolean closed;
        private ReadView(SessionCheckpointStore issuer,BrowserStore.ReadView catalog,PrivateRecordStore.Kind kind,long revision,byte[] state,ResourceBudgets.Allocation credit){this.issuer=issuer;this.catalog=catalog;this.kind=kind;this.revision=revision;opaqueState=state;this.credit=credit;}
        public synchronized PrivateRecordStore.Kind recordKind(){open();return kind;}public synchronized long recordRevision(){open();return revision;}
        /** Caller borrowed bytes only; no SDK object/restore approval, duplicates become caller-owned. */
        public synchronized ByteBuffer borrowOpaqueSerializedBytes(ComponentGate.BusinessPermit permit){open();issuer.check(permit);catalog.metadata(permit);if(opaqueState==null)throw new IllegalStateException("No authenticated existing native checkpoint");return ByteBuffer.wrap(opaqueState).asReadOnlyBuffer();}
        private void open(){if(closed)throw new IllegalStateException("Native checkpoint read closed");}
        @Override public synchronized void close(){if(closed)return;closed=true;if(opaqueState!=null){Arrays.fill(opaqueState,(byte)0);opaqueState=null;}if(credit!=null){credit.releaseAfterOwnedAllocationFreed();credit=null;}}
    }
    private final PrivateRecordStore records;private final BrowserStore browser;private final ComponentGate gate;private final ComponentGate.DataEpochPin source;private final ResourceBudgets.ByteBudget held=new ResourceBudgets.ByteBudget(MAX_RECORD_BYTES,1);
    private SessionCheckpointStore(PrivateRecordStore records,BrowserStore browser,ComponentGate gate,ComponentGate.DataEpochPin source){this.records=records;this.browser=browser;this.gate=gate;this.source=source;}
    public static SessionCheckpointStore openExisting(PrivateRecordStore records,BrowserStore canonicalExistingBrowserStore,ComponentGate gate,ComponentGate.BusinessPermit permit){if(records==null || canonicalExistingBrowserStore==null || gate==null)throw bad();ComponentGate.DataEpochPin pin=gate.captureDataEpoch(permit);records.requireBindings(gate,pin.dataEpoch(),permit);canonicalExistingBrowserStore.requireBindings(gate,permit);return new SessionCheckpointStore(records,canonicalExistingBrowserStore,gate,pin);}
    public ReadView readForExactRetainedMetadata(BrowserStore.ReadView originalNativeCatalog,BrowserCatalogCodec.Tab originalTab,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{
        check(permit);if(originalNativeCatalog==null || originalTab==null || originalNativeCatalog.kind()!=PrivateRecordStore.Kind.CURRENT)throw bad();BrowserCatalogCodec.UnverifiedCatalog metadata=originalNativeCatalog.metadata(permit);BrowserCatalogCodec.Workspace parent=null;for(BrowserCatalogCodec.Workspace w:metadata.workspaces())for(BrowserCatalogCodec.Tab t:w.tabs())if(t==originalTab)parent=w;if(parent==null)throw bad();final byte[] tab=id(originalTab.id(),"tab_"),workspace=id(parent.id(),"ws_");ResourceBudgets.Allocation credit=held.tryReserve(MAX_RECORD_BYTES);if(credit==null)throw new IllegalStateException("Prior native checkpoint bytes still owned");byte[] state=null;
        try{PrivateRecordStore.Reference reference=records.reference(PrivateRecordSealer.Purpose.GECKO_CHECKPOINT,tab,permit);try(PrivateRecordStore.ReadRecord record=records.read(reference,permit)){PrivateRecordStore.ReadStamp stamp=record.stamp();if(stamp.kind()==PrivateRecordStore.Kind.CURRENT)state=record.readOwnedPlaintext(bytes->decodeOpaque(bytes,workspace,tab));check(permit);originalNativeCatalog.metadata(permit);ReadView result=new ReadView(this,originalNativeCatalog,stamp.kind(),stamp.revision(),state,credit);state=null;credit=null;return result;}}
        finally{if(state!=null)Arrays.fill(state,(byte)0);if(credit!=null)credit.releaseAfterOwnedAllocationFreed();}
    }
    /** Wire validation only, not JSON/native SessionState validity or committed document proof. */
    static byte[] decodeOpaque(ByteBuffer borrowed,byte[] expectedWorkspace,byte[] expectedTab){if(borrowed==null || borrowed.remaining()>MAX_RECORD_BYTES || expectedWorkspace==null || expectedWorkspace.length!=16 || expectedTab==null || expectedTab.length!=16)throw bad();ByteBuffer b=borrowed.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);try{if(b.getLong()!=MAGIC || (b.getShort()&65535)!=1)throw bad();int n=b.getShort()&65535;if(n!=ENGINE.length() || n>b.remaining())throw bad();byte[] engine=new byte[n];b.get(engine);if(!ENGINE.equals(new String(engine,StandardCharsets.US_ASCII)))throw bad();byte[] ws=new byte[16],tab=new byte[16];b.get(ws);b.get(tab);if(!Arrays.equals(ws,expectedWorkspace) || !Arrays.equals(tab,expectedTab) || b.getLong()<1)throw bad();int length=b.getInt();if(length<2 || length!=b.remaining())throw bad();validateUtf8(b.asReadOnlyBuffer());byte[] state=new byte[length];b.get(state);return state;}catch(BufferUnderflowException malformed){throw bad();}}
    private static void validateUtf8(ByteBuffer input){CharsetDecoder decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);CharBuffer scratch=CharBuffer.allocate(1024);try{for(;;){CoderResult result=decoder.decode(input,scratch,true);scratch.clear();if(result.isError())throw bad();if(result.isUnderflow())break;}if(decoder.flush(scratch).isError())throw bad();}finally{Arrays.fill(scratch.array(),'\0');}}
    private void check(ComponentGate.BusinessPermit permit){source.requireBindings(gate,permit);records.requireBindings(gate,source.dataEpoch(),permit);browser.requireBindings(gate,permit);}
    private static byte[] id(String value,String prefix){if(value==null || !value.matches(prefix+"[0-9a-f]{32}"))throw bad();byte[] bytes=new byte[16];for(int i=0;i<16;i++)bytes[i]=(byte)Integer.parseInt(value.substring(prefix.length()+2*i,prefix.length()+2*i+2),16);return bytes;}
    private static IllegalArgumentException bad(){return new IllegalArgumentException("Native retained checkpoint metadata unavailable/malformed/unsupported");}
}
