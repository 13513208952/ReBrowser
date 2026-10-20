package io.github.rebrowser.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import io.github.rebrowser.subapps.SubapplicationRegistry;
import java.util.List;
import java.util.Map;

/**
 * Native built-in subapplication list. The directory is static; enablement is a READ-ONLY
 * projection that may be UNKNOWN. Opening, enabling/disabling and the 48dp settings gear only
 * emit exact user INTENT; this window never creates/stops a session or service, writes enablement
 * or performs occupancy/cache/data cleanup.
 */
public final class ApplicationsActivity extends Activity {
    public enum Enablement { UNKNOWN, REPORTED_ENABLED, REPORTED_DISABLED }
    public interface Actions {
        void open(SubapplicationRegistry.Descriptor descriptor);
        void requestToggle(SubapplicationRegistry.Descriptor descriptor,boolean currentlyEnabled);
        void settings(SubapplicationRegistry.Descriptor descriptor);
    }
    public static final class Projection {
        private final Map<String,Enablement> states;private final long revision;
        public Projection(Map<String,Enablement> states,long revision){
            if(states==null || states.size()>SubapplicationRegistry.all().size() || revision<1)throw new IllegalArgumentException("Invalid enablement projection");
            for(Map.Entry<String,Enablement> entry:states.entrySet()){
                SubapplicationRegistry.require(entry.getKey());
                if(entry.getValue()==null)throw new IllegalArgumentException("Unknown state must be explicit");
            }
            this.states=java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(states));this.revision=revision;
        }
        public Enablement state(SubapplicationRegistry.Descriptor descriptor){if(descriptor==null)throw new IllegalArgumentException("Missing descriptor");Enablement state=states.get(descriptor.localId());return state==null?Enablement.UNKNOWN:state;}
        public long revision(){return revision;}
        public static String label(Enablement state){
            if(state==null)throw new IllegalArgumentException("Missing enablement");
            switch(state){case REPORTED_ENABLED:return "已启用（报告）";case REPORTED_DISABLED:return "已停用（报告）";default:return "启用状态未知";}
        }
    }

    private LinearLayout content;
    private BrowserToolbar.Binding binding;private Object frame;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        setContentView(new ScrollView(this){{addView(content);}});
        render(null,null);
    }

    public void bind(BrowserToolbar.Binding binding,Projection projection,Actions actions){
        BrowserToolbar.main();
        if(binding==null || actions==null)throw new IllegalArgumentException("Missing native applications binding");
        binding.check();
        this.binding=binding;
        render(projection,actions);
    }
    public void unbind(){BrowserToolbar.main();binding=null;frame=null;render(null,null);}

    private void render(Projection projection,Actions actions){
        BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();
        final Object current=new Object();frame=current;
        TextView status=new TextView(this);
        status.setText("固定内建子应用；状态为原生只读投影，操作仅提交意图");
        content.addView(status);
        List<SubapplicationRegistry.Descriptor> all=SubapplicationRegistry.all();
        for(SubapplicationRegistry.Descriptor descriptor:all){
            Enablement state=projection==null?Enablement.UNKNOWN:projection.state(descriptor);
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView label=new TextView(this);
            label.setText(descriptor.displayName()+" · "+Projection.label(state));
            label.setTextSize(16);
            label.setMinimumHeight(dp(48));
            row.addView(label,new LinearLayout.LayoutParams(0,dp(48),1));
            Button toggle=new Button(this);
            toggle.setText(state==Enablement.REPORTED_ENABLED?"停用":"启用");
            toggle.setMinimumHeight(dp(48));
            toggle.setEnabled(actions!=null && state!=Enablement.UNKNOWN);
            toggle.setContentDescription((state==Enablement.REPORTED_ENABLED?"请求停用：":"请求启用：")+descriptor.displayName());
            toggle.setOnClickListener(v->dispatch(current,()->actions.requestToggle(descriptor,state==Enablement.REPORTED_ENABLED)));
            row.addView(toggle,new LinearLayout.LayoutParams(dp(88),dp(48)));
            Button open=new Button(this);
            open.setText("打开");
            open.setMinimumHeight(dp(48));
            open.setEnabled(actions!=null);
            open.setContentDescription("打开：" + descriptor.displayName());
            open.setOnClickListener(v->dispatch(current,()->actions.open(descriptor)));
            row.addView(open,new LinearLayout.LayoutParams(dp(88),dp(48)));
            Button gear=new Button(this);
            gear.setText("设置");
            gear.setMinimumWidth(dp(48));gear.setMinimumHeight(dp(48));
            gear.setContentDescription("子应用设置：" + descriptor.displayName());
            gear.setEnabled(actions!=null);
            gear.setOnClickListener(v->dispatch(current,()->actions.settings(descriptor)));
            row.addView(gear,new LinearLayout.LayoutParams(dp(64),dp(48)));
            content.addView(row);
        }
    }

    private void dispatch(Object expected,Runnable intent){
        BrowserToolbar.Binding captured=binding;if(captured==null || frame!=expected)return;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{captured.check();if(frame!=expected || captured!=binding)return;intent.run();});
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)setTitle("操作结果未知，请重新读取子应用状态");
    }
    @Override protected void onDestroy(){binding=null;frame=null;BrowserToolbar.clearOwnedViewTree(content);super.onDestroy();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
