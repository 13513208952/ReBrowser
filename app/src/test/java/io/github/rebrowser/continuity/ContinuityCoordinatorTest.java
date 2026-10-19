package io.github.rebrowser.continuity;

import static org.junit.Assert.*;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.Test;

/** Actual production reducer, synthetic package-private native signals ONLY.
 * Does NOT execute OwnerAuthentication, Android prompts/ticket consume or erasure. */
public final class ContinuityCoordinatorTest {
    private static final String A=repeat('a'),B=repeat('b'),R=repeat('c'),S=repeat('d');
    private static String repeat(char c){char[] b=new char[64];Arrays.fill(b,c);return new String(b);}
    private static LastGoodSourceStore.SourceEpoch pair(String s,String r,int n){byte[] e=new byte[16];Arrays.fill(e,(byte)n);return new LastGoodSourceStore.SourceEpoch(s,r,e);}
    private static final LastGoodSourceStore.NativeFence FIXTURE_ONLY=(e,r)->{};
    private static final class Time implements ContinuityCoordinator.Clock {long now=1000;boolean broken;public long elapsedMs(){if(broken)throw new IllegalStateException("synthetic clock error");return now;}}
    private static final class Fixture implements AutoCloseable {
        final Path dir;final LastGoodSourceStore store;final LastGoodSourceStore.View baseline;final ContinuityCoordinator.Context context;final Time time=new Time();final Object window=new Object();
        boolean current=true;final ContinuityCoordinator flow;
        Fixture()throws IOException{
            Path work=Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath();assertFalse(work.startsWith(Paths.get("/tmp")));dir=Files.createTempDirectory(work,"continuity-core-host-");store=new LastGoodSourceStore(dir);baseline=store.seedVerifiedFresh(store.inspect(),pair(A,R,1),FIXTURE_ONLY);context=ContinuityCoordinator.Context.fromNativeMetadata(baseline,B,S);flow=newFlow();
        }
        ContinuityCoordinator newFlow(){return new ContinuityCoordinator(context,window,c->{assertSame(context,c);if(!current)throw new SecurityException("synthetic native context revoked");},time);}
        @Override public void close()throws IOException{List<Path> paths=new ArrayList<>();try(Stream<Path> walked=Files.walk(dir)){walked.forEach(paths::add);}paths.sort(Comparator.reverseOrder());for(Path p:paths)Files.delete(p);}
    }
    private static void ordinary(ContinuityCoordinator f){ContinuityCoordinator.View v=f.view();f.confirmOrdinary(v.revision(),v.step());}
    private static void toForce2(ContinuityCoordinator f){ordinary(f);ordinary(f);assertEquals(ContinuityState.Confirmation.FORCE_2,f.view().step());}
    private static ContinuityCoordinator.OwnerAttempt attempt(ContinuityCoordinator f){ContinuityCoordinator.View v=f.view();return f.beginOwnerConfirmation(v.revision(),v.step());}
    private static void syntheticVerified(ContinuityCoordinator.OwnerAttempt attempt){attempt.nativeWorkMayStart();attempt.nativeTerminal(true);attempt.verifiedAfterNativeTicketConsume();}
    private static ContinuityCoordinator.Consent syntheticFinalConsent(ContinuityCoordinator f){toForce2(f);syntheticVerified(attempt(f));syntheticVerified(attempt(f));return f.availableConsent();}
    @Test public void exactOrdinaryOrderAndNoClickInsteadOfSecondOwnerVerification()throws Exception{
        try(Fixture f=new Fixture()){
            assertEquals(ContinuityState.Confirmation.TRUST_SOURCE,f.flow.view().step());assertThrows(IllegalStateException.class,f.flow::availableConsent);ordinary(f.flow);assertEquals(ContinuityState.Confirmation.FORCE_1,f.flow.view().step());ordinary(f.flow);assertEquals(ContinuityState.Confirmation.FORCE_2,f.flow.view().step());assertThrows(IllegalStateException.class,()->ordinary(f.flow));assertEquals(ContinuityCoordinator.Stage.CONFIRMING,f.flow.view().stage());
        }
    }
    @Test public void doubleClickWrongRevisionOrStepCannotSkipWarnings()throws Exception{
        try(Fixture f=new Fixture()){
            ContinuityCoordinator.View first=f.flow.view();ordinary(f.flow);assertThrows(IllegalStateException.class,()->f.flow.confirmOrdinary(first.revision(),first.step()));assertThrows(IllegalStateException.class,()->f.flow.confirmOrdinary(f.flow.view().revision(),ContinuityState.Confirmation.FORCE_3));assertEquals(ContinuityState.Confirmation.FORCE_1,f.flow.view().step());assertThrows(IllegalStateException.class,()->attempt(f.flow));
        }
    }
    @Test public void twoDifferentPrivateAttemptsAndFinalConsentAreSeparateFromErasure()throws Exception{
        try(Fixture f=new Fixture()){
            toForce2(f.flow);ContinuityCoordinator.OwnerAttempt second=attempt(f.flow);assertEquals(ContinuityState.Confirmation.FORCE_2,second.step());assertTrue(second.windowMatches(f.window));assertFalse(second.windowMatches(new Object()));syntheticVerified(second);assertEquals(ContinuityState.Confirmation.FORCE_3,f.flow.view().step());assertThrows(IllegalStateException.class,f.flow::availableConsent);
            ContinuityCoordinator.OwnerAttempt third=attempt(f.flow);assertNotSame(second,third);assertEquals(ContinuityState.Confirmation.FORCE_3,third.step());syntheticVerified(third);assertNotNull(f.flow.availableConsent());assertEquals(ContinuityCoordinator.Stage.CONSENT_AVAILABLE,f.flow.view().stage());assertEquals(A,f.store.inspect().record().lastGood().signingSha256());assertEquals(1,f.store.inspect().record().revision());
        }
    }
    @Test public void unknownSubmissionNeverReturnsNativeWorkCreditOrAllowsNewAttempt()throws Exception{
        try(Fixture f=new Fixture()){
            toForce2(f.flow);ContinuityCoordinator.OwnerAttempt pending=attempt(f.flow);pending.nativeWorkMayStart();pending.nativeSubmissionUnknown();assertTrue(f.flow.view().hasPendingNativeOwnerWork());assertThrows(IllegalStateException.class,()->attempt(f.flow));assertThrows(IllegalStateException.class,()->ordinary(f.flow));assertThrows(IllegalStateException.class,pending::verifiedAfterNativeTicketConsume);
            pending.nativeTerminal(false);assertFalse(f.flow.view().hasPendingNativeOwnerWork());assertEquals(ContinuityCoordinator.Stage.RETIRED,f.flow.view().stage());
        }
    }
    @Test public void retirementSuppressesConsentButDoesNotSettleOriginalOsWork()throws Exception{
        try(Fixture f=new Fixture()){
            toForce2(f.flow);ContinuityCoordinator.OwnerAttempt pending=attempt(f.flow);pending.nativeWorkMayStart();f.flow.retire();assertTrue(f.flow.view().hasPendingNativeOwnerWork());pending.nativeSubmissionUnknown();assertTrue(f.flow.view().hasPendingNativeOwnerWork());pending.nativeTerminal(true);assertFalse(f.flow.view().hasPendingNativeOwnerWork());assertThrows(IllegalStateException.class,pending::verifiedAfterNativeTicketConsume);assertThrows(IllegalStateException.class,f.flow::availableConsent);
        }
    }
    @Test public void reservationBeforeForegroundIsNotSubmittedNativeWork()throws Exception{
        try(Fixture f=new Fixture()){
            toForce2(f.flow);ContinuityCoordinator.OwnerAttempt pending=attempt(f.flow);assertFalse(f.flow.view().hasPendingNativeOwnerWork());pending.nativeNoSubmissionYet();pending.checkBeforeNativeBegin();f.flow.retire();assertFalse(f.flow.view().hasPendingNativeOwnerWork());assertThrows(IllegalStateException.class,pending::nativeWorkMayStart);
        }
    }
    @Test public void positiveNeverSubmittedObservationCanRetrySameUnstartedAttempt()throws Exception{
        try(Fixture f=new Fixture()){
            toForce2(f.flow);ContinuityCoordinator.OwnerAttempt pending=attempt(f.flow);pending.nativeWorkMayStart();pending.nativeNoSubmissionYet();assertFalse(f.flow.view().hasPendingNativeOwnerWork());pending.nativeWorkMayStart();assertTrue(f.flow.view().hasPendingNativeOwnerWork());pending.nativeTerminal(true);pending.verifiedAfterNativeTicketConsume();assertEquals(ContinuityState.Confirmation.FORCE_3,f.flow.view().step());
        }
    }
    @Test public void nativeTicketWaitIsNotStepSuccessOrNewPromptCredit()throws Exception{
        try(Fixture f=new Fixture()){
            toForce2(f.flow);ContinuityCoordinator.OwnerAttempt pending=attempt(f.flow);pending.nativeWorkMayStart();pending.nativeTerminal(true);assertFalse(f.flow.view().hasPendingNativeOwnerWork());assertEquals(ContinuityCoordinator.Stage.WAITING_TICKET,f.flow.view().stage());assertThrows(IllegalStateException.class,()->attempt(f.flow));assertThrows(IllegalStateException.class,()->ordinary(f.flow));assertThrows(IllegalStateException.class,f.flow::availableConsent);pending.verifiedAfterNativeTicketConsume();assertEquals(ContinuityState.Confirmation.FORCE_3,f.flow.view().step());
        }
    }
    @Test public void failedVerificationDoesNotBecomeNoLockOrAdvanceAnyStep()throws Exception{
        try(Fixture f=new Fixture()){
            toForce2(f.flow);ContinuityCoordinator.OwnerAttempt pending=attempt(f.flow);pending.nativeWorkMayStart();pending.nativeTerminal(false);pending.nativeTerminal(true);assertThrows(IllegalStateException.class,pending::verifiedAfterNativeTicketConsume);assertEquals(ContinuityCoordinator.Stage.RETIRED,f.flow.view().stage());assertEquals(ContinuityState.Confirmation.TRUST_SOURCE,f.newFlow().view().step());
        }
    }
    @Test public void duplicateOrOldSecondVerificationCannotCompleteThird()throws Exception{
        try(Fixture f=new Fixture()){
            toForce2(f.flow);ContinuityCoordinator.OwnerAttempt second=attempt(f.flow);syntheticVerified(second);ContinuityCoordinator.OwnerAttempt third=attempt(f.flow);third.nativeWorkMayStart();second.nativeTerminal(true);assertThrows(IllegalStateException.class,second::verifiedAfterNativeTicketConsume);assertTrue(f.flow.view().hasPendingNativeOwnerWork());assertEquals(ContinuityState.Confirmation.FORCE_3,f.flow.view().step());third.nativeTerminal(false);
        }
    }
    @Test public void sourceRevokedAfterActualTerminalStillBlocksTicketEligibility()throws Exception{
        try(Fixture f=new Fixture()){
            toForce2(f.flow);ContinuityCoordinator.OwnerAttempt pending=attempt(f.flow);pending.nativeWorkMayStart();pending.nativeTerminal(true);f.current=false;assertThrows(SecurityException.class,pending::checkBeforeNativeTicketConsume);assertFalse(f.flow.view().hasPendingNativeOwnerWork());assertEquals(ContinuityCoordinator.Stage.RETIRED,f.flow.view().stage());
        }
    }
    @Test public void sourceRevokedWithOldOsWorkStillPendingCanOnlySettleNotPublish()throws Exception{
        try(Fixture f=new Fixture()){
            toForce2(f.flow);ContinuityCoordinator.OwnerAttempt pending=attempt(f.flow);pending.nativeWorkMayStart();f.current=false;assertThrows(SecurityException.class,f.flow::availableConsent);assertTrue(f.flow.view().hasPendingNativeOwnerWork());pending.nativeTerminal(true);assertFalse(f.flow.view().hasPendingNativeOwnerWork());assertThrows(IllegalStateException.class,pending::verifiedAfterNativeTicketConsume);
        }
    }
    @Test public void revocationAfterFinalConsentPreventsEvenOneNativeConsumption()throws Exception{
        try(Fixture f=new Fixture()){
            ContinuityCoordinator.Consent consent=syntheticFinalConsent(f.flow);f.current=false;assertThrows(SecurityException.class,()->f.flow.consumeConsentForNativeErasure(consent));assertEquals(ContinuityCoordinator.Stage.RETIRED,f.flow.view().stage());assertEquals(1,f.store.inspect().record().revision());
        }
    }
    @Test public void consentSingleUseReturnsOnlyBoundMetadataNotBusinessUnlock()throws Exception{
        try(Fixture f=new Fixture()){
            ContinuityCoordinator.Consent consent=syntheticFinalConsent(f.flow);assertSame(f.context,f.flow.consumeConsentForNativeErasure(consent));assertEquals(ContinuityCoordinator.Stage.CONSENT_CONSUMED,f.flow.view().stage());assertThrows(IllegalStateException.class,()->f.flow.consumeConsentForNativeErasure(consent));assertEquals(LastGoodSourceStore.Phase.STABLE,f.store.inspect().record().phase());
        }
    }
    @Test public void foreignFlowCannotUseAnotherWindowsConsent()throws Exception{
        try(Fixture f=new Fixture()){
            ContinuityCoordinator.Consent foreign=syntheticFinalConsent(f.flow);ContinuityCoordinator other=f.newFlow();ContinuityCoordinator.Consent own=syntheticFinalConsent(other);assertNotSame(foreign,own);assertThrows(IllegalStateException.class,()->other.consumeConsentForNativeErasure(foreign));assertSame(f.context,other.consumeConsentForNativeErasure(own));
        }
    }
    @Test public void finalConsentAtThirtySecondsAllowedButNotAfterward()throws Exception{
        try(Fixture f=new Fixture()){
            ContinuityCoordinator.Consent allowed=syntheticFinalConsent(f.flow);f.time.now+=30_000;assertSame(f.context,f.flow.consumeConsentForNativeErasure(allowed));ContinuityCoordinator other=f.newFlow();ContinuityCoordinator.Consent expired=syntheticFinalConsent(other);f.time.now+=30_001;assertThrows(SecurityException.class,()->other.consumeConsentForNativeErasure(expired));assertEquals(ContinuityCoordinator.Stage.RETIRED,other.view().stage());
        }
    }
    @Test public void clockRollbackPoisonsEligibilityButOldWorkCanStillActuallyEnd()throws Exception{
        try(Fixture f=new Fixture()){
            toForce2(f.flow);ContinuityCoordinator.OwnerAttempt pending=attempt(f.flow);pending.nativeWorkMayStart();f.time.now--;assertThrows(SecurityException.class,f.flow::availableConsent);assertTrue(f.flow.view().hasPendingNativeOwnerWork());pending.nativeTerminal(false);assertFalse(f.flow.view().hasPendingNativeOwnerWork());f.time.now+=100;assertThrows(IllegalStateException.class,()->ordinary(f.flow));
        }
    }
    @Test public void unavailableClockIsNeverConsentOrActualWorkSettlement()throws Exception{
        try(Fixture f=new Fixture()){
            toForce2(f.flow);ContinuityCoordinator.OwnerAttempt pending=attempt(f.flow);pending.nativeWorkMayStart();f.time.broken=true;assertThrows(SecurityException.class,f.flow::availableConsent);assertTrue(f.flow.view().hasPendingNativeOwnerWork());pending.nativeTerminal(false);assertFalse(f.flow.view().hasPendingNativeOwnerWork());
        }
    }
    @Test public void wrongThreadCannotChangeStepOrReportNativeOwnerEnd()throws Exception{
        try(Fixture f=new Fixture()){
            toForce2(f.flow);ContinuityCoordinator.OwnerAttempt pending=attempt(f.flow);pending.nativeWorkMayStart();AtomicReference<Throwable> failure=new AtomicReference<>();Thread other=new Thread(()->{try{pending.nativeTerminal(false);}catch(Throwable t){failure.set(t);}});other.start();other.join(2000);assertFalse(other.isAlive());assertTrue(failure.get() instanceof IllegalStateException);assertTrue(f.flow.view().hasPendingNativeOwnerWork());pending.nativeTerminal(false);
        }
    }
    @Test public void currentGuardRetiringDuringCheckNeverAdvancesOrdinaryStep()throws Exception{
        try(Fixture f=new Fixture()){
            AtomicReference<ContinuityCoordinator> ref=new AtomicReference<>();ContinuityCoordinator flow=new ContinuityCoordinator(f.context,new Object(),c->ref.get().retire(),f.time);ref.set(flow);assertThrows(IllegalStateException.class,()->ordinary(flow));assertEquals(ContinuityState.Confirmation.TRUST_SOURCE,flow.view().step());assertEquals(ContinuityCoordinator.Stage.RETIRED,flow.view().stage());
        }
    }
    @Test public void reentrantGuardCannotReserveOrSkipAConfirmation()throws Exception{
        try(Fixture f=new Fixture()){
            AtomicReference<ContinuityCoordinator> ref=new AtomicReference<>();ContinuityCoordinator flow=new ContinuityCoordinator(f.context,new Object(),c->ordinary(ref.get()),f.time);ref.set(flow);assertThrows(SecurityException.class,()->ordinary(flow));assertEquals(ContinuityState.Confirmation.TRUST_SOURCE,flow.view().step());
        }
    }
    @Test public void contextRejectsWrongPreparedCandidateDestructivePhaseAndSameSource()throws Exception{
        try(Fixture f=new Fixture()){
            assertThrows(IllegalArgumentException.class,()->ContinuityCoordinator.Context.fromNativeMetadata(f.baseline,A,R));assertThrows(IllegalArgumentException.class,()->ContinuityCoordinator.Context.fromNativeMetadata(f.baseline,B.toUpperCase(Locale.ROOT),S));
            LastGoodSourceStore.View prepared=f.store.prepareReplacement(f.baseline,pair(B,S,2),FIXTURE_ONLY);assertNotNull(ContinuityCoordinator.Context.fromNativeMetadata(prepared,B,S));assertThrows(IllegalArgumentException.class,()->ContinuityCoordinator.Context.fromNativeMetadata(prepared,B,R));LastGoodSourceStore.View destructive=f.store.markDestructionMayStart(prepared,FIXTURE_ONLY);assertThrows(IllegalArgumentException.class,()->ContinuityCoordinator.Context.fromNativeMetadata(destructive,B,S));
        }
    }
    @Test public void contextEpochIsDefensiveAndInterruptionDoesNotPersistConfirmations()throws Exception{
        try(Fixture f=new Fixture()){
            byte[] e=f.context.oldEpoch();e[0]=9;assertEquals(1,f.context.oldEpoch()[0]);toForce2(f.flow);f.flow.retire();assertEquals(ContinuityState.Confirmation.TRUST_SOURCE,f.newFlow().view().step());assertEquals(A,f.store.inspect().record().lastGood().signingSha256());
        }
    }
    @Test public void publicApiHasNoBooleanVerificationOrConsentConstructor()throws Exception{
        for(java.lang.reflect.Constructor<?> c:ContinuityCoordinator.Consent.class.getDeclaredConstructors()){assertFalse(Modifier.isPublic(c.getModifiers()));if(!c.isSynthetic())assertTrue(Modifier.isPrivate(c.getModifiers()));}
        for(java.lang.reflect.Method method:ContinuityCoordinator.OwnerAttempt.class.getMethods())assertFalse(method.getName().equals("verifiedAfterNativeTicketConsume"));
        for(java.lang.reflect.Constructor<?> c:ContinuityCoordinator.class.getDeclaredConstructors())assertFalse(Modifier.isPublic(c.getModifiers()));
    }
}
