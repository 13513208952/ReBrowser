package io.github.reoutlook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Opens the standalone BiliClient app or shows its download address. */
public final class ReVideoShortcutActivity extends Activity {
    static final String TARGET_PACKAGE = "com.bilibilitakemeback";
    static final String DOWNLOAD_URL = "https://github.com/13513208952/BiliClient/releases";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(TARGET_PACKAGE);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
            finish();
            return;
        }

        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("BiliClient 下载地址", DOWNLOAD_URL));

        new AlertDialog.Builder(this)
                .setTitle("未安装 BiliClient")
                .setMessage("请下载安装 BiliClient：\n\n" + DOWNLOAD_URL
                        + "\n\n下载地址已复制到剪切板。")
                .setPositiveButton("确定", (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }
}
