package io.github.rebrowser.bridge;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strict, bounded parser for the READ-ONLY connector protocol `rebrowser.connector/1`. This is
 * NOT the administrator control protocol and it grants nothing: parsed messages are explicitly
 * UNVERIFIED, never a DocumentLease/READY capability, sender/port proof, account identity,
 * command admission or routing decision. Callers must validate the actual native sender/session/
 * document/port before any action; duplicate keys, unknown fields, coercion, non-finite numbers,
 * oversized envelopes and malformed UTF-8 all fail closed.
 */
public final class BridgeMessages {
    public static final String PROTOCOL = "rebrowser.connector/1";
    public static final int MAX_ENVELOPE_BYTES = 65_536;
    public static final int MAX_DEPTH = 12;
    public static final int MAX_FIELDS = 64;
    public static final int MAX_ARRAY = 256;
    public static final int MAX_STRING_CHARS = 8192;
    public static final int MAX_NODES = 4096;
    public static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    public static final int MAX_CHUNK_BYTES = 32_768;
    private static final int MAX_JSON_STRING_CHARS=43692; // only STREAM_CHUNK.base64 may exceed ordinary string limit

    public enum Kind {
        HELLO, READY, COMMAND, ACCEPTED, RESULT, EVENT, CANCEL, CANCEL_STATUS,
        STREAM_BEGIN, STREAM_CREDIT, STREAM_CHUNK, STREAM_END, ERROR
    }
    public enum ValueType { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }

    /** Immutable bounded JSON value with no reflection or implicit conversion. */
    public static final class Value {
        private final ValueType type;private final Map<String,Value> object;private final List<Value> array;
        private final String string;private final long integer;private final double floating;private final boolean integral,booleanValue;
        private Value(ValueType type,Map<String,Value> object,List<Value> array,String string,long integer,double floating,boolean integral,boolean booleanValue){
            this.type=type;this.object=object;this.array=array;this.string=string;this.integer=integer;this.floating=floating;this.integral=integral;this.booleanValue=booleanValue;
        }
        public ValueType type(){return type;}
        public Map<String,Value> object(){if(type!=ValueType.OBJECT)throw new IllegalArgumentException("Not an object");return object;}
        public List<Value> array(){if(type!=ValueType.ARRAY)throw new IllegalArgumentException("Not an array");return array;}
        public String string(){if(type!=ValueType.STRING)throw new IllegalArgumentException("Not a string");return string;}
        public boolean bool(){if(type!=ValueType.BOOLEAN)throw new IllegalArgumentException("Not a boolean");return booleanValue;}
        public boolean isIntegral(){return type==ValueType.NUMBER && integral;}
        public long asLong(){if(!isIntegral())throw new IllegalArgumentException("Not an integral number");return integer;}
        public double asDouble(){if(type!=ValueType.NUMBER)throw new IllegalArgumentException("Not a number");return integral?integer:floating;}
        public String displayType(){return type==ValueType.NUMBER?(integral?"integer":"number"):type.name().toLowerCase(java.util.Locale.ROOT);}
    }

    /** Parsed but UNVERIFIED envelope; payload accessors re-check required/unknown fields. */
    public static final class Message {
        private final Kind kind;private final Value root;
        private Message(Kind kind,Value root){this.kind=kind;this.root=root;}
        public Kind kind(){return kind;}
        public Value root(){return root;}
        public Hello hello(){require(Kind.HELLO);return new Hello(text(required("connectorVersion"),64),text(required("documentInstanceHint"),256));}
        public Ready ready(){require(Kind.READY);Value limits=required("limits"),actions=required("supportedActions");if(limits.type()!=ValueType.OBJECT || actions.type()!=ValueType.ARRAY)throw invalid("READY shapes");java.util.Set<String> seen=new java.util.HashSet<>();for(Value action:actions.array())if(!seen.add(ascii(action,128)))throw invalid("duplicate action");return new Ready(binding(),limits,actions);}
        public Command command(){require(Kind.COMMAND);return new Command(binding(),requestId(),ascii(required("action"),128),required("args"));}
        public Accepted accepted(){require(Kind.ACCEPTED);return new Accepted(binding(),requestId());}
        public Result result(){require(Kind.RESULT);return new Result(binding(),requestId(),text(required("outcome"),64),text(required("evidenceKind"),64),required("result"));}
        public Event event(){require(Kind.EVENT);return new Event(binding(),strictInteger("eventSequence"),text(required("eventType"),64),text(required("resourceRef"),256),required("payload"));}
        public Cancel cancel(){require(Kind.CANCEL);return new Cancel(binding(),requestId());}
        public CancelStatus cancelStatus(){require(Kind.CANCEL_STATUS);return new CancelStatus(binding(),requestId(),text(required("cancelState"),32));}
        public StreamBegin streamBegin(){require(Kind.STREAM_BEGIN);return new StreamBegin(binding(),requestId(),streamId(),text(required("purpose"),64),strictInteger("declaredBytes"));}
        public StreamCredit streamCredit(){require(Kind.STREAM_CREDIT);return new StreamCredit(binding(),streamId(),strictInteger("nextSequence"),strictInteger("creditBytes"),strictInteger("maxTotalBytes"));}
        public StreamChunk streamChunk(){require(Kind.STREAM_CHUNK);long raw=strictInteger("rawBytes");String encoded=text(required("base64"),MAX_JSON_STRING_CHARS);if(raw<1 || raw>MAX_CHUNK_BYTES || encoded.length()!=4*((raw+2)/3))throw invalid("chunk size");byte[] decoded;try{decoded=java.util.Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException bad){throw invalid("chunk base64");}if(decoded.length!=raw || !java.util.Base64.getEncoder().encodeToString(decoded).equals(encoded))throw invalid("noncanonical chunk");return new StreamChunk(binding(),streamId(),strictInteger("sequence"),raw,encoded);}
        public StreamEnd streamEnd(){require(Kind.STREAM_END);return new StreamEnd(binding(),streamId(),strictInteger("totalBytes"),pattern(required("sha256"),"^[0-9a-f]{64}$"));}
        public Error error(){require(Kind.ERROR);return new Error(optional("binding")==null?null:binding(),optional("requestId")==null?null:requestId(),text(required("errorCode"),64),text(required("retryClass"),32));}
        public Binding binding(){
            Value value=required("binding");
            if(value.type()!=ValueType.OBJECT)throw invalid("binding must be an object");
            Map<String,Value> fields=value.object();
            requireExact(fields,"binding",new String[]{"dataEpoch","partitionRef","partitionGeneration","sessionRef","sessionGeneration","documentLease","portLeaseToken"},new String[0]);
            return new Binding(hex(fields.get("dataEpoch"),32),ascii(fields.get("partitionRef"),64),generation(fields.get("partitionGeneration")),
                    ascii(fields.get("sessionRef"),64),generation(fields.get("sessionGeneration")),hex(fields.get("documentLease"),64),hex(fields.get("portLeaseToken"),64));
        }
        private String requestId(){return pattern(required("requestId"),"^r_[0-9a-f]{32}$");}
        private String streamId(){return pattern(required("streamId"),"^b_[0-9a-f]{32}$");}
        private Value required(String name){Value value=root.object().get(name);if(value==null)throw invalid("missing field "+name);return value;}
        private Value optional(String name){return root.object().get(name);}
        private long strictInteger(String name){return safeInteger(required(name));}
        private void require(Kind expected){if(kind!=expected)throw invalid("expected "+expected+" but got "+kind);}
    }

    public static final class Hello {
        private final String connectorVersion,documentInstanceHint;
        Hello(String version,String hint){connectorVersion=version;documentInstanceHint=hint;}
        public String connectorVersion(){return connectorVersion;}
        /** A hint only; never a document identity or READY capability. */
        public String documentInstanceHint(){return documentInstanceHint;}
        public boolean isVerified(){return false;}
    }
    /** Opaque native-issued binding as RECEIVED; every field stays unverified until actual port check. */
    public static final class Binding {
        private final String dataEpoch,partitionRef,sessionRef,documentLease,portLeaseToken;
        private final long partitionGeneration,sessionGeneration;
        Binding(String epoch,String partition,long partitionGeneration,String session,long sessionGeneration,String documentLease,String portLease){
            dataEpoch=epoch;partitionRef=partition;this.partitionGeneration=partitionGeneration;sessionRef=session;
            this.sessionGeneration=sessionGeneration;this.documentLease=documentLease;portLeaseToken=portLease;
        }
        public String dataEpochHex(){return dataEpoch;}
        public String partitionRef(){return partitionRef;}
        public long partitionGeneration(){return partitionGeneration;}
        public String sessionRef(){return sessionRef;}
        public long sessionGeneration(){return sessionGeneration;}
        public String documentLeaseHex(){return documentLease;}
        public String portLeaseTokenHex(){return portLeaseToken;}
        public boolean isVerified(){return false;}
    }
    public static final class Ready {
        private final Binding binding;private final Value limits,supportedActions;
        Ready(Binding binding,Value limits,Value actions){this.binding=binding;this.limits=limits;this.supportedActions=actions;}
        public Binding binding(){return binding;}
        public Value limits(){return limits;}
        public Value supportedActions(){return supportedActions;}
    }
    public static final class Command {
        private final Binding binding;private final String requestId,action;private final Value args;
        Command(Binding binding,String requestId,String action,Value args){this.binding=binding;this.requestId=requestId;this.action=action;this.args=args;}
        public Binding binding(){return binding;}
        public String requestId(){return requestId;}
        public String action(){return action;}
        public Value args(){return args;}
    }
    public static final class Accepted {
        private final Binding binding;private final String requestId;
        Accepted(Binding binding,String requestId){this.binding=binding;this.requestId=requestId;}
        public Binding binding(){return binding;}
        public String requestId(){return requestId;}
        /** Connector-side acceptance only; NOT successful play/send/clear and not slot release. */
        public boolean isSuccessfulEffect(){return false;}
    }
    public static final class Result {
        private final Binding binding;private final String requestId,outcome,evidenceKind;private final Value result;
        Result(Binding binding,String requestId,String outcome,String evidenceKind,Value result){this.binding=binding;this.requestId=requestId;this.outcome=outcome;this.evidenceKind=evidenceKind;this.result=result;}
        public Binding binding(){return binding;}
        public String requestId(){return requestId;}
        public String outcome(){return outcome;}
        public String evidenceKind(){return evidenceKind;}
        public Value result(){return result;}
    }
    public static final class Event {
        private final Binding binding;private final long sequence;private final String eventType,resourceRef;private final Value payload;
        Event(Binding binding,long sequence,String eventType,String resourceRef,Value payload){this.binding=binding;this.sequence=sequence;this.eventType=eventType;this.resourceRef=resourceRef;this.payload=payload;}
        public Binding binding(){return binding;}
        public long eventSequence(){return sequence;}
        public String eventType(){return eventType;}
        public String resourceRef(){return resourceRef;}
        public Value payload(){return payload;}
    }
    public static final class Cancel { private final Binding binding;private final String requestId;Cancel(Binding b,String r){binding=b;requestId=r;}public Binding binding(){return binding;}public String requestId(){return requestId;} }
    public static final class CancelStatus { private final Binding binding;private final String requestId,cancelState;CancelStatus(Binding b,String r,String s){binding=b;requestId=r;cancelState=s;}public Binding binding(){return binding;}public String requestId(){return requestId;}public String cancelState(){return cancelState;} }
    public static final class StreamBegin { private final Binding binding;private final String requestId,streamId,purpose;private final long declaredBytes;StreamBegin(Binding b,String r,String s,String p,long d){binding=b;requestId=r;streamId=s;purpose=p;declaredBytes=d;}public Binding binding(){return binding;}public String requestId(){return requestId;}public String streamId(){return streamId;}public String purpose(){return purpose;}public long declaredBytes(){return declaredBytes;} }
    public static final class StreamCredit { private final Binding binding;private final String streamId;private final long nextSequence,creditBytes,maxTotalBytes;StreamCredit(Binding b,String s,long n,long c,long m){binding=b;streamId=s;nextSequence=n;creditBytes=c;maxTotalBytes=m;}public Binding binding(){return binding;}public String streamId(){return streamId;}public long nextSequence(){return nextSequence;}public long creditBytes(){return creditBytes;}public long maxTotalBytes(){return maxTotalBytes;} }
    public static final class StreamChunk { private final Binding binding;private final String streamId,base64;private final long sequence,rawBytes;StreamChunk(Binding b,String s,long q,long r,String v){binding=b;streamId=s;sequence=q;rawBytes=r;base64=v;}public Binding binding(){return binding;}public String streamId(){return streamId;}public long sequence(){return sequence;}public long rawBytes(){return rawBytes;}public String base64(){return base64;} }
    public static final class StreamEnd { private final Binding binding;private final String streamId,sha256;private final long totalBytes;StreamEnd(Binding b,String s,long t,String h){binding=b;streamId=s;totalBytes=t;sha256=h;}public Binding binding(){return binding;}public String streamId(){return streamId;}public long totalBytes(){return totalBytes;}public String sha256(){return sha256;} }
    public static final class Error { private final Binding binding;private final String requestId,errorCode,retryClass;Error(Binding b,String r,String c,String e){binding=b;requestId=r;errorCode=c;retryClass=e;}public Binding binding(){return binding;}public String requestId(){return requestId;}public String errorCode(){return errorCode;}public String retryClass(){return retryClass;} }

    private static final Map<Kind,String[]> REQUIRED=requiredFields();
    private static final Map<Kind,String[]> OPTIONAL=optionalFields();
    private static Map<Kind,String[]> requiredFields(){
        Map<Kind,String[]> map=new java.util.EnumMap<>(Kind.class);
        map.put(Kind.HELLO,new String[]{"protocol","kind","connectorVersion","documentInstanceHint"});
        map.put(Kind.READY,new String[]{"protocol","kind","binding","limits","supportedActions"});
        map.put(Kind.COMMAND,new String[]{"protocol","kind","binding","requestId","action","args"});
        map.put(Kind.ACCEPTED,new String[]{"protocol","kind","binding","requestId"});
        map.put(Kind.RESULT,new String[]{"protocol","kind","binding","requestId","outcome","evidenceKind","result"});
        map.put(Kind.EVENT,new String[]{"protocol","kind","binding","eventSequence","eventType","resourceRef","payload"});
        map.put(Kind.CANCEL,new String[]{"protocol","kind","binding","requestId"});
        map.put(Kind.CANCEL_STATUS,new String[]{"protocol","kind","binding","requestId","cancelState"});
        map.put(Kind.STREAM_BEGIN,new String[]{"protocol","kind","binding","requestId","streamId","purpose","declaredBytes"});
        map.put(Kind.STREAM_CREDIT,new String[]{"protocol","kind","binding","streamId","nextSequence","creditBytes","maxTotalBytes"});
        map.put(Kind.STREAM_CHUNK,new String[]{"protocol","kind","binding","streamId","sequence","rawBytes","base64"});
        map.put(Kind.STREAM_END,new String[]{"protocol","kind","binding","streamId","totalBytes","sha256"});
        map.put(Kind.ERROR,new String[]{"protocol","kind","errorCode","retryClass"});
        return Collections.unmodifiableMap(map);
    }
    private static Map<Kind,String[]> optionalFields(){
        Map<Kind,String[]> map=new java.util.EnumMap<>(Kind.class);
        map.put(Kind.COMMAND,new String[0]);
        map.put(Kind.ERROR,new String[]{"binding","requestId"});
        for(Kind kind:Kind.values())map.putIfAbsent(kind,new String[0]);
        return Collections.unmodifiableMap(map);
    }

    public static Message parse(String utf8Text){
        if(utf8Text==null)throw invalid("missing envelope");
        if(utf8Text.length()>MAX_ENVELOPE_BYTES)throw invalid("envelope too large");
        // String.getBytes(UTF_8) silently REPLACES unpaired UTF-16 surrogates.
        // Reject them before encoding so malformed caller input cannot be laundered.
        for(int i=0;i<utf8Text.length();i++){
            char c=utf8Text.charAt(i);
            if(Character.isHighSurrogate(c)){
                if(++i>=utf8Text.length() || !Character.isLowSurrogate(utf8Text.charAt(i)))
                    throw invalid("invalid UTF-16");
            }else if(Character.isLowSurrogate(c))throw invalid("invalid UTF-16");
        }
        byte[] bytes=utf8Text.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_ENVELOPE_BYTES)throw invalid("envelope too large");
        return parse(bytes);
    }
    public static Message parse(byte[] utf8Bytes){
        if(utf8Bytes==null)throw invalid("missing envelope");
        if(utf8Bytes.length>MAX_ENVELOPE_BYTES)throw invalid("envelope too large");
        String text=decode(utf8Bytes);
        if(!text.isEmpty() && text.charAt(0)==0xFEFF)throw invalid("BOM not allowed");
        Parser parser=new Parser(text);
        Value root=parser.parseDocument();
        if(root.type()!=ValueType.OBJECT)throw invalid("envelope must be an object");
        Map<String,Value> fields=root.object();
        Value protocol=fields.get("protocol");
        if(protocol==null || protocol.type()!=ValueType.STRING || !PROTOCOL.equals(protocol.string()))throw invalid("unsupported protocol");
        Value kindValue=fields.get("kind");
        if(kindValue==null || kindValue.type()!=ValueType.STRING)throw invalid("missing kind");
        Kind kind;
        try{kind=Kind.valueOf(kindValue.string());}catch(IllegalArgumentException unknown){throw invalid("unsupported kind");}
        requireExact(fields,"envelope",REQUIRED.get(kind),OPTIONAL.get(kind));
        validateStrings(root,kind,null,0);
        Message message=new Message(kind,root);
        // Force strict typing of the mandatory payload during parse, not only at optional access.
        switch(kind){
            case HELLO: message.hello();break;
            case READY: message.ready();break;
            case COMMAND: message.command();break;
            case ACCEPTED: message.accepted();break;
            case RESULT: message.result();break;
            case EVENT: message.event();break;
            case CANCEL: message.cancel();break;
            case CANCEL_STATUS: message.cancelStatus();break;
            case STREAM_BEGIN: message.streamBegin();break;
            case STREAM_CREDIT: message.streamCredit();break;
            case STREAM_CHUNK: message.streamChunk();break;
            case STREAM_END: message.streamEnd();break;
            case ERROR: message.error();break;
            default: throw invalid("unsupported kind");
        }
        return message;
    }

    private static void validateStrings(Value value,Kind kind,String field,int depth){
        if(value.type()==ValueType.STRING){if(value.string().length()>MAX_STRING_CHARS && !(kind==Kind.STREAM_CHUNK && depth==1 && "base64".equals(field)))throw invalid("ordinary string too long");}
        else if(value.type()==ValueType.OBJECT){for(Map.Entry<String,Value> e:value.object().entrySet()){if(e.getKey().length()>128)throw invalid("field name too long");validateStrings(e.getValue(),kind,e.getKey(),depth+1);}}
        else if(value.type()==ValueType.ARRAY)for(Value child:value.array())validateStrings(child,kind,null,depth+1);
    }
    private static String decode(byte[] bytes){
        CharsetDecoder decoder=StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try{CharBuffer buffer=decoder.decode(ByteBuffer.wrap(bytes));return buffer.toString();}
        catch(CharacterCodingException invalidUtf8){throw invalid("invalid UTF-8");}
    }

    private static void requireExact(Map<String,Value> fields,String where,String[] required,String[] optional){
        if(fields.size()>MAX_FIELDS)throw invalid(where+" has too many fields");
        for(String name:required)if(!fields.containsKey(name))throw invalid(where+" missing "+name);
        List<String> allowed=new ArrayList<>(java.util.Arrays.asList(required));
        allowed.addAll(java.util.Arrays.asList(optional));
        for(String name:fields.keySet())if(!allowed.contains(name))throw invalid(where+" has unknown field "+name);
    }
    private static String text(Value value,int max){if(value==null || value.type()!=ValueType.STRING)throw invalid("string required");return bounded(value.string(),max);}
    private static String bounded(String value,int max){
        if(value.length()>max)throw invalid("string too long");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw invalid("broken UTF-16");}else if(Character.isLowSurrogate(c))throw invalid("broken UTF-16");}
        return value;
    }
    private static String hex(Value value,int chars){return pattern(value,"^[0-9a-f]{"+chars+"}$");}
    private static String ascii(Value value,int max){
        String text=text(value,max);
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c<0x21 || c>0x7e)throw invalid("opaque ref must be printable ASCII");}
        return text;
    }
    private static long generation(Value value){long number=safeInteger(value);if(number<0)throw invalid("negative generation");return number;}
    private static long safeInteger(Value value){
        if(value==null || !value.isIntegral())throw invalid("exact integer required");
        long number=value.asLong();
        if(number<0 || number>MAX_SAFE_INTEGER)throw invalid("integer outside safe range");
        return number;
    }
    private static String pattern(Value value,String regex){
        String text=text(value,256);
        if(!java.util.regex.Pattern.matches(regex,text))throw invalid("malformed opaque identifier");
        return text;
    }
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException("Invalid connector envelope: "+message);}

    /** Strict recursive-descent JSON reader: bounded depth/size, no coercion, no duplicate keys. */
    private static final class Parser {
        private final String text;private int index,nodes;
        Parser(String text){this.text=text;}
        Value parseDocument(){
            skipWhitespace();Value value=parseValue(0);skipWhitespace();
            if(index!=text.length())throw invalid("trailing content");
            return value;
        }
        private Value parseValue(int depth){
            if(depth>MAX_DEPTH)throw invalid("depth exceeded");
            if(++nodes>MAX_NODES)throw invalid("too many values");
            skipWhitespace();
            if(index>=text.length())throw invalid("unexpected end");
            char c=text.charAt(index);
            switch(c){
                case '{':return parseObject(depth);
                case '[':return parseArray(depth);
                case '"':return new Value(ValueType.STRING,null,null,parseString(),0,0,false,false);
                case 't':expect("true");return new Value(ValueType.BOOLEAN,null,null,null,0,0,false,true);
                case 'f':expect("false");return new Value(ValueType.BOOLEAN,null,null,null,0,0,false,false);
                case 'n':expect("null");return new Value(ValueType.NULL,null,null,null,0,0,false,false);
                default:return parseNumber();
            }
        }
        private Value parseObject(int depth){
            index++;skipWhitespace();
            Map<String,Value> fields=new LinkedHashMap<>();
            if(peek('}')){index++;return new Value(ValueType.OBJECT,Collections.unmodifiableMap(fields),null,null,0,0,false,false);}
            while(true){
                skipWhitespace();
                if(!peek('"'))throw invalid("object key must be a string");
                String name=parseString();
                if(fields.containsKey(name))throw invalid("duplicate key "+name);
                if(fields.size()>=MAX_FIELDS)throw invalid("too many object fields");
                skipWhitespace();if(!peek(':'))throw invalid("missing colon");
                index++;Value value=parseValue(depth+1);
                fields.put(name,value);
                skipWhitespace();
                if(peek(',')){index++;continue;}
                if(peek('}')){index++;break;}
                throw invalid("malformed object");
            }
            return new Value(ValueType.OBJECT,Collections.unmodifiableMap(fields),null,null,0,0,false,false);
        }
        private Value parseArray(int depth){
            index++;skipWhitespace();List<Value> values=new ArrayList<>();
            if(peek(']')){index++;return new Value(ValueType.ARRAY,null,Collections.unmodifiableList(values),null,0,0,false,false);}
            while(true){
                if(values.size()>=MAX_ARRAY)throw invalid("array too long");
                values.add(parseValue(depth+1));
                skipWhitespace();
                if(peek(',')){index++;skipWhitespace();continue;}
                if(peek(']')){index++;break;}
                throw invalid("malformed array");
            }
            return new Value(ValueType.ARRAY,null,Collections.unmodifiableList(values),null,0,0,false,false);
        }
        private String parseString(){
            index++;StringBuilder out=new StringBuilder();
            while(true){
                if(index>=text.length())throw invalid("unterminated string");
                char c=text.charAt(index++);
                if(c=='"')break;
                if(c=='\\'){
                    if(index>=text.length())throw invalid("unterminated escape");
                    char escape=text.charAt(index++);
                    switch(escape){
                        case '"':out.append('"');break;
                        case '\\':out.append('\\');break;
                        case '/':out.append('/');break;
                        case 'b':out.append('\b');break;
                        case 'f':out.append('\f');break;
                        case 'n':out.append('\n');break;
                        case 'r':out.append('\r');break;
                        case 't':out.append('\t');break;
                        case 'u':
                            if(index+4>text.length())throw invalid("bad unicode escape");
                            int code=0;
                            for(int i=0;i<4;i++){char h=text.charAt(index+i);
                                int digit=h>='0'&&h<='9'?h-'0':h>='a'&&h<='f'?h-'a'+10:h>='A'&&h<='F'?h-'A'+10:-1;if(digit<0)throw invalid("bad unicode escape");code=(code<<4)|digit;}
                            index+=4;out.append((char)code);break;
                        default:throw invalid("unsupported escape");
                    }
                    continue;
                }
                if(c<0x20)throw invalid("raw control character in string");
                out.append(c);
            }
            String value=out.toString();
            if(value.length()>MAX_JSON_STRING_CHARS)throw invalid("string too long");
            for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw invalid("lone surrogate");}else if(Character.isLowSurrogate(c))throw invalid("lone surrogate");}
            return value;
        }
        private Value parseNumber(){
            int start=index;
            if(peek('-'))index++;
            if(index>=text.length())throw invalid("bad number");
            if(peek('0')){index++;if(index<text.length() && asciiDigit(text.charAt(index)))throw invalid("leading zero");}
            else{
                if(!asciiDigit(text.charAt(index)))throw invalid("bad number");
                while(index<text.length() && asciiDigit(text.charAt(index)))index++;
            }
            boolean integral=true;
            if(peek('.')){integral=false;index++;if(index>=text.length() || !asciiDigit(text.charAt(index)))throw invalid("bad fraction");while(index<text.length() && asciiDigit(text.charAt(index)))index++;}
            if(peek('e') || peek('E')){integral=false;index++;if(peek('+') || peek('-'))index++;if(index>=text.length() || !asciiDigit(text.charAt(index)))throw invalid("bad exponent");while(index<text.length() && asciiDigit(text.charAt(index)))index++;}
            String literal=text.substring(start,index);
            if(integral){
                try{long value=Long.parseLong(literal);return new Value(ValueType.NUMBER,null,null,null,value,value,true,false);}
                catch(NumberFormatException overflow){throw invalid("integer overflow");}
            }
            double value=Double.parseDouble(literal);
            if(!Double.isFinite(value))throw invalid("non-finite number");
            return new Value(ValueType.NUMBER,null,null,null,0,value,false,false);
        }
        private void expect(String literal){
            if(!text.startsWith(literal,index))throw invalid("malformed literal");
            index+=literal.length();
        }
        /** JSON numeric tokens permit ASCII 0-9, never Unicode decimal digits. */
        private static boolean asciiDigit(char c){return c>='0' && c<='9';}
        private boolean peek(char c){return index<text.length() && text.charAt(index)==c;}
        private void skipWhitespace(){while(index<text.length()){char c=text.charAt(index);if(c==' '||c=='\t'||c=='\n'||c=='\r')index++;else break;}}
    }
    /** Bounded native outbound OBSERVATION-only frames. Building JSON never issues a lease. */
    static String nativeReady(Binding b,String action){return finish("{\"protocol\":\""+PROTOCOL+"\",\"kind\":\"READY\",\"binding\":"+bindingJson(b)+",\"limits\":{\"chunkBytes\":32768,\"creditBytes\":65536},\"supportedActions\":["+quoted(action,128)+"]}");}
    static String nativeObserve(Binding b,String requestId,String action){if(requestId==null || !requestId.matches("r_[0-9a-f]{32}"))throw invalid("native request id");return finish("{\"protocol\":\""+PROTOCOL+"\",\"kind\":\"COMMAND\",\"binding\":"+bindingJson(b)+",\"requestId\":"+quoted(requestId,34)+",\"action\":"+quoted(action,128)+",\"args\":{}}");}
    static String nativeCredit(Binding b,String streamId,long sequence,int credit,long maximum){if(streamId==null || !streamId.matches("b_[0-9a-f]{32}") || sequence<0 || sequence>MAX_SAFE_INTEGER || credit<1 || credit>MAX_CHUNK_BYTES*2 || maximum<0 || maximum>MAX_SAFE_INTEGER)throw invalid("native stream credit");return finish("{\"protocol\":\""+PROTOCOL+"\",\"kind\":\"STREAM_CREDIT\",\"binding\":"+bindingJson(b)+",\"streamId\":"+quoted(streamId,34)+",\"nextSequence\":"+sequence+",\"creditBytes\":"+credit+",\"maxTotalBytes\":"+maximum+"}");}
    private static String bindingJson(Binding b){if(b==null)throw invalid("native binding");return "{\"dataEpoch\":"+quoted(b.dataEpochHex(),32)+",\"partitionRef\":"+quoted(b.partitionRef(),64)+",\"partitionGeneration\":"+b.partitionGeneration()+",\"sessionRef\":"+quoted(b.sessionRef(),64)+",\"sessionGeneration\":"+b.sessionGeneration()+",\"documentLease\":"+quoted(b.documentLeaseHex(),64)+",\"portLeaseToken\":"+quoted(b.portLeaseTokenHex(),64)+"}";}
    private static String quoted(String value,int maximum){if(value==null || value.isEmpty() || value.length()>maximum)throw invalid("native ASCII field");StringBuilder out=new StringBuilder("\"");for(int n=0;n<value.length();n++){char c=value.charAt(n);if(c<33 || c>126)throw invalid("native ASCII field");if(c=='\"' || c=='\\')out.append('\\');out.append(c);}return out.append('\"').toString();}
    private static String finish(String value){parse(value);return value;} // Same strict schema, no ad-hoc bypass.
    private BridgeMessages(){}
}
