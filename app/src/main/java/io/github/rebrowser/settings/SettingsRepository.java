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

/** Native settings including approved new-tab presentation, the global search provider and
 * desired built-in enablement. RBSETT03 adds the main browser's actual homepage to the unshipped settings; it does not
 * guess/migrate old bytes. Complete three-page settings are still being restored.
 * Desired enabled does NOT mean opened/running/logged in; disabled does NOT mean actual stop.
 * Missing/unread/corrupt records do not default to false/Bing or automatically seed/overwrite.
 * Caller supplies an explicit complete intended snapshot after native domain authorization. */
public final class SettingsRepository {
    public static final int MAX_BYTES=64*1024;
    public enum NewTabBackground { WHITE, EXCLUSIVE }
    public static final class Snapshot {
        private final long revision;private final SearchEngines.Provider search;private final boolean outlook,music,recent;private final NewTabBackground background;private final String homepage;
        /** Explicit new complete intent uses approved full-background/recent defaults. Missing
         * persisted settings are still not treated as a new install or automatically seeded. */
        public Snapshot(long metadataRevision,SearchEngines.Provider search,boolean outlookEnabled,boolean musicEnabled){this(metadataRevision,search,outlookEnabled,musicEnabled,NewTabBackground.EXCLUSIVE,true);}
        public Snapshot(long metadataRevision,SearchEngines.Provider search,boolean outlookEnabled,boolean musicEnabled,NewTabBackground background,boolean showRecent){this(metadataRevision,search,outlookEnabled,musicEnabled,background,showRecent,SearchEngines.defaultHome().url());}
        public Snapshot(long metadataRevision,SearchEngines.Provider search,boolean outlookEnabled,boolean musicEnabled,NewTabBackground background,boolean showRecent,String homepage){if(metadataRevision<0 || search==null || background==null)throw NativeRecordCodec.bad();revision=metadataRevision;this.search=search;outlook=outlookEnabled;music=musicEnabled;this.background=background;recent=showRecent;NativeRecordCodec.validateText(homepage,8192,false);this.homepage=NativeRecordCodec.validateText("about:blank".equals(homepage)?homepage:SearchEngines.requireWebUrl(homepage).url(),8192,false);}
        public long metadataRevision(){return revision;}public SearchEngines.Provider searchProvider(){return search;}
        public String homepage(){return homepage;}public SearchEngines.Target homeTarget(){return "about:blank".equals(homepage)?SearchEngines.newTab():SearchEngines.requireWebUrl(homepage);}
        public NewTabBackground newTabBackground(){return background;}public boolean newTabRecentEnabled(){return recent;}
        public boolean desiredEnabled(String id){SubapplicationRegistry.Descriptor descriptor=SubapplicationRegistry.require(id);return descriptor.route()==SubapplicationRegistry.Route.OUTLOOK_NATIVE_ENTRY?outlook:music;}
        public Snapshot withSearch(SearchEngines.Provider provider){return new Snapshot(next(revision),provider,outlook,music,background,recent,homepage);}
        public Snapshot withEnabled(String id,boolean enabled){SubapplicationRegistry.Descriptor d=SubapplicationRegistry.require(id);return new Snapshot(next(revision),search,d.route()==SubapplicationRegistry.Route.OUTLOOK_NATIVE_ENTRY?enabled:outlook,d.route()==SubapplicationRegistry.Route.MUSIC_NATIVE_ENTRY?enabled:music,background,recent,homepage);}
        public Snapshot withNewTab(NewTabBackground background,boolean showRecent){return new Snapshot(next(revision),search,outlook,music,background,showRecent,homepage);}
        public Snapshot withHomepage(String homepage){return new Snapshot(next(revision),search,outlook,music,background,recent,homepage);}
    }
    public static final NativeRecordCodec.Codec<Snapshot> CODEC=new NativeRecordCodec.Codec<Snapshot>(){
        public Snapshot decode(ByteBuffer buffer){NativeRecordCodec.Reader r=new NativeRecordCodec.Reader(buffer,"RBSETT03",MAX_BYTES);long rev=r.nonnegative();SearchEngines.Provider search=SearchEngines.Provider.fromId(r.text(16,false));boolean outlook=r.bool(),music=r.bool();NewTabBackground background;try{background=NewTabBackground.valueOf(r.text(16,false));}catch(IllegalArgumentException invalid){throw NativeRecordCodec.bad();}boolean recent=r.bool();String home=r.text(8192,false);r.end();Snapshot value=new Snapshot(rev,search,outlook,music,background,recent,home);if(!home.equals(value.homepage()))throw NativeRecordCodec.bad();return value;}
        public byte[] encode(Snapshot s){if(s==null)throw NativeRecordCodec.bad();return new NativeRecordCodec.Writer("RBSETT03",MAX_BYTES).nonnegative(s.revision).text(s.search.id(),16,false).bool(s.outlook).bool(s.music).text(s.background.name(),16,false).bool(s.recent).text(s.homepage,8192,false).finish();}
    };
    private final OwnedDataCatalog records;public SettingsRepository(OwnedDataCatalog records){if(records==null)throw NativeRecordCodec.bad();this.records=records;}
    public OwnedDataCatalog.View<Snapshot> read(ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return records.read(OwnedDataCatalog.Family.SETTINGS,CODEC,permit);}
    public long compareAndWrite(OwnedDataCatalog.View<Snapshot> expected,Snapshot intended,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{
        records.requireFamily(expected,OwnedDataCatalog.Family.SETTINGS,CODEC);if(intended==null || expected.kind()==PrivateRecordStore.Kind.CURRENT && intended.revision<=expected.value(permit).revision)throw NativeRecordCodec.bad();return records.compareAndWrite(expected,intended,permit);
    }
    public void requireExact(OwnedDataCatalog.View<Snapshot> view,ComponentGate.BusinessPermit permit)throws IOException{records.requireFamily(view,OwnedDataCatalog.Family.SETTINGS,CODEC);records.requireExact(view,permit);}
    public void beginNativeHandoff(OwnedDataCatalog.View<Snapshot> view,ComponentGate.BusinessPermit permit)throws IOException{records.requireFamily(view,OwnedDataCatalog.Family.SETTINGS,CODEC);view.beginNativeHandoff(records,permit);}
    private static long next(long value){if(value==Long.MAX_VALUE)throw NativeRecordCodec.bad();return value+1;}
}
