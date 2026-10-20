package io.github.rebrowser.outlook;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.rebrowser.ui.BrowserToolbar;
import java.util.List;

/**
 * Native Outlook WINDOW SHELL: official entry choice, a read-only account observation string and
 * an offline-mail entry. It never creates a Gecko session/context, starts an authentication popup,
 * derives an account from a URL/Intent or opens the private offline database. Actions are INTENT
 * for the native mailbox owner and require a current display binding.
 */
public final class OutlookActivity extends Activity {
    public enum AccountObservation { UNKNOWN, REPORTED_SIGNED_IN, REPORTED_SIGNED_OUT }
    public interface Actions {
        void chooseEntry(OfficialMailEntry.Choice choice);
        void openOfflineMail();
        void requestAccountCheck();
    }
    public static final int MAX_ACCOUNT_LABEL_CHARS = 128;

    private LinearLayout content;
    private BrowserToolbar.Binding binding;private Object frame;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        setContentView(content);
        render(AccountObservation.UNKNOWN,OfficialMailEntry.fixedEntries(),null);
        // No Intent extras are interpreted as account/session authority.
    }

    public void bind(BrowserToolbar.Binding binding,AccountObservation observation,OfficialMailEntry.Choice observedRecent,Actions actions){
        ownerThread();
        if(binding==null || observation==null || actions==null)throw new IllegalArgumentException("Missing native Outlook binding");
        binding.check();
        this.binding=binding;
        render(observation,OfficialMailEntry.entriesWithRecent(observedRecent),actions);
    }

    public void unbind(){ownerThread();binding=null;frame=null;render(AccountObservation.UNKNOWN,OfficialMailEntry.fixedEntries(),null);}

    private void render(AccountObservation observation,List<OfficialMailEntry.Choice> entries,Actions actions){
        BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();
        final Object current=new Object();frame=current;
        TextView header=new TextView(this);
        header.setText("Outlook 官方邮箱");
        header.setTextSize(20);
        content.addView(header);
        TextView account=new TextView(this);
        account.setText(accountLabel(observation));
        content.addView(account);
        for(OfficialMailEntry.Choice choice:entries){
            Button button=new Button(this);
            button.setText(choice.label());
            button.setMinimumHeight(dp(48));
            button.setEnabled(actions!=null);
            button.setContentDescription("打开官方入口：" + choice.label());
            button.setOnClickListener(v->dispatch(current,()->actions.chooseEntry(choice)));
            content.addView(button,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
        }
        Button check=new Button(this);
        check.setText("重新读取账号状态");
        check.setMinimumHeight(dp(48));
        check.setEnabled(actions!=null);
        check.setContentDescription("请求重新读取官方账号状态（不代表已登录）");
        check.setOnClickListener(v->dispatch(current,()->actions.requestAccountCheck()));
        content.addView(check,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
        Button offline=new Button(this);
        offline.setText("离线邮件");
        offline.setMinimumHeight(dp(48));
        offline.setEnabled(actions!=null);
        offline.setContentDescription("打开本机离线邮件列表");
        offline.setOnClickListener(v->dispatch(current,()->actions.openOfflineMail()));
        content.addView(offline,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
    }

    public static String accountLabel(AccountObservation observation){
        if(observation==null)throw new IllegalArgumentException("Missing account observation");
        switch(observation){case REPORTED_SIGNED_IN:return "官方页面报告已登录（非fresh认证）";case REPORTED_SIGNED_OUT:return "官方页面报告未登录";default:return "账号状态未知";}
    }

    private void dispatch(Object expected,Runnable intent){
        BrowserToolbar.Binding captured=binding;
        if(captured==null || frame!=expected)return;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(frame!=expected || captured!=binding)return;
            intent.run();
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)setTitle("操作结果未知，请核对当前邮箱状态");
    }
    @Override protected void onDestroy(){binding=null;frame=null;BrowserToolbar.clearOwnedViewTree(content);super.onDestroy();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static void ownerThread(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Native UI owner thread required");}
}
