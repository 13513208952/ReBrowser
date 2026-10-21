package io.github.rebrowser.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import io.github.rebrowser.subapps.SubapplicationRegistry;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Native built-in subapplication list. The directory is static; enablement and the OFFERED
 * actions are READ-ONLY projections that may be UNKNOWN. Open/toggle/settings only emit exact
 * INTENT with the original frame+entry, and a requested toggle waits for a fresh native
 * projection instead of optimistically flipping the displayed state.
 */
public final class ApplicationsActivity extends Activity {
    public enum Enablement { UNKNOWN, REPORTED_ENABLED, REPORTED_DISABLED }
    /** Display-only offered capabilities; none of them grants the operation. */
    public enum Offered { OPEN, TOGGLE, SETTINGS }
    public interface Actions {
        void open(SubapplicationRegistry.Descriptor descriptor);
        void requestToggle(SubapplicationRegistry.Descriptor descriptor,boolean currentlyEnabled);
        void settings(SubapplicationRegistry.Descriptor descriptor);
    }

    /** One entry's reported state plus offered actions and an explicit unavailable reason. */
    public static final class EntryState {
        private final Enablement enablement;private final Set<Offered> offered;private final String unavailableReason;
        public EntryState(Enablement enablement,Set<Offered> offered,String unavailableReason){
            if(enablement==null || offered==null)throw new IllegalArgumentException("Invalid entry state");
            EnumSet<Offered> copy=EnumSet.noneOf(Offered.class);copy.addAll(offered);
            this.enablement=enablement;this.offered=Collections.unmodifiableSet(copy);
            this.unavailableReason=unavailableReason==null||unavailableReason.isEmpty()?"原生未提供该操作":bounded(unavailableReason,96);
        }
        public Enablement enablement(){return enablement;}
        public Set<Offered> offered(){return offered;}
        public boolean offers(Offered action){return offered.contains(action);}
        public String unavailableReason(){return unavailableReason;}
    }

    public static final class Projection {
        private final Map<String,EntryState> states;private final long revision;
        public Projection(Map<String,Enablement> states,long revision){
            this(legacy(states),revision,true);
        }
        private static Map<String,EntryState> legacy(Map<String,Enablement> states){
            if(states==null || states.size()>SubapplicationRegistry.all().size())throw new IllegalArgumentException("Invalid enablement projection");
            Map<String,EntryState> converted=new LinkedHashMap<>();
            for(Map.Entry<String,Enablement> entry:states.entrySet()){
                if(entry.getValue()==null)throw new IllegalArgumentException("Unknown state must be explicit");
                // Legacy callers supplied only a state: no action is inferred from it.
                converted.put(entry.getKey(),new EntryState(entry.getValue(),EnumSet.noneOf(Offered.class),"仅提供启用状态，未提供操作"));
            }
            return converted;
        }
        public Projection(Map<String,EntryState> states,long revision,boolean typed){
            if(states==null || states.size()>SubapplicationRegistry.all().size() || revision<1)throw new IllegalArgumentException("Invalid enablement projection");
            for(Map.Entry<String,EntryState> entry:states.entrySet()){
                SubapplicationRegistry.require(entry.getKey());
                if(entry.getValue()==null)throw new IllegalArgumentException("Unknown entry state must be explicit");
            }
            this.states=Collections.unmodifiableMap(new LinkedHashMap<>(states));this.revision=revision;
        }
        public Enablement state(SubapplicationRegistry.Descriptor descriptor){return entryState(descriptor).enablement();}
        public EntryState entryState(SubapplicationRegistry.Descriptor descriptor){
            if(descriptor==null)throw new IllegalArgumentException("Missing descriptor");
            EntryState state=states.get(descriptor.localId());
            return state==null?new EntryState(Enablement.UNKNOWN,EnumSet.noneOf(Offered.class),"原生未提供该子应用状态"):state;
        }
        public long revision(){return revision;}
        public static String label(Enablement state){
            if(state==null)throw new IllegalArgumentException("Missing enablement");
            switch(state){case REPORTED_ENABLED:return "已启用（报告）";case REPORTED_DISABLED:return "已停用（报告）";default:return "启用状态未知";}
        }
    }

    private LinearLayout content;
    private BrowserToolbar.Binding binding;
    private Object frame;
    private Actions actions;
    private Projection projection;
    private final Set<String> toggleAttempts=new java.util.HashSet<>();

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        setContentView(new ScrollView(this){{addView(content);}});
        render(null,null);
    }

    public void bind(BrowserToolbar.Binding binding,Projection projection,Actions actions){
        BrowserToolbar.main();
        if(binding==null || projection==null || actions==null)throw new IllegalArgumentException("Missing native applications binding");
        binding.check();
        this.binding=binding;this.projection=projection;this.actions=actions;this.frame=new Object();
        toggleAttempts.clear();
        render(projection,actions);
    }
    public void unbind(){BrowserToolbar.main();binding=null;frame=null;actions=null;projection=null;toggleAttempts.clear();render(null,null);}

    private void render(Projection projection,Actions actions){
        BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();
        final Object current=frame;
        TextView status=new TextView(this);
        status.setText("固定内建子应用；状态与可执行操作为原生只读投影，操作仅提交意图");
        content.addView(status);
        List<SubapplicationRegistry.Descriptor> all=SubapplicationRegistry.all();
        for(SubapplicationRegistry.Descriptor descriptor:all){
            EntryState state=projection==null
                    ?new EntryState(Enablement.UNKNOWN,EnumSet.noneOf(Offered.class),"原生未提供该子应用状态")
                    :projection.entryState(descriptor);
            Enablement enablement=state.enablement();
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView label=new TextView(this);
            boolean attemptedToggle=toggleAttempts.contains(descriptor.localId());
            String suffix=attemptedToggle?" · 已请求启停，等待原生刷新":state.offers(Offered.OPEN)||state.offers(Offered.TOGGLE)||state.offers(Offered.SETTINGS)?"":" · "+state.unavailableReason();
            label.setText(descriptor.displayName()+" · "+Projection.label(enablement)+suffix);
            label.setTextSize(16);
            label.setMinimumHeight(dp(48));
            row.addView(label,new LinearLayout.LayoutParams(0,dp(48),1));
            boolean toggleEnabled=actions!=null && state.offers(Offered.TOGGLE) && enablement!=Enablement.UNKNOWN && !attemptedToggle;
            Button toggle=new Button(this);
            toggle.setText(enablement==Enablement.REPORTED_ENABLED?"停用":"启用");
            toggle.setMinimumHeight(dp(48));
            toggle.setEnabled(toggleEnabled);
            toggle.setContentDescription(toggleEnabled?(enablement==Enablement.REPORTED_ENABLED?"请求停用：":"请求启用：")+descriptor.displayName()
                    :attemptedToggle?"已请求启停，等待原生刷新":"启停操作未提供");
            toggle.setOnClickListener(toggleEnabled?v->requestToggle(current,descriptor,enablement==Enablement.REPORTED_ENABLED,actions):null);
            row.addView(toggle,new LinearLayout.LayoutParams(dp(88),dp(48)));
            boolean openEnabled=actions!=null && state.offers(Offered.OPEN);
            Button open=new Button(this);
            open.setText("打开");
            open.setMinimumHeight(dp(48));
            open.setEnabled(openEnabled);
            open.setContentDescription(openEnabled?"打开：" + descriptor.displayName():"打开操作未提供");
            open.setOnClickListener(openEnabled?v->dispatch(current,actions,()->actions.open(descriptor)):null);
            row.addView(open,new LinearLayout.LayoutParams(dp(88),dp(48)));
            boolean settingsEnabled=actions!=null && state.offers(Offered.SETTINGS);
            Button gear=new Button(this);
            gear.setText("设置");
            gear.setMinimumWidth(dp(48));gear.setMinimumHeight(dp(48));
            gear.setContentDescription(settingsEnabled?"子应用设置：" + descriptor.displayName():"设置入口未提供");
            gear.setEnabled(settingsEnabled);
            gear.setOnClickListener(settingsEnabled?v->dispatch(current,actions,()->actions.settings(descriptor)):null);
            row.addView(gear,new LinearLayout.LayoutParams(dp(64),dp(48)));
            content.addView(row);
        }
    }

    private void requestToggle(Object expected,SubapplicationRegistry.Descriptor descriptor,boolean currentlyEnabled,Actions actions){
        BrowserToolbar.Binding captured=binding;
        if(captured==null || actions==null || actions!=this.actions || frame!=expected)return;
        try{captured.check();}catch(RuntimeException stale){return;}
        if(frame!=expected || binding!=captured || actions!=this.actions)return;
        if(!toggleAttempts.add(descriptor.localId()))return; // one toggle attempt per frame+entry
        render(projection,actions);
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(frame!=expected || captured!=binding)return;
            actions.requestToggle(descriptor,currentlyEnabled);
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)setTitle("启停请求结果未知，请重新读取子应用状态");
    }

    private void dispatch(Object expected,Actions actions,Runnable intent){
        BrowserToolbar.Binding captured=binding;if(captured==null || actions==null || frame!=expected)return;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{captured.check();if(frame!=expected || captured!=binding)return;intent.run();});
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)setTitle("操作结果未知，请重新读取子应用状态");
    }
    @Override protected void onDestroy(){binding=null;frame=null;actions=null;projection=null;toggleAttempts.clear();BrowserToolbar.clearOwnedViewTree(content);super.onDestroy();}
    private static String bounded(String value,int max){
        if(value.length()>max)throw new IllegalArgumentException("Unbounded reason text");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw new IllegalArgumentException("Control in reason");if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}
        return value;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
