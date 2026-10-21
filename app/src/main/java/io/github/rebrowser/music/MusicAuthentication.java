package io.github.rebrowser.music;

import io.github.rebrowser.bridge.DocumentLease;
import io.github.rebrowser.bridge.MusicConnector;
import io.github.rebrowser.core.TaskCoordinator;
import java.util.function.LongSupplier;

/** Fresh music account-check correlation. Account DTOs stay REPORTED hints, never grant playback
 * or assert a verified official login. No UA spoof/login/network retry, and timeout/focus/port
 * loss is UNKNOWN rather than logout or underlying native execution completion. */
public final class MusicAuthentication {
    public enum State { CHECK_REQUESTED, REPORTED_HINT, WAIT_EXPIRED, RETIRED }
    public static final class Check {
        private final MusicAuthentication issuer;private final DocumentLease lease;private final TaskCoordinator.Owner owner;private final String request;private final long deadline;private State state=State.CHECK_REQUESTED;private boolean consumed;private MusicConnector.Observation hint;
        private Check(MusicAuthentication issuer,DocumentLease lease,TaskCoordinator.Owner owner,String request,long deadline){this.issuer=issuer;this.lease=lease;this.owner=owner;this.request=request;this.deadline=deadline;}
    }
    private final LongSupplier clock;private final Thread thread=Thread.currentThread();private Check active;private long last=-1;private boolean retired;
    public MusicAuthentication(LongSupplier nativeElapsedRealtime){if(nativeElapsedRealtime==null)throw new IllegalArgumentException("Native music check clock required");clock=nativeElapsedRealtime;}
    public Check begin(DocumentLease actualLease,TaskCoordinator.Owner nativeRegisteredOwner,String nativeRequestId){owner();long now=now();if(retired || active!=null || actualLease==null || nativeRegisteredOwner==null || !nativeRegisteredOwner.mayPublishCurrentResult() || nativeRequestId==null || !nativeRequestId.matches("r_[0-9a-f]{32}") || now>Long.MAX_VALUE-30_000)throw denied();actualLease.requireNativeTaskSource(nativeRegisteredOwner);active=new Check(this,actualLease,nativeRegisteredOwner,nativeRequestId,now+30_000);return active;}
    public MusicConnector.Observation acceptReportedHint(Check check,DocumentLease.Received received){current(check);if(check.consumed || check.state!=State.CHECK_REQUESTED || now()>=check.deadline)throw denied();received.requireLease(check.lease);if(!check.request.equals(received.message().result().requestId()))throw denied();check.consumed=true;MusicConnector.Observation hint=new MusicConnector(check.lease).observe(received,now());current(check);check.hint=hint;check.state=State.REPORTED_HINT;return hint;}
    public State state(Check check){owner();own(check);if(check.state==State.CHECK_REQUESTED && now()>=check.deadline)check.state=State.WAIT_EXPIRED;return check.state;}
    public void releaseAfterActualOwnerEnded(Check check){owner();own(check);if(!check.owner.hasReportedActualDomainEnd())throw denied();check.hint=null;check.state=State.RETIRED;active=null;}
    public void retire(){owner();retired=true;if(active!=null){active.hint=null;active.state=State.RETIRED;}}
    private void current(Check check){owner();own(check);if(retired || check.state==State.RETIRED || !check.owner.mayPublishCurrentResult())throw denied();check.lease.requireNativeTaskSource(check.owner);}
    private void own(Check check){if(check==null || check.issuer!=this || active!=check)throw denied();}
    private long now(){long n;try{n=clock.getAsLong();}catch(RuntimeException failure){retired=true;throw denied();}if(n<0 || n<last){retired=true;throw denied();}last=n;return n;}
    private void owner(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Native music check owner thread required");}
    private static SecurityException denied(){return new SecurityException("Fresh music account hint unavailable or stale");}
}
