package io.github.rebrowser.ui;

import android.content.Context;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.browser.SearchEngines;

/** Native address/actions only; callbacks still need current domain/actor/target authorization. */
public final class BrowserToolbar extends LinearLayout {
    public interface NativeWindowGuard { void checkCurrent(); }
    public interface Actions { void submit(String originalInput);void back();void reload();void workspaces();void tabs();void menu(); }
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
    private final EditText address;private final Button back,reload,workspace,tabs,menu;private final AddressState state=new AddressState();
    private Binding binding;private Actions actions;
    public BrowserToolbar(Context context){
        super(context);main();setOrientation(VERTICAL);
        LinearLayout controls=new LinearLayout(context);controls.setOrientation(HORIZONTAL);
        back=button(controls,"返回");reload=button(controls,"刷新");workspace=button(controls,"环境");tabs=button(controls,"子标签");menu=button(controls,"菜单");addView(controls,new LayoutParams(LayoutParams.MATCH_PARENT,dp(48)));
        address=new EditText(context);address.setSingleLine(true);address.setHint("搜索或输入网址");address.setContentDescription("地址与搜索");address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);address.setImeOptions(EditorInfo.IME_ACTION_GO);address.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SearchEngines.MAX_INPUT_CHARS)});addView(address,new LayoutParams(LayoutParams.MATCH_PARENT,dp(48)));
        address.setOnEditorActionListener((view,action,event)->{if(action!=EditorInfo.IME_ACTION_GO && !(event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER && event.getAction()==KeyEvent.ACTION_UP))return false;dispatch(0);return true;});
        back.setOnClickListener(v->dispatch(1));reload.setOnClickListener(v->dispatch(2));workspace.setOnClickListener(v->dispatch(3));tabs.setOnClickListener(v->dispatch(4));menu.setOnClickListener(v->dispatch(5));setControls(false);
    }
    public void bind(Binding current,Actions nativeActions,String displayUrl,boolean canGoBack){main();if(current==null || nativeActions==null)throw invalid();current.check();if(binding!=current){unbind();binding=current;}actions=nativeActions;state.reportDisplay(displayUrl==null?"":displayUrl);if(!address.hasFocus())address.setText(state.visible());setControls(true);back.setEnabled(canGoBack);}
    public void unbind(){main();binding=null;actions=null;state.clear();if(address!=null){address.setText("");address.setError(null);address.clearFocus();}setControls(false);}
    private void dispatch(int kind){Binding captured=binding;Actions callback=actions;if(captured==null || callback==null)return;try{captured.check();if(captured!=binding || callback!=actions)return;if(kind==0){state.edit(address.getText().toString());callback.submit(state.visible());address.clearFocus();}else if(kind==1)callback.back();else if(kind==2)callback.reload();else if(kind==3)callback.workspaces();else if(kind==4)callback.tabs();else callback.menu();}catch(SecurityException stale){unbind();}catch(RuntimeException unknown){address.setError("操作结果未知，请核对当前状态");}}
    private Button button(LinearLayout parent,String text){Button button=new Button(getContext());button.setText(text);button.setMinWidth(dp(48));button.setMinimumHeight(dp(48));parent.addView(button,new LayoutParams(0,LayoutParams.MATCH_PARENT,1));return button;}
    private void setControls(boolean enabled){for(View v:new View[]{back,reload,workspace,tabs,menu,address})if(v!=null)v.setEnabled(enabled);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native UI owner thread required");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid native toolbar binding");}
}
