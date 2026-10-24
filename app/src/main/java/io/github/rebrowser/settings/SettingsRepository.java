package io.github.rebrowser.settings;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.browser.SearchEngines;
import io.github.rebrowser.data.NativeRecordCodec;
import io.github.rebrowser.data.OwnedDataCatalog;
import io.github.rebrowser.data.PrivateRecordStore;
import io.github.rebrowser.subapps.SubapplicationRegistry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

/** Canonical existing native settings. RBSETT03 remains byte-identical until an explicit native
 * direction intent writes RBSETT04; an explicit device-choice edit writes RBSETT05. No read
 * migration/default seeding or repair. Device choice is presentation, never trust/identity. Desired
 * enablement does not mean running; a direction preference is not actual rotation completion. */
public final class SettingsRepository {
    public static final int MAX_BYTES=64*1024;
    public enum NewTabBackground { WHITE, EXCLUSIVE }
    public enum WindowOrientation { PORTRAIT, LANDSCAPE, FOLLOW_SYSTEM }
    public enum DeviceType { AUTO, PHONE, TABLET }
    public static final class Snapshot {
        private final long revision;private final SearchEngines.Provider search;
        private final boolean outlook,music,recent;private final NewTabBackground background;
        private final String homepage;private final WindowOrientation orientation;private final DeviceType deviceType;
        public Snapshot(long revision,SearchEngines.Provider search,boolean outlook,boolean music){this(revision,search,outlook,music,NewTabBackground.EXCLUSIVE,true);}
        public Snapshot(long revision,SearchEngines.Provider search,boolean outlook,boolean music,NewTabBackground background,boolean recent){this(revision,search,outlook,music,background,recent,SearchEngines.defaultHome().url());}
        public Snapshot(long revision,SearchEngines.Provider search,boolean outlook,boolean music,NewTabBackground background,boolean recent,String homepage){this(revision,search,outlook,music,background,recent,homepage,null);}
        private Snapshot(long revision,SearchEngines.Provider search,boolean outlook,boolean music,NewTabBackground background,boolean recent,String homepage,WindowOrientation orientation){this(revision,search,outlook,music,background,recent,homepage,orientation,null);}
        private Snapshot(long revision,SearchEngines.Provider search,boolean outlook,boolean music,NewTabBackground background,boolean recent,String homepage,WindowOrientation orientation,DeviceType deviceType){
            this.deviceType=deviceType;if(revision<0 || search==null || background==null)throw NativeRecordCodec.bad();
            this.revision=revision;this.search=search;this.outlook=outlook;this.music=music;this.background=background;this.recent=recent;this.orientation=orientation;
            NativeRecordCodec.validateText(homepage,8192,false);
            this.homepage=NativeRecordCodec.validateText("about:blank".equals(homepage)?homepage:SearchEngines.requireWebUrl(homepage).url(),8192,false);
        }
        public long metadataRevision(){return revision;}
        public SearchEngines.Provider searchProvider(){return search;}
        public String homepage(){return homepage;}
        public SearchEngines.Target homeTarget(){return "about:blank".equals(homepage)?SearchEngines.newTab():SearchEngines.requireWebUrl(homepage);}
        public NewTabBackground newTabBackground(){return background;}
        public boolean newTabRecentEnabled(){return recent;}
        /** Null is the unchanged legacy schema, NOT a stored setting or repair/default receipt. */
        public WindowOrientation windowOrientation(){return orientation;}
        /** Null is legacy implicit AUTO presentation, not stored evidence of physical identity. */
        public DeviceType deviceType(){return deviceType;}
        public boolean desiredEnabled(String id){SubapplicationRegistry.Descriptor d=SubapplicationRegistry.require(id);return d.route()==SubapplicationRegistry.Route.OUTLOOK_NATIVE_ENTRY?outlook:music;}
        public Snapshot withSearch(SearchEngines.Provider provider){return new Snapshot(next(revision),provider,outlook,music,background,recent,homepage,orientation,deviceType);}
        public Snapshot withEnabled(String id,boolean enabled){SubapplicationRegistry.Descriptor d=SubapplicationRegistry.require(id);return new Snapshot(next(revision),search,d.route()==SubapplicationRegistry.Route.OUTLOOK_NATIVE_ENTRY?enabled:outlook,d.route()==SubapplicationRegistry.Route.MUSIC_NATIVE_ENTRY?enabled:music,background,recent,homepage,orientation,deviceType);}
        public Snapshot withNewTab(NewTabBackground background,boolean recent){return new Snapshot(next(revision),search,outlook,music,background,recent,homepage,orientation,deviceType);}
        public Snapshot withHomepage(String homepage){return new Snapshot(next(revision),search,outlook,music,background,recent,homepage,orientation,deviceType);}
        public Snapshot withGeneral(SearchEngines.Provider search,String homepage,NewTabBackground background,boolean recent){return new Snapshot(next(revision),search,outlook,music,background,recent,homepage,orientation,deviceType);}
        public Snapshot withGeneralAndDeviceType(SearchEngines.Provider search,String homepage,NewTabBackground background,boolean recent,DeviceType type){if(deviceType!=null && type==null)throw NativeRecordCodec.bad();return new Snapshot(next(revision),search,outlook,music,background,recent,homepage,orientation,type);}
        public Snapshot withDeviceType(DeviceType type){if(type==null)throw NativeRecordCodec.bad();return new Snapshot(next(revision),search,outlook,music,background,recent,homepage,orientation,type);}
        public Snapshot withWindowOrientation(WindowOrientation mode){if(mode==null)throw NativeRecordCodec.bad();return new Snapshot(next(revision),search,outlook,music,background,recent,homepage,mode,deviceType);}
    }
    public static final NativeRecordCodec.Codec<Snapshot> CODEC=new NativeRecordCodec.Codec<Snapshot>(){
        public Snapshot decode(ByteBuffer buffer){
            if(buffer==null || buffer.remaining()<10)throw NativeRecordCodec.bad();
            byte version=buffer.get(buffer.position()+7);
            if(version!='3' && version!='4' && version!='5')throw NativeRecordCodec.bad();
            NativeRecordCodec.Reader r=new NativeRecordCodec.Reader(buffer,version=='3'?"RBSETT03":version=='4'?"RBSETT04":"RBSETT05",MAX_BYTES);
            long revision=r.nonnegative();SearchEngines.Provider search=SearchEngines.Provider.fromId(r.text(16,false));
            boolean outlook=r.bool(),music=r.bool();NewTabBackground background;WindowOrientation orientation=null;DeviceType device=null;
            try{background=NewTabBackground.valueOf(r.text(16,false));}catch(IllegalArgumentException invalid){throw NativeRecordCodec.bad();}
            boolean recent=r.bool();String home=r.text(8192,false);
            try{
                if(version=='4' || version=='5' && r.bool())orientation=WindowOrientation.valueOf(r.text(32,false));
                if(version=='5')device=DeviceType.valueOf(r.text(16,false));
            }catch(IllegalArgumentException invalid){throw NativeRecordCodec.bad();}
            r.end();Snapshot value=new Snapshot(revision,search,outlook,music,background,recent,home,orientation,device);
            if(!home.equals(value.homepage()))throw NativeRecordCodec.bad();return value;
        }
        public byte[] encode(Snapshot s){
            if(s==null)throw NativeRecordCodec.bad();
            NativeRecordCodec.Writer w=new NativeRecordCodec.Writer(s.deviceType!=null?"RBSETT05":s.orientation==null?"RBSETT03":"RBSETT04",MAX_BYTES)
                    .nonnegative(s.revision).text(s.search.id(),16,false).bool(s.outlook).bool(s.music)
                    .text(s.background.name(),16,false).bool(s.recent).text(s.homepage,8192,false);
            if(s.deviceType!=null)w.bool(s.orientation!=null);
            if(s.orientation!=null)w.text(s.orientation.name(),32,false);
            if(s.deviceType!=null)w.text(s.deviceType.name(),16,false);return w.finish();
        }
    };
    private final OwnedDataCatalog records;
    public SettingsRepository(OwnedDataCatalog records){if(records==null)throw NativeRecordCodec.bad();this.records=records;}
    public OwnedDataCatalog.View<Snapshot> read(ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return records.read(OwnedDataCatalog.Family.SETTINGS,CODEC,permit);}
    public long compareAndWrite(OwnedDataCatalog.View<Snapshot> expected,Snapshot intended,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{
        records.requireFamily(expected,OwnedDataCatalog.Family.SETTINGS,CODEC);if(intended==null || expected.kind()==PrivateRecordStore.Kind.CURRENT && intended.revision<=expected.value(permit).revision)throw NativeRecordCodec.bad();return records.compareAndWrite(expected,intended,permit);
    }
    /** General/device-choice intent cannot smuggle application enablement or direction changes. */
    public long compareAndWriteGeneral(OwnedDataCatalog.View<Snapshot> expected,Snapshot intended,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{
        records.requireFamily(expected,OwnedDataCatalog.Family.SETTINGS,CODEC);if(expected.kind()!=PrivateRecordStore.Kind.CURRENT || intended==null)throw NativeRecordCodec.bad();Snapshot before=expected.value(permit);
        if(intended.revision!=next(before.revision) || intended.outlook!=before.outlook || intended.music!=before.music || intended.orientation!=before.orientation || before.deviceType!=null && intended.deviceType==null)throw NativeRecordCodec.bad();return records.compareAndWrite(expected,intended,permit);
    }
    /** Explicit direction-only CAS; missing records remain unavailable, never born here. */
    public long compareAndWriteWindowOrientation(OwnedDataCatalog.View<Snapshot> expected,Snapshot intended,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{
        records.requireFamily(expected,OwnedDataCatalog.Family.SETTINGS,CODEC);if(expected.kind()!=PrivateRecordStore.Kind.CURRENT || intended==null)throw NativeRecordCodec.bad();Snapshot before=expected.value(permit);
        if(intended.revision!=next(before.revision) || intended.orientation==null || intended.deviceType!=before.deviceType || intended.search!=before.search
                || !intended.homepage.equals(before.homepage) || intended.background!=before.background || intended.recent!=before.recent
                || intended.outlook!=before.outlook || intended.music!=before.music)throw NativeRecordCodec.bad();
        return records.compareAndWrite(expected,intended,permit);
    }
    public void requireExact(OwnedDataCatalog.View<Snapshot> view,ComponentGate.BusinessPermit permit)throws IOException{records.requireFamily(view,OwnedDataCatalog.Family.SETTINGS,CODEC);records.requireExact(view,permit);}
    public void beginNativeHandoff(OwnedDataCatalog.View<Snapshot> view,ComponentGate.BusinessPermit permit)throws IOException{records.requireFamily(view,OwnedDataCatalog.Family.SETTINGS,CODEC);view.beginNativeHandoff(records,permit);}
    private static long next(long value){if(value==Long.MAX_VALUE)throw NativeRecordCodec.bad();return value+1;}
}
