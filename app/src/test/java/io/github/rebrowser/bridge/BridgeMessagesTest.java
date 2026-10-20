package io.github.rebrowser.bridge;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** Strict bounded parser; parsed envelopes remain UNVERIFIED and grant nothing. */
public final class BridgeMessagesTest {
    private static final String HEX32="0123456789abcdef0123456789abcdef";
    private static final String HEX64="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static String binding(){
        return "{\"dataEpoch\":\""+HEX32+"\",\"partitionRef\":\"p1\",\"partitionGeneration\":0,"
                +"\"sessionRef\":\"s1\",\"sessionGeneration\":1,\"documentLease\":\""+HEX64+"\",\"portLeaseToken\":\""+HEX64+"\"}";
    }
    private static String hello(String hint){return "{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"HELLO\",\"connectorVersion\":\"1\",\"documentInstanceHint\":\""+hint+"\"}";}

    @Test public void validHelloParsesWithoutBecomingVerified(){
        BridgeMessages.Message message=BridgeMessages.parse(hello("hint"));
        assertEquals(BridgeMessages.Kind.HELLO,message.kind());
        assertFalse(message.hello().isVerified());
        assertEquals("hint",message.hello().documentInstanceHint());
    }
    @Test public void readyBindingIsFieldCheckedButUnverified(){
        String json="{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"READY\",\"binding\":"+binding()+",\"limits\":{\"a\":1},\"supportedActions\":[\"x\"]}";
        BridgeMessages.Ready ready=BridgeMessages.parse(json).ready();
        assertFalse(ready.binding().isVerified());
        assertEquals("p1",ready.binding().partitionRef());
        assertEquals(1,ready.binding().sessionGeneration());
        assertEquals(BridgeMessages.MAX_SAFE_INTEGER,BridgeMessages.MAX_SAFE_INTEGER);
    }
    @Test public void duplicateUnknownAndMalformedFieldsFailClosed(){
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse("{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"HELLO\",\"connectorVersion\":\"1\",\"connectorVersion\":\"2\",\"documentInstanceHint\":\"h\"}"));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse("{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"HELLO\",\"connectorVersion\":\"1\",\"documentInstanceHint\":\"h\",\"extra\":1}"));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(hello("h").replace("\"HELLO\"","\"HELLO2\"")));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(hello("h").replace("rebrowser.connector/1","rebrowser.connector/2")));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse("[]"));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(hello("h")+"{}"));
    }
    @Test public void utf8BomAndControlCharactersAreRejected(){
        byte[] bom=("\ufeff"+hello("h")).getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(bom));
        byte[] badUtf8={'{','"','a','"',':','"',(byte)0xc3,'"'};
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(badUtf8));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(hello("a\u0001b")));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(hello("\\ud800")));
    }
    @Test public void stringApiRejectsUnpairedUtf16BeforeEncoderReplacement(){
        String loneHigh=String.valueOf((char)0xD800);
        String loneLow=String.valueOf((char)0xDC00);
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(hello(loneHigh)));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(hello(loneLow)));
        assertEquals(BridgeMessages.Kind.HELLO,BridgeMessages.parse(hello("\\uD83D\\uDE00")).kind());
    }
    @Test public void unicodeEscapesAcceptAsciiHexOnly(){
        String malformed=hello("\\u" + "\u0661" + "234"); // Arabic-Indic 1 is not ASCII 1
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(malformed));
        assertEquals("a",BridgeMessages.parse(hello("\\u0061")).hello().documentInstanceHint());
    }
    @Test public void numericGrammarRejectsUnicodeDigitsInEveryNumberPosition(){
        String start="{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"EVENT\",\"binding\":"+binding()+",\"eventSequence\":";
        String end=",\"eventType\":\"t\",\"resourceRef\":\"r\",\"payload\":{}}";
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(start+"\u0661"+end));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(start+"1\u0661"+end));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(start+"1.\u0661"+end));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(start+"1e\u0661"+end));
        assertEquals(1,BridgeMessages.parse(start+"1"+end).event().eventSequence());
    }
    @Test public void oversizeDepthAndArrayBoundsAreEnforced(){
        StringBuilder deep=new StringBuilder();
        for(int i=0;i<20;i++)deep.append('[');
        deep.append('0');
        for(int i=0;i<20;i++)deep.append(']');
        String value="{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"COMMAND\",\"binding\":"+binding()+",\"requestId\":\"r_"+HEX32+"\",\"action\":\"a\",\"args\":"+deep+"}";
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(value));
        String big=hello(new String(new char[300]).replace('\0','h'));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(big));
        byte[] huge=new byte[BridgeMessages.MAX_ENVELOPE_BYTES+1];
        java.util.Arrays.fill(huge,(byte)' ');
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(huge));
    }
    @Test public void eventSequenceMustBeASafeExactInteger(){
        String base="{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"EVENT\",\"binding\":"+binding()+",\"eventSequence\":";
        String tail=",\"eventType\":\"t\",\"resourceRef\":\"r\",\"payload\":{}}";
        assertEquals(1,BridgeMessages.parse(base+"1"+tail).event().eventSequence());
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(base+"1.5"+tail));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(base+"-1"+tail));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(base+"9007199254740992"+tail));
        assertThrows(IllegalArgumentException.class,()->BridgeMessages.parse(base+"\"1\""+tail));
    }
    @Test public void resultOutcomeIsDataNotASuccessReceipt(){
        String json="{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"RESULT\",\"binding\":"+binding()
                +",\"requestId\":\"r_"+HEX32+"\",\"outcome\":\"unknown\",\"evidenceKind\":\"none\",\"result\":{\"x\":true}}";
        BridgeMessages.Result result=BridgeMessages.parse(json).result();
        assertEquals("unknown",result.outcome());
        assertTrue(result.result().object().get("x").bool());
    }
}
