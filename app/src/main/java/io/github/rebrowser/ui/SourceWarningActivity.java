package io.github.rebrowser.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import io.github.rebrowser.continuity.*;

/** Native exact four-warning consumer. No Intent/source/boolean consent injection, automatic
 * click, OS prompt at launch, business unlock, key generation or erasure success inference.
 * Bootstrap must explicitly bind actual native baseline/candidate/current guard + real erasure
 * owner. Unbound/failed flows stay locked; this Activity is not yet a manifest recovery route. */
public final class SourceWarningActivity extends Activity {
    public interface NativeFinalConsentHandler {void acceptOnce(ContinuityCoordinator exactFlow,ContinuityCoordinator.Consent nativeConsent);}
    private TextView warning,status;private Button confirm,cancel;private OwnerAuthentication authentication;private ContinuityCoordinator flow;private ContinuityCoordinator.OwnerAttempt pending;private NativeFinalConsentHandler erasure;private boolean bound,destroyed,waitingForeground,inEvent,finalHandoff;
    private final OwnerAuthentication.ContinuityListener observer=()->{if(!destroyed)observeForegroundThenRender();};
    @Override protected void onCreate(Bundle state){super.onCreate(state);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(20*getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);warning=new TextView(this);warning.setText(ContinuityState.Confirmation.TRUST_SOURCE.warning());warning.setTextSize(20);root.addView(warning);status=new TextView(this);root.addView(status);confirm=new Button(this);confirm.setText("继续");confirm.setMinimumHeight(Math.round(48*getResources().getDisplayMetrics().density));root.addView(confirm);cancel=new Button(this);cancel.setText("取消并保持锁定");cancel.setOnClickListener(v->{retire();finish();});root.addView(cancel);setContentView(root);unavailable("未绑定原生来源核验；保持锁定");}
    /** Native bootstrap only. Context is metadata; actual signer/Root/owned-window checks happen
     * inside OwnerAuthentication.newContinuityFlow, never from Activity extras. */
    public void bindNativeRecovery(ContinuityCoordinator.Context actualNativeMetadata,ContinuityCoordinator.CurrentSourceGuard actualCurrentSourceGuard,NativeFinalConsentHandler actualErasureOwner)throws SigningIdentity.Unavailable{
        owner();if(destroyed || bound || warning==null || actualErasureOwner==null)throw new IllegalStateException("Native recovery window unavailable");bound=true;authentication=new OwnerAuthentication(this);flow=authentication.newContinuityFlow(actualNativeMetadata,actualCurrentSourceGuard);erasure=actualErasureOwner;render();
    }
    private void clicked(ContinuityCoordinator expected,ContinuityCoordinator.View captured){owner();if(destroyed || inEvent || flow!=expected || finalHandoff)return;inEvent=true;confirm.setEnabled(false);try{if(captured.step().requiresSeparateAndroidOwnerVerification()){pending=expected.beginOwnerConfirmation(captured.revision(),captured.step());OwnerAuthentication.Start start=authentication.beginContinuityVerification(pending,observer);waitingForeground=start==OwnerAuthentication.Start.WAIT_FOREGROUND;}else expected.confirmOrdinary(captured.revision(),captured.step());}catch(RuntimeException unknown){retire();unavailable("来源或本人验证不可用；保持锁定");}finally{inEvent=false;}observeForegroundThenRender();}
    private void observeForegroundThenRender(){if(destroyed || inEvent || flow==null)return;inEvent=true;try{if(waitingForeground && pending!=null && hasWindowFocus()){OwnerAuthentication.Start start=authentication.beginContinuityVerification(pending,observer);waitingForeground=start==OwnerAuthentication.Start.WAIT_FOREGROUND;}authentication.tryConsumeContinuityVerification();}catch(RuntimeException unknown){retire();unavailable("来源或本人验证结果不可用；保持锁定");}finally{inEvent=false;}render();}
    private void render(){owner();if(destroyed || flow==null || finalHandoff)return;ContinuityCoordinator expected=flow;ContinuityCoordinator.View view=expected.view();warning.setText(view.step().warning());confirm.setOnClickListener(null);confirm.setEnabled(false);switch(view.stage()){
        case CONFIRMING:pending=null;waitingForeground=false;status.setText(view.step().requiresSeparateAndroidOwnerVerification()?"此步骤必须单独通过 Android 本人验证；取消不会清理数据":"来源仍被锁定；此确认不会直接解锁");confirm.setEnabled(true);confirm.setOnClickListener(v->clicked(expected,view));break;
        case WAITING_OWNER:case WAITING_TICKET:status.setText("等待本次独立本人验证；不以超时或返回代替真实结果");break;
        case CONSENT_AVAILABLE:finalHandoff=true;confirm.setOnClickListener(null);status.setText("已请求原生核验与清理；完成前仍保持锁定");try{erasure.acceptOnce(expected,expected.availableConsent());}catch(RuntimeException unknown){unavailable("核验清理结果未知；保持锁定，不自动重试");}break;
        case CONSENT_CONSUMED:status.setText("原生清理协调器已接收；不代表清理完成或可解锁");break;
        default:unavailable("验证已撤销；保持锁定");
    }}
    private void unavailable(String text){if(status!=null)status.setText(text);if(confirm!=null){confirm.setEnabled(false);confirm.setOnClickListener(null);}}
    private void retire(){waitingForeground=false;if(flow!=null)flow.retire();if(authentication!=null)authentication.close();} // Requests cancel, never assumes pending OS work ended.
    @Override protected void onResume(){super.onResume();observeForegroundThenRender();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)observeForegroundThenRender();}
    @Override protected void onDestroy(){destroyed=true;retire();erasure=null;pending=null;if(confirm!=null)confirm.setOnClickListener(null);if(cancel!=null)cancel.setOnClickListener(null);super.onDestroy();}
    private static void owner(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Native recovery UI owner required");}
}
