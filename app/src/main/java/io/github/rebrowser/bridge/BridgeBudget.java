package io.github.rebrowser.bridge;

/**
 * Bounded credit accounting for the connector protocol (64 KiB envelope, 32 KiB raw chunk,
 * 64 KiB credit window) mirroring the native resource-budget discipline. Credit is NOT authority,
 * not durable commit and not stream completion: only the actual owner may release after the real
 * consumer freed its bytes. A timeout, Future.done or receiver return never releases credit here.
 */
public final class BridgeBudget {
    public static final int MAX_ENVELOPE_UTF8_BYTES = 65_536;
    public static final int MAX_STREAM_CHUNK_BYTES = 32_768;
    public static final int CREDIT_WINDOW_BYTES = 65_536;
    public static final int MAX_OPEN_STREAMS_PER_DOCUMENT = 1;
    public static final int MAX_QUEUED_INGRESS_ENVELOPES = 8;

    public enum Rejection { ENVELOPE_TOO_LARGE, CHUNK_TOO_LARGE, CREDIT_TOO_SMALL, CREDIT_ABOVE_WINDOW, SEQUENCE_GAP, NO_CREDIT, STREAM_LIMIT, QUEUE_LIMIT, ZERO_PROGRESS }

    public static final class EnvelopeSize {
        private final int utf8Bytes;private EnvelopeSize(int bytes){utf8Bytes=bytes;}
        public int utf8Bytes(){return utf8Bytes;}
    }

    /** Strict envelope limit check on the encoded byte count (never on a character count). */
    public static EnvelopeSize envelope(int utf8Bytes){
        if(utf8Bytes<0)throw new IllegalArgumentException("Negative envelope size");
        if(utf8Bytes>MAX_ENVELOPE_UTF8_BYTES)throw new Rejected(Rejection.ENVELOPE_TOO_LARGE);
        return new EnvelopeSize(utf8Bytes);
    }
    public static int chunk(int rawBytes){
        if(rawBytes<0)throw new IllegalArgumentException("Negative chunk size");
        if(rawBytes==0)throw new Rejected(Rejection.ZERO_PROGRESS);
        if(rawBytes>MAX_STREAM_CHUNK_BYTES)throw new Rejected(Rejection.CHUNK_TOO_LARGE);
        return rawBytes;
    }

    public static final class Rejected extends IllegalArgumentException {
        private final Rejection rejection;
        Rejected(Rejection rejection){super(rejection.name());this.rejection=rejection;}
        public Rejection rejection(){return rejection;}
    }

    /** Byte+object bounded credit with one-use opaque reservations. */
    public static final class Window {
        private final long limit;private final int maxObjects;private long used;private int objects;
        public Window(long windowBytes,int maxObjects){
            if(windowBytes<1 || windowBytes>CREDIT_WINDOW_BYTES || maxObjects<1 || maxObjects>MAX_QUEUED_INGRESS_ENVELOPES)throw new IllegalArgumentException("Invalid credit window");
            limit=windowBytes;this.maxObjects=maxObjects;
        }
        public synchronized Credit tryReserve(long bytes){
            if(bytes<1)throw new IllegalArgumentException("Non-positive credit");
            if(bytes>CREDIT_WINDOW_BYTES)throw new Rejected(Rejection.CREDIT_ABOVE_WINDOW);
            if(objects>=maxObjects || bytes>limit-used)return null;
            used+=bytes;objects++;return new Credit(this,bytes);
        }
        public synchronized void assertAvailable(long bytes){
            if(bytes<1 || bytes>CREDIT_WINDOW_BYTES)throw new Rejected(Rejection.CREDIT_ABOVE_WINDOW);
            if(bytes>limit-used)throw new Rejected(Rejection.NO_CREDIT);
        }
        public synchronized int objects(){return objects;}
        public synchronized long usedBytes(){return used;}
        public synchronized long remaining(){return limit-used;}
        private synchronized boolean release(Credit credit){
            if(credit.released)return false;
            credit.released=true;used-=credit.bytes;objects--;return true;
        }
    }
    /** Opaque native-owner only; never serialized to a connector or administrator. */
    public static final class Credit {
        private final Window issuer;private final long bytes;private volatile boolean released;
        private Credit(Window issuer,long bytes){this.issuer=issuer;this.bytes=bytes;}
        public long bytes(){return bytes;}
        public boolean releaseAfterConsumerFreedBytes(){return issuer.release(this);}
        public boolean isReleased(){return released;}
    }

    /** Exact contiguous sequence check for one stream; duplicates need recorded digest elsewhere. */
    public static void requireSequence(long expected,long actual){
        if(expected<0 || actual<0)throw new IllegalArgumentException("Negative stream sequence");
        if(expected!=actual)throw new Rejected(Rejection.SEQUENCE_GAP);
    }
    private BridgeBudget(){}
}
