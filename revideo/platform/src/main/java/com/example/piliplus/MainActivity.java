package com.example.piliplus;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ryanheise.audioservice.AudioService;
import com.ryanheise.audioservice.AudioServiceActivity;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends AudioServiceActivity {
    private MethodChannel hostChannel;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        AndroidHelper.attachActivity(this);
        hostChannel = new MethodChannel(
                flutterEngine.getDartExecutor().getBinaryMessenger(),
                "io.github.rebrowser.revideo/host"
        );
        hostChannel.setMethodCallHandler((call, result) -> {
            if ("close".equals(call.method)) {
                result.success(null);
                closeReVideo();
            } else {
                result.notImplemented();
            }
        });
    }

    @Override
    public void cleanUpFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        if (hostChannel != null) {
            hostChannel.setMethodCallHandler(null);
            hostChannel = null;
        }
        AndroidHelper.detachActivity(this);
        super.cleanUpFlutterEngine(flutterEngine);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (AndroidHelper.isFoldable) {
            Runnable callback = AndroidHelper.ToDart.onConfigurationChanged;
            if (callback != null) {
                callback.run();
            }
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        Runnable callback = AndroidHelper.ToDart.onUserLeaveHint;
        if (callback != null) {
            callback.run();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode, @Nullable Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        AndroidHelper.isPipMode = isInPictureInPictureMode;
    }

    void closeReVideo() {
        if (!isFinishing() && !isDestroyed()) {
            AndroidHelper.ToDart.onUserLeaveHint = null;
            AndroidHelper.ToDart.onConfigurationChanged = null;
            stopService(new Intent(this, AudioService.class));
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        AndroidHelper.ToDart.onUserLeaveHint = null;
        AndroidHelper.ToDart.onConfigurationChanged = null;
        AndroidHelper.detachActivity(this);
        stopService(new Intent(this, AudioService.class));
        super.onDestroy();
    }
}
