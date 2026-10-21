package io.github.rebrowser.outlook;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.data.NativeRecordCodec;
import java.security.SecureRandom;
import java.util.*;

/** Native local account routing/catalogue. A local account ID/label/selection is NOT a Microsoft
 * login/account proof. No cookies, credentials, remote IDs from page claims, automatic account
 * merge, data deletion or persisted replay. Native SQL/account-store reconciliation is separate. */
public final class MailAccountCatalog {
    public static final class AccountRef {
        private final MailAccountCatalog issuer;private final byte[] localId;private String label;private volatile long revision;private volatile boolean retired;
        private AccountRef(MailAccountCatalog issuer,byte[] localId,String label){this.issuer=issuer;this.localId=localId;this.label=label;}
        public byte[] localAccountId(){issuer.requireCurrent(this);return localId.clone();}
        public String label(){issuer.requireCurrent(this);return label;}
        public long revision(){issuer.requireCurrent(this);return revision;}
    }
    public static final class Selection {
        private final MailAccountCatalog issuer;private final AccountRef account;private final long catalogueRevision,accountRevision;
        private Selection(MailAccountCatalog issuer,AccountRef account){this.issuer=issuer;this.account=account;catalogueRevision=issuer.revision;accountRevision=account.revision;}
        public AccountRef account(){issuer.requireSelection(this);return account;}
    }
    /** Private native selection/source lifetime for an IO owner; NOT remote login or a DB path. */
    public static final class PersistenceScope {
        private final MailAccountCatalog issuer;private final AccountRef account;private final long catalogueRevision,accountRevision,bindingRevision;
        private PersistenceScope(MailAccountCatalog issuer,Selection selection,long nativeBindingRevision){this.issuer=issuer;account=selection.account;catalogueRevision=selection.catalogueRevision;accountRevision=selection.accountRevision;bindingRevision=nativeBindingRevision;}
        public void requireCurrent(){issuer.source.requireCurrent();if(issuer.closed || account.retired || issuer.selected!=account || issuer.revision!=catalogueRevision || account.revision!=accountRevision)throw denied();issuer.source.requireCurrent();}
        public byte[] accountId(){requireCurrent();return account.localId.clone();}
        public byte[] dataEpoch(){requireCurrent();return issuer.source.dataEpoch();}
        public long bindingRevision(){requireCurrent();return bindingRevision;}
    }
    public PersistenceScope capturePersistenceScope(Selection selection,long nativeExpectedBindingRevision){requireSelection(selection);if(nativeExpectedBindingRevision<1)throw denied();return new PersistenceScope(this,selection,nativeExpectedBindingRevision);}
    private final ComponentGate.DataEpochPin source;private final Thread owner=Thread.currentThread();private final Map<String,AccountRef> accounts=new LinkedHashMap<>();private final SecureRandom random=new SecureRandom();private volatile AccountRef selected;private volatile long revision;private volatile boolean closed;
    public MailAccountCatalog(ComponentGate.DataEpochPin nativeSource){if(nativeSource==null)throw new IllegalArgumentException("Native mail account source required");source=nativeSource;source.requireCurrent();}
    /** Explicit LOCAL user-account slot creation only, never called from an Outlook page DTO. */
    public AccountRef createNativeLocalAccount(String nativeDisplayLabel){current();String label=NativeRecordCodec.validateText(nativeDisplayLabel,640,false);if(accounts.size()>=32 || revision==Long.MAX_VALUE)throw new IllegalStateException("Native mail account limit reached");byte[] id=null;String key=null;for(int n=0;n<16;n++){byte[] candidate=new byte[16];random.nextBytes(candidate);String hex=hex(candidate);if(!accounts.containsKey(hex)){id=candidate;key=hex;break;}}if(id==null)throw new IllegalStateException("Native account identity collision");AccountRef ref=new AccountRef(this,id,label);accounts.put(key,ref);revision++;return ref;}
    public void rename(AccountRef ref,long expectedAccountRevision,String nativeLabel){requireCurrent(ref);if(ref.revision!=expectedAccountRevision || ref.revision==Long.MAX_VALUE || revision==Long.MAX_VALUE)throw denied();String label=NativeRecordCodec.validateText(nativeLabel,640,false);ref.label=label;ref.revision++;revision++;}
    public Selection select(AccountRef ref){requireCurrent(ref);if(revision==Long.MAX_VALUE)throw denied();selected=ref;revision++;return new Selection(this,ref);}
    public void requireSelection(Selection selection){current();if(selection==null || selection.issuer!=this || selection.account!=selected || selection.catalogueRevision!=revision || selection.accountRevision!=selection.account.revision)throw denied();requireCurrent(selection.account);}
    public void requireCurrent(AccountRef ref){current();if(ref==null || ref.issuer!=this || ref.retired || accounts.get(hex(ref.localId))!=ref)throw denied();}
    public List<AccountRef> listNativeLocalAccounts(){current();List<AccountRef> current=new ArrayList<>();for(AccountRef a:accounts.values())if(!a.retired)current.add(a);return Collections.unmodifiableList(current);}
    public void retire(AccountRef ref){requireCurrent(ref);if(ref.revision==Long.MAX_VALUE || revision==Long.MAX_VALUE)throw denied();ref.retired=true;ref.revision++;revision++;if(selected==ref)selected=null;} // No cookie/data erase or implicit logout.
    public void close(){owner();closed=true;selected=null;for(AccountRef a:accounts.values()){a.retired=true;a.label="";}}
    public byte[] nativeDataEpoch(){current();return source.dataEpoch();}
    private void current(){owner();source.requireCurrent();if(closed)throw denied();}
    private void owner(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Native mail account owner thread required");}
    private static String hex(byte[] b){StringBuilder out=new StringBuilder();for(byte v:b)out.append(Character.forDigit((v&255)>>>4,16)).append(Character.forDigit(v&15,16));return out.toString();}
    private static SecurityException denied(){return new SecurityException("Native local mail account selection stale");}
}
