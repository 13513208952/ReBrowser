package io.github.reoutlook;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Small deterministic icons rather than device-dependent emoji. */
final class ReBrowserHistoryIcon extends Drawable {
    static final int MAINTAIN = 0, CLEAR_TIMED = 1, PAUSE = 2;
    private final int type;
    private final boolean active;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int alpha = 255;

    ReBrowserHistoryIcon(int type, boolean active) { this.type = type; this.active = active; }

    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 32f, getBounds().height() / 32f);
        paint.setColor(active ? type == MAINTAIN ? Color.rgb(220, 60, 75)
                : Color.rgb(115, 80, 200) : Color.rgb(159, 164, 176));
        paint.setAlpha(alpha);
        paint.setStyle(Paint.Style.FILL);
        if (type == MAINTAIN) {
            if (active) canvas.drawRoundRect(9, 9, 23, 23, 2, 2, paint);
            else {
                Path triangle = new Path();
                triangle.moveTo(11, 7); triangle.lineTo(25, 16); triangle.lineTo(11, 25); triangle.close();
                canvas.drawPath(triangle, paint);
            }
        } else if (type == CLEAR_TIMED) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f);
            canvas.drawArc(4, 4, 28, 28, 35, 290, false, paint);
            paint.setStyle(Paint.Style.FILL);
            Path arrow = new Path(); arrow.moveTo(23, 3); arrow.lineTo(30, 9); arrow.lineTo(22, 11); arrow.close();
            canvas.drawPath(arrow, paint);
            paint.setTextSize(10); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            canvas.drawText("30", 16, 20, paint);
        } else {
            Path mask = new Path();
            mask.moveTo(3, 10); mask.cubicTo(9, 6, 12, 8, 16, 10);
            mask.cubicTo(20, 8, 23, 6, 29, 10);
            mask.lineTo(27, 21); mask.cubicTo(22, 27, 19, 23, 16, 19);
            mask.cubicTo(13, 23, 10, 27, 5, 21); mask.close();
            canvas.drawPath(mask, paint);
            paint.setColor(Color.WHITE);
            paint.setAlpha(alpha);
            canvas.drawOval(7, 13, 13, 17, paint); canvas.drawOval(19, 13, 25, 17, paint);
        }
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
