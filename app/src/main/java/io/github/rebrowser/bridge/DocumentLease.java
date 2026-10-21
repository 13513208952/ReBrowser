package io.github.rebrowser.bridge;

import io.github.rebrowser.bootstrap.ComponentGate;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Native exact PORT-incarnation routing lease. NOT a committed Gecko document/principal,
 * account, login, user gesture, Actor, grant or actual-work-end proof. Payload hints never mint it.
 * Package-private issuer is the actual native broker; native guard must fence navigation/source
 * and session ownership. Same URL/new port NEVER revives an old token or native request. */
public final class DocumentLease {
    public enum State { CONNECTED, HELLO_SPENT, READY_OFFERED, RETIRED }
    public static final class Received {
        private final DocumentLease issuer;private final BridgeMessages.Message message;
        private Received(DocumentLease issuer,BridgeMessages.Message message){this.issuer=issuer;this.message=message;}
        public BridgeMessages.Message message(){issuer.requireCurrent();return message;}
        public void requireLease(DocumentLease expected){if(issuer!=expected)throw denied();issuer.requireCurrent();}
    }
    private final Object port;private final ComponentGate.DataEpochPin source;private final Runnable nativeCurrent;private final Thread owner=Thread.currentThread();private final BridgeMessages.Binding binding;
    private State state=State.CONNECTED;private long nextEvent;private boolean checking;
    DocumentLease(Object actualPort,ComponentGate.DataEpochPin source,String nativePartitionRef,String nativeSessionRef,Runnable nativeCurrent){
        if(actualPort==null || source==null || nativeCurrent==null || !ref(nativePartitionRef) || !ref(nativeSessionRef))throw new IllegalArgumentException("Native port/session binding required");this.port=actualPort;this.source=source;this.nativeCurrent=nativeCurrent;
        binding=new BridgeMessages.Binding(hex(source.dataEpoch()),nativePartitionRef,0,nativeSessionRef,0,nonce(),nonce());requireCurrent();
    }
    public State state(){thread();return state;}
    public void requireNativeTaskSource(io.github.rebrowser.core.TaskCoordinator.Owner actualOwner){requireCurrent();if(actualOwner==null)throw denied();actualOwner.requireNativeDataSource(source);requireCurrent();}
    BridgeMessages.Binding nativeBinding(){requireCurrent();return binding;}
    void spendHello(Object actualPort,BridgeMessages.Message message){requirePort(actualPort);if(state!=State.CONNECTED)throw denied();state=State.HELLO_SPENT;if(message==null || message.kind()!=BridgeMessages.Kind.HELLO || !BuiltinVersion.VALUE.equals(message.hello().connectorVersion())){state=State.RETIRED;throw denied();}}
    /** Java postMessage return is only OFFERED. It is not peer READY/account/command success. */
    void readyPostReturned(Object actualPort){requirePort(actualPort);if(state!=State.HELLO_SPENT)throw denied();state=State.READY_OFFERED;}
    Received receive(Object actualPort,BridgeMessages.Message message){
        requirePort(actualPort);try{if(state!=State.READY_OFFERED || message==null)throw denied();
        switch(message.kind()){
            case ACCEPTED:case RESULT:case EVENT:case CANCEL_STATUS:case STREAM_BEGIN:case STREAM_CHUNK:case STREAM_END:case ERROR:break;
            default:throw denied();
        }
        BridgeMessages.Binding incoming=message.kind()==BridgeMessages.Kind.ERROR?message.error().binding():message.binding();match(incoming);
        if(message.kind()==BridgeMessages.Kind.EVENT){if(message.event().eventSequence()!=nextEvent || nextEvent==BridgeMessages.MAX_SAFE_INTEGER)throw denied();nextEvent++;}
        requireCurrent();return new Received(this,message);
        }catch(RuntimeException rejected){state=State.RETIRED;throw rejected;}
    }
    void requirePort(Object actual){if(actual!=port)throw denied();requireCurrent();}
    public void requireCurrent(){thread();if(state==State.RETIRED || checking)throw denied();checking=true;try{source.requireCurrent();nativeCurrent.run();source.requireCurrent();}catch(RuntimeException stale){state=State.RETIRED;throw denied();}finally{checking=false;}}
    public void retire(){thread();state=State.RETIRED;} // Routing fence only; no slot release.
    private void match(BridgeMessages.Binding b){if(b==null || !same(binding.dataEpochHex(),b.dataEpochHex()) || !same(binding.partitionRef(),b.partitionRef()) || binding.partitionGeneration()!=b.partitionGeneration() || !same(binding.sessionRef(),b.sessionRef()) || binding.sessionGeneration()!=b.sessionGeneration() || !same(binding.documentLeaseHex(),b.documentLeaseHex()) || !same(binding.portLeaseTokenHex(),b.portLeaseTokenHex()))throw denied();}
    private static boolean same(String a,String b){return b!=null && MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.US_ASCII),b.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Native connector owner thread required");}
    private static boolean ref(String s){if(s==null || s.isEmpty() || s.length()>64)return false;for(int n=0;n<s.length();n++)if(s.charAt(n)<33 || s.charAt(n)>126)return false;return true;}
    private static String nonce(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return hex(b);}
    static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(Character.forDigit((v&255)>>>4,16)).append(Character.forDigit(v&15,16));return s.toString();}
    static final class BuiltinVersion {static final String VALUE="1.0.0";private BuiltinVersion(){}}
    private static SecurityException denied(){return new SecurityException("Connector route unavailable or stale");}
}
