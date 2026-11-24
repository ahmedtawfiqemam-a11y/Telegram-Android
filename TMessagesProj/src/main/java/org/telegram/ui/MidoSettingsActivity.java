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
        items.add(UItem.asHeader("فك القيود وحماية الوسائط (Restricted Content)"));
        items.add(UItem.asCheck(20, "تخطي حظر الحفظ والتحميل 🔓 (Save Restricted)")
                .setChecked(MidoConfig.saveRestrictedContent)
                .setValue("حفظ وتنزيل ونسخ الوسائط من القنوات والمجموعات المقيدة بحرية"));

        items.add(UItem.asCheck(21, "السماح بلقطات الشاشة 📸 (Allow Screenshots)")
                .setChecked(MidoConfig.allowScreenshots)
                .setValue("أخذ سكرين شوت في المحادثات السرية والقنوات المحمية بدون قيود"));

        items.add(UItem.asCheck(22, "حفظ وسائط العرض لمرة واحدة ⏳ (Save View-Once)")
                .setChecked(MidoConfig.saveExpiringMedia)
                .setValue("حفظ الصور والفيديوهات والبصمات ذاتية التدمير في المعرض دون اختفائها"));
        items.add(UItem.asShadow("تتيح لك هذه الميزات التحكم الكامل وتخطي أي قيود يفرضها منشئو القنوات أو أصحاب المحادثات."));

        items.add(UItem.asHeader("الرسائل والمحادثات المتقدمة (Advanced Chat)"));
        items.add(UItem.asCheck(1, "منع حذف الرسائل 🛡️ (Anti-Delete)")
                .setChecked(MidoConfig.antiDelete)
                .setValue("الاحتفاظ بالرسائل حتى لو حذفها الطرف الآخر لجميع الأطراف"));

        items.add(UItem.asCheck(23, "معرفة الرسائل قبل التعديل ✏️ (Edit History)")
                .setChecked(MidoConfig.showEditHistory)
                .setValue("إظهار النص الأصلي القديم للرسالة قبل قيام الطرف الآخر بتعديلها"));

        items.add(UItem.asCheck(24, "إعادة توجيه بدون اقتباس 🔄 (Forward without Quote)")
                .setChecked(MidoConfig.forwardWithoutQuote)
                .setValue("تحويل الرسائل والوسائط بدون اسم المرسل الأصلي وبدون علامة التوجيه"));

        items.add(UItem.asCheck(25, "تأكيد قبل إجراء المكالمة 📞 (Confirm Call)")
                .setChecked(MidoConfig.confirmCall)
                .setValue("طلب تأكيد بنعم أو لا قبل الاتصال لمنع المكالمات بالخطأ"));
        items.add(UItem.asShadow("أدوات دردشة حصرية تمنحك تفوقاً وراحة كاملة أثناء المراسلة."));

        items.add(UItem.asHeader("وضع الشبح والخصوصية القصوى (Ghost Mode)"));
        items.add(UItem.asCheck(4, "إخفاء علامات قراءة الرسائل (Ghost Read)")
                .setChecked(MidoConfig.hideReadReceipts)
                .setValue("قراءة الرسائل والاستماع للبصمات دون إرسال الصح الأزرق للمرسل"));

        items.add(UItem.asCheck(5, "تجميد آخر ظهور (Freeze Last Seen)")
                .setChecked(MidoConfig.freezeLastSeen)
                .setValue("تثبيت وقت آخر ظهور عند توقيت محدد ومنع تحديثه للآخرين"));

        items.add(UItem.asCheck(6, "إخفاء حالة الاتصال (Hide Online Status)")
                .setChecked(MidoConfig.hideOnlineStatus)
                .setValue("تصفح المحادثات دون الظهور (متصل الآن / Online) للآخرين"));

        items.add(UItem.asCheck(7, "إخفاء جاري الكتابة والتسجيل (Hide Typing & Recording)")
                .setChecked(MidoConfig.hideTyping)
                .setValue("إخفاء مؤشرات الكتابة، تسجيل الصوت، أو إرسال الوسائط أثناء الدردشة"));

        items.add(UItem.asCheck(3, "مشاهدة الاستوري بدون ظهور 👁️ (Stealth Stories)")
                .setChecked(MidoConfig.stealthStories)
                .setValue("مشاهدة قصص وحالات الآخرين دون معرفتهم ودون تسجيل اسمك في المشاهدات"));

        items.add(UItem.asCheck(8, "المستمع المتخفي بالمكالمات الصوتية 🎧 (Ghost Voice Call)")
                .setChecked(MidoConfig.ghostCall)
                .setValue("الدخول للمكالمات الجماعية بوضع المستمع الصامت المتخفي ومنع بث الصوت"));

        items.add(UItem.asHeader("تعدد الحسابات (Multi-Accounts)"));
        items.add(UItem.asCheck(2, "تعدد الحسابات الخارق 🚀 (20 حساب)")
                .setChecked(MidoConfig.unlimitedAccounts)
                .setValue("تسجيل الدخول حتى 20 حساباً بدون قيود وبدون الحاجة لـ Telegram Premium"));
        items.add(UItem.asShadow("يتيح لك التبديل بين حتى 20 رقماً هاتفياً مسجلاً في نفس التطبيق بسلاسة."));

        items.add(UItem.asHeader("مجتمع وقنوات المطور ميدو"));
        items.add(UItem.asButton(10, "📢 قناة التحديثات والسورس (@SourceMedo)", "اضغط للانضمام لقناة وسورس المطور الرسمية"));
        items.add(UItem.asButton(11, "💬 مجموعة المناقشة والدعم الفني", "اضغط للانضمام لجروب النقاش وحل المشكلات"));
        items.add(UItem.asShadow("انضم لقنوات المطور للحصول على التحديثات والميزات الجديدة أولاً بأول."));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 20) {
            MidoConfig.setSaveRestrictedContent(!MidoConfig.saveRestrictedContent);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.saveRestrictedContent);
            }
        } else if (item.id == 21) {
            MidoConfig.setAllowScreenshots(!MidoConfig.allowScreenshots);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.allowScreenshots);
            }
        } else if (item.id == 22) {
            MidoConfig.setSaveExpiringMedia(!MidoConfig.saveExpiringMedia);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.saveExpiringMedia);
            }
        } else if (item.id == 1) {
            MidoConfig.setAntiDelete(!MidoConfig.antiDelete);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.antiDelete);
            }
        } else if (item.id == 23) {
            MidoConfig.setShowEditHistory(!MidoConfig.showEditHistory);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.showEditHistory);
            }
        } else if (item.id == 24) {
            MidoConfig.setForwardWithoutQuote(!MidoConfig.forwardWithoutQuote);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.forwardWithoutQuote);
            }
        } else if (item.id == 25) {
            MidoConfig.setConfirmCall(!MidoConfig.confirmCall);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.confirmCall);
            }
        } else if (item.id == 4) {
            MidoConfig.setHideReadReceipts(!MidoConfig.hideReadReceipts);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.hideReadReceipts);
            }
        } else if (item.id == 5) {
            MidoConfig.setFreezeLastSeen(!MidoConfig.freezeLastSeen);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.freezeLastSeen);
            }
        } else if (item.id == 6) {
            MidoConfig.setHideOnlineStatus(!MidoConfig.hideOnlineStatus);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.hideOnlineStatus);
            }
        } else if (item.id == 7) {
            MidoConfig.setHideTyping(!MidoConfig.hideTyping);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.hideTyping);
            }
        } else if (item.id == 3) {
            MidoConfig.setStealthStories(!MidoConfig.stealthStories);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.stealthStories);
            }
        } else if (item.id == 8) {
            MidoConfig.setGhostCall(!MidoConfig.ghostCall);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.ghostCall);
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
