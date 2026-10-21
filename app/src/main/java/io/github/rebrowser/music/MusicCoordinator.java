package io.github.rebrowser.music;

import android.app.Application;
import android.os.Looper;
import android.os.SystemClock;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bridge.*;
import io.github.rebrowser.core.TaskCoordinator;
import io.github.rebrowser.engine.GeckoRuntimeOwner;

/** Canonical native music observation/session owner shared by UI and bound service. Constructs
 * no runtime/page/focus/notification and never plays at startup. Explicitly installs fixed built-in
 * once, routes only committed native observation tasks and keeps source/document-bound claims.
 * No play/seek/login/backend is fabricated from a page result, focus or Java callback return.
 * Actual playback/rights/foreground-service and Android/Gecko integration remain separate gates. */
public final class MusicCoordinator {
    public interface ObservationListener {void pageClaimAvailable();}
    private final ComponentGate gate;private final ComponentGate.BusinessPermit permit;private final ComponentGate.DataEpochPin source;private final GeckoRuntimeOwner runtime;private final MusicSessionOwner sessions;private final Application application;
    private BuiltinConnectorManager manager;private ConnectorBroker broker;private boolean installAttempted,retired;private DocumentLease.Received originalObservation;private MusicConnector.Observation observation;private ObservationListener observer;
    public MusicCoordinator(Application nativeOwnApplication,ComponentGate gate,ComponentGate.BusinessPermit permit,GeckoRuntimeOwner canonicalNativeRuntime,MusicSessionOwner canonicalMusicSessions){if(nativeOwnApplication==null || gate==null || canonicalNativeRuntime==null || canonicalMusicSessions==null)throw new IllegalArgumentException("Canonical native music owners required");main();this.gate=gate;this.permit=permit;source=gate.captureDataEpoch(permit);runtime=canonicalNativeRuntime;sessions=canonicalMusicSessions;application=nativeOwnApplication;runtime.requireNativeBindings(application,gate,source);}
    public void openOfficialEntry(TaskCoordinator.Owner admittedNativeNavigationTask){check();sessions.openOfficialEntry(admittedNativeNavigationTask,permit);}
    /** This explicit native action may open the native session/runtime; never invoked by a page. */
    public void installObservationRouteOnce(ObservationListener nativeOwnedObserver){check();if(installAttempted || nativeOwnedObserver==null)throw new IllegalStateException("Native music connector setup already attempted");installAttempted=true;observer=nativeOwnedObserver;manager=runtime.builtinConnectors(permit);manager.awaitOrEnsureNativeStatusOnce(status->{if(retired || status!=BuiltinConnectorManager.Status.READY)return;try{check();broker=new ConnectorBroker(sessions.bridgeContext(permit),manager.requireInstalled(),this::received);}catch(RuntimeException unknown){retired=true;clearClaims();}});}
    private void received(ConnectorBroker.Request request,DocumentLease.Received received){check();if(request==null || received==null || received.message().kind()!=BridgeMessages.Kind.RESULT)return;request.requireCurrentNativePublication();MusicConnector.Observation parsed=broker.readMusicPageClaims(received,SystemClock.elapsedRealtime());request.requireCurrentNativePublication();check();originalObservation=received;observation=parsed;try{observer.pageClaimAvailable();}catch(RuntimeException ignored){/* observer return/exception never settles native work */}}
    public ConnectorBroker.Request requestReadOnlyObservation(TaskCoordinator.Owner admittedNativeTask){check();if(broker==null)throw new IllegalStateException("Native music route not ready");return broker.observe(admittedNativeTask);}
    /** A cached page observation at its stated native time, not current account/playback authority. */
    public MusicConnector.Observation lastPageObservation(){check();if(originalObservation==null)return null;originalObservation.message();check();return observation;}
    public void releaseAfterActualNativeOwnerEnded(ConnectorBroker.Request request){main();if(broker==null)throw new IllegalStateException("Native music broker absent");broker.releaseAfterNativeOwnerEnded(request);}
    public void retirePublication(){main();retired=true;clearClaims();if(broker!=null)broker.retire();observer=null;} // Does NOT close/end player, request or focus ownership.
    public void requestOwnedSessionClose(){main();retirePublication();sessions.requestOwnedSessionClose();} // Observed window close != native media/task-chain end.
    private void clearClaims(){originalObservation=null;observation=null;}
    private void check(){main();if(retired)throw new SecurityException("Native music coordinator retired");source.requireBindings(gate,permit);runtime.requireNativeBindings(application,gate,source);}
    private static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native music domain UI owner required");}
}
