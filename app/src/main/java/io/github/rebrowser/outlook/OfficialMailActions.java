package io.github.rebrowser.outlook;

import io.github.rebrowser.core.TaskCoordinator;
import io.github.rebrowser.data.NativeRecordCodec;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native fixed SEND/MOVE/DELETE preparation/handoff owner, NOT an arbitrary JS/API/URL relay.
 * Mandatory adapters must GENUINELY bind current authenticated official mailbox, exact target,
 * high-risk owner/current principal policy and fixed official transport. They are NOT installed:
 * page claims, local AccountRef, public signing bytes and native port routing cannot supply them.
 * This core does not invent Graph credentials/endpoints or unsupported bridge writer actions.
 * It NEVER turns Java return/page callback/cancel/timeout into verified effect or native end.
 * Qualified transport must independently record actual attributable server receipts in native
 * TaskJournal and actual resource-owner end; no synthetic receipt/callback issuer exists here.
 */
public final class OfficialMailActions {
    public enum Action { SEND, MOVE, DELETE }
    public enum HandoffObservation { PREPARED_NATIVE_INTENT, HANDOFF_CALL_RETURNED, HANDOFF_OUTCOME_UNKNOWN, CANCEL_REQUESTED_NOT_ENDED, CANCEL_CALL_UNKNOWN, RETIRED_ACTUAL_OWNER_END }
    /** Read-only genuine current checks; NOT page account matching or repeated grant spending. */
    public interface CurrentMailboxAuthority {void requireExactCurrentWrite(MailAccountCatalog.PersistenceScope actualLocalScope,Action action,String nativeItemTarget,String nativeFolderTarget,TaskCoordinator.Owner admittedNativeTask);}
    /** Qualified NATIVE fixed official operation implementation only. No caller script, callback
     * success flag or default implementation. Java return is a handoff observation, not receipt. */
    public interface NativeOfficialWriteAdapter {void handoffOnce(Operation exactNativeOperation)throws Exception;void requestStopOwned(Operation exactNativeOperation)throws Exception;}
    public static final class Operation {
        private final OfficialMailActions issuer;private final MailAccountCatalog.PersistenceScope scope;private final TaskCoordinator.Submission task;private final Action action;private final String item,folder;private String recipient,subject,body;private final AtomicBoolean dispatched=new AtomicBoolean(),cancelAttempted=new AtomicBoolean();private HandoffObservation observed=HandoffObservation.PREPARED_NATIVE_INTENT;private boolean released;
        private Operation(OfficialMailActions issuer,MailAccountCatalog.PersistenceScope scope,TaskCoordinator.Submission task,Action action,String item,String folder,String recipient,String subject,String body){this.issuer=issuer;this.scope=scope;this.task=task;this.action=action;this.item=item;this.folder=folder;this.recipient=recipient;this.subject=subject;this.body=body;}
        public Action action(){issuer.current(this,false);return action;}public String nativeItemTarget(){issuer.current(this,false);return item;}public String nativeFolderTarget(){issuer.current(this,false);return folder;}
        public String nativeRecipient(){issuer.current(this,false);if(action!=Action.SEND)throw denied();return recipient;}public String nativeSubject(){issuer.current(this,false);if(action!=Action.SEND)throw denied();return subject;}public String nativePlainBody(){issuer.current(this,false);if(action!=Action.SEND)throw denied();return body;}
        /** Backend uses this actual private native task root for genuine current journaling only;
         * any actual end it reports MUST cover its real native network/writer/resource chain. */
        public TaskCoordinator.Owner actualNativeTaskOwner(){issuer.current(this,false);return task.nativeOwner();}
        public HandoffObservation handoffObservation(){issuer.owner();issuer.own(this);return observed;}
    }
    private final MailAccountCatalog.PersistenceScope scope;private final CurrentMailboxAuthority authority;private final NativeOfficialWriteAdapter backend;private final Set<Operation> operations=Collections.newSetFromMap(new IdentityHashMap<Operation,Boolean>());private final Thread owner=Thread.currentThread();private boolean preparing;
    public OfficialMailActions(MailAccountCatalog.PersistenceScope actualLocalScope,CurrentMailboxAuthority actualQualifiedMailboxAuthority,NativeOfficialWriteAdapter actualQualifiedNativeTransport){if(actualLocalScope==null || actualQualifiedMailboxAuthority==null || actualQualifiedNativeTransport==null)throw new IllegalArgumentException("Actual qualified mailbox/authority/transport required");scope=actualLocalScope;scope.requireCurrent();authority=actualQualifiedMailboxAuthority;backend=actualQualifiedNativeTransport;}
    /** Explicit native composer data only. No attachment paths/HTML/scripts/credential fields. */
    public Operation prepareNativeSend(TaskCoordinator.Submission admittedNativeTask,String nativeRecipient,String nativeSubject,String nativePlainBody){owner();String recipient=NativeRecordCodec.validateText(nativeRecipient,320,false);if(!recipient.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?"))throw new IllegalArgumentException("Unsupported bounded native recipient");String subject=NativeRecordCodec.validateText(nativeSubject,2048,true);String body=plainBody(nativePlainBody);return prepare(admittedNativeTask,Action.SEND,"","",recipient,subject,body);}
    /** Native SQL SUMMARY identity is metadata only; the mandatory authenticated-mailbox adapter
     * must repeat actual server-target binding/current native policy, never trust page IDs. */
    public Operation prepareNativeMove(TaskCoordinator.Submission admittedNativeTask,MailDatabase.Summary exactNativeItem,String nativeExplicitFolderTarget){owner();if(exactNativeItem==null)throw denied();return prepare(admittedNativeTask,Action.MOVE,NativeRecordCodec.validateText(exactNativeItem.officialItemKey(),1024,false),NativeRecordCodec.validateText(nativeExplicitFolderTarget,1024,false),"","","");}
    public Operation prepareNativeDelete(TaskCoordinator.Submission admittedNativeTask,MailDatabase.Summary exactNativeItem){owner();if(exactNativeItem==null)throw denied();return prepare(admittedNativeTask,Action.DELETE,NativeRecordCodec.validateText(exactNativeItem.officialItemKey(),1024,false),"","","","");}
    private Operation prepare(TaskCoordinator.Submission task,Action action,String item,String folder,String recipient,String subject,String body){owner();scope.requireCurrent();if(preparing || operations.size()>=32 || task==null)throw denied();preparing=true;try{scope.requireNativeTaskSource(task.nativeOwner());if(task.nativeOwner().hasReportedActualDomainEnd() || task.nativeOwner().cancellation().snapshot().cancellationRequested())throw denied();authority.requireExactCurrentWrite(scope,action,item,folder,task.nativeOwner());Operation operation=new Operation(this,scope,task,action,item,folder,recipient,subject,body);operations.add(operation);return operation;}finally{preparing=false;}}
    public HandoffObservation dispatchPreparedOnce(Operation exactNativeOperation){owner();current(exactNativeOperation,false);if(!exactNativeOperation.dispatched.compareAndSet(false,true))throw denied();exactNativeOperation.observed=HandoffObservation.HANDOFF_OUTCOME_UNKNOWN;exactNativeOperation.task.nativeOwner().claimCommittedNativeRoute(exactNativeOperation);current(exactNativeOperation,true);exactNativeOperation.observed=HandoffObservation.HANDOFF_OUTCOME_UNKNOWN;try{backend.handoffOnce(exactNativeOperation);exactNativeOperation.observed=HandoffObservation.HANDOFF_CALL_RETURNED;}catch(Exception unknown){/* native owner remains registered; NEVER alternate route/retry */}return exactNativeOperation.observed;}
    public HandoffObservation requestStopOwned(Operation operation){owner();own(operation);if(operation.released || !operation.dispatched.get() || !operation.cancelAttempted.compareAndSet(false,true))throw denied();operation.observed=HandoffObservation.CANCEL_CALL_UNKNOWN;try{backend.requestStopOwned(operation);operation.observed=HandoffObservation.CANCEL_REQUESTED_NOT_ENDED;}catch(Exception unknown){/* old native work retains ownership; not cancelled/ended proof */}return operation.observed;}
    /** Actual TaskCoordinator end observation ONLY, not a native page/transport success callback. */
    public void releaseAfterActualNativeOwnerEnd(Operation operation){owner();own(operation);if(operation.released || !operation.task.nativeOwner().hasReportedActualDomainEnd())throw denied();operation.released=true;operation.recipient=null;operation.subject=null;operation.body=null;operation.observed=HandoffObservation.RETIRED_ACTUAL_OWNER_END;} // String caller copies/physical GC remain outside this logical lifetime.
    private void current(Operation operation,boolean requireRoute){owner();own(operation);if(preparing || operation.released)throw denied();scope.requireCurrent();operation.scope.requireNativeTaskSource(operation.task.nativeOwner());if(operation.task.nativeOwner().hasReportedActualDomainEnd() || operation.task.nativeOwner().cancellation().snapshot().cancellationRequested())throw denied();authority.requireExactCurrentWrite(scope,operation.action,operation.item,operation.folder,operation.task.nativeOwner());if(requireRoute)operation.task.nativeOwner().requireCurrentCommittedNativeRoute(operation);scope.requireCurrent();}
    private void own(Operation operation){if(operation==null || operation.issuer!=this || !operations.contains(operation))throw denied();}
    private void owner(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Native official-mail command owner thread required");}
    private static String plainBody(String s){if(s==null || s.length()>65536)throw new IllegalArgumentException("Bounded native plain mail body required");for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length() || !Character.isLowSurrogate(s.charAt(i)))throw new IllegalArgumentException("Mail UTF16");}else if(Character.isLowSurrogate(c) || Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t')throw new IllegalArgumentException("Mail plain text");}return s;}
    private static SecurityException denied(){return new SecurityException("Native official mail scope/owner unavailable or spent");}
}
