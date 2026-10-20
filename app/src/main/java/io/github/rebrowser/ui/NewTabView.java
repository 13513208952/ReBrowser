package io.github.rebrowser.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.rebrowser.browser.SearchEngines;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Native new-tab surface: an ALREADY-PROVIDED in-memory background, the four fixed search-engine
 * entries and a bounded shortcut row. It never loads a URL, reads a bookmark store or accepts an
 * external image URI; buttons report exact user INTENT only, which the owning window must
 * re-authorize against the current source/epoch/target.
 */
public final class NewTabView extends LinearLayout {
    public static final int MAX_SHORTCUTS = 12;
    public static final int MAX_TITLE_CHARS = 120;
    public static final int MAX_HOST_CHARS = 255;

    public interface Actions {
        void search(SearchEngines.Provider provider);
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

    public static final class Model {
        private final List<Shortcut> shortcuts;
        public Model(List<Shortcut> shortcuts){
            if(shortcuts==null || shortcuts.size()>MAX_SHORTCUTS)throw new IllegalArgumentException("Invalid shortcut count");
            Set<Object> tokens=Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            List<Shortcut> copy=new ArrayList<>();
            for(Shortcut shortcut:shortcuts){if(shortcut==null)throw new IllegalArgumentException("Missing shortcut");if(!tokens.add(shortcut.token()))throw new IllegalArgumentException("Duplicate shortcut token");copy.add(shortcut);}
            this.shortcuts=Collections.unmodifiableList(copy);
        }
        public List<Shortcut> shortcuts(){return shortcuts;}
        public boolean empty(){return shortcuts.isEmpty();}
        public static List<SearchEngines.Provider> providers(){
            return Collections.unmodifiableList(java.util.Arrays.asList(SearchEngines.Provider.BING,SearchEngines.Provider.BAIDU,SearchEngines.Provider.DUCKDUCKGO,SearchEngines.Provider.GOOGLE));
        }
        public static String providerLabel(SearchEngines.Provider provider){
            if(provider==null)throw new IllegalArgumentException("Missing provider");
            switch(provider){case BING:return "必应";case BAIDU:return "百度";case DUCKDUCKGO:return "DuckDuckGo";case GOOGLE:return "Google";default:throw new IllegalArgumentException("Unsupported provider");}
        }
    }

    private final ImageView background;
    private final LinearLayout searchRow,shortcutRow;
    private final TextView emptyHint;
    private final List<View> rendered=new ArrayList<>();
    private Object frame;
    private BrowserToolbar.Binding binding;
    private Actions actions;

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
        for(SearchEngines.Provider provider:Model.providers()){
            Button button=new Button(getContext());
            button.setText(Model.providerLabel(provider));
            button.setMinimumHeight(dp(48));
            button.setContentDescription("使用"+Model.providerLabel(provider)+"搜索");
            button.setOnClickListener(v->run(current,()->actions.search(provider)));
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
        manage.setOnClickListener(v->run(current,()->actions.manageShortcuts()));
        shortcutRow.addView(manage,new LayoutParams(LayoutParams.MATCH_PARENT,dp(48)));
        rendered.add(manage);
    }

    public void unbind(){BrowserToolbar.main();clearBinding(true);}
    private void clearBinding(boolean discardArtwork){
        frame=null;binding=null;actions=null;
        for(View view:rendered)BrowserToolbar.clearOwnedViewTree(view);
        searchRow.removeAllViews();shortcutRow.removeAllViews();rendered.clear();setContentDescription(null);
        setEmpty(true);
        if(discardArtwork)background.setImageDrawable(null); // release retired private artwork
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
