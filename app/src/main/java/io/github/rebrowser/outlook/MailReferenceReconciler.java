package io.github.rebrowser.outlook;

import io.github.rebrowser.core.TaskCoordinator;

/** Repairs transactional reference-count CACHES from the actual SQL reference view. This is
 * mail integrity, not deferred ordinary subapplication cleanup. It deletes no objects/files,
 * never trusts cached zero as GC authority and does not claim decoded/AEAD byte integrity. */
public final class MailReferenceReconciler {
    private final MailDatabase database;
    public MailReferenceReconciler(MailDatabase existingAccountDatabase){if(existingAccountDatabase==null)throw new IllegalArgumentException("Native mail DB required");database=existingAccountDatabase;}
    public int reconcileAfterNativeAuthorization(TaskCoordinator.Owner admittedNativeIntegrityTask,int maximumObjects)throws Exception{
        if(admittedNativeIntegrityTask==null || maximumObjects<1 || maximumObjects>100_000)throw new IllegalArgumentException("Native mail integrity task/budget required");MailDatabase.NativeFence fence=()->{if(!admittedNativeIntegrityTask.mayPublishCurrentResult())throw new SecurityException("Native integrity task scope retired");};fence.requireCurrent();admittedNativeIntegrityTask.claimCommittedNativeRoute(new Object());int changed=database.writeAfterNativeAuthorization(fence,tx->tx.reconcileReferenceCounts(maximumObjects));fence.requireCurrent();return changed;
    }
}
