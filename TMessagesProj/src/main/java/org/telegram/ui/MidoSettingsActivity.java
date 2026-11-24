package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.MidoConfig;
import org.telegram.messenger.R;
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
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1) {
            MidoConfig.setAntiDelete(!MidoConfig.antiDelete);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(MidoConfig.antiDelete);
            }
        }
    }
}
