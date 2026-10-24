package io.github.rebrowser.crypto;

import static org.junit.Assert.*;
import io.github.rebrowser.bootstrap.*;
import io.github.rebrowser.browser.*;
import io.github.rebrowser.settings.SettingsRepository;
import io.github.rebrowser.data.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;

/** Actual HOST AES-GCM/file readback + canonical existing-reader compatibility. These public
 * TOY keys/reflected host owner are deliberately NOT native first-setup/source/key evidence. */
public final class InitialRecordEnvelopeTest {
    private final SecretKey key=new SecretKeySpec(new byte[32],"AES");
    private PrivateRecordSealer.Binding binding(PrivateRecordSealer.Purpose purpose,String scope){return new PrivateRecordSealer.Binding(purpose,new byte[16],scope.getBytes(StandardCharsets.US_ASCII),1);}
    @Test public void initialFormatConsumedByCanonicalExistingPrivateStore()throws Exception{
        Path directory=Files.createTempDirectory("initial-envelope-");
        AtomicReference<ComponentGate.SourceView> source=new AtomicReference<>(ComponentGate.SourceView.acceptedNative(new Object(),new byte[16]));ComponentGate gate=ComponentGate.forNativeSource(ProcessRole.MAIN,source::get);ComponentGate.BusinessPermit permit=gate.requireBusiness();
        Constructor<DataKeyVault> vc=DataKeyVault.class.getDeclaredConstructor(SecretKey.class,ComponentGate.class,byte[].class,boolean.class);vc.setAccessible(true);DataKeyVault vault=vc.newInstance(key,gate,new byte[16],false);
        Constructor<PrivateRecordStore> pc=PrivateRecordStore.class.getDeclaredConstructor(Path[].class,ComponentGate.class,byte[].class,DataKeyVault.class,boolean.class);pc.setAccessible(true);PrivateRecordStore records=pc.newInstance(new Path[]{directory},gate,new byte[16],vault,false);
        try{
            write(directory,binding(PrivateRecordSealer.Purpose.SETTINGS,"RB-SETTINGS-0001"),SettingsRepository.CODEC.encode(new SettingsRepository.Snapshot(0,SearchEngines.Provider.BING,false,false)));
            write(directory,binding(PrivateRecordSealer.Purpose.HISTORY,"RB-HISTORY-00001"),HistoryStore.CODEC.encode(new HistoryStore.Snapshot(0,false,Collections.emptyList())));
            write(directory,binding(PrivateRecordSealer.Purpose.BOOKMARKS,"RB-BOOKMARKS-001"),BookmarkStore.CODEC.encode(new BookmarkStore.Snapshot(0,Collections.emptyList())));
            OwnedDataCatalog catalog=OwnedDataCatalog.openExisting(records,gate,permit);
            try(OwnedDataCatalog.View<SettingsRepository.Snapshot> view=catalog.read(OwnedDataCatalog.Family.SETTINGS,SettingsRepository.CODEC,permit)){assertEquals(PrivateRecordStore.Kind.CURRENT,view.kind());assertEquals(1,view.recordRevision());assertEquals(SearchEngines.Provider.BING,view.value(permit).searchProvider());assertEquals(SettingsRepository.NewTabBackground.EXCLUSIVE,view.value(permit).newTabBackground());assertTrue(view.value(permit).newTabRecentEnabled());assertEquals(SearchEngines.defaultHome().url(),view.value(permit).homepage());}
            try(OwnedDataCatalog.View<HistoryStore.Snapshot> view=catalog.read(OwnedDataCatalog.Family.HISTORY,HistoryStore.CODEC,permit)){assertTrue(view.value(permit).entries().isEmpty());}
            try(OwnedDataCatalog.View<BookmarkStore.Snapshot> view=catalog.read(OwnedDataCatalog.Family.BOOKMARKS,BookmarkStore.CODEC,permit)){assertTrue(view.value(permit).nodes().isEmpty());}
        }finally{try(java.util.stream.Stream<Path> all=Files.walk(directory)){for(Path p:(Iterable<Path>)all.sorted(Comparator.reverseOrder())::iterator)Files.deleteIfExists(p);}}
    }
    private void write(Path directory,PrivateRecordSealer.Binding binding,byte[] plaintext)throws Exception{byte[] sealed=DataKeyVault.sealEnvelope(key,binding,plaintext);StringBuilder scope=new StringBuilder();for(byte b:binding.nativeScope())scope.append(String.format(java.util.Locale.ROOT,"%02x",b));Path file=directory.resolve("p"+binding.purpose().code()+"_"+scope+".v1");Files.write(file,sealed,StandardOpenOption.CREATE_NEW);assertArrayEquals(plaintext,DataKeyVault.openEnvelope(key,binding,Files.readAllBytes(file)));Arrays.fill(plaintext,(byte)0);}
    @Test public void everyFirstEnvelopeByteTamperFails()throws Exception{PrivateRecordSealer.Binding b=binding(PrivateRecordSealer.Purpose.SETTINGS,"RB-SETTINGS-0001");byte[] sealed=DataKeyVault.sealEnvelope(key,b,new byte[]{1,2});for(int i=0;i<sealed.length;i++){byte[] copy=sealed.clone();copy[i]^=1;assertThrows(java.security.GeneralSecurityException.class,()->DataKeyVault.openEnvelope(key,b,copy));}}
    @Test public void differentKeyOrNativeEpochCannotReadInitialEnvelope()throws Exception{PrivateRecordSealer.Binding b=binding(PrivateRecordSealer.Purpose.SETTINGS,"RB-SETTINGS-0001");byte[] sealed=DataKeyVault.sealEnvelope(key,b,new byte[0]),different=new byte[32];different[0]=1;assertThrows(java.security.GeneralSecurityException.class,()->DataKeyVault.openEnvelope(new SecretKeySpec(different,"AES"),b,sealed));byte[] epoch=new byte[16];epoch[0]=1;assertThrows(java.security.GeneralSecurityException.class,()->DataKeyVault.openEnvelope(key,new PrivateRecordSealer.Binding(b.purpose(),epoch,b.nativeScope(),1),sealed));}
    @Test public void newRecordNeverReusesDekOrNonce()throws Exception{PrivateRecordSealer.Binding b=binding(PrivateRecordSealer.Purpose.HISTORY,"RB-HISTORY-00001");assertFalse(Arrays.equals(DataKeyVault.sealEnvelope(key,b,new byte[0]),DataKeyVault.sealEnvelope(key,b,new byte[0])));}
    @Test public void purePrimitiveRejectsMissingBindingBeforeAllocation(){assertThrows(IllegalArgumentException.class,()->DataKeyVault.sealEnvelope(key,null,new byte[0]));assertThrows(IllegalArgumentException.class,()->DataKeyVault.openEnvelope(key,null,new byte[0]));}
}
