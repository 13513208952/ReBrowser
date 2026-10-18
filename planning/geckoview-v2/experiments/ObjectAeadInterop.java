import io.github.rebrowser.crypto.ObjectAead;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

/** HOST public dummy DEK only, legacy R9 whole-array fixture vs production streaming. */
public final class ObjectAeadInterop {
    private static int checks;
    private static void yes(boolean value){if(!value)throw new AssertionError("interop "+checks);checks++;}
    public static void main(String[] args)throws Exception {
        MailObjectFormatVectors.main(new String[0]); // rerun pinned RFC/NIST and64 historical dummy checks
        int id=100;
        for(int length:new int[]{0,1,32768,32769,65537,262144})for(int codec=0;codec<2;codec++) {
            byte[] plain=new byte[length];new Random(length).nextBytes(plain);
            byte[] packed=codec==0?plain:MailObjectFormatVectors.compress(plain);
            byte[] object=ByteBuffer.allocate(16).putLong(0).putLong(id++).array();
            ObjectAead.Codec kind=codec==0?ObjectAead.Codec.RAW:ObjectAead.Codec.ZLIB;
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(plain);
            ObjectAead.Limits limits=new ObjectAead.Limits(1048576,1048576);
            ObjectAead.Metadata expected=ObjectAead.catalogMetadata(limits,kind,ObjectAead.Purpose.BODY,1,
                MailObjectFormatVectors.EPOCH,MailObjectFormatVectors.ACCOUNT,object,plain.length,packed.length,digest);
            ByteArrayOutputStream opened=new ByteArrayOutputStream();
            ObjectAead.open(expected,MailObjectFormatVectors.DUMMY_DEK,new ByteArrayInputStream(MailObjectFormatVectors.encode(plain,codec,object)),opened,()->false);
            yes(Arrays.equals(packed,opened.toByteArray()));
            ObjectAead.WritePlan fresh=ObjectAead.freshWrite(limits,kind,ObjectAead.Purpose.BODY,1,
                MailObjectFormatVectors.EPOCH,MailObjectFormatVectors.ACCOUNT,plain.length,packed.length,digest);
            ByteArrayOutputStream sealed=new ByteArrayOutputStream();
            ObjectAead.seal(fresh,MailObjectFormatVectors.DUMMY_DEK,new ByteArrayInputStream(packed),sealed,()->false);
            byte[] wire=sealed.toByteArray();
            yes(Arrays.equals(plain,MailObjectFormatVectors.decode(wire,fresh.metadata().objectId())));
            // Deterministic reference ciphertext for exact SAME public fixture representation.
            // No real key and no production re-encrypt-existing-ID API is exercised.
            yes(Arrays.equals(wire,MailObjectFormatVectors.encode(plain,codec,fresh.metadata().objectId())));
        }
        System.out.println("{\"oldAndProductionInteropChecks\":"+checks+",\"scope\":\"HOST public dummy DEK, same provider; not Android or data custody\"}");
    }
}
