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
    /** One session-lifetime stream slot: token/port rotation never resets held byte ownership.
     * Producer retirement, REAL native-owner end and actual chunk disposal are separate axes. */
    public static final class Streams {
        private final Window bytes=new Window(CREDIT_WINDOW_BYTES,MAX_QUEUED_INGRESS_ENVELOPES);private StreamSlot live;
        synchronized StreamSlot tryOpen(){if(live!=null)return null;live=new StreamSlot(this);return live;}
        private synchronized void releaseIfEnded(StreamSlot slot){if(live==slot && slot.retired && slot.nativeEnd && bytes.objects()==0)live=null;}
        public synchronized boolean hasOwnedStream(){return live!=null;}
        public long ownedBytes(){return bytes.usedBytes();}
    }
    static final class StreamSlot {
        final Streams issuer;private boolean retired,nativeEnd;
        private StreamSlot(Streams issuer){this.issuer=issuer;}
        Credit reserve(int n){synchronized(issuer){if(retired || issuer.live!=this)throw new IllegalStateException("Bridge stream fenced");return issuer.bytes.tryReserve(n);}}
        void producerRetired(){synchronized(issuer){retired=true;issuer.releaseIfEnded(this);}}
        void nativeOwnerEnded(){synchronized(issuer){nativeEnd=true;issuer.releaseIfEnded(this);}}
        void bytesActuallyFreed(){issuer.releaseIfEnded(this);}
        long available(){return issuer.bytes.remaining();}
    }
    /** Our bounded String roots only; SDK's already-materialized/transient JSON memory is separate. */
    public static final class Ingress {
        private int objects;private long bytes;
        public synchronized Envelope retain(String text){
            if(text==null || text.length()>MAX_ENVELOPE_UTF8_BYTES)throw new Rejected(Rejection.ENVELOPE_TOO_LARGE);int count=0;
            for(int n=0;n<text.length();n++){char c=text.charAt(n);if(Character.isHighSurrogate(c)){if(++n>=text.length() || !Character.isLowSurrogate(text.charAt(n)))throw new IllegalArgumentException("Invalid UTF16 ingress");count+=4;}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Invalid UTF16 ingress");else count+=c<128?1:c<2048?2:3;if(count>MAX_ENVELOPE_UTF8_BYTES)throw new Rejected(Rejection.ENVELOPE_TOO_LARGE);}
            if(objects>=MAX_QUEUED_INGRESS_ENVELOPES || count>8L*MAX_ENVELOPE_UTF8_BYTES-bytes)throw new Rejected(Rejection.QUEUE_LIMIT);objects++;bytes+=count;return new Envelope(this,text,count);
        }
        public synchronized int objects(){return objects;}
        private synchronized void free(int size){objects--;bytes-=size;}
    }
    public static final class Envelope implements AutoCloseable {
        private final Ingress issuer;private String text;private final int size;
        private Envelope(Ingress issuer,String text,int size){this.issuer=issuer;this.text=text;this.size=size;}
        public synchronized String borrowed(){if(text==null)throw new IllegalStateException("Bridge ingress closed");return text;}
        @Override public synchronized void close(){if(text==null)return;text=null;issuer.free(size);}
    }
    private BridgeBudget(){}
}
