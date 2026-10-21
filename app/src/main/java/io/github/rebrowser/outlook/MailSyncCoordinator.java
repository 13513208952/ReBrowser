package io.github.rebrowser.outlook;

import io.github.rebrowser.core.TaskCoordinator;
import io.github.rebrowser.data.NativeRecordCodec;
import java.util.*;

/** Account-scoped native metadata batch + cursor commit in ONE actual transaction. Only a
 * pre-admitted native official producer may supply updates; CandidatePage/account hints cannot
 * promote themselves here. Body/attachment object adoption is separate until authenticated
 * object-store receipts are available. No fetch/retry/login, cloud write or cursor-on-partial. */
public final class MailSyncCoordinator {
    public static final class Update {
        private final String officialKey,serverRevision,subject,sender;private final long time,expectedRevision,generation;
        public Update(String nativeOfficialKey,String nativeServerRevision,String subject,String sender,long receivedUtc,long expectedLocalRevision,long nativeGeneration){officialKey=text(nativeOfficialKey,1024,false);serverRevision=text(nativeServerRevision,4096,true);this.subject=text(subject,2048,true);this.sender=text(sender,1024,true);if(receivedUtc<0 || expectedLocalRevision<0 || expectedLocalRevision==Long.MAX_VALUE || nativeGeneration<1)throw bad();time=receivedUtc;expectedRevision=expectedLocalRevision;generation=nativeGeneration;}
    }
    public static final class Batch {
        private final List<Update> updates;private final String stream,cursor;private final long expectedCursor,newCursor;
        public Batch(List<Update> admittedNativeUpdates,String nativeStream,String nativeOpaqueCursor,long expectedCursorRevision,long newCursorRevision){
            if(admittedNativeUpdates==null || admittedNativeUpdates.size()>100 || expectedCursorRevision<0 || newCursorRevision<=expectedCursorRevision)throw bad();List<Update> copy=new ArrayList<>(admittedNativeUpdates);Set<String> ids=new HashSet<>();long bytes=0;for(Update u:copy){if(u==null || !ids.add(u.officialKey))throw bad();bytes+=u.officialKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+u.serverRevision.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+u.subject.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+u.sender.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(bytes>128*1024)throw bad();}updates=Collections.unmodifiableList(copy);stream=text(nativeStream,64,false);cursor=text(nativeOpaqueCursor,4096,true);expectedCursor=expectedCursorRevision;newCursor=newCursorRevision;
        }
        public int itemCount(){return updates.size();}
    }
    private final MailDatabase database;
    public MailSyncCoordinator(MailDatabase nativeExistingAccountDatabase){if(nativeExistingAccountDatabase==null)throw bad();database=nativeExistingAccountDatabase;}
    public long commitAdmittedNativeMetadata(TaskCoordinator.Owner nativeRegisteredTask,Batch batch)throws Exception{
        if(nativeRegisteredTask==null || batch==null)throw bad();MailDatabase.NativeFence fence=()->{if(!nativeRegisteredTask.mayPublishCurrentResult())throw new SecurityException("Native mail sync scope retired");};fence.requireCurrent();nativeRegisteredTask.claimCommittedNativeRoute(batch);
        Long committed=database.writeAfterNativeAuthorization(fence,tx->{for(Update u:batch.updates){fence.requireCurrent();tx.putSummary(u.officialKey,u.serverRevision,u.subject,u.sender,u.time,u.expectedRevision,u.generation);}fence.requireCurrent();tx.commitCursor(batch.stream,batch.cursor,batch.expectedCursor,batch.newCursor);return batch.newCursor;});fence.requireCurrent();return committed;
    }
    private static String text(String s,int bytes,boolean empty){return NativeRecordCodec.validateText(s,bytes,empty);}
    private static IllegalArgumentException bad(){return new IllegalArgumentException("Invalid bounded native mail metadata batch");}
}
