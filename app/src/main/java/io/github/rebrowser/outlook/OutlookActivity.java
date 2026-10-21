package io.github.rebrowser.outlook;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import io.github.rebrowser.ui.BrowserToolbar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Native Outlook WINDOW SHELL. It shows the page's reported account observations, the fixed
 * official entries and the offline-list OFFERED state SEPARATELY. An account check captures the
 * exact row token+revision and is one-shot per frame; a new page/account never reuses the old
 * summary or availability. It never logs in, reads cookies, calls a mail API, derives an account
 * from a URL/Intent or constructs an owner proof; only fixed OfficialMailEntry values are used.
 */
public final class OutlookActivity extends Activity {
    public enum AccountObservation { UNKNOWN, REPORTED_SIGNED_IN, REPORTED_SIGNED_OUT }
    public interface Actions {
        void chooseEntry(OfficialMailEntry.Choice choice);
        void openOfflineMail(long expectedRevision);
        void checkAccount(AccountRow account,long expectedRevision);
    }
    public static final int MAX_ACCOUNT_LABEL_CHARS = 128;
    public static final int MAX_ACCOUNTS = 8;

    /** One reported account row; the token is an opaque per-page handoff, not a login proof. */
    public static final class AccountRow {
        private final Object token;private final String label;private final AccountObservation observation;
        public AccountRow(Object token,String label,AccountObservation observation){
            if(token==null || observation==null)throw new IllegalArgumentException("Incomplete account row");
            this.token=token;this.label=text(label,MAX_ACCOUNT_LABEL_CHARS);this.observation=observation;
        }
        public Object token(){return token;}
        public String label(){return label;}
        public AccountObservation observation(){return observation;}
    }
    /** One supplied page; the summary and offline availability belong to THIS revision only. */
    public static final class Page {
        private final List<AccountRow> accounts;private final OfficialMailEntry.Choice observedRecent;
        private final Boolean offlineOffered;private final long revision;
        public Page(List<AccountRow> accounts,OfficialMailEntry.Choice observedRecent,Boolean offlineOffered,long revision){
            if(accounts==null || accounts.size()>MAX_ACCOUNTS || revision<1)throw new IllegalArgumentException("Invalid Outlook page");
            Set<Object> tokens=Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for(AccountRow account:accounts){if(account==null)throw new IllegalArgumentException("Missing account row");if(!tokens.add(account.token()))throw new IllegalArgumentException("Duplicate account token");}
            this.accounts=Collections.unmodifiableList(new ArrayList<>(accounts));
            this.observedRecent=observedRecent;this.offlineOffered=offlineOffered;this.revision=revision;
        }
        public List<AccountRow> accounts(){return accounts;}
        public OfficialMailEntry.Choice observedRecent(){return observedRecent;}
        public Boolean offlineOffered(){return offlineOffered;}
        public long revision(){return revision;}
    }

    private LinearLayout content;
    private TextView status;
    private BrowserToolbar.Binding binding;private Object frame;
    private Actions actions;private Page page;
    private final Set<Object> checkedAccounts=Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        setContentView(new ScrollView(this){{addView(content);}});
        status=new TextView(this);
        status.setText("未连接原生 Outlook 窗口");
        content.addView(status);
        render(AccountObservation.UNKNOWN,OfficialMailEntry.fixedEntries(),null,null);
        // No Intent extras are interpreted as account/session authority.
    }

    public void bind(BrowserToolbar.Binding binding,Page page,Actions actions){
        ownerThread();
        if(binding==null || page==null || actions==null)throw new IllegalArgumentException("Missing native Outlook binding");
        binding.check();
        this.binding=binding;this.page=page;this.actions=actions;this.frame=new Object();
        checkedAccounts.clear();status.setText("本页账号仅为观测，操作等待原生确认"); // no previous account summary
        render(AccountObservation.UNKNOWN,OfficialMailEntry.entriesWithRecent(page.observedRecent()),page,actions);
    }

    public void unbind(){ownerThread();binding=null;frame=null;actions=null;page=null;checkedAccounts.clear();render(AccountObservation.UNKNOWN,OfficialMailEntry.fixedEntries(),null,null);}

    private void render(AccountObservation summary,List<OfficialMailEntry.Choice> entries,Page currentPage,Actions actions){
        CharSequence message=status.getText();BrowserToolbar.clearOwnedViewTree(content);content.removeAllViews();status.setText(message);
        content.addView(status);
        final Object current=frame;
        TextView header=new TextView(this);
        header.setText("Outlook 官方邮箱");
        header.setTextSize(20);
        content.addView(header);
        TextView account=new TextView(this);
        account.setText(accountLabel(summary));
        content.addView(account);
        if(currentPage!=null && currentPage.accounts().isEmpty()){
            TextView none=new TextView(this);
            none.setText("本页未提供账号观测");
            content.addView(none);
        }
        if(currentPage!=null){
            for(AccountRow row:currentPage.accounts()){
                LinearLayout line=new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                TextView label=new TextView(this);
                boolean checked=checkedAccounts.contains(row.token());
                label.setText(row.label()+" · "+accountLabel(row.observation())+(checked?" · 已请求重新读取，等待原生刷新":""));
                label.setMinimumHeight(dp(48));
                line.addView(label,new LinearLayout.LayoutParams(0,dp(48),1));
                boolean enabled=actions!=null && !checked;
                Button check=new Button(this);
                check.setText("重新读取");
                check.setMinimumHeight(dp(48));
                check.setEnabled(enabled);
                check.setContentDescription(enabled?"请求重新读取该账号状态：" + row.label():"已请求，等待原生刷新：" + row.label());
                check.setOnClickListener(enabled?v->checkAccount(current,row,actions):null);
                line.addView(check,new LinearLayout.LayoutParams(dp(112),dp(48)));
                content.addView(line);
            }
        }
        for(OfficialMailEntry.Choice choice:entries){
            Button button=new Button(this);
            button.setText(choice.label());
            button.setMinimumHeight(dp(48));
            button.setEnabled(actions!=null);
            button.setContentDescription("打开官方入口：" + choice.label());
            button.setOnClickListener(actions==null?null:v->dispatch(current,actions,()->actions.chooseEntry(choice)));
            content.addView(button,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
        }
        Button offline=new Button(this);
        Boolean offered=currentPage==null?null:currentPage.offlineOffered();
        boolean offlineEnabled=actions!=null && Boolean.TRUE.equals(offered);
        offline.setText("离线邮件"+(offered==null?" · 状态未知":offered?"":" · 未提供"));
        offline.setMinimumHeight(dp(48));
        offline.setEnabled(offlineEnabled);
        offline.setContentDescription(offlineEnabled?"打开本机离线邮件列表":"离线邮件入口未提供或未知");
        offline.setOnClickListener(offlineEnabled?v->dispatch(current,actions,()->actions.openOfflineMail(currentPage.revision())):null);
        content.addView(offline,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
    }

    private void checkAccount(Object expected,AccountRow row,Actions actions){
        BrowserToolbar.Binding captured=binding;
        if(captured==null || actions==null || frame!=expected || page==null)return;
        final Page originalPage=page;boolean exact=false;for(AccountRow candidate:originalPage.accounts())if(candidate==row){exact=true;break;}if(!exact || actions!=this.actions)return;
        try{captured.check();}catch(RuntimeException stale){return;}
        if(frame!=expected || binding!=captured || page!=originalPage || actions!=this.actions)return;
        if(!checkedAccounts.add(row.token()))return; // one check per frame+token
        render(AccountObservation.UNKNOWN,OfficialMailEntry.entriesWithRecent(page.observedRecent()),page,actions);
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(frame!=expected || captured!=binding || page!=originalPage || actions!=this.actions)return;
            actions.checkAccount(row,originalPage.revision());
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)
            status.setText("账号读取结果未知，请重新读取；不显示为已登录");
    }

    private void dispatch(Object expected,Actions actions,Runnable intent){
        BrowserToolbar.Binding captured=binding;
        if(captured==null || actions==null || frame!=expected)return;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(frame!=expected || captured!=binding)return;
            intent.run();
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected)setTitle("操作结果未知，请核对当前邮箱状态");
    }

    @Override protected void onDestroy(){binding=null;frame=null;actions=null;page=null;checkedAccounts.clear();BrowserToolbar.clearOwnedViewTree(content);super.onDestroy();}
    public static String accountLabel(AccountObservation observation){
        if(observation==null)throw new IllegalArgumentException("Missing account observation");
        switch(observation){case REPORTED_SIGNED_IN:return "官方页面报告已登录（非fresh认证）";case REPORTED_SIGNED_OUT:return "官方页面报告未登录";default:return "账号状态未知";}
    }
    private static String text(String value,int max){
        if(value==null || value.length()>max)throw new IllegalArgumentException("Unbounded account label");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw new IllegalArgumentException("Control in account label");if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}
        return value;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static void ownerThread(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Native UI owner thread required");}
}
