package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

public class MidoMajorBadgeDrawable extends Drawable {

    private final int sizePx;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gleamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path starPath = new Path();
    private int lastBoundsW = -1;
    private int lastBoundsH = -1;

    public MidoMajorBadgeDrawable(int sizeDp) {
        this.sizePx = AndroidUtilities.dp(sizeDp);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Math.max(1f, sizePx * 0.06f));
        borderPaint.setColor(0xFF3E63C9);
        gleamPaint.setColor(0x66FFF7C2);
    }

    @Override
    public int getIntrinsicWidth() {
        return sizePx;
    }

    @Override
    public int getIntrinsicHeight() {
        return sizePx;
    }

    private void updateShadersAndPaths(Rect bounds) {
        int w = bounds.width();
        int h = bounds.height();
        if (w <= 0 || h <= 0 || (w == lastBoundsW && h == lastBoundsH)) {
            return;
        }
        lastBoundsW = w;
        lastBoundsH = h;

        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.min(w, h) * 0.48f;

        bgPaint.setShader(new LinearGradient(
                cx, cy - radius, cx, cy + radius,
                0xFF1A2E66, 0xFF0C1530, Shader.TileMode.CLAMP
        ));

        starPaint.setShader(new LinearGradient(
                cx, cy - radius * 0.7f, cx, cy + radius * 0.7f,
                0xFFFFEB5C, 0xFFFF8C00, Shader.TileMode.CLAMP
        ));

        // Build Major-style 5-pointed rounded star
        starPath.reset();
        float outerR = radius * 0.72f;
        float innerR = radius * 0.34f;
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36.0);
            float r = (i % 2 == 0) ? outerR : innerR;
            float x = cx + (float) (Math.cos(angle) * r);
            float y = cy + (float) (Math.sin(angle) * r);
            if (i == 0) {
                starPath.moveTo(x, y);
            } else {
                starPath.lineTo(x, y);
            }
        }
        starPath.close();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        updateShadersAndPaths(bounds);

        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.min(bounds.width(), bounds.height()) * 0.46f;

        canvas.drawCircle(cx, cy, radius, bgPaint);
        canvas.drawCircle(cx, cy, radius, borderPaint);
        canvas.drawPath(starPath, starPaint);
        canvas.drawCircle(cx - radius * 0.12f, cy - radius * 0.18f, radius * 0.14f, gleamPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        bgPaint.setAlpha(alpha);
        borderPaint.setAlpha(alpha);
        starPaint.setAlpha(alpha);
        gleamPaint.setAlpha((int) (alpha * 0.4f));
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        // Intentionally ignored so Telegram theme tints do not overwrite Major emblem colors
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
