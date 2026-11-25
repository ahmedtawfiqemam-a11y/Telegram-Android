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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;

/**
 * Collects deleted accounts, frozen/restricted/blocked accounts, and bots across dialogs.
 * Allows opening any chat to inspect history, multi-selecting or selecting all, and batch deleting/blocking.
 */
public class MidoDeletedAccountsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int CAT_ALL = 0;
    private static final int CAT_DELETED = 1;
    private static final int CAT_FROZEN = 2;
    private static final int CAT_BOTS = 3;

    private static class Entry {
        final long dialogId;
        final TLRPC.User user;
        final int date;
        final int category; // CAT_DELETED, CAT_FROZEN, CAT_BOTS
        final String displayName;
        final String subtitle;
        final String statusLabel;
        final int statusColor;

        Entry(long dialogId, TLRPC.User user, int date, int category, String displayName, String subtitle, String statusLabel, int statusColor) {
            this.dialogId = dialogId;
            this.user = user;
            this.date = date;
            this.category = category;
            this.displayName = displayName;
            this.subtitle = subtitle;
            this.statusLabel = statusLabel;
            this.statusColor = statusColor;
        }
    }

    private FrameLayout rootLayout;
    private UniversalRecyclerView listView;
    private LinearLayout bottomActionBar;
    private TextView selectedCountTv;
    private TextView selectAllToggleBtn;
    private TextView deleteSelectedBtn;

    private int selectedCategory = CAT_ALL;
    private String searchQuery = "";
    private final ArrayList<Entry> allEntries = new ArrayList<>();
    private final ArrayList<Entry> filteredEntries = new ArrayList<>();
    private final HashSet<Long> selectedIds = new HashSet<>();

    private int countDeleted;
    private int countFrozen;
    private int countBots;

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
        simpleActionBar.setTitle(LocaleController.isRTL ? "الحسابات المحذوفة" : "Delete Accounts");
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
                .setSearchFieldHint(LocaleController.isRTL ? "بحث في المحذوفين والبوتات..." : "Search deleted or bots...");

        menu.addItem(1, R.drawable.msg_markread, resourceProvider);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.setCaptureSectionsDecoratorAllowed(true);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        listView.setPadding(0, ActionBar.getCurrentActionBarHeight() + dp(10), 0, dp(76));
        listView.setClipToPadding(false);
        rootLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Bottom Native Floating Multi-Action Bar
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

        deleteSelectedBtn = new TextView(context);
        deleteSelectedBtn.setText("مسح المحدد 🗑");
        deleteSelectedBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        deleteSelectedBtn.setTypeface(AndroidUtilities.bold());
        deleteSelectedBtn.setTextColor(Color.WHITE);
        deleteSelectedBtn.setPadding(dp(14), dp(7), dp(14), dp(7));
        deleteSelectedBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(10), 0xFFE53935, 0xFFC62828));
        deleteSelectedBtn.setOnClickListener(v -> confirmDeleteSelected());
        bottomActionBar.addView(deleteSelectedBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        rootLayout.addView(bottomActionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 12, 0, 12, 14));
        rootLayout.addView(simpleActionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        scanDialogs();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        scanDialogs();
        refreshList();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogsNeedReload) {
            scanDialogs();
            refreshList();
        }
    }

    private void scanDialogs() {
        allEntries.clear();
        countDeleted = 0;
        countFrozen = 0;
        countBots = 0;

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

        for (int i = 0; i < rawDialogs.size(); i++) {
            TLRPC.Dialog d = rawDialogs.get(i);
            if (d.id <= 0) continue; // Only private users and bots

            TLRPC.User user = mc.getUser(d.id);
            if (user != null && (user.self || user.id == UserObject.VERIFY)) {
                continue;
            }

            boolean isDeleted = UserObject.isDeleted(user);
            boolean isBot = user != null && user.bot;
            boolean isBlocked = mc.blockePeers != null && mc.blockePeers.indexOfKey(d.id) >= 0;
            boolean isRestricted = user != null && (
                    user.scam
                    || user.fake
                    || (user.restriction_reason != null && !user.restriction_reason.isEmpty())
            );
            boolean isFrozen = isBlocked || isRestricted || (!isDeleted && !isBot && user != null
                    && (user.status instanceof TLRPC.TL_userStatusEmpty) && !user.contact && !user.support);

            if (isDeleted) {
                countDeleted++;
                String sub = "حساب محذوف نهائياً من تيليجرام • ID: " + d.id;
                allEntries.add(new Entry(d.id, user, d.last_message_date, CAT_DELETED,
                        LocaleController.getString(R.string.HiddenName), sub, "محذوف 👻", 0xFFE53935));
            } else if (isFrozen) {
                countFrozen++;
                String name = UserObject.getUserName(user);
                String uname = UserObject.getPublicUsername(user, false);
                String tag = isBlocked ? "محظور 🚫" : "مجمّد / مقيد ❄️";
                String sub = (!TextUtils.isEmpty(uname) ? "@" + uname + " • " : "") + "حساب متجمد أو مقيد";
                allEntries.add(new Entry(d.id, user, d.last_message_date, CAT_FROZEN,
                        name, sub, tag, 0xFFF59E0B));
            } else if (isBot) {
                countBots++;
                String name = UserObject.getUserName(user);
                String uname = UserObject.getPublicUsername(user, false);
                String sub = !TextUtils.isEmpty(uname) ? ("@" + uname + " • بوت تيليجرام") : "بوت محادثة";
                allEntries.add(new Entry(d.id, user, d.last_message_date, CAT_BOTS,
                        name, sub, "بوت 🤖", Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)));
            }
        }

        Collections.sort(allEntries, (a, b) -> Integer.compare(b.date, a.date));
    }

    private void refreshList() {
        updateBottomBar();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void toggleSelectAll() {
        if (filteredEntries.isEmpty()) {
            return;
        }
        boolean allSelected = true;
        for (int i = 0; i < filteredEntries.size(); i++) {
            if (!selectedIds.contains(filteredEntries.get(i).dialogId)) {
                allSelected = false;
                break;
            }
        }
        if (allSelected) {
            for (int i = 0; i < filteredEntries.size(); i++) {
                selectedIds.remove(filteredEntries.get(i).dialogId);
            }
        } else {
            for (int i = 0; i < filteredEntries.size(); i++) {
                selectedIds.add(filteredEntries.get(i).dialogId);
            }
        }
        refreshList();
    }

    private void updateBottomBar() {
        if (bottomActionBar == null) return;
        int count = selectedIds.size();
        if (count == 0) {
            bottomActionBar.setVisibility(View.GONE);
            simpleActionBar.setTitle("الحسابات المحذوفة (" + allEntries.size() + ")");
        } else {
            bottomActionBar.setVisibility(View.VISIBLE);
            selectedCountTv.setText("تم تحديد: " + count);
            boolean allSelected = !filteredEntries.isEmpty() && count >= filteredEntries.size();
            selectAllToggleBtn.setText(allSelected ? "إلغاء الكل" : "تحديد الكل");
            simpleActionBar.setTitle("محدد: " + count);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        Context context = getContext();
        if (context == null) return;

        filteredEntries.clear();
        String q = searchQuery != null ? searchQuery.toLowerCase(Locale.US) : "";
        for (int i = 0; i < allEntries.size(); i++) {
            Entry e = allEntries.get(i);
            if (selectedCategory != CAT_ALL && e.category != selectedCategory) {
                continue;
            }
            if (!TextUtils.isEmpty(q)) {
                boolean match = e.displayName.toLowerCase(Locale.US).contains(q)
                        || e.subtitle.toLowerCase(Locale.US).contains(q)
                        || String.valueOf(e.dialogId).contains(q);
                if (!match) continue;
            }
            filteredEntries.add(e);
        }

        // Top Summary Card
        items.add(UItem.asCustom(10, new SummaryCard(context)));
        items.add(UItem.asShadow(null));

        // Category Filter Chips
        items.add(UItem.asCustomShadow(11, new FilterChips(context)));

        if (filteredEntries.isEmpty()) {
            items.add(UItem.asHeader("القائمة فارغة"));
            items.add(UItem.asButton(12, "لا توجد حسابات مطابقة 🎉", "لم يتم العثور على حسابات محذوفة أو مجمّدة أو بوتات في هذا التصنيف."));
        } else {
            items.add(UItem.asHeader("اضغط للدخول للشات أو حدد للمسح (" + filteredEntries.size() + ")"));
            for (int i = 0; i < filteredEntries.size(); i++) {
                Entry e = filteredEntries.get(i);
                UItem rowItem = UItem.asCustom(100 + i, new EntryRow(context, e));
                rowItem.object = e;
                items.add(rowItem);
            }
            items.add(UItem.asShadow("• اضغط على أي صف للدخول إلى المحادثة وقراءة الرسائل أو التحقق منها.\n• اضغط مطولاً أو على دائرة التحديد لاختيار حسابات متعددة أو الضغط على (✓) بالأعلى لتحديد الكل وحذفها دفعة واحدة."));
        }
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (!(item.object instanceof Entry)) {
            return;
        }
        Entry e = (Entry) item.object;
        if (!selectedIds.isEmpty()) {
            if (selectedIds.contains(e.dialogId)) {
                selectedIds.remove(e.dialogId);
            } else {
                selectedIds.add(e.dialogId);
            }
            refreshList();
        } else {
            openChat(e);
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (!(item.object instanceof Entry)) {
            return false;
        }
        Entry e = (Entry) item.object;
        if (selectedIds.contains(e.dialogId)) {
            selectedIds.remove(e.dialogId);
        } else {
            selectedIds.add(e.dialogId);
        }
        refreshList();
        return true;
    }

    private void openChat(Entry e) {
        Bundle args = new Bundle();
        args.putLong("user_id", e.dialogId);
        presentFragment(new ChatActivity(args));
    }

    private void confirmDeleteSelected() {
        if (selectedIds.isEmpty() || getParentActivity() == null) {
            return;
        }
        Context context = getParentActivity();
        final boolean[] revokeBoth = new boolean[]{true};
        final boolean[] blockBots = new boolean[]{false};

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(4), 0, dp(4));

        TextCheckCell revokeCell = new TextCheckCell(context);
        revokeCell.setTextAndCheck("حذف المحادثة للطرفين أيضاً", revokeBoth[0], true);
        revokeCell.setBackground(Theme.getSelectorDrawable(true));
        revokeCell.setOnClickListener(v -> {
            revokeBoth[0] = !revokeBoth[0];
            revokeCell.setChecked(revokeBoth[0]);
        });
        box.addView(revokeCell);

        TextCheckCell blockCell = new TextCheckCell(context);
        blockCell.setTextAndCheck("حظر وإيقاف البوتات المحددة", blockBots[0], false);
        blockCell.setBackground(Theme.getSelectorDrawable(true));
        blockCell.setOnClickListener(v -> {
            blockBots[0] = !blockBots[0];
            blockCell.setChecked(blockBots[0]);
        });
        box.addView(blockCell);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("حذف " + selectedIds.size() + " محادثة محددة");
        builder.setMessage("سيتم حذف المحادثات المحددة نهائياً من قائمة دردشاتك.");
        builder.setView(box);
        builder.setPositiveButton("حذف الآن", (d, w) -> {
            MessagesController mc = MessagesController.getInstance(currentAccount);
            ArrayList<Long> toDelete = new ArrayList<>(selectedIds);
            for (int i = 0; i < toDelete.size(); i++) {
                long did = toDelete.get(i);
                TLRPC.User user = mc.getUser(did);
                if (blockBots[0] && user != null && user.bot) {
                    mc.blockPeer(did);
                }
                mc.deleteDialog(did, 0, revokeBoth[0]);
            }
            selectedIds.clear();
            scanDialogs();
            refreshList();
            BulletinFactory.of(this).createSuccessBulletin("تم حذف " + toDelete.size() + " محادثة بنجاح 🗑").show();
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
            title.setText("ملخص التنظيف والتحكم");
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

            row.addView(makeStatBox(context, "محذوفة 👻", String.valueOf(countDeleted), 0xFFE53935),
                    LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 6, 0));
            row.addView(makeStatBox(context, "مجمّدة ❄️", String.valueOf(countFrozen), 0xFFF59E0B),
                    LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 3, 0, 3, 0));
            row.addView(makeStatBox(context, "بوتات 🤖", String.valueOf(countBots), accent),
                    LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 6, 0, 0, 0));
        }

        private View makeStatBox(Context context, String label, String val, int color) {
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

            addChip(context, row, CAT_ALL, "الكل (" + allEntries.size() + ")");
            addChip(context, row, CAT_DELETED, "حسابات محذوفة (" + countDeleted + ")");
            addChip(context, row, CAT_FROZEN, "مجمّدة ومقيدة (" + countFrozen + ")");
            addChip(context, row, CAT_BOTS, "البوتات (" + countBots + ")");
        }

        private void addChip(Context context, LinearLayout row, int cat, String label) {
            boolean sel = selectedCategory == cat;
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
                if (selectedCategory != cat) {
                    selectedCategory = cat;
                    refreshList();
                }
            });
            row.addView(tv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        }
    }

    private class EntryRow extends LinearLayout {
        public EntryRow(Context context, Entry e) {
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
            if (e.user != null) {
                avatarDrawable.setInfo(currentAccount, e.user);
            } else {
                avatarDrawable.setInfo(e.dialogId, "D", null);
            }
            BackupImageView avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(21));
            if (e.user != null && !UserObject.isDeleted(e.user)) {
                avatarView.setForUserOrChat(e.user, avatarDrawable);
            } else {
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_ANONYMOUS);
                avatarView.setImageDrawable(avatarDrawable);
            }
            addView(avatarView, LayoutHelper.createLinear(42, 42, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(VERTICAL);
            addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

            TextView nameTv = new TextView(context);
            nameTv.setText(e.displayName);
            nameTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
            nameTv.setTypeface(AndroidUtilities.bold());
            nameTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTv.setSingleLine();
            nameTv.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(nameTv);

            TextView subTv = new TextView(context);
            subTv.setText(e.subtitle);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            subTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subTv.setSingleLine();
            subTv.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(subTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            TextView badge = new TextView(context);
            badge.setText(e.statusLabel);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            badge.setTypeface(AndroidUtilities.bold());
            badge.setTextColor(e.statusColor);
            badge.setPadding(dp(8), dp(4), dp(8), dp(4));
            badge.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.multAlpha(e.statusColor, 0.13f)));
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
