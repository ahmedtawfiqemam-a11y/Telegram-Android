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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MidoChannelBookmarks;
import org.telegram.messenger.MidoChannelBookmarks.ChannelBookmark;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class MidoFollowedChannelsActivity extends BaseFragment {

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("قنوات بدون اشتراك 📌");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.setCaptureSectionsDecoratorAllowed(true);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        Context context = getContext();
        if (context == null) return;

        ArrayList<ChannelBookmark> list = MidoChannelBookmarks.getAllBookmarks();
        items.add(UItem.asHeader("القنوات المتابعة بصمت (دون انضمام)"));

        if (list.isEmpty()) {
            items.add(UItem.asButton(1, "لا توجد قنوات مضافة بعد", "افتح أي قناة بدون اشتراك واضغط (⋮ ➔ متابعة القناة دون اشتراك 📌)"));
            items.add(UItem.asShadow("يحفظ التطبيق موضع آخر منشور قرأته تلقائياً ويعيدك إليه في كل مرة دون أن يظهر اسمك ضمن المشتركين."));
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            ChannelBookmark b = list.get(i);
            BookmarkRow row = new BookmarkRow(context, b);
            UItem item = UItem.asCustom(100 + i, row);
            item.object = b;
            items.add(item);
        }

        items.add(UItem.asShadow("• اضغط على أي قناة لاستكمال القراءة من آخر منشور توقفت عنده.\n• اضغط مطولاً لإزالتها من القائمة أو للانضمام الرسمي."));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof ChannelBookmark) {
            openChannel((ChannelBookmark) item.object);
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (!(item.object instanceof ChannelBookmark) || getParentActivity() == null) {
            return false;
        }
        ChannelBookmark b = (ChannelBookmark) item.object;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(b.title);
        builder.setItems(new CharSequence[]{
                "متابعة القراءة (من منشور #" + b.lastReadMsgId + ")",
                "إزالة من القائمة 📌"
        }, (d, which) -> {
            if (which == 0) {
                openChannel(b);
            } else {
                MidoChannelBookmarks.unfollow(b.channelId);
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
                BulletinFactory.of(this).createSuccessBulletin("تمت إزالة القناة من المتابعة الصامتة").show();
            }
        });
        showDialog(builder.create());
        return true;
    }

    private void openChannel(ChannelBookmark b) {
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(b.channelId);
        if (chat == null && b.accessHash != 0) {
            TLRPC.TL_channel stub = new TLRPC.TL_channel();
            stub.id = b.channelId;
            stub.access_hash = b.accessHash;
            stub.title = b.title;
            stub.username = b.username;
            stub.broadcast = true;
            stub.left = true;
            MessagesController.getInstance(currentAccount).putChat(stub, true);
            chat = stub;
        }
        if (chat != null) {
            Bundle args = new Bundle();
            args.putLong("chat_id", b.channelId);
            if (b.lastReadMsgId > 0) {
                args.putInt("message_id", b.lastReadMsgId);
            }
            presentFragment(new ChatActivity(args));
        } else if (!TextUtils.isEmpty(b.username)) {
            MessagesController.getInstance(currentAccount).openByUserName(b.username, this, 1);
        }
    }

    private class BookmarkRow extends LinearLayout {
        public BookmarkRow(Context context, ChannelBookmark b) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(14), dp(10), dp(14), dp(10));

            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(b.channelId);
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            if (chat != null) {
                avatarDrawable.setInfo(currentAccount, chat);
            } else {
                avatarDrawable.setInfo(b.channelId, b.title, null);
            }

            BackupImageView avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(21));
            if (chat != null) {
                avatarView.setForUserOrChat(chat, avatarDrawable);
            } else {
                avatarView.setImageDrawable(avatarDrawable);
            }
            addView(avatarView, LayoutHelper.createLinear(42, 42, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(VERTICAL);
            addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

            TextView titleTv = new TextView(context);
            titleTv.setText(b.title);
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.5f);
            titleTv.setTypeface(AndroidUtilities.bold());
            titleTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleTv.setSingleLine();
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(titleTv);

            String sub = "آخر منشور قرأته: #" + Math.max(1, b.lastReadMsgId);
            if (!TextUtils.isEmpty(b.username)) {
                sub = "@" + b.username + "  •  " + sub;
            }
            TextView subTv = new TextView(context);
            subTv.setText(sub);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            subTv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subTv.setSingleLine();
            subTv.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(subTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            int diff = Math.max(0, b.latestKnownMsgId - b.lastReadMsgId);
            TextView pill = new TextView(context);
            if (diff > 0) {
                pill.setText("+" + diff + " جديد");
                int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
                pill.setTextColor(accent);
                pill.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.multAlpha(accent, 0.14f)));
            } else {
                pill.setText("محدّث ✓");
                pill.setTextColor(0xFF1FA75A);
                pill.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.multAlpha(0xFF1FA75A, 0.14f)));
            }
            pill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            pill.setTypeface(AndroidUtilities.bold());
            pill.setPadding(dp(8), dp(4), dp(8), dp(4));
            addView(pill, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }
}
