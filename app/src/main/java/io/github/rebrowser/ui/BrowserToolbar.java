package io.github.rebrowser.ui;

import android.content.Context;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import io.github.rebrowser.R;
import android.widget.EditText;
import android.widget.LinearLayout;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.browser.SearchEngines;

/** Native address/actions only; callbacks still need current domain/actor/target authorization. */
public final class BrowserToolbar extends LinearLayout {
    /** Java callback return/exception only, NEVER domain success or actual work-end proof. */
    public enum IntentOutcome { CALLBACK_RETURNED, CALLBACK_UNKNOWN }
    public static IntentOutcome invokeNativeIntent(Runnable exactNativeCallback){if(exactNativeCallback==null)throw invalid();try{exactNativeCallback.run();return IntentOutcome.CALLBACK_RETURNED;}catch(RuntimeException unknown){return IntentOutcome.CALLBACK_UNKNOWN;}}
    /** Clears ONLY the caller-owned presentation subtree, including listeners/private recycled text.
     * This is not session/window close, data deletion, owner consent or a task-end receipt. */
    public static void clearOwnedViewTree(View root){
        main();if(root==null)return;java.util.ArrayDeque<View> pending=new java.util.ArrayDeque<>();pending.add(root);int count=0;
        while(!pending.isEmpty()){View view=pending.removeFirst();if(++count>10000)throw new IllegalStateException("Owned UI subtree too large");
            if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)pending.addLast(group.getChildAt(i));}
            if(view instanceof android.widget.AdapterView){android.widget.AdapterView<?> adapter=(android.widget.AdapterView<?>)view;if(!(adapter instanceof android.widget.Spinner))adapter.setOnItemClickListener(null); // Spinner forbids even a null item-click listener
                adapter.setOnItemLongClickListener(null);adapter.setOnItemSelectedListener(null);}
            else view.setOnClickListener(null); // AdapterView forbids even setOnClickListener(null)
            view.setOnLongClickListener(null);view.setOnTouchListener(null);view.setContentDescription(null);
            if(view instanceof android.widget.CompoundButton)((android.widget.CompoundButton)view).setOnCheckedChangeListener(null);
            if(view instanceof android.widget.TextView){android.widget.TextView text=(android.widget.TextView)view;text.setOnEditorActionListener(null);text.setText("");}
            if(view instanceof android.widget.ImageView)((android.widget.ImageView)view).setImageDrawable(null);
            if(view instanceof android.widget.SeekBar)((android.widget.SeekBar)view).setOnSeekBarChangeListener(null);
        }
    }
    public static String boundedStatusReason(String reason){return reason==null?"":io.github.rebrowser.core.DomainResult.text(reason,200,false);}
    public interface NativeWindowGuard { void checkCurrent(); }
    public interface Actions { void submit(String originalInput);void back();void reload();void workspaces();void tabs();void menu();default void home(){submit(SearchEngines.defaultHome().url());}default void addChild(){tabs();} }
    /** Exact native presentation binding. Never decode a permit/guard from UI/IPC data. */
    public static final class Binding {
        private final ComponentGate gate;private final ComponentGate.BusinessPermit permit;private final NativeWindowGuard window;private final Thread thread=Thread.currentThread();
        private boolean retired,checking;
        public Binding(ComponentGate gate,ComponentGate.BusinessPermit permit,NativeWindowGuard nativeWindow){if(gate==null || nativeWindow==null)throw invalid();this.gate=gate;this.permit=permit;window=nativeWindow;check();}
        public void check(){thread();if(retired || checking)throw new SecurityException("Stale native view binding");gate.checkCurrent(permit);checking=true;try{window.checkCurrent();}finally{checking=false;}if(retired)throw new SecurityException("Retired native view binding");gate.checkCurrent(permit);}
        public void retire(){thread();retired=true;}
        private void thread(){if(Thread.currentThread()!=thread)throw new IllegalStateException("Native view binding owner thread required");}
    }
    /** Keeps a native-reported display value separate from explicitly edited user input. */
    public static final class AddressState {
        private String observed="",draft="";private boolean editing;
        public void reportDisplay(String text){observed=bounded(text);if(!editing)draft=observed;}
        public void edit(String original){draft=bounded(original);editing=true;}
        public String visible(){return editing?draft:observed;}
        public void abandonEdit(){editing=false;draft=observed;}
        public void clear(){observed="";draft="";editing=false;}
        private static String bounded(String value){if(value==null || value.length()>SearchEngines.MAX_INPUT_CHARS)throw invalid();for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw invalid();}else if(Character.isLowSurrogate(c))throw invalid();}return value;}
    }
    private final EditText address;private final ImageButton home;private final android.widget.TextView addChild,workspace,menu;private final View blankSpacer;private final AddressState state=new AddressState();
    private Binding binding;private Actions actions;private boolean nativeNewTab,photo;
    public BrowserToolbar(Context context){
        super(context);main();setOrientation(HORIZONTAL);setGravity(android.view.Gravity.CENTER_VERTICAL);setPadding(dp(6),dp(6),dp(6),dp(6));setElevation(dp(3));
        // These glyphs, dimensions and order are inherited from the approved native toolbar.
        // Owner-approved refinement ONLY: same placement/46x50dp touch area/action, stable
        // vector geometry instead of a font-dependent house glyph. No navigation semantics change.
        home=new ImageButton(context);home.setImageResource(R.drawable.ic_rb_home);home.setContentDescription("打开当前子标签页的主页");home.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);TypedValue feedback=new TypedValue();if(context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,feedback,true))home.setBackgroundResource(feedback.resourceId);home.setPadding(dp(11),dp(13),dp(11),dp(13));addView(home,new LayoutParams(dp(46),dp(50)));home.setOnClickListener(v->dispatch(6));
        address=new EditText(context);address.setSaveEnabled(false);address.setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO);address.setSingleLine(true);address.setTextSize(14);address.setTextColor(0xff222730);address.setHintTextColor(0xff646b76);address.setHint("搜索或输入网址");address.setContentDescription("地址与搜索");address.setSelectAllOnFocus(true);address.setPadding(dp(16),0,dp(12),0);address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);address.setImeOptions(EditorInfo.IME_ACTION_GO);address.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SearchEngines.MAX_INPUT_CHARS)});android.graphics.drawable.GradientDrawable pill=new android.graphics.drawable.GradientDrawable();pill.setColor(0xffeff1f6);pill.setCornerRadius(dp(24));address.setBackground(pill);LayoutParams ap=new LayoutParams(0,dp(48),1);ap.setMargins(dp(2),0,dp(3),0);addView(address,ap);
        blankSpacer=new View(context);addView(blankSpacer,new LayoutParams(0,dp(48),1));blankSpacer.setVisibility(GONE);
        addChild=icon("＋","新建并打开子标签页",27,40,48);addChild.setOnClickListener(v->dispatch(7));
        workspace=icon("1","管理总标签页",19,38,38);LayoutParams counter=(LayoutParams)workspace.getLayoutParams();counter.rightMargin=dp(3);workspace.setLayoutParams(counter);workspace.setOnClickListener(v->dispatch(3));
        menu=icon("⋮","ReBrowser 菜单",25,42,50);menu.setOnClickListener(v->dispatch(5));
        address.setOnEditorActionListener((view,action,event)->{if(action!=EditorInfo.IME_ACTION_GO && !(event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER && event.getAction()==KeyEvent.ACTION_UP))return false;dispatch(0);return true;});appearance(false);setControls(false);
    }
    public void bind(Binding current,Actions nativeActions,String displayUrl,boolean canGoBack){bind(current,nativeActions,displayUrl,canGoBack,false,1);}
    public void bind(Binding current,Actions nativeActions,String displayUrl,boolean canGoBack,boolean nativeNewTab,int workspaceCount){main();if(current==null || nativeActions==null || workspaceCount<1 || workspaceCount>64)throw invalid();current.check();if(binding!=current){unbind();binding=current;}actions=nativeActions;state.reportDisplay(displayUrl==null?"":displayUrl);if(!address.hasFocus())address.setText(state.visible());this.nativeNewTab=nativeNewTab;address.setVisibility(nativeNewTab?GONE:VISIBLE);blankSpacer.setVisibility(nativeNewTab?VISIBLE:GONE);workspace.setText(Integer.toString(workspaceCount));workspace.setContentDescription("管理总标签页，共"+workspaceCount+"个");appearance(photo);setControls(true);}
    public void appearance(boolean photo){main();this.photo=photo;boolean light=photo && nativeNewTab;int foreground=light?0xffffffff:0xff31353d;setBackgroundColor(light?0x00000000:0xffffffff);for(android.widget.TextView v:new android.widget.TextView[]{addChild,workspace,menu})v.setTextColor(foreground);home.setImageTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{foreground,(foreground&0x00ffffff)|0x61000000}));android.graphics.drawable.GradientDrawable counter=new android.graphics.drawable.GradientDrawable();counter.setColor(0x00000000);counter.setCornerRadius(dp(8));counter.setStroke(dp(2),light?0xffffffff:0xff444952);workspace.setBackground(counter);setElevation(light?0:dp(3));}
    public View menuAnchor(){return menu;}
    public boolean hasInputFocus(){main();return address.hasFocus();}
    public boolean dismissInput(){main();if(!address.hasFocus())return false;address.clearFocus();android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);if(ime!=null)ime.hideSoftInputFromWindow(address.getWindowToken(),0);state.abandonEdit();address.setText(state.visible());return true;}
    public void unbind(){main();binding=null;actions=null;state.clear();if(address!=null){address.setText("");address.setError(null);address.clearFocus();}setControls(false);}
    private void dispatch(int kind){Binding captured=binding;Actions callback=actions;if(captured==null || callback==null)return;try{captured.check();if(captured!=binding || callback!=actions)return;IntentOutcome outcome=invokeNativeIntent(()->{if(kind==0){state.edit(address.getText().toString());String original=state.visible();dismissInput();callback.submit(original);}else if(kind==3)callback.workspaces();else if(kind==5)callback.menu();else if(kind==6)callback.home();else if(kind==7)callback.addChild();});if(outcome==IntentOutcome.CALLBACK_UNKNOWN && binding==captured && actions==callback)address.setError("操作结果未知，请核对当前状态");}catch(SecurityException stale){unbind();}catch(RuntimeException unknown){address.setError("操作结果未知，请核对当前状态");}}
    private android.widget.TextView icon(String text,String description,int size,int width,int height){android.widget.TextView view=new android.widget.TextView(getContext());view.setText(text);view.setTextSize(size);view.setGravity(android.view.Gravity.CENTER);view.setContentDescription(description);view.setBackgroundResource(android.R.drawable.list_selector_background);addView(view,new LayoutParams(dp(width),dp(height)));return view;}
    private void setControls(boolean enabled){for(View v:new View[]{home,addChild,workspace,menu,address})if(v!=null)v.setEnabled(enabled);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native UI owner thread required");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native toolbar binding");}
}
