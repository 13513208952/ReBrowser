/* HOST DESIGN VECTORS ONLY. Not application crypto/storage code.
 * All keys/IDs are fixed public dummy values. No provisioning, credentials,
 * Android APIs, real files, networking or data deletion. Whole byte arrays here
 * are bounded test fixtures; the product design requires streamed IO.
 */
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class MailObjectFormatVectors {
  static int checks;
  static final byte[] EPOCH = new byte[16];
  static final byte[] ACCOUNT = fill(16, 0x22);
  static final byte[] DUMMY_DEK = fill(32, 0x11);
  static byte[] fill(int n, int value) { byte[] b = new byte[n]; Arrays.fill(b, (byte)value); return b; }
  static byte[] hex(String s) { byte[] r = new byte[s.length()/2]; for(int i=0;i<r.length;i++) r[i]=(byte)Integer.parseInt(s.substring(2*i,2*i+2),16); return r; }
  static void yes(boolean ok) { if(!ok) throw new AssertionError("vector mismatch"); checks++; }
  interface Operation { void run() throws Exception; }
  static void rejects(Operation op) throws Exception { boolean failed=false; try { op.run(); } catch(Exception expected) { failed=true; } yes(failed); }
  static byte[] join(byte[]... parts) throws Exception { ByteArrayOutputStream out=new ByteArrayOutputStream(); for(byte[] p:parts)out.write(p); return out.toByteArray(); }
  static byte[] hmac(byte[] key, byte[] value) throws Exception { Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(value); }
  static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int n) throws Exception {
    byte[] prk=hmac(salt,ikm), previous=new byte[0]; ByteArrayOutputStream out=new ByteArrayOutputStream();
    for(int i=1;out.size()<n;i++){previous=hmac(prk,join(previous,info,new byte[]{(byte)i}));out.write(previous);}
    return Arrays.copyOf(out.toByteArray(),n);
  }
  static byte[] objectKey(byte[] epoch, byte[] account, byte[] object, int purpose) throws Exception {
    return hkdf(DUMMY_DEK,epoch,join("RBMOBJ1-KEY\0".getBytes(StandardCharsets.US_ASCII),account,object,ByteBuffer.allocate(4).putShort((short)purpose).putShort((short)1).array()),32);
  }
  static byte[] aad(byte[] header,int index,int size) throws Exception { return join("RBMOBJ1-AAD\0".getBytes(StandardCharsets.US_ASCII),header,ByteBuffer.allocate(8).putInt(index).putInt(size).array()); }
  static byte[] crypt(int mode, byte[] key, int index, byte[] associated, byte[] value) throws Exception {
    Cipher c=Cipher.getInstance("AES/GCM/NoPadding");byte[] nonce=ByteBuffer.allocate(12).putLong(0).putInt(index).array();
    c.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));c.updateAAD(associated);return c.doFinal(value);
  }
  static byte[] compress(byte[] plain) throws Exception {
    Deflater d=new Deflater(9);try {d.setInput(plain);d.finish();ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[4096];while(!d.finished()){int n=d.deflate(b);if(n==0)throw new IllegalStateException();o.write(b,0,n);}return o.toByteArray();}finally{d.end();}
  }
  static byte[] encode(byte[] plain,int codec,byte[] object) throws Exception {
    byte[] packed=codec==0?plain:compress(plain);int count=Math.max(1,(packed.length+32767)/32768);
    byte[] header=ByteBuffer.allocate(124).put("RBMOBJ1\0".getBytes(StandardCharsets.US_ASCII)).putShort((short)1).putShort((short)1).putShort((short)codec).putShort((short)1).putInt(1).put(EPOCH).put(ACCOUNT).put(object).putLong(plain.length).putLong(packed.length).putInt(32768).putInt(count).put(MessageDigest.getInstance("SHA-256").digest(plain)).array();
    byte[] key=objectKey(EPOCH,ACCOUNT,object,1);ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(header);
    for(int i=0,off=0;i<count;i++){int n=Math.min(32768,packed.length-off);byte[] cipher=crypt(Cipher.ENCRYPT_MODE,key,i,aad(header,i,n),Arrays.copyOfRange(packed,off,off+n));out.write(ByteBuffer.allocate(4).putInt(cipher.length).array());out.write(cipher);off+=n;}
    return out.toByteArray();
  }
  static byte[] decode(byte[] wire,byte[] expectedObject) throws Exception {
    if(wire.length<144)throw new IllegalArgumentException("short");ByteBuffer in=ByteBuffer.wrap(wire);byte[] header=Arrays.copyOf(wire,124),magic=new byte[8];in.get(magic);
    if(!Arrays.equals(magic,"RBMOBJ1\0".getBytes(StandardCharsets.US_ASCII))||in.getShort()!=1||in.getShort()!=1)throw new IllegalArgumentException("format");
    int codec=in.getShort(),purpose=in.getShort(),keyVersion=in.getInt();byte[] epoch=new byte[16],account=new byte[16],object=new byte[16];in.get(epoch).get(account).get(object);
    long original=in.getLong(),packedLen=in.getLong();int chunk=in.getInt(),count=in.getInt();byte[] digest=new byte[32];in.get(digest);
    if((codec!=0&&codec!=1)||purpose!=1||keyVersion!=1||!Arrays.equals(epoch,EPOCH)||!Arrays.equals(account,ACCOUNT)||!Arrays.equals(object,expectedObject)||original<0||original>1048576||packedLen<0||packedLen>1048576||chunk!=32768||count!=Math.max(1,(packedLen+32767)/32768))throw new IllegalArgumentException("bounds/identity");
    byte[] key=objectKey(epoch,account,object,purpose);ByteArrayOutputStream compressed=new ByteArrayOutputStream();
    for(int i=0;i<count;i++){int size=(int)Math.min(32768,packedLen-compressed.size());int cLen=in.getInt();if(cLen!=size+16||cLen>in.remaining())throw new IllegalArgumentException("frame");byte[] encrypted=new byte[cLen];in.get(encrypted);compressed.write(crypt(Cipher.DECRYPT_MODE,key,i,aad(header,i,size),encrypted));}
    if(in.hasRemaining()||compressed.size()!=packedLen)throw new IllegalArgumentException("trailing/truncated");byte[] decoded;
    if(codec==0){decoded=compressed.toByteArray();}else{Inflater z=new Inflater();try{z.setInput(compressed.toByteArray());ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[4096];while(!z.finished()){int n=z.inflate(b);if(n==0&&!z.finished())throw new IllegalArgumentException("truncated deflate");if(o.size()+n>original)throw new IllegalArgumentException("output bound");o.write(b,0,n);}if(z.getRemaining()!=0)throw new IllegalArgumentException("trailing deflate");decoded=o.toByteArray();}finally{z.end();}}
    if(decoded.length!=original||!Arrays.equals(MessageDigest.getInstance("SHA-256").digest(decoded),digest))throw new IllegalArgumentException("integrity");return decoded;
  }
  public static void main(String[] args) throws Exception {
    byte[] okm=hkdf(fill(22,0x0b),hex("000102030405060708090a0b0c"),hex("f0f1f2f3f4f5f6f7f8f9"),42);
    yes(Arrays.equals(okm,hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865")));
    Cipher nist=Cipher.getInstance("AES/GCM/NoPadding");nist.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(new byte[32],"AES"),new GCMParameterSpec(128,new byte[12]));
    yes(Arrays.equals(nist.doFinal(new byte[16]),hex("cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919")));
    int id=1;for(int len:new int[]{0,1,32768,32769,65537,262144})for(int codec=0;codec<=1;codec++){
      byte[] plain=new byte[len];new Random(len).nextBytes(plain);byte[] object=ByteBuffer.allocate(16).putLong(0).putLong(id++).array(),wire=encode(plain,codec,object);
      yes(Arrays.equals(plain,decode(wire,object)));byte[] bad=wire.clone();bad[bad.length-1]^=1;rejects(()->decode(bad,object));
      rejects(()->decode(Arrays.copyOf(wire,wire.length-1),object));rejects(()->decode(Arrays.copyOf(wire,wire.length+1),object));
      byte[] altered=wire.clone();altered[40]^=1;rejects(()->decode(altered,object));
    }
    byte[] object=ByteBuffer.allocate(16).putLong(0).putLong(id++).array(),wire=encode(new byte[65536],0,object);
    byte[] reordered=wire.clone();int frame=32768+16+4;System.arraycopy(wire,124+frame,reordered,124,frame);System.arraycopy(wire,124,reordered,124+frame,frame);rejects(()->decode(reordered,object));
    byte[] changedHeader=wire.clone();changedHeader[120]^=1;rejects(()->decode(changedHeader,object));
    System.out.println("{\"hostFixedCryptoAndFormatChecks\":"+checks+",\"headerBytes\":124,\"realKeysProvisioned\":false,\"AndroidCryptoValidated\":false}");
  }
}
