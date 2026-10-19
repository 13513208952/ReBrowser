package io.github.rebrowser.core;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentCallbacks2;

/** Native allocation policy, not authority or a global/Gecko memory/OOM guarantee. */
public final class ResourceBudgets {
    public static final int STREAM_CHUNK_BYTES=32*1024;
    public static final int MAX_CONTROL_BYTES=64*1024;
    private static final long MIB=1024L*1024L;
    public enum Tier { CONSERVATIVE, NORMAL }
    public static final class Limits {
        private final Tier tier;
        private Limits(Tier tier){this.tier=tier;}
        public Tier tier(){return tier;}
        public int maxJobsPerNativeLane(){return tier==Tier.CONSERVATIVE?1:4;}
        public int maxCompressionWorkers(){return 1;}
        public long rebuildableMemoryBytes(){return tier==Tier.CONSERVATIVE?8*MIB:32*MIB;}
        public long maxDecodedArtworkPixels(){return tier==Tier.CONSERVATIVE?1024L*1024L:2048L*1024L;}
        public int maxRetainedRebuildableObjects(){return tier==Tier.CONSERVATIVE?32:128;}
        public ByteBudget newOwnedMemoryBudget(){return new ByteBudget(rebuildableMemoryBytes(),maxRetainedRebuildableObjects());}
    }
    /** Own native service only; never accept a caller-supplied memory-class/low-RAM grant. */
    public static Limits inspectOwnRuntime(Application application){
        if(application==null)throw invalid();
        try{
            ActivityManager manager=application.getSystemService(ActivityManager.class);
            if(manager==null)return observedLimits(0,true);
            return observedLimits(manager.getMemoryClass(),manager.isLowRamDevice());
        }catch(RuntimeException unavailable){return observedLimits(0,true);}
    }
    // Package-private SYNTHETIC observation boundary used by HOST tests, not an alternate OS provider.
    static Limits observedLimits(int memoryClassMiB,boolean lowRam){
        return new Limits(lowRam || memoryClassMiB<192?Tier.CONSERVATIVE:Tier.NORMAL);
    }
    public static final class Usage {
        private final long bytes;private final int objects;
        private Usage(long bytes,int objects){this.bytes=bytes;this.objects=objects;}
        public long bytes(){return bytes;}
        public int objects(){return objects;}
    }
    /** Only application-owned in-memory buffers/cache objects whose actual lifetime is tracked. */
    public static final class ByteBudget {
        private final long limit;private final int maxObjects;
        private long used;private int count;
        public ByteBudget(long nativeByteLimit,int nativeObjectLimit){
            if(nativeByteLimit<1 || nativeByteLimit>(1L<<40) || nativeObjectLimit<1 || nativeObjectLimit>4096)throw invalid();limit=nativeByteLimit;maxObjects=nativeObjectLimit;
        }
        /** Reserve BEFORE allocation; null = bounded rejection, never an eviction/retry instruction. */
        public synchronized Allocation tryReserve(long nativeBytes){
            if(nativeBytes<1)throw invalid();
            if(count>=maxObjects || nativeBytes>limit-used)return null;
            Allocation allocation=new Allocation(this,nativeBytes);used+=nativeBytes;count++;return allocation;
        }
        public synchronized Usage usage(){return new Usage(used,count);}
        private synchronized boolean release(Allocation allocation){
            if(allocation.released)return false;allocation.released=true;used-=allocation.bytes;count--;return true;
        }
    }
    /** Native allocation owner only. NEVER serialize/return token to administrator/JS/client code. */
    public static final class Allocation {
        private final ByteBudget issuer;private final long bytes;private boolean released;
        private Allocation(ByteBudget issuer,long bytes){this.issuer=issuer;this.bytes=bytes;}
        public long reservedBytes(){return bytes;}
        /** Trusted native owner has REALLY freed its buffer/cache reference; cancel != free. */
        public boolean releaseAfterOwnedAllocationFreed(){return issuer.release(this);}
    }
    public static final class TrimAdvice {
        private final boolean dropRebuildable,allowNewBackground;
        private TrimAdvice(boolean drop,boolean background){dropRebuildable=drop;allowNewBackground=background;}
        public boolean dropOnlyRebuildableMemoryCaches(){return dropRebuildable;}
        public boolean allowNewBackgroundAllocations(){return allowNewBackground;}
        /** Advice never grants permission to kill/clear currently owned foreground or media chains. */
        public boolean preserveOwnedForegroundAndMediaWork(){return true;}
    }
    /** Called with native onTrimMemory/low-memory state; does not free/close anything itself. */
    public static TrimAdvice trimAdvice(int nativeTrimLevel,boolean nativeLowMemory){
        boolean pressure=nativeLowMemory || nativeTrimLevel==ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE || nativeTrimLevel==ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || nativeTrimLevel==ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || nativeTrimLevel>=ComponentCallbacks2.TRIM_MEMORY_BACKGROUND;
        // UI_HIDDEN alone is not proof that service-owned music/media can stop.
        return new TrimAdvice(pressure,!pressure);
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native allocation budget");}
    private ResourceBudgets(){}
}
