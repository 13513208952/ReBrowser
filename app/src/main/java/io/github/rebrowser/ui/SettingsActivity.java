package io.github.rebrowser.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Native settings WINDOW SHELL composed from the static READ-ONLY SettingsSections directory.
 * It neither opens SettingsRepository nor writes a preference, invents defaults, shows source
 * warnings or performs owner verification: rows merely report the exact row INTENT and values
 * that were not observed stay "未读取" and inert.
 */
public final class SettingsActivity extends Activity {
    public interface Actions {
        void openRow(SettingsSections.Row row,long expectedRevision);
    }
    public static final int MAX_ROWS = 24;

    /** Observed read-only values for rows; absent = unknown. No default is synthesised. */
    public static final class Observed {
        private final Map<String,Boolean> values;private final long revision;
        public Observed(Map<String,Boolean> values,long revision){
            if(values==null || values.size()>SettingsSections.MAX_ROWS || revision<1)throw new IllegalArgumentException("Invalid observed settings");
            for(Map.Entry<String,Boolean> entry:values.entrySet()){if(entry.getKey()==null || !Rows.ids().contains(entry.getKey()))throw new IllegalArgumentException("Invalid row id");}
            this.values=Collections.unmodifiableMap(new java.util.LinkedHashMap<>(values));this.revision=revision;
        }
        public Boolean value(String rowId){return values.get(rowId);}
        public long revision(){return revision;}
    }
    public static final class Rows {
        public static int count(){int total=0;for(SettingsSections.Section section:SettingsSections.sections())total+=section.rows().size();if(total>MAX_ROWS)throw new IllegalStateException("Settings rows exceed bound");return total;}
        public static Set<String> ids(){Set<String> ids=new HashSet<>();for(SettingsSections.Section section:SettingsSections.sections())for(SettingsSections.Row row:section.rows())if(!ids.add(row.id()))throw new IllegalStateException("Duplicate settings row");return ids;}
        public static List<SettingsSections.Section> sections(){return SettingsSections.sections();}
    }

    private LinearLayout content;
    private BrowserToolbar.Binding binding;private Object frame;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        setContentView(scroll);
        render(null,null);
    }

    /** Native owner supplied values; unknown rows remain visible but disabled. */
    public void bind(BrowserToolbar.Binding binding,Observed observed,Actions actions){
        BrowserToolbar.main();
        if(binding==null || observed==null || actions==null)throw new IllegalArgumentException("Missing native settings binding");
        binding.check();
        this.binding=binding;
        render(observed,actions);
    }

    public void unbind(){
        BrowserToolbar.main();
        binding=null;frame=null;
        render(null,null);
    }

    private void render(Observed observed,Actions actions){
        BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();
        final Object current=new Object();frame=current;
        TextView status=new TextView(this);
        status.setText(observed==null?"设置值未读取（需要原生窗口提供）":"设置值来自原生只读投影");
        content.addView(status);
        for(SettingsSections.Section section:Rows.sections()){
            TextView title=new TextView(this);
            title.setText(section.title());
            title.setTextSize(18);
            content.addView(title);
            for(SettingsSections.Row row:section.rows()){
                Boolean value=observed==null?null:observed.value(row.id());
                Button button=new Button(this);
                button.setText(row.title()+" · "+SettingsSections.observedValueText(value));
                button.setMinimumHeight(dp(48));
                button.setEnabled(actions!=null && SettingsSections.actionable(value) && row.route()!=SettingsSections.Route.SOURCE_SAFETY);
                button.setContentDescription(row.title()+(value==null?"，未读取":value?"，已开启":"，已关闭"));
                long revision=observed==null?0:observed.revision();
                button.setOnClickListener(v->dispatch(current,row,revision,actions));
                content.addView(button,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
            }
        }
    }

    private void dispatch(Object expected,SettingsSections.Row row,long revision,Actions actions){
        BrowserToolbar.Binding captured=binding;
        if(captured==null || actions==null || frame!=expected)return;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(frame!=expected || captured!=binding)return;
            actions.openRow(row,revision);
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)setTitle("操作结果未知，请重新读取设置");
    }

    @Override protected void onDestroy(){binding=null;frame=null;BrowserToolbar.clearOwnedViewTree(content);super.onDestroy();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
