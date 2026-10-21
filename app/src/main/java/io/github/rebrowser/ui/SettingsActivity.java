package io.github.rebrowser.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native settings WINDOW SHELL with TYPED edit sessions over the static READ-ONLY directory.
 * Edits never write preferences: they are collected per stable row id and emitted as one bounded
 * change INTENT carrying the original revision. Invalid input is explained locally and does not
 * submit; an attempted change with unknown outcome is never shown as saved and cannot escape its
 * frame by rebinding.
 */
public final class SettingsActivity extends Activity {
    public interface Actions {
        void openRow(SettingsSections.Row row,long expectedRevision);
        /** One batch intent, NOT a series of independently executed mutations. */
        void onChanges(List<Change> changes,long expectedRevision);
    }
    public static final int MAX_ROWS = 24;

    /** One typed change for exactly one row; not a storage key or saved receipt. */
    public static final class Change {
        private final String rowId;private final SettingsSections.ValueKind kind;
        private final Boolean booleanValue;private final String stringValue;private final long expectedRevision;
        Change(String rowId,SettingsSections.ValueKind kind,Boolean booleanValue,String stringValue,long expectedRevision){
            if(rowId==null || kind==null || expectedRevision<1)throw new IllegalArgumentException("Invalid settings change");
            SettingsSections.Row schema=SettingsSections.requireRow(rowId);
            if(schema.kind()!=kind || kind==SettingsSections.ValueKind.READ_ONLY)throw new IllegalArgumentException("Setting editor was not offered");
            if(stringValue!=null)SettingsSections.text(stringValue,kind==SettingsSections.ValueKind.FIXED_OPTION?SettingsSections.MAX_OPTION_VALUE_CHARS:schema.maxTextChars());
            if((kind==SettingsSections.ValueKind.BOOLEAN)!=(booleanValue!=null))throw new IllegalArgumentException("Boolean change mismatch");
            if(kind==SettingsSections.ValueKind.BOOLEAN && stringValue!=null)throw new IllegalArgumentException("Boolean change carries text");
            if(kind==SettingsSections.ValueKind.BOUNDED_TEXT && (stringValue==null || stringValue.isEmpty()))throw new IllegalArgumentException("Text change requires value");
            if(kind!=SettingsSections.ValueKind.BOUNDED_TEXT && kind!=SettingsSections.ValueKind.FIXED_OPTION && stringValue!=null)throw new IllegalArgumentException("Unexpected text value");
            if(kind==SettingsSections.ValueKind.FIXED_OPTION && SettingsSections.requireRow(rowId).findOption(stringValue)==null)throw new IllegalArgumentException("Unsupported option value");
            this.rowId=rowId;this.kind=kind;this.booleanValue=booleanValue;this.stringValue=stringValue;this.expectedRevision=expectedRevision;
        }
        public String rowId(){return rowId;}
        public SettingsSections.ValueKind kind(){return kind;}
        public Boolean booleanValue(){return booleanValue;}
        public String stringValue(){return stringValue;}
        public long expectedRevision(){return expectedRevision;}
        public boolean isPersisted(){return false;}
    }

    /** Bounded owner-thread edit buffer: one change per row, invalid input rejected with a reason. */
    public static final class EditSession {
        private final Thread owner=Thread.currentThread();
        private final long revision;
        private final Map<String,Change> pending=new LinkedHashMap<>();
        private boolean attempted;
        public EditSession(long revision){if(revision<1)throw new IllegalArgumentException("Invalid settings revision");this.revision=revision;}
        public long revision(){return revision;}
        public String setBoolean(String rowId,boolean value){return put(sessionRow(rowId,SettingsSections.ValueKind.BOOLEAN),new Change(rowId,SettingsSections.ValueKind.BOOLEAN,value,null,revision));}
        public String chooseOption(String rowId,String value){
            SettingsSections.Row row=sessionRow(rowId,SettingsSections.ValueKind.FIXED_OPTION);
            if(value==null)return "未选择选项";
            SettingsSections.Option option=row.findOption(value);
            if(option==null)return "不支持的选项，未提交";
            return put(row,new Change(rowId,SettingsSections.ValueKind.FIXED_OPTION,null,option.value(),revision));
        }
        public String setText(String rowId,String value){
            SettingsSections.Row row=sessionRow(rowId,SettingsSections.ValueKind.BOUNDED_TEXT);
            if(value==null || value.trim().isEmpty())return "内容不能为空";
            if(value.length()>row.maxTextChars())return "内容超过" + row.maxTextChars() + "字符";
            try{SettingsSections.text(value,row.maxTextChars());}catch(IllegalArgumentException invalid){return "内容包含不可用字符";}
            return put(row,new Change(rowId,SettingsSections.ValueKind.BOUNDED_TEXT,null,value,revision));
        }
        private SettingsSections.Row sessionRow(String rowId,SettingsSections.ValueKind kind){
            check();SettingsSections.Row row=SettingsSections.requireRow(rowId);
            if(row.kind()!=kind)throw new IllegalArgumentException("Row kind mismatch");
            return row;
        }
        private String put(SettingsSections.Row row,Change change){
            check();
            if(attempted)return "更改已提交，等待原生重新读取";
            pending.put(row.id(),change);return null;
        }
        public void discard(String rowId){check();if(!attempted)pending.remove(rowId);}
        /** Cancel only undelivered edits; an attempted change can never be "cancelled" locally. */
        public boolean cancel(){check();if(attempted)return false;pending.clear();return true;}
        public List<Change> pendingChanges(){check();return Collections.unmodifiableList(new ArrayList<>(pending.values()));}
        public boolean isDirty(){check();return !pending.isEmpty();}
        /** Consume every pending change at once; the buffer stays spent even on unknown outcome. */
        public List<Change> beginAttempt(){check();if(attempted || pending.isEmpty())return Collections.emptyList();attempted=true;return pendingChanges();}
        public boolean attempted(){check();return attempted;}
        private void check(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Settings edit owner thread required");}
    }

    /** Observed read-only values; absent = unknown and never enabled by default. */
    public static final class Observed {
        private final Map<String,Boolean> booleans;private final Map<String,String> options;private final Map<String,String> texts;
        private final long revision;
        public Observed(Map<String,Boolean> booleans,Map<String,String> options,Map<String,String> texts,long revision){
            if(booleans==null || options==null || texts==null || revision<1)throw new IllegalArgumentException("Invalid observed settings");
            if(booleans.size()>MAX_ROWS || options.size()>MAX_ROWS || texts.size()>MAX_ROWS)throw new IllegalArgumentException("Settings projection too large");
            for(String key:booleans.keySet())SettingsSections.requireRow(key);
            for(Map.Entry<String,String> e:options.entrySet()){SettingsSections.requireRow(e.getKey());if(e.getValue()!=null)SettingsSections.text(e.getValue(),SettingsSections.MAX_OPTION_VALUE_CHARS);}
            for(Map.Entry<String,String> e:texts.entrySet()){SettingsSections.requireRow(e.getKey());if(e.getValue()!=null)SettingsSections.text(e.getValue(),SettingsSections.MAX_TEXT_CHARS);}
            this.booleans=Collections.unmodifiableMap(new LinkedHashMap<>(booleans));
            this.options=Collections.unmodifiableMap(new LinkedHashMap<>(options));
            this.texts=Collections.unmodifiableMap(new LinkedHashMap<>(texts));
            this.revision=revision;
        }
        public Observed(Map<String,Boolean> booleans,long revision){this(booleans,Collections.<String,String>emptyMap(),Collections.<String,String>emptyMap(),revision);}
        public Boolean value(String rowId){return booleans.get(rowId);}
        public String option(String rowId){return options.get(rowId);}
        public String text(String rowId){return texts.get(rowId);}
        public long revision(){return revision;}
    }
    public static final class Rows {
        public static int count(){int total=0;for(SettingsSections.Section section:SettingsSections.sections())total+=section.rows().size();if(total>MAX_ROWS)throw new IllegalStateException("Settings rows exceed bound");return total;}
        public static java.util.Set<String> ids(){java.util.Set<String> ids=new java.util.HashSet<>();for(SettingsSections.Section section:SettingsSections.sections())for(SettingsSections.Row row:section.rows())if(!ids.add(row.id()))throw new IllegalStateException("Duplicate settings row");return ids;}
        public static List<SettingsSections.Section> sections(){return SettingsSections.sections();}
    }

    private LinearLayout content;
    private TextView status;
    private BrowserToolbar.Binding binding;
    private Object frame;
    private Actions actions;
    private EditSession edits;
    private Observed observed;private Button submitControl;
    private void updateSubmit(){if(submitControl!=null)submitControl.setEnabled(edits!=null && edits.isDirty() && !edits.attempted());}

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        setContentView(scroll);
        status=new TextView(this);
        status.setText("设置值未读取（需要原生窗口提供）");
        content.addView(status);
        renderRows();
    }

    /** Native owner supplied values; unknown rows remain visible but disabled. */
    public void bind(BrowserToolbar.Binding binding,Observed observed,Actions actions){
        BrowserToolbar.main();
        if(binding==null || observed==null || actions==null)throw new IllegalArgumentException("Missing native settings binding");
        binding.check();
        this.binding=binding;this.observed=observed;this.actions=actions;this.frame=new Object();
        this.edits=new EditSession(observed.revision());
        BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();
        content.addView(status);
        status.setText("设置值来自原生只读投影 · 更改需原生确认");
        renderRows();
    }

    public void unbind(){
        BrowserToolbar.main();
        binding=null;frame=null;actions=null;edits=null;observed=null;
        BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();
        content.addView(status);
        status.setText("设置值未读取（需要原生窗口提供）");
    }

    private void renderRows(){
        CharSequence message=status.getText();BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();status.setText(message);content.addView(status);
        final Object current=frame;
        for(SettingsSections.Section section:Rows.sections()){
            TextView title=new TextView(this);
            title.setText(section.title());
            title.setTextSize(18);
            content.addView(title);
            for(SettingsSections.Row row:section.rows())content.addView(controlFor(current,row));
        }
        if(binding!=null){
            Button submit=new Button(this);submitControl=submit;
            submit.setText("提交更改");
            submit.setMinimumHeight(dp(48));
            submit.setEnabled(edits!=null && edits.isDirty() && !edits.attempted());
            submit.setOnClickListener(v->submitChanges(current));
            content.addView(submit,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
        }
    }

    /** Widget construction happens only after onCreate; no Activity context is captured early. */
    private View controlFor(Object expectedFrame,SettingsSections.Row row){
        boolean active=binding!=null && frame==expectedFrame && edits!=null && !edits.attempted();
        switch(row.kind()){
            case BOOLEAN:{
                CheckBox box=new CheckBox(this);
                Boolean value=observed==null?null:observed.value(row.id());
                box.setText(row.title()+" · "+(value==null?SettingsSections.UNKNOWN_TEXT:SettingsSections.observedValueText(value)));
                box.setChecked(Boolean.TRUE.equals(value));
                box.setEnabled(active && value!=null);
                box.setMinimumHeight(dp(48));
                box.setOnCheckedChangeListener((button,checked)->{
                    if(!isCurrent(expectedFrame))return;
                    String problem=edits.setBoolean(row.id(),checked);
                    if(problem!=null)status.setText(row.title()+"：" + problem);
                    else status.setText(row.title()+"：已加入待提交更改");updateSubmit();
                });
                return box;
            }
            case FIXED_OPTION:{
                LinearLayout line=new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                TextView label=new TextView(this);
                String observedValue=observed==null?null:observed.option(row.id());
                label.setText(row.title()+"："+SettingsSections.observedOptionText(observedValue,row.options()));
                label.setMinimumHeight(dp(48));
                line.addView(label,new LinearLayout.LayoutParams(0,dp(48),2));
                Spinner spinner=new Spinner(this);
                List<String> labels=new ArrayList<>();labels.add(SettingsSections.UNKNOWN_TEXT);
                for(SettingsSections.Option option:row.options())labels.add(option.label());
                android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels);
                spinner.setAdapter(adapter);
                int selected=0;
                if(observedValue!=null)for(int i=0;i<row.options().size();i++)if(row.options().get(i).value().equals(observedValue))selected=i+1;
                spinner.setSelection(selected);
                spinner.setEnabled(active && row.findOption(observedValue)!=null);
                spinner.setMinimumHeight(dp(48));
                spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                    public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                        if(!isCurrent(expectedFrame) || position==0 || position>row.options().size())return;
                        String value=row.options().get(position-1).value();
                        if(value.equals(observedValue))return;
                        String problem=edits.chooseOption(row.id(),value);updateSubmit();
                        status.setText(row.title()+"：" + (problem==null?"已加入待提交更改":problem));
                    }
                    public void onNothingSelected(android.widget.AdapterView<?> parent){}
                });
                line.addView(spinner,new LinearLayout.LayoutParams(0,dp(48),1));
                return line;
            }
            case BOUNDED_TEXT:{
                LinearLayout line=new LinearLayout(this);
                line.setOrientation(LinearLayout.VERTICAL);
                TextView label=new TextView(this);
                String textValue=observed==null?null:observed.text(row.id());
                label.setText(row.title()+"："+SettingsSections.observedTextValue(textValue,row.maxTextChars()));
                line.addView(label);
                EditText input=new EditText(this);input.setSaveEnabled(false);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
                input.setSingleLine(true);
                input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(row.maxTextChars())});
                input.setEnabled(active && textValue!=null);
                input.setHint(row.title());
                Button apply=new Button(this);
                apply.setText("加入待提交");
                apply.setMinimumHeight(dp(48));
                apply.setEnabled(active && textValue!=null);
                apply.setOnClickListener(v->{
                    if(!isCurrent(expectedFrame))return;
                    String problem=edits.setText(row.id(),input.getText()==null?"":input.getText().toString());updateSubmit();
                    status.setText(row.title()+"：" + (problem==null?"已加入待提交更改":problem));
                });
                line.addView(input,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
                line.addView(apply,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
                return line;
            }
            default:{
                Button button=new Button(this);
                Boolean value=observed==null?null:observed.value(row.id());
                button.setText(row.title()+" · "+(value==null?"只读/未读取":SettingsSections.observedValueText(value)));
                button.setMinimumHeight(dp(48));
                button.setEnabled(active);
                button.setContentDescription("打开设置项：" + row.title());
                button.setOnClickListener(v->{
                    Actions owner=actions;BrowserToolbar.Binding captured=binding;
                    if(owner==null || captured==null || !isCurrent(expectedFrame))return;
                    BrowserToolbar.invokeNativeIntent(()->{captured.check();if(isCurrent(expectedFrame))owner.openRow(row,edits.revision());});
                });
                return button;
            }
        }
    }

    private void submitChanges(Object expectedFrame){
        BrowserToolbar.Binding captured=binding;Actions owner=actions;
        if(captured==null || owner==null || edits==null || !isCurrent(expectedFrame))return;
        List<Change> batch=edits.beginAttempt();
        if(batch.isEmpty())return;
        status.setText("已提交 " + batch.size() + " 项更改，等待原生确认");
        renderRows();
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(!isCurrent(expectedFrame))return;
            owner.onChanges(batch,batch.get(0).expectedRevision());
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && isCurrent(expectedFrame))
            status.setText("设置更改结果未知，请重新读取；不显示为已保存");
    }

    @Override protected void onDestroy(){binding=null;frame=null;actions=null;edits=null;observed=null;submitControl=null;BrowserToolbar.clearOwnedViewTree(content);super.onDestroy();}
    private boolean isCurrent(Object expectedFrame){return frame!=null && frame==expectedFrame && binding!=null;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
