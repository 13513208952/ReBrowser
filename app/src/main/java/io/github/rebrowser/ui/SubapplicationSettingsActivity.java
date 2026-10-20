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
import java.util.List;
import java.util.Set;

/**
 * Native subapplication settings WINDOW SHELL. It renders a bounded, already-supplied projection
 * of music source/entry/restore (or Outlook entry) options and emits exact change INTENT. It never
 * reads accounts/Cookies/preferences, switches audio source, signs out, runs data cleanup or
 * edits the fixed supported-subapplication list.
 */
public final class SubapplicationSettingsActivity extends Activity {
    public static final int MAX_OPTIONS = 16;
    public static final int MAX_LABEL_CHARS = 64;

    public interface Actions {
        void change(String subapplicationId,String optionToken,String desiredValue,long expectedRevision);
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
    }

    private LinearLayout content;
    private BrowserToolbar.Binding binding;private Object frame;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        setContentView(new ScrollView(this){{addView(content);}});
        TextView empty=new TextView(this);empty.setText("未连接原生子应用设置窗口");content.addView(empty);
    }

    public void bind(BrowserToolbar.Binding binding,Options options,Actions actions){
        BrowserToolbar.main();
        if(binding==null || options==null || actions==null)throw new IllegalArgumentException("Missing native subapplication binding");
        binding.check();
        this.binding=binding;
        BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();
        final Object current=new Object();frame=current;
        TextView header=new TextView(this);
        header.setText(SubapplicationRegistry.require(options.subapplicationId()).displayName()+" 设置 · 仅展示已提供选项");
        content.addView(header);
        for(Option option:options.options()){
            TextView label=new TextView(this);
            label.setText(option.label()+"：" + (option.observed()==null?"未读取":"已读取"));
            content.addView(label);
            LinearLayout choices=new LinearLayout(this);
            choices.setOrientation(LinearLayout.HORIZONTAL);
            for(String value:option.allowed()){
                Button button=new Button(this);
                button.setText(value);
                button.setMinimumHeight(dp(48));
                button.setEnabled(option.observed()!=null && !value.equals(option.observed()));
                button.setContentDescription("将"+option.label()+"改为"+value+"（请求）");
                button.setOnClickListener(v->dispatch(current,options,option,value,actions));
                choices.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));
            }
            content.addView(choices);
        }
    }

    public void unbind(){BrowserToolbar.main();binding=null;frame=null;BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();}

    private void dispatch(Object expected,Options options,Option option,String desired,Actions actions){
        BrowserToolbar.Binding captured=binding;
        if(captured==null || frame!=expected || !option.allowed().contains(desired))return;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(frame!=expected || captured!=binding)return;
            actions.change(options.subapplicationId(),option.token(),desired,options.revision());
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)setTitle("更改结果未知，请重新读取设置");
    }

    static String opaqueToken(String value){
        if(value==null || value.isEmpty() || value.length()>32)throw new IllegalArgumentException("Invalid option token");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);boolean ok=(c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='_'||c=='-';if(!ok)throw new IllegalArgumentException("Option token must be an opaque id");}
        return value;
    }
    static String text(String value){
        if(value==null || value.isEmpty() || value.length()>MAX_LABEL_CHARS)throw new IllegalArgumentException("Invalid option text");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw new IllegalArgumentException("Control in option text");if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}
        return value;
    }
    @Override protected void onDestroy(){binding=null;frame=null;BrowserToolbar.clearOwnedViewTree(content);super.onDestroy();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
