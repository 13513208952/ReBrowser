package io.github.rebrowser.system;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.provider.Settings;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.ui.BrowserToolbar;
import java.util.List;

/** Own-UID role observation and explicit native-user handoff to a SYSTEM default-apps page.
 * Never assigns a role or trusts caller/Activity-result booleans. Request data is not authority. */
public final class DefaultBrowserFlow {
    public enum Role { UNKNOWN, HELD, NOT_HELD }
    public enum RequestKind { OPEN_SYSTEM_DEFAULT_APPS_SETTINGS }
    public enum LaunchObservation { CALL_RETURNED_NOT_ROLE_ASSIGNED, SUBMISSION_UNKNOWN }

    public static final class Observation {
        private final Role role;private final long revision;
        public Observation(Role role,long revision){
            if(role==null || revision<1)throw new IllegalArgumentException("Invalid role observation");
            this.role=role;this.revision=revision;
        }
        public Role role(){return role;}
        public long revision(){return revision;}
        public boolean roleHeld(){return role==Role.HELD;}
        public String label(){
            switch(role){case HELD:return "当前是默认浏览器";case NOT_HELD:return "当前不是默认浏览器";default:return "默认浏览器状态未知";}
        }
    }
    /** One attempt latch, not a source, native window, successful launch or role receipt. */
    public static final class Recommendation {
        private final DefaultBrowserFlow issuer;private final RequestKind kind;
        private final long expectedRevision;private boolean attempted;
        private Recommendation(DefaultBrowserFlow issuer,RequestKind kind,long expectedRevision){
            this.issuer=issuer;this.kind=kind;this.expectedRevision=expectedRevision;
        }
        public RequestKind kind(){return kind;}
        public long expectedRevision(){return expectedRevision;}
        public boolean dispatchAttempted(){issuer.check();return attempted;}
    }
    private final Thread owner=Thread.currentThread();
    private Observation current;private long revision;private boolean retired;
    public Observation observe(Boolean roleHeld){
        check();if(retired)throw new IllegalStateException("Retired default-browser flow");
        Role role=roleHeld==null?Role.UNKNOWN:roleHeld?Role.HELD:Role.NOT_HELD;
        if(revision==Long.MAX_VALUE)throw new IllegalStateException("Role revision exhausted");
        revision++;current=new Observation(role,revision);return current;
    }
    public Observation current(){check();if(current==null)throw new IllegalStateException("Role not observed");return current;}
    public Recommendation recommendation(RequestKind kind){
        check();Observation observed=current();
        if(kind==null)throw new IllegalArgumentException("Missing recommendation kind");
        return new Recommendation(this,kind,observed.revision());
    }
    /** Ordinary native observation of this caller UID only. Unavailable is not NOT_HELD. */
    public static Boolean observeOwnRole(Activity actualWindow){
        if(actualWindow==null || actualWindow.isDestroyed())return null;
        try{
            RoleManager roles=actualWindow.getSystemService(RoleManager.class);
            return roles==null || !roles.isRoleAvailable(RoleManager.ROLE_BROWSER)?null:
                    Boolean.valueOf(roles.isRoleHeld(RoleManager.ROLE_BROWSER));
        }catch(RuntimeException unavailable){return null;}
    }
    /** Native click only; request data alone cannot call through the separate window/source guards.
     * Spend before launch; unknown submission never selects a fallback or reuses this request. */
    public LaunchObservation openSystemDefaultApps(Activity actualWindow,BrowserToolbar.Binding original,
            ComponentGate.DataEpochPin source,Recommendation request){
        check();checkRequest(request);
        if(actualWindow==null || original==null || source==null || actualWindow.isFinishing()
                || actualWindow.isDestroyed() || !actualWindow.hasWindowFocus())
            throw new SecurityException("Original default-app settings window unavailable");
        original.check();source.requireCurrent();
        Intent explicit=resolveSystemPage(actualWindow.getPackageManager());
        original.check();source.requireCurrent();
        if(actualWindow.isFinishing() || actualWindow.isDestroyed() || !actualWindow.hasWindowFocus())
            throw new SecurityException("Original default-app settings window lost focus");
        claimAttempt(request);
        try{actualWindow.startActivity(explicit);return LaunchObservation.CALL_RETURNED_NOT_ROLE_ASSIGNED;}
        catch(RuntimeException unknown){return LaunchObservation.SUBMISSION_UNKNOWN;}
    }
    private void checkRequest(Recommendation request){
        if(retired || request==null || request.issuer!=this || request.attempted
                || request.expectedRevision!=current().revision())
            throw new SecurityException("Original default-app settings request unavailable");
    }
    /** Package model seam; spending this latch does NOT bypass native checks or assign a role. */
    void claimAttempt(Recommendation request){check();checkRequest(request);request.attempted=true;}
    private static Intent resolveSystemPage(PackageManager packages){
        Intent fixed=new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
        int flags=PackageManager.MATCH_DEFAULT_ONLY|PackageManager.MATCH_SYSTEM_ONLY;
        ResolveInfo selected=selectSystemHandler(packages.resolveActivity(fixed,flags),
                packages.queryIntentActivities(fixed,flags));
        // Keep the system-owned page out of the browser's native Back/task stack.
        return new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).setComponent(
                new ComponentName(selected.activityInfo.packageName,selected.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    /** Preferred synthetic resolver/third-party chooser is not a candidate. Actual caller uses
     * only the fixed action's fresh PackageManager result, never caller-supplied ResolveInfo. */
    static ResolveInfo selectSystemHandler(ResolveInfo preferred,List<ResolveInfo> candidates){
        if(candidates==null || candidates.size()>32)throw new IllegalStateException("System default-app page unavailable");
        ResolveInfo selected=null;
        for(ResolveInfo candidate:candidates)if(systemHandler(candidate)){
            if(selected==null)selected=candidate;
            if(systemHandler(preferred) && candidate.activityInfo.packageName.equals(preferred.activityInfo.packageName)
                    && candidate.activityInfo.name.equals(preferred.activityInfo.name)){selected=candidate;break;}
        }
        if(selected==null)throw new IllegalStateException("System default-app page unavailable");
        return selected;
    }
    static boolean systemHandler(ResolveInfo info){
        return info!=null && info.activityInfo!=null && info.activityInfo.exported && info.activityInfo.enabled
                && info.activityInfo.applicationInfo!=null && info.activityInfo.applicationInfo.enabled
                && (info.activityInfo.applicationInfo.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0
                && info.activityInfo.packageName!=null && !info.activityInfo.packageName.isEmpty()
                && info.activityInfo.name!=null && !info.activityInfo.name.isEmpty();
    }
    public void retire(){check();retired=true;current=null;}
    private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Default-browser owner thread required");}
}
