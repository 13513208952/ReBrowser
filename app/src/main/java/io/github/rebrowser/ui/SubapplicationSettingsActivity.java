package io.github.rebrowser.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import io.github.rebrowser.subapps.SubapplicationRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Native subapplication settings WINDOW SHELL with a bounded edit buffer. Edits are collected per
 * stable option token and submitted as ONE atomic change intent carrying the original revision;
 * cancelling only discards edits that were never delivered, and an unknown save is never reported
 * as cancelled or saved. No account/Cookie/preference access, audio switching, sign-out, cleanup
 * or supported-list mutation happens here.
 */
public final class SubapplicationSettingsActivity extends Activity {
    public static final int MAX_OPTIONS = 16;
    public static final int MAX_LABEL_CHARS = 64;
    public static final int MAX_TOKEN_CHARS = 32;

    public interface Actions {
        void change(String subapplicationId,String optionToken,String desiredValue,long expectedRevision);
        /** Atomic multi-option submission; still only an INTENT for the native owner. */
        void submit(Batch batch);
    }
    public static final class Option {
        private final String token,label;private final Set<String> allowed;private final String observed;
        public Option(String token,String label,Set<String> allowed,String observed){
            this.token=opaqueToken(token);this.label=text(label);
            if(allowed==null || allowed.isEmpty() || allowed.size()>8)throw new IllegalArgumentException("Invalid option domain");
            Set<String> copy=new java.util.LinkedHashSet<>();
            for(String value:allowed)copy.add(text(value));
            this.allowed=Collections.unmodifiableSet(copy);
            if(observed!=null && !this.allowed.contains(observed))throw new IllegalArgumentException("Observed option outside its domain");
            this.observed=observed;
        }
        public String token(){return token;}
        public String label(){return label;}
        public Set<String> allowed(){return allowed;}
        /** null = not read; never replaced by a default. */
        public String observed(){return observed;}
    }
    public static final class Options {
        private final String subapplicationId;private final List<Option> options;private final long revision;
        public Options(String subapplicationId,List<Option> options,long revision){
            this.subapplicationId=SubapplicationRegistry.require(subapplicationId).localId();
            if(options==null || options.size()>MAX_OPTIONS || revision<1)throw new IllegalArgumentException("Invalid options page");
            Set<String> tokens=new HashSet<>();List<Option> copy=new ArrayList<>();
            for(Option option:options){if(option==null)throw new IllegalArgumentException("Missing option");if(!tokens.add(option.token()))throw new IllegalArgumentException("Duplicate option token");copy.add(option);}
            this.options=Collections.unmodifiableList(copy);this.revision=revision;
        }
        public String subapplicationId(){return subapplicationId;}
        public List<Option> options(){return options;}
        public long revision(){return revision;}
        public Option requireOption(String token){
            if(token==null)throw new IllegalArgumentException("Missing option token");
            for(Option option:options)if(option.token().equals(token))return option;
            throw new IllegalArgumentException("Unknown option token");
        }
    }
    /** One atomic submission of at most MAX_OPTIONS typed option changes. */
    public static final class Batch {
        private final String subapplicationId;private final long expectedRevision;
        private final Map<String,String> changes;
        public Batch(String subapplicationId,long expectedRevision,Map<String,String> changes){
            if(subapplicationId==null || expectedRevision<1 || changes==null || changes.isEmpty() || changes.size()>MAX_OPTIONS)
                throw new IllegalArgumentException("Invalid subapplication change batch");
            SubapplicationRegistry.require(subapplicationId);
            for(Map.Entry<String,String> e:changes.entrySet()){opaqueToken(e.getKey());text(e.getValue());}
            this.subapplicationId=subapplicationId;this.expectedRevision=expectedRevision;
            this.changes=Collections.unmodifiableMap(new LinkedHashMap<>(changes));
        }
        public String subapplicationId(){return subapplicationId;}
        public long expectedRevision(){return expectedRevision;}
        public Map<String,String> changes(){return changes;}
        public boolean isPersisted(){return false;}
    }

    /** Owner-thread draft: at most one value per option; cancel only affects undelivered edits. */
    public static final class Draft {
        private final Thread owner=Thread.currentThread();
        private final Options base;private final Map<String,String> pending=new LinkedHashMap<>();
        private boolean attempted;
        public Draft(Options base){if(base==null)throw new IllegalArgumentException("Missing options base");this.base=base;}
        public Options base(){return base;}
        public boolean isDirty(){check();return !pending.isEmpty();}
        public boolean attempted(){check();return attempted;}
        public String set(String token,String value){
            check();
            Option option=base.requireOption(token);
            if(option.observed()==null)return "原生值未读取，不能编辑";
            if(value==null || !option.allowed().contains(value))return "不支持的选项值，未加入";
            if(attempted)return "更改已提交，等待原生重新读取";
            if(value.equals(option.observed()))pending.remove(token);else pending.put(token,value);return null;
        }
        public void discard(String token){check();if(!attempted)pending.remove(token);}
        /** Returns true only when undelivered edits were actually discarded. */
        public boolean cancel(){check();if(attempted)return false;pending.clear();return true;}
        public Map<String,String> pending(){check();return Collections.unmodifiableMap(new LinkedHashMap<>(pending));}
        public Batch beginAttempt(){
            check();
            if(attempted || pending.isEmpty())throw new IllegalStateException("No pending edits to submit");
            attempted=true;
            return new Batch(base.subapplicationId(),base.revision(),pending);
        }
        private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Subapplication draft owner thread required");}
    }

    private LinearLayout content;
    private TextView status;
    private BrowserToolbar.Binding binding;private Object frame;
    private Actions actions;private Draft draft;
    private Option editing;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        setContentView(scroll);
        status=new TextView(this);
        status.setText("未连接原生子应用设置窗口");
        content.addView(status);
    }

    public void bind(BrowserToolbar.Binding binding,Options options,Actions actions){
        BrowserToolbar.main();
        if(binding==null || options==null || actions==null)throw new IllegalArgumentException("Missing native subapplication binding");
        binding.check();
        this.binding=binding;this.actions=actions;this.draft=new Draft(options);this.frame=new Object();
        status.setText(SubapplicationRegistry.require(options.subapplicationId()).displayName()+" 设置 · 更改为未提交草稿");
        render();
    }

    private void render(){
        CharSequence message=status.getText();BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();status.setText(message);
        content.addView(status);
        if(draft==null || binding==null)return;
        final Object expectedFrame=frame;final Draft exactDraft=draft;final BrowserToolbar.Binding exactBinding=binding;
        Options options=draft.base();
        for(Option option:options.options()){
            LinearLayout line=new LinearLayout(this);
            line.setOrientation(LinearLayout.VERTICAL);
            TextView label=new TextView(this);
            String pendingValue=draft.pending().get(option.token());
            label.setText(option.label()+"：" + (pendingValue!=null?"待提交 " + pendingValue:option.observed()==null?"未读取":"已读取 " + option.observed()));
            line.addView(label);
            LinearLayout choices=new LinearLayout(this);
            choices.setOrientation(LinearLayout.HORIZONTAL);
            for(String value:option.allowed()){
                Button button=new Button(this);
                button.setText(value);
                button.setMinimumHeight(dp(48));
                boolean active=draft!=null && !draft.attempted() && option.observed()!=null && !value.equals(option.observed());
                button.setEnabled(active);
                button.setContentDescription(active?"将"+option.label()+"改为"+value+"（草稿）":"选项不可编辑或已提交");
                button.setOnClickListener(active?v->choose(expectedFrame,exactDraft,exactBinding,option,value):null);
                choices.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));
            }
            line.addView(choices);
            content.addView(line);
        }
        Button submit=new Button(this);
        submit.setText("提交更改");
        submit.setMinimumHeight(dp(48));
        boolean canSubmit=draft.isDirty() && !draft.attempted();
        submit.setEnabled(canSubmit);
        submit.setOnClickListener(canSubmit?v->submit():null);
        content.addView(submit,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
        Button cancel=new Button(this);
        cancel.setText("放弃未提交更改");
        cancel.setMinimumHeight(dp(48));
        cancel.setEnabled(draft.isDirty() && !draft.attempted());
        cancel.setOnClickListener(v->{
            if(frame!=expectedFrame || draft!=exactDraft || binding!=exactBinding)return;
            boolean discarded=exactDraft.cancel();
            status.setText(discarded?"已放弃未提交更改":"更改已提交，不能本地撤销，等待原生结果");
            render();
        });
        content.addView(cancel,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
    }

    private void choose(Object expectedFrame,Draft exactDraft,BrowserToolbar.Binding captured,Option option,String value){
        if(captured==null || captured!=binding || exactDraft!=draft || frame!=expectedFrame)return;
        try{captured.check();}catch(SecurityException|IllegalStateException stale){unbind();return;}
        if(frame!=expectedFrame || draft!=exactDraft || binding!=captured)return;
        String problem=exactDraft.set(option.token(),value);
        status.setText(problem==null?option.label()+"：已加入待提交更改":problem);
        render();
    }

    private void submit(){
        BrowserToolbar.Binding captured=binding;Actions owner=actions;Object expected=frame;
        if(captured==null || owner==null || draft==null || frame!=expected)return;
        final Batch batch;
        try{batch=draft.beginAttempt();}catch(IllegalStateException empty){return;}
        status.setText("已提交 " + batch.changes().size() + " 项更改，等待原生确认");
        render();
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(frame!=expected || captured!=binding)return;
            owner.submit(batch);
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)
            status.setText("更改结果未知，请重新读取；不能视为已保存或已取消");
    }

    public void unbind(){BrowserToolbar.main();binding=null;frame=null;actions=null;draft=null;editing=null;BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();content.addView(status);status.setText("未连接原生子应用设置窗口");}

    @Override protected void onDestroy(){binding=null;frame=null;actions=null;draft=null;editing=null;BrowserToolbar.clearOwnedViewTree(content);super.onDestroy();}
    static String opaqueToken(String value){
        if(value==null || value.isEmpty() || value.length()>MAX_TOKEN_CHARS)throw new IllegalArgumentException("Invalid option token");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);boolean ok=(c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='_'||c=='-';if(!ok)throw new IllegalArgumentException("Option token must be an opaque id");}
        return value;
    }
    static String text(String value){
        if(value==null || value.isEmpty() || value.length()>MAX_LABEL_CHARS)throw new IllegalArgumentException("Invalid option text");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw new IllegalArgumentException("Control in option text");if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}
        return value;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
