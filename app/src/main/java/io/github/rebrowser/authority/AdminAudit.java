package io.github.rebrowser.authority;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.control.ActionSchemas;
import java.util.*;
import java.util.function.LongSupplier;

/** Bounded native audit of LOCAL observations only, not execution/effect/durability receipts.
 * Deliberately no key IDs, device names, handles, args, URLs, signatures or raw error strings.
 * No logcat/network/file sink and no public IPC enumeration route. */
public final class AdminAudit {
    public enum Outcome { RESERVATION_RETURNED, REJECTED_BEFORE_RESERVATION, RESERVATION_OUTCOME_UNKNOWN }
    public static final class Entry {
        private final long sequence,time;private final ActionSchemas.Kind action;private final Outcome outcome;
        private Entry(long sequence,long time,ActionSchemas.Kind action,Outcome outcome){this.sequence=sequence;this.time=time;this.action=action;this.outcome=outcome;}
        public long sequence(){return sequence;}public long elapsedMs(){return time;}public ActionSchemas.Kind action(){return action;}public Outcome outcome(){return outcome;}
    }
    private final ComponentGate.DataEpochPin source;private final LongSupplier clock;private final Entry[] ring;private int count,next;private long sequence,last=-1;private boolean closed;
    public AdminAudit(ComponentGate.DataEpochPin nativeSource,LongSupplier elapsedRealtime,int capacity){if(nativeSource==null || elapsedRealtime==null || capacity<1 || capacity>256)throw new IllegalArgumentException("Invalid native audit bounds");source=nativeSource;clock=elapsedRealtime;ring=new Entry[capacity];source.requireCurrent();}
    public synchronized void observe(ActionSchemas.Kind action,Outcome outcome){
        if(action==null || outcome==null)throw new IllegalArgumentException("Typed audit observation required");check();if(sequence==Long.MAX_VALUE){close();throw denied();}Entry e=new Entry(++sequence,last,action,outcome);source.requireCurrent();ring[next]=e;next=(next+1)%ring.length;count=Math.min(count+1,ring.length);
    }
    /** Trusted native owner only. A future remote route needs its OWN fresh result-scope authority. */
    public synchronized List<Entry> readForNativeOwner(){check();List<Entry> copy=new ArrayList<>(count);for(int i=0;i<count;i++)copy.add(ring[(next-count+i+ring.length)%ring.length]);source.requireCurrent();return Collections.unmodifiableList(copy);}
    public synchronized void close(){closed=true;Arrays.fill(ring,null);count=next=0;}
    private void check(){source.requireCurrent();long now;try{now=clock.getAsLong();}catch(RuntimeException e){close();throw denied();}if(closed || now<0 || now<last){close();throw denied();}last=now;}
    private static SecurityException denied(){return new SecurityException("Native audit unavailable");}
}
