package io.github.rebrowser.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.rebrowser.ReBrowserApplication;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.bootstrap.StartupCoordinator;
import io.github.rebrowser.entrypoints.AdminControlActivity;

/**
 * Native v2 entry shell. Browser-domain/session/UI wiring is the next implementation
 * increment; no legacy WebView facade, hidden debug bypass or unguarded GeckoView.
 */
public final class BrowserActivity extends Activity {
    private StartupCoordinator startup;
    private TextView status;
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(getApplication() instanceof ReBrowserApplication)) { finish(); return; }
        startup = ((ReBrowserApplication) getApplication()).primaryStartup();
        if (!startup.gate().mayShowNativeShell()) { finish(); return; }
        LinearLayout root = new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);root.setPadding(pad,pad,pad,pad);
        TextView title = new TextView(this);title.setText("ReBrowser");title.setTextSize(24);root.addView(title);
        status = new TextView(this);root.addView(status);
        Button admin = new Button(this);admin.setText("管理员调试");
        admin.setOnClickListener(v -> startActivity(new Intent(this, AdminControlActivity.class)));root.addView(admin);
        setContentView(root);showNativeState();
        // Do not auto-load external Intent data, restore sessions or initialize Gecko.
    }
    @Override protected void onResume() { super.onResume(); if (status != null) showNativeState(); }
    private void showNativeState() {
        ComponentGate.SourceState state = startup.gate().sourceState();
        status.setText(state == ComponentGate.SourceState.UNCONFIGURED
                ? "v2 主程序开发中：正式来源及管理员根尚未配置，网页业务保持锁定。"
                : "网页业务来源闸门：" + state.name());
    }
}
