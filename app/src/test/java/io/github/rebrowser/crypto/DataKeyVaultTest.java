package io.github.rebrowser.crypto;
import static org.junit.Assert.*;
import io.github.rebrowser.bootstrap.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;
/** Actual wrapping/record composition with PRIVATE reflected public-TOY key, no Android provider. */
public final class DataKeyVaultTest {
 private static final class F {final AtomicReference<ComponentGate.SourceView> source=new AtomicReference<>(ComponentGate.SourceView.acceptedNative(new Object(),new byte[16]));final ComponentGate gate=ComponentGate.forNativeSource(ProcessRole.MAIN,source::get);final ComponentGate.BusinessPermit permit=gate.requireBusiness();final DataKeyVault vault=create(gate,new byte[16]);}
 private static DataKeyVault create(ComponentGate gate,byte[] epoch){try{Constructor<DataKeyVault> c=DataKeyVault.class.getDeclaredConstructor(SecretKey.class,ComponentGate.class,byte[].class,boolean.class);assertTrue(Modifier.isPrivate(c.getModifiers()));c.setAccessible(true);return c.newInstance(new SecretKeySpec(new byte[32],"AES"),gate,epoch,false);}catch(Exception e){throw new AssertionError(e);}}
 private static PrivateRecordSealer.Binding b(){return new PrivateRecordSealer.Binding(PrivateRecordSealer.Purpose.BROWSER_CATALOG,new byte[16],new byte[16],1);}
 @Test public void independentWrappingAndRecordRoundTrip()throws Exception{F f=new F();byte[] data={1,2,0,-1};byte[] sealed=f.vault.seal(b(),data,f.permit);assertEquals(164,sealed.length);assertArrayEquals(data,f.vault.open(b(),sealed,f.permit));assertEquals(1,DataKeyVault.unverifiedRevision(sealed));}
 @Test public void wrappedFreshKeysAndNoncesNeverReuseCiphertext()throws Exception{F f=new F();assertFalse(Arrays.equals(f.vault.seal(b(),new byte[0],f.permit),f.vault.seal(b(),new byte[0],f.permit)));}
 @Test public void everyEnvelopeByteTamperRejectedWithoutPrivateReturn()throws Exception{F f=new F();byte[] sealed=f.vault.seal(b(),new byte[]{1},f.permit);for(int i=0;i<sealed.length;i++){byte[] corrupt=sealed.clone();corrupt[i]^=1;assertThrows(GeneralSecurityException.class,()->f.vault.open(b(),corrupt,f.permit));}}
 @Test public void anotherRevisionCannotBorrowWrappedDek()throws Exception{F f=new F();byte[] sealed=f.vault.seal(b(),new byte[]{1},f.permit);PrivateRecordSealer.Binding other=new PrivateRecordSealer.Binding(b().purpose(),new byte[16],new byte[16],2);assertThrows(GeneralSecurityException.class,()->f.vault.open(other,sealed,f.permit));}
 @Test public void anotherScopeCannotBorrowWrappedDek()throws Exception{F f=new F();byte[] sealed=f.vault.seal(b(),new byte[]{1},f.permit),scope=new byte[16];scope[0]=1;PrivateRecordSealer.Binding other=new PrivateRecordSealer.Binding(b().purpose(),new byte[16],scope,1);assertThrows(GeneralSecurityException.class,()->f.vault.open(other,sealed,f.permit));}
 @Test public void purposeSeparationAppliesAlsoToWrappingLayer()throws Exception{F f=new F();byte[] sealed=f.vault.seal(b(),new byte[]{1},f.permit);assertThrows(GeneralSecurityException.class,()->f.vault.open(new PrivateRecordSealer.Binding(PrivateRecordSealer.Purpose.GECKO_CHECKPOINT,new byte[16],new byte[16],1),sealed,f.permit));}
 @Test public void foreignGateAndEpochRejectBeforeUse(){F f=new F(),other=new F();assertThrows(IllegalArgumentException.class,()->f.vault.requireBindings(other.gate,new byte[16],other.permit));byte[] epoch=new byte[16];epoch[0]=1;assertThrows(IllegalArgumentException.class,()->f.vault.requireBindings(f.gate,epoch,f.permit));assertThrows(SecurityException.class,()->f.vault.seal(b(),new byte[0],other.permit));}
 @Test public void sourceFenceBlocksOldVaultAndNewPermitCannotRetargetIt()throws Exception{F f=new F();byte[] sealed=f.vault.seal(b(),new byte[0],f.permit);f.source.set(ComponentGate.SourceView.acceptedNative(new Object(),new byte[16]));ComponentGate.BusinessPermit fresh=f.gate.requireBusiness();assertThrows(SecurityException.class,()->f.vault.open(b(),sealed,fresh));assertThrows(SecurityException.class,()->f.vault.seal(b(),new byte[0],fresh));}
 @Test public void absentNativeFactoryInputCannotGenerateFallbackKey(){F f=new F();assertThrows(IllegalArgumentException.class,()->DataKeyVault.openExisting(null,f.gate,new byte[16],f.permit));}
 @Test public void noPublicProviderOrKeyGenerationDeletionApi(){assertEquals(0,DataKeyVault.class.getConstructors().length);for(Method m:DataKeyVault.class.getDeclaredMethods())if(Modifier.isPublic(m.getModifiers()))for(String forbidden:new String[]{"generate","delete","alias","export","seed","provider"})assertFalse(m.getName().toLowerCase().contains(forbidden));}
 @Test public void outerLengthAndTruncationRemainStrict()throws Exception{F f=new F();byte[] sealed=f.vault.seal(b(),new byte[0],f.permit);assertThrows(IllegalArgumentException.class,()->f.vault.open(b(),new byte[159],f.permit));assertThrows(GeneralSecurityException.class,()->f.vault.open(b(),Arrays.copyOf(sealed,sealed.length+1),f.permit));}
 @Test public void untrustedRevisionCandidateNotAuthentication()throws Exception{F f=new F();byte[] sealed=f.vault.seal(b(),new byte[0],f.permit);ByteBuffer.wrap(sealed,120,8).putLong(9);assertEquals(9,DataKeyVault.unverifiedRevision(sealed));assertThrows(GeneralSecurityException.class,()->f.vault.open(new PrivateRecordSealer.Binding(b().purpose(),new byte[16],new byte[16],9),sealed,f.permit));}
}
