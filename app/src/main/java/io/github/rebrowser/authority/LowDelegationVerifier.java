package io.github.rebrowser.authority;

import io.github.rebrowser.control.ActionRegistry;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.util.*;
import java.util.function.LongSupplier;

/** Source-neutral cryptographic low-certificate chain component. A verified chain is NOT an Actor,
 * current enabled state, challenge admission, scope grant or target-possession receipt.
 * Binary certificate format 1 is bounded/native; opaque control-envelope bytes are NOT silently
 * promoted into the direct compiled-key route. Current revocations must be checked at admission. */
public final class LowDelegationVerifier {
    private static final byte[] MAGIC="RBLOW01\0".getBytes(StandardCharsets.US_ASCII);
    public static final int MAX_DEPTH=4,MAX_BYTES=8192;
    public static final class Certificate {
        private final String issuer,child;private final int level;private final long from,until;private final byte[] spki,parent;private final Set<String> actions;
        public Certificate(String issuer,String child,int level,long from,long until,byte[] canonicalSpki,byte[] parentCertificateSha256,Collection<String> actions){
            if(!id(issuer) || !id(child) || issuer.equals(child) || level<0 || level>=5 || from<0 || until<=from || until-from>86_400_000L || canonicalSpki==null || canonicalSpki.length!=91 || parentCertificateSha256==null || parentCertificateSha256.length!=32 || actions==null || actions.isEmpty() || actions.size()>32)throw invalid();
            this.issuer=issuer;this.child=child;this.level=level;this.from=from;this.until=until;spki=canonicalSpki.clone();parent=parentCertificateSha256.clone();TreeSet<String> a=new TreeSet<>();for(String s:actions){ActionRegistry.require(s);if(!a.add(s))throw invalid();}this.actions=Collections.unmodifiableSet(a);
            try{CryptoEncoding.p256PublicKey(spki);}catch(GeneralSecurityException e){throw invalid();}
        }
    }
    public static final class LeafProof {
        private final String keyId;private final ECPublicKey key;private final int level;private final Set<String> actions;private final byte[] chainHash;private final long expires;
        private LeafProof(String keyId,ECPublicKey key,int level,Set<String> actions,byte[] hash,long expires){this.keyId=keyId;this.key=key;this.level=level;this.actions=actions;chainHash=hash.clone();this.expires=expires;}
        public String keyId(){return keyId;}public int ceiling(){return level;}public boolean permits(String action){return actions.contains(action);}public byte[] chainSha256(){return chainHash.clone();}public long expiresUtcMs(){return expires;}
        public boolean verifyRequestBytes(byte[] exactFrame,byte[] signature)throws GeneralSecurityException{if(exactFrame==null || exactFrame.length>35000)throw invalid();CryptoEncoding.validateP256DerSignature(signature);Signature v=Signature.getInstance("SHA256withECDSA");v.initVerify(key);v.update(exactFrame);return v.verify(signature);}
    }
    private final CompiledPrincipals compiled;private final byte[] target,epoch;private final LongSupplier utc;private long last=-1;private boolean closed;
    public LowDelegationVerifier(CompiledPrincipals compiled,byte[] nativeTarget,byte[] nativeEpoch,LongSupplier nativeUtc){if(compiled==null || nativeTarget==null || nativeTarget.length!=32 || nativeEpoch==null || nativeEpoch.length!=16 || nativeUtc==null)throw invalid();this.compiled=compiled;target=nativeTarget.clone();epoch=nativeEpoch.clone();utc=nativeUtc;}
    public byte[] signingBytes(Certificate c){if(c==null)throw invalid();try{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);o.write(MAGIC);o.write(target);o.write(epoch);o.write(compiled.identityPolicySha256());o.writeUTF(c.issuer);o.writeUTF(c.child);o.writeByte(c.level);o.writeLong(c.from);o.writeLong(c.until);o.write(c.spki);o.write(c.parent);o.writeByte(c.actions.size());for(String a:c.actions)o.writeUTF(a);return b.toByteArray();}catch(IOException e){throw new AssertionError(e);}}
    /** The exact signed-body digest is the next link, not its ECDSA representation. */
    public static byte[] encodeChain(List<byte[]> signedBodies,List<byte[]> signatures){
        if(signedBodies==null || signatures==null || signedBodies.isEmpty() || signedBodies.size()>MAX_DEPTH || signedBodies.size()!=signatures.size())throw invalid();
        try{ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(b);o.writeByte(signedBodies.size());for(int n=0;n<signedBodies.size();n++){byte[] body=signedBodies.get(n),sig=signatures.get(n);if(body==null || body.length>1800 || body.length<100 || sig==null)throw invalid();CryptoEncoding.validateP256DerSignature(sig);o.writeShort(body.length);o.write(body);o.writeByte(sig.length);o.write(sig);}if(b.size()>MAX_BYTES)throw invalid();return b.toByteArray();}catch(IOException e){throw new AssertionError(e);}
    }
    public synchronized LeafProof verify(byte[] chain)throws GeneralSecurityException{
        long now;try{now=utc.getAsLong();}catch(RuntimeException failure){closed=true;throw rejected();}if(closed || now<0 || now<last){closed=true;throw rejected();}last=now;
        if(chain==null || chain.length==0 || chain.length>MAX_BYTES || !compiled.formalRootConfigured())throw rejected();
        byte[] snapshot=chain.clone();
        try{DataInputStream in=new DataInputStream(new ByteArrayInputStream(snapshot));int depth=in.readUnsignedByte();if(depth<1 || depth>MAX_DEPTH)throw rejected();
            String previousId=null;ECPublicKey previousKey=null;int ceiling=10;long earliest=0,expires=Long.MAX_VALUE;byte[] parent=new byte[32];Set<String> permitted=null;Set<String> ids=new HashSet<>(),keys=new HashSet<>();
            for(int n=0;n<depth;n++){
                int length=in.readUnsignedShort();if(length<100 || length>1800 || length>in.available())throw rejected();byte[] body=new byte[length];in.readFully(body);int sl=in.readUnsignedByte();if(sl<8 || sl>72 || sl>in.available())throw rejected();byte[] sig=new byte[sl];in.readFully(sig);CryptoEncoding.validateP256DerSignature(sig);
                Certificate c=decode(body);if(!MessageDigest.isEqual(body,signingBytes(c)) || !MessageDigest.isEqual(parent,c.parent))throw rejected();
                ECPublicKey issuer;
                if(n==0){CompiledPrincipals.Entry entry=compiled.find(c.issuer);if(entry==null || entry.maxLowDelegation()<0)throw rejected();for(String action:c.actions)if(!entry.permitsLowDelegationAction(action))throw rejected();issuer=entry.publicKey();ceiling=entry.maxLowDelegation();ids.add(c.issuer);keys.add(entry.spkiSha256());}
                else{if(!c.issuer.equals(previousId))throw rejected();issuer=previousKey;}
                ECPublicKey child=CryptoEncoding.p256PublicKey(c.spki);String fp=CryptoEncoding.sha256Hex(c.spki);
                if(compiled.find(c.child)!=null || compiled.containsFingerprint(fp) || !ids.add(c.child) || !keys.add(fp) || c.level>ceiling || c.from<earliest || c.until>expires || now<c.from || now>=c.until || permitted!=null && !permitted.containsAll(c.actions))throw rejected();
                Signature v=Signature.getInstance("SHA256withECDSA");v.initVerify(issuer);v.update(body);if(!v.verify(sig))throw rejected();
                previousId=c.child;previousKey=child;ceiling=c.level;earliest=c.from;expires=c.until;permitted=c.actions;parent=CryptoEncoding.sha256(body);
            }
            if(in.available()!=0)throw rejected();return new LeafProof(previousId,previousKey,ceiling,permitted,CryptoEncoding.sha256(snapshot),expires);
        }catch(IOException|IllegalArgumentException malformed){throw rejected();}
    }
    private Certificate decode(byte[] body)throws IOException{
        DataInputStream i=new DataInputStream(new ByteArrayInputStream(body));byte[] prefix=new byte[MAGIC.length+32+16+32];i.readFully(prefix);ByteArrayOutputStream p=new ByteArrayOutputStream();p.write(MAGIC);p.write(target);p.write(epoch);p.write(compiled.identityPolicySha256());if(!MessageDigest.isEqual(prefix,p.toByteArray()))throw invalid();
        String issuer=i.readUTF(),child=i.readUTF();int level=i.readUnsignedByte();long from=i.readLong(),until=i.readLong();byte[] key=new byte[91],parent=new byte[32];i.readFully(key);i.readFully(parent);int count=i.readUnsignedByte();if(count<1 || count>32)throw invalid();List<String> actions=new ArrayList<>();for(int n=0;n<count;n++)actions.add(i.readUTF());if(i.available()!=0)throw invalid();return new Certificate(issuer,child,level,from,until,key,parent,actions);
    }
    private static boolean id(String s){if(s==null || s.isEmpty() || s.length()>128)return false;for(int n=0;n<s.length();n++)if(s.charAt(n)<33 || s.charAt(n)>126)return false;return true;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid low delegation encoding");}
    private static GeneralSecurityException rejected(){return new GeneralSecurityException("Invalid low delegation proof");}
}
