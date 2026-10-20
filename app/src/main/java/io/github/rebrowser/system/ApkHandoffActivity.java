package io.github.rebrowser.system;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.rebrowser.ui.BrowserToolbar;
import io.github.rebrowser.core.DomainResult;

/** Native report-only handoff window. A PUBLIC display DTO is NOT a verifier receipt.
 * No Intent extras/path read, APK staging/parsing/verification, URI grant or installer dispatch.
 * Only native owner callbacks can handle the exact one-attempt presentation. No install success.
 */
public final class ApkHandoffActivity extends Activity {
 public enum HandoffState { WAITING_FOR_OWNER, REPORTED_READY, HANDED_OFF_TO_SYSTEM, UNKNOWN }
 public interface Actions {void confirmHandoff(DisplayDescriptor descriptor);void cancel();}
 public static final class DisplayDescriptor {
  private final String displayName,versionText,sha256Hex;private final long byteCount;
  public DisplayDescriptor(String name,String version,long bytes,String digest){displayName=DomainResult.text(name,256,false);versionText=DomainResult.text(version,64,false);if(name.isEmpty() || bytes<1 || bytes>(1L<<33) || digest==null || !digest.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid APK display report");byteCount=bytes;sha256Hex=digest;}
  public String displayName(){return displayName;}public String versionText(){return versionText;}public long byteCount(){return byteCount;}public String sha256Hex(){return sha256Hex;}
 }
 private TextView stateText,detailText;private Button confirm,cancel;private BrowserToolbar.Binding binding;private DisplayDescriptor descriptor;private Actions actions;private Object frame;private boolean attempted;
 @Override protected void onCreate(Bundle saved){super.onCreate(saved);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);stateText=new TextView(this);detailText=new TextView(this);confirm=new Button(this);cancel=new Button(this);confirm.setText("请求交给系统安装器");cancel.setText("取消");confirm.setMinimumHeight(dp(48));cancel.setMinimumHeight(dp(48));root.addView(stateText);root.addView(detailText);root.addView(confirm);root.addView(cancel);setContentView(root);showUnavailable(null);}
 public Object showReport(BrowserToolbar.Binding binding,DisplayDescriptor descriptor,Actions actions){owner();if(binding==null || descriptor==null || actions==null)throw new IllegalArgumentException("Missing native presentation");binding.check();this.binding=binding;this.descriptor=descriptor;this.actions=actions;frame=new Object();attempted=false;confirm.setText("请求交给系统安装器");cancel.setText("取消");Object current=frame;stateText.setText(stateLabel(HandoffState.REPORTED_READY));detailText.setText(descriptor.displayName()+" · "+descriptor.versionText()+" · "+descriptor.byteCount()+"字节 · SHA256 "+descriptor.sha256Hex().substring(0,16)+"…（报告）");confirm.setEnabled(true);cancel.setEnabled(true);confirm.setOnClickListener(v->dispatch(current,true));cancel.setOnClickListener(v->dispatch(current,false));return current;}
 public boolean showHandoffState(BrowserToolbar.Binding expected,Object expectedFrame,HandoffState state){owner();if(expected==null || expected!=binding || expectedFrame==null || expectedFrame!=frame)return false;expected.check();if(expected!=binding || expectedFrame!=frame)return false;stateText.setText(stateLabel(state));return true;}
 public void showUnavailable(String reason){owner();binding=null;descriptor=null;actions=null;frame=null;attempted=true;BrowserToolbar.clearOwnedViewTree(confirm);BrowserToolbar.clearOwnedViewTree(cancel);if(confirm!=null)confirm.setEnabled(false);if(cancel!=null)cancel.setEnabled(false);if(stateText!=null)stateText.setText(stateLabel(HandoffState.UNKNOWN));if(detailText!=null)detailText.setText(reason==null?"等待原生文件报告":BrowserToolbar.boundedStatusReason(reason));}
 private void dispatch(Object expected,boolean approve){BrowserToolbar.Binding captured=binding;DisplayDescriptor exact=descriptor;Actions callback=actions;if(captured==null || exact==null || callback==null || expected!=frame || attempted)return;try{captured.check();}catch(RuntimeException stale){showUnavailable(null);return;}if(captured!=binding || expected!=frame)return;attempted=true;confirm.setEnabled(false);cancel.setEnabled(false);BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{if(approve)callback.confirmHandoff(exact);else callback.cancel();});if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected && binding==captured)stateText.setText(stateLabel(HandoffState.UNKNOWN));}
 public static String stateLabel(HandoffState state){if(state==null)throw new IllegalArgumentException("Missing handoff observation");switch(state){case WAITING_FOR_OWNER:return "等待原生校验器";case REPORTED_READY:return "原生已提供报告，待控制器重新校验后交接";case HANDED_OFF_TO_SYSTEM:return "原生报告已交系统（安装结果未确认）";default:return "交接状态未知";}}
 @Override protected void onDestroy(){binding=null;descriptor=null;actions=null;frame=null;BrowserToolbar.clearOwnedViewTree(findViewById(android.R.id.content));super.onDestroy();}
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}private static void owner(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Native window owner thread required");}
}
