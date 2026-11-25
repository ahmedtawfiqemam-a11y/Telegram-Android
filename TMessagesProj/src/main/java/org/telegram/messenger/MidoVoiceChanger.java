package org.telegram.messenger;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Real-time PCM-16 pitch/formant voice modulator for Telegram voice calls (WebRTC)
 * and voice messages (Recorder V1 & V2). Uses a zero-allocation dual-tap circular
 * harmonizer per stream so call audio remains lock-free and duration stays invariant.
 */
public class MidoVoiceChanger {

    public static final int MODE_OFF = 0;
    public static final int MODE_DEEP = 1;
    public static final int MODE_HIGH = 2;
    public static final int MODE_CHILD = 3;
    public static final int MODE_ROBOT = 4;
    public static final int MODE_MONSTER = 5;
    public static final int MODE_CUSTOM = 6;

    private static final VoiceStreamProcessor CALL_PROCESSOR = new VoiceStreamProcessor();
    private static final VoiceStreamProcessor MESSAGE_PROCESSOR = new VoiceStreamProcessor();

    public static float getActivePitchFactor() {
        switch (MidoConfig.voiceChangerMode) {
            case MODE_DEEP:
                return 0.78f;
            case MODE_HIGH:
                return 1.26f;
            case MODE_CHILD:
                return 1.45f;
            case MODE_ROBOT:
                return 0.94f;
            case MODE_MONSTER:
                return 0.65f;
            case MODE_CUSTOM:
                return Math.max(0.55f, Math.min(1.65f, MidoConfig.voiceChangerPitch));
            case MODE_OFF:
            default:
                return 1.0f;
        }
    }

    public static String getPresetName(int mode) {
        switch (mode) {
            case MODE_DEEP:
                return "صوت عميق / رجل 🎙️";
            case MODE_HIGH:
                return "صوت حاد / أنثوي 🎭";
            case MODE_CHILD:
                return "طفل / سنجاب 🐿️";
            case MODE_ROBOT:
                return "روبوت إلكتروني 🤖";
            case MODE_MONSTER:
                return "صوت ضخم / مخيف 👹";
            case MODE_CUSTOM:
                return String.format(Locale.US, "مخصص (%.2fx) 🎛️", MidoConfig.voiceChangerPitch);
            case MODE_OFF:
            default:
                return "طبيعي (إيقاف)";
        }
    }

    public static void processCallFrame(ByteBuffer buffer, int bytesLen, int sampleRate) {
        processCallFrame(buffer, bytesLen, sampleRate, 1);
    }

    public static void processCallFrame(ByteBuffer buffer, int bytesLen, int sampleRate, int channels) {
        if (MidoConfig.voiceChangerMode == MODE_OFF || !MidoConfig.voiceChangerCalls || buffer == null || bytesLen < 2) {
            return;
        }
        CALL_PROCESSOR.process(buffer, bytesLen, sampleRate, channels, getActivePitchFactor(), MidoConfig.voiceChangerMode == MODE_ROBOT);
    }

    public static void processMessageFrame(ByteBuffer buffer, int bytesLen, int sampleRate) {
        if (MidoConfig.voiceChangerMode == MODE_OFF || !MidoConfig.voiceChangerMessages || buffer == null || bytesLen < 2) {
            return;
        }
        MESSAGE_PROCESSOR.process(buffer, bytesLen, sampleRate, 1, getActivePitchFactor(), MidoConfig.voiceChangerMode == MODE_ROBOT);
    }

    public static void resetStreams() {
        CALL_PROCESSOR.reset();
        MESSAGE_PROCESSOR.reset();
    }

    private static boolean isContextDead(Context context) {
        if (context == null) {
            return true;
        }
        if (context instanceof Activity) {
            Activity act = (Activity) context;
            if (act.isFinishing()) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= 17 && act.isDestroyed()) {
                return true;
            }
        }
        return false;
    }

    public static void showPresetDialog(Context context, Runnable onChanged) {
        if (isContextDead(context)) {
            return;
        }
        final CharSequence[] labels = new CharSequence[]{
                "طبيعي (إيقاف)",
                "صوت عميق / رجل 🎙️",
                "صوت حاد / أنثوي 🎭",
                "طفل / سنجاب 🐿️",
                "روبوت إلكتروني 🤖",
                "صوت ضخم / مخيف 👹",
                "نبرة مخصصة (شريط التحكم) 🎛️"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("مغيّر الصوت الفوري");
        builder.setItems(labels, (dialog, which) -> {
            if (which == MODE_CUSTOM) {
                showCustomPitchDialog(context, onChanged);
            } else {
                MidoConfig.setVoiceChangerMode(which);
                resetStreams();
                if (onChanged != null) {
                    onChanged.run();
                }
            }
        });
        try {
            builder.show();
        } catch (Throwable ignore) {
        }
    }

    public static void showCustomPitchDialog(Context context, Runnable onChanged) {
        if (isContextDead(context)) {
            return;
        }
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(22), dp(10), dp(22), dp(8));

        TextView valueTv = new TextView(context);
        valueTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueTv.setTypeface(AndroidUtilities.bold());
        valueTv.setGravity(Gravity.CENTER_HORIZONTAL);
        valueTv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        container.addView(valueTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(100); // maps 0.55f .. 1.55f
        int initialProgress = Math.max(0, Math.min(100, Math.round((MidoConfig.voiceChangerPitch - 0.55f) * 100f)));
        seekBar.setProgress(initialProgress);
        valueTv.setText(String.format(Locale.US, "درجة النبرة: %.2fx", 0.55f + initialProgress / 100f));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float pitch = 0.55f + (progress / 100f);
                valueTv.setText(String.format(Locale.US, "درجة النبرة: %.2fx", pitch));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        container.addView(seekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("ضبط نبرة الصوت المخصصة");
        builder.setView(container);
        builder.setPositiveButton("تطبيق", (d, w) -> {
            float pitch = 0.55f + (seekBar.getProgress() / 100f);
            MidoConfig.setVoiceChangerPitch(pitch);
            MidoConfig.setVoiceChangerMode(MODE_CUSTOM);
            resetStreams();
            if (onChanged != null) {
                onChanged.run();
            }
        });
        builder.setNegativeButton("إلغاء", null);
        try {
            builder.show();
        } catch (Throwable ignore) {
        }
    }

    /**
     * Zero-allocation circular-buffer pitch shifter with dual raised-cosine (Hann) crossfaded taps.
     * Supports mono and stereo PCM-16 streams while keeping tempo invariant and clicks eliminated.
     */
    private static final class VoiceStreamProcessor {
        private static final int RING_SIZE = 4096;
        private static final int MASK = RING_SIZE - 1;
        private static final int HALF = RING_SIZE / 2;
        private static final int MAX_CHANNELS = 2;

        private final float[][] rings = new float[MAX_CHANNELS][RING_SIZE];
        private final int[] writeIdx = new int[MAX_CHANNELS];
        private double phase = 0d; // Normalized [0, 1) delay sweep phase across HALF samples
        private double modPhase = 0d;
        private long lastFrameElapsedMs = 0L;
        private int lastChannels = 1;

        synchronized void reset() {
            for (int c = 0; c < MAX_CHANNELS; c++) {
                float[] r = rings[c];
                for (int i = 0; i < RING_SIZE; i++) {
                    r[i] = 0f;
                }
                writeIdx[c] = 0;
            }
            phase = 0d;
            modPhase = 0d;
            lastFrameElapsedMs = 0L;
        }

        synchronized void process(ByteBuffer buffer, int bytesLen, int sampleRate, int channels, float pitchFactor, boolean robotMod) {
            long nowMs = SystemClock.elapsedRealtime();
            if (lastFrameElapsedMs != 0L && (nowMs - lastFrameElapsedMs) > 600L) {
                reset();
            }
            lastFrameElapsedMs = nowMs;

            if (sampleRate <= 0) {
                sampleRate = 48000;
            }
            int ch = Math.max(1, Math.min(MAX_CHANNELS, channels));
            if (ch != lastChannels) {
                reset();
                lastChannels = ch;
            }
            int frameBytes = 2 * ch;
            int totalSamples = Math.min(bytesLen / 2, buffer.remaining() / 2);
            if (totalSamples <= 0) {
                totalSamples = Math.min(bytesLen / 2, buffer.capacity() / 2);
            }
            int frameCount = totalSamples / ch;
            if (frameCount <= 0) {
                return;
            }

            final boolean bigEndian = buffer.order() == ByteOrder.BIG_ENDIAN;
            final double phaseInc = (1.0d - pitchFactor) / (double) HALF;
            final double robotPhaseInc = (2.0d * Math.PI * 72.0d) / sampleRate;
            final int startPos = buffer.position() < buffer.limit() ? buffer.position() : 0;

            for (int f = 0; f < frameCount; f++) {
                phase += phaseInc;
                if (phase < 0d) {
                    phase -= Math.floor(phase);
                } else if (phase >= 1.0d) {
                    phase -= Math.floor(phase);
                }
                double phaseB = phase + 0.5d;
                if (phaseB >= 1.0d) {
                    phaseB -= 1.0d;
                }

                float delayA = (float) (phase * HALF);
                float delayB = (float) (phaseB * HALF);
                // Hann windows shifted by 0.5 sum identically to 1.0 and reach 0.0 at wrap points
                float weightA = (float) (0.5d * (1.0d - Math.cos(2.0d * Math.PI * phase)));
                float weightB = 1.0f - weightA;

                float robotCarrier = 1.0f;
                if (robotMod) {
                    robotCarrier = 0.55f + 0.45f * (float) Math.sin(modPhase);
                    modPhase += robotPhaseInc;
                    if (modPhase > 2.0d * Math.PI) {
                        modPhase -= 2.0d * Math.PI;
                    }
                }

                for (int c = 0; c < ch; c++) {
                    int byteIdx = startPos + f * frameBytes + c * 2;
                    if (byteIdx + 1 >= buffer.capacity()) {
                        return;
                    }
                    short inSample;
                    if (bigEndian) {
                        int hi = buffer.get(byteIdx);
                        int lo = buffer.get(byteIdx + 1) & 0xFF;
                        inSample = (short) ((hi << 8) | lo);
                    } else {
                        int lo = buffer.get(byteIdx) & 0xFF;
                        int hi = buffer.get(byteIdx + 1);
                        inSample = (short) (lo | (hi << 8));
                    }

                    float[] r = rings[c];
                    int w = writeIdx[c];
                    r[w] = inSample;

                    float readA = w - delayA;
                    if (readA < 0f) readA += RING_SIZE;
                    float readB = w - delayB;
                    if (readB < 0f) readB += RING_SIZE;

                    float out = readLinear(r, readA) * weightA + readLinear(r, readB) * weightB;
                    if (robotMod) {
                        out *= robotCarrier;
                    }

                    int clamped = Math.round(out);
                    if (clamped > 32767) clamped = 32767;
                    if (clamped < -32768) clamped = -32768;

                    if (bigEndian) {
                        buffer.put(byteIdx, (byte) ((clamped >> 8) & 0xFF));
                        buffer.put(byteIdx + 1, (byte) (clamped & 0xFF));
                    } else {
                        buffer.put(byteIdx, (byte) (clamped & 0xFF));
                        buffer.put(byteIdx + 1, (byte) ((clamped >> 8) & 0xFF));
                    }
                    writeIdx[c] = (w + 1) & MASK;
                }
            }
        }

        private static float readLinear(float[] ring, float pos) {
            int idx0 = ((int) pos) & MASK;
            int idx1 = (idx0 + 1) & MASK;
            float frac = pos - (int) pos;
            return ring[idx0] * (1.0f - frac) + ring[idx1] * frac;
        }
    }
}
