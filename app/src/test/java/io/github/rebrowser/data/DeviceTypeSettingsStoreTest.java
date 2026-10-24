package io.github.rebrowser.data;

import io.github.rebrowser.browser.SearchEngines;
import io.github.rebrowser.settings.SettingsRepository;
import io.github.rebrowser.settings.SettingsRepository.Snapshot;
import io.github.rebrowser.settings.SettingsRepository.DeviceType;
import io.github.rebrowser.settings.SettingsRepository.WindowOrientation;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real codec/encrypted HOST filesystem/CAS with synthetic source/key, never Android hardware,
 * UI/rotation/classification or a source/administrator/enrollment authority test. */
public final class DeviceTypeSettingsStoreTest {
    private static Snapshot old(long revision){return new Snapshot(revision,SearchEngines.Provider.BING,false,false);}
    @Test public void bothLegacySchemasRemainByteIdenticalUntilExplicitDeviceIntent(){
        for(Snapshot legacy:new Snapshot[]{old(0),old(0).withWindowOrientation(WindowOrientation.LANDSCAPE)}){
            byte[] wire=SettingsRepository.CODEC.encode(legacy);Snapshot read=SettingsRepository.CODEC.decode(ByteBuffer.wrap(wire));
            assertNull(read.deviceType());assertArrayEquals(wire,SettingsRepository.CODEC.encode(read));
            assertEquals(wire[7],SettingsRepository.CODEC.encode(read.withGeneral(read.searchProvider(),read.homepage(),read.newTabBackground(),read.newTabRecentEnabled()))[7]);
        }
    }
    @Test public void explicit05DeviceModesPreserveOptionalDirectionAndRoundTrip(){
        for(DeviceType type:DeviceType.values())for(Snapshot base:new Snapshot[]{old(0),old(0).withWindowOrientation(WindowOrientation.PORTRAIT)}){
            Snapshot intended=base.withDeviceType(type);byte[] wire=SettingsRepository.CODEC.encode(intended);assertEquals('5',wire[7]);
            Snapshot read=SettingsRepository.CODEC.decode(ByteBuffer.wrap(wire));assertEquals(type,read.deviceType());assertEquals(base.windowOrientation(),read.windowOrientation());
            assertArrayEquals(wire,SettingsRepository.CODEC.encode(read));assertEquals(base.homepage(),read.homepage());
        }
        assertThrows(IllegalArgumentException.class,()->old(0).withDeviceType(null));
    }
    @Test public void allExistingTransformsKeepDeviceCorrectionAndNewFormat(){
        Snapshot base=old(0).withDeviceType(DeviceType.PHONE),after=base.withSearch(SearchEngines.Provider.BAIDU).withEnabled("music",true)
                .withNewTab(SettingsRepository.NewTabBackground.WHITE,false).withHomepage("about:blank").withWindowOrientation(WindowOrientation.LANDSCAPE)
                .withGeneral(SearchEngines.Provider.BING,"https://cn.bing.com/",SettingsRepository.NewTabBackground.EXCLUSIVE,true);
        assertEquals(DeviceType.PHONE,after.deviceType());assertEquals(WindowOrientation.LANDSCAPE,after.windowOrientation());assertEquals('5',SettingsRepository.CODEC.encode(after)[7]);
        assertThrows(IllegalArgumentException.class,()->base.withGeneralAndDeviceType(base.searchProvider(),base.homepage(),base.newTabBackground(),base.newTabRecentEnabled(),null));
    }
    @Test public void malformed05NeverDefaultsToAutoOrDowngrades(){
        byte[] valid=SettingsRepository.CODEC.encode(old(0).withDeviceType(DeviceType.AUTO));
        byte[] badFlag=valid.clone();int marker=valid.length-(2+4)-1;badFlag[marker]=2;
        assertThrows(IllegalArgumentException.class,()->SettingsRepository.CODEC.decode(ByteBuffer.wrap(badFlag)));
        byte[] badType=valid.clone();badType[badType.length-1]='X';assertThrows(IllegalArgumentException.class,()->SettingsRepository.CODEC.decode(ByteBuffer.wrap(badType)));
        byte[] extra=java.util.Arrays.copyOf(valid,valid.length+1);assertThrows(IllegalArgumentException.class,()->SettingsRepository.CODEC.decode(ByteBuffer.wrap(extra)));
        assertThrows(IllegalArgumentException.class,()->SettingsRepository.CODEC.decode(ByteBuffer.wrap(java.util.Arrays.copyOf(valid,valid.length-1))));
    }
    @Test public void encryptedLegacyReadDoesNotMigrateAndActualGeneralSaveThenDirectionKeepsBoth()throws Exception{
        try(BrowserMetadataStoreTest.F f=new BrowserMetadataStoreTest.F()){
            f.seedSettings(false);
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){f.settings.compareAndWriteWindowOrientation(v,v.value(f.permit).withWindowOrientation(WindowOrientation.LANDSCAPE),f.permit);}
            java.nio.file.Path path=f.root.resolve(f.ref(OwnedDataCatalog.Family.SETTINGS).opaqueRef());byte[] before=Files.readAllBytes(path);
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){assertNull(v.value(f.permit).deviceType());}
            assertArrayEquals(before,Files.readAllBytes(path));long written;
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){written=f.settings.compareAndWriteGeneral(v,v.value(f.permit).withDeviceType(DeviceType.TABLET),f.permit);}
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){assertEquals(written,v.recordRevision());assertEquals(DeviceType.TABLET,v.value(f.permit).deviceType());assertEquals(WindowOrientation.LANDSCAPE,v.value(f.permit).windowOrientation());f.settings.compareAndWriteWindowOrientation(v,v.value(f.permit).withWindowOrientation(WindowOrientation.PORTRAIT),f.permit);}
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){assertEquals(DeviceType.TABLET,v.value(f.permit).deviceType());assertEquals(WindowOrientation.PORTRAIT,v.value(f.permit).windowOrientation());}
        }
    }
    @Test public void directionWriterCannotSmuggleDeviceAndGeneralCannotDropIt()throws Exception{
        try(BrowserMetadataStoreTest.F f=new BrowserMetadataStoreTest.F()){
            f.seedSettings(false);
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){f.settings.compareAndWriteWindowOrientation(v,v.value(f.permit).withWindowOrientation(WindowOrientation.PORTRAIT),f.permit);}
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){assertThrows(IllegalArgumentException.class,()->f.settings.compareAndWriteWindowOrientation(v,v.value(f.permit).withDeviceType(DeviceType.TABLET),f.permit));f.settings.compareAndWriteGeneral(v,v.value(f.permit).withDeviceType(DeviceType.PHONE),f.permit);}
            try(OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){
                Snapshot base=v.value(f.permit);Snapshot drop=new Snapshot(base.metadataRevision(),base.searchProvider(),false,false,base.newTabBackground(),base.newTabRecentEnabled(),base.homepage()).withWindowOrientation(base.windowOrientation());
                assertEquals(base.metadataRevision()+1,drop.metadataRevision());assertEquals(base.windowOrientation(),drop.windowOrientation());assertNull(drop.deviceType());
                assertThrows(IllegalArgumentException.class,()->f.settings.compareAndWriteGeneral(v,drop,f.permit));
            }
        }
    }
    @Test public void missingSettingsAreNotBornFromDeviceChoice()throws Exception{
        try(BrowserMetadataStoreTest.F f=new BrowserMetadataStoreTest.F();OwnedDataCatalog.View<Snapshot> v=f.settings.read(f.permit)){
            assertEquals(PrivateRecordStore.Kind.MISSING,v.kind());assertThrows(IllegalArgumentException.class,()->f.settings.compareAndWriteGeneral(v,old(0).withDeviceType(DeviceType.PHONE),f.permit));
            assertFalse(Files.exists(f.root.resolve(f.ref(OwnedDataCatalog.Family.SETTINGS).opaqueRef())));
        }
    }
}
