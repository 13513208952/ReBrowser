package io.github.reoutlook;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Thin page-loading indicator. Its palette is independent of navigation and future theme UI. */
final class ReBrowserLoadingBar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] colors = {Color.rgb(245, 158, 66), Color.rgb(225, 105, 139), Color.rgb(139, 92, 246)};
    private String pageId;
    private float displayedProgress;
    private int targetProgress;
    private boolean completed;
    private ValueAnimator movement;
    private final Runnable fade = () -> animate().alpha(0f).setDuration(180)
            .withEndAction(() -> setVisibility(INVISIBLE)).start();

    ReBrowserLoadingBar(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(INVISIBLE);
    }

    /** One color gives a solid line; two or more give a fixed, full-width gradient. */
    void setPalette(int... palette) {
        if (palette == null || palette.length == 0) throw new IllegalArgumentException("Empty palette");
        colors = palette.clone();
        updatePaint();
        invalidate();
    }

    void bind(String id, boolean loading, int progress) {
        if (id == null || !loading) {
            hideImmediately();
            pageId = id;
            return;
        }
        if (!id.equals(pageId) || getVisibility() != VISIBLE) start(id);
        update(id, progress);
    }

    void start(String id) {
        cancelAnimations();
        pageId = id;
        completed = false;
        targetProgress = 5;
        displayedProgress = 0.05f;
        setAlpha(1f);
        setVisibility(VISIBLE);
        invalidate();
    }

    void update(String id, int progress) {
        if (id == null || !id.equals(pageId) || completed) return;
        int bounded = Math.max(5, Math.min(100, progress));
        if (bounded < targetProgress) return;
        if (bounded == 100) {
            finish(id);
        } else if (bounded > targetProgress) {
            targetProgress = bounded;
            moveTo(bounded / 100f, false);
        }
    }

    void finish(String id) {
        if (id == null || !id.equals(pageId) || completed || getVisibility() != VISIBLE) return;
        completed = true;
        targetProgress = 100;
        moveTo(1f, true);
    }

    void hideImmediately() {
        cancelAnimations();
        completed = true;
        targetProgress = 0;
        displayedProgress = 0;
        setAlpha(1f);
        setVisibility(INVISIBLE);
        invalidate();
    }

    private void moveTo(float target, boolean finish) {
        if (movement != null) {
            movement.removeAllListeners();
            movement.cancel();
        }
        ValueAnimator animator = ValueAnimator.ofFloat(displayedProgress, target);
        movement = animator;
        animator.setDuration(finish ? 160 : 220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            displayedProgress = (float) value.getAnimatedValue();
            invalidate();
        });
        if (finish) animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                postDelayed(fade, 100);
            }
        });
        animator.start();
    }

    private void cancelAnimations() {
        removeCallbacks(fade);
        animate().withEndAction(null).cancel();
        if (movement != null) {
            movement.removeAllListeners();
            movement.cancel();
            movement = null;
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updatePaint();
    }

    private void updatePaint() {
        paint.setColor(colors[0]);
        paint.setShader(colors.length == 1 ? null : new LinearGradient(
                0, 0, Math.max(1, getWidth()), 0, colors, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float right = getWidth() * displayedProgress;
        canvas.drawRoundRect(0, 0, right, getHeight(), getHeight() / 2f, getHeight() / 2f, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        hideImmediately();
        super.onDetachedFromWindow();
    }
}
