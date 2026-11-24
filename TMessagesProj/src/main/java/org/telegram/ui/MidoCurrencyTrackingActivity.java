package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MidoCurrencyManager;
import org.telegram.messenger.MidoCurrencyManager.CurrencyAsset;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleActionBarMenu;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;
import org.telegram.utils.glass.source.GlassSourceRenderNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

public class MidoCurrencyTrackingActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {

    private boolean hasMainTabs;
    private int additionNavigationBarHeight;
    private UniversalRecyclerView listView;
    private FrameLayout contentView;

    private int selectedCategory = MidoCurrencyManager.CATEGORY_ALL;
    private String searchQuery = "";

    private final ArrayList<CurrencyAsset> visibleAssets = new ArrayList<>();

    public MidoCurrencyTrackingActivity() {
        super();
    }

    public MidoCurrencyTrackingActivity(android.os.Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        if (arguments != null) {
            hasMainTabs = arguments.getBoolean("hasMainTabs", false);
        }
        additionNavigationBarHeight = hasMainTabs ? dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        return super.onFragmentCreate();
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);

        contentView = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                glassEngine.invalidate();
                if (!hasMainTabs) {
                    AndroidUtilities.drawNavigationBarProtection(canvas, this, getThemedColor(Theme.key_windowBackgroundWhite), mSystemInsets.bottom);
                }
            }
        };
        contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fragmentView = contentView;

        if (!hasMainTabs) {
            simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        }
        simpleActionBar.setTitle(LocaleController.isRTL ? "تتبع العملات" : "Currency Tracking");
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (!hasMainTabs) {
                        finishFragment();
                    }
                } else if (id == 1) {
                    triggerRefresh(true);
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
                .setSearchFieldHint(LocaleController.isRTL ? "ابحث عن عملة (TON, Gram, BTC...)" : "Search coin (TON, Gram, BTC...)");

        menu.addItem(1, R.drawable.msg_topic_restart, resourceProvider);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setCaptureSectionsDecoratorAllowed(true);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        listView.setClipToPadding(false);
        BaseFragmentSimpleGlass.setListViewParams(listView, 0, 0, ActionBar.getCurrentActionBarHeight() + dp(6), additionNavigationBarHeight);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (listView.scrollingByUser) {
                    AndroidUtilities.hideKeyboard(fragmentView);
                }
            }
        });
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        contentView.addView(simpleActionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        MidoCurrencyManager.refreshPricesIfNeeded(() -> {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
        });

        return fragmentView;
    }

    private void triggerRefresh(boolean showBulletin) {
        MidoCurrencyManager.refreshPrices(() -> {
            if (listView != null && listView.adapter != null) {
                listView.adapter.update(true);
            }
            if (showBulletin && fragmentView != null) {
                BulletinFactory.of(this).createSuccessBulletin(
                        LocaleController.isRTL ? "تم تحديث أسعار العملات بنجاح" : "Currency prices updated"
                ).show();
            }
        });
    }

    private void refreshList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        Context context = getContext();
        if (context == null) {
            return;
        }

        ArrayList<CurrencyAsset> allAssets = MidoCurrencyManager.getAssets();
        visibleAssets.clear();

        String q = searchQuery != null ? searchQuery.toLowerCase(Locale.US) : "";
        for (int i = 0; i < allAssets.size(); i++) {
            CurrencyAsset a = allAssets.get(i);
            if (selectedCategory != MidoCurrencyManager.CATEGORY_ALL && a.category != selectedCategory) {
                continue;
            }
            if (!TextUtils.isEmpty(q)) {
                boolean match = a.symbol.toLowerCase(Locale.US).contains(q)
                        || a.nameEn.toLowerCase(Locale.US).contains(q)
                        || a.nameAr.contains(q)
                        || a.networkTag.toLowerCase(Locale.US).contains(q);
                if (!match) continue;
            }
            visibleAssets.add(a);
        }

        if (TextUtils.isEmpty(q)) {
            MarketOverviewCard overview = new MarketOverviewCard(context, allAssets);
            items.add(UItem.asCustom(1000, overview));
            items.add(UItem.asShadow(null));
        }

        FilterChipsBar chipsBar = new FilterChipsBar(context);
        items.add(UItem.asCustomShadow(1001, chipsBar));

        if (visibleAssets.isEmpty()) {
            items.add(UItem.asHeader(LocaleController.isRTL ? "نتائج البحث" : "Search Results"));
            items.add(UItem.asButton(1002, LocaleController.isRTL ? "لا توجد عملات مطابقة" : "No matching currencies",
                    LocaleController.isRTL ? "جرّب البحث باسم TON أو GRAM أو BEP20 أو BTC" : "Try TON, GRAM, BEP20, USD or BTC"));
        } else {
            items.add(UItem.asHeader(getCategoryHeaderTitle()));
            for (int i = 0; i < visibleAssets.size(); i++) {
                CurrencyAsset asset = visibleAssets.get(i);
                CurrencyRowView row = new CurrencyRowView(context, asset);
                UItem rowItem = UItem.asCustom(2000 + i, row);
                rowItem.object = asset;
                items.add(rowItem);
            }
            items.add(UItem.asShadow(LocaleController.isRTL
                    ? "• يتم حفظ آخر سعر قبل كل تحديث لحساب الفرق الفعلي (كان ➔ الآن).\n• اضغط على أي عملة لفتح التحليل التفصيلي وحاسبة التحويل الفورية للدولار والجنيه."
                    : "• Previous prices are tracked to show real movement (Prev ➔ Now).\n• Tap any currency to open the detailed chart and instant USD/EGP converter."));
        }
    }

    private String getCategoryHeaderTitle() {
        if (selectedCategory == MidoCurrencyManager.CATEGORY_TELEGRAM) {
            return LocaleController.isRTL ? "عملات منظومة تيليجرام (TON & Jettons)" : "Telegram Ecosystem (TON & Jettons)";
        } else if (selectedCategory == MidoCurrencyManager.CATEGORY_BEP20) {
            return LocaleController.isRTL ? "عملات شبكة BEP-20 (BSC)" : "BEP-20 Network Tokens";
        } else if (selectedCategory == MidoCurrencyManager.CATEGORY_MARKET) {
            return LocaleController.isRTL ? "الدولار والعملات الرئيسية والذهب" : "Dollar, Crypto Majors & Gold";
        }
        return LocaleController.isRTL ? "الأسعار الحية وتحليل التغير" : "Live Prices & Price Analysis";
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 1000) {
            triggerRefresh(true);
        } else if (item.object instanceof CurrencyAsset) {
            showCurrencyDetailSheet((CurrencyAsset) item.object);
        }
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        if (listView != null) {
            BaseFragmentSimpleGlass.setListViewParams(listView, top, bottom, ActionBar.getCurrentActionBarHeight() + dp(6), additionNavigationBarHeight);
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }

    @Override
    public void onThemeColorsChanged() {
        super.onThemeColorsChanged();
        if (contentView != null) {
            contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
    }

    @Override
    public void addThemeColorKeys(HashSet<Integer> keys) {
        super.addThemeColorKeys(keys);
        keys.add(Theme.key_windowBackgroundGray);
        keys.add(Theme.key_windowBackgroundWhite);
    }

    @Override
    public GlassSourceRenderNode getGlassSource() {
        return getBaseSimpleGlass().getGlassSource();
    }

    @Override
    public GlassSourceRenderNode getFrostedGlassSource() {
        return getBaseSimpleGlass().getFrostedGlassSource();
    }

    @Override
    public void onParentScrollToTop() {
        if (listView != null) {
            listView.smoothScrollToPosition(0);
        }
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        return !simpleActionBar.isSearchFieldVisible();
    }

    @Nullable
    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    // =========================================================================
    // Detail & Instant Converter BottomSheet (Telegram Native)
    // =========================================================================

    private void showCurrencyDetailSheet(CurrencyAsset asset) {
        if (getParentActivity() == null) {
            return;
        }
        Context context = getParentActivity();
        BottomSheet.Builder builder = new BottomSheet.Builder(context, true, resourceProvider);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(22));

        // Top Header: Emblem + Name + Live Price
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        CoinBadgeView badge = new CoinBadgeView(context, asset);
        header.addView(badge, LayoutHelper.createLinear(46, 46, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView titleTv = new TextView(context);
        titleTv.setText(asset.nameAr + " (" + asset.symbol + ")");
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleTv.setTypeface(AndroidUtilities.bold());
        titleTv.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        titleCol.addView(titleTv);

        TextView subTv = new TextView(context);
        subTv.setText(asset.nameEn + " • " + asset.networkTag);
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subTv.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
        titleCol.addView(subTv);

        LinearLayout priceCol = new LinearLayout(context);
        priceCol.setOrientation(LinearLayout.VERTICAL);
        priceCol.setGravity(Gravity.END);
        header.addView(priceCol, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView bigPriceTv = new TextView(context);
        bigPriceTv.setText(MidoCurrencyManager.formatPrice(asset.currentPrice, asset.isEgpQuoted));
        bigPriceTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        bigPriceTv.setTypeface(AndroidUtilities.bold());
        bigPriceTv.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        priceCol.addView(bigPriceTv);

        if (!asset.isEgpQuoted) {
            TextView egpEquivTv = new TextView(context);
            double egpVal = asset.currentPrice * MidoCurrencyManager.getUsdToEgpRate();
            egpEquivTv.setText("≈ " + MidoCurrencyManager.formatPrice(egpVal, true));
            egpEquivTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            egpEquivTv.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
            priceCol.addView(egpEquivTv);
        }

        // Price Analysis Box: Previous -> Current & 24h High/Low
        LinearLayout analysisBox = new LinearLayout(context);
        analysisBox.setOrientation(LinearLayout.VERTICAL);
        analysisBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        analysisBox.setBackground(Theme.createRoundRectDrawable(dp(14),
                Theme.multAlpha(getThemedColor(Theme.key_featuredStickers_addButton), 0.08f)));
        root.addView(analysisBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        double prev = asset.getComparisonBasePrice();
        double deltaPct = asset.getComparisonDeltaPercent();
        boolean up = deltaPct >= 0;
        int trendColor = up ? 0xFF1FA75A : 0xFFE53935;

        TextView analysisTitle = new TextView(context);
        analysisTitle.setText(LocaleController.isRTL ? "تحليل حركة السعر:" : "Price Movement Analysis:");
        analysisTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        analysisTitle.setTypeface(AndroidUtilities.bold());
        analysisTitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
        analysisBox.addView(analysisTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView movementTv = new TextView(context);
        String prevLabel = asset.hasDistinctSnapshot() ? "كان " : "قبل 24 ساعة كان ";
        movementTv.setText(prevLabel
                + MidoCurrencyManager.formatPrice(prev, asset.isEgpQuoted)
                + "  ➔  أصبح "
                + MidoCurrencyManager.formatPrice(asset.currentPrice, asset.isEgpQuoted)
                + "  (" + MidoCurrencyManager.formatPercent(deltaPct) + ")");
        movementTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        movementTv.setTypeface(AndroidUtilities.bold());
        movementTv.setTextColor(trendColor);
        analysisBox.addView(movementTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView rangeTv = new TextView(context);
        rangeTv.setText("أعلى سعر: " + MidoCurrencyManager.formatPrice(asset.high24h, asset.isEgpQuoted)
                + "   •   أدنى سعر: " + MidoCurrencyManager.formatPrice(asset.low24h, asset.isEgpQuoted));
        rangeTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        rangeTv.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
        analysisBox.addView(rangeTv);

        // Large Interactive Sparkline Chart
        SparklineChartView bigChart = new SparklineChartView(context, asset, true);
        root.addView(bigChart, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 118, 0, 0, 0, 14));

        // Instant Converter Box
        LinearLayout convBox = new LinearLayout(context);
        convBox.setOrientation(LinearLayout.VERTICAL);
        convBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        convBox.setBackground(Theme.createRoundRectDrawable(dp(14),
                Theme.multAlpha(getThemedColor(Theme.key_dialogTextGray3), 0.08f)));
        root.addView(convBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        TextView convHeader = new TextView(context);
        convHeader.setText("محول العملات الفوري (" + asset.symbol + " ⇄ USD / EGP)");
        convHeader.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        convHeader.setTypeface(AndroidUtilities.bold());
        convHeader.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        convBox.addView(convHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        LinearLayout convRow = new LinearLayout(context);
        convRow.setOrientation(LinearLayout.HORIZONTAL);
        convRow.setGravity(Gravity.CENTER_VERTICAL);
        convBox.addView(convRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        EditTextBoldCursor amountInput = new EditTextBoldCursor(context);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountInput.setText("10");
        amountInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        amountInput.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        amountInput.setHintColor(getThemedColor(Theme.key_dialogTextHint));
        amountInput.setLineColors(
                getThemedColor(Theme.key_windowBackgroundWhiteInputField),
                getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                getThemedColor(Theme.key_text_RedRegular)
        );
        amountInput.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        amountInput.setHint(asset.symbol);
        convRow.addView(amountInput, LayoutHelper.createLinear(110, 44, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        TextView resultTv = new TextView(context);
        resultTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        resultTv.setTypeface(AndroidUtilities.bold());
        resultTv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText4));
        convRow.addView(resultTv, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        Runnable updateConv = () -> {
            double qty = 0d;
            try {
                String txt = amountInput.getText().toString().trim();
                if (!TextUtils.isEmpty(txt)) {
                    qty = Double.parseDouble(txt);
                }
            } catch (Throwable ignore) {
            }
            if (asset.isEgpQuoted) {
                double egpTotal = qty * asset.currentPrice;
                resultTv.setText("= " + MidoCurrencyManager.formatPrice(egpTotal, true));
            } else {
                double usdTotal = qty * asset.currentPrice;
                double egpTotal = usdTotal * MidoCurrencyManager.getUsdToEgpRate();
                resultTv.setText("= " + MidoCurrencyManager.formatPrice(usdTotal, false)
                        + "\n≈ " + MidoCurrencyManager.formatPrice(egpTotal, true));
            }
        };
        updateConv.run();

        amountInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateConv.run();
            }
        });

        // Telegram Action Button
        TextView closeBtn = new TextView(context);
        closeBtn.setText(LocaleController.isRTL ? "تحديث السعر الآن" : "Refresh Price Now");
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        closeBtn.setTypeface(AndroidUtilities.bold());
        closeBtn.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        closeBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                dp(10),
                getThemedColor(Theme.key_featuredStickers_addButton),
                getThemedColor(Theme.key_featuredStickers_addButtonPressed)
        ));
        root.addView(closeBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46));

        builder.setCustomView(root);
        BottomSheet sheet = builder.create();
        closeBtn.setOnClickListener(v -> {
            sheet.dismiss();
            triggerRefresh(true);
        });
        sheet.show();
    }

    // =========================================================================
    // Custom Native Views (Overview Card, Filter Chips, Currency Row, Sparkline)
    // =========================================================================

    private class MarketOverviewCard extends LinearLayout {
        public MarketOverviewCard(Context context, ArrayList<CurrencyAsset> allAssets) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(16), dp(14), dp(16), dp(14));

            LinearLayout topRow = new LinearLayout(context);
            topRow.setOrientation(HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            addView(topRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

            TextView title = new TextView(context);
            title.setText(LocaleController.isRTL ? "مؤشر سوق تيليجرام والعملات" : "Telegram & Crypto Market Pulse");
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
            topRow.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            TextView statusPill = new TextView(context);
            boolean live = MidoCurrencyManager.isLastSyncLive();
            statusPill.setText(live ? "● مباشر Live" : "● مرجعي Offline");
            statusPill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            statusPill.setTypeface(AndroidUtilities.bold());
            int pillColor = live ? 0xFF1FA75A : getThemedColor(Theme.key_windowBackgroundWhiteBlueText4);
            statusPill.setTextColor(pillColor);
            statusPill.setPadding(dp(8), dp(3), dp(8), dp(3));
            statusPill.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.multAlpha(pillColor, 0.13f)));
            topRow.addView(statusPill, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            // 3 Top Spotlight Tiles: TON | GRAM | USD/EGP
            LinearLayout tilesRow = new LinearLayout(context);
            tilesRow.setOrientation(HORIZONTAL);
            addView(tilesRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

            CurrencyAsset ton = findAsset(allAssets, "TON");
            CurrencyAsset gram = findAsset(allAssets, "GRAM");
            CurrencyAsset usd = findAsset(allAssets, "USD");
            if (ton != null) tilesRow.addView(createSpotTile(context, ton), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 6, 0));
            if (gram != null) tilesRow.addView(createSpotTile(context, gram), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 3, 0, 3, 0));
            if (usd != null) tilesRow.addView(createSpotTile(context, usd), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 6, 0, 0, 0));

            TextView hint = new TextView(context);
            hint.setText("سعر الدولار مقابل الجنيه: 1 USD = " + MidoCurrencyManager.formatPrice(MidoCurrencyManager.getUsdToEgpRate(), true)
                    + "  •  اضغط للتحديث الفوري");
            hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            hint.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            addView(hint);
        }

        private CurrencyAsset findAsset(ArrayList<CurrencyAsset> list, String id) {
            for (int i = 0; i < list.size(); i++) {
                if (id.equals(list.get(i).id)) return list.get(i);
            }
            return null;
        }

        private View createSpotTile(Context context, CurrencyAsset asset) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            card.setBackground(Theme.createRoundRectDrawable(dp(12),
                    Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundGray), 0.85f)));

            TextView sym = new TextView(context);
            sym.setText(asset.symbol);
            sym.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            sym.setTypeface(AndroidUtilities.bold());
            sym.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            card.addView(sym);

            TextView price = new TextView(context);
            price.setText(MidoCurrencyManager.formatPrice(asset.currentPrice, asset.isEgpQuoted));
            price.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            price.setTypeface(AndroidUtilities.bold());
            price.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            price.setSingleLine();
            card.addView(price, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 2));

            double pct = asset.getComparisonDeltaPercent();
            boolean up = pct >= 0;
            TextView chg = new TextView(context);
            chg.setText((up ? "▲ " : "▼ ") + MidoCurrencyManager.formatPercent(pct));
            chg.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            chg.setTypeface(AndroidUtilities.bold());
            chg.setTextColor(up ? 0xFF1FA75A : 0xFFE53935);
            card.addView(chg);

            return card;
        }
    }

    private class FilterChipsBar extends HorizontalScrollView {
        public FilterChipsBar(Context context) {
            super(context);
            setHorizontalScrollBarEnabled(false);
            setClipToPadding(false);
            setPadding(dp(12), dp(2), dp(12), dp(8));

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            addView(row, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            addChip(context, row, MidoCurrencyManager.CATEGORY_ALL, LocaleController.isRTL ? "الكل" : "All");
            addChip(context, row, MidoCurrencyManager.CATEGORY_TELEGRAM, LocaleController.isRTL ? "تيليجرام (TON / Gram)" : "Telegram (TON/Gram)");
            addChip(context, row, MidoCurrencyManager.CATEGORY_BEP20, LocaleController.isRTL ? "شبكة BEP-20" : "BEP-20");
            addChip(context, row, MidoCurrencyManager.CATEGORY_MARKET, LocaleController.isRTL ? "الدولار و BTC والذهب" : "USD, BTC & Gold");
        }

        private void addChip(Context context, LinearLayout row, int catId, String label) {
            boolean selected = selectedCategory == catId;
            TextView chip = new TextView(context);
            chip.setText(label);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));
            int accent = getThemedColor(Theme.key_featuredStickers_addButton);
            if (selected) {
                chip.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
                chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                        dp(18),
                        accent,
                        getThemedColor(Theme.key_featuredStickers_addButtonPressed)
                ));
            } else {
                chip.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                        dp(18),
                        getThemedColor(Theme.key_windowBackgroundWhite),
                        Theme.multAlpha(accent, 0.14f)
                ));
            }
            chip.setOnClickListener(v -> {
                if (selectedCategory != catId) {
                    selectedCategory = catId;
                    refreshList();
                }
            });
            row.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        }
    }

    private class CurrencyRowView extends LinearLayout {
        public CurrencyRowView(Context context, CurrencyAsset asset) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(14), dp(11), dp(14), dp(11));

            CoinBadgeView badge = new CoinBadgeView(context, asset);
            addView(badge, LayoutHelper.createLinear(42, 42, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

            LinearLayout leftCol = new LinearLayout(context);
            leftCol.setOrientation(VERTICAL);
            addView(leftCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

            // Top line: Arabic name + Symbol + Network pill
            LinearLayout titleRow = new LinearLayout(context);
            titleRow.setOrientation(HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            leftCol.addView(titleRow);

            TextView nameTv = new TextView(context);
            nameTv.setText(asset.symbol + " • " + (LocaleController.isRTL ? asset.nameAr : asset.nameEn));
            nameTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            nameTv.setTypeface(AndroidUtilities.bold());
            nameTv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTv.setSingleLine();
            nameTv.setEllipsize(TextUtils.TruncateAt.END);
            titleRow.addView(nameTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));

            TextView netTv = new TextView(context);
            netTv.setText(asset.networkTag);
            netTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            netTv.setTypeface(AndroidUtilities.bold());
            netTv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            netTv.setSingleLine();
            netTv.setEllipsize(TextUtils.TruncateAt.END);
            leftCol.addView(netTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 3));

            // Comparison analysis line: "كان $1.90 ➔ $1.80"
            double basePrev = asset.getComparisonBasePrice();
            double deltaPct = asset.getComparisonDeltaPercent();
            boolean up = deltaPct >= 0;
            int deltaColor = up ? 0xFF1FA75A : 0xFFE53935;

            TextView compareTv = new TextView(context);
            String arrowPrefix = asset.hasDistinctSnapshot() ? "كان " : "24س ";
            compareTv.setText(arrowPrefix
                    + MidoCurrencyManager.formatPrice(basePrev, asset.isEgpQuoted)
                    + " ➔ "
                    + MidoCurrencyManager.formatPrice(asset.currentPrice, asset.isEgpQuoted));
            compareTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
            compareTv.setTypeface(AndroidUtilities.bold());
            compareTv.setTextColor(deltaColor);
            compareTv.setSingleLine();
            compareTv.setEllipsize(TextUtils.TruncateAt.END);
            leftCol.addView(compareTv);

            // Sparkline chart in middle-right
            SparklineChartView spark = new SparklineChartView(context, asset, false);
            addView(spark, LayoutHelper.createLinear(58, 30, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            // Far right: Current Price + % pill
            LinearLayout rightCol = new LinearLayout(context);
            rightCol.setOrientation(VERTICAL);
            rightCol.setGravity(Gravity.END);
            addView(rightCol, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            TextView priceTv = new TextView(context);
            priceTv.setText(MidoCurrencyManager.formatPrice(asset.currentPrice, asset.isEgpQuoted));
            priceTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            priceTv.setTypeface(AndroidUtilities.bold());
            priceTv.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            rightCol.addView(priceTv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END, 0, 0, 0, 3));

            TextView pctPill = new TextView(context);
            pctPill.setText((up ? "▲ " : "▼ ") + MidoCurrencyManager.formatPercent(deltaPct));
            pctPill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            pctPill.setTypeface(AndroidUtilities.bold());
            pctPill.setTextColor(deltaColor);
            pctPill.setPadding(dp(7), dp(2), dp(7), dp(2));
            pctPill.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.multAlpha(deltaColor, 0.13f)));
            rightCol.addView(pctPill, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    private static class CoinBadgeView extends View {
        private final CurrencyAsset asset;
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        public CoinBadgeView(Context context, CurrencyAsset asset) {
            super(context);
            this.asset = asset;
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(1.5f));
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(AndroidUtilities.bold());
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(w, h) / 2f - dp(1.5f);

            bgPaint.setShader(new LinearGradient(0, 0, w, h, asset.badgeColor, asset.badgeAccent, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, bgPaint);

            ringPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 60));
            canvas.drawCircle(cx, cy, r - dp(1), ringPaint);

            String label = asset.badgeText;
            float textSize = label.length() <= 1 ? dp(18) : (label.length() <= 3 ? dp(12.5f) : dp(10.5f));
            textPaint.setTextSize(textSize);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textY = cy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, cx, textY, textPaint);
        }
    }

    private class SparklineChartView extends View {
        private final CurrencyAsset asset;
        private final boolean detailed;
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path linePath = new Path();
        private final Path fillPath = new Path();

        public SparklineChartView(Context context, CurrencyAsset asset, boolean detailed) {
            super(context);
            this.asset = asset;
            this.detailed = detailed;

            boolean up = asset.getComparisonDeltaPercent() >= 0;
            int color = up ? 0xFF1FA75A : 0xFFE53935;

            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(dp(detailed ? 2.4f : 1.9f));
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            linePaint.setStrokeJoin(Paint.Join.ROUND);
            linePaint.setColor(color);

            fillPaint.setStyle(Paint.Style.FILL);
            dotPaint.setColor(color);

            gridPaint.setStyle(Paint.Style.STROKE);
            gridPaint.setStrokeWidth(dp(1));
            gridPaint.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(4)}, 0));
            gridPaint.setColor(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteGrayText), 0.25f));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            ArrayList<Float> pts = asset.history;
            if (pts == null || pts.size() < 2) {
                return;
            }
            float w = getWidth();
            float h = getHeight();
            float padTop = dp(detailed ? 10 : 4);
            float padBottom = dp(detailed ? 10 : 4);
            float padX = dp(detailed ? 6 : 3);
            float drawW = Math.max(1f, w - padX * 2f);
            float drawH = Math.max(1f, h - padTop - padBottom);

            float min = Float.MAX_VALUE;
            float max = Float.MIN_VALUE;
            for (int i = 0; i < pts.size(); i++) {
                float v = pts.get(i);
                if (v < min) min = v;
                if (v > max) max = v;
            }
            float range = Math.max(max - min, Math.max(1e-6f, max * 0.005f));

            if (detailed) {
                canvas.drawLine(padX, padTop + drawH * 0.5f, w - padX, padTop + drawH * 0.5f, gridPaint);
            }

            linePath.reset();
            fillPath.reset();

            int n = pts.size();
            float lastX = padX;
            float lastY = padTop;
            for (int i = 0; i < n; i++) {
                float x = padX + (drawW * i) / (float) (n - 1);
                float norm = (pts.get(i) - min) / range;
                float y = padTop + drawH * (1f - norm);
                if (i == 0) {
                    linePath.moveTo(x, y);
                    fillPath.moveTo(x, h - padBottom);
                    fillPath.lineTo(x, y);
                } else {
                    float prevX = padX + (drawW * (i - 1)) / (float) (n - 1);
                    float prevNorm = (pts.get(i - 1) - min) / range;
                    float prevY = padTop + drawH * (1f - prevNorm);
                    float midX = (prevX + x) * 0.5f;
                    linePath.cubicTo(midX, prevY, midX, y, x, y);
                    fillPath.cubicTo(midX, prevY, midX, y, x, y);
                }
                lastX = x;
                lastY = y;
            }
            fillPath.lineTo(lastX, h - padBottom);
            fillPath.close();

            int baseColor = linePaint.getColor();
            fillPaint.setShader(new LinearGradient(0, padTop, 0, h - padBottom,
                    Theme.multAlpha(baseColor, detailed ? 0.24f : 0.20f),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawPath(fillPath, fillPaint);
            canvas.drawPath(linePath, linePaint);

            dotPaint.setColor(Theme.multAlpha(baseColor, 0.26f));
            canvas.drawCircle(lastX, lastY, dp(detailed ? 5f : 3.5f), dotPaint);
            dotPaint.setColor(baseColor);
            canvas.drawCircle(lastX, lastY, dp(detailed ? 2.6f : 1.9f), dotPaint);
        }
    }
}
