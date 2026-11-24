package org.telegram.messenger;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.nio.ByteBuffer;
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
        if (MidoConfig.voiceChangerMode == MODE_OFF || !MidoConfig.voiceChangerCalls || buffer == null || bytesLen < 2) {
            return;
        }
        CALL_PROCESSOR.process(buffer, bytesLen, sampleRate, getActivePitchFactor(), MidoConfig.voiceChangerMode == MODE_ROBOT);
    }

    public static void processMessageFrame(ByteBuffer buffer, int bytesLen, int sampleRate) {
        if (MidoConfig.voiceChangerMode == MODE_OFF || !MidoConfig.voiceChangerMessages || buffer == null || bytesLen < 2) {
            return;
        }
        MESSAGE_PROCESSOR.process(buffer, bytesLen, sampleRate, getActivePitchFactor(), MidoConfig.voiceChangerMode == MODE_ROBOT);
    }

    public static void resetStreams() {
        CALL_PROCESSOR.reset();
        MESSAGE_PROCESSOR.reset();
    }

    public static void showPresetDialog(Context context, Runnable onChanged) {
        if (context == null) {
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
        builder.show();
    }

    public static void showCustomPitchDialog(Context context, Runnable onChanged) {
        if (context == null) {
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
        builder.show();
    }

    /**
     * Lock-free circular buffer pitch shifter with dual overlapping crossfaded read taps.
     */
    private static final class VoiceStreamProcessor {
        private static final int RING_SIZE = 4096;
        private static final int MASK = RING_SIZE - 1;
        private static final int HALF = RING_SIZE / 2;

        private final float[] ring = new float[RING_SIZE];
        private int writeIndex = 0;
        private float delay = HALF;
        private double modPhase = 0d;

        synchronized void reset() {
            for (int i = 0; i < RING_SIZE; i++) {
                ring[i] = 0f;
            }
            writeIndex = 0;
            delay = HALF;
            modPhase = 0d;
        }

        synchronized void process(ByteBuffer buffer, int bytesLen, int sampleRate, float pitchFactor, boolean robotMod) {
            if (sampleRate <= 0) {
                sampleRate = 48000;
            }
            int sampleCount = Math.min(bytesLen / 2, buffer.remaining() / 2);
            if (sampleCount <= 0) {
                // Fall back to capacity bounds if caller left position == limit
                sampleCount = Math.min(bytesLen / 2, buffer.capacity() / 2);
            }
            if (sampleCount <= 0) {
                return;
            }

            final float delayStep = 1.0f - pitchFactor;
            final double robotPhaseInc = (2.0d * Math.PI * 72.0d) / sampleRate;
            final int startPos = buffer.position() < buffer.limit() ? buffer.position() : 0;

            for (int i = 0; i < sampleCount; i++) {
                int byteIdx = startPos + i * 2;
                if (byteIdx + 1 >= buffer.capacity()) {
                    break;
                }
                int lo = buffer.get(byteIdx) & 0xFF;
                int hi = buffer.get(byteIdx + 1);
                short inSample = (short) (lo | (hi << 8));

                ring[writeIndex] = inSample;

                delay += delayStep;
                while (delay < 0f) {
                    delay += HALF;
                }
                while (delay >= HALF) {
                    delay -= HALF;
                }

                float readA = writeIndex - delay;
                if (readA < 0f) readA += RING_SIZE;

                float delayB = delay + HALF;
                if (delayB >= RING_SIZE) delayB -= RING_SIZE;
                float readB = writeIndex - delayB;
                if (readB < 0f) readB += RING_SIZE;

                float sampleA = readLinear(readA);
                float sampleB = readLinear(readB);

                // Smooth triangular crossfade across the HALF window boundary to prevent clicks
                float norm = delay / (float) HALF; // 0..1
                float weightB = 1.0f - Math.abs(2.0f * norm - 1.0f); // 0 at boundaries, 1 at center
                float weightA = 1.0f - weightB;

                float out = sampleA * weightA + sampleB * weightB;

                if (robotMod) {
                    float carrier = (float) Math.sin(modPhase);
                    out = out * (0.55f + 0.45f * carrier);
                    modPhase += robotPhaseInc;
                    if (modPhase > 2.0d * Math.PI) {
                        modPhase -= 2.0d * Math.PI;
                    }
                }

                int clamped = Math.round(out);
                if (clamped > 32767) clamped = 32767;
                if (clamped < -32768) clamped = -32768;

                buffer.put(byteIdx, (byte) (clamped & 0xFF));
                buffer.put(byteIdx + 1, (byte) ((clamped >> 8) & 0xFF));

                writeIndex = (writeIndex + 1) & MASK;
            }
        }

        private float readLinear(float pos) {
            int idx0 = ((int) pos) & MASK;
            int idx1 = (idx0 + 1) & MASK;
            float frac = pos - (int) pos;
            return ring[idx0] * (1.0f - frac) + ring[idx1] * frac;
        }
    }
}
