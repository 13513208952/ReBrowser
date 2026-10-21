package io.github.rebrowser.outlook;

import io.github.rebrowser.bridge.DocumentLease;
import io.github.rebrowser.bridge.OutlookConnector;
import io.github.rebrowser.core.TaskCoordinator;
import java.util.function.LongSupplier;

/** Fresh exact-request official-page CHECK orchestration; outcomes explicitly remain reported
 * page claims, never a native account binding/authentication receipt. Timeout/network/port loss
 * is UNKNOWN, not logout or actual worker end. No network/login/retry/fallback is started here. */
public final class MailAuthCoordinator {
    public enum State { REQUESTED, REPORTED_CLAIM, WAIT_DEADLINE_EXCEEDED, RETIRED }
    public static final class Attempt {
        private final MailAuthCoordinator issuer;private final MailAccountCatalog.Selection selection;private final DocumentLease lease;private final TaskCoordinator.Owner owner;private final String requestId;private final long deadline;
        private State state=State.REQUESTED;private OutlookConnector.Observation claim;private boolean consumed;
        private Attempt(MailAuthCoordinator issuer,MailAccountCatalog.Selection selection,DocumentLease lease,TaskCoordinator.Owner owner,String requestId,long deadline){this.issuer=issuer;this.selection=selection;this.lease=lease;this.owner=owner;this.requestId=requestId;this.deadline=deadline;}
    }
    private final MailAccountCatalog accounts;private final LongSupplier clock;private final Thread thread=Thread.currentThread();private Attempt active;private long last=-1;private boolean closed;
    public MailAuthCoordinator(MailAccountCatalog nativeAccounts,LongSupplier nativeElapsedRealtime){if(nativeAccounts==null || nativeElapsedRealtime==null)throw new IllegalArgumentException("Native mail auth owner required");accounts=nativeAccounts;clock=nativeElapsedRealtime;}
    public Attempt begin(MailAccountCatalog.Selection exactNativeSelection,DocumentLease nativeLease,TaskCoordinator.Owner actualNativeTask,String nativeRegisteredRequestId){
        owner();long now=now();accounts.requireSelection(exactNativeSelection);if(closed || active!=null || nativeLease==null || actualNativeTask==null || !actualNativeTask.mayPublishCurrentResult() || nativeRegisteredRequestId==null || !nativeRegisteredRequestId.matches("r_[0-9a-f]{32}") || now>Long.MAX_VALUE-30_000)throw denied();nativeLease.requireNativeTaskSource(actualNativeTask);accounts.requireNativeTaskSource(actualNativeTask);active=new Attempt(this,exactNativeSelection,nativeLease,actualNativeTask,nativeRegisteredRequestId,now+30_000);return active;
    }
    public OutlookConnector.Observation acceptClaim(Attempt attempt,DocumentLease.Received received){
        current(attempt);if(attempt.consumed || attempt.state!=State.REQUESTED || now()>=attempt.deadline)throw denied();received.requireLease(attempt.lease);if(!attempt.requestId.equals(received.message().result().requestId()))throw denied();attempt.consumed=true;OutlookConnector.Observation observation=new OutlookConnector(attempt.lease).observe(received,now());current(attempt);attempt.claim=observation;attempt.state=State.REPORTED_CLAIM;return observation;
    }
    public State state(Attempt attempt){owner();own(attempt);if(attempt.state==State.REQUESTED && now()>=attempt.deadline)attempt.state=State.WAIT_DEADLINE_EXCEEDED;return attempt.state;}
    /** Real owning TaskCoordinator end only; client wait timeout/claim cannot release this slot. */
    public void releaseAfterActualOwnerEnded(Attempt attempt){owner();own(attempt);if(!attempt.owner.hasReportedActualDomainEnd())throw denied();attempt.claim=null;attempt.state=State.RETIRED;active=null;}
    public void retire(){owner();closed=true;if(active!=null){active.state=State.RETIRED;active.claim=null;}} // Old actual work may still settle via release.
    private void current(Attempt attempt){owner();own(attempt);if(closed || attempt.state==State.RETIRED || !attempt.owner.mayPublishCurrentResult())throw denied();accounts.requireSelection(attempt.selection);attempt.lease.requireNativeTaskSource(attempt.owner);accounts.requireNativeTaskSource(attempt.owner);}
    private void own(Attempt attempt){if(attempt==null || attempt.issuer!=this || active!=attempt)throw denied();}
    private long now(){long n;try{n=clock.getAsLong();}catch(RuntimeException unavailable){closed=true;throw denied();}if(n<0 || n<last){closed=true;throw denied();}last=n;return n;}
    private void owner(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Native mail auth owner thread required");}
    private static SecurityException denied(){return new SecurityException("Native mail check unavailable or stale");}
}
