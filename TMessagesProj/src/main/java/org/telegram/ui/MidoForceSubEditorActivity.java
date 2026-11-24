package org.telegram.ui;

import android.content.Context;
import android.graphics.Paint;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MidoConfig;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.QuoteSpan;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanReplacement;

import java.util.ArrayList;

public class MidoForceSubEditorActivity extends BaseFragment {

    private static final int DONE_BUTTON = 1;

    private SizeNotifierFrameLayout contentView;
    private ScrollView scrollView;
    private EditTextEmoji editTextEmoji;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("تعديل رسالة الاشتراك");

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItemWithWidth(DONE_BUTTON, R.drawable.ic_ab_done, AndroidUtilities.dp(56));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == DONE_BUTTON) {
                    saveAndFinish();
                }
            }
        });

        contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (scrollView != null && editTextEmoji != null) {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) scrollView.getLayoutParams();
                    int pad = editTextEmoji.getEmojiPadding();
                    if (lp.bottomMargin != pad) {
                        lp.bottomMargin = pad;
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = contentView;

        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        contentView.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Main editor card
        LinearLayout editorCard = new LinearLayout(context);
        editorCard.setOrientation(LinearLayout.VERTICAL);
        editorCard.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        editorCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        container.addView(editorCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        TextView label = new TextView(context);
        label.setText("نص رسالة الاشتراك الإجباري (يدعم الإيموجي المميز والتنسيقات)");
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        label.setTypeface(AndroidUtilities.bold());
        label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        editorCard.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Formatting toolbar
        HorizontalScrollView formatScroll = new HorizontalScrollView(context);
        formatScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout formatBar = new LinearLayout(context);
        formatBar.setOrientation(LinearLayout.HORIZONTAL);
        formatScroll.addView(formatBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        editorCard.addView(formatScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        addFormatButton(context, formatBar, "عريض (Bold)", () -> applySelectionStyle(0));
        addFormatButton(context, formatBar, "مائل (Italic)", () -> applySelectionStyle(1));
        addFormatButton(context, formatBar, "مونو (Mono)", () -> applySelectionStyle(2));
        addFormatButton(context, formatBar, "مخفي (Spoiler)", () -> applySelectionStyle(3));
        addFormatButton(context, formatBar, "تحته خط", () -> applySelectionStyle(4));
        addFormatButton(context, formatBar, "مشطوب", () -> applySelectionStyle(5));
        addFormatButton(context, formatBar, "اقتباس (Quote)", () -> {
            if (editTextEmoji != null) {
                editTextEmoji.getEditText().makeSelectedQuote();
            }
        });
        addFormatButton(context, formatBar, "رابط 🔗", () -> {
            if (editTextEmoji != null) {
                editTextEmoji.getEditText().makeSelectedUrl();
            }
        });
        addFormatButton(context, formatBar, "إزالة التنسيق", () -> applySelectionStyle(6));

        editTextEmoji = new EditTextEmoji(context, contentView, this, EditTextEmoji.STYLE_FRAGMENT, true) {
            @Override
            protected boolean allowEntities() {
                return true;
            }

            @Override
            protected void createEmojiView() {
                super.createEmojiView();
                if (getEmojiView() != null && getEmojiView().getLayoutParams() instanceof FrameLayout.LayoutParams) {
                    ((FrameLayout.LayoutParams) getEmojiView().getLayoutParams()).gravity = Gravity.BOTTOM;
                }
            }
        };
        editTextEmoji.getEditText().setAllowTextEntitiesIntersection(true);
        editTextEmoji.setMaxLines(12);
        editTextEmoji.setHint("اكتب رسالة الاشتراك هنا... واضغط على أيقونة 😊 لإضافة الإيموجي المميز");
        editorCard.addView(editTextEmoji, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        SpannableStringBuilder initial = new SpannableStringBuilder(MidoConfig.forceSubMessage != null ? MidoConfig.forceSubMessage : "");
        applyEntitiesToEditable(initial, MidoConfig.getForceSubEntitiesCopy(), editTextEmoji.getEditText().getPaint().getFontMetricsInt());
        editTextEmoji.setText(initial);
        editTextEmoji.setSelection(initial.length());

        // Placeholders card
        LinearLayout varsCard = new LinearLayout(context);
        varsCard.setOrientation(LinearLayout.VERTICAL);
        varsCard.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        varsCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        container.addView(varsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        TextView varsTitle = new TextView(context);
        varsTitle.setText("متغيرات تلقائية (اضغط للإدراج):");
        varsTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        varsTitle.setTypeface(AndroidUtilities.bold());
        varsTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        varsCard.addView(varsTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        HorizontalScrollView varsScroll = new HorizontalScrollView(context);
        varsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout varsBar = new LinearLayout(context);
        varsBar.setOrientation(LinearLayout.HORIZONTAL);
        varsScroll.addView(varsBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        varsCard.addView(varsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        addFormatButton(context, varsBar, "#FirstName (الاسم)", () -> insertAtCursor("#FirstName"));
        addFormatButton(context, varsBar, "#UserName (اليوزر)", () -> insertAtCursor("#UserName"));
        addFormatButton(context, varsBar, "#ChannelLink (القناة)", () -> insertAtCursor("#ChannelLink"));

        TextView tipText = new TextView(context);
        tipText.setText("• إذا كنت مشتركاً في Telegram Premium يمكنك استخدام الإيموجي المميز المتحرك مباشرة من زر الإيموجي (😊).\n• حدد أي نص واضغط على أحد أزرار التنسيق بالأعلى أو من القائمة المنبثقة (غامق / مونو / اقتباس / مخفي / رابط).");
        tipText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tipText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        tipText.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(20));
        container.addView(tipText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return fragmentView;
    }

    private void addFormatButton(Context context, LinearLayout parent, String title, Runnable action) {
        TextView btn = new TextView(context);
        btn.setText(title);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        btn.setTypeface(AndroidUtilities.bold());
        btn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        btn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4), 0.12f)));
        btn.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        btn.setOnClickListener(v -> action.run());
        parent.addView(btn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));
    }

    private void applySelectionStyle(int mode) {
        if (editTextEmoji == null) {
            return;
        }
        int start = editTextEmoji.getEditText().getSelectionStart();
        int end = editTextEmoji.getEditText().getSelectionEnd();
        if (start == end) {
            if (getParentActivity() != null) {
                Toast.makeText(getParentActivity(), "ظلّل النص أولاً ثم اضغط على التنسيق", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        switch (mode) {
            case 0: editTextEmoji.getEditText().makeSelectedBold(); break;
            case 1: editTextEmoji.getEditText().makeSelectedItalic(); break;
            case 2: editTextEmoji.getEditText().makeSelectedMono(); break;
            case 3: editTextEmoji.getEditText().makeSelectedSpoiler(); break;
            case 4: editTextEmoji.getEditText().makeSelectedUnderline(); break;
            case 5: editTextEmoji.getEditText().makeSelectedStrike(); break;
            case 6: editTextEmoji.getEditText().makeSelectedRegular(); break;
        }
    }

    private void insertAtCursor(String token) {
        if (editTextEmoji == null || editTextEmoji.getText() == null) {
            return;
        }
        Editable editable = editTextEmoji.getText();
        int start = Math.max(0, editTextEmoji.getEditText().getSelectionStart());
        int end = Math.max(0, editTextEmoji.getEditText().getSelectionEnd());
        int min = Math.min(start, end);
        int max = Math.max(start, end);
        editable.replace(min, max, token);
    }

    private void saveAndFinish() {
        if (editTextEmoji == null) {
            finishFragment();
            return;
        }
        CharSequence[] mess = new CharSequence[]{editTextEmoji.getText()};
        ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(currentAccount).getEntities(mess, true);
        String val = mess[0] != null ? mess[0].toString().trim() : "";
        if (TextUtils.isEmpty(val)) {
            if (getParentActivity() != null) {
                Toast.makeText(getParentActivity(), "لا يمكن حفظ رسالة فارغة", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (entities != null) {
            int maxLen = val.length();
            for (int i = entities.size() - 1; i >= 0; i--) {
                TLRPC.MessageEntity e = entities.get(i);
                if (e.offset >= maxLen) {
                    entities.remove(i);
                } else if (e.offset + e.length > maxLen) {
                    e.length = maxLen - e.offset;
                }
            }
        }
        MidoConfig.setForceSubMessage(val, entities);
        finishFragment();
    }

    private static void applyEntitiesToEditable(SpannableStringBuilder sb, ArrayList<TLRPC.MessageEntity> entities, Paint.FontMetricsInt fontMetrics) {
        if (sb == null || entities == null || entities.isEmpty()) {
            return;
        }
        int len = sb.length();
        for (int a = 0; a < entities.size(); a++) {
            TLRPC.MessageEntity entity = entities.get(a);
            if (entity.offset < 0 || entity.length <= 0 || entity.offset >= len) {
                continue;
            }
            int end = Math.min(len, entity.offset + entity.length);
            try {
                if (entity instanceof TLRPC.TL_messageEntityCode || entity instanceof TLRPC.TL_messageEntityPre) {
                    TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                    run.flags |= TextStyleSpan.FLAG_STYLE_MONO;
                    MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, end, sb, true);
                } else if (entity instanceof TLRPC.TL_messageEntityBold) {
                    TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                    run.flags |= TextStyleSpan.FLAG_STYLE_BOLD;
                    MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, end, sb, true);
                } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
                    TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                    run.flags |= TextStyleSpan.FLAG_STYLE_ITALIC;
                    MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, end, sb, true);
                } else if (entity instanceof TLRPC.TL_messageEntityStrike) {
                    TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                    run.flags |= TextStyleSpan.FLAG_STYLE_STRIKE;
                    MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, end, sb, true);
                } else if (entity instanceof TLRPC.TL_messageEntityUnderline) {
                    TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                    run.flags |= TextStyleSpan.FLAG_STYLE_UNDERLINE;
                    MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, end, sb, true);
                } else if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                    sb.setSpan(new URLSpanReplacement(entity.url), entity.offset, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (entity instanceof TLRPC.TL_messageEntitySpoiler) {
                    TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                    run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                    MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, end, sb, true);
                } else if (entity instanceof TLRPC.TL_messageEntityBlockquote) {
                    QuoteSpan.putQuoteToEditable(sb, entity.offset, end, entity.collapsed);
                } else if (entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                    TLRPC.TL_messageEntityCustomEmoji ce = (TLRPC.TL_messageEntityCustomEmoji) entity;
                    AnimatedEmojiSpan span = ce.document != null
                            ? new AnimatedEmojiSpan(ce.document, fontMetrics)
                            : new AnimatedEmojiSpan(ce.document_id, fontMetrics);
                    sb.setSpan(span, entity.offset, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (editTextEmoji != null && editTextEmoji.isPopupShowing()) {
            if (invoked) {
                editTextEmoji.hidePopup(true);
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (editTextEmoji != null) {
            editTextEmoji.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (editTextEmoji != null) {
            editTextEmoji.onPause();
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (editTextEmoji != null) {
            editTextEmoji.onDestroy();
        }
    }
}
