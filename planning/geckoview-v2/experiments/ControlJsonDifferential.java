import io.github.rebrowser.control.ControlDecoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** HOST stdin fixture bridge only. Emits typed AST events, never performs admission. */
public final class ControlJsonDifferential {
    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static void events(ControlDecoder.Value value, StringBuilder out) {
        switch(value.kind()) {
            case OBJECT:
                out.append("O ").append(value.object().size()).append('\n');
                for(Map.Entry<String,ControlDecoder.Value> entry:value.object().entrySet()) {
                    out.append("K ").append(b64(entry.getKey())).append('\n');events(entry.getValue(),out);
                }
                break;
            case ARRAY:
                out.append("A ").append(value.array().size()).append('\n');
                for(ControlDecoder.Value item:value.array())events(item,out);
                break;
            case STRING: out.append("S ").append(b64(value.string())).append('\n');break;
            case NUMBER: out.append("N ").append(value.numberToken()).append('\n');break;
            case BOOLEAN: out.append(value.bool()?"T\n":"F\n");break;
            case NULL: out.append("Z\n");break;
            default: throw new AssertionError();
        }
    }
    public static void main(String[] args) throws Exception {
        BufferedReader reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
        String line;
        while((line=reader.readLine())!=null) {
            try {
                if(line.length()>100000)throw new IllegalArgumentException();
                ControlDecoder.UnverifiedProof proof=ControlDecoder.decode(Base64.getUrlDecoder().decode(line));
                StringBuilder out=new StringBuilder();events(proof.args(),out);System.out.println(b64(out.toString()));
            } catch(IllegalArgumentException invalid) {System.out.println("REJECT");}
        }
    }
}
