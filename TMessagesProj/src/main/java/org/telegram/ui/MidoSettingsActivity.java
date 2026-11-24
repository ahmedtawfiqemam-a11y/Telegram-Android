package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MidoConfig;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.NotificationsCheckCell;
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
        items.add(UItem.asHeader("الاشتراك الإجباري في الخاص (Force Subscription)"));
        items.add(UItem.asButtonCheck(30, "تفعيل الاشتراك الإجباري 📢 (Force Subscribe)", "إلزام أي شخص يراسلك خاص بالاشتراك في قناتك أو مجموعتك أولاً").setChecked(MidoConfig.forceSubEnabled));

        items.add(UItem.asButton(31, "🔗 قناة/جروب الاشتراك: " + MidoConfig.forceSubChannel, "اضغط لتعديل رابط القناة أو المجموعة التي تريد إجبارهم عليها"));
        items.add(UItem.asButton(32, "📝 تعديل رسالة الاشتراك الإجباري", "يدعم الإيموجي المميز (Premium) والغامق والمونو والاقتباس والمتغيرات"));
        items.add(UItem.asButtonCheck(33, "استثناء جهات الاتصال المحفوظة 👥 (Exceptions)", "عدم طلب الاشتراك من أصدقائك وجهات الاتصال المسجلة في هاتفك").setChecked(MidoConfig.forceSubExcludeContacts));
        items.add(UItem.asShadow("عند تفعيل الميزة، سيرسل التطبيق رسالة الاشتراك تلقائياً لأي شخص يراسلك خاص، وفور انضمامه تُمسح الرسالة ويتمكن من مراسلتك بحرية."));

        items.add(UItem.asHeader("فك القيود وحماية الوسائط (Restricted Content)"));
        items.add(UItem.asButtonCheck(20, "تخطي حظر الحفظ والتحميل 🔓 (Save Restricted)", "حفظ وتنزيل ونسخ الوسائط من القنوات والمجموعات المقيدة بحرية").setChecked(MidoConfig.saveRestrictedContent));

        items.add(UItem.asButtonCheck(21, "السماح بلقطات الشاشة 📸 (Allow Screenshots)", "أخذ سكرين شوت في المحادثات السرية والقنوات المحمية بدون قيود").setChecked(MidoConfig.allowScreenshots));

        items.add(UItem.asButtonCheck(22, "حفظ وسائط العرض لمرة واحدة ⏳ (Save View-Once)", "حفظ الصور والفيديوهات والبصمات ذاتية التدمير في المعرض دون اختفائها").setChecked(MidoConfig.saveExpiringMedia));
        items.add(UItem.asShadow("تتيح لك هذه الميزات التحكم الكامل وتخطي أي قيود يفرضها منشئو القنوات أو أصحاب المحادثات."));

        items.add(UItem.asHeader("الرسائل والمحادثات المتقدمة (Advanced Chat)"));
        items.add(UItem.asButtonCheck(1, "منع حذف الرسائل 🛡️ (Anti-Delete)", "الاحتفاظ بالرسائل حتى لو حذفها الطرف الآخر لجميع الأطراف").setChecked(MidoConfig.antiDelete));

        items.add(UItem.asButtonCheck(26, "الاحتفاظ بالقنوات بعد الطرد 🚪 (Keep Kicked Chats)", "بقاء القنوات والمجموعات والرسائل السابقة حتى بعد الطرد أو الحظر منها").setChecked(MidoConfig.keepKickedChats));

        items.add(UItem.asButtonCheck(23, "معرفة الرسائل قبل التعديل ✏️ (Edit History)", "إظهار النص الأصلي القديم للرسالة قبل قيام الطرف الآخر بتعديلها").setChecked(MidoConfig.showEditHistory));

        items.add(UItem.asButtonCheck(24, "إعادة توجيه بدون اقتباس 🔄 (Forward without Quote)", "تحويل الرسائل والوسائط بدون اسم المرسل الأصلي وبدون علامة التوجيه").setChecked(MidoConfig.forwardWithoutQuote));

        items.add(UItem.asButtonCheck(25, "تأكيد قبل إجراء المكالمة 📞 (Confirm Call)", "طلب تأكيد بنعم أو لا قبل الاتصال لمنع المكالمات بالخطأ").setChecked(MidoConfig.confirmCall));
        items.add(UItem.asShadow("أدوات دردشة حصرية تمنحك تفوقاً وراحة كاملة أثناء المراسلة."));

        items.add(UItem.asHeader("وضع الشبح والخصوصية القصوى (Ghost Mode)"));
        items.add(UItem.asButtonCheck(4, "إخفاء علامات قراءة الرسائل (Ghost Read)", "قراءة الرسائل والاستماع للبصمات دون إرسال الصح الأزرق للمرسل").setChecked(MidoConfig.hideReadReceipts));

        items.add(UItem.asButtonCheck(5, "تجميد آخر ظهور (Freeze Last Seen)", "تثبيت وقت آخر ظهور عند توقيت محدد ومنع تحديثه للآخرين").setChecked(MidoConfig.freezeLastSeen));

        items.add(UItem.asButtonCheck(6, "إخفاء حالة الاتصال (Hide Online Status)", "تصفح المحادثات دون الظهور (متصل الآن / Online) للآخرين").setChecked(MidoConfig.hideOnlineStatus));

        items.add(UItem.asButtonCheck(7, "إخفاء جاري الكتابة والتسجيل (Hide Typing & Recording)", "إخفاء مؤشرات الكتابة، تسجيل الصوت، أو إرسال الوسائط أثناء الدردشة").setChecked(MidoConfig.hideTyping));

        items.add(UItem.asButtonCheck(3, "مشاهدة الاستوري بدون ظهور 👁️ (Stealth Stories)", "مشاهدة قصص وحالات الآخرين دون معرفتهم ودون تسجيل اسمك في المشاهدات").setChecked(MidoConfig.stealthStories));

        items.add(UItem.asButtonCheck(27, "حفظ الاستوري المحذوفة 12 ساعة 🗑 (Anti-Delete Stories)", "إظهار الاستوريات التي يحذفها الآخرون مع علامة (محذوفة) وحذفها تلقائياً بعد 12 ساعة").setChecked(MidoConfig.antiDeleteStories));

        items.add(UItem.asButtonCheck(8, "المستمع المتخفي بالمكالمات الصوتية 🎧 (Ghost Voice Call)", "الدخول للمكالمات الجماعية بوضع المستمع الصامت المتخفي ومنع بث الصوت").setChecked(MidoConfig.ghostCall));

        items.add(UItem.asHeader("تعدد الحسابات (Multi-Accounts)"));
        items.add(UItem.asButtonCheck(2, "تعدد الحسابات الخارق 🚀 (20 حساب)", "تسجيل الدخول حتى 20 حساباً بدون قيود وبدون الحاجة لـ Telegram Premium").setChecked(MidoConfig.unlimitedAccounts));
        items.add(UItem.asShadow("يتيح لك التبديل بين حتى 20 رقماً هاتفياً مسجلاً في نفس التطبيق بسلاسة."));

        items.add(UItem.asHeader("المظهر وشريط الموسيقى وشارة التوثيق (exteraGram Features)"));
        items.add(UItem.asButtonCheck(40, "شريط الموسيقى في البروفايل 🎵 (Profile Music Bar)", "عرض شريط الموسيقى الزجاجي الأنيق والتحكم بالصوتيات داخل الملف الشخصي مثل exteraGram").setChecked(MidoConfig.profileMusicBar));

        items.add(UItem.asButtonCheck(41, "شارة التوثيق الزرقاء ونجمة المطور ⭐ (Dev Verified)", "إظهار شارة التوثيق الرسمية ونجمة المطور لحسابك ولحساب المطور @SourceMedo لدى كل مستخدمي النسخة").setChecked(MidoConfig.devBadgeEnabled));

        items.add(UItem.asButtonCheck(42, "توثيق حسابي الشخصي أيضاً 👑 (Verify My Account)", "إظهار شارة التوثيق الدائمة بجانب اسم حسابك في كل مكان داخل التطبيق").setChecked(MidoConfig.myAccountVerified));

        items.add(UItem.asButton(43, "✏️ تخصيص يوزر المطور الموثق: @" + MidoConfig.devUsername, "اضغط لتعديل معرف التليجرام الخاص بالمطور الموثق"));
        items.add(UItem.asShadow("تمنحك هذه الميزات مظهراً مميزاً وشريط موسيقى تفاعلي زجاجي مستوحى من exteraGram وشعار موثق حصري."));

        items.add(UItem.asHeader("مجتمع وقنوات المطور ميدو"));
        items.add(UItem.asButton(10, "📢 قناة التحديثات والسورس (@SourceMedo)", "اضغط للانضمام لقناة وسورس المطور الرسمية"));
        items.add(UItem.asButton(11, "💬 مجموعة المناقشة والدعم الفني", "اضغط للانضمام لجروب النقاش وحل المشكلات"));
        items.add(UItem.asShadow("انضم لقنوات المطور للحصول على التحديثات والميزات الجديدة أولاً بأول."));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 30) {
            MidoConfig.setForceSubEnabled(!MidoConfig.forceSubEnabled);
            setRowChecked(view, MidoConfig.forceSubEnabled);
        } else if (item.id == 31) {
            if (getParentActivity() == null) return;
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle("قناة أو مجموعة الاشتراك الإجباري");
            final EditText editText = new EditText(getParentActivity());
            editText.setText(MidoConfig.forceSubChannel);
            editText.setHint("@SourceMedo أو رابط القناة/الجروب");
            builder.setView(editText);
            builder.setPositiveButton("حفظ", (d, w) -> {
                String val = editText.getText().toString().trim();
                if (!TextUtils.isEmpty(val)) {
                    MidoConfig.setForceSubChannel(val);
                    if (listView != null && listView.adapter != null) {
                        listView.adapter.update(true);
                    }
                }
            });
            builder.setNegativeButton("إلغاء", null);
            showDialog(builder.create());
        } else if (item.id == 32) {
            presentFragment(new MidoForceSubEditorActivity());
        } else if (item.id == 33) {
            MidoConfig.setForceSubExcludeContacts(!MidoConfig.forceSubExcludeContacts);
            setRowChecked(view, MidoConfig.forceSubExcludeContacts);
        } else if (item.id == 20) {
            MidoConfig.setSaveRestrictedContent(!MidoConfig.saveRestrictedContent);
            setRowChecked(view, MidoConfig.saveRestrictedContent);
        } else if (item.id == 21) {
            MidoConfig.setAllowScreenshots(!MidoConfig.allowScreenshots);
            setRowChecked(view, MidoConfig.allowScreenshots);
        } else if (item.id == 22) {
            MidoConfig.setSaveExpiringMedia(!MidoConfig.saveExpiringMedia);
            setRowChecked(view, MidoConfig.saveExpiringMedia);
        } else if (item.id == 1) {
            MidoConfig.setAntiDelete(!MidoConfig.antiDelete);
            setRowChecked(view, MidoConfig.antiDelete);
        } else if (item.id == 26) {
            MidoConfig.setKeepKickedChats(!MidoConfig.keepKickedChats);
            setRowChecked(view, MidoConfig.keepKickedChats);
        } else if (item.id == 23) {
            MidoConfig.setShowEditHistory(!MidoConfig.showEditHistory);
            setRowChecked(view, MidoConfig.showEditHistory);
        } else if (item.id == 24) {
            MidoConfig.setForwardWithoutQuote(!MidoConfig.forwardWithoutQuote);
            setRowChecked(view, MidoConfig.forwardWithoutQuote);
        } else if (item.id == 25) {
            MidoConfig.setConfirmCall(!MidoConfig.confirmCall);
            setRowChecked(view, MidoConfig.confirmCall);
        } else if (item.id == 4) {
            MidoConfig.setHideReadReceipts(!MidoConfig.hideReadReceipts);
            setRowChecked(view, MidoConfig.hideReadReceipts);
        } else if (item.id == 5) {
            MidoConfig.setFreezeLastSeen(!MidoConfig.freezeLastSeen);
            setRowChecked(view, MidoConfig.freezeLastSeen);
        } else if (item.id == 6) {
            MidoConfig.setHideOnlineStatus(!MidoConfig.hideOnlineStatus);
            setRowChecked(view, MidoConfig.hideOnlineStatus);
        } else if (item.id == 7) {
            MidoConfig.setHideTyping(!MidoConfig.hideTyping);
            setRowChecked(view, MidoConfig.hideTyping);
        } else if (item.id == 3) {
            MidoConfig.setStealthStories(!MidoConfig.stealthStories);
            setRowChecked(view, MidoConfig.stealthStories);
        } else if (item.id == 27) {
            MidoConfig.setAntiDeleteStories(!MidoConfig.antiDeleteStories);
            setRowChecked(view, MidoConfig.antiDeleteStories);
        } else if (item.id == 8) {
            MidoConfig.setGhostCall(!MidoConfig.ghostCall);
            setRowChecked(view, MidoConfig.ghostCall);
        } else if (item.id == 2) {
            MidoConfig.setUnlimitedAccounts(!MidoConfig.unlimitedAccounts);
            setRowChecked(view, MidoConfig.unlimitedAccounts);
        } else if (item.id == 40) {
            MidoConfig.setProfileMusicBar(!MidoConfig.profileMusicBar);
            setRowChecked(view, MidoConfig.profileMusicBar);
        } else if (item.id == 41) {
            MidoConfig.setDevBadgeEnabled(!MidoConfig.devBadgeEnabled);
            setRowChecked(view, MidoConfig.devBadgeEnabled);
        } else if (item.id == 42) {
            MidoConfig.setMyAccountVerified(!MidoConfig.myAccountVerified);
            setRowChecked(view, MidoConfig.myAccountVerified);
        } else if (item.id == 43) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle("معرف المطور الموثق");
            final EditText editText = new EditText(getParentActivity());
            editText.setText(MidoConfig.devUsername);
            editText.setSelection(editText.getText().length());
            builder.setView(editText);
            builder.setPositiveButton("حفظ", (dialog, which) -> {
                String val = editText.getText().toString().trim().replace("@", "");
                if (!TextUtils.isEmpty(val)) {
                    MidoConfig.setDevUsername(val);
                    if (listView != null && listView.adapter != null) {
                        listView.adapter.update(true);
                    }
                }
            });
            builder.setNegativeButton("إلغاء", null);
            showDialog(builder.create());
        } else if (item.id == 10) {
            MessagesController.getInstance(currentAccount).openByUserName("SourceMedo", this, 1);
        } else if (item.id == 11) {
            Browser.openUrl(getParentActivity(), "https://t.me/+m3DnjfmnWpFmYWVi");
        }
    }

    private void setRowChecked(View view, boolean checked) {
        if (view instanceof NotificationsCheckCell) {
            ((NotificationsCheckCell) view).setChecked(checked);
        } else if (view instanceof TextCheckCell) {
            ((TextCheckCell) view).setChecked(checked);
        }
    }
}
