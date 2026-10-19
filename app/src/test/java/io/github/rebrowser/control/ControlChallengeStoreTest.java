package io.github.rebrowser.control;

import static org.junit.Assert.*;

import io.github.rebrowser.authority.AdminProofVerifier;
import io.github.rebrowser.authority.CompiledPrincipals;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

/** Actual challenge store HOST tests with PUBLIC scalar1 fixture; no durable/native effects. */
public final class ControlChallengeStoreTest {
    private static final String SPKI="MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q";
    private interface Checked {void run()throws Exception;}
    private static byte[] u(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static ControlChallengeStore.Context context(int revision){return new ControlChallengeStore.Context(new byte[32],new byte[16],new byte[32],revision);}
    private static ControlChallengeStore.Template template(){return new ControlChallengeStore.Template("public-dummy-principal","host.window.back","window_synthetic","",new byte[16],new byte[32],u("{\"windowRef\":\"window_synthetic\",\"expectedPresentationRevision\":1}"));}
    private static AdminProofVerifier verifier()throws Exception{
        // HOST reflection only; production never exposes a dynamic high-identity registration API.
        Constructor<CompiledPrincipals.Entry> ec=CompiledPrincipals.Entry.class.getDeclaredConstructor(String.class,CompiledPrincipals.Role.class,int.class,byte[].class);ec.setAccessible(true);
        CompiledPrincipals.Entry e=ec.newInstance("public-dummy-principal",CompiledPrincipals.Role.ROOT,10,CryptoEncoding.decodeBase64Url(SPKI,91));
        Constructor<CompiledPrincipals> registry=CompiledPrincipals.class.getDeclaredConstructor(CompiledPrincipals.Entry[].class);registry.setAccessible(true);
        return new AdminProofVerifier(registry.newInstance((Object)new CompiledPrincipals.Entry[]{e}));
    }
    static AdminProofVerifier.KeyProof sign(ControlEnvelope frame)throws Exception{
        AlgorithmParameters p=AlgorithmParameters.getInstance("EC");p.init(new ECGenParameterSpec("secp256r1"));
        PrivateKey PUBLIC_SCALAR_ONE=KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(BigInteger.ONE,p.getParameterSpec(ECParameterSpec.class)));
        Signature signer=Signature.getInstance("SHA256withECDSA");signer.initSign(PUBLIC_SCALAR_ONE);signer.update(frame.signedBytes());
        String wire="{\"protocol\":\"rebrowser.control/1\",\"frameB64\":\""+CryptoEncoding.base64Url(frame.signedBytes())+"\",\"signatureB64\":\""+CryptoEncoding.base64Url(signer.sign())+"\"}";
        return verifier().verifyCompiledKey(ControlDecoder.decode(u(wire)));
    }
    private static ControlChallengeStore.Admission<String> good(AtomicInteger jobs){return new ControlChallengeStore.Admission<String>(){
        public void authorize(AdminProofVerifier.KeyProof proof){}
        public String reserveJob(AdminProofVerifier.KeyProof proof){return "synthetic-job-"+jobs.incrementAndGet();}
    };}
    private static void rejected(ControlChallengeStore.Failure expected,Checked operation)throws Exception{
        try{operation.run();fail("must reject");}catch(ControlChallengeStore.Rejected failure){assertEquals(expected,failure.failure());}
    }

    @Test public void issuedFrameBindsServerContextAndThirtySecondDeadline()throws Exception{
        AtomicLong clock=new AtomicLong(1000);ControlChallengeStore store=new ControlChallengeStore(context(7),clock::get,8,4);ControlEnvelope frame=store.issue(template());
        assertEquals(31000,frame.unsigned63(ControlEnvelope.Field.DEADLINE_ELAPSED_MS));assertEquals(7,frame.unsigned63(ControlEnvelope.Field.DISABLE_REVISION));
        assertArrayEquals(new byte[32],frame.bytes(ControlEnvelope.Field.TARGET_INSTALL_SPKI_SHA256));
        ControlEnvelope another=store.issue(template());assertFalse(Arrays.equals(frame.bytes(ControlEnvelope.Field.NONCE),another.bytes(ControlEnvelope.Field.NONCE)));
        ControlEnvelope restarted=new ControlChallengeStore(context(7),clock::get,8,4).issue(template());
        assertFalse(Arrays.equals(frame.bytes(ControlEnvelope.Field.SERVER_INSTANCE_NONCE),restarted.bytes(ControlEnvelope.Field.SERVER_INSTANCE_NONCE)));
    }
    @Test public void sameChallengeDifferentValidSignatureStillReplays()throws Exception{
        AtomicLong clock=new AtomicLong(10);ControlChallengeStore store=new ControlChallengeStore(context(1),clock::get,8,4);ControlEnvelope frame=store.issue(template());AtomicInteger jobs=new AtomicInteger();
        assertEquals("synthetic-job-1",store.admit(sign(frame),good(jobs)));
        rejected(ControlChallengeStore.Failure.REPLAY,()->store.admit(sign(frame),good(jobs)));assertEquals(1,jobs.get());
    }
    @Test public void changedSignedArgsCannotConsumeIssuedChallenge()throws Exception{
        ControlChallengeStore store=new ControlChallengeStore(context(1),()->0L,8,4);ControlEnvelope original=store.issue(template());EnumMap<ControlEnvelope.Field,byte[]> changed=new EnumMap<>(ControlEnvelope.Field.class);
        for(ControlEnvelope.Field f:ControlEnvelope.Field.values())changed.put(f,original.bytes(f));changed.put(ControlEnvelope.Field.ARGS_BYTES,u("{\"other\":true}"));
        AtomicInteger jobs=new AtomicInteger();rejected(ControlChallengeStore.Failure.INVALID_CHALLENGE,()->store.admit(sign(ControlEnvelope.fromFields(changed)),good(jobs)));
        assertEquals(0,jobs.get());assertNotNull(store.admit(sign(original),good(jobs)));assertEquals(1,jobs.get());
    }
    @Test public void exactDeadlineAndContextReplacementInvalidateOldProofs()throws Exception{
        AtomicLong clock=new AtomicLong(100);ControlChallengeStore store=new ControlChallengeStore(context(1),clock::get,8,4);AdminProofVerifier.KeyProof old=sign(store.issue(template()));
        clock.set(30100);rejected(ControlChallengeStore.Failure.EXPIRED,()->store.admit(old,good(new AtomicInteger())));
        AdminProofVerifier.KeyProof pending=sign(store.issue(template()));store.replaceNativeContext(context(2));
        rejected(ControlChallengeStore.Failure.INVALID_CHALLENGE,()->store.admit(pending,good(new AtomicInteger())));
    }
    @Test public void denialDoesNotReserveOrConsumeButLateAuthorizationExpires()throws Exception{
        AtomicLong clock=new AtomicLong(0);ControlChallengeStore store=new ControlChallengeStore(context(1),clock::get,8,4);AdminProofVerifier.KeyProof proof=sign(store.issue(template()));AtomicInteger jobs=new AtomicInteger();
        try{store.admit(proof,new ControlChallengeStore.Admission<String>(){public void authorize(AdminProofVerifier.KeyProof p)throws Exception{throw new Exception("synthetic policy denial");}public String reserveJob(AdminProofVerifier.KeyProof p){fail();return "bad";}});fail();}catch(Exception denied){assertEquals("synthetic policy denial",denied.getMessage());}
        assertNotNull(store.admit(proof,good(jobs)));assertEquals(1,jobs.get());
        AdminProofVerifier.KeyProof late=sign(store.issue(template()));
        rejected(ControlChallengeStore.Failure.EXPIRED,()->store.admit(late,new ControlChallengeStore.Admission<String>(){public void authorize(AdminProofVerifier.KeyProof p){clock.set(30000);}public String reserveJob(AdminProofVerifier.KeyProof p){fail();return "bad";}}));
    }
    @Test public void unknownReservationFailureBurnsProofRatherThanAutoReplaying()throws Exception{
        ControlChallengeStore store=new ControlChallengeStore(context(1),()->0L,8,4);AdminProofVerifier.KeyProof proof=sign(store.issue(template()));AtomicInteger reservations=new AtomicInteger();
        try{store.admit(proof,new ControlChallengeStore.Admission<String>(){public void authorize(AdminProofVerifier.KeyProof p){}public String reserveJob(AdminProofVerifier.KeyProof p)throws Exception{reservations.incrementAndGet();throw new Exception("synthetic unknown commit outcome");}});fail();}catch(Exception unknown){assertEquals("synthetic unknown commit outcome",unknown.getMessage());}
        rejected(ControlChallengeStore.Failure.REPLAY,()->store.admit(proof,good(reservations)));assertEquals(1,reservations.get());
    }
    @Test public void nullReservationCannotProduceSuccessfulAdmission()throws Exception{
        ControlChallengeStore store=new ControlChallengeStore(context(1),()->0L,8,4);AdminProofVerifier.KeyProof proof=sign(store.issue(template()));
        try{store.admit(proof,new ControlChallengeStore.Admission<String>(){public void authorize(AdminProofVerifier.KeyProof p){}public String reserveJob(AdminProofVerifier.KeyProof p){return null;}});fail();}catch(IllegalStateException expected){}
        rejected(ControlChallengeStore.Failure.REPLAY,()->store.admit(proof,good(new AtomicInteger())));
    }
    @Test public void capacitiesCountSpentUntilExpiryWithoutClientReset()throws Exception{
        AtomicLong clock=new AtomicLong(0);ControlChallengeStore store=new ControlChallengeStore(context(1),clock::get,2,1);ControlEnvelope frame=store.issue(template());
        rejected(ControlChallengeStore.Failure.RESOURCE_LIMIT,()->store.issue(template()));store.admit(sign(frame),good(new AtomicInteger()));
        rejected(ControlChallengeStore.Failure.RESOURCE_LIMIT,()->store.issue(template()));clock.set(30000);assertNotNull(store.issue(template()));
    }
    @Test public void concurrentReplayReservesOnlyOneJob()throws Exception{
        ControlChallengeStore store=new ControlChallengeStore(context(1),()->0L,8,4);AdminProofVerifier.KeyProof proof=sign(store.issue(template()));AtomicInteger jobs=new AtomicInteger();ExecutorService pool=Executors.newFixedThreadPool(4);CountDownLatch start=new CountDownLatch(1);List<Future<Boolean>> results=new ArrayList<>();
        try{for(int i=0;i<16;i++)results.add(pool.submit(()->{assertTrue(start.await(2,TimeUnit.SECONDS));try{store.admit(proof,good(jobs));return true;}catch(ControlChallengeStore.Rejected failure){assertEquals(ControlChallengeStore.Failure.REPLAY,failure.failure());return false;}}));start.countDown();int accepted=0;for(Future<Boolean> f:results)if(f.get(3,TimeUnit.SECONDS))accepted++;assertEquals(1,accepted);assertEquals(1,jobs.get());}
        finally{pool.shutdownNow();assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS));}
    }
    @Test public void reentrantAdmissionCannotConsumeSameProofTwice()throws Exception{
        ControlChallengeStore store=new ControlChallengeStore(context(1),()->0L,8,4);AdminProofVerifier.KeyProof proof=sign(store.issue(template()));AtomicInteger jobs=new AtomicInteger();
        store.admit(proof,new ControlChallengeStore.Admission<String>(){public void authorize(AdminProofVerifier.KeyProof p)throws Exception{rejected(ControlChallengeStore.Failure.REENTRANT_ADMISSION,()->store.admit(p,good(jobs)));}public String reserveJob(AdminProofVerifier.KeyProof p){return "job-"+jobs.incrementAndGet();}});assertEquals(1,jobs.get());
    }
    @Test public void retainedFrameByteBudgetBoundsLargeChallengesAndIsReclaimedOnExpiry()throws Exception{
        AtomicLong clock=new AtomicLong(0);ControlChallengeStore store=new ControlChallengeStore(context(1),clock::get,256,256);
        byte[] args=new byte[32000];Arrays.fill(args,(byte)' ');args[0]='{';args[args.length-1]='}';
        ControlChallengeStore.Template large=new ControlChallengeStore.Template("public-dummy-principal","host.window.back","window_synthetic","",new byte[16],new byte[32],args);
        int count=0;while(true){try{store.issue(large);count++;}catch(ControlChallengeStore.Rejected limit){assertEquals(ControlChallengeStore.Failure.RESOURCE_LIMIT,limit.failure());break;}}
        assertTrue(count>0);assertTrue(count<256);assertTrue((long)count*args.length<=ControlChallengeStore.MAX_RETAINED_FRAME_BYTES);
        clock.set(30000);assertNotNull(store.issue(large));store.replaceNativeContext(context(2));assertNotNull(store.issue(large));
    }
    @Test public void clockRollbackPoisonsServerInsteadOfExtendingProofLife()throws Exception{
        AtomicLong clock=new AtomicLong(10);ControlChallengeStore store=new ControlChallengeStore(context(1),clock::get,8,4);AdminProofVerifier.KeyProof proof=sign(store.issue(template()));clock.set(9);
        rejected(ControlChallengeStore.Failure.CLOCK_UNAVAILABLE,()->store.admit(proof,good(new AtomicInteger())));clock.set(11);rejected(ControlChallengeStore.Failure.CLOSED,()->store.issue(template()));
    }
}
