package io.github.rebrowser.control;

import static org.junit.Assert.*;

import io.github.rebrowser.control.ControlEnvelope.Field;
import io.github.rebrowser.control.ControlDecoder.UnverifiedProof;
import io.github.rebrowser.control.ControlDecoder.Value;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Random;
import org.junit.Test;

/** Parser/transport syntax only. Even the synthetic signatures below are NOT valid proofs. */
public final class ControlDecoderTest {
    private static final byte[] SIGNATURE={0x30,6,2,1,1,2,1,1}; // DER shape valid, authenticity absent
    private static byte[] utf8(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static EnumMap<Field,byte[]> fields(String args) {
        int[] exact={0,0,0,32,16,32,32,8,16,32,8,16,0,0,0,32,0};
        EnumMap<Field,byte[]> map=new EnumMap<>(Field.class);
        for(Field f:Field.values())map.put(f,new byte[exact[f.ordinal()]]);
        map.put(Field.PROTOCOL,utf8("rebrowser.control/1"));map.put(Field.APPLICATION_ID,utf8("io.github.rebrowser"));
        map.put(Field.PRINCIPAL_KEY_ID,utf8("synthetic-unverified"));map.put(Field.ACTION_ID,utf8("mail.query"));
        map.put(Field.SCOPE_REF,utf8("account_synthetic"));map.put(Field.ARGS_BYTES,utf8(args));return map;
    }
    private static String wireText(EnumMap<Field,byte[]> map,String extra) {
        return "{\"protocol\":\"rebrowser.control/1\",\"frameB64\":\""+CryptoEncoding.base64Url(ControlEnvelope.fromFields(map).signedBytes())
            +"\",\"signatureB64\":\""+CryptoEncoding.base64Url(SIGNATURE)+"\""+extra+"}";
    }
    private static byte[] wire(String args){return utf8(wireText(fields(args),""));}
    private static UnverifiedProof decode(String args){return ControlDecoder.decode(wire(args));}
    private static void reject(byte[] input) {
        try{ControlDecoder.decode(input);fail("must reject invalid transport/syntax");}
        catch(IllegalArgumentException expected){assertEquals("Invalid control proof envelope",expected.getMessage());}
    }
    private static void rejectArgs(String args){reject(wire(args));}
    private static void wrongType(Runnable action){try{action.run();fail("no coercion");}catch(IllegalArgumentException expected){}}

    @Test public void typedDtoTreeAndOriginalBytesAreDistinct() {
        String args=" { \"q\":\"合成😀\",\"limit\":2,\"ok\":true,\"no\":false,\"n\":null,\"a\":[1,{}] } \n";
        UnverifiedProof proof=decode(args);
        assertEquals("合成😀",proof.args().object().get("q").string());
        assertEquals("2",proof.args().object().get("limit").numberToken());
        assertTrue(proof.args().object().get("ok").bool());assertFalse(proof.args().object().get("no").bool());
        assertEquals(Value.Kind.NULL,proof.args().object().get("n").kind());
        assertEquals(2,proof.args().object().get("a").array().size());
        assertArrayEquals(utf8(args),proof.frame().bytes(Field.ARGS_BYTES));assertEquals(0,proof.delegation().length);
    }

    @Test public void wireIsExactlyOneObjectWithRequiredStringFields() {
        for(String raw:new String[]{"{}","[]","null","false","1","\"x\"",
            "{\"protocol\":null}","{\"protocol\":\"rebrowser.control/1\",\"frameB64\":1,\"signatureB64\":true}"})reject(utf8(raw));
        String good=wireText(fields("{}"),"");
        reject(utf8(good.substring(0,good.length()-1)+",\"extra\":0}"));
        reject(utf8(good.replace("\"protocol\":\"rebrowser.control/1\",","")));
        reject(utf8(good.replace("rebrowser.control/1","rebrowser.control/2")));
        reject(utf8(good.replace("\"signatureB64\":\"MAYCAQECAQE\"","\"signatureB64\":false")));
    }

    @Test public void duplicateDecodedOuterKeysIncludingUnicodeAliasesAreRejected() {
        for(String key:new String[]{"protocol","\\u0070rotocol"}) {
            reject(utf8(wireText(fields("{}"),",\""+key+"\":\"rebrowser.control/1\"")));
        }
        reject(utf8(wireText(fields("{}"),",\"frameB64\":\"\"")));
    }

    @Test public void duplicateArgsKeysRejectAtEveryDepthAndForNullValues() {
        for(String args:new String[]{"{\"a\":1,\"a\":2}","{\"a\":null,\"a\":1}","{\"a\":1,\"\\u0061\":2}",
            "{\"o\":{\"x\":0,\"x\":1}}","{\"a\":[{\"x\":0,\"x\":1}]}"})rejectArgs(args);
    }

    @Test public void validEscapesAndSupplementaryUnicodeDecodeWithoutNormalization() {
        assertEquals("😀",decode("{\"q\":\"\\ud83d\\ude00\"}").args().object().get("q").string());
        assertEquals("\"\\/\b\f\n\r\t",decode("{\"q\":\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"}").args().object().get("q").string());
        assertEquals(2,decode("{\"é\":1,\"é\":2}").args().object().size());
        assertEquals("\0",decode("{\"q\":\"\\u0000\"}").args().object().get("q").string());
    }

    @Test public void malformedEscapesAndLoneSurrogatesAreRejected() {
        for(String text:new String[]{"\\ud800","\\udc00","\\ud800x","\\ud800\\u0041","\\udc00\\ud800","\\u12","\\uxxxx","\\x","\\U0001"}) {
            rejectArgs("{\"q\":\""+text+"\"}");
        }
        for(int c=0;c<32;c++)rejectArgs("{\"q\":\""+(char)c+"\"}");
    }

    @Test public void numbersPreserveExactTokensNeverCoerceOrRound() {
        for(String number:new String[]{"0","-0","1","-2","1.00","1e2","1E+2","1e-2","9223372036854775808","1e999999999999999"}) {
            Value value=decode("{\"n\":"+number+"}").args().object().get("n");assertEquals(number,value.numberToken());
            wrongType(value::string);wrongType(value::bool);
        }
        Value quoted=decode("{\"n\":\"2\"}").args().object().get("n");wrongType(quoted::numberToken);
        Value bool=decode("{\"n\":true}").args().object().get("n");wrongType(bool::numberToken);
    }

    @Test public void invalidNumberGrammarAndConstantsAreRejected() {
        for(String bad:new String[]{"00","01","-01","+1",".1","1.","1e","1e+","--1","NaN","Infinity","-Infinity","0x10","1_0"})rejectArgs("{\"n\":"+bad+"}");
        char[] digits=new char[ControlDecoder.MAX_NUMBER_TOKEN_CHARS];Arrays.fill(digits,'1');
        assertEquals(digits.length,decode("{\"n\":"+new String(digits)+"}").args().object().get("n").numberToken().length());
        rejectArgs("{\"n\":"+new String(digits)+"1}");
    }

    @Test public void trailingTokensCommentsAndNonJsonWhitespaceAreRejected() {
        for(String bad:new String[]{"{}{}","{};","{\"a\":1,}","{\"a\":[1,]}","{/*x*/}","{a:1}","{'a':1}","\ufeff{}","\u00a0{}","{\"a\":truefalse}","{\"a\":\"unfinished}"})rejectArgs(bad);
        String text=wireText(fields("{}"),"");reject(utf8(text+"{}"));reject(utf8("\ufeff"+text));
        assertNotNull(ControlDecoder.decode(utf8(" \t\n\r"+text+" \t\r\n")));
    }

    @Test public void argsRootMustBeObjectAndNotEmptyOrScalar() {
        for(String bad:new String[]{"[]","null","true","1","\"object\""," "})rejectArgs(bad);
    }

    @Test public void boundedContainerDepthIsCheckedBeforeRecursion() {
        StringBuilder good=new StringBuilder("{\"a\":");for(int i=1;i<ControlDecoder.MAX_CONTAINER_DEPTH;i++)good.append('[');
        good.append('0');for(int i=1;i<ControlDecoder.MAX_CONTAINER_DEPTH;i++)good.append(']');good.append('}');
        assertNotNull(decode(good.toString()));
        rejectArgs(good.toString().replace("0","[0]"));
    }

    @Test public void valueNodeBudgetHasExactBoundary() {
        StringBuilder args=new StringBuilder("{\"a\":[");
        for(int i=0;i<ControlDecoder.MAX_VALUE_NODES-2;i++){if(i!=0)args.append(',');args.append('0');}args.append("]}");
        assertNotNull(decode(args.toString()));
        rejectArgs(args.toString().replace("]}",",0]}"));
    }

    @Test public void immutableTreeAndProofCopiesCannotBeChangedAfterDecode() {
        byte[] input=wire("{\"o\":{\"x\":1},\"a\":[1]}");UnverifiedProof proof=ControlDecoder.decode(input);
        Arrays.fill(input,(byte)0);byte[] sig=proof.signature();Arrays.fill(sig,(byte)0);assertArrayEquals(SIGNATURE,proof.signature());
        try{proof.args().object().clear();fail();}catch(UnsupportedOperationException expected){}
        try{proof.args().object().get("o").object().clear();fail();}catch(UnsupportedOperationException expected){}
        try{proof.args().object().get("a").array().clear();fail();}catch(UnsupportedOperationException expected){}
        wrongType(proof.args()::string);
    }

    @Test public void delegationBytesMustMatchSignedDigestButCertificateIsStillUnverified() {
        byte[] bundle={1,2,3};EnumMap<Field,byte[]> map=fields("{}");map.put(Field.DELEGATION_SHA256,CryptoEncoding.sha256(bundle));
        UnverifiedProof proof=ControlDecoder.decode(utf8(wireText(map,",\"delegationB64\":\""+CryptoEncoding.base64Url(bundle)+"\"")));
        assertArrayEquals(bundle,proof.delegation());byte[] returned=proof.delegation();returned[0]=0;assertArrayEquals(bundle,proof.delegation());
        reject(utf8(wireText(map,"")));
        reject(utf8(wireText(map,",\"delegationB64\":\"\"")));
        reject(utf8(wireText(map,",\"delegationB64\":\"AQIE\"")));
        reject(utf8(wireText(fields("{}"),",\"delegationB64\":\"AQID\"")));
        reject(utf8(wireText(fields("{}"),",\"delegationB64\":\"\"")));
    }

    @Test public void delegationAndBinaryEncodingBoundsAreEnforced() {
        byte[] bundle=new byte[8192];EnumMap<Field,byte[]> map=fields("{}");map.put(Field.DELEGATION_SHA256,CryptoEncoding.sha256(bundle));
        assertEquals(8192,ControlDecoder.decode(utf8(wireText(map,",\"delegationB64\":\""+CryptoEncoding.base64Url(bundle)+"\""))).delegation().length);
        reject(utf8(wireText(map,",\"delegationB64\":\""+CryptoEncoding.base64Url(new byte[8193])+"\"")));
        String good=wireText(fields("{}"),"");
        reject(utf8(good.replace("MAYCAQECAQE","MAYCAQECAQE=")));
        reject(utf8(good.replace("MAYCAQECAQE","AA")));
        reject(utf8(good.replace("MAYCAQECAQE","MAYCAQACAQE"))); // zero r
    }

    @Test public void originalUtf8WireAndSizeLimitsAreStrict() {
        reject(null);reject(new byte[ControlDecoder.MAX_WIRE_BYTES+1]);
        byte[] malformed=wire("{}");malformed[0]=(byte)0xff;reject(malformed);
        byte[] good=wire("{}");byte[] atLimit=new byte[ControlDecoder.MAX_WIRE_BYTES];Arrays.fill(atLimit,(byte)' ');System.arraycopy(good,0,atLimit,0,good.length);
        assertNotNull(ControlDecoder.decode(atLimit));
    }

    @Test public void parsingDoesNotCheckActionSchemasAuthenticateOrGrantRoles() {
        EnumMap<Field,byte[]> map=fields("{\"unknown\":true,\"claimedRoot\":true}");map.put(Field.ACTION_ID,utf8("not.registered"));
        UnverifiedProof proof=ControlDecoder.decode(utf8(wireText(map,"")));
        assertEquals("not.registered",proof.frame().ascii(Field.ACTION_ID));assertTrue(proof.args().object().get("claimedRoot").bool());
        // The immutable result is explicitly UNVERIFIED; schema/key/policy/challenge must reject it later.
        assertArrayEquals(SIGNATURE,proof.signature());
    }

    @Test public void seededMalformedWireNeverLeaksUnexpectedParserExceptions() {
        Random random=new Random(0x4a534f4eL);byte[] good=wire("{\"q\":\"合成😀\",\"limit\":2}");
        for(int i=0;i<2000;i++) {
            byte[] candidate=i%2==0?good.clone():new byte[random.nextInt(600)];
            if(i%2==0)candidate[random.nextInt(candidate.length)]^=(byte)(1+random.nextInt(255));else random.nextBytes(candidate);
            try {UnverifiedProof proof=ControlDecoder.decode(candidate);assertNotNull(proof.args().object());}
            catch(IllegalArgumentException expected){assertEquals("Invalid control proof envelope",expected.getMessage());}
        }
    }
}
