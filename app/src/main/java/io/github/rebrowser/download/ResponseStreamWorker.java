package io.github.rebrowser.download;

import io.github.rebrowser.core.Cancellation;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Synchronous OWNED stream work, to run on a bounded native IO lane, NEVER Gecko/UI thread.
 * Output is an unpublished private stage only: no public URI, filesystem path, publish,
 * fsync, DB commit, remote-effect claim or task-slot release. Any failure taints the stage.
 * Both non-null streams transfer ownership at entry and close even on invalid arguments,
 * read/write/cancel/flush/close failure. A result exists only after both close calls return.
 * Cancellation/interrupt does NOT prove a blocked reader ended. Caller keeps actual work
 * ownership until this whole chain finishes, including close; close failure stays explicit.
 *
 * Bulk reads ONLY (also EOF probe): pinned GeckoInputStream.read() reads four bytes as an
 * int, and read(byte[],off,len) ignores interrupt while waiting. Do not use read() fallback,
 * available(), Future.done or a client timeout as exact EOF/termination evidence.
 */
public final class ResponseStreamWorker {
    public static final int BUFFER_BYTES=32_768;
    public static final long MAX_COPY_BYTES=1L<<40;
    private static final int MAX_ZERO_READS=16;
    public static final class CopiedStage {
        private final long bytes;
        private final byte[] digest;
        private CopiedStage(long bytes,byte[] digest){this.bytes=bytes;this.digest=digest;}
        public long byteCount(){return bytes;}
        public byte[] originalSha256(){return digest.clone();}
        // No "published", "durable", "quiescent" or whole-domain work-ended flag.
    }
    private ResponseStreamWorker(){}
    public static CopiedStage copyOwned(InputStream input,OutputStream privateStage,long maximumBytes,long expectedBytes,Cancellation.Requester cancellation)throws IOException{
        CopiedStage copied;
        try(InputStream source=input;OutputStream stage=privateStage){
            if(source==null || stage==null || cancellation==null || maximumBytes<0 || maximumBytes>MAX_COPY_BYTES || expectedBytes< -1 || expectedBytes>maximumBytes)throw new IllegalArgumentException("Invalid owned response copy limits");
            MessageDigest digest=sha256();byte[] buffer=new byte[BUFFER_BYTES];long total=0;int zeros=0;
            while(true){
                checkCancellation(cancellation);
                long remaining=maximumBytes-total;if(expectedBytes>=0)remaining=Math.min(remaining,expectedBytes-total);
                int requested=(int)Math.min(BUFFER_BYTES,remaining+1); // one bulk sentinel at exact length/quota, NEVER InputStream.read()
                int read=source.read(buffer,0,requested);
                checkCancellation(cancellation); // ignored interrupt/blocking read is not a write permission
                if(read== -1)break;
                if(read< -1 || read>requested)throw new IOException("Invalid response stream read count");
                if(read==0){if(++zeros>MAX_ZERO_READS)throw new IOException("Response stream made no progress");continue;}
                zeros=0;
                if(read>maximumBytes-total || (expectedBytes>=0 && read>expectedBytes-total))throw new IOException("Response exceeded native byte limit");
                stage.write(buffer,0,read);digest.update(buffer,0,read);total+=read;
            }
            if(expectedBytes>=0 && total!=expectedBytes)throw new IOException("Response length mismatch");
            checkCancellation(cancellation);stage.flush();checkCancellation(cancellation);
            copied=new CopiedStage(total,digest.digest());
        } // no successful return on input/output close failure; try-with-resources preserves suppressed errors
        return copied;
    }
    /** Rejected/ignored response still owns a body. No reading/draining/cookie re-fetch fallback. */
    public static void discardOwned(InputStream input)throws IOException{try(InputStream ignored=input){/* close even without reading; native IO owner accounts actual end */}}
    private static void checkCancellation(Cancellation.Requester requester)throws InterruptedIOException{
        if(requester.snapshot().cancellationRequested() || Thread.currentThread().isInterrupted())throw new InterruptedIOException("Owned response copy cancelled");
    }
    private static MessageDigest sha256(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException unavailable){throw new IllegalStateException("SHA-256 unavailable");}}
}
