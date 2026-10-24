package io.github.rebrowser.data;

import io.github.rebrowser.browser.SearchEngines;
import io.github.rebrowser.settings.SettingsRepository;
import io.github.rebrowser.settings.SettingsRepository.Snapshot;
import io.github.rebrowser.settings.SettingsRepository.WindowOrientation;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual canonical codec and private encrypted HOST filesystem with synthetic source/key only.
 * Not an Android orientation request, window geometry/rotation or Keystore qualification. */
public final class WindowOrientationStoreTest {
    private static Snapshot legacy(long revision){return new Snapshot(revision,SearchEngines.Provider.BING,false,true,SettingsRepository.NewTabBackground.WHITE,false,"https://www.baidu.com/?a=1#home");}
    @Test public void legacy03BytesAndBorrowedPositionRemainExactlyUnchanged(){
        byte[] old=new NativeRecordCodec.Writer("RBSETT03",SettingsRepository.MAX_BYTES).nonnegative(7).text("bing",16,false)
                .bool(false).bool(true).text("WHITE",16,false).bool(false).text("https://www.baidu.com/?a=1#home",8192,false).finish();
        ByteBuffer buffer=ByteBuffer.allocate(old.length+5);buffer.position(5);buffer.put(old);buffer.flip();buffer.position(5);
        Snapshot read=SettingsRepository.CODEC.decode(buffer);assertNull(read.windowOrientation());assertEquals(5,buffer.position());
        assertArrayEquals(old,SettingsRepository.CODEC.encode(read));assertArrayEquals(old,SettingsRepository.CODEC.encode(legacy(7)));
    }
    @Test public void onlyExplicitDirectionIntentChangesSchemaAndRoundTripsAllModes(){
        for(WindowOrientation mode:WindowOrientation.values()){
            Snapshot after=legacy(7).withWindowOrientation(mode);assertEquals(8,after.metadataRevision());
            byte[] bytes=SettingsRepository.CODEC.encode(after);assertEquals("RBSETT04",new String(bytes,0,8,StandardCharsets.US_ASCII));
            Snapshot read=SettingsRepository.CODEC.decode(ByteBuffer.wrap(bytes));assertEquals(mode,read.windowOrientation());
            assertArrayEquals(bytes,SettingsRepository.CODEC.encode(read));assertEquals(legacy(7).homepage(),read.homepage());
            assertTrue(read.desiredEnabled("music"));assertFalse(read.newTabRecentEnabled());
        }
        assertThrows(IllegalArgumentException.class,()->legacy(1).withWindowOrientation(null));
    }
    @Test public void allOtherSnapshotEditsPreserveDirectionAndLegacyEditsDoNotUpgrade(){
        Snapshot old=legacy(1).withGeneral(SearchEngines.Provider.BAIDU,"about:blank",SettingsRepository.NewTabBackground.EXCLUSIVE,true);
        assertNull(old.windowOrientation());assertEquals('3',SettingsRepository.CODEC.encode(old)[7]);
        Snapshot current=legacy(1).withWindowOrientation(WindowOrientation.LANDSCAPE).withSearch(SearchEngines.Provider.BAIDU)
                .withEnabled("outlook",true).withNewTab(SettingsRepository.NewTabBackground.EXCLUSIVE,true).withHomepage("about:blank")
                .withGeneral(SearchEngines.Provider.BING,"https://cn.bing.com/",SettingsRepository.NewTabBackground.WHITE,false);
        assertEquals(WindowOrientation.LANDSCAPE,current.windowOrientation());assertEquals('4',SettingsRepository.CODEC.encode(current)[7]);
    }
    @Test public void invalidOrIncomplete04NeverFallsBackToLegacy(){
        byte[] valid=SettingsRepository.CODEC.encode(legacy(0).withWindowOrientation(WindowOrientation.PORTRAIT));
        for(int cut:new int[]{0,7,9,valid.length-1})assertThrows(IllegalArgumentException.class,()->SettingsRepository.CODEC.decode(ByteBuffer.wrap(java.util.Arrays.copyOf(valid,cut))));
        byte[] trailing=java.util.Arrays.copyOf(valid,valid.length+1);assertThrows(IllegalArgumentException.class,()->SettingsRepository.CODEC.decode(ByteBuffer.wrap(trailing)));
        byte[] unknown=valid.clone();unknown[unknown.length-1]='X';assertThrows(IllegalArgumentException.class,()->SettingsRepository.CODEC.decode(ByteBuffer.wrap(unknown)));
        byte[] wrong=valid.clone();wrong[7]='5';assertThrows(IllegalArgumentException.class,()->SettingsRepository.CODEC.decode(ByteBuffer.wrap(wrong)));
    }
    @Test public void actualEncrypted03ReadDoesNotMigrateThenExplicitOrientationWrites04()throws Exception{
        try(BrowserMetadataStoreTest.F f=new BrowserMetadataStoreTest.F()){
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){f.settings.compareAndWrite(v,legacy(0),f.permit);}
            java.nio.file.Path path=f.root.resolve(f.ref(OwnedDataCatalog.Family.SETTINGS).opaqueRef());byte[] before=Files.readAllBytes(path);
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){assertNull(v.value(f.permit).windowOrientation());}
            assertArrayEquals(before,Files.readAllBytes(path));long written;
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){written=f.settings.compareAndWriteWindowOrientation(v,v.value(f.permit).withWindowOrientation(WindowOrientation.LANDSCAPE),f.permit);}
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){assertEquals(written,v.recordRevision());assertEquals(WindowOrientation.LANDSCAPE,v.value(f.permit).windowOrientation());assertEquals(legacy(0).homepage(),v.value(f.permit).homepage());}
            assertFalse(java.util.Arrays.equals(before,Files.readAllBytes(path)));assertFalse(new String(Files.readAllBytes(path),StandardCharsets.ISO_8859_1).contains("LANDSCAPE"));
        }
    }
    @Test public void orientationWriterRejectsSmuggledGeneralAndApplicationsAtExactNextRevision()throws Exception{
        try(BrowserMetadataStoreTest.F f=new BrowserMetadataStoreTest.F()){
            f.seedSettings(false);
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){
                Snapshot base=v.value(f.permit);
                Snapshot home=new Snapshot(base.metadataRevision(),base.searchProvider(),false,false,base.newTabBackground(),base.newTabRecentEnabled(),"about:blank").withWindowOrientation(WindowOrientation.PORTRAIT);
                Snapshot apps=new Snapshot(base.metadataRevision(),base.searchProvider(),true,true,base.newTabBackground(),base.newTabRecentEnabled(),base.homepage()).withWindowOrientation(WindowOrientation.PORTRAIT);
                assertThrows(IllegalArgumentException.class,()->f.settings.compareAndWriteWindowOrientation(v,home,f.permit));
                assertThrows(IllegalArgumentException.class,()->f.settings.compareAndWriteWindowOrientation(v,apps,f.permit));
                assertThrows(IllegalArgumentException.class,()->f.settings.compareAndWriteWindowOrientation(v,base,f.permit));
                assertThrows(IllegalArgumentException.class,()->f.settings.compareAndWriteWindowOrientation(v,base.withWindowOrientation(WindowOrientation.PORTRAIT).withWindowOrientation(WindowOrientation.LANDSCAPE),f.permit));
            }
        }
    }
    @Test public void generalWriterCannotChangeOrDropExistingDirection()throws Exception{
        try(BrowserMetadataStoreTest.F f=new BrowserMetadataStoreTest.F()){
            f.seedSettings(false);
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){f.settings.compareAndWriteWindowOrientation(v,v.value(f.permit).withWindowOrientation(WindowOrientation.LANDSCAPE),f.permit);}
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){
                Snapshot base=v.value(f.permit);Snapshot drop=new Snapshot(base.metadataRevision()+1,base.searchProvider(),false,false,base.newTabBackground(),base.newTabRecentEnabled(),base.homepage());
                assertThrows(IllegalArgumentException.class,()->f.settings.compareAndWriteGeneral(v,drop,f.permit));
                assertThrows(IllegalArgumentException.class,()->f.settings.compareAndWriteGeneral(v,base.withWindowOrientation(WindowOrientation.FOLLOW_SYSTEM),f.permit));
                f.settings.compareAndWriteGeneral(v,base.withGeneral(SearchEngines.Provider.BAIDU,"about:blank",SettingsRepository.NewTabBackground.WHITE,false),f.permit);
            }
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){assertEquals(WindowOrientation.LANDSCAPE,v.value(f.permit).windowOrientation());assertEquals("about:blank",v.value(f.permit).homepage());}
        }
    }
    @Test public void missingRecordCannotBeSeededByOrientationWriter()throws Exception{
        try(BrowserMetadataStoreTest.F f=new BrowserMetadataStoreTest.F();OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){
            assertEquals(PrivateRecordStore.Kind.MISSING,v.kind());assertThrows(IllegalArgumentException.class,()->f.settings.compareAndWriteWindowOrientation(v,legacy(0).withWindowOrientation(WindowOrientation.PORTRAIT),f.permit));
            assertFalse(Files.exists(f.root.resolve(f.ref(OwnedDataCatalog.Family.SETTINGS).opaqueRef())));
        }
    }
}
