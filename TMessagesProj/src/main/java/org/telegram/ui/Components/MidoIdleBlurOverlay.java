package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.content.ContextWrapper;
import android.util.TypedValue;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MidoConfig;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.ActionBar.Theme;

/**
 * Full-screen frosted blur privacy guard that automatically blurs chat contents
 * and contact names when the screen sits idle without touch for MidoConfig.autoBlurSeconds.
 * Consumes the first tap to reveal the screen cleanly without accidental clicks underneath.
 */
public class MidoIdleBlurOverlay extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final View sourceView;
    private final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect srcRect = new Rect();
    private final Rect dstRect = new Rect();

    private Bitmap blurredBitmap;
    private boolean isBlurred = false;
    private boolean isHostResumed = false;
    private int captureGeneration = 0;

    private final Runnable idleRunnable = this::activateBlur;

    public MidoIdleBlurOverlay(@NonNull Context context, View sourceView) {
        super(context);
        this.sourceView = sourceView;

        setWillNotDraw(false);
        setVisibility(GONE);
        setAlpha(0f);
        setClickable(true);
        setFocusable(true);

        // Sleek centered Telegram-native privacy shield card
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(22), dp(24), dp(22));
        card.setBackground(Theme.createRoundRectDrawable(dp(22), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhite), 0.92f)));
        card.setElevation(dp(8));

        TextView iconTv = new TextView(context);
        iconTv.setText("🔒");
        iconTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 36);
        iconTv.setGravity(Gravity.CENTER);
        card.addView(iconTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView titleTv = new TextView(context);
        titleTv.setText("تم تغبيش الشاشة لخصوصيتك");
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        titleTv.setTypeface(AndroidUtilities.bold());
        titleTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTv.setGravity(Gravity.CENTER);
        card.addView(titleTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView subTv = new TextView(context);
        subTv.setText("تم إخفاء المحادثات والأسماء لعدم لمس الشاشة • اضغط في أي مكان للمتابعة");
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subTv.setGravity(Gravity.CENTER);
        card.addView(subTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        TextView unlockBtn = new TextView(context);
        unlockBtn.setText("إظهار المحادثة 👁️");
        unlockBtn.setGravity(Gravity.CENTER);
        unlockBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        unlockBtn.setTypeface(AndroidUtilities.bold());
        unlockBtn.setTextColor(Color.WHITE);
        unlockBtn.setPadding(dp(22), dp(10), dp(22), dp(10));
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        unlockBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(12), accent, Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        unlockBtn.setOnClickListener(v -> dismissAnimated());
        card.addView(unlockBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        addView(card, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 28, 0, 28, 0));

        setOnClickListener(v -> dismissAnimated());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.midoAutoBlurSettingsChanged);
        rescheduleIdleTimer();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.midoAutoBlurSettingsChanged);
        AndroidUtilities.cancelRunOnUIThread(idleRunnable);
        recycleBitmap();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.midoAutoBlurSettingsChanged) {
            if (!MidoConfig.autoBlurOnIdle && isBlurred) {
                dismissAnimated();
            } else {
                rescheduleIdleTimer();
            }
        }
    }

    public void onHostResumed() {
        isHostResumed = true;
        rescheduleIdleTimer();
    }

    public void onHostPaused() {
        isHostResumed = false;
        AndroidUtilities.cancelRunOnUIThread(idleRunnable);
        if (isBlurred) {
            dismissImmediate();
        }
    }

    public void onUserTouched() {
        if (!isBlurred) {
            rescheduleIdleTimer();
        }
    }

    public boolean isBlurred() {
        return isBlurred;
    }

    public boolean dismissIfBlurred() {
        if (!isBlurred) {
            return false;
        }
        dismissAnimated();
        return true;
    }

    private void rescheduleIdleTimer() {
        AndroidUtilities.cancelRunOnUIThread(idleRunnable);
        if (!isHostResumed || !MidoConfig.autoBlurOnIdle || isBlurred) {
            return;
        }
        int delayMs = Math.max(3, MidoConfig.autoBlurSeconds) * 1000;
        AndroidUtilities.runOnUIThread(idleRunnable, delayMs);
    }

    private void activateBlur() {
        if (!isHostResumed || !MidoConfig.autoBlurOnIdle || isBlurred) {
            return;
        }
        // Skip during active call or voice note recording
        if (VoIPService.getSharedInstance() != null || MediaController.getInstance().isRecordingAudio()) {
            rescheduleIdleTimer();
            return;
        }
        captureAndBlurSource();
        isBlurred = true;
        setVisibility(VISIBLE);
        bringToFront();
        animate().cancel();
        animate().alpha(1f).setDuration(240).start();
    }

    private void dismissAnimated() {
        if (!isBlurred) {
            rescheduleIdleTimer();
            return;
        }
        isBlurred = false;
        captureGeneration++;
        animate().cancel();
        animate().alpha(0f).setDuration(180).withEndAction(() -> {
            if (!isBlurred) {
                setVisibility(GONE);
                recycleBitmap();
                rescheduleIdleTimer();
            }
        }).start();
    }

    private void dismissImmediate() {
        isBlurred = false;
        captureGeneration++;
        animate().cancel();
        setAlpha(0f);
        setVisibility(GONE);
        recycleBitmap();
    }

    private Window findHostWindow() {
        Context ctx = sourceView != null ? sourceView.getContext() : getContext();
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) {
                Activity act = (Activity) ctx;
                return !act.isFinishing() ? act.getWindow() : null;
            }
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }

    private void captureAndBlurSource() {
        recycleBitmap();
        final int token = ++captureGeneration;
        if (sourceView == null || !sourceView.isAttachedToWindow() || sourceView.getWidth() <= 0 || sourceView.getHeight() <= 0) {
            return;
        }
        final int w = sourceView.getWidth();
        final int h = sourceView.getHeight();
        final int scaleW = Math.max(64, w / 6);
        final int scaleH = Math.max(64, h / 6);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Window window = findHostWindow();
            if (window != null && window.getDecorView() != null && window.getDecorView().isAttachedToWindow()) {
                try {
                    int[] loc = new int[2];
                    sourceView.getLocationInWindow(loc);
                    Rect region = new Rect(loc[0], loc[1], loc[0] + w, loc[1] + h);
                    Bitmap bmp = Bitmap.createBitmap(scaleW, scaleH, Bitmap.Config.ARGB_8888);
                    PixelCopy.request(window, region, bmp, copyResult -> {
                        if (copyResult == PixelCopy.SUCCESS && isBlurred && token == captureGeneration && isAttachedToWindow()) {
                            try {
                                Utilities.stackBlurBitmap(bmp, 18);
                                blurredBitmap = bmp;
                                invalidate();
                                return;
                            } catch (Throwable e) {
                                FileLog.e(e);
                            }
                        }
                        if (!bmp.isRecycled()) {
                            bmp.recycle();
                        }
                    }, AndroidUtilities.applicationHandler);
                    return;
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            // Do not call software Canvas.draw() on API >= 26 hierarchies containing HARDWARE bitmaps/RenderNodes;
            // fall back cleanly to the frosted privacy scrim in onDraw().
            return;
        }

        try {
            Bitmap bmp = Bitmap.createBitmap(scaleW, scaleH, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.scale(scaleW / (float) w, scaleH / (float) h);
            sourceView.draw(c);
            Utilities.stackBlurBitmap(bmp, 18);
            blurredBitmap = bmp;
        } catch (Throwable e) {
            FileLog.e(e);
            blurredBitmap = null;
        }
    }

    private void recycleBitmap() {
        if (blurredBitmap != null) {
            if (!blurredBitmap.isRecycled()) {
                blurredBitmap.recycle();
            }
            blurredBitmap = null;
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (blurredBitmap != null && !blurredBitmap.isRecycled()) {
            srcRect.set(0, 0, blurredBitmap.getWidth(), blurredBitmap.getHeight());
            dstRect.set(0, 0, w, h);
            canvas.drawBitmap(blurredBitmap, srcRect, dstRect, blurPaint);
            scrimPaint.setColor(Theme.isCurrentThemeDark() ? 0x66080C12 : 0x55DCE3EC);
            canvas.drawRect(0, 0, w, h, scrimPaint);
        } else {
            scrimPaint.setColor(Theme.isCurrentThemeDark() ? 0xEA0F141A : 0xEAF2F5F9);
            canvas.drawRect(0, 0, w, h, scrimPaint);
        }
        super.onDraw(canvas);
    }
}
