package io.github.rebrowser.system;

import android.content.pm.ApplicationInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

/** Fabricated metadata tests only, not a real resolver, signature, Android window or role test. */
public final class DefaultAppsHandlerTest {
    private static ResolveInfo candidate(String pkg,String name,int flags){
        ResolveInfo r=new ResolveInfo();r.activityInfo=new ActivityInfo();r.activityInfo.packageName=pkg;r.activityInfo.name=name;
        r.activityInfo.enabled=true;r.activityInfo.exported=true;r.activityInfo.applicationInfo=new ApplicationInfo();
        r.activityInfo.applicationInfo.enabled=true;r.activityInfo.applicationInfo.flags=flags;return r;
    }
    @Test public void onlyEnabledExportedSystemOrUpdatedSystemCandidates(){
        ResolveInfo r=candidate("system.settings","DefaultApps",ApplicationInfo.FLAG_SYSTEM);
        assertTrue(DefaultBrowserFlow.systemHandler(r));r.activityInfo.applicationInfo.flags=ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        assertTrue(DefaultBrowserFlow.systemHandler(r));r.activityInfo.applicationInfo.flags=0;assertFalse(DefaultBrowserFlow.systemHandler(r));
        r.activityInfo.applicationInfo.flags=ApplicationInfo.FLAG_SYSTEM;r.activityInfo.exported=false;assertFalse(DefaultBrowserFlow.systemHandler(r));
        r.activityInfo.exported=true;r.activityInfo.enabled=false;assertFalse(DefaultBrowserFlow.systemHandler(r));
        r.activityInfo.enabled=true;r.activityInfo.applicationInfo.enabled=false;assertFalse(DefaultBrowserFlow.systemHandler(r));
    }
    @Test public void incompleteMetadataNeverBecomesSystemTarget(){
        assertFalse(DefaultBrowserFlow.systemHandler(null));ResolveInfo r=new ResolveInfo();assertFalse(DefaultBrowserFlow.systemHandler(r));
        r=candidate("system.settings","DefaultApps",ApplicationInfo.FLAG_SYSTEM);r.activityInfo.name="";assertFalse(DefaultBrowserFlow.systemHandler(r));
        r.activityInfo.name="DefaultApps";r.activityInfo.packageName=null;assertFalse(DefaultBrowserFlow.systemHandler(r));
    }
    @Test public void syntheticPreferredOrThirdPartyIsNotLaunched(){
        ResolveInfo system=candidate("oem.settings","DefaultApps",ApplicationInfo.FLAG_SYSTEM);
        ResolveInfo resolver=candidate("android","SyntheticResolver",ApplicationInfo.FLAG_SYSTEM);
        ResolveInfo third=candidate("third.party","DefaultApps",0);
        assertSame(system,DefaultBrowserFlow.selectSystemHandler(resolver,Arrays.asList(third,system)));
        assertSame(system,DefaultBrowserFlow.selectSystemHandler(third,Arrays.asList(third,system)));
        assertThrows(IllegalStateException.class,()->DefaultBrowserFlow.selectSystemHandler(resolver,Collections.singletonList(third)));
    }
    @Test public void preferredMustMatchActualEligibleCandidateAndListsAreBounded(){
        ResolveInfo first=candidate("system.one","DefaultApps",ApplicationInfo.FLAG_SYSTEM),second=candidate("system.two","DefaultApps",ApplicationInfo.FLAG_SYSTEM);
        ResolveInfo preferred=candidate("system.two","DefaultApps",ApplicationInfo.FLAG_SYSTEM);
        assertSame(second,DefaultBrowserFlow.selectSystemHandler(preferred,Arrays.asList(first,second)));
        assertThrows(IllegalStateException.class,()->DefaultBrowserFlow.selectSystemHandler(preferred,null));
        assertThrows(IllegalStateException.class,()->DefaultBrowserFlow.selectSystemHandler(preferred,Collections.emptyList()));
        assertThrows(IllegalStateException.class,()->DefaultBrowserFlow.selectSystemHandler(preferred,Collections.nCopies(33,first)));
    }
}
