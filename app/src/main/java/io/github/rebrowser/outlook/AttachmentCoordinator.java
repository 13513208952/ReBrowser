package io.github.rebrowser.outlook;

import io.github.rebrowser.core.TaskCoordinator;
import io.github.rebrowser.download.SaveTargetRegistry;
import java.io.IOException;

/** Native existing-OFFLINE attachment relation -> exact current immutable object -> actual
 * authenticated RAW reader/export. No invented website fetch/login/account binding from page
 * IDs/URLs, no body-as-attachment substitution or silently guessed filename/path/MIME semantics.
 * Actual official attachment acquisition remains held until authenticated account/producer exists.
 * Read/target authority and separate high-risk export admission are mandatory native adapters;
 * neither is supplied by metadata, page claims, Actor DTO or completion/100% indication. */
public final class AttachmentCoordinator {
    public interface CurrentAttachmentAccess {void requireExactCurrentNativeAttachment(TaskCoordinator.Owner actualNativeTask,String nativeItemKey,String nativeAttachmentKey);}
    private final MailDatabase database;private final MailExportCoordinator exports;private final CurrentAttachmentAccess access;
    public AttachmentCoordinator(MailDatabase canonicalNativeAccountDatabase,MailExportCoordinator nativeAuthorizedExportOwner,CurrentAttachmentAccess realCurrentNativeAccess){if(canonicalNativeAccountDatabase==null || nativeAuthorizedExportOwner==null || realCurrentNativeAccess==null)throw new IllegalArgumentException("Native attachment account/access/export owners required");database=canonicalNativeAccountDatabase;exports=nativeAuthorizedExportOwner;access=realCurrentNativeAccess;}
    /** Native registered item/attachment targets only; this API is never exposed to a page port. */
    public MailDatabase.AttachmentRef inspectExistingNativeAttachment(TaskCoordinator.Owner actualNativeTask,String exactNativeItem,String exactNativeAttachment){MailDatabase.NativeFence fence=fence(actualNativeTask,exactNativeItem,exactNativeAttachment);fence.requireCurrent();return database.readExistingReadyAttachment(exactNativeItem,exactNativeAttachment,fence);}
    public MailExportCoordinator.ExportObservation saveExactExistingAttachment(TaskCoordinator.Owner admittedNativeTask,MailDatabase.AttachmentRef originalNativeAttachment,SaveTargetRegistry.Target actualSelectedSaveTarget)throws Exception{
        if(originalNativeAttachment==null || actualSelectedSaveTarget==null)throw new IllegalArgumentException("Exact native attachment/save intent required");String item=originalNativeAttachment.nativeItemKeyMetadata(),attachment=originalNativeAttachment.nativeAttachmentKeyMetadata();MailDatabase.NativeFence current=fence(admittedNativeTask,item,attachment);database.requireCurrentAttachment(originalNativeAttachment,current);
        try(MailDatabase.ObjectRecord object=database.readReadyRawObject(originalNativeAttachment.nativeObjectId(),current)){if(object.purposeCode()!=2 || object.originalByteCount()!=originalNativeAttachment.observedBytes())throw new IOException("Native attachment representation/reference mismatch");database.requireCurrentAttachment(originalNativeAttachment,current);MailExportCoordinator.ExportObservation result=exports.exportExactRawObjectToSelectedTarget(admittedNativeTask,object,actualSelectedSaveTarget);database.requireCurrentAttachment(originalNativeAttachment,current);return result;}
        // Public provider may already hold bytes if final relation/current-scope check fails.
        // No automatic retry/delete/actual native-end or completed official-download inference.
    }
    private MailDatabase.NativeFence fence(TaskCoordinator.Owner task,String item,String attachment){if(task==null)throw new IllegalArgumentException("Actual native attachment task required");return ()->{database.requireNativeTaskSource(task);if(!task.mayPublishCurrentResult() || task.hasReportedActualDomainEnd() || task.cancellation().snapshot().cancellationRequested())throw new SecurityException("Native attachment task retired");access.requireExactCurrentNativeAttachment(task,item,attachment);database.requireNativeTaskSource(task);};}
}
