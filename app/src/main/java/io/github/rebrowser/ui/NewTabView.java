package io.github.rebrowser.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.InputFilter;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import io.github.rebrowser.browser.SearchEngines;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Native new-tab surface: an ALREADY-PROVIDED in-memory background, a real bounded search input
 * with the four fixed providers, and a bounded shortcut row. Only an explicit user submit emits
 * provider+raw keyword as INTENT (never loadUri); blank/oversized/broken UTF-16 input is rejected
 * locally, and the keyboard/button share one in-flight guard so a single submit is not doubled.
 */
public final class NewTabView extends LinearLayout {
    public static final int MAX_SHORTCUTS = 12;
    public static final int MAX_TITLE_CHARS = 120;
    public static final int MAX_HOST_CHARS = 255;
    public static final int MAX_QUERY_CHARS = 8192;

    public interface Actions {
        void search(SearchEngines.Provider provider,String rawQuery);
        void openShortcut(Shortcut shortcut);
        void manageShortcuts();
    }

    /** Display-only shortcut descriptor; the token is a process-local handoff, not a load grant. */
    public static final class Shortcut {
        private final Object token;private final String title,hostLabel;
        public Shortcut(Object token,String title,String hostLabel){
            if(token==null || title==null || hostLabel==null)throw new IllegalArgumentException("Incomplete shortcut");
            this.token=token;this.title=text(title,MAX_TITLE_CHARS);this.hostLabel=host(hostLabel);
        }
        public Object token(){return token;}
        public String title(){return title;}
        public String hostLabel(){return hostLabel;}
    }

    /** Bounded query validation shared by the IME path and the explicit button path. */
    public static final class Query {
        public static String require(String rawQuery){
            if(rawQuery==null)throw new IllegalArgumentException("Missing search text");
            if(rawQuery.length()>MAX_QUERY_CHARS)throw new IllegalArgumentException("搜索内容过长");
            String trimmed=rawQuery.trim();
            if(trimmed.isEmpty())throw new IllegalArgumentException("搜索内容为空");
            text(rawQuery,MAX_QUERY_CHARS);
            return rawQuery;
        }
    }

    public static final class Model {
        private final List<Shortcut> shortcuts;private final boolean manageOffered;
        public Model(List<Shortcut> shortcuts){this(shortcuts,true);}
        public Model(List<Shortcut> shortcuts,boolean manageOffered){
            if(shortcuts==null || shortcuts.size()>MAX_SHORTCUTS)throw new IllegalArgumentException("Invalid shortcut count");
            Set<Object> tokens=Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            List<Shortcut> copy=new ArrayList<>();
            for(Shortcut shortcut:shortcuts){if(shortcut==null)throw new IllegalArgumentException("Missing shortcut");if(!tokens.add(shortcut.token()))throw new IllegalArgumentException("Duplicate shortcut token");copy.add(shortcut);}
            this.shortcuts=Collections.unmodifiableList(copy);this.manageOffered=manageOffered;
        }
        public List<Shortcut> shortcuts(){return shortcuts;}
        public boolean empty(){return shortcuts.isEmpty();}
        public boolean manageOffered(){return manageOffered;}
        public static List<SearchEngines.Provider> providers(){
            return Collections.unmodifiableList(java.util.Arrays.asList(SearchEngines.Provider.BING,SearchEngines.Provider.BAIDU,SearchEngines.Provider.DUCKDUCKGO,SearchEngines.Provider.GOOGLE));
        }
        public static String providerLabel(SearchEngines.Provider provider){
            if(provider==null)throw new IllegalArgumentException("Missing provider");
            switch(provider){case BING:return "必应";case BAIDU:return "百度";case DUCKDUCKGO:return "DuckDuckGo";case GOOGLE:return "Google";default:throw new IllegalArgumentException("Unsupported provider");}
        }
    }

    private ImageView background;
    private TextView emptyHint;
    private EditText searchInput;
    private Spinner providerSpinner;private Button submitButton;
    private LinearLayout searchRow,shortcutRow;
    private final List<View> rendered=new ArrayList<>();
    private Object frame;
    private BrowserToolbar.Binding binding;
    private Actions actions;
    private boolean submitInFlight;

    public NewTabView(Context context){
        super(context);
        BrowserToolbar.main();
        setOrientation(VERTICAL);
        background=new ImageView(context);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setContentDescription("已提供的新标签页背景");
        addView(background,new LayoutParams(LayoutParams.MATCH_PARENT,dp(220)));
        TextView title=new TextView(context);
        title.setText("新标签页");
        title.setTextSize(22);
        addView(title,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        LinearLayout searchBox=new LinearLayout(context);
        searchBox.setOrientation(HORIZONTAL);
        providerSpinner=new Spinner(context);
        List<String> labels=new ArrayList<>();
        for(SearchEngines.Provider provider:Model.providers())labels.add(Model.providerLabel(provider));
        providerSpinner.setAdapter(new android.widget.ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,labels));
        providerSpinner.setMinimumHeight(dp(48));
        providerSpinner.setContentDescription("搜索提供方");
        searchBox.addView(providerSpinner,new LayoutParams(dp(112),dp(48)));
        searchInput=new EditText(context);searchInput.setSaveEnabled(false);searchInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        searchInput.setSingleLine(true);
        searchInput.setHint("搜索或输入网址");
        searchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_QUERY_CHARS)});
        searchBox.addView(searchInput,new LayoutParams(0,dp(48),1));
        Button submit=new Button(context);submitButton=submit;
        submit.setText("搜索");
        submit.setMinimumHeight(dp(48));
        submit.setContentDescription("使用所选提供方提交搜索");
        submit.setOnClickListener(v->submitSearch());
        searchBox.addView(submit,new LayoutParams(dp(72),dp(48)));
        addView(searchBox,new LayoutParams(LayoutParams.MATCH_PARENT,dp(48)));
        searchInput.setOnEditorActionListener((view,action,event)->{
            boolean ime=action==EditorInfo.IME_ACTION_SEARCH;
            boolean enter=event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER && event.getAction()==KeyEvent.ACTION_UP;
            if(!ime && !enter)return false;
            submitSearch();
            return true;
        });
        searchRow=new LinearLayout(context);
        searchRow.setOrientation(HORIZONTAL);
        addView(searchRow,new LayoutParams(LayoutParams.MATCH_PARENT,dp(56)));
        emptyHint=new TextView(context);
        emptyHint.setText("未提供收藏快捷方式");
        addView(emptyHint,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        shortcutRow=new LinearLayout(context);
        shortcutRow.setOrientation(VERTICAL);
        addView(shortcutRow,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        setEmpty(true);
    }

    /** Only caller-owned in-memory artwork; no URI, file or network access happens here. */
    public void bindBackground(Drawable providedInMemory){
        BrowserToolbar.main();
        background.setImageDrawable(providedInMemory);
    }

    public void bind(BrowserToolbar.Binding binding,Model model,Actions actions){
        BrowserToolbar.main();
        if(binding==null || model==null || actions==null)throw new IllegalArgumentException("Missing new-tab binding");
        binding.check();
        clearBinding(false); // rebind preserves the explicitly supplied background
        this.binding=binding;this.actions=actions;
        final Object current=new Object();frame=current;
        submitInFlight=false;searchInput.setEnabled(true);submitButton.setEnabled(true);
        for(SearchEngines.Provider provider:Model.providers()){
            Button button=new Button(getContext());
            button.setText(Model.providerLabel(provider));
            button.setMinimumHeight(dp(48));
            button.setContentDescription("选择"+Model.providerLabel(provider));
            button.setOnClickListener(v->{
                if(frame!=current)return;
                providerSpinner.setSelection(Model.providers().indexOf(provider));
            });
            searchRow.addView(button,new LayoutParams(0,LayoutParams.MATCH_PARENT,1));
            rendered.add(button);
        }
        setEmpty(model.empty());
        for(Shortcut shortcut:model.shortcuts()){
            Button button=new Button(getContext());
            button.setText(shortcut.title());
            button.setMinimumHeight(dp(48));
            button.setContentDescription("打开收藏：" + shortcut.title() + "（" + shortcut.hostLabel() + "）");
            button.setOnClickListener(v->run(current,()->actions.openShortcut(shortcut)));
            shortcutRow.addView(button,new LayoutParams(LayoutParams.MATCH_PARENT,dp(48)));
            rendered.add(button);
        }
        Button manage=new Button(getContext());
        manage.setText("管理收藏");
        manage.setMinimumHeight(dp(48));
        manage.setEnabled(model.manageOffered());
        manage.setContentDescription(model.manageOffered()?"管理收藏":"收藏管理未由原生提供");
        manage.setOnClickListener(model.manageOffered()?v->run(current,()->actions.manageShortcuts()):null);
        shortcutRow.addView(manage,new LayoutParams(LayoutParams.MATCH_PARENT,dp(48)));
        rendered.add(manage);
    }

    /** Explicit submit only; blank/oversized/broken UTF-16 is explained locally, never sent. */
    private void submitSearch(){
        BrowserToolbar.Binding captured=binding;
        if(captured==null || actions==null)return;
        final Object expected=frame;final Actions exactActions=actions;
        if(submitInFlight)return; // keyboard+button double press cannot send twice
        final String raw;
        try{raw=Query.require(searchInput.getText()==null?null:searchInput.getText().toString());}
        catch(IllegalArgumentException invalid){searchInput.setError(invalid.getMessage());return;}
        int position=providerSpinner.getSelectedItemPosition();
        if(position<0 || position>=Model.providers().size()){searchInput.setError("提供方未知");return;}
        final SearchEngines.Provider provider=Model.providers().get(position);
        try{captured.check();}catch(RuntimeException stale){return;}
        if(frame!=expected || binding!=captured || actions!=exactActions)return;
        submitInFlight=true;searchInput.setEnabled(false);submitButton.setEnabled(false);setContentDescription("搜索已请求，等待原生刷新");
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(frame!=expected || captured!=binding || actions!=exactActions)return;
            exactActions.search(provider,raw);
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)
            setContentDescription("搜索请求结果未知，请核对当前页面");
    }

    public void unbind(){BrowserToolbar.main();clearBinding(true);}

    private void clearBinding(boolean discardArtwork){
        frame=null;binding=null;actions=null;submitInFlight=false;
        for(View view:rendered)BrowserToolbar.clearOwnedViewTree(view);
        searchRow.removeAllViews();shortcutRow.removeAllViews();rendered.clear();setContentDescription(null);
        if(searchInput!=null){searchInput.setText("");searchInput.setError(null);}
        setEmpty(true);
        if(discardArtwork && background!=null)background.setImageDrawable(null); // release retired private artwork
    }
    @Override protected void onDetachedFromWindow(){unbind();super.onDetachedFromWindow();}

    private void run(Object expected,Runnable intent){
        BrowserToolbar.Binding captured=binding;Actions callback=actions;
        if(captured==null || callback==null || frame!=expected)return;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(frame!=expected || captured!=binding || callback!=actions)return;
            intent.run();
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)setContentDescription("操作结果未知，请核对当前页面");
    }
    private void setEmpty(boolean empty){emptyHint.setVisibility(empty?VISIBLE:GONE);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static String text(String value,int max){
        if(value.isEmpty() || value.length()>max)throw new IllegalArgumentException("Unbounded new-tab text");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw new IllegalArgumentException("Control in new-tab text");if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}
        return value;
    }
    private static String host(String value){
        if(value.isEmpty() || value.length()>MAX_HOST_CHARS)throw new IllegalArgumentException("Unbounded shortcut host label");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c<0x21 || c>0x7e)throw new IllegalArgumentException("Shortcut host label must be printable ASCII");}
        return value;
    }
}
