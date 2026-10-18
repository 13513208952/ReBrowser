import java.io.*;
import java.math.BigInteger;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/** HOST-only public scalar1 fixtures, no real administrator/release/device keys. */
public final class ControlProofVectors {
    static final byte[] MAGIC = "ReBrowser.Control.Proof\0".getBytes(StandardCharsets.US_ASCII);
    static final String[] NAMES = {"protocol","applicationId","principalKeyId","targetInstallSpkiSha256","dataEpoch","serverInstanceNonce","compiledPolicySha256","disableRevision","challengeId","nonce","deadlineElapsedMs","requestId","actionId","scopeRef","ownerGrantRef","delegationSha256","argsBytes"};
    static final int[] MAX = {32,128,128,32,16,32,32,8,16,32,8,16,128,256,128,32,32768};
    static final int[] EXACT = {0,0,0,32,16,32,32,8,16,32,8,16,0,0,0,32,0};
    static final BigInteger ORDER = new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551",16);
    static int checks;
    interface Operation { void run() throws Exception; }
    static void check(boolean ok) { if (!ok) throw new AssertionError("check " + checks); checks++; }
    static void reject(Operation op) throws Exception {
        boolean rejected = false;
        try { op.run(); } catch (IllegalArgumentException | IOException e) { rejected = true; }
        check(rejected);
    }
    static byte[] utf8(String s) throws Exception {
        ByteBuffer b = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(s));
        byte[] out = new byte[b.remaining()];b.get(out);return out;
    }
    static void field(int i, byte[] value) throws IOException {
        if (value.length > MAX[i] || value.length == 0 && i != 14 || EXACT[i] != 0 && value.length != EXACT[i]) throw new IOException("field length");
        if (i==0 || i==1 || i==2 || i==12 || i==13 || i==14) {
            for (byte b : value) if ((b & 255) < 33 || (b & 255) > 126) throw new IOException("ASCII");
        }
        if (i==0 && !Arrays.equals(value,"rebrowser.control/1".getBytes(StandardCharsets.US_ASCII))) throw new IOException("protocol");
        if (i==1 && !Arrays.equals(value,"io.github.rebrowser".getBytes(StandardCharsets.US_ASCII))) throw new IOException("application");
        if ((i==7 || i==10) && value[0] < 0) throw new IOException("u63");
        if (i==16) StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value));
    }
    static byte[] encode(byte[][] fields) throws IOException {
        if (fields.length != NAMES.length) throw new IOException("field count");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();DataOutputStream out = new DataOutputStream(bytes);out.write(MAGIC);out.writeShort(1);
        for (int i=0;i<fields.length;i++) { field(i,fields[i]);out.writeInt(fields[i].length);out.write(fields[i]); }
        if (bytes.size() > 35000) throw new IOException("frame bound");return bytes.toByteArray();
    }
    static byte[][] decode(byte[] frame) throws IOException {
        if (frame.length > 35000) throw new IOException("frame bound");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame));
        if (!Arrays.equals(in.readNBytes(MAGIC.length), MAGIC) || in.readUnsignedShort()!=1) throw new IOException("domain/version");
        byte[][] result = new byte[NAMES.length][];
        for (int i=0;i<NAMES.length;i++) {
            int n=in.readInt();if(n<0 || n>MAX[i]) throw new IOException("length before allocation");
            result[i]=in.readNBytes(n);if(result[i].length!=n) throw new EOFException();field(i,result[i]);
        }
        if(in.read()!=-1) throw new IOException("trailing");return result;
    }
    static BigInteger[] der(byte[] sig) throws IOException {
        if(sig.length<8 || sig.length>72 || sig[0]!=0x30 || (sig[1]&255)!=sig.length-2) throw new IOException("DER sequence");
        BigInteger[] values=new BigInteger[2];int at=2;
        for(int i=0;i<2;i++) {
            if(at+2>sig.length || sig[at++]!=2) throw new IOException("DER integer");
            int n=sig[at++]&255;if(n<1 || n>33 || at+n>sig.length || sig[at]<0 || n>1 && sig[at]==0 && sig[at+1]>=0) throw new IOException("DER minimal positive");
            values[i]=new BigInteger(Arrays.copyOfRange(sig,at,at+n));at+=n;
            if(values[i].signum()<=0 || values[i].compareTo(ORDER)>=0) throw new IOException("scalar range");
        }
        if(at!=sig.length) throw new IOException("DER trailing");return values;
    }
    static byte[] derEncode(BigInteger r,BigInteger s) throws IOException {
        byte[] a=r.toByteArray(), b=s.toByteArray();ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(0x30);out.write(4+a.length+b.length);out.write(2);out.write(a.length);out.write(a);out.write(2);out.write(b.length);out.write(b);return out.toByteArray();
    }
    static boolean verify(PublicKey key,byte[] frame,byte[] sig) throws Exception {
        der(sig);Signature verifier=Signature.getInstance("SHA256withECDSA");verifier.initVerify(key);verifier.update(frame);return verifier.verify(sig);
    }
    static String b64(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    static byte[] b64decode(String s) {
        if(!s.matches("[A-Za-z0-9_-]*")) throw new IllegalArgumentException("base64 alphabet");
        byte[] bytes=Base64.getUrlDecoder().decode(s);if(!b64(bytes).equals(s)) throw new IllegalArgumentException("noncanonical base64");return bytes;
    }
    static byte[][] copy(byte[][] fields) { return Arrays.stream(fields).map(byte[]::clone).toArray(byte[][]::new); }
    public static void main(String[] args) throws Exception {
        byte[][] fields=new byte[NAMES.length][];
        for(int i=0;i<fields.length;i++) { fields[i]=new byte[EXACT[i]==0?1:EXACT[i]];Arrays.fill(fields[i],(byte)(i+1)); }
        fields[0]=utf8("rebrowser.control/1");fields[1]=utf8("io.github.rebrowser");fields[2]=utf8("public-dummy-principal");fields[7]=ByteBuffer.allocate(8).putLong(3).array();fields[10]=ByteBuffer.allocate(8).putLong(30000).array();fields[12]=utf8("mail.query");fields[13]=utf8("account_synthetic");fields[14]=new byte[0];fields[15]=new byte[32];fields[16]=utf8("{\"query\":\"合成\",\"limit\":2}");
        byte[] frame=encode(fields);check(Arrays.deepEquals(decode(frame),fields));
        AlgorithmParameters parameters=AlgorithmParameters.getInstance("EC");parameters.init(new ECGenParameterSpec("secp256r1"));ECParameterSpec ec=parameters.getParameterSpec(ECParameterSpec.class);
        KeyFactory factory=KeyFactory.getInstance("EC");PrivateKey privateKey=factory.generatePrivate(new ECPrivateKeySpec(BigInteger.ONE,ec));PublicKey publicKey=factory.generatePublic(new ECPublicKeySpec(ec.getGenerator(),ec));
        Signature signer=Signature.getInstance("SHA256withECDSA");signer.initSign(privateKey);signer.update(frame);byte[] sig=signer.sign();check(verify(publicKey,frame,sig));
        for(int i=0;i<frame.length;i++) { byte[] changed=frame.clone();changed[i]^=1;check(!verify(publicKey,changed,sig)); }
        for(int i=0;i<NAMES.length;i++) { final int field=i;byte[][] changed=copy(fields);changed[field]=new byte[MAX[field]+1];reject(()->encode(changed)); }
        byte[] trailing=Arrays.copyOf(frame,frame.length+1);reject(()->decode(trailing));
        for(int n=0;n<frame.length;n++) { final byte[] shortFrame=Arrays.copyOf(frame,n);reject(()->decode(shortFrame)); }
        final byte[][] badRevision=copy(fields);badRevision[7][0]=(byte)0x80;reject(()->encode(badRevision));
        final byte[][] badUtf8=copy(fields);badUtf8[16]=new byte[]{(byte)0xc0,(byte)0xaf};reject(()->encode(badUtf8));
        boolean surrogateRejected=false;try { utf8("\ud800"); } catch(CharacterCodingException e) { surrogateRejected=true; }check(surrogateRejected);
        check(Arrays.equals(b64decode(b64(sig)),sig));reject(()->b64decode(b64(sig)+"="));reject(()->b64decode("AB"));
        BigInteger[] rs=der(sig);byte[] alternate=derEncode(rs[0],ORDER.subtract(rs[1]));check(verify(publicKey,frame,alternate));
        reject(()->der(new byte[]{0x30,6,2,1,0,2,1,1}));reject(()->der(Arrays.copyOf(sig,sig.length+1)));
        StringBuilder out=new StringBuilder("{\"scope\":\"HOST public scalar1 only; not Android/authorization implementation\",\"checks\":"+checks+",\"publicSpkiB64\":\""+b64(publicKey.getEncoded())+"\",\"frameB64\":\""+b64(frame)+"\",\"signatureB64\":\""+b64(sig)+"\",\"alternateValidSignatureB64\":\""+b64(alternate)+"\",\"frameSha256\":\""+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(frame))+"\",\"fieldHex\":{");
        for(int i=0;i<NAMES.length;i++) { if(i>0)out.append(',');out.append('\"').append(NAMES[i]).append("\":\"").append(HexFormat.of().formatHex(fields[i])).append('\"'); }
        System.out.println(out.append("}}").toString());
    }
}
