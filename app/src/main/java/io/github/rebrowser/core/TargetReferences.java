package io.github.rebrowser.core;

import android.app.Activity;
import android.app.Application;
import android.os.Looper;
import io.github.rebrowser.bootstrap.ComponentGate;
import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native WINDOW slice only. A handle/lease is NOT actor, scope, owner consent or dispatch authority. */
public final class TargetReferences {
    public interface CurrentEpoch { void checkCurrent(byte[] expectedNativeEpoch); }
    public interface CurrentWindowScope { void checkCurrent(Registration exactIssuedWindow); }
    private interface WindowProbe { WindowState inspect(Object nativeActivity); }
    private static final class WindowState {
        final Object application,window;final boolean destroyed,finishing;
        private WindowState(Object application,Object window,boolean destroyed,boolean finishing){this.application=application;this.window=window;this.destroyed=destroyed;this.finishing=finishing;}
    }
    private static final class AndroidWindowProbe implements WindowProbe {
        @Override public WindowState inspect(Object nativeActivity){
            if(Looper.myLooper()!=Looper.getMainLooper() || !(nativeActivity instanceof Activity))throw denied();
            Activity activity=(Activity)nativeActivity;
            return new WindowState(activity.getApplication(),activity.getWindow(),activity.isDestroyed(),activity.isFinishing());
        }
    }
    public static final class Registration {
        private final TargetReferences issuer;private final Slot slot;
        private Registration(TargetReferences issuer,Slot slot){this.issuer=issuer;this.slot=slot;}
        public String reference(){return slot.reference;}
        /** Native metadata only; cannot be supplied by caller as proof that window is current. */
        public long presentationRevision(){issuer.thread();return slot.revision;}
    }
    public static final class WindowLease {
        private final TargetReferences issuer;private final Slot slot;private final long revision;
        private WindowLease(TargetReferences issuer,Slot slot){this.issuer=issuer;this.slot=slot;revision=slot.revision;}
    }
    private static final class Slot {
        final String reference;final WeakReference<Object> activity,window;
        long revision;boolean retired;Registration registration;
        Slot(String reference,Object activity,Object window){this.reference=reference;this.activity=new WeakReference<>(activity);this.window=new WeakReference<>(window);}
    }
    private final Object application;private final ComponentGate gate;private final ComponentGate.ResourcePin sourceLifetime;private final CurrentEpoch epochGuard;
    private final byte[] epoch;private final int capacity;private final WindowProbe probe;
    private final Thread owner=Thread.currentThread();private final SecureRandom random=new SecureRandom();
    private final Map<String,Slot> issued=new LinkedHashMap<>();
    private boolean checking;
    public TargetReferences(Application ownApplication,ComponentGate gate,byte[] nativeEpoch,CurrentEpoch nativeCurrentEpoch,int capacity){
        this(ownApplication,gate,nativeEpoch,nativeCurrentEpoch,capacity,new AndroidWindowProbe());
        if(Looper.myLooper()!=Looper.getMainLooper())throw denied();
    }
    /** PRIVATE synthetic boundary for HOST tests only, no public alternate window provider. */
    private TargetReferences(Object application,ComponentGate gate,byte[] epoch,CurrentEpoch guard,int capacity,WindowProbe probe){
        if(application==null || gate==null || !gate.mayShowNativeShell() || epoch==null || epoch.length!=16 || guard==null || capacity<1 || capacity>64 || probe==null)throw invalid();
        this.application=application;this.gate=gate;sourceLifetime=gate.newResourcePin();this.epoch=epoch.clone();epochGuard=guard;this.capacity=capacity;this.probe=probe;
    }
    public Registration registerWindowAfterAuthorization(Activity nativeOwnedActivity,ComponentGate.BusinessPermit permit){return registerNative(nativeOwnedActivity,permit);}
    private Registration registerNative(Object nativeActivity,ComponentGate.BusinessPermit permit){
        access(permit);if(nativeActivity==null)throw invalid();
        WindowState state=inspect(nativeActivity);requireLive(state);
        for(Slot old:issued.values())if(!old.retired && old.activity.get()==nativeActivity)throw new IllegalStateException("Native window already registered");
        if(issued.size()>=capacity)throw new IllegalStateException("Native issued-window capacity reached");
        String reference=null;
        for(int attempt=0;attempt<16;attempt++){byte[] bytes=new byte[16];random.nextBytes(bytes);StringBuilder text=new StringBuilder("win_");for(byte b:bytes)text.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));if(!issued.containsKey(text.toString())){reference=text.toString();break;}}
        if(reference==null)throw new IllegalStateException("Native window identity collision");
        access(permit);WindowState again=inspect(nativeActivity);requireLive(again);if(again.window!=state.window)throw denied();access(permit);
        Slot slot=new Slot(reference,nativeActivity,state.window);slot.registration=new Registration(this,slot);issued.put(reference,slot);return slot.registration;
    }
    /** Native policy has authenticated the actor/action; callback must recheck exact current window scope. */
    public WindowLease resolveWindowAfterAuthorization(String untrustedReference,long expectedPresentationRevision,CurrentWindowScope nativeScope,ComponentGate.BusinessPermit permit){
        access(permit);
        if(untrustedReference==null || !untrustedReference.matches("win_[0-9a-f]{32}") || expectedPresentationRevision<0 || expectedPresentationRevision>9007199254740991L)throw invalid();
        Slot slot=issued.get(untrustedReference);if(slot==null)throw denied();
        resolveNative(slot,expectedPresentationRevision,nativeScope,permit);return new WindowLease(this,slot);
    }
    /** Repeat immediately before OWNED native UI dispatch; never keep raw Activity as lasting authority. */
    public Activity activityBeforeDispatch(WindowLease lease,CurrentWindowScope nativeScope,ComponentGate.BusinessPermit permit){return (Activity)checkLease(lease,nativeScope,permit);}
    private Object checkLease(WindowLease lease,CurrentWindowScope nativeScope,ComponentGate.BusinessPermit permit){
        if(lease==null || lease.issuer!=this)throw denied();return resolveNative(lease.slot,lease.revision,nativeScope,permit);
    }
    private Object resolveNative(Slot slot,long revision,CurrentWindowScope nativeScope,ComponentGate.BusinessPermit permit){
        access(permit);if(nativeScope==null)throw invalid();requireRevision(slot,revision);
        Object activity=slot.activity.get(),window=slot.window.get();if(activity==null || window==null)throw denied();
        WindowState state=inspect(activity);requireLive(state);if(state.window!=window)throw denied();
        checking=true;try{nativeScope.checkCurrent(slot.registration);}finally{checking=false;}
        access(permit);requireRevision(slot,revision);WindowState again=inspect(activity);requireLive(again);if(again.window!=window)throw denied();
        checking=true;try{nativeScope.checkCurrent(slot.registration);}finally{checking=false;}
        access(permit);requireRevision(slot,revision);return activity;
    }
    /** Native lifecycle invalidation only, not a positive action or actual-work-end report. */
    public void notePresentationChanged(Registration owned){
        thread();Slot slot=own(owned);if(slot.retired)throw denied();if(slot.revision==9007199254740991L){retire(owned);throw new IllegalStateException("Native window revision exhausted");}slot.revision++;
    }
    /** Source-locked old native UI may still retire its exact owned reference, never another issuer's. */
    public void retire(Registration owned){thread();Slot slot=own(owned);slot.retired=true;slot.activity.clear();slot.window.clear();}
    private Slot own(Registration registration){if(registration==null || registration.issuer!=this || issued.get(registration.slot.reference)!=registration.slot)throw denied();return registration.slot;}
    private void requireRevision(Slot slot,long revision){if(slot.retired || slot.revision!=revision || issued.get(slot.reference)!=slot)throw denied();}
    private WindowState inspect(Object activity){if(checking)throw denied();checking=true;try{return probe.inspect(activity);}finally{checking=false;}}
    private void requireLive(WindowState state){if(state==null || state.application!=application || state.window==null || state.destroyed || state.finishing)throw denied();}
    private void access(ComponentGate.BusinessPermit permit){
        thread();if(checking)throw denied();sourceLifetime.checkOrBind(permit);checking=true;try{epochGuard.checkCurrent(epoch.clone());}finally{checking=false;}sourceLifetime.checkOrBind(permit);
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Native window registry owner thread required");}
    private static SecurityException denied(){return new SecurityException("Native window target unavailable or stale");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native window reference binding");}
}
