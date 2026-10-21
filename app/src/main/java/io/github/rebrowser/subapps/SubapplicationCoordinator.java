package io.github.rebrowser.subapps;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.data.NativeRecordCodec;
import io.github.rebrowser.data.OwnedDataCatalog;
import io.github.rebrowser.settings.SettingsRepository;
import java.io.IOException;
import java.security.GeneralSecurityException;

/** Shared native desired-enablement/CAS + bounded synchronous handoff service.
 * Caller already owns domain authority and durable task/one-route admission; this is NOT Actor
 * authentication or durable external-effect idempotency. No installed opener/stopper, launch,
 * service creation, account operation or data cleanup occurs by constructing/reading this class.
 * Desired DISABLED is separate from actual work stop; callback return is not observed success.
 * A synchronous callback cannot retain its fence across Handler/worker handoff. */
public final class SubapplicationCoordinator {
    public enum Command { OPEN, REQUEST_STOP }
    public interface NativeCommand {
        /** Repeat fence immediately before owned native handoff. Never auto-retry another route. */
        void request(SubapplicationRegistry.Descriptor descriptor,Command command,Fence fence)throws Exception;
    }
    public static final class Fence {
        private final SubapplicationCoordinator issuer;private final OwnedDataCatalog.View<SettingsRepository.Snapshot> view;private final ComponentGate.BusinessPermit permit;private final Thread owner=Thread.currentThread();private boolean active=true,checking;
        private Fence(SubapplicationCoordinator issuer,OwnedDataCatalog.View<SettingsRepository.Snapshot> view,ComponentGate.BusinessPermit permit){this.issuer=issuer;this.view=view;this.permit=permit;}
        public void checkCurrent()throws IOException{if(!active || checking || Thread.currentThread()!=owner)throw new SecurityException("Retired native subapplication fence");checking=true;try{issuer.settings.requireExact(view,permit);}finally{checking=false;}}
    }
    private final SettingsRepository settings;
    public SubapplicationCoordinator(SettingsRepository canonicalNativeSettings){if(canonicalNativeSettings==null)throw NativeRecordCodec.bad();settings=canonicalNativeSettings;}
    public OwnedDataCatalog.View<SettingsRepository.Snapshot> readDesired(ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return settings.read(permit);}
    public long setDesiredEnabled(OwnedDataCatalog.View<SettingsRepository.Snapshot> expected,String id,boolean enabled,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{
        if(expected==null)throw NativeRecordCodec.bad();SubapplicationRegistry.require(id);SettingsRepository.Snapshot before=expected.value(permit);return settings.compareAndWrite(expected,before.withEnabled(id,enabled),permit);
    }
    public void request(OwnedDataCatalog.View<SettingsRepository.Snapshot> expected,String id,Command command,NativeCommand nativeOwner,ComponentGate.BusinessPermit permit)throws Exception{
        if(expected==null || command==null || nativeOwner==null)throw NativeRecordCodec.bad();SubapplicationRegistry.Descriptor descriptor=SubapplicationRegistry.require(id);SettingsRepository.Snapshot state=expected.value(permit);
        if(command==Command.OPEN && !state.desiredEnabled(id))throw new SecurityException("Native subapplication disabled");
        settings.beginNativeHandoff(expected,permit); // spend BEFORE callback, including unknown failure
        Fence fence=new Fence(this,expected,permit);
        try{fence.checkCurrent();nativeOwner.request(descriptor,command,fence);fence.checkCurrent();}
        finally{fence.active=false;} // No actual-open/stop/end inference or automatic resubmission.
    }
}
