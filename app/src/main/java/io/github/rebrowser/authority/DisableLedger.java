package io.github.rebrowser.authority;

import io.github.rebrowser.crypto.CryptoEncoding;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Signed, bounded, immutable compiled-identity disable history reducer. NOT persistence/latest
 * evidence. A native journal owner MUST compare the snapshot with its current durable head inside
 * the admission/commit fence. Importing a valid old history never establishes latest policy.
 * No key addition, delegation admission, device enumeration or automatic target resurrection. */
public final class DisableLedger {
    public static final int MAX_RECORDS=4096;
    public enum Operation { DISABLE, RESTORE }
    public static final class Event {
        private final Operation operation;private final String actor,target;private final long revision,withdraws;private final byte[] previous,signature;
        public Event(Operation operation,String actor,String target,long revision,long withdraws,byte[] previous,byte[] signature){
            if(operation==null || !id(actor) || !id(target) || revision<1 || withdraws<0 || previous==null || previous.length!=32 || signature==null || signature.length>72)throw invalid();
            if((operation==Operation.DISABLE)!=(withdraws==0))throw invalid();
            CryptoEncoding.validateP256DerSignature(signature);this.operation=operation;this.actor=actor;this.target=target;this.revision=revision;this.withdraws=withdraws;this.previous=previous.clone();this.signature=signature.clone();
        }
    }
    private static final class Active {final String actor,target;final int issuerLevel;Active(String a,String t,int l){actor=a;target=t;issuerLevel=l;}}
    public static final class Snapshot {
        private final DisableLedger issuer;private final long revision;private final byte[] head;private final Map<Long,Active> active;
        private Snapshot(DisableLedger issuer,long revision,byte[] head,Map<Long,Active> active){this.issuer=issuer;this.revision=revision;this.head=head.clone();this.active=Collections.unmodifiableMap(new LinkedHashMap<>(active));}
        public long revision(){return revision;}public byte[] headSha256(){return head.clone();}
        public boolean belongsTo(CompiledPrincipals table){return issuer.compiled==table;}
        public boolean disabled(String principal){CompiledPrincipals.Entry e=issuer.compiled.find(principal);if(e==null)throw invalid();if(e.role()==CompiledPrincipals.Role.ROOT)return false;for(Active a:active.values())if(a.target.equals(principal))return true;return false;}
        public boolean matchesNativeBinding(byte[] target,byte[] epoch){return MessageDigest.isEqual(issuer.target,target) && MessageDigest.isEqual(issuer.epoch,epoch);}
    }
    private final CompiledPrincipals compiled;private final byte[] target,epoch;
    public DisableLedger(CompiledPrincipals compiled,byte[] nativeTarget,byte[] nativeEpoch){if(compiled==null || nativeTarget==null || nativeTarget.length!=32 || nativeEpoch==null || nativeEpoch.length!=16)throw invalid();this.compiled=compiled;target=nativeTarget.clone();epoch=nativeEpoch.clone();}
    /** Empty verified HISTORY only, NOT proof of fresh install, initialized DB or enabled identity. */
    public Snapshot emptyHistory(){return new Snapshot(this,0,CryptoEncoding.sha256(prefix()),Collections.<Long,Active>emptyMap());}
    public Snapshot appendVerified(Snapshot before,Event event)throws GeneralSecurityException{
        if(before==null || before.issuer!=this || event==null || before.revision>=MAX_RECORDS || event.revision!=before.revision+1 || !MessageDigest.isEqual(event.previous,before.head))throw rejected();
        CompiledPrincipals.Entry actor=compiled.find(event.actor),victim=compiled.find(event.target);
        if(actor==null || victim==null || before.disabled(event.actor) || !AuthorityScale.mayDisableLevel(actor.level(),victim.level()))throw rejected();
        byte[] signed=signingBytes(event.operation,event.actor,event.target,event.revision,event.withdraws,event.previous);
        Signature verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(actor.publicKey());verifier.update(signed);if(!verifier.verify(event.signature))throw rejected();
        Map<Long,Active> next=new LinkedHashMap<>(before.active);
        if(event.operation==Operation.DISABLE)next.put(event.revision,new Active(event.actor,event.target,actor.level()));
        else{Active original=next.get(event.withdraws);if(original==null || !original.target.equals(event.target) || !(original.actor.equals(event.actor) || actor.level()>original.issuerLevel))throw rejected();next.remove(event.withdraws);}
        // Signature is verified but excluded from logical head: high-S alternatives cannot fork it.
        return new Snapshot(this,event.revision,CryptoEncoding.sha256(signed),next);
    }
    public byte[] signingBytes(Operation operation,String actor,String target,long revision,long withdraws,byte[] previous){
        if(operation==null || !id(actor) || !id(target) || revision<1 || withdraws<0 || previous==null || previous.length!=32)throw invalid();
        try{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);o.write(prefix());o.writeByte(operation.ordinal());o.writeLong(revision);o.writeLong(withdraws);o.write(previous);o.writeUTF(actor);o.writeUTF(target);return b.toByteArray();}catch(IOException impossible){throw new AssertionError(impossible);}
    }
    private byte[] prefix(){try{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write("ReBrowser.Disable.History.1\0".getBytes(StandardCharsets.US_ASCII));b.write(target);b.write(epoch);b.write(compiled.identityPolicySha256());return b.toByteArray();}catch(IOException impossible){throw new AssertionError(impossible);}}
    private static boolean id(String s){if(s==null || s.isEmpty() || s.length()>128)return false;for(int n=0;n<s.length();n++)if(s.charAt(n)<33 || s.charAt(n)>126)return false;return true;}
    private static GeneralSecurityException rejected(){return new GeneralSecurityException("Invalid disable history");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid disable record");}
}
