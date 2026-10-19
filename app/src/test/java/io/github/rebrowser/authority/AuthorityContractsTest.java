package io.github.rebrowser.authority;

import static org.junit.Assert.*;

import io.github.rebrowser.control.ControlDecoder;
import io.github.rebrowser.control.ControlEnvelope;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.EnumMap;
import org.junit.Test;

/** Compiled identity/key-possession tests, NOT disable-ledger/whitelist/action admission tests. */
public final class AuthorityContractsTest {
    private static final String SPKI="MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q";
    private static final String SIG="MEYCIQCU0ZY6UBOMmUrOwly4Z3xr6yk6H_DlhL3JyDLQiLw7uwIhAP1d0grGvaikMFcw0IIjzYz83WVELc0ZC5QAqRidveH9";
    private static final String HIGH_S_ALTERNATE="MEUCIQCU0ZY6UBOMmUrOwly4Z3xr6yk6H_DlhL3JyDLQiLw7uwIgAqIt9DlCV1zPqM8vfdwycsAJlWl5SoV5X7khql6lQ1Q";
    private interface Checked{void run()throws Exception;}
    private static byte[] u(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static byte[] spki(){return CryptoEncoding.decodeBase64Url(SPKI,91);}
    private static EnumMap<ControlEnvelope.Field,byte[]> fields(){
        int[] exact={0,0,0,32,16,32,32,8,16,32,8,16,0,0,0,32,0};EnumMap<ControlEnvelope.Field,byte[]> f=new EnumMap<>(ControlEnvelope.Field.class);
        for(ControlEnvelope.Field key:ControlEnvelope.Field.values()){byte[] b=new byte[exact[key.ordinal()]==0?1:exact[key.ordinal()]];Arrays.fill(b,(byte)(key.ordinal()+1));f.put(key,b);}
        f.put(ControlEnvelope.Field.PROTOCOL,u("rebrowser.control/1"));f.put(ControlEnvelope.Field.APPLICATION_ID,u("io.github.rebrowser"));f.put(ControlEnvelope.Field.PRINCIPAL_KEY_ID,u("public-dummy-principal"));
        f.put(ControlEnvelope.Field.DISABLE_REVISION,ByteBuffer.allocate(8).putLong(3).array());f.put(ControlEnvelope.Field.DEADLINE_ELAPSED_MS,ByteBuffer.allocate(8).putLong(30000).array());f.put(ControlEnvelope.Field.ACTION_ID,u("mail.query"));f.put(ControlEnvelope.Field.SCOPE_REF,u("account_synthetic"));f.put(ControlEnvelope.Field.OWNER_GRANT_REF,new byte[0]);f.put(ControlEnvelope.Field.DELEGATION_SHA256,new byte[32]);f.put(ControlEnvelope.Field.ARGS_BYTES,u("{\"query\":\"合成\",\"limit\":2}"));return f;
    }
    private static ControlDecoder.UnverifiedProof proof(EnumMap<ControlEnvelope.Field,byte[]> fields,String signature,String extra){
        return ControlDecoder.decode(u("{\"protocol\":\"rebrowser.control/1\",\"frameB64\":\""+CryptoEncoding.base64Url(ControlEnvelope.fromFields(fields).signedBytes())+"\",\"signatureB64\":\""+signature+"\""+extra+"}"));
    }
    private static CompiledPrincipals registry()throws Exception{return new CompiledPrincipals(new CompiledPrincipals.Entry("public-dummy-principal",CompiledPrincipals.Role.ROOT,10,spki()));}
    private static void reject(Checked c)throws Exception{try{c.run();fail();}catch(GeneralSecurityException|IllegalArgumentException expected){}}

    @Test public void ordinaryThresholdAndDisableRelationsStayDistinct(){
        for(int actor=0;actor<=10;actor++)for(int target=0;target<=10;target++){
            assertEquals(actor>=target,AuthorityScale.meetsThreshold(actor,target));
            assertEquals(target!=10&&actor>target,AuthorityScale.mayDisableLevel(actor,target));
        }
        assertEquals(5,AuthorityScale.HIGH_BOUNDARY);assertEquals(9,AuthorityScale.HIGHEST);
        for(int n=0;n<=10;n++)assertEquals(n<5,AuthorityScale.isLowDelegationLevel(n));
    }
    @Test public void invalidLevelsNeverCoerce()throws Exception{
        for(int n:new int[]{-1,11,Integer.MIN_VALUE,Integer.MAX_VALUE})reject(()->AuthorityScale.meetsThreshold(n,1));
    }
    @Test public void productionPolicyIsUnconfiguredAndRejectsPublicTestKey()throws Exception{
        assertFalse(CompiledPrincipals.production().formalRootConfigured());assertEquals(0,CompiledPrincipals.production().size());
        reject(()->new AdminProofVerifier(CompiledPrincipals.production()).verifyCompiledKey(proof(fields(),SIG,"")));
    }
    @Test public void validHistoricalProofAndEquivalentSignatureMapToOneCompiledIdentity()throws Exception{
        CompiledPrincipals table=registry();AdminProofVerifier verifier=new AdminProofVerifier(table);
        for(String signature:new String[]{SIG,HIGH_S_ALTERNATE}){
            AdminProofVerifier.KeyProof key=verifier.verifyCompiledKey(proof(fields(),signature,""));
            assertSame(table.find("public-dummy-principal"),key.compiledPrincipal());assertEquals(10,key.compiledPrincipal().level());
            assertEquals(CompiledPrincipals.Role.ROOT,key.compiledPrincipal().role());
        }
        // No caller app/signature/source parameter exists. Key possession is not effective action admission.
    }
    @Test public void changedSignedArgsAndShapeOnlySignatureFail()throws Exception{
        AdminProofVerifier verifier=new AdminProofVerifier(registry());EnumMap<ControlEnvelope.Field,byte[]> f=fields();f.put(ControlEnvelope.Field.ARGS_BYTES,u("{\"claimedRoot\":true}"));
        reject(()->verifier.verifyCompiledKey(proof(f,SIG,"")));reject(()->verifier.verifyCompiledKey(proof(fields(),"MAYCAQECAQE","")));
    }
    @Test public void duplicateIdsOrSameKeyUnderDifferentRolesAreNotNewIdentities()throws Exception{
        CompiledPrincipals.Entry root=new CompiledPrincipals.Entry("one",CompiledPrincipals.Role.ROOT,10,spki());
        CompiledPrincipals.Entry alias=new CompiledPrincipals.Entry("two",CompiledPrincipals.Role.LOW,1,spki());
        reject(()->new CompiledPrincipals(root,root));reject(()->new CompiledPrincipals(root,alias));
    }
    @Test public void roleLevelAndKeyDefinitionsAreStrict()throws Exception{
        reject(()->new CompiledPrincipals.Entry("root",CompiledPrincipals.Role.ROOT,9,spki()));
        reject(()->new CompiledPrincipals.Entry("highest",CompiledPrincipals.Role.HIGHEST,8,spki()));
        reject(()->new CompiledPrincipals.Entry("low",CompiledPrincipals.Role.LOW,5,spki()));
        reject(()->new CompiledPrincipals.Entry("bad id",CompiledPrincipals.Role.HIGH,5,spki()));
        reject(()->new CompiledPrincipals.Entry("root",CompiledPrincipals.Role.ROOT,10,new byte[91]));
    }
    @Test public void delegatedBytesDoNotSilentlyBecomeDirectCompiledProof()throws Exception{
        EnumMap<ControlEnvelope.Field,byte[]> f=fields();byte[] cert={1,2,3};f.put(ControlEnvelope.Field.DELEGATION_SHA256,CryptoEncoding.sha256(cert));
        ControlDecoder.UnverifiedProof delegated=proof(f,SIG,",\"delegationB64\":\"AQID\"");
        reject(()->new AdminProofVerifier(registry()).verifyCompiledKey(delegated));
    }
    @Test public void nullAndUnknownIdentityUseGenericPrivateValueFreeFailure()throws Exception{
        AdminProofVerifier verifier=new AdminProofVerifier(registry());reject(()->verifier.verifyCompiledKey(null));
        EnumMap<ControlEnvelope.Field,byte[]> f=fields();f.put(ControlEnvelope.Field.PRINCIPAL_KEY_ID,u("unknown-synthetic"));
        try{verifier.verifyCompiledKey(proof(f,SIG,""));fail();}catch(GeneralSecurityException expected){assertEquals("Invalid administrator proof",expected.getMessage());}
    }
}
