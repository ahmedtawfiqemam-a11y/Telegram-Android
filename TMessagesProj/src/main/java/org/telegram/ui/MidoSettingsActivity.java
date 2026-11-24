package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MidoConfig;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class MidoSettingsActivity extends BaseFragment {

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("إعدادات ميدو / Mido Settings");
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

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("الخصوصية والرسائل (Privacy)"));
        items.add(UItem.asCheck(1, "منع حذف الرسائل (Anti-Delete)")
                .setChecked(MidoConfig.antiDelete)
                .setValue("الاحتفاظ بالرسائل حتى لو حذفها الطرف الآخر لجميع الأطراف"));
        items.add(UItem.asShadow("عند تفعيل هذا الخيار، لن يتم حذف الرسائل من محادثاتك حتى إذا اختار الطرف الآخر حذف لدى الجميع."));

        items.add(UItem.asCheck(3, "مشاهدة الاستوري بدون ظهور 👁️ (وضع الشبح)")
                .setChecked(MidoConfig.stealthStories)
                .setValue("مشاهدة قصص وحالات الآخرين دون معرفتهم ودون تسجيل اسمك في قائمة المشاهدات"));
        items.add(UItem.asShadow("يمكنك مشاهدة أي ستوري بحرية كاملة دون أن يظهر لصاحب الستوري أنك قمت بفتحها."));

        items.add(UItem.asHeader("تعدد الحسابات (Multi-Accounts)"));
        items.add(UItem.asCheck(2, "تعدد الحسابات الخارق 🚀 (20 حساب)")
                .setChecked(MidoConfig.unlimitedAccounts)
                .setValue("إمكانية تسجيل الدخول بأرقام متعددة تصل إلى 20 حساباً بدون قيود وبدون الحاجة لـ Telegram Premium"));
        items.add(UItem.asShadow("يتيح لك التبديل بين حتى 20 رقماً هاتفياً مسجلاً في نفس التطبيق بسلاسة."));

        items.add(UItem.asHeader("مجتمع وقنوات المطور ميدو"));
        items.add(UItem.asButton(10, "📢 قناة التحديثات والسورس (@SourceMedo)", "اضغط للانضمام لقناة وسورس المطور الرسمية"));
        items.add(UItem.asButton(11, "💬 مجموعة المناقشة والدعم الفني", "اضغط للانضمام لجروب النقاش وحل المشكلات"));
        items.add(UItem.asShadow("انضم لقنوات المطور للحصول على التحديثات والميزات الجديدة أولاً بأول."));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
if (item.id == 1) {
            MidoConfig.setAntiDelete(!MidoConfig.antiDelete);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.antiDelete);
            }
        } else if (item.id == 3) {
            MidoConfig.setStealthStories(!MidoConfig.stealthStories);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.stealthStories);
            }
        } else if (item.id == 2) {
            MidoConfig.setUnlimitedAccounts(!MidoConfig.unlimitedAccounts);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.unlimitedAccounts);
            }
        } else if (item.id == 10) {
            MessagesController.getInstance(currentAccount).openByUserName("SourceMedo", this, 1);
        } else if (item.id == 11) {
            Browser.openUrl(getParentActivity(), "https://t.me/+m3DnjfmnWpFmYWVi");
        }
    }
}
