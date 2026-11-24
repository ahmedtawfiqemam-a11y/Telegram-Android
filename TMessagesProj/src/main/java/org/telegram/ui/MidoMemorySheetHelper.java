package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MidoMemoryManager;
import org.telegram.messenger.MidoMemoryManager.AppointmentEntry;
import org.telegram.messenger.MidoMemoryManager.MemoryCard;
import org.telegram.messenger.MidoMemoryManager.NoteEntry;
import org.telegram.messenger.MidoMemoryManager.TopicFolder;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Native Telegram BottomSheets for per-chat Memory Cards (notes & reminders)
 * and saving cross-chat messages into local Virtual Topic Folders.
 */
public class MidoMemorySheetHelper {

    public static void showChatMemorySheet(
            BaseFragment fragment,
            long dialogId,
            String chatTitle,
            String username,
            Runnable onUpdated
    ) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        Context context = fragment.getParentActivity();
        Theme.ResourcesProvider rp = fragment.getResourceProvider();

        BottomSheet.Builder builder = new BottomSheet.Builder(context, true, rp);
        ScrollView scrollView = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(22));
        scrollView.addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final long[] selectedApMs = new long[]{System.currentTimeMillis() + 86_400_000L};
        final String[] selectedWhenText = new String[]{"غداً"};

        // Header
        TextView titleTv = new TextView(context);
        titleTv.setText("بطاقة الذاكرة: " + (!TextUtils.isEmpty(chatTitle) ? chatTitle : ("#" + dialogId)));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleTv.setTypeface(AndroidUtilities.bold());
        titleTv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        root.addView(titleTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView subTv = new TextView(context);
        subTv.setText("ملاحظات ومواعيد محلية خاصة بك لا تظهر للطرف الآخر");
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        subTv.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, rp));
        root.addView(subTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Add Note Row
        TextView notesHdr = new TextView(context);
        notesHdr.setText("📝 إضافة ملاحظة خاصة");
        notesHdr.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        notesHdr.setTypeface(AndroidUtilities.bold());
        notesHdr.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, rp));
        root.addView(notesHdr, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        LinearLayout addNoteRow = new LinearLayout(context);
        addNoteRow.setOrientation(LinearLayout.HORIZONTAL);
        addNoteRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(addNoteRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        EditTextBoldCursor noteInput = createStyledInput(context, rp, "مثلاً: حساب العمل / رقم الهاتف / ملاحظة...");
        addNoteRow.addView(noteInput, LayoutHelper.createLinear(0, 42, 1f, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        TextView addNoteBtn = createPillButton(context, rp, "حفظ 📝", Theme.getColor(Theme.key_featuredStickers_addButton, rp), true);
        addNoteRow.addView(addNoteBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38, Gravity.CENTER_VERTICAL));

        LinearLayout notesContainer = new LinearLayout(context);
        notesContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(notesContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Add Appointment Row
        TextView apHdr = new TextView(context);
        apHdr.setText("📅 مواعيدي والتزاماتي مع هذا الشخص");
        apHdr.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        apHdr.setTypeface(AndroidUtilities.bold());
        apHdr.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, rp));
        root.addView(apHdr, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        EditTextBoldCursor apInput = createStyledInput(context, rp, "عنوان الموعد (مثلاً: مراجعة العرض، مكالمة)...");
        root.addView(apInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 0, 0, 0, 8));

        LinearLayout whenRow = new LinearLayout(context);
        whenRow.setOrientation(LinearLayout.HORIZONTAL);
        whenRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(whenRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        TextView datePickBtn = createPillButton(context, rp, "التوقيت: غداً ⏰", Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, rp), false);
        datePickBtn.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(context, (dpView, y, m, d) -> {
                cal.set(y, m, d);
                new TimePickerDialog(context, (tpView, hh, mm) -> {
                    cal.set(Calendar.HOUR_OF_DAY, hh);
                    cal.set(Calendar.MINUTE, mm);
                    selectedApMs[0] = cal.getTimeInMillis();
                    SimpleDateFormat fmt = new SimpleDateFormat("dd MMM • HH:mm", Locale.getDefault());
                    selectedWhenText[0] = fmt.format(cal.getTime());
                    datePickBtn.setText("📅 " + selectedWhenText[0]);
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        whenRow.addView(datePickBtn, LayoutHelper.createLinear(0, 38, 1f, 0, 0, 8, 0));

        TextView addApBtn = createPillButton(context, rp, "إضافة موعد ✓", 0xFF1FA75A, true);
        whenRow.addView(addApBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38));

        LinearLayout appsContainer = new LinearLayout(context);
        appsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(appsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Link to full Memory & Topic Folders screen
        TextView openAllBtn = createPillButton(context, rp, "عرض كل البطاقات والمجلدات الموضوعية 📂", Theme.getColor(Theme.key_featuredStickers_addButton, rp), false);
        root.addView(openAllBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42));

        final SimpleDateFormat timeFmt = new SimpleDateFormat("dd/MM HH:mm", Locale.US);
        Runnable[] rebuildLists = new Runnable[1];
        rebuildLists[0] = () -> {
            notesContainer.removeAllViews();
            appsContainer.removeAllViews();
            MemoryCard card = MidoMemoryManager.getCard(dialogId);

            if (card == null || card.notes.isEmpty()) {
                TextView empty = new TextView(context);
                empty.setText("لا توجد ملاحظات مسجلة بعد.");
                empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
                empty.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, rp));
                notesContainer.addView(empty);
            } else {
                for (int i = 0; i < card.notes.size(); i++) {
                    NoteEntry n = card.notes.get(i);
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(12), dp(9), dp(10), dp(9));
                    row.setBackground(Theme.createRoundRectDrawable(dp(12),
                            Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, rp), 0.08f)));

                    LinearLayout col = new LinearLayout(context);
                    col.setOrientation(LinearLayout.VERTICAL);
                    row.addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

                    TextView txt = new TextView(context);
                    txt.setText(n.text);
                    txt.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    txt.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
                    col.addView(txt);

                    TextView ts = new TextView(context);
                    ts.setText(timeFmt.format(new Date(n.createdAtMs)));
                    ts.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
                    ts.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, rp));
                    col.addView(ts);

                    TextView del = new TextView(context);
                    del.setText("حذف");
                    del.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                    del.setTypeface(AndroidUtilities.bold());
                    del.setTextColor(0xFFE53935);
                    del.setPadding(dp(8), dp(4), dp(8), dp(4));
                    del.setOnClickListener(v -> {
                        MidoMemoryManager.deleteNote(dialogId, n.id);
                        rebuildLists[0].run();
                        if (onUpdated != null) onUpdated.run();
                    });
                    row.addView(del);

                    row.setOnClickListener(v -> {
                        AndroidUtilities.addToClipboard(n.text);
                        BulletinFactory.of(fragment).createSuccessBulletin("تم نسخ الملاحظة").show();
                    });
                    notesContainer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
                }
            }

            if (card == null || card.appointments.isEmpty()) {
                TextView empty = new TextView(context);
                empty.setText("لا توجد مواعيد مضافة لهذا الشخص.");
                empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
                empty.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, rp));
                appsContainer.addView(empty);
            } else {
                for (int i = 0; i < card.appointments.size(); i++) {
                    AppointmentEntry a = card.appointments.get(i);
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(12), dp(9), dp(10), dp(9));
                    int tint = a.done ? 0xFF1FA75A : 0xFFF59E0B;
                    row.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.multAlpha(tint, 0.10f)));

                    LinearLayout col = new LinearLayout(context);
                    col.setOrientation(LinearLayout.VERTICAL);
                    row.addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

                    TextView txt = new TextView(context);
                    txt.setText((a.done ? "✓ " : "⏰ ") + a.title);
                    txt.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    txt.setTypeface(AndroidUtilities.bold());
                    txt.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
                    if (a.done) {
                        txt.setPaintFlags(txt.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    }
                    col.addView(txt);

                    if (!TextUtils.isEmpty(a.whenText)) {
                        TextView when = new TextView(context);
                        when.setText(a.whenText);
                        when.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
                        when.setTextColor(tint);
                        col.addView(when);
                    }

                    TextView del = new TextView(context);
                    del.setText("حذف");
                    del.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                    del.setTypeface(AndroidUtilities.bold());
                    del.setTextColor(0xFFE53935);
                    del.setPadding(dp(8), dp(4), dp(8), dp(4));
                    del.setOnClickListener(v -> {
                        MidoMemoryManager.deleteAppointment(dialogId, a.id);
                        rebuildLists[0].run();
                        if (onUpdated != null) onUpdated.run();
                    });
                    row.addView(del);

                    row.setOnClickListener(v -> {
                        MidoMemoryManager.toggleAppointmentDone(dialogId, a.id);
                        rebuildLists[0].run();
                        if (onUpdated != null) onUpdated.run();
                    });
                    appsContainer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
                }
            }
        };
        rebuildLists[0].run();

        addNoteBtn.setOnClickListener(v -> {
            String txt = noteInput.getText().toString().trim();
            if (TextUtils.isEmpty(txt)) return;
            MidoMemoryManager.addNote(dialogId, chatTitle, username, txt);
            noteInput.setText("");
            rebuildLists[0].run();
            if (onUpdated != null) onUpdated.run();
        });

        addApBtn.setOnClickListener(v -> {
            String ap = apInput.getText().toString().trim();
            if (TextUtils.isEmpty(ap)) return;
            MidoMemoryManager.addAppointment(dialogId, chatTitle, username, ap, selectedWhenText[0], selectedApMs[0]);
            apInput.setText("");
            rebuildLists[0].run();
            if (onUpdated != null) onUpdated.run();
        });

        builder.setCustomView(scrollView);
        BottomSheet sheet = builder.create();
        openAllBtn.setOnClickListener(v -> {
            sheet.dismiss();
            fragment.presentFragment(new MidoMemoryAndTopicsActivity());
        });
        sheet.show();
    }

    public static void showSaveToTopicFolderSheet(
            BaseFragment fragment,
            long dialogId,
            long accessHash,
            int messageId,
            String chatTitle,
            String chatUsername,
            String senderName,
            String previewText
    ) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        Context context = fragment.getParentActivity();
        Theme.ResourcesProvider rp = fragment.getResourceProvider();

        BottomSheet.Builder builder = new BottomSheet.Builder(context, true, rp);
        ScrollView scrollView = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(22));
        scrollView.addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView titleTv = new TextView(context);
        titleTv.setText("حفظ في مجلد موضوعي محلي 📂");
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleTv.setTypeface(AndroidUtilities.bold());
        titleTv.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        root.addView(titleTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView msgPreview = new TextView(context);
        String cleanSnippet = previewText != null ? previewText.replace("\n", " ").trim() : "[رسالة]";
        if (cleanSnippet.length() > 90) cleanSnippet = cleanSnippet.substring(0, 90) + "...";
        msgPreview.setText("من: " + chatTitle + "\n\"" + cleanSnippet + "\"");
        msgPreview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        msgPreview.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, rp));
        msgPreview.setPadding(dp(12), dp(8), dp(12), dp(8));
        msgPreview.setBackground(Theme.createRoundRectDrawable(dp(12),
                Theme.multAlpha(Theme.getColor(Theme.key_dialogTextGray3, rp), 0.08f)));
        root.addView(msgPreview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // Quick Create New Topic Folder
        LinearLayout createRow = new LinearLayout(context);
        createRow.setOrientation(LinearLayout.HORIZONTAL);
        createRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(createRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        EditTextBoldCursor folderInput = createStyledInput(context, rp, "اسم مجلد جديد (مثلاً: الذكاء الاصطناعي)...");
        createRow.addView(folderInput, LayoutHelper.createLinear(0, 42, 1f, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        TextView createBtn = createPillButton(context, rp, "إنشاء وحفظ +", 0xFF1FA75A, true);
        createRow.addView(createBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 38, Gravity.CENTER_VERTICAL));

        LinearLayout foldersList = new LinearLayout(context);
        foldersList.setOrientation(LinearLayout.VERTICAL);
        root.addView(foldersList, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setCustomView(scrollView);
        BottomSheet sheet = builder.create();

        ArrayList<TopicFolder> folders = MidoMemoryManager.getFolders();
        for (int i = 0; i < folders.size(); i++) {
            TopicFolder f = folders.get(i);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(11), dp(14), dp(11));
            row.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                    dp(12),
                    Theme.multAlpha(f.color, 0.10f),
                    Theme.multAlpha(f.color, 0.22f)
            ));

            View dot = new View(context);
            dot.setBackground(Theme.createRoundRectDrawable(dp(6), f.color));
            row.addView(dot, LayoutHelper.createLinear(12, 12, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

            TextView name = new TextView(context);
            name.setText(f.name);
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            name.setTypeface(AndroidUtilities.bold());
            name.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
            row.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            TextView cnt = new TextView(context);
            cnt.setText(f.messages.size() + " رسالة");
            cnt.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            cnt.setTypeface(AndroidUtilities.bold());
            cnt.setTextColor(f.color);
            row.addView(cnt);

            row.setOnClickListener(v -> {
                boolean added = MidoMemoryManager.saveMessageToFolder(
                        f.id, dialogId, accessHash, messageId, chatTitle, chatUsername, senderName, previewText
                );
                sheet.dismiss();
                BulletinFactory.of(fragment).createSuccessBulletin(
                        added ? ("تم تجميع الرسالة في: " + f.name) : "الرسالة موجودة بالفعل في هذا المجلد"
                ).show();
            });
            foldersList.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        }

        createBtn.setOnClickListener(v -> {
            String name = folderInput.getText().toString().trim();
            if (TextUtils.isEmpty(name)) return;
            TopicFolder newF = MidoMemoryManager.createFolder(name, 0);
            if (newF != null) {
                MidoMemoryManager.saveMessageToFolder(
                        newF.id, dialogId, accessHash, messageId, chatTitle, chatUsername, senderName, previewText
                );
                sheet.dismiss();
                BulletinFactory.of(fragment).createSuccessBulletin("تم إنشاء المجلد (" + newF.name + ") وحفظ الرسالة 📂").show();
            }
        });

        sheet.show();
    }

    private static EditTextBoldCursor createStyledInput(Context context, Theme.ResourcesProvider rp, String hint) {
        EditTextBoldCursor et = new EditTextBoldCursor(context);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        et.setHintColor(Theme.getColor(Theme.key_dialogTextHint, rp));
        et.setLineColors(
                Theme.getColor(Theme.key_dialogInputField, rp),
                Theme.getColor(Theme.key_dialogInputFieldActivated, rp),
                Theme.getColor(Theme.key_text_RedRegular, rp)
        );
        et.setCursorColor(Theme.getColor(Theme.key_dialogInputFieldActivated, rp));
        et.setHint(hint);
        return et;
    }

    private static TextView createPillButton(Context context, Theme.ResourcesProvider rp, String label, int color, boolean filled) {
        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setPadding(dp(12), dp(6), dp(12), dp(6));
        if (filled) {
            tv.setTextColor(Color.WHITE);
            tv.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(10), color, Theme.multAlpha(color, 0.8f)));
        } else {
            tv.setTextColor(color);
            tv.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(10), Theme.multAlpha(color, 0.12f), Theme.multAlpha(color, 0.24f)));
        }
        return tv;
    }
}
