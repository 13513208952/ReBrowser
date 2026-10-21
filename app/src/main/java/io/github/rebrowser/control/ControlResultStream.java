package io.github.rebrowser.control;

import io.github.rebrowser.bootstrap.ComponentGate;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Bounded native PRIVATE result-reader. An object ID/job/signature/finished task is NOT viewer
 * authority: trusted ingress supplies current exact principal/result/target authorization fence,
 * checked before AND after reads. No raw file/URI from IPC, auto retry or native-work-end claim.
 * Not attached to a Provider until real current result-scope authorization exists. */
public final class ControlResultStream implements AutoCloseable {
    public interface CurrentAudience {void requireCurrentResultScope();}
    public final class Chunk implements AutoCloseable {
        private byte[] data;private final boolean eof;
        private Chunk(byte[] data,boolean eof){this.data=data;this.eof=eof;}
        public ByteBuffer borrowedReadOnly(){synchronized(ControlResultStream.this){synchronized(this){check();if(data==null)throw new IllegalStateException("Private result chunk closed");return ByteBuffer.wrap(data).asReadOnlyBuffer();}}}
        public boolean actualEofObserved(){return eof;}
        @Override public void close(){synchronized(ControlResultStream.this){synchronized(this){if(data==null)return;Arrays.fill(data,(byte)0);data=null;if(outstanding==this)outstanding=null;}}}
    }
    private final InputStream input;private final ComponentGate.DataEpochPin source;private final CurrentAudience audience;private final long maximum;private long bytes;private Chunk outstanding;private boolean eof,closed,failed,reading,checking;private volatile boolean viewerDetached;
    /** Takes ownership of already native-resolved input; max+1 sentinel may detect oversize. */
    public ControlResultStream(InputStream nativeOwnedPrivateInput,ComponentGate.DataEpochPin nativeSource,CurrentAudience nativeCurrentAudience,long admittedMaximumBytes){if(nativeOwnedPrivateInput==null || nativeSource==null || nativeCurrentAudience==null || admittedMaximumBytes<0 || admittedMaximumBytes>64L*1024*1024)throw new IllegalArgumentException("Native private result binding required");input=nativeOwnedPrivateInput;source=nativeSource;audience=nativeCurrentAudience;maximum=admittedMaximumBytes;try{check();}catch(RuntimeException rejected){closed=true;try{input.close();}catch(IOException|RuntimeException unknownClose){rejected.addSuppressed(unknownClose);}throw rejected;}}
    public synchronized Chunk nextOwnedChunk()throws IOException{
        check();if(reading || outstanding!=null || eof)throw new IllegalStateException("Prior private result bytes still owned or EOF");reading=true;byte[] buffer=new byte[(int)Math.min(32768L,maximum-bytes+1)];
        try{int n=input.read(buffer);if(n< -1 || n>buffer.length || n==0)throw new IOException("Private result input made invalid progress");check();if(n>maximum-bytes)throw new IOException("Private result exceeds native budget");if(n<0){eof=true;outstanding=new Chunk(new byte[0],true);}else{bytes+=n;outstanding=new Chunk(Arrays.copyOf(buffer,n),false);}return outstanding;}
        catch(IOException|RuntimeException unknown){failed=true;throw unknown;}finally{Arrays.fill(buffer,(byte)0);reading=false;}
    }
    public void detachViewer(){viewerDetached=true;} // Stops publication, not underlying IO/worker completion.
    public synchronized long bytesReturned(){return bytes;}
    @Override public synchronized void close()throws IOException{if(reading)throw new IllegalStateException("Private result read still owned");if(closed)return;closed=true;if(outstanding!=null)outstanding.close();input.close();} // Spend before actual close; unknown cannot retry another source.
    private void check(){if(closed || failed || viewerDetached || checking){failed=true;throw new SecurityException("Private result viewer retired");}checking=true;try{source.requireCurrent();audience.requireCurrentResultScope();source.requireCurrent();if(closed || failed || viewerDetached)throw new SecurityException("Private result audience changed during check");}catch(RuntimeException rejected){failed=true;throw rejected;}finally{checking=false;}}
}
