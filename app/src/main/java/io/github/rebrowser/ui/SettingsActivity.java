package io.github.rebrowser.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.FrameLayout;
import io.github.rebrowser.ReBrowserApplication;
import io.github.rebrowser.bootstrap.StartupCoordinator;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.settings.SettingsRepository;
import io.github.rebrowser.browser.SearchEngines;
import io.github.rebrowser.system.DefaultBrowserFlow;
import io.github.rebrowser.system.DeviceWindowState;
import io.github.rebrowser.data.OwnedDataCatalog;
import io.github.rebrowser.data.PrivateRecordStore;
import java.lang.ref.WeakReference;
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
 * Native three-peer settings over the canonical epoch-owned encrypted repository. Typed edits
 * remain bounded draft intents until explicit Save; original record revision/source is checked
 * again on the shared IO owner. Only actual write+readback is displayed as saved. Unknown writes
 * remain spent and window retirement never fabricates cancellation or a second submission.
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
            if("ui.row.general.homepage".equals(rowId))try{if(!"about:blank".equals(value))SearchEngines.requireWebUrl(value);}catch(IllegalArgumentException invalid){return "主页应为 http／https 网址或 about:blank";}
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
            for(String key:booleans.keySet())if(SettingsSections.requireRow(key).kind()!=SettingsSections.ValueKind.BOOLEAN)throw new IllegalArgumentException("Boolean projection kind mismatch");
            for(Map.Entry<String,String> e:options.entrySet()){if(SettingsSections.requireRow(e.getKey()).kind()!=SettingsSections.ValueKind.FIXED_OPTION)throw new IllegalArgumentException("Option projection kind mismatch");if(e.getValue()!=null)SettingsSections.text(e.getValue(),SettingsSections.MAX_OPTION_VALUE_CHARS);}
            for(Map.Entry<String,String> e:texts.entrySet()){if(SettingsSections.requireRow(e.getKey()).kind()!=SettingsSections.ValueKind.BOUNDED_TEXT)throw new IllegalArgumentException("Text projection kind mismatch");if(e.getValue()!=null)SettingsSections.text(e.getValue(),SettingsSections.MAX_TEXT_CHARS);}
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

    private LinearLayout content;private final LinearLayout[] panels=new LinearLayout[3];private final ScrollView[] pages=new ScrollView[3];private final TextView[] pageButtons=new TextView[3];private int selectedPage;
    private final Map<String,String> textDrafts=new LinkedHashMap<>(),inputProblems=new LinkedHashMap<>();
    private TextView status;private WindowPresentation windowPresentation;
    private BrowserToolbar.Binding binding;
    private Object frame;
    private Actions actions;
    private EditSession edits;
    private Observed observed;private Button submitControl;private boolean rendering;
    private final DefaultBrowserFlow defaultBrowser=new DefaultBrowserFlow();private DefaultBrowserFlow.Observation defaultRole;private boolean defaultHandoffPending;
    private void updateSubmit(){if(submitControl!=null)submitControl.setEnabled(edits!=null && !edits.attempted() && inputProblems.isEmpty());}

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setBackgroundColor(0xffffffff);
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.VERTICAL);header.setPadding(dp(22),dp(18),dp(22),dp(8));content.addView(header);
        TextView title=new TextView(this);title.setText("ReBrowser 设置");title.setTextSize(26);title.setTypeface(null,android.graphics.Typeface.BOLD);header.addView(title);
        TextView scope=new TextView(this);scope.setText("这些设置由同一应用内的所有 ReBrowser 总标签页共享；网站登录状态仍按总标签页环境隔离。");scope.setTextSize(13);header.addView(scope);
        LinearLayout navigation=new LinearLayout(this);header.addView(navigation);FrameLayout host=new FrameLayout(this);content.addView(host,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<3;i++){final int page=i;TextView button=new TextView(this);button.setText(Rows.sections().get(i).title());button.setTextSize(14);button.setGravity(android.view.Gravity.CENTER);button.setContentDescription(Rows.sections().get(i).title()+"设置");button.setOnClickListener(v->showPage(page));navigation.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));pageButtons[i]=button;ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setSaveEnabled(false);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),0,dp(22),dp(20));scroll.addView(panel);host.addView(scroll,new FrameLayout.LayoutParams(-1,-1));pages[i]=scroll;panels[i]=panel;}
        status=new TextView(this);status.setText("正在读取实际设置；未读取的值不可编辑");status.setPadding(dp(22),0,dp(22),0);content.addView(status);
        submitControl=new Button(this);submitControl.setText("保存并返回");submitControl.setOnClickListener(v->{if(edits!=null && !edits.isDirty() && binding!=null){binding.check();finish();}else submitChanges(frame);});LinearLayout.LayoutParams save=new LinearLayout.LayoutParams(-1,dp(54));save.setMargins(dp(22),dp(8),dp(22),dp(12));content.addView(submitControl,save);
        setContentView(content);windowPresentation=new WindowPresentation(this);windowPresentation.attachNativeContent(content);showPage(0);renderRows();
        startup=((ReBrowserApplication)getApplication()).primaryStartup();
        Object previous=getLastNonConfigurationInstance();if(previous instanceof Retained){Retained saved=(Retained)previous;nativeBase=saved.base;nativePermit=saved.permit;observed=saved.observed;edits=saved.edits;saveJob=saved.save;textDrafts.putAll(saved.text);inputProblems.putAll(saved.problems);selectedPage=saved.page;frame=new Object();for(int i=0;i<3;i++){final int page=i,y=saved.scroll[i];pages[i].post(()->pages[page].scrollTo(0,y));}}
    }

    /** Native owner supplied values; unknown rows remain visible but disabled. */
    public void bind(BrowserToolbar.Binding binding,Observed observed,Actions actions){
        BrowserToolbar.main();
        if(binding==null || observed==null || actions==null)throw new IllegalArgumentException("Missing native settings binding");
        binding.check();
        this.binding=binding;this.observed=observed;this.actions=actions;this.frame=new Object();
        this.edits=new EditSession(observed.revision());
        textDrafts.clear();inputProblems.clear();
        status.setText("已读取实际设置；修改后保存并返回");
        renderRows();
    }

    public void unbind(){
        BrowserToolbar.main();
        if(binding!=null)binding.retire();binding=null;frame=null;actions=null;edits=null;observed=null;
        textDrafts.clear();inputProblems.clear();for(LinearLayout panel:panels)if(panel!=null){BrowserToolbar.clearOwnedViewTree(panel);panel.removeAllViews();}
        status.setText("当前来源设置不可用；未补入默认值");updateSubmit();
    }

    private void renderRows(){
        final Object current=frame;rendering=true;
        try{for(int i=0;i<3;i++){LinearLayout panel=panels[i];BrowserToolbar.clearOwnedViewTree(panel);panel.removeAllViews();for(SettingsSections.Row row:Rows.sections().get(i).rows())panel.addView(controlFor(current,row));}}finally{rendering=false;}updateSubmit();
    }

    private void showPage(int selected){selectedPage=selected;for(int i=0;i<3;i++)if(pages[i]!=null){pages[i].setVisibility(i==selected?View.VISIBLE:View.GONE);pageButtons[i].setBackgroundColor(i==selected?0xffdce1ea:0xfff2f3f7);}}
    private Change pending(String row){if(edits!=null)for(Change change:edits.pendingChanges())if(row.equals(change.rowId()))return change;return null;}
    /** Widget construction happens only after onCreate; no Activity context is captured early. */
    private View controlFor(Object expectedFrame,SettingsSections.Row row){
        boolean active=binding!=null && frame==expectedFrame && edits!=null && !edits.attempted();
        if(row.route()==SettingsSections.Route.DEFAULT_BROWSER)return defaultBrowserControl(expectedFrame,active);
        switch(row.kind()){
            case BOOLEAN:{
                CheckBox box=new CheckBox(this);
                Boolean value=pending(row.id())!=null?pending(row.id()).booleanValue():observed==null?null:observed.value(row.id());
                box.setText(row.title()+(value==null?" · "+SettingsSections.UNKNOWN_TEXT:""));
                box.setChecked(Boolean.TRUE.equals(value));
                box.setEnabled(active && value!=null);
                box.setMinimumHeight(dp(48));
                box.setOnCheckedChangeListener((button,checked)->{
                    if(!isCurrent(expectedFrame))return;
                    if(observed!=null && java.util.Objects.equals(observed.value(row.id()),checked)){edits.discard(row.id());updateSubmit();return;}
                    String problem=edits.setBoolean(row.id(),checked);
                    if(problem!=null)status.setText(row.title()+"：" + problem);
                    else status.setText(row.title()+"：已修改，尚未保存");updateSubmit();
                });
                return box;
            }
            case FIXED_OPTION:{
                LinearLayout line=new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                TextView label=new TextView(this);
                String observedValue=pending(row.id())!=null?pending(row.id()).stringValue():observed==null?null:observed.option(row.id());
                label.setText(row.title());
                label.setMinimumHeight(dp(48));
                line.addView(label,new LinearLayout.LayoutParams(0,dp(48),2));
                Spinner spinner=new Spinner(this);spinner.setContentDescription(row.title());
                final TextView deviceStatus="ui.row.general.device-type".equals(row.id())?new TextView(this):null;
                if(deviceStatus!=null)deviceStatus.setText(deviceTypeStatus(observedValue));
                final int offset=row.findOption(observedValue)==null?1:0;
                List<String> labels=new ArrayList<>();if(offset==1)labels.add(SettingsSections.UNKNOWN_TEXT);
                for(SettingsSections.Option option:row.options())labels.add(option.label());
                android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels);
                spinner.setAdapter(adapter);
                int selected=0;
                if(observedValue!=null)for(int i=0;i<row.options().size();i++)if(row.options().get(i).value().equals(observedValue))selected=i+offset;
                spinner.setSelection(selected);
                spinner.setEnabled(active && row.findOption(observedValue)!=null);
                spinner.setMinimumHeight(dp(48));
                spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                    public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                        if(!isCurrent(expectedFrame) || position<offset || position>=row.options().size()+offset)return;
                        String value=row.options().get(position-offset).value();
                        if(deviceStatus!=null)deviceStatus.setText(deviceTypeStatus(value));
                        if(observed!=null && value.equals(observed.option(row.id()))){edits.discard(row.id());updateSubmit();return;}
                        String problem=edits.chooseOption(row.id(),value);updateSubmit();
                        status.setText(row.title()+"：" + (problem==null?"已修改，尚未保存":problem));
                    }
                    public void onNothingSelected(android.widget.AdapterView<?> parent){}
                });
                line.addView(spinner,new LinearLayout.LayoutParams(0,dp(48),1));
                if(deviceStatus==null)return line;
                LinearLayout deviceBox=new LinearLayout(this);deviceBox.setOrientation(LinearLayout.VERTICAL);deviceBox.addView(line);deviceBox.addView(deviceStatus);
                TextView help=new TextView(this);help.setText("自动按原生最大屏幕像素与物理 DPI 推算对角线，7 英寸及以上按平板呈现，可手动纠正；不按当前分屏宽度或网页 UA 判断。修改后保存才生效，不影响来源、设备信任或管理员权限。");help.setTextSize(12);deviceBox.addView(help);return deviceBox;
            }
            case BOUNDED_TEXT:{
                LinearLayout line=new LinearLayout(this);
                line.setOrientation(LinearLayout.VERTICAL);
                TextView label=new TextView(this);
                String textValue=observed==null?null:observed.text(row.id());
                label.setText(row.title());
                line.addView(label);
                EditText input=new EditText(this);input.setSaveEnabled(false);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
                input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
                input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(row.maxTextChars())});
                input.setEnabled(active && textValue!=null);
                input.setHint(row.title());input.setContentDescription(row.title());input.setText(textDrafts.containsKey(row.id())?textDrafts.get(row.id()):textValue==null?"":textValue);
                input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!isCurrent(expectedFrame))return;String raw=s.toString();textDrafts.put(row.id(),raw);String problem=edits.setText(row.id(),raw);if(problem!=null){edits.discard(row.id());inputProblems.put(row.id(),problem);input.setError(problem);}else{inputProblems.remove(row.id());input.setError(null);if(observed!=null && raw.equals(observed.text(row.id())))edits.discard(row.id());}updateSubmit();}public void afterTextChanged(Editable e){}});
                line.addView(input,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
                return line;
            }
            default:{
                Button button=new Button(this);
                button.setText(row.title());
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

    private String deviceTypeStatus(String option){
        if(option==null)return "设备选择未读取";
        try{return "选择预览："+DeviceWindowState.inspectDevice(this,SettingsRepository.DeviceType.valueOf(option.toUpperCase(java.util.Locale.ROOT))).label();}
        catch(RuntimeException unavailable){return "设备信息未能读取；未据此判断来源或权限";}
    }
    private View defaultBrowserControl(Object expectedFrame,boolean active){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        TextView label=new TextView(this);label.setText(defaultRole==null?"默认浏览器状态未知":defaultRole.label());
        label.setTextSize(14);box.addView(label);
        Button open=new Button(this);open.setText("前往系统选择默认浏览器");
        open.setContentDescription("打开系统默认应用设置");open.setMinimumHeight(dp(48));
        open.setEnabled(active && !defaultHandoffPending);
        BrowserToolbar.Binding originalBinding=binding;
        open.setOnClickListener(v->openDefaultBrowser(expectedFrame,originalBinding,open));
        box.addView(open,new LinearLayout.LayoutParams(-1,dp(52)));return box;
    }
    private void openDefaultBrowser(Object expectedFrame,BrowserToolbar.Binding originalBinding,Button open){
        if(!isCurrent(expectedFrame) || originalBinding==null || binding!=originalBinding || nativeBase==null
                || edits==null || edits.attempted() || saveJob!=null || defaultHandoffPending)return;
        try{
            originalBinding.check();nativeBase.source.requireCurrent();refreshDefaultRole();
            DefaultBrowserFlow.Recommendation request=defaultBrowser.recommendation(DefaultBrowserFlow.RequestKind.OPEN_SYSTEM_DEFAULT_APPS_SETTINGS);
            defaultHandoffPending=true;open.setEnabled(false);
            DefaultBrowserFlow.LaunchObservation result=defaultBrowser.openSystemDefaultApps(this,originalBinding,nativeBase.source,request);
            status.setText(result==DefaultBrowserFlow.LaunchObservation.CALL_RETURNED_NOT_ROLE_ASSIGNED?
                    "已请求系统默认应用页面；角色以返回后实际观察为准":"系统页面提交结果未知；未更换路由或自动设置默认浏览器");
        }catch(RuntimeException unavailable){
            defaultHandoffPending=true;open.setEnabled(false);
            status.setText("系统默认应用入口或原窗口不可用；未自动重试、授予角色或更换目标");
        }
    }
    private void refreshDefaultRole(){
        if(nativeBase==null)return;
        nativeBase.source.requireCurrent();defaultRole=defaultBrowser.observe(DefaultBrowserFlow.observeOwnRole(this));
        nativeBase.source.requireCurrent();
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

    private StartupCoordinator startup;private ComponentGate.BusinessPermit nativePermit;private NativeRead nativeBase;private boolean foreground,sourceLost;private Object readRequest;private NativeSave saveJob;private StartupCoordinator.EntrySubscription sourceWatch;private final Handler nativeMain=new Handler(Looper.getMainLooper());
    private static final class NativeRead {final SettingsRepository.Snapshot value;final long recordRevision;final ComponentGate.DataEpochPin source;NativeRead(SettingsRepository.Snapshot value,long recordRevision,ComponentGate.DataEpochPin source){this.value=value;this.recordRevision=recordRevision;this.source=source;}}
    private static final class NativeSave {final NativeRead base;final SettingsRepository.Snapshot intended;volatile NativeRead verified;volatile boolean done;volatile String failure;volatile WeakReference<SettingsActivity> window=new WeakReference<>(null);NativeSave(NativeRead base,SettingsRepository.Snapshot intended){this.base=base;this.intended=intended;}}
    private static final class Retained {NativeRead base;ComponentGate.BusinessPermit permit;Observed observed;EditSession edits;NativeSave save;int page;int[] scroll=new int[3];Map<String,String> text,problems;}
    @Override protected void onResume(){super.onResume();foreground=true;defaultHandoffPending=false;
        try{if(sourceLost)throw new SecurityException("Settings source retired");if(nativeBase!=null){nativeBase.source.requireCurrent();nativePermit=nativePermit==null?startup.gate().requireBusiness():nativePermit;binding=nativeBinding();actions=nativeActions;if(frame==null)frame=new Object();refreshDefaultRole();renderRows();showPage(selectedPage);}else loadNative();
            WeakReference<SettingsActivity> weak=new WeakReference<>(this);if(sourceWatch==null)sourceWatch=startup.observeNativeEntry(()->{SettingsActivity window=weak.get();if(window!=null && window.foreground)try{if(window.nativeBase!=null)window.nativeBase.source.requireCurrent();else window.startup.gate().requireBusiness();}catch(RuntimeException lost){window.lockNative();}});
            if(saveJob!=null){saveJob.window=new WeakReference<>(this);if(saveJob.done)finishNativeSave(saveJob);}
        }catch(RuntimeException unavailable){lockNative();}
    }
    @Override protected void onStop(){foreground=false;readRequest=null;if(sourceWatch!=null){sourceWatch.close();sourceWatch=null;}if(binding!=null)binding.retire();binding=null;for(ScrollView page:pages)page.setVisibility(View.INVISIBLE);if(saveJob!=null && saveJob.window.get()==this)saveJob.window.clear();super.onStop();}
    private BrowserToolbar.Binding nativeBinding(){return new BrowserToolbar.Binding(startup.gate(),nativePermit,()->{if(!foreground || sourceLost || isDestroyed() || isFinishing() || nativeBase==null)throw new SecurityException("Settings window retired");nativeBase.source.requireCurrent();});}
    private static NativeRead readActual(StartupCoordinator owner,ComponentGate.BusinessPermit permit)throws Exception{SettingsRepository repository=owner.openExistingSettingsOnIo(permit);try(OwnedDataCatalog.View<SettingsRepository.Snapshot> view=repository.read(permit)){if(view.kind()!=PrivateRecordStore.Kind.CURRENT)throw new java.io.IOException("Settings unavailable");NativeRead result=new NativeRead(view.value(permit),view.recordRevision(),owner.gate().captureDataEpoch(permit));result.source.requireCurrent();return result;}}
    private void loadNative(){if(!foreground || sourceLost)return;nativePermit=startup.gate().requireBusiness();Object request=readRequest=new Object();StartupCoordinator owner=startup;ComponentGate.BusinessPermit permit=nativePermit;WeakReference<SettingsActivity> weak=new WeakReference<>(this);Handler handler=nativeMain;status.setText("正在读取实际加密配置");
        try{owner.executeBrowserDataIo(permit,()->{NativeRead result=null;try{result=readActual(owner,permit);}catch(Exception unavailable){/* no defaults or repair */}NativeRead observed=result;handler.post(()->{SettingsActivity window=weak.get();if(window==null || !window.foreground || window.readRequest!=request || window.isDestroyed() || window.isFinishing())return;if(observed==null){window.status.setText("配置未能读取；未填入默认值");return;}try{observed.source.requireCurrent();window.acceptNativeRead(observed);}catch(RuntimeException lost){window.lockNative();}});});}catch(RuntimeException unsubmitted){status.setText("读取未能提交；未自动重试");}
    }
    private void acceptNativeRead(NativeRead read){read.source.requireCurrent();nativeBase=read;refreshDefaultRole();Map<String,Boolean> flags=new LinkedHashMap<>();flags.put("ui.row.general.recent",read.value.newTabRecentEnabled());Map<String,String> options=new LinkedHashMap<>();options.put("ui.row.search.engine",read.value.searchProvider().id());options.put("ui.row.general.device-type",(read.value.deviceType()==null?SettingsRepository.DeviceType.AUTO:read.value.deviceType()).name().toLowerCase(java.util.Locale.ROOT));options.put("ui.row.appearance.background",read.value.newTabBackground()==SettingsRepository.NewTabBackground.EXCLUSIVE?"exclusive":"white");Map<String,String> texts=new LinkedHashMap<>();texts.put("ui.row.general.homepage",read.value.homepage());bind(nativeBinding(),new Observed(flags,options,texts,read.recordRevision),nativeActions);showPage(selectedPage);}
    private final Actions nativeActions=new Actions(){
        public void openRow(SettingsSections.Row row,long revision){if(binding==null)return;binding.check();String message;
            switch(row.route()){
                case SOURCE_SAFETY:message="当前来源已由原生检查接受。\nRoot SHA-256："+startup.compiledSourceIdentity().requireRootSpkiSha256()+"\n此页不接受来源、销毁数据或授予管理员认证。";break;
                case SITE_PERMISSIONS:message="总标签环境之间 Cookie 已隔离；同一总标签的子页共享环境。\n当前有限演示中网站权限请求仍拒绝；有限授权和原生请求界面待继续接通，未用开关伪报授权。";break;
                case SUBAPPLICATIONS:message="ReMusic／ReOutlook 仍按主程序之后的顺序接通。当前配置不会自动启动或停止子应用，未将保存偏好称作运行完成。";break;
                default:message="ReBrowser 2.0.0 开发预览\n当前仅临时浏览演示，尚非完整 v2。";
            }new AlertDialog.Builder(SettingsActivity.this).setTitle(row.title()).setMessage(message).setPositiveButton("知道了",null).show();
        }
        public void onChanges(List<Change> changes,long expectedRevision){saveNative(changes,expectedRevision);}
    };
    /** Native caller translates bounded UI vocabulary into one canonical domain snapshot. */
    static SettingsRepository.Snapshot intendedGeneral(SettingsRepository.Snapshot base,List<Change> changes,long expectedRevision){
        if(base==null || changes==null || changes.isEmpty() || expectedRevision<1)throw new IllegalArgumentException("Missing settings edit");SearchEngines.Provider search=base.searchProvider();SettingsRepository.DeviceType device=base.deviceType();String home=base.homepage();SettingsRepository.NewTabBackground background=base.newTabBackground();boolean recent=base.newTabRecentEnabled();java.util.Set<String> ids=new java.util.HashSet<>();
        for(Change change:changes){if(change==null || change.expectedRevision()!=expectedRevision || !ids.add(change.rowId()))throw new IllegalArgumentException("Settings batch mismatch");switch(change.rowId()){
            case "ui.row.general.homepage":home=change.stringValue();break;
            case "ui.row.search.engine":search=SearchEngines.Provider.fromId(change.stringValue());break;
            case "ui.row.appearance.background":if("exclusive".equals(change.stringValue()))background=SettingsRepository.NewTabBackground.EXCLUSIVE;else if("white".equals(change.stringValue()))background=SettingsRepository.NewTabBackground.WHITE;else throw new IllegalArgumentException("Unknown background");break;
            case "ui.row.general.recent":recent=change.booleanValue();break;
            case "ui.row.general.device-type":device=SettingsRepository.DeviceType.valueOf(change.stringValue().toUpperCase(java.util.Locale.ROOT));break;
            default:throw new IllegalArgumentException("Unregistered settings writer");
        }}return base.withGeneralAndDeviceType(search,home,background,recent,device);
    }
    private void saveNative(List<Change> changes,long expectedRevision){
        if(binding==null || nativeBase==null || saveJob!=null)throw new SecurityException("Native settings save unavailable");binding.check();nativeBase.source.requireCurrent();if(expectedRevision!=nativeBase.recordRevision)throw new SecurityException("Settings base changed");SettingsRepository.Snapshot intended;
        try{intended=intendedGeneral(nativeBase.value,changes,expectedRevision);}catch(IllegalArgumentException invalid){status.setText("主页或设置值不可用；没有写入。请重新读取后编辑");return;}
        NativeSave job=saveJob=new NativeSave(nativeBase,intended);job.window=new WeakReference<>(this);StartupCoordinator owner=startup;ComponentGate.BusinessPermit permit=nativePermit;Handler handler=nativeMain;
        try{owner.executeBrowserDataIo(permit,()->{try{job.base.source.requireCurrent();SettingsRepository repository=owner.openExistingSettingsOnIo(permit);long written;
            try(OwnedDataCatalog.View<SettingsRepository.Snapshot> current=repository.read(permit)){if(current.kind()!=PrivateRecordStore.Kind.CURRENT || current.recordRevision()!=job.base.recordRevision)throw new java.io.IOException("Settings revision changed");job.base.source.requireCurrent();written=repository.compareAndWriteGeneral(current,job.intended,permit);}
            NativeRead verified=readActual(owner,permit);byte[] expectedBytes=SettingsRepository.CODEC.encode(job.intended),actualBytes=SettingsRepository.CODEC.encode(verified.value);try{if(verified.recordRevision!=written || !java.util.Arrays.equals(expectedBytes,actualBytes))throw new java.io.IOException("Settings readback mismatch");job.base.source.requireCurrent();job.verified=verified;}finally{java.util.Arrays.fill(expectedBytes,(byte)0);java.util.Arrays.fill(actualBytes,(byte)0);}
        }catch(Exception unknown){job.failure="保存结果未确认或原记录已变化；没有自动重试、重置或宣称已保存";}finally{job.done=true;handler.post(()->{SettingsActivity window=job.window.get();if(window!=null && window.foreground && !window.isFinishing() && !window.isDestroyed())window.finishNativeSave(job);});}});
        }catch(java.util.concurrent.RejectedExecutionException unsubmitted){job.failure="保存任务未获队列接纳；没有写入，请重新读取后操作";job.done=true;finishNativeSave(job);}
        catch(RuntimeException unknownSubmission){job.failure="保存提交结果未知；保留原操作，不自动重试或释放工作状态";status.setText(job.failure);}
    }
    private void finishNativeSave(NativeSave job){if(saveJob!=job || !job.done || !foreground)return;try{job.base.source.requireCurrent();if(job.verified!=null){job.verified.source.requireCurrent();status.setText("配置已写入并回读确认");finish();}else{status.setText(job.failure);Button reread=new Button(this);reread.setText("重新读取实际配置");reread.setOnClickListener(v->{saveJob=null;loadNative();});panels[selectedPage].addView(reread);}}catch(RuntimeException lost){lockNative();}}
    private void lockNative(){sourceLost=true;readRequest=null;if(binding!=null)binding.retire();unbind();nativeBase=null;status.setText("来源或原生设置窗口已失效；未恢复旧草稿或解锁");for(ScrollView page:pages)page.setVisibility(View.INVISIBLE);}
    @Override public Object onRetainNonConfigurationInstance(){Retained saved=new Retained();saved.base=nativeBase;saved.permit=nativePermit;saved.observed=observed;saved.edits=edits;saved.save=saveJob;saved.page=selectedPage;saved.text=new LinkedHashMap<>(textDrafts);saved.problems=new LinkedHashMap<>(inputProblems);for(int i=0;i<3;i++)saved.scroll[i]=pages[i].getScrollY();return saved;}
    @Override protected void onDestroy(){defaultBrowser.retire();defaultRole=null;binding=null;frame=null;actions=null;edits=null;observed=null;submitControl=null;nativeBase=null;nativePermit=null;textDrafts.clear();inputProblems.clear();if(windowPresentation!=null)windowPresentation.close();BrowserToolbar.clearOwnedViewTree(content);super.onDestroy();}
    private boolean isCurrent(Object expectedFrame){return !rendering && foreground && frame!=null && frame==expectedFrame && binding!=null;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
