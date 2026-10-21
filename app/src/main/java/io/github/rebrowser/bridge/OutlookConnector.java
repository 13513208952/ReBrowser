package io.github.rebrowser.bridge;

import java.util.*;

/** Bounded read-only MAIL SUMMARY claims. Native sender routing does not authenticate the site's
 * mutable JS account/IDs. No MailAccountCatalog admission, SQL writes, MIME/HTML/attachment IO,
 * sync cursor, login recovery, send/delete/move actions or privileged page-world evaluation. */
public final class OutlookConnector {
    public static final String ACTION="outlook.observe";
    public enum AccountClaim { UNKNOWN, REPORTED_SIGNED_IN, REPORTED_SIGNED_OUT }
    public static final class Summary {
        private final String pageId,subject,sender;private final long received;private final boolean attachment;
        private Summary(String pageId,String subject,String sender,long received,boolean attachment){this.pageId=pageId;this.subject=subject;this.sender=sender;this.received=received;this.attachment=attachment;}
        public String unverifiedPageId(){return pageId;}public String subject(){return subject;}public String senderDisplay(){return sender;}public long reportedReceivedUtcMs(){return received;}public boolean reportedHasAttachment(){return attachment;}
    }
    public static final class Observation {
        private final AccountClaim account;private final String hint;private final List<Summary> rows;private final long time;
        private Observation(AccountClaim account,String hint,List<Summary> rows,long time){this.account=account;this.hint=hint;this.rows=Collections.unmodifiableList(rows);this.time=time;}
        public AccountClaim reportedAccount(){return account;}public String unverifiedAccountHint(){return hint;}public List<Summary> summaries(){return rows;}public long nativeObservedAtMs(){return time;}
    }
    private final DocumentLease lease;private long last=-1;
    public OutlookConnector(DocumentLease nativeRoutingLease){if(nativeRoutingLease==null)throw MusicConnector.bad();lease=nativeRoutingLease;}
    public Observation observe(DocumentLease.Received received,long nativeElapsedMs){
        if(received==null || nativeElapsedMs<0 || nativeElapsedMs<last)throw MusicConnector.bad();received.requireLease(lease);Map<String,BridgeMessages.Value> f=MusicConnector.object(MusicConnector.observationPayload(received.message(),"outlook-observation"),"account","accountHint","messages");
        AccountClaim account;try{account=AccountClaim.valueOf(f.get("account").string());}catch(IllegalArgumentException invalid){throw MusicConnector.bad();}String hint=MusicConnector.text(f.get("accountHint"),256);List<BridgeMessages.Value> input=f.get("messages").array();if(input.size()>100 || account!=AccountClaim.REPORTED_SIGNED_IN && !input.isEmpty())throw MusicConnector.bad();
        List<Summary> rows=new ArrayList<>();Set<String> ids=new HashSet<>();for(BridgeMessages.Value v:input){Map<String,BridgeMessages.Value> row=MusicConnector.object(v,"id","subject","sender","receivedUtcMs","hasAttachment");String id=MusicConnector.text(row.get("id"),256);if(id.isEmpty() || !ids.add(id))throw MusicConnector.bad();long date=row.get("receivedUtcMs").asLong();if(date<0 || date>BridgeMessages.MAX_SAFE_INTEGER)throw MusicConnector.bad();rows.add(new Summary(id,MusicConnector.text(row.get("subject"),512),MusicConnector.text(row.get("sender"),256),date,row.get("hasAttachment").bool()));}
        received.requireLease(lease);last=nativeElapsedMs;return new Observation(account,hint,rows,nativeElapsedMs);
    }
}
