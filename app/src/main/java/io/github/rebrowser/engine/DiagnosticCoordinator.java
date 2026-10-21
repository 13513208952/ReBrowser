package io.github.rebrowser.engine;

import io.github.rebrowser.bootstrap.ComponentGate;
import java.nio.ByteBuffer;

/** Native session-OBSERVATION diagnostics only: no eval/script/selector, content capture, URL
 * refetch, cookie/permission queries, native trace/logcat or alleged committed document/effect.
 * Trusted ingress supplies actual current viewer + registered exact-target policy. Parsed proof,
 * public observation counters or caller package cannot supply that authority. Not an IPC route. */
public final class DiagnosticCoordinator {
    public interface CurrentNativeViewer {void requireExactTargetRead(GeckoSessionOwner.SessionRef actualNativeTarget);}
    public final class Observation {
        private final GeckoSessionOwner.SessionRef target;private final SessionPresentation.Frame frame;
        private Observation(GeckoSessionOwner.SessionRef target,SessionPresentation.Frame frame){this.target=target;this.frame=frame;}
        /** Optional private display fields; never written to diagnostic/system logs. */
        public String borrowedReportedTitle(){require(target);return frame.reportedTitle();}
        public String borrowedReportedLocation(){require(target);return frame.reportedLocation();}
        public long observationSequence(){require(target);return frame.observationSequence();}
        /** Fixed 30-byte redacted native metadata, no URL/title/principal/account/lease tokens.
         * Native caller owns any copy; bytes are not signatures, latest state or task-end proof. */
        public byte[] redactedSessionMetadata(){require(target);ByteBuffer b=ByteBuffer.allocate(30);b.putInt(0x52424431).putLong(frame.observationSequence()).putInt(frame.reportedProgress()).putInt(frame.reportedSecurityMode()).put((byte)(frame.reportedSecure()?1:0)).put((byte)(frame.reportedCertificateException()?1:0)).put((byte)(frame.loadingObserved()?1:0)).put((byte)(frame.reportedCanGoBack()?1:0)).put((byte)(frame.reportedCanGoForward()?1:0)).put((byte)(frame.closeWasRequested()?1:0)).put((byte)(frame.reportedFullScreen()?1:0)).put((byte)(frame.reportedStopSuccess()==null?0:frame.reportedStopSuccess()?1:2)).putShort((short)0);require(target);return b.array();}
    }
    private final ComponentGate gate;private final ComponentGate.BusinessPermit permit;private final ComponentGate.DataEpochPin source;private final GeckoSessionOwner sessions;private final CurrentNativeViewer viewer;private final Thread owner=Thread.currentThread();private boolean checking,retired;
    public DiagnosticCoordinator(ComponentGate actualGate,ComponentGate.BusinessPermit permit,GeckoSessionOwner canonicalSessionOwner,CurrentNativeViewer actualCurrentNativeViewer){if(actualGate==null || canonicalSessionOwner==null || actualCurrentNativeViewer==null)throw new IllegalArgumentException("Native diagnostic source/target/viewer required");gate=actualGate;this.permit=permit;source=gate.captureDataEpoch(permit);sessions=canonicalSessionOwner;viewer=actualCurrentNativeViewer;}
    public Observation captureNativeSessionObservation(GeckoSessionOwner.SessionRef actualRegisteredTarget){require(actualRegisteredTarget);SessionPresentation.Frame frame=sessions.presentation(actualRegisteredTarget,permit);if(frame.retired())throw new SecurityException("Native session presentation retired");require(actualRegisteredTarget);return new Observation(actualRegisteredTarget,frame);}
    public void retirePublication(){owner();retired=true;} // Observation retirement never cancels native Gecko work.
    private void require(GeckoSessionOwner.SessionRef target){owner();if(retired || checking || target==null)throw new SecurityException("Native diagnostic target/viewer retired");checking=true;try{source.requireBindings(gate,permit);sessions.requireOpen(target,permit);viewer.requireExactTargetRead(target);source.requireBindings(gate,permit);sessions.requireOpen(target,permit);if(retired)throw new SecurityException("Native diagnostic scope changed");}catch(RuntimeException rejected){retired=true;throw rejected;}finally{checking=false;}}
    private void owner(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Native diagnostic owner thread required");}
}
