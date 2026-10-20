package io.github.rebrowser.core;

import static org.junit.Assert.*;
import io.github.rebrowser.bootstrap.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;

/** Actual registry methods through PRIVATE SYNTHETIC window probe. No Android Activity/Window call. */
public final class TargetReferencesTest {
    private static final class Info {Object app,window=new Object();boolean destroyed,finishing;Runnable hook;Info(Object app){this.app=app;}}
    private static final class Fixture {
        final Object app=new Object();final byte[] epoch=new byte[16];final AtomicBoolean current=new AtomicBoolean(true);
        final AtomicReference<ComponentGate.SourceState> state=new AtomicReference<>(ComponentGate.SourceState.TRUSTED);
        final Object incarnation=new Object();final ComponentGate gate=ComponentGate.forNativeSource(ProcessRole.MAIN,()->state.get()==ComponentGate.SourceState.TRUSTED?ComponentGate.SourceView.acceptedNative(incarnation,new byte[16]):ComponentGate.SourceView.untrusted(state.get()));
        final IdentityHashMap<Object,Info> windows=new IdentityHashMap<>();final AtomicInteger probes=new AtomicInteger();
        final TargetReferences refs;final Method register,checkLease;
        Fixture(int capacity)throws Exception{
            Class<?> probe=Class.forName(TargetReferences.class.getName()+"$WindowProbe"),windowState=Class.forName(TargetReferences.class.getName()+"$WindowState");Constructor<?> stateCtor=windowState.getDeclaredConstructors()[0];stateCtor.setAccessible(true);
            Object fake=Proxy.newProxyInstance(probe.getClassLoader(),new Class<?>[]{probe},(proxy,method,args)->{assertEquals("inspect",method.getName());probes.incrementAndGet();Info info=windows.get(args[0]);if(info==null)throw new SecurityException("synthetic unknown window");if(info.hook!=null)info.hook.run();return stateCtor.newInstance(info.app,info.window,info.destroyed,info.finishing);});
            Constructor<TargetReferences> ctor=TargetReferences.class.getDeclaredConstructor(Object.class,ComponentGate.class,byte[].class,TargetReferences.CurrentEpoch.class,int.class,probe);assertTrue(Modifier.isPrivate(ctor.getModifiers()));ctor.setAccessible(true);refs=ctor.newInstance(app,gate,epoch,(TargetReferences.CurrentEpoch)e->{assertArrayEquals(new byte[16],e);e[0]=7;if(!current.get())throw new SecurityException("synthetic epoch revoked");},capacity,fake);
            register=TargetReferences.class.getDeclaredMethod("registerNative",Object.class,ComponentGate.BusinessPermit.class);register.setAccessible(true);checkLease=TargetReferences.class.getDeclaredMethod("checkLease",TargetReferences.WindowLease.class,TargetReferences.CurrentWindowScope.class,ComponentGate.BusinessPermit.class);checkLease.setAccessible(true);
        }
        Object window(){Object target=new Object();windows.put(target,new Info(app));return target;}
        TargetReferences.Registration register(Object target){return (TargetReferences.Registration)invoke(register,refs,target,gate.requireBusiness());}
        TargetReferences.WindowLease lease(TargetReferences.Registration r){return refs.resolveWindowAfterAuthorization(r.reference(),r.presentationRevision(),issued->assertSame(r,issued),gate.requireBusiness());}
        Object handoff(TargetReferences.WindowLease lease){return invoke(checkLease,refs,lease,(TargetReferences.CurrentWindowScope)issued->{},gate.requireBusiness());}
    }
    private static Object invoke(Method method,Object receiver,Object... args){try{return method.invoke(receiver,args);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException)throw (RuntimeException)e.getCause();if(e.getCause() instanceof Error)throw (Error)e.getCause();throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @Test public void issuedOpaqueWindowMatchesActionSchemaAndResolvesExactNativeObject()throws Exception{
        Fixture f=new Fixture(4);Object window=f.window();TargetReferences.Registration r=f.register(window);assertTrue(r.reference().matches("win_[0-9a-f]{32}"));assertEquals(0,r.presentationRevision());assertSame(window,f.handoff(f.lease(r)));assertTrue(f.probes.get()>=4);
    }
    @Test public void registryEpochIsDefensiveAndCannotBecomeCallerAuthority()throws Exception{
        Fixture f=new Fixture(2);f.epoch[0]=9;TargetReferences.Registration r=f.register(f.window());assertNotNull(f.handoff(f.lease(r)));
    }
    @Test public void lockedSourceAndForeignPermitRejectBeforeWindowInspection()throws Exception{
        Fixture f=new Fixture(2),other=new Fixture(2);Object window=f.window();assertThrows(SecurityException.class,()->invoke(f.register,f.refs,window,other.gate.requireBusiness()));ComponentGate.BusinessPermit permit=f.gate.requireBusiness();f.state.set(ComponentGate.SourceState.LOCKED);assertThrows(SecurityException.class,()->invoke(f.register,f.refs,window,permit));assertEquals(0,f.probes.get());
    }
    @Test public void requiresActualOwnApplicationLiveWindowNotCallerPackagePrivilege()throws Exception{
        for(int condition=0;condition<4;condition++){Fixture f=new Fixture(1);Object window=f.window();Info info=f.windows.get(window);if(condition==0)info.app=new Object();if(condition==1)info.window=null;if(condition==2)info.destroyed=true;if(condition==3)info.finishing=true;assertThrows(SecurityException.class,()->f.register(window));}
    }
    @Test public void duplicateLiveRegistrationAndTombstoneBudgetRejectWithoutEviction()throws Exception{
        Fixture f=new Fixture(1);Object window=f.window();TargetReferences.Registration r=f.register(window);assertThrows(IllegalStateException.class,()->f.register(window));f.refs.retire(r);assertThrows(IllegalStateException.class,()->f.register(f.window()));
    }
    @Test public void retiredReferenceNeverRetargetsEvenSamePhysicalNativeObject()throws Exception{
        Fixture f=new Fixture(3);Object window=f.window();TargetReferences.Registration old=f.register(window);f.refs.retire(old);TargetReferences.Registration replacement=f.register(window);assertNotEquals(old.reference(),replacement.reference());assertThrows(SecurityException.class,()->f.refs.resolveWindowAfterAuthorization(old.reference(),0,r->{},f.gate.requireBusiness()));assertSame(window,f.handoff(f.lease(replacement)));
    }
    @Test public void presentationRevisionInvalidatesBothOldResolutionAndAlreadyIssuedLease()throws Exception{
        Fixture f=new Fixture(2);Object window=f.window();TargetReferences.Registration r=f.register(window);TargetReferences.WindowLease lease=f.lease(r);f.refs.notePresentationChanged(r);assertThrows(SecurityException.class,()->f.handoff(lease));assertThrows(SecurityException.class,()->f.refs.resolveWindowAfterAuthorization(r.reference(),0,x->{},f.gate.requireBusiness()));assertSame(window,f.handoff(f.lease(r)));
    }
    @Test public void changedUnderlyingWindowCannotBeSilentlyReattachedToOldReference()throws Exception{
        Fixture f=new Fixture(1);Object window=f.window();TargetReferences.Registration r=f.register(window);TargetReferences.WindowLease lease=f.lease(r);f.windows.get(window).window=new Object();assertThrows(SecurityException.class,()->f.handoff(lease));
    }
    @Test public void nativeEpochRevocationAfterLeaseIssueBlocksActualActivityHandoff()throws Exception{
        Fixture f=new Fixture(1);TargetReferences.Registration r=f.register(f.window());TargetReferences.WindowLease lease=f.lease(r);f.current.set(false);assertThrows(SecurityException.class,()->f.handoff(lease));
    }
    @Test public void mandatoryCurrentNativeScopeCanRejectWithoutPublishingWindow()throws Exception{
        Fixture f=new Fixture(1);TargetReferences.Registration r=f.register(f.window());assertThrows(SecurityException.class,()->f.refs.resolveWindowAfterAuthorization(r.reference(),0,x->{throw new SecurityException("synthetic actor scope denied");},f.gate.requireBusiness()));assertThrows(IllegalArgumentException.class,()->f.refs.resolveWindowAfterAuthorization(r.reference(),0,null,f.gate.requireBusiness()));
    }
    @Test public void invalidationInsideNativeScopeCallbackDoesNotPublishStaleTarget()throws Exception{
        Fixture f=new Fixture(1);TargetReferences.Registration r=f.register(f.window());assertThrows(SecurityException.class,()->f.refs.resolveWindowAfterAuthorization(r.reference(),0,x->f.refs.notePresentationChanged(x),f.gate.requireBusiness()));assertEquals(1,r.presentationRevision());
    }
    @Test public void finalNativeProbeRevocationIsRecheckedNotOnlyCheckedAtLookup()throws Exception{
        Fixture f=new Fixture(1);Object window=f.window();TargetReferences.Registration r=f.register(window);TargetReferences.WindowLease lease=f.lease(r);AtomicInteger inspections=new AtomicInteger();f.windows.get(window).hook=()->{if(inspections.incrementAndGet()==2)f.current.set(false);};assertThrows(SecurityException.class,()->f.handoff(lease));assertEquals(2,inspections.get());
    }
    @Test public void foreignRegistrationOrLeaseCannotUseAnotherRegistryAuthority()throws Exception{
        Fixture a=new Fixture(1),b=new Fixture(1);TargetReferences.Registration r=a.register(a.window());TargetReferences.WindowLease lease=a.lease(r);assertThrows(SecurityException.class,()->b.refs.retire(r));assertThrows(SecurityException.class,()->b.handoff(lease));
    }
    @Test public void sourceLockedNativeOwnerCanRetireButCannotResolveOrDispatch()throws Exception{
        Fixture f=new Fixture(1);TargetReferences.Registration r=f.register(f.window());TargetReferences.WindowLease lease=f.lease(r);ComponentGate.BusinessPermit permit=f.gate.requireBusiness();f.state.set(ComponentGate.SourceState.LOCKED);f.refs.notePresentationChanged(r);f.refs.retire(r);assertThrows(SecurityException.class,()->invoke(f.checkLease,f.refs,lease,(TargetReferences.CurrentWindowScope)x->{},permit));
    }
    @Test public void reentrantPositiveResolutionRejectsInsteadOfBorrowingScopeCheck()throws Exception{
        Fixture f=new Fixture(1);TargetReferences.Registration r=f.register(f.window());assertThrows(SecurityException.class,()->f.refs.resolveWindowAfterAuthorization(r.reference(),0,x->f.lease(r),f.gate.requireBusiness()));assertNotNull(f.handoff(f.lease(r)));
    }
    @Test public void wrongThreadCannotResolveIssuedTarget()throws Exception{
        Fixture f=new Fixture(1);TargetReferences.Registration r=f.register(f.window());ExecutorService worker=Executors.newSingleThreadExecutor();try{Future<?> result=worker.submit(()->assertThrows(IllegalStateException.class,()->f.lease(r)));result.get(3,TimeUnit.SECONDS);}finally{worker.shutdown();assertTrue(worker.awaitTermination(3,TimeUnit.SECONDS));}
    }
    @Test public void weakNativeWindowLossDoesNotReopenOrInventAnActivity()throws Exception{
        Fixture f=new Fixture(1);TargetReferences.Registration r=f.register(f.window());TargetReferences.WindowLease lease=f.lease(r);Field slotField=TargetReferences.Registration.class.getDeclaredField("slot");slotField.setAccessible(true);Object slot=slotField.get(r);for(String name:new String[]{"activity","window"}){Field field=slot.getClass().getDeclaredField(name);assertEquals(WeakReference.class,field.getType());field.setAccessible(true);((WeakReference<?>)field.get(slot)).clear();}assertThrows(SecurityException.class,()->f.handoff(lease));
    }
    @Test public void freshPermitCannotRetargetOldWindowRegistryAfterObservedSourceLock()throws Exception{
        Fixture f=new Fixture(2);TargetReferences.Registration registration=f.register(f.window());f.state.set(ComponentGate.SourceState.LOCKED);f.gate.sourceState();f.state.set(ComponentGate.SourceState.TRUSTED);assertThrows(SecurityException.class,()->f.refs.resolveWindowAfterAuthorization(registration.reference(),0,x->{},f.gate.requireBusiness()));f.refs.retire(registration);
    }
    @Test public void malformedReferenceAndUnsafeRevisionNeverBecomeSearchOrScopeFallback()throws Exception{
        Fixture f=new Fixture(1);f.register(f.window());assertThrows(IllegalArgumentException.class,()->f.refs.resolveWindowAfterAuthorization("../window",0,r->{},f.gate.requireBusiness()));assertThrows(IllegalArgumentException.class,()->f.refs.resolveWindowAfterAuthorization("win_"+"0".repeat(32),9007199254740992L,r->{},f.gate.requireBusiness()));assertThrows(SecurityException.class,()->f.refs.resolveWindowAfterAuthorization("win_"+"0".repeat(32),0,r->{},f.gate.requireBusiness()));for(Constructor<?> c:TargetReferences.class.getConstructors())assertEquals(android.app.Application.class,c.getParameterTypes()[0]);
    }
}
