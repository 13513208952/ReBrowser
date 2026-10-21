package io.github.rebrowser.outlook;

import io.github.rebrowser.bridge.DocumentLease;
import io.github.rebrowser.bridge.OutlookConnector;
import io.github.rebrowser.bridge.BridgeStreamReceiver;
import java.security.MessageDigest;
import java.util.*;

/** Correlates a bounded native-selected account/port with candidate mail metadata/object bytes.
 * Output is deliberately UNVERIFIED business content: port identity, page account hints, hashes
 * and local account IDs do NOT authenticate Microsoft account ownership or authorize sync/write.
 * MailSyncCoordinator must require a separately qualified official capture/admission route. */
public final class MailCaptureValidator {
    public static final class CandidatePage {
        private final byte[] localAccount,epoch;private final List<OutlookConnector.Summary> summaries;private final String reportedAccountHint;
        private CandidatePage(byte[] account,byte[] epoch,List<OutlookConnector.Summary> summaries,String hint){localAccount=account.clone();this.epoch=epoch.clone();this.summaries=Collections.unmodifiableList(new ArrayList<>(summaries));reportedAccountHint=hint;}
        public byte[] unverifiedLocalAccountId(){return localAccount.clone();}public byte[] unverifiedDataEpoch(){return epoch.clone();}public List<OutlookConnector.Summary> unverifiedSummaries(){return summaries;}public String unverifiedAccountHint(){return reportedAccountHint;}
    }
    private final MailAccountCatalog accounts;private final MailAccountCatalog.Selection selection;private final DocumentLease lease;private final OutlookConnector parser;private final byte[] account,epoch;
    public MailCaptureValidator(MailAccountCatalog nativeAccounts,MailAccountCatalog.Selection exactSelection,DocumentLease actualNativeRoutingLease){if(nativeAccounts==null || exactSelection==null || actualNativeRoutingLease==null)throw new IllegalArgumentException("Native mail capture scope required");accounts=nativeAccounts;selection=exactSelection;lease=actualNativeRoutingLease;accounts.requireSelection(selection);lease.requireCurrent();account=selection.account().localAccountId();epoch=accounts.nativeDataEpoch();parser=new OutlookConnector(lease);}
    public CandidatePage inspectCandidate(DocumentLease.Received exactPortMessage,long nativeElapsedMs){current();exactPortMessage.requireLease(lease);OutlookConnector.Observation observation=parser.observe(exactPortMessage,nativeElapsedMs);if(observation.reportedAccount()!=OutlookConnector.AccountClaim.REPORTED_SIGNED_IN)throw denied();Set<String> ids=new HashSet<>();for(OutlookConnector.Summary row:observation.summaries())if(!ids.add(row.unverifiedPageId()))throw denied();current();return new CandidatePage(account,epoch,observation.summaries(),observation.unverifiedAccountHint());}
    /** Structural RAW descriptor consistency only, not AEAD/catalog/official-source authentication. */
    public void inspectCandidateRawObject(MailObjectFormat.Header nativeProposedHeader,BridgeStreamReceiver.EndObservation receivedIntegrity,MailObjectFormat.Purpose admittedPurpose){
        current();if(nativeProposedHeader==null || receivedIntegrity==null || admittedPurpose==null || nativeProposedHeader.codec()!=MailObjectFormat.Codec.RAW || nativeProposedHeader.purpose()!=admittedPurpose || !MessageDigest.isEqual(account,nativeProposedHeader.accountId()) || !MessageDigest.isEqual(epoch,nativeProposedHeader.dataEpoch()) || nativeProposedHeader.originalBytes()!=receivedIntegrity.receivedBytes() || !hex(nativeProposedHeader.originalSha256()).equals(receivedIntegrity.sha256()))throw denied();current();
    }
    private void current(){accounts.requireSelection(selection);lease.requireCurrent();if(!MessageDigest.isEqual(account,selection.account().localAccountId()) || !MessageDigest.isEqual(epoch,accounts.nativeDataEpoch()))throw denied();}
    private static String hex(byte[] b){StringBuilder out=new StringBuilder();for(byte v:b)out.append(Character.forDigit((v&255)>>>4,16)).append(Character.forDigit(v&15,16));return out.toString();}
    private static SecurityException denied(){return new SecurityException("Unverified mail candidate has wrong scope/shape");}
}
