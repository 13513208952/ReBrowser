package io.github.rebrowser.authority;

import static org.junit.Assert.*;
import io.github.rebrowser.control.ActionRegistry;
import io.github.rebrowser.crypto.CryptoEncoding;
import io.github.rebrowser.continuity.SourceBootstrap;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import org.junit.Test;

/** ACTUAL public production Root and public OpenSSL provisioning signature, JCA verified.
 * NO private material/OS consent/install/device enrollment/source acceptance or dispatch. */
public final class ProductionRootProvisioningTest {
    private static final String ID="rebrowser-root-2897220b1306695b",FP="2897220b1306695b955d7d8d91c9d34f23d342e9ae5260b68619363b02fd2f11";
    private static byte[] challenge(){return CryptoEncoding.decodeBase64Url("UmVCcm93c2VyLlJvb3QuUHJvdmlzaW9uaW5nLlZlcmlmeS4xAIoN6GrAuD6oQfZzG4zXBqWlBmqWtSPhu-Sa2eaTIIGC",256);}
    private static byte[] signature(){return CryptoEncoding.decodeBase64Url("MEQCIEBfiiBO86s5iVMVrkgRJDKt8RlMIfM34CEBlAU_BmfxAiAa3lU6U86o_xI4TOIYQa8gl6itzNvX4lPpeYYMQxJ7eA",72);}
    @Test public void actualInitialRootIsPublicPinnedIdentityNotTestScalarOrRuntimeInput(){CompiledPrincipals c=CompiledPrincipals.production();assertEquals(1,c.size());assertTrue(c.formalRootConfigured());CompiledPrincipals.Entry e=c.find(ID);assertNotNull(e);assertEquals(FP,e.spkiSha256());assertEquals(CompiledPrincipals.Role.ROOT,e.role());assertEquals(10,e.level());assertEquals(FP,c.rootSpkiSha256());assertNull(c.find("public-dummy-principal"));assertEquals(91,e.publicKey().getEncoded().length);}
    @Test public void actualOpenSslPossessionSampleVerifiesWithProductionPublicKeyInJca()throws Exception{byte[] message=challenge(),sig=signature();assertTrue(new String(message,0,36,StandardCharsets.US_ASCII).startsWith("ReBrowser.Root.Provisioning.Verify.1"));CryptoEncoding.validateP256DerSignature(sig);Signature verify=Signature.getInstance("SHA256withECDSA");verify.initVerify(CompiledPrincipals.production().find(ID).publicKey());verify.update(message);assertTrue(verify.verify(sig));message[message.length-1]^=1;verify.initVerify(CompiledPrincipals.production().find(ID).publicKey());verify.update(message);assertFalse(verify.verify(sig));}
    @Test public void initialRootCanDelegateOnlyExistingLowActionScopesNotCreateHighIdentity(){CompiledPrincipals.Entry root=CompiledPrincipals.production().find(ID);assertEquals(4,root.maxLowDelegation());for(String action:new String[]{"host.window.back","host.openDefaultBrowserFlow","host.openPermissionSettings","host.openInstaller"}){assertTrue(root.permitsLowDelegationAction(action));assertTrue(ActionRegistry.require(action).requiredLevel()<=root.maxLowDelegation());}assertFalse(root.permitsLowDelegationAction("authority.addHighTrustPrincipal"));assertThrows(IllegalArgumentException.class,()->ActionRegistry.require("authority.addHighTrustPrincipal"));}
    @Test public void adbConnectedCandidateIsNotAnEnrolledInstallation(){assertEquals(0,DeviceWhitelist.production(CompiledPrincipals.production()).compiledDeviceEntryCount());assertEquals(SourceBootstrap.Status.UNAVAILABLE,SourceBootstrap.inspectOwn(null,null).status());}
    @Test public void explicitEmptyCompiledFixtureStillFailsClosed(){CompiledPrincipals empty=new CompiledPrincipals();assertFalse(empty.formalRootConfigured());assertNull(empty.rootSpkiSha256());assertEquals(0,empty.size());}
    @Test public void compiledRootDoesNotChangeStrictDisableRelation(){for(int actor=0;actor<=10;actor++)assertFalse(AuthorityScale.mayDisableLevel(actor,10));assertTrue(AuthorityScale.meetsThreshold(10,10));}
}
