package io.github.rebrowser.authority;
import static org.junit.Assert.*;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import org.junit.Test;
/** Real HOST P256 signatures and private native issuer, synthetic source/clock/compiled principals.
 * No actual owner prompt, production key, device enrollment or signing/provisioning execution. */
public final class EnrollmentProposalTest {
 static final class F {
  final DelegationAndPossessionTest.P p=new DelegationAndPossessionTest.P("high");
  final EnrollmentProposal proposals=new EnrollmentProposal(p.f.compiled,p.f.pin,p.f.time::get);
  F()throws Exception{}
  EnrollmentProposal.Challenge issue(String principal,String owner)throws Exception{return proposals.prepareForNativeReview(p.request(principal),owner,p.device.getPublic().getEncoded());}
  byte[] sign(EnrollmentProposal.Challenge c)throws Exception{return CurrentAuthorityKernelTest.sign(p.device.getPrivate(),c.signingBytes());}
 }
 @Test public void realFreshTargetProofIsDraftNotEnrollmentOrOwnerConsent()throws Exception{F f=new F();EnrollmentProposal.Challenge c=f.issue("root","high");EnrollmentProposal.Draft d=f.proposals.verifyTargetPossessionForDraft(c,f.sign(c));assertEquals("high",d.listOwnerKeyId());assertEquals("root",d.preparerKeyId());assertEquals(f.p.target.length,32);assertEquals(CryptoEncoding.sha256Hex(f.p.device.getPublic().getEncoded()),d.proposedInstallSpkiSha256());assertTrue(d.assurance().contains("NO_OWNER_CONSENT"));assertTrue(new String(d.portableDraftBytes(),StandardCharsets.ISO_8859_1).startsWith("ReBrowser.Enrollment.NOT_OWNER_APPROVED.DRAFT.1"));}
 @Test public void highestOnlyOwnListWhileRootMayUseBoth()throws Exception{F f=new F();assertNotNull(f.issue("high","high"));assertThrows(SecurityException.class,()->f.issue("high","root"));assertNotNull(f.issue("root","root"));assertNotNull(f.issue("root","high"));}
 @Test public void unknownOrLowListCannotBeProposed()throws Exception{F f=new F();assertThrows(SecurityException.class,()->f.issue("root","missing"));assertThrows(SecurityException.class,()->f.issue("root","low"));}
 @Test public void repeatedTargetProofCannotReplay()throws Exception{F f=new F();EnrollmentProposal.Challenge c=f.issue("root","high");byte[] sig=f.sign(c);f.proposals.verifyTargetPossessionForDraft(c,sig);assertThrows(SecurityException.class,()->f.proposals.verifyTargetPossessionForDraft(c,sig));}
 @Test public void wrongSignatureSpendsChallengeBeforeVerification()throws Exception{F f=new F();EnrollmentProposal.Challenge c=f.issue("root","high");assertThrows(SecurityException.class,()->f.proposals.verifyTargetPossessionForDraft(c,CurrentAuthorityKernelTest.sign(f.p.f.root.getPrivate(),c.signingBytes())));assertThrows(SecurityException.class,()->f.proposals.verifyTargetPossessionForDraft(c,f.sign(c)));}
 @Test public void malformedDerAlsoSpends()throws Exception{F f=new F();EnrollmentProposal.Challenge c=f.issue("root","high");assertThrows(IllegalArgumentException.class,()->f.proposals.verifyTargetPossessionForDraft(c,new byte[]{1}));assertThrows(SecurityException.class,()->f.proposals.verifyTargetPossessionForDraft(c,f.sign(c)));}
 @Test public void anotherIssuerCannotConsumeEvenIdenticalPolicy()throws Exception{F f=new F();EnrollmentProposal.Challenge c=f.issue("root","high");EnrollmentProposal other=new EnrollmentProposal(f.p.f.compiled,f.p.f.pin,f.p.f.time::get);assertThrows(SecurityException.class,()->other.verifyTargetPossessionForDraft(c,f.sign(c)));assertNotNull(f.proposals.verifyTargetPossessionForDraft(c,f.sign(c)));}
 @Test public void deadlineExactIsExpired()throws Exception{F f=new F();EnrollmentProposal.Challenge c=f.issue("root","high");f.p.f.time.addAndGet(30_000);assertThrows(SecurityException.class,()->f.proposals.verifyTargetPossessionForDraft(c,f.sign(c)));}
 @Test public void clockRollbackIsSticky()throws Exception{F f=new F();EnrollmentProposal.Challenge c=f.issue("root","high");long old=f.p.f.time.get();f.p.f.time.set(old-1);assertThrows(SecurityException.class,()->f.proposals.verifyTargetPossessionForDraft(c,f.sign(c)));f.p.f.time.set(old);assertThrows(SecurityException.class,()->f.issue("root","high"));}
 @Test public void sameEpochSourceIncarnationRevokesChallenge()throws Exception{F f=new F();EnrollmentProposal.Challenge c=f.issue("root","high");f.p.f.incarnation.set(new Object());assertThrows(SecurityException.class,()->f.proposals.verifyTargetPossessionForDraft(c,f.sign(c)));}
 @Test public void nativeSignerMustMatchBothActualKeyAndEpoch()throws Exception{F f=new F();EnrollmentProposal.Challenge c=f.issue("root","high");c.requireCurrentTarget(f.p.target,f.p.f.epoch);assertThrows(SecurityException.class,()->c.requireCurrentTarget(new byte[32],f.p.f.epoch));byte[] wrong=f.p.f.epoch.clone();wrong[0]^=1;assertThrows(SecurityException.class,()->c.requireCurrentTarget(f.p.target,wrong));}
 @Test public void spentChallengeNoLongerPermitsNativeSigning()throws Exception{F f=new F();EnrollmentProposal.Challenge c=f.issue("root","high");f.proposals.verifyTargetPossessionForDraft(c,f.sign(c));assertThrows(SecurityException.class,()->c.requireCurrentTarget(f.p.target,f.p.f.epoch));}
 @Test public void challengeAndPortableDraftBytesDefensivelyCopied()throws Exception{F f=new F();EnrollmentProposal.Challenge c=f.issue("root","high");byte[] a=c.signingBytes();a[0]^=1;assertFalse(Arrays.equals(a,c.signingBytes()));EnrollmentProposal.Draft d=f.proposals.verifyTargetPossessionForDraft(c,f.sign(c));byte[] b=d.portableDraftBytes();b[0]^=1;assertFalse(Arrays.equals(b,d.portableDraftBytes()));}
 @Test public void freshNonceAndOwnerAreInSignedOriginalBytes()throws Exception{F f=new F();EnrollmentProposal.Challenge a=f.issue("root","high"),b=f.issue("root","root");assertFalse(Arrays.equals(a.signingBytes(),b.signingBytes()));assertThrows(SecurityException.class,()->f.proposals.verifyTargetPossessionForDraft(b,f.sign(a)));}
 @Test public void targetMustBeExactSignedRequestTarget()throws Exception{F f=new F();KeyPair another=CurrentAuthorityKernelTest.key();assertThrows(SecurityException.class,()->f.proposals.prepareForNativeReview(f.p.request("root"),"high",another.getPublic().getEncoded()));}
 @Test public void productionUnconfiguredCannotIssueAnyDraft()throws Exception{F f=new F();EnrollmentProposal production=new EnrollmentProposal(CompiledPrincipals.production(),f.p.f.pin,f.p.f.time::get);assertThrows(SecurityException.class,()->production.prepareForNativeReview(f.p.request("root"),"root",f.p.device.getPublic().getEncoded()));}
 @Test public void eightPendingLimitDoesNotEvictUnknownOwnership()throws Exception{F f=new F();for(int i=0;i<8;i++)f.issue("root","high");assertThrows(SecurityException.class,()->f.issue("root","high"));}
 @Test public void closeRevokesPendingAndFutureIssuance()throws Exception{F f=new F();EnrollmentProposal.Challenge c=f.issue("root","high");f.proposals.close();assertThrows(SecurityException.class,()->f.proposals.verifyTargetPossessionForDraft(c,f.sign(c)));assertThrows(SecurityException.class,()->f.issue("root","high"));}
}
