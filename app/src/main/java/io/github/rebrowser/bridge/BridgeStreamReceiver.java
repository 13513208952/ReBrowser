package io.github.rebrowser.bridge;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Base64;

/** Exact bound stream -> bounded OWNED chunks. No file path/output publication, whole-body
 * allocation, auto-fetch, cursor commit, result authority or cancellation-as-work-end.
 * Base64/SDK transient allocations are separate from our 64KiB retained raw-byte window.
 * Stream slot survives producer/port retirement until real native-owner end AND chunk disposal. */
public final class BridgeStreamReceiver implements AutoCloseable {
    public enum State { RECEIVING, END_OBSERVED, FAILED, CLOSED }
    public static final class OwnedChunk implements AutoCloseable {
        private byte[] bytes;private BridgeBudget.Credit credit;private final BridgeBudget.StreamSlot slot;
        private OwnedChunk(byte[] bytes,BridgeBudget.Credit credit,BridgeBudget.StreamSlot slot){this.bytes=bytes;this.credit=credit;this.slot=slot;}
        /** Borrowed immutable view; retaining it does not transfer ownership/authority. */
        public synchronized ByteBuffer borrowedReadOnly(){if(bytes==null)throw new IllegalStateException("Bridge chunk closed");return ByteBuffer.wrap(bytes).asReadOnlyBuffer();}
        @Override public synchronized void close(){if(bytes==null)return;java.util.Arrays.fill(bytes,(byte)0);bytes=null;credit.releaseAfterConsumerFreedBytes();credit=null;slot.bytesActuallyFreed();}
    }
    public static final class EndObservation {
        private final long bytes;private final String sha;
        private EndObservation(long bytes,String sha){this.bytes=bytes;this.sha=sha;}
        public long receivedBytes(){return bytes;}public String sha256(){return sha;}
        // Integrity only. It is NOT actual consumer completion/native end/domain persistence.
    }
    private final DocumentLease lease;private final String requestId,streamId,purpose;private final long declared,maximum;private final BridgeBudget.StreamSlot slot;private final MessageDigest digest;private final ArrayDeque<OwnedChunk> queue=new ArrayDeque<>();private final Thread thread=Thread.currentThread();
    private State state=State.RECEIVING;private long count,sequence,allowance;
    BridgeStreamReceiver(DocumentLease lease,DocumentLease.Received begin,String expectedRequestId,String admittedPurpose,long nativeMaximum,BridgeBudget.Streams nativeSessionStreams){
        if(lease==null || begin==null || expectedRequestId==null || !expectedRequestId.matches("r_[0-9a-f]{32}") || admittedPurpose==null || nativeMaximum<0 || nativeMaximum>64L*1024*1024 || nativeSessionStreams==null)throw invalid();begin.requireLease(lease);BridgeMessages.StreamBegin b=begin.message().streamBegin();if(!expectedRequestId.equals(b.requestId()) || !admittedPurpose.equals(b.purpose()) || b.declaredBytes()>nativeMaximum)throw invalid();
        this.lease=lease;requestId=expectedRequestId;streamId=b.streamId();purpose=admittedPurpose;declared=b.declaredBytes();maximum=nativeMaximum;try{digest=MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}slot=nativeSessionStreams.tryOpen();if(slot==null)throw new IllegalStateException("Native session still owns a bridge stream");
    }
    /** Spend advertised allowance BEFORE native post. Unknown post never permits a retry grant. */
    String creditBeforePost(int bytes){current();if(state!=State.RECEIVING || bytes<1 || bytes>BridgeBudget.CREDIT_WINDOW_BYTES || bytes>slot.available()-allowance || bytes>declared-count-allowance)throw invalid();allowance+=bytes;return BridgeMessages.nativeCredit(lease.nativeBinding(),streamId,sequence,bytes,maximum);}
    public void acceptChunk(DocumentLease.Received incoming){
        current();if(state!=State.RECEIVING || incoming==null)throw invalid();incoming.requireLease(lease);BridgeMessages.StreamChunk chunk=incoming.message().streamChunk();
        if(!streamId.equals(chunk.streamId()) || chunk.sequence()!=sequence || sequence==BridgeMessages.MAX_SAFE_INTEGER || chunk.rawBytes()>allowance || chunk.rawBytes()>declared-count || queue.size()>=BridgeBudget.MAX_QUEUED_INGRESS_ENVELOPES){fail();throw invalid();}
        BridgeBudget.Credit credit=slot.reserve((int)chunk.rawBytes());if(credit==null){fail();throw new IllegalStateException("Bridge consumer still owns byte credit");}byte[] bytes=null;
        try{bytes=Base64.getDecoder().decode(chunk.base64());if(bytes.length!=chunk.rawBytes())throw invalid();OwnedChunk owned=new OwnedChunk(bytes,credit,slot);digest.update(bytes);count+=bytes.length;allowance-=bytes.length;sequence++;queue.add(owned);bytes=null;credit=null;}
        catch(RuntimeException invalid){fail();throw invalid;}finally{if(bytes!=null)java.util.Arrays.fill(bytes,(byte)0);if(credit!=null)credit.releaseAfterConsumerFreedBytes();}
    }
    public OwnedChunk pollOwned(){current();if(state==State.CLOSED || state==State.FAILED)throw invalid();return queue.poll();} // ownership transfers; return is NOT credit release.
    public EndObservation observeEnd(DocumentLease.Received incoming){
        current();if(state!=State.RECEIVING || incoming==null)throw invalid();incoming.requireLease(lease);BridgeMessages.StreamEnd end=incoming.message().streamEnd();String actual=DocumentLease.hex(digest.digest());
        if(!streamId.equals(end.streamId()) || end.totalBytes()!=count || count!=declared || !MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII),end.sha256().getBytes(java.nio.charset.StandardCharsets.US_ASCII))){fail();throw invalid();}
        state=State.END_OBSERVED;slot.producerRetired();return new EndObservation(count,actual);
    }
    public State state(){owner();return state;}public String streamId(){return streamId;}public String purpose(){return purpose;}public String requestId(){return requestId;}
    /** Private native task owner only. Broker checks actual TaskCoordinator owner-end first. */
    void nativeOwnerEnded(){owner();slot.nativeOwnerEnded();}
    @Override public void close(){owner();if(state==State.CLOSED)return;state=State.CLOSED;allowance=0;slot.producerRetired();OwnedChunk c;while((c=queue.poll())!=null)c.close();}
    private void fail(){state=State.FAILED;allowance=0;slot.producerRetired();}
    private void current(){owner();lease.requireCurrent();}
    private void owner(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Native stream ingress thread required");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid bound bridge stream");}
}
