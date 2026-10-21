package io.github.rebrowser.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.EnumSet;

/**
 * Native READ-ONLY site-permission page, grouped by the supplied site identity (partition+host)
 * and capability. A change request captures the exact record token+revision; after an attempt the
 * row is locally marked "waiting for a fresh read" rather than revoked, and a rebind never carries
 * that pending mark into a new page. Expiry text is only the supplied observation; this window
 * never prompts the OS, grants/revokes Gecko permission or merges different scopes.
 */
public final class SitePermissionsActivity extends Activity {
    public static final int MAX_ROWS = 100;
    public static final int MAX_HOST_CHARS = 255;
    public enum Expiry { UNKNOWN, NONE, TIMED, EXPIRED }
    public enum OsState { UNKNOWN, GRANTED, DENIED, NOT_REQUIRED }
    public enum Capability { GEOLOCATION, CAMERA, MICROPHONE, NOTIFICATIONS, PERSISTENT_STORAGE, MEDIA_KEY_SYSTEM, OTHER }
    public enum RequestedChange { GRANT, REVOKE, RESET }

    public static final class Row {
        private final Object token,siteScopeIdentity;private final String hostLabel,partitionLabel;private final Capability capability;
        private final Expiry expiry;private final long expiresInMs;private final OsState osState;
        private final Set<RequestedChange> nativeOffered;
        public Row(Object token,String hostLabel,String partitionLabel,Capability capability,Expiry expiry,long expiresInMs,OsState osState){
            // Absence of a native action offer never enables any permission change.
            this(token,hostLabel,partitionLabel,capability,expiry,expiresInMs,osState,
                    Collections.<RequestedChange>emptySet());
        }
        public Row(Object token,String hostLabel,String partitionLabel,Capability capability,Expiry expiry,long expiresInMs,OsState osState,boolean nativeRevokeOffered){
            this(token,hostLabel,partitionLabel,capability,expiry,expiresInMs,osState,
                    nativeRevokeOffered?EnumSet.of(RequestedChange.REVOKE):Collections.<RequestedChange>emptySet());
        }
        /** Only the exact native owner supplies an explicit per-action offer. */
        public Row(Object token,String hostLabel,String partitionLabel,Capability capability,Expiry expiry,long expiresInMs,
                   OsState osState,Set<RequestedChange> nativeOffered){this(token,hostLabel,partitionLabel,capability,expiry,expiresInMs,osState,nativeOffered,null);}
        public Row(Object token,String hostLabel,String partitionLabel,Capability capability,Expiry expiry,long expiresInMs,OsState osState,Set<RequestedChange> nativeOffered,Object nativeSiteScopeIdentity){
            if(token==null || capability==null || expiry==null || osState==null || nativeOffered==null)
                throw new IllegalArgumentException("Incomplete permission row");
            EnumSet<RequestedChange> offered=EnumSet.noneOf(RequestedChange.class);
            for(RequestedChange change:nativeOffered){
                if(change==null)throw new IllegalArgumentException("Null permission action offer");
                offered.add(change);
            }
            this.nativeOffered=Collections.unmodifiableSet(offered);
            this.token=token;this.siteScopeIdentity=nativeSiteScopeIdentity==null?token:nativeSiteScopeIdentity;this.hostLabel=host(hostLabel);this.partitionLabel=host(partitionLabel);
            if(expiry!=Expiry.TIMED)this.expiresInMs=expressUnknown(expiresInMs);
            else{if(expiresInMs<0 || expiresInMs>365L*24*60*60*1000)throw new IllegalArgumentException("Invalid expiry");this.expiresInMs=expiresInMs;}
            this.capability=capability;this.expiry=expiry;this.osState=osState;
        }
        public Object token(){return token;}
        /** Native grouping identity only, NEVER an authorization grant. Unknown stays per-row. */
        public Object siteScopeIdentity(){return siteScopeIdentity;}
        public String hostLabel(){return hostLabel;}
        public String partitionLabel(){return partitionLabel;}
        public Capability capability(){return capability;}
        public Expiry expiry(){return expiry;}
        public long expiresInMs(){return expiresInMs;}
        public OsState osState(){return osState;}
        public boolean nativeRevokeOffered(){return nativeOffers(RequestedChange.REVOKE);}
        public boolean nativeOffers(RequestedChange change){
            if(change==null)throw new IllegalArgumentException("Missing action");
            return nativeOffered.contains(change);
        }
        public Set<RequestedChange> offeredActions(){return nativeOffered;}
        public String label(){return hostLabel+" · "+capabilityLabel(capability)+" · "+expiryLabel(expiry,expiresInMs)+" · "+osLabel(osState);}
    }
    public interface Actions {
        void requestChange(Row row,RequestedChange change,long expectedRevision);
    }
    public static final class Page {
        private final List<Row> rows;private final long revision;
        public Page(List<Row> rows,long revision){
            if(rows==null || rows.size()>MAX_ROWS || revision<1)throw new IllegalArgumentException("Invalid permission page");
            Set<Object> tokens=Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            List<Row> copy=new ArrayList<>();
            for(Row row:rows){if(row==null)throw new IllegalArgumentException("Missing permission row");if(!tokens.add(row.token()))throw new IllegalArgumentException("Duplicate permission token");copy.add(row);}
            this.rows=Collections.unmodifiableList(copy);this.revision=revision;
        }
        public List<Row> rows(){return rows;}
        public long revision(){return revision;}
    }
    /** Stable site group: exact partition+host identity, never merged by host name alone. */
    public static final class Group {
        private final String partitionLabel,hostLabel;private final List<Row> rows;
        private Group(String partitionLabel,String hostLabel,List<Row> rows){this.partitionLabel=partitionLabel;this.hostLabel=hostLabel;this.rows=Collections.unmodifiableList(rows);}
        public String partitionLabel(){return partitionLabel;}
        public String hostLabel(){return hostLabel;}
        public List<Row> rows(){return rows;}
        public String label(){return hostLabel+"（" + partitionLabel + "）";}
    }
    public static final class Groups {
        /** First-appearance group order; capabilities ordered by kind then original position. */
        public static List<Group> of(Page page){
            if(page==null)throw new IllegalArgumentException("Missing permission page");
            java.util.Map<Object,List<Row>> byScope=new java.util.IdentityHashMap<>();List<Object> order=new ArrayList<>();
            for(Row row:page.rows()){List<Row> group=byScope.get(row.siteScopeIdentity());if(group==null){group=new ArrayList<>();byScope.put(row.siteScopeIdentity(),group);order.add(row.siteScopeIdentity());}else if(!group.get(0).partitionLabel().equals(row.partitionLabel()) || !group.get(0).hostLabel().equals(row.hostLabel()))throw new IllegalArgumentException("Conflicting native site display identity");group.add(row);}
            List<Group> groups=new ArrayList<>();
            for(Object scope:order){List<Row> rows=new ArrayList<>(byScope.get(scope));java.util.Collections.sort(rows,new java.util.Comparator<Row>(){public int compare(Row a,Row b){return Integer.compare(a.capability().ordinal(),b.capability().ordinal());}});Row first=rows.get(0);groups.add(new Group(first.partitionLabel(),first.hostLabel(),rows));}
            return Collections.unmodifiableList(groups);
        }
    }

    private LinearLayout content;
    private TextView status;
    private BrowserToolbar.Binding binding;private Object frame;
    private Actions actions;private Page page;
    private final Set<AttemptKey> pending=new HashSet<>();

    private static final class AttemptKey {
        final long revision;final Object token;final RequestedChange change;
        AttemptKey(long revision,Object token,RequestedChange change){this.revision=revision;this.token=token;this.change=change;}
        @Override public boolean equals(Object other){if(!(other instanceof AttemptKey))return false;AttemptKey key=(AttemptKey)other;return revision==key.revision && token==key.token && change==key.change;}
        @Override public int hashCode(){return Long.hashCode(revision)*31+change.hashCode();}
    }

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        setContentView(scroll);
        status=new TextView(this);
        status.setText("网站权限未读取；不显示其他范围站点");
        content.addView(status);
    }

    public void bind(BrowserToolbar.Binding binding,Page page,Actions actions){
        BrowserToolbar.main();
        if(binding==null || page==null || actions==null)throw new IllegalArgumentException("Missing native permission binding");
        binding.check();
        this.binding=binding;this.page=page;this.actions=actions;this.frame=new Object();
        pending.clear(); // a new native page owns a new pending domain
        status.setText(page.rows().isEmpty()?"没有已提供的网站权限记录":"按站点与能力分组；更改仅提交请求");
        render();
    }

    private void render(){
        CharSequence message=status.getText();BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();status.setText(message);
        content.addView(status);
        if(page==null || binding==null)return;
        final Object expectedFrame=frame;final Page originalPage=page;final Actions originalActions=actions;
        for(Group group:Groups.of(page)){
            TextView header=new TextView(this);
            header.setText(group.label());
            header.setTextSize(16);
            header.setMinimumHeight(dp(48));
            content.addView(header);
            for(Row row:group.rows()){
                TextView label=new TextView(this);
                label.setText("  " + capabilityLabel(row.capability()) + " · " + expiryLabel(row.expiry(),row.expiresInMs()) + " · " + osLabel(row.osState()));
                content.addView(label);
                LinearLayout line=new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                for(RequestedChange change:RequestedChange.values()){
                    boolean offered=row.nativeOffers(change);
                    boolean attempted=rowPending(row.token(),page.revision());
                    boolean enabled=offered && !attempted && binding!=null;
                    Button button=new Button(this);
                    button.setText(change==RequestedChange.GRANT?"请求允许":change==RequestedChange.REVOKE?"请求撤回":"请求重置");
                    button.setMinimumHeight(dp(48));
                    button.setEnabled(enabled);
                    button.setContentDescription(enabled?button.getText()+"：" + row.label()
                            :attempted?"已请求，等待原生重新读取：" + row.label():"该操作未由原生提供：" + row.label());
                    button.setOnClickListener(enabled?v->request(expectedFrame,originalPage,originalActions,row,change):null);
                    line.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));
                }
                content.addView(line);
            }
        }
    }

    private boolean rowPending(Object token,long revision){for(AttemptKey key:pending)if(key.token==token && key.revision==revision)return true;return false;}
    private void request(Object expected,Page currentPage,Actions owner,Row row,RequestedChange change){
        BrowserToolbar.Binding captured=binding;
        if(captured==null || owner==null || owner!=actions || currentPage==null || page!=currentPage || frame!=expected || !row.nativeOffers(change) || rowPending(row.token(),currentPage.revision()))return;
        boolean exact=false;for(Row candidate:currentPage.rows())if(candidate==row){exact=true;break;}if(!exact)return;
        try{captured.check();}catch(RuntimeException stale){return;}if(frame!=expected || binding!=captured || page!=currentPage || actions!=owner)return;
        AttemptKey key=new AttemptKey(currentPage.revision(),row.token(),change);
        if(!pending.add(key))return; // consumed before the callback; unknown stays pending
        render();
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(frame!=expected || captured!=binding || page!=currentPage)return;
            owner.requestChange(row,change,currentPage.revision());
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)
            status.setText("权限请求结果未知，请重新读取；不显示为已撤回");
    }

    public void unbind(){BrowserToolbar.main();binding=null;frame=null;actions=null;page=null;pending.clear();BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();content.addView(status);status.setText("网站权限未读取；不显示其他范围站点");}

    @Override protected void onDestroy(){binding=null;frame=null;actions=null;page=null;pending.clear();BrowserToolbar.clearOwnedViewTree(content);super.onDestroy();}
    public static String capabilityLabel(Capability capability){
        if(capability==null)throw new IllegalArgumentException("Missing capability");
        switch(capability){case GEOLOCATION:return "位置";case CAMERA:return "摄像头";case MICROPHONE:return "麦克风";case NOTIFICATIONS:return "通知";case PERSISTENT_STORAGE:return "持久存储";case MEDIA_KEY_SYSTEM:return "媒体密钥系统";default:return "其他能力";}
    }
    public static String expiryLabel(Expiry expiry,long expiresInMs){
        if(expiry==null)throw new IllegalArgumentException("Missing expiry");
        switch(expiry){case NONE:return "无期限授权（报告）";case TIMED:return "剩余约" + Math.max(0,expiresInMs/60_000) + "分钟（报告）";case EXPIRED:return "已过期（报告，不代表传感器已停）";default:return "期限未知";}
    }
    public static String osLabel(OsState state){
        if(state==null)throw new IllegalArgumentException("Missing OS state");
        switch(state){case GRANTED:return "系统已允许";case DENIED:return "系统已拒绝";case NOT_REQUIRED:return "无需系统许可";default:return "系统状态未知";}
    }
    private static long expressUnknown(long value){if(value!=0)throw new IllegalArgumentException("Expiry value requires TIMED state");return 0;}
    static String host(String value){
        if(value==null || value.isEmpty() || value.length()>MAX_HOST_CHARS)throw new IllegalArgumentException("Invalid host label");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c<0x21 || c>0x7e)throw new IllegalArgumentException("Host label must be printable ASCII");}
        return value.toLowerCase(java.util.Locale.ROOT);
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
