package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleActionBarMenu;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

/**
 * Scans joined channels & groups to surface inactive/abandoned ones based on months since last post.
 * Lets users inspect any channel before leaving, select multiple/all, and bulk-leave cleanly.
 */
public class MidoDeadChannelsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int FILTER_3_MONTHS = 3;
    private static final int FILTER_6_MONTHS = 6;
    private static final int FILTER_12_MONTHS = 12;
    private static final int FILTER_1_MONTH = 1;

    private static class DeadChannelEntry {
        final long dialogId;
        final TLRPC.Chat chat;
        final int lastDateSec;
        final int monthsInactive;
        final String lastPostStr;
        final String badgeText;
        final int badgeColor;

        DeadChannelEntry(long dialogId, TLRPC.Chat chat, int lastDateSec, int monthsInactive, String lastPostStr, String badgeText, int badgeColor) {
            this.dialogId = dialogId;
            this.chat = chat;
            this.lastDateSec = lastDateSec;
            this.monthsInactive = monthsInactive;
            this.lastPostStr = lastPostStr;
            this.badgeText = badgeText;
            this.badgeColor = badgeColor;
        }
    }

    private FrameLayout rootLayout;
    private UniversalRecyclerView listView;
    private LinearLayout bottomActionBar;
    private TextView selectedCountTv;
    private TextView selectAllToggleBtn;
    private TextView leaveSelectedBtn;

    private int minMonthsFilter = FILTER_3_MONTHS;
    private boolean includeGroups = false;
    private String searchQuery = "";

    private final ArrayList<DeadChannelEntry> allChannels = new ArrayList<>();
    private final ArrayList<DeadChannelEntry> filteredEntries = new ArrayList<>();
    private final HashSet<Long> selectedIds = new HashSet<>();

    private int count3M;
    private int count6M;
    private int count12M;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogsNeedReload);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload);
        super.onFragmentDestroy();
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);

        rootLayout = new FrameLayout(context);
        rootLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = rootLayout;

        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.isRTL ? "منظّف القنوات المهجورة" : "Dead Channels Cleaner");
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (!selectedIds.isEmpty()) {
                        selectedIds.clear();
                        updateBottomBar();
                        refreshList();
                    } else {
                        finishFragment();
                    }
                } else if (id == 1) {
                    toggleSelectAll();
                }
            }
        });

        SimpleActionBarMenu menu = simpleActionBar.createMenu();
        menu.addItem(0, R.drawable.outline_header_search, resourceProvider)
                .setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchCollapse() {
                        searchQuery = "";
                        refreshList();
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        searchQuery = editText.getText().toString().trim();
                        refreshList();
                    }
                })
                .setSearchFieldHint(LocaleController.isRTL ? "ابحث في القنوات المهجورة..." : "Search dead channels...");

        menu.addItem(1, R.drawable.msg_markread, resourceProvider);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.setCaptureSectionsDecoratorAllowed(true);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        listView.setPadding(0, ActionBar.getCurrentActionBarHeight() + dp(10), 0, dp(76));
        listView.setClipToPadding(false);
        rootLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Floating Bottom Multi-Leave Action Bar
        bottomActionBar = new LinearLayout(context);
        bottomActionBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomActionBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomActionBar.setPadding(dp(14), dp(10), dp(14), dp(10));
        bottomActionBar.setBackground(Theme.createRoundRectDrawable(dp(18), Theme.getColor(Theme.key_windowBackgroundWhite)));
        bottomActionBar.setElevation(dp(4));
        bottomActionBar.setVisibility(View.GONE);

        selectedCountTv = new TextView(context);
        selectedCountTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        selectedCountTv.setTypeface(AndroidUtilities.bold());
        selectedCountTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        bottomActionBar.addView(selectedCountTv, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        selectAllToggleBtn = new TextView(context);
        selectAllToggleBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        selectAllToggleBtn.setTypeface(AndroidUtilities.bold());
        int blue = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);
        selectAllToggleBtn.setTextColor(blue);
        selectAllToggleBtn.setPadding(dp(12), dp(7), dp(12), dp(7));
        selectAllToggleBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(10), Theme.multAlpha(blue, 0.12f), Theme.multAlpha(blue, 0.22f)));
        selectAllToggleBtn.setOnClickListener(v -> toggleSelectAll());
        bottomActionBar.addView(selectAllToggleBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        leaveSelectedBtn = new TextView(context);
        leaveSelectedBtn.setText("مغادرة المحدد 🚪");
        leaveSelectedBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        leaveSelectedBtn.setTypeface(AndroidUtilities.bold());
        leaveSelectedBtn.setTextColor(Color.WHITE);
        leaveSelectedBtn.setPadding(dp(14), dp(7), dp(14), dp(7));
        leaveSelectedBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(10), 0xFFE53935, 0xFFC62828));
        leaveSelectedBtn.setOnClickListener(v -> confirmLeaveSelected());
        bottomActionBar.addView(leaveSelectedBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        rootLayout.addView(bottomActionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 12, 0, 12, 14));
        rootLayout.addView(simpleActionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        scanChannels();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        scanChannels();
        refreshList();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogsNeedReload) {
            scanChannels();
            refreshList();
        }
    }

    private void scanChannels() {
        allChannels.clear();
        count3M = 0;
        count6M = 0;
        count12M = 0;

        MessagesController mc = MessagesController.getInstance(currentAccount);
        LongSparseArray<TLRPC.Dialog> dict = mc.dialogs_dict;
        ArrayList<TLRPC.Dialog> rawDialogs = new ArrayList<>();
        if (dict != null && dict.size() > 0) {
            for (int i = 0; i < dict.size(); i++) {
                TLRPC.Dialog d = dict.valueAt(i);
                if (d != null) rawDialogs.add(d);
            }
        } else {
            rawDialogs.addAll(mc.getAllDialogs());
        }

        long nowSec = System.currentTimeMillis() / 1000L;
        SimpleDateFormat dateFmt = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

        for (int i = 0; i < rawDialogs.size(); i++) {
            TLRPC.Dialog d = rawDialogs.get(i);
            if (d.id >= 0) continue; // Chats & channels have negative dialog IDs

            TLRPC.Chat chat = mc.getChat(-d.id);
            if (chat == null || ChatObject.isNotInChat(chat) || chat instanceof TLRPC.TL_channelForbidden) {
                continue;
            }
            boolean isBroadcastChannel = ChatObject.isChannel(chat) && !chat.megagroup;
            if (!includeGroups && !isBroadcastChannel) {
                continue;
            }
            if (d.last_message_date <= 0) {
                continue;
            }

            long ageSec = Math.max(0L, nowSec - d.last_message_date);
            int days = (int) (ageSec / 86400L);
            int months = days / 30;
            if (months < 1) {
                continue; // Active within the last month
            }

            if (months >= 3) count3M++;
            if (months >= 6) count6M++;
            if (months >= 12) count12M++;

            String lastDateStr = "آخر نشر: " + dateFmt.format(new Date(d.last_message_date * 1000L));
            String uname = ChatObject.getPublicUsername(chat);
            if (!TextUtils.isEmpty(uname)) {
                lastDateStr = "@" + uname + " • " + lastDateStr;
            }
            if (chat.creator) {
                lastDateStr = "👑 مالك • " + lastDateStr;
            }

            String badge;
            int color;
            if (months >= 12) {
                int yrs = months / 12;
                badge = yrs == 1 ? "مهجورة +سنة 🕸️" : ("مهجورة " + yrs + " سنوات 🕸️");
                color = 0xFFE53935;
            } else if (months >= 6) {
                badge = "متوقفة " + months + " أشهر 💤";
                color = 0xFFEA580C;
            } else {
                badge = "متوقفة " + months + " أشهر ⏳";
                color = 0xFFF59E0B;
            }

            allChannels.add(new DeadChannelEntry(d.id, chat, d.last_message_date, months, lastDateStr, badge, color));
        }

        // Oldest / longest dead first so user sees most abandoned channels at the top
        Collections.sort(allChannels, (a, b) -> Integer.compare(a.lastDateSec, b.lastDateSec));
    }

    private void refreshList() {
        updateBottomBar();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void toggleSelectAll() {
        if (filteredEntries.isEmpty()) return;
        boolean allSel = true;
        for (int i = 0; i < filteredEntries.size(); i++) {
            if (!selectedIds.contains(filteredEntries.get(i).dialogId)) {
                allSel = false;
                break;
            }
        }
        if (allSel) {
            for (int i = 0; i < filteredEntries.size(); i++) {
                selectedIds.remove(filteredEntries.get(i).dialogId);
            }
        } else {
            for (int i = 0; i < filteredEntries.size(); i++) {
                if (!filteredEntries.get(i).chat.creator) {
                    selectedIds.add(filteredEntries.get(i).dialogId);
                }
            }
        }
        refreshList();
    }

    private void updateBottomBar() {
        if (bottomActionBar == null) return;
        int count = selectedIds.size();
        if (count == 0) {
            bottomActionBar.setVisibility(View.GONE);
            simpleActionBar.setTitle("القنوات المهجورة (" + filteredEntries.size() + ")");
        } else {
            bottomActionBar.setVisibility(View.VISIBLE);
            selectedCountTv.setText("تم تحديد: " + count);
            selectAllToggleBtn.setText(count >= Math.max(1, filteredEntries.size()) ? "إلغاء الكل" : "تحديد الكل");
            simpleActionBar.setTitle("محدد للمغادرة: " + count);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        Context context = getContext();
        if (context == null) return;

        filteredEntries.clear();
        String q = searchQuery != null ? searchQuery.toLowerCase(Locale.US) : "";
        for (int i = 0; i < allChannels.size(); i++) {
            DeadChannelEntry e = allChannels.get(i);
            if (e.monthsInactive < minMonthsFilter) {
                continue;
            }
            if (!TextUtils.isEmpty(q)) {
                String title = e.chat.title != null ? e.chat.title.toLowerCase(Locale.US) : "";
                boolean match = title.contains(q) || e.lastPostStr.toLowerCase(Locale.US).contains(q);
                if (!match) continue;
            }
            filteredEntries.add(e);
        }

        items.add(UItem.asCustom(10, new SummaryCard(context)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asCustomShadow(11, new FilterChips(context)));

        items.add(UItem.asButtonCheck(12, "تضمين المجموعات الخاملة أيضاً 👥", "فحص المجموعات التي توقفت عن التفاعل بجانب القنوات").setChecked(includeGroups));

        if (filteredEntries.isEmpty()) {
            items.add(UItem.asHeader("النتائج"));
            items.add(UItem.asButton(13, "قائمة نظيفة! 🎉", "لا توجد قنوات متوقفة منذ " + minMonthsFilter + "+ أشهر في اشتراكاتك."));
        } else {
            items.add(UItem.asHeader("القنوات المهجورة (" + filteredEntries.size() + ")"));
            for (int i = 0; i < filteredEntries.size(); i++) {
                DeadChannelEntry e = filteredEntries.get(i);
                UItem rowItem = UItem.asCustom(100 + i, new ChannelRow(context, e));
                rowItem.object = e;
                items.add(rowItem);
            }
            items.add(UItem.asShadow("• القنوات التي أنشأتها (👑 مالك) محمية من (تحديد الكل) التلقائي لمنع مغادرتها بالخطأ.\n• اضغط على أي قناة لمعاينة آخر منشوراتها قبل المغادرة."));
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 12) {
            includeGroups = !includeGroups;
            scanChannels();
            refreshList();
            return;
        }
        if (!(item.object instanceof DeadChannelEntry)) {
            return;
        }
        DeadChannelEntry e = (DeadChannelEntry) item.object;
        if (!selectedIds.isEmpty()) {
            if (selectedIds.contains(e.dialogId)) {
                selectedIds.remove(e.dialogId);
            } else {
                selectedIds.add(e.dialogId);
            }
            refreshList();
        } else {
            Bundle args = new Bundle();
            args.putLong("chat_id", e.chat.id);
            presentFragment(new ChatActivity(args));
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (!(item.object instanceof DeadChannelEntry)) return false;
        DeadChannelEntry e = (DeadChannelEntry) item.object;
        if (selectedIds.contains(e.dialogId)) {
            selectedIds.remove(e.dialogId);
        } else {
            selectedIds.add(e.dialogId);
        }
        refreshList();
        return true;
    }

    private void confirmLeaveSelected() {
        if (selectedIds.isEmpty() || getParentActivity() == null) return;
        Context context = getParentActivity();
        final boolean[] deleteHistory = new boolean[]{true};

        TextCheckCell delCell = new TextCheckCell(context);
        delCell.setTextAndCheck("مسح سجل المحادثة محلياً عند المغادرة", deleteHistory[0], false);
        delCell.setBackground(Theme.getSelectorDrawable(true));
        delCell.setOnClickListener(v -> {
            deleteHistory[0] = !deleteHistory[0];
            delCell.setChecked(deleteHistory[0]);
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("مغادرة " + selectedIds.size() + " قناة مهجورة");
        builder.setMessage("سيتم إلغاء اشتراكك ومغادرة جميع القنوات المحددة لتنظيف قائمة دردشاتك.");
        builder.setView(delCell);
        builder.setPositiveButton("مغادرة الآن", (d, w) -> {
            MessagesController mc = MessagesController.getInstance(currentAccount);
            TLRPC.User me = UserConfig.getInstance(currentAccount).getCurrentUser();
            ArrayList<Long> toLeave = new ArrayList<>(selectedIds);
            for (int i = 0; i < toLeave.size(); i++) {
                long did = toLeave.get(i);
                mc.deleteParticipantFromChat(-did, me, null, deleteHistory[0], false);
            }
            selectedIds.clear();
            scanChannels();
            refreshList();
            BulletinFactory.of(this).createSuccessBulletin("تمت مغادرة " + toLeave.size() + " قناة مهجورة 🚪").show();
        });
        builder.setNegativeButton("إلغاء", null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        View posBtn = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (posBtn instanceof TextView) {
            ((TextView) posBtn).setTextColor(0xFFE53935);
        }
    }

    private class SummaryCard extends LinearLayout {
        public SummaryCard(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(16), dp(14), dp(16), dp(14));

            LinearLayout head = new LinearLayout(context);
            head.setOrientation(HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

            TextView title = new TextView(context);
            title.setText("تحليل نشاط اشتراكاتك 🕸️");
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            head.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            TextView selAllBtn = new TextView(context);
            selAllBtn.setText("تحديد الكل ✓");
            selAllBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            selAllBtn.setTypeface(AndroidUtilities.bold());
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            selAllBtn.setTextColor(accent);
            selAllBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
            selAllBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                    dp(10), Theme.multAlpha(accent, 0.12f), Theme.multAlpha(accent, 0.24f)));
            selAllBtn.setOnClickListener(v -> toggleSelectAll());
            head.addView(selAllBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            row.addView(makeBox(context, "+3 أشهر", String.valueOf(count3M), 0xFFF59E0B),
                    LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 6, 0));
            row.addView(makeBox(context, "+6 أشهر", String.valueOf(count6M), 0xFFEA580C),
                    LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 3, 0, 3, 0));
            row.addView(makeBox(context, "+سنة", String.valueOf(count12M), 0xFFE53935),
                    LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 6, 0, 0, 0));
        }

        private View makeBox(Context context, String label, String val, int color) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            card.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.multAlpha(color, 0.10f)));

            TextView numTv = new TextView(context);
            numTv.setText(val);
            numTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            numTv.setTypeface(AndroidUtilities.bold());
            numTv.setTextColor(color);
            card.addView(numTv);

            TextView lblTv = new TextView(context);
            lblTv.setText(label);
            lblTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            lblTv.setTypeface(AndroidUtilities.bold());
            lblTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            card.addView(lblTv);

            return card;
        }
    }

    private class FilterChips extends HorizontalScrollView {
        public FilterChips(Context context) {
            super(context);
            setHorizontalScrollBarEnabled(false);
            setPadding(dp(12), dp(2), dp(12), dp(8));
            setClipToPadding(false);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            addView(row, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            addChip(context, row, FILTER_3_MONTHS, "متوقفة 3+ أشهر (" + count3M + ")");
            addChip(context, row, FILTER_6_MONTHS, "متوقفة 6+ أشهر (" + count6M + ")");
            addChip(context, row, FILTER_12_MONTHS, "مهجورة +سنة (" + count12M + ")");
            addChip(context, row, FILTER_1_MONTH, "خاملة +شهر (" + allChannels.size() + ")");
        }

        private void addChip(Context context, LinearLayout row, int months, String label) {
            boolean sel = minMonthsFilter == months;
            TextView tv = new TextView(context);
            tv.setText(label);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            tv.setTypeface(AndroidUtilities.bold());
            tv.setPadding(dp(14), dp(7), dp(14), dp(7));
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (sel) {
                tv.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
                tv.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                        dp(18), accent, Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
            } else {
                tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                tv.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                        dp(18), Theme.getColor(Theme.key_windowBackgroundWhite), Theme.multAlpha(accent, 0.14f)));
            }
            tv.setOnClickListener(v -> {
                if (minMonthsFilter != months) {
                    minMonthsFilter = months;
                    refreshList();
                }
            });
            row.addView(tv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        }
    }

    private class ChannelRow extends LinearLayout {
        public ChannelRow(Context context, DeadChannelEntry e) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(14), dp(10), dp(14), dp(10));

            CircleCheckView checkView = new CircleCheckView(context, selectedIds.contains(e.dialogId));
            checkView.setOnClickListener(v -> {
                if (selectedIds.contains(e.dialogId)) {
                    selectedIds.remove(e.dialogId);
                } else {
                    selectedIds.add(e.dialogId);
                }
                refreshList();
            });
            addView(checkView, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(currentAccount, e.chat);
            BackupImageView avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(21));
            avatarView.setForUserOrChat(e.chat, avatarDrawable);
            addView(avatarView, LayoutHelper.createLinear(42, 42, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(VERTICAL);
            addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

            TextView nameTv = new TextView(context);
            nameTv.setText(e.chat.title);
            nameTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
            nameTv.setTypeface(AndroidUtilities.bold());
            nameTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTv.setSingleLine();
            nameTv.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(nameTv);

            TextView subTv = new TextView(context);
            subTv.setText(e.lastPostStr);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            subTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subTv.setSingleLine();
            subTv.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(subTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            TextView badge = new TextView(context);
            badge.setText(e.badgeText);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            badge.setTypeface(AndroidUtilities.bold());
            badge.setTextColor(e.badgeColor);
            badge.setPadding(dp(8), dp(4), dp(8), dp(4));
            badge.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.multAlpha(e.badgeColor, 0.13f)));
            addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    private static class CircleCheckView extends View {
        private final boolean checked;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public CircleCheckView(Context context, boolean checked) {
            super(context);
            this.checked = checked;
            tickPaint.setStyle(Paint.Style.STROKE);
            tickPaint.setStrokeWidth(dp(2));
            tickPaint.setStrokeCap(Paint.Cap.ROUND);
            tickPaint.setStrokeJoin(Paint.Join.ROUND);
            tickPaint.setColor(Color.WHITE);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = dp(10.5f);
            if (checked) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                canvas.drawCircle(cx, cy, r, paint);
                canvas.drawLine(cx - dp(4), cy + dp(0.5f), cx - dp(1), cy + dp(3.5f), tickPaint);
                canvas.drawLine(cx - dp(1), cy + dp(3.5f), cx + dp(4.5f), cy - dp(3f), tickPaint);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.6f));
                paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), 0.55f));
                canvas.drawCircle(cx, cy, r, paint);
            }
        }
    }
}
