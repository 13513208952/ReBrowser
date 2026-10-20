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

/**
 * Native READ-ONLY site-permission list: origin display, capability, native grant expiry and OS
 * state, with an explicit change INTENT. It never prompts the OS, never grants/revokes Gecko
 * permission and never treats a finished native timer as proof that sensors stopped. Only rows
 * provided by the owner are shown; it does not enumerate other scopes.
 */
public final class SitePermissionsActivity extends Activity {
    public static final int MAX_ROWS = 100;
    public static final int MAX_HOST_CHARS = 255;
    public enum Expiry { UNKNOWN, NONE, TIMED, EXPIRED }
    public enum OsState { UNKNOWN, GRANTED, DENIED, NOT_REQUIRED }
    public enum Capability { GEOLOCATION, CAMERA, MICROPHONE, NOTIFICATIONS, PERSISTENT_STORAGE, MEDIA_KEY_SYSTEM, OTHER }
    public enum RequestedChange { GRANT, REVOKE, RESET }

    public static final class Row {
        private final Object token;private final String hostLabel,partitionLabel;private final Capability capability;
        private final Expiry expiry;private final long expiresInMs;private final OsState osState;
        public Row(Object token,String hostLabel,String partitionLabel,Capability capability,Expiry expiry,long expiresInMs,OsState osState){
            if(token==null || capability==null || expiry==null || osState==null)throw new IllegalArgumentException("Incomplete permission row");
            this.token=token;this.hostLabel=host(hostLabel);this.partitionLabel=host(partitionLabel);
            if(expiry!=Expiry.TIMED)this.expiresInMs=expressUnknown(expiresInMs);
            else{if(expiresInMs<0 || expiresInMs>365L*24*60*60*1000)throw new IllegalArgumentException("Invalid expiry");this.expiresInMs=expiresInMs;}
            this.capability=capability;this.expiry=expiry;this.osState=osState;
        }
        public Object token(){return token;}
        public String hostLabel(){return hostLabel;}
        public String partitionLabel(){return partitionLabel;}
        public Capability capability(){return capability;}
        public Expiry expiry(){return expiry;}
        public long expiresInMs(){return expiresInMs;}
        public OsState osState(){return osState;}
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

    private LinearLayout content;
    private BrowserToolbar.Binding binding;private Object frame;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        setContentView(new ScrollView(this){{addView(content);}});
        TextView empty=new TextView(this);empty.setText("网站权限未读取；不显示其他范围站点");content.addView(empty);
    }

    public void bind(BrowserToolbar.Binding binding,Page page,Actions actions){
        BrowserToolbar.main();
        if(binding==null || page==null || actions==null)throw new IllegalArgumentException("Missing native permission binding");
        binding.check();
        this.binding=binding;
        BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();
        final Object current=new Object();frame=current;
        TextView header=new TextView(this);
        header.setText(page.rows().isEmpty()?"没有已提供的网站权限记录":"网站权限为只读投影；更改仅提交请求");
        content.addView(header);
        for(Row row:page.rows()){
            TextView label=new TextView(this);
            label.setText(row.label());
            label.setMinimumHeight(dp(48));
            content.addView(label,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
            LinearLayout actionsRow=new LinearLayout(this);
            actionsRow.setOrientation(LinearLayout.HORIZONTAL);
            for(RequestedChange change:RequestedChange.values()){
                Button button=new Button(this);
                button.setText(change==RequestedChange.GRANT?"请求允许":change==RequestedChange.REVOKE?"请求撤销":"请求重置");
                button.setMinimumHeight(dp(48));
                button.setEnabled(row.expiry()!=Expiry.UNKNOWN || row.osState()!=OsState.UNKNOWN);
                button.setContentDescription(button.getText()+"：" + row.label());
                button.setOnClickListener(v->dispatch(current,row,change,page.revision(),actions));
                actionsRow.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));
            }
            content.addView(actionsRow);
        }
    }
    public void unbind(){BrowserToolbar.main();binding=null;frame=null;BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();}

    private void dispatch(Object expected,Row row,RequestedChange change,long revision,Actions actions){
        BrowserToolbar.Binding captured=binding;
        if(captured==null || frame!=expected)return;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(frame!=expected || captured!=binding)return;
            actions.requestChange(row,change,revision);
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)setTitle("权限更改结果未知，请重新读取");
    }

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
    @Override protected void onDestroy(){binding=null;frame=null;BrowserToolbar.clearOwnedViewTree(content);super.onDestroy();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
