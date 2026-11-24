package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MidoMemoryManager;
import org.telegram.messenger.MidoMemoryManager.AppointmentEntry;
import org.telegram.messenger.MidoMemoryManager.MemoryCard;
import org.telegram.messenger.MidoMemoryManager.TopicFolder;
import org.telegram.messenger.MidoMemoryManager.TopicMessage;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Unified Telegram-native screen for:
 * 1) Cross-chat Virtual Topic Folders (drill down into messages gathered across channels/groups/chats).
 * 2) All Per-Chat Memory Cards & Appointments.
 */
public class MidoMemoryAndTopicsActivity extends BaseFragment {

    private static final int TAB_TOPICS = 0;
    private static final int TAB_CARDS = 1;

    private UniversalRecyclerView listView;
    private int currentTab = TAB_TOPICS;
    private String openedFolderId = null;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frame;

        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle("الذاكرة والمجلدات الموضوعية");
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (openedFolderId != null) {
                        openedFolderId = null;
                        updateTitleAndList();
                    } else {
                        finishFragment();
                    }
                }
            }
        });

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.setCaptureSectionsDecoratorAllowed(true);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        listView.setPadding(0, ActionBar.getCurrentActionBarHeight() + dp(10), 0, dp(20));
        listView.setClipToPadding(false);

        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        frame.addView(simpleActionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        return fragmentView;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (openedFolderId != null) {
            if (invoked) {
                openedFolderId = null;
                updateTitleAndList();
            }
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private void updateTitleAndList() {
        if (openedFolderId != null) {
            TopicFolder f = MidoMemoryManager.getFolderById(openedFolderId);
            simpleActionBar.setTitle(f != null ? f.name : "مجلد موضوعي");
        } else {
            simpleActionBar.setTitle("الذاكرة والمجلدات الموضوعية");
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        Context context = getContext();
        if (context == null) return;

        if (openedFolderId != null) {
            TopicFolder f = MidoMemoryManager.getFolderById(openedFolderId);
            items.add(UItem.asHeader("الرسائل المتجمعة من القنوات والمجموعات (" + (f != null ? f.messages.size() : 0) + ")"));
            if (f == null || f.messages.isEmpty()) {
                items.add(UItem.asButton(1, "هذا المجلد فارغ حالياً", "اضغط على أي رسالة في أي قناة أو مجموعة ➔ حفظ في مجلد موضوعي 📂"));
            } else {
                for (int i = 0; i < f.messages.size(); i++) {
                    TopicMessage tm = f.messages.get(i);
                    UItem item = UItem.asCustom(300 + i, new TopicMessageRow(context, tm, f.color));
                    item.object = tm;
                    items.add(item);
                }
            }
            items.add(UItem.asShadow("اضغط على أي رسالة للانتقال مباشرة إلى نفس المنشور داخل قناته أو مجموعته الأصلية."));
            return;
        }

        items.add(UItem.asCustomShadow(10, new TabBarView(context)));

        if (currentTab == TAB_TOPICS) {
            items.add(UItem.asHeader("مجلدات تجميع الرسائل عبر المحادثات 📂"));
            items.add(UItem.asButton(11, "+ إنشاء مجلد موضوعي جديد", "اجمع منشورات متشابهة من قنوات وجروبات مختلفة في مكان واحد محلياً"));

            ArrayList<TopicFolder> folders = MidoMemoryManager.getFolders();
            for (int i = 0; i < folders.size(); i++) {
                TopicFolder f = folders.get(i);
                UItem row = UItem.asCustom(100 + i, new FolderRow(context, f));
                row.object = f;
                items.add(row);
            }
            items.add(UItem.asShadow("• اضغط على أي مجلد لعرض كل الرسائل المحفوظة بداخله.\n• لإضافة رسالة: اضغط عليها داخل أي محادثة أو قناة واختر (حفظ في مجلد موضوعي 📂)."));
        } else {
            ArrayList<MemoryCard> cards = MidoMemoryManager.getAllCards();
            items.add(UItem.asHeader("بطاقات الذاكرة والمواعيد المسجلة (" + cards.size() + ")"));
            if (cards.isEmpty()) {
                items.add(UItem.asButton(12, "لا توجد بطاقات ذاكرة مسجلة بعد", "افتح أي محادثة واضغط (⋮ ➔ بطاقة الذاكرة والمواعيد 📝)"));
            } else {
                for (int i = 0; i < cards.size(); i++) {
                    MemoryCard c = cards.get(i);
                    UItem row = UItem.asCustom(200 + i, new CardRow(context, c));
                    row.object = c;
                    items.add(row);
                }
            }
            items.add(UItem.asShadow("جميع ملاحظاتك ومواعيدك محفوظة محلياً فقط على جهازك ولا يراها أحد غيرك."));
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 11) {
            showCreateFolderDialog();
        } else if (item.object instanceof TopicFolder) {
            openedFolderId = ((TopicFolder) item.object).id;
            updateTitleAndList();
        } else if (item.object instanceof TopicMessage) {
            jumpToTopicMessage((TopicMessage) item.object);
        } else if (item.object instanceof MemoryCard) {
            MemoryCard card = (MemoryCard) item.object;
            MidoMemorySheetHelper.showChatMemorySheet(this, card.dialogId, card.title, card.username, this::updateTitleAndList);
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (getParentActivity() == null) return false;
        if (item.object instanceof TopicFolder) {
            TopicFolder f = (TopicFolder) item.object;
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setTitle(f.name);
            b.setItems(new CharSequence[]{"حذف المجلد 🗑"}, (d, w) -> {
                MidoMemoryManager.deleteFolder(f.id);
                updateTitleAndList();
                BulletinFactory.of(this).createSuccessBulletin("تم حذف المجلد").show();
            });
            showDialog(b.create());
            return true;
        } else if (item.object instanceof TopicMessage && openedFolderId != null) {
            TopicMessage tm = (TopicMessage) item.object;
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
            b.setTitle(tm.chatTitle);
            b.setItems(new CharSequence[]{
                    "الانتقال للرسالة الأصلية 🔗",
                    "نسخ نص الرسالة 📋",
                    "إزالة من المجلد 🗑"
            }, (d, w) -> {
                if (w == 0) {
                    jumpToTopicMessage(tm);
                } else if (w == 1) {
                    AndroidUtilities.addToClipboard(tm.previewText);
                    BulletinFactory.of(this).createSuccessBulletin("تم النسخ").show();
                } else {
                    MidoMemoryManager.removeMessageFromFolder(openedFolderId, tm.dialogId, tm.messageId);
                    updateTitleAndList();
                }
            });
            showDialog(b.create());
            return true;
        }
        return false;
    }

    private void showCreateFolderDialog() {
        if (getParentActivity() == null) return;
        Context context = getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("إنشاء مجلد موضوعي جديد 📂");

        FrameLayout box = new FrameLayout(context);
        box.setPadding(dp(24), dp(8), dp(24), dp(4));
        EditTextBoldCursor et = new EditTextBoldCursor(context);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        et.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        et.setLineColors(
                Theme.getColor(Theme.key_dialogInputField),
                Theme.getColor(Theme.key_dialogInputFieldActivated),
                Theme.getColor(Theme.key_text_RedRegular)
        );
        et.setCursorColor(Theme.getColor(Theme.key_dialogInputFieldActivated));
        et.setHint("مثلاً: صفقات / كورسات / أخبار...");
        box.addView(et, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40));

        builder.setView(box);
        builder.setPositiveButton("إنشاء", (d, w) -> {
            String name = et.getText().toString().trim();
            if (!TextUtils.isEmpty(name)) {
                MidoMemoryManager.createFolder(name, 0);
                updateTitleAndList();
            }
        });
        builder.setNegativeButton("إلغاء", null);
        showDialog(builder.create());
    }

    private void jumpToTopicMessage(TopicMessage tm) {
        MessagesController mc = MessagesController.getInstance(currentAccount);
        Bundle args = new Bundle();
        if (tm.dialogId < 0) {
            long cid = -tm.dialogId;
            if (mc.getChat(cid) == null && tm.accessHash != 0) {
                TLRPC.TL_channel stub = new TLRPC.TL_channel();
                stub.id = cid;
                stub.access_hash = tm.accessHash;
                stub.title = tm.chatTitle;
                stub.username = tm.chatUsername;
                mc.putChat(stub, true);
            }
            if (mc.getChat(cid) != null) {
                args.putLong("chat_id", cid);
                args.putInt("message_id", tm.messageId);
                presentFragment(new ChatActivity(args));
            } else if (!TextUtils.isEmpty(tm.chatUsername)) {
                mc.openByUserName(tm.chatUsername, this, 1);
            }
        } else {
            if (mc.getUser(tm.dialogId) != null) {
                args.putLong("user_id", tm.dialogId);
                args.putInt("message_id", tm.messageId);
                presentFragment(new ChatActivity(args));
            } else if (!TextUtils.isEmpty(tm.chatUsername)) {
                mc.openByUserName(tm.chatUsername, this, 1);
            }
        }
    }

    private class TabBarView extends LinearLayout {
        public TabBarView(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setPadding(dp(12), dp(2), dp(12), dp(8));

            addTab(context, TAB_TOPICS, "مجلدات المواضيع 📂");
            addTab(context, TAB_CARDS, "بطاقات الذاكرة والمواعيد 📝");
        }

        private void addTab(Context context, int tabId, String label) {
            boolean sel = currentTab == tabId;
            TextView tv = new TextView(context);
            tv.setText(label);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            tv.setTypeface(AndroidUtilities.bold());
            tv.setPadding(dp(10), dp(9), dp(10), dp(9));
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (sel) {
                tv.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
                tv.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                        dp(14), accent, Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
            } else {
                tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                tv.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                        dp(14), Theme.getColor(Theme.key_windowBackgroundWhite), Theme.multAlpha(accent, 0.14f)));
            }
            tv.setOnClickListener(v -> {
                if (currentTab != tabId) {
                    currentTab = tabId;
                    updateTitleAndList();
                }
            });
            addView(tv, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, tabId == 0 ? 0 : 4, 0, tabId == 0 ? 4 : 0, 0));
        }
    }

    private static class FolderRow extends LinearLayout {
        public FolderRow(Context context, TopicFolder f) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(14), dp(12), dp(14), dp(12));

            View dot = new View(context);
            dot.setBackground(Theme.createRoundRectDrawable(dp(12), f.color));
            addView(dot, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(VERTICAL);
            addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            TextView title = new TextView(context);
            title.setText(f.name);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            col.addView(title);

            TextView sub = new TextView(context);
            sub.setText(f.messages.isEmpty() ? "لا توجد رسائل محفوظة بعد" : ("يحتوي على " + f.messages.size() + " رسالة مترابطة"));
            sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            col.addView(sub);

            TextView badge = new TextView(context);
            badge.setText(String.valueOf(f.messages.size()));
            badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            badge.setTypeface(AndroidUtilities.bold());
            badge.setTextColor(f.color);
            badge.setPadding(dp(10), dp(4), dp(10), dp(4));
            badge.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.multAlpha(f.color, 0.14f)));
            addView(badge);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    private static class TopicMessageRow extends LinearLayout {
        private static final SimpleDateFormat FMT = new SimpleDateFormat("dd MMM • HH:mm", Locale.US);

        public TopicMessageRow(Context context, TopicMessage m, int accentColor) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(14), dp(11), dp(14), dp(11));

            LinearLayout top = new LinearLayout(context);
            top.setOrientation(HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            addView(top, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 5));

            TextView sourcePill = new TextView(context);
            sourcePill.setText("من: " + m.chatTitle);
            sourcePill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            sourcePill.setTypeface(AndroidUtilities.bold());
            sourcePill.setTextColor(accentColor);
            sourcePill.setPadding(dp(8), dp(3), dp(8), dp(3));
            sourcePill.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.multAlpha(accentColor, 0.13f)));
            top.addView(sourcePill, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

            TextView ts = new TextView(context);
            ts.setText(FMT.format(new Date(m.savedAtMs)));
            ts.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            ts.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            top.addView(ts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            TextView body = new TextView(context);
            body.setText(m.previewText);
            body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            body.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            body.setMaxLines(3);
            body.setEllipsize(TextUtils.TruncateAt.END);
            addView(body);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    private class CardRow extends LinearLayout {
        public CardRow(Context context, MemoryCard c) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(14), dp(10), dp(14), dp(10));

            AvatarDrawable ad = new AvatarDrawable();
            ad.setInfo(c.dialogId, !TextUtils.isEmpty(c.title) ? c.title : "C", null);
            BackupImageView av = new BackupImageView(context);
            av.setRoundRadius(dp(21));
            av.setImageDrawable(ad);
            addView(av, LayoutHelper.createLinear(42, 42, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(VERTICAL);
            addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            TextView title = new TextView(context);
            title.setText(c.title);
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            col.addView(title);

            String summary = "";
            if (!c.notes.isEmpty()) {
                summary = "📝 " + c.notes.get(0).text;
            } else if (!c.appointments.isEmpty()) {
                AppointmentEntry a = c.appointments.get(0);
                summary = "📅 " + a.title + " (" + a.whenText + ")";
            }
            TextView sub = new TextView(context);
            sub.setText(summary);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            sub.setSingleLine();
            sub.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(sub);

            TextView badge = new TextView(context);
            badge.setText(c.notes.size() + "📝  " + c.appointments.size() + "📅");
            badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            badge.setTypeface(AndroidUtilities.bold());
            int blue = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);
            badge.setTextColor(blue);
            badge.setPadding(dp(8), dp(4), dp(8), dp(4));
            badge.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.multAlpha(blue, 0.12f)));
            addView(badge);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }
}
