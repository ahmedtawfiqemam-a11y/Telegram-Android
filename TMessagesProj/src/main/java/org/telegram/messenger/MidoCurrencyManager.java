package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class MidoCurrencyManager {

    public static final int CATEGORY_ALL = 0;
    public static final int CATEGORY_TELEGRAM = 1;
    public static final int CATEGORY_BEP20 = 2;
    public static final int CATEGORY_MARKET = 3;

    public static class CurrencyAsset {
        public final String id;
        public final String symbol;
        public final String nameAr;
        public final String nameEn;
        public final String networkTag;
        public final int category;
        public final int badgeColor;
        public final int badgeAccent;
        public final String badgeText;
        public final String coinGeckoId;
        public final String binanceSymbol;
        public final boolean isEgpQuoted;

        public double currentPrice;
        public double previousPrice;
        public double change24h;
        public double high24h;
        public double low24h;
        public final ArrayList<Float> history = new ArrayList<>();

        public CurrencyAsset(
                String id,
                String symbol,
                String nameAr,
                String nameEn,
                String networkTag,
                int category,
                int badgeColor,
                int badgeAccent,
                String badgeText,
                String coinGeckoId,
                String binanceSymbol,
                boolean isEgpQuoted,
                double defaultPrice,
                double defaultPrevPrice,
                double defaultChange24h,
                float[] defaultHistory
        ) {
            this.id = id;
            this.symbol = symbol;
            this.nameAr = nameAr;
            this.nameEn = nameEn;
            this.networkTag = networkTag;
            this.category = category;
            this.badgeColor = badgeColor;
            this.badgeAccent = badgeAccent;
            this.badgeText = badgeText;
            this.coinGeckoId = coinGeckoId;
            this.binanceSymbol = binanceSymbol;
            this.isEgpQuoted = isEgpQuoted;
            this.currentPrice = defaultPrice;
            this.previousPrice = defaultPrevPrice;
            this.change24h = defaultChange24h;
            if (defaultHistory != null) {
                for (float v : defaultHistory) {
                    this.history.add(v);
                }
            }
            recomputeBounds();
        }

        public void recomputeBounds() {
            double min = currentPrice;
            double max = currentPrice;
            for (int i = 0; i < history.size(); i++) {
                double p = history.get(i);
                if (p > 0) {
                    if (p < min || min <= 0) min = p;
                    if (p > max) max = p;
                }
            }
            if (previousPrice > 0) {
                if (previousPrice < min) min = previousPrice;
                if (previousPrice > max) max = previousPrice;
            }
            low24h = min;
            high24h = max;
        }

        public boolean hasDistinctSnapshot() {
            return previousPrice > 0 && currentPrice > 0 && Math.abs(previousPrice - currentPrice) / currentPrice > 0.0002d;
        }

        public double getComparisonBasePrice() {
            if (hasDistinctSnapshot()) {
                return previousPrice;
            }
            double denom = 1.0d + (change24h / 100.0d);
            if (denom > 0.05d && Math.abs(change24h) > 0.01d) {
                return currentPrice / denom;
            }
            return previousPrice > 0 ? previousPrice : currentPrice;
        }

        public double getComparisonDeltaPercent() {
            double base = getComparisonBasePrice();
            if (base <= 0) {
                return change24h;
            }
            return ((currentPrice - base) / base) * 100.0d;
        }
    }

    private static final Object LOCK = new Object();
    private static final ArrayList<CurrencyAsset> ASSETS = new ArrayList<>();
    private static volatile boolean initialized = false;
    private static volatile boolean fetching = false;
    private static volatile long lastUpdatedMs = 0L;
    private static volatile double usdToEgpRate = 49.65d;
    private static volatile boolean lastSyncLive = false;

    private static void ensureInit() {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            ASSETS.clear();
            ASSETS.add(new CurrencyAsset(
                    "TON", "TON", "تونكوين", "Toncoin", "TON Network",
                    CATEGORY_TELEGRAM, 0xFF0098EA, 0xFF0077CC, "TON",
                    "the-open-network", "TONUSDT", false,
                    1.80d, 1.90d, -5.26d,
                    new float[]{1.92f, 1.90f, 1.88f, 1.91f, 1.87f, 1.84f, 1.86f, 1.82f, 1.79f, 1.81f, 1.80f}
            ));
            ASSETS.add(new CurrencyAsset(
                    "GRAM", "GRAM", "جرام تيليجرام", "Gram Coin", "TON Jetton",
                    CATEGORY_TELEGRAM, 0xFF2AABEE, 0xFF1D89C8, "GRAM",
                    "gram", null, false,
                    0.00482d, 0.00445d, 8.31d,
                    new float[]{0.0042f, 0.0043f, 0.0044f, 0.00445f, 0.0045f, 0.0046f, 0.00455f, 0.0047f, 0.00478f, 0.00482f}
            ));
            ASSETS.add(new CurrencyAsset(
                    "NOT", "NOT", "نوت كوين", "Notcoin", "Telegram Web3",
                    CATEGORY_TELEGRAM, 0xFF1E293B, 0xFF334155, "NOT",
                    "notcoin", "NOTUSDT", false,
                    0.00642d, 0.00610d, 5.25d,
                    new float[]{0.0059f, 0.0060f, 0.0061f, 0.00605f, 0.00618f, 0.00625f, 0.00630f, 0.00636f, 0.00642f}
            ));
            ASSETS.add(new CurrencyAsset(
                    "DOGS", "DOGS", "دوجز", "DOGS", "Telegram Meme",
                    CATEGORY_TELEGRAM, 0xFF2F80ED, 0xFF1A5FB4, "DOGS",
                    "dogs-2", "DOGSUSDT", false,
                    0.00054d, 0.00051d, 5.88d,
                    new float[]{0.00049f, 0.00050f, 0.00051f, 0.00050f, 0.00052f, 0.00053f, 0.000525f, 0.000535f, 0.00054f}
            ));
            ASSETS.add(new CurrencyAsset(
                    "USDT_BEP20", "USDT", "تيثر (دولار رقمي)", "Tether USD", "BEP-20 • BSC",
                    CATEGORY_BEP20, 0xFF26A17B, 0xFF1B7B5D, "USDT",
                    "tether", null, false,
                    1.000d, 0.999d, 0.10d,
                    new float[]{0.999f, 1.000f, 0.999f, 1.000f, 1.001f, 1.000f, 0.999f, 1.000f, 1.000f}
            ));
            ASSETS.add(new CurrencyAsset(
                    "BNB_BEP20", "BNB", "بي إن بي (BEP-20)", "BNB Smart Chain", "BEP-20 Native",
                    CATEGORY_BEP20, 0xFFF3BA2F, 0xFFD0980B, "BNB",
                    "binancecoin", "BNBUSDT", false,
                    612.40d, 598.10d, 2.39d,
                    new float[]{594f, 596f, 598f, 601f, 604f, 602f, 607f, 610f, 612.4f}
            ));
            ASSETS.add(new CurrencyAsset(
                    "USD", "USD", "الدولار الأمريكي", "US Dollar / EGP", "Fiat • مقابل الجنيه",
                    CATEGORY_MARKET, 0xFF2E9B56, 0xFF1F7A40, "$",
                    null, null, true,
                    49.65d, 49.40d, 0.51d,
                    new float[]{49.25f, 49.30f, 49.35f, 49.40f, 49.45f, 49.50f, 49.55f, 49.60f, 49.65f}
            ));
            ASSETS.add(new CurrencyAsset(
                    "BTC", "BTC", "بيتكوين", "Bitcoin", "Bitcoin Mainnet",
                    CATEGORY_MARKET, 0xFFF7931A, 0xFFD97706, "₿",
                    "bitcoin", "BTCUSDT", false,
                    89450.0d, 87200.0d, 2.58d,
                    new float[]{86800f, 87100f, 87200f, 87800f, 88100f, 88500f, 88900f, 89200f, 89450f}
            ));
            ASSETS.add(new CurrencyAsset(
                    "ETH", "ETH", "إيثيريوم", "Ethereum", "ERC-20 / BEP-20",
                    CATEGORY_MARKET, 0xFF627EEA, 0xFF475EC4, "Ξ",
                    "ethereum", "ETHUSDT", false,
                    3120.50d, 3195.00d, -2.33d,
                    new float[]{3210f, 3200f, 3195f, 3180f, 3165f, 3150f, 3140f, 3130f, 3120.5f}
            ));
            ASSETS.add(new CurrencyAsset(
                    "SOL", "SOL", "سولانا", "Solana", "Solana SPL",
                    CATEGORY_MARKET, 0xFF8C45FF, 0xFF14F195, "SOL",
                    "solana", "SOLUSDT", false,
                    188.40d, 181.20d, 3.97d,
                    new float[]{179f, 180f, 181.2f, 183f, 184.5f, 186f, 187f, 188.4f}
            ));
            ASSETS.add(new CurrencyAsset(
                    "XAU", "XAU", "الذهب العالمي", "Gold (Ounce)", "Spot XAU / PAXG",
                    CATEGORY_MARKET, 0xFFD4AF37, 0xFFA67C00, "Au",
                    "pax-gold", "PAXGUSDT", false,
                    2684.00d, 2658.00d, 0.98d,
                    new float[]{2650f, 2655f, 2658f, 2663f, 2669f, 2674f, 2679f, 2684f}
            ));

            loadCacheLocked();
            initialized = true;
        }
    }

    private static SharedPreferences getPrefs() {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return ApplicationLoader.applicationContext.getSharedPreferences("mido_currency_cache", 0);
    }

    private static void loadCacheLocked() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return;
        }
        lastUpdatedMs = prefs.getLong("last_updated_ms", 0L);
        usdToEgpRate = Double.longBitsToDouble(prefs.getLong("usd_egp", Double.doubleToRawLongBits(49.65d)));
        if (usdToEgpRate <= 1.0d) {
            usdToEgpRate = 49.65d;
        }
        for (int i = 0; i < ASSETS.size(); i++) {
            CurrencyAsset a = ASSETS.get(i);
            if (prefs.contains("cur_" + a.id)) {
                double cur = Double.longBitsToDouble(prefs.getLong("cur_" + a.id, Double.doubleToRawLongBits(a.currentPrice)));
                double prev = Double.longBitsToDouble(prefs.getLong("prev_" + a.id, Double.doubleToRawLongBits(a.previousPrice)));
                double chg = Double.longBitsToDouble(prefs.getLong("chg_" + a.id, Double.doubleToRawLongBits(a.change24h)));
                if (cur > 0) a.currentPrice = cur;
                if (prev > 0) a.previousPrice = prev;
                a.change24h = chg;
            }
            String histStr = prefs.getString("hist_" + a.id, null);
            if (!TextUtils.isEmpty(histStr)) {
                String[] parts = histStr.split(",");
                ArrayList<Float> parsed = new ArrayList<>();
                for (String part : parts) {
                    try {
                        float val = Float.parseFloat(part.trim());
                        if (val > 0) parsed.add(val);
                    } catch (Throwable ignore) {
                    }
                }
                if (parsed.size() >= 3) {
                    a.history.clear();
                    a.history.addAll(parsed);
                }
            }
            a.recomputeBounds();
        }
    }

    private static void saveCacheLocked() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return;
        }
        SharedPreferences.Editor ed = prefs.edit();
        ed.putLong("last_updated_ms", lastUpdatedMs);
        ed.putLong("usd_egp", Double.doubleToRawLongBits(usdToEgpRate));
        for (int i = 0; i < ASSETS.size(); i++) {
            CurrencyAsset a = ASSETS.get(i);
            ed.putLong("cur_" + a.id, Double.doubleToRawLongBits(a.currentPrice));
            ed.putLong("prev_" + a.id, Double.doubleToRawLongBits(a.previousPrice));
            ed.putLong("chg_" + a.id, Double.doubleToRawLongBits(a.change24h));
            StringBuilder sb = new StringBuilder();
            for (int h = 0; h < a.history.size(); h++) {
                if (h > 0) sb.append(',');
                sb.append(a.history.get(h));
            }
            ed.putString("hist_" + a.id, sb.toString());
        }
        ed.apply();
    }

    public static ArrayList<CurrencyAsset> getAssets() {
        ensureInit();
        synchronized (LOCK) {
            return new ArrayList<>(ASSETS);
        }
    }

    public static double getUsdToEgpRate() {
        ensureInit();
        return usdToEgpRate;
    }

    public static long getLastUpdatedMs() {
        return lastUpdatedMs;
    }

    public static boolean isLastSyncLive() {
        return lastSyncLive;
    }

    public static boolean isFetching() {
        return fetching;
    }

    public static void refreshPricesIfNeeded(Runnable onUiComplete) {
        ensureInit();
        long now = System.currentTimeMillis();
        if (now - lastUpdatedMs < 45_000L && lastSyncLive) {
            if (onUiComplete != null) {
                AndroidUtilities.runOnUIThread(onUiComplete);
            }
            return;
        }
        refreshPrices(onUiComplete);
    }

    public static void refreshPrices(Runnable onUiComplete) {
        ensureInit();
        if (fetching) {
            return;
        }
        fetching = true;
        Utilities.globalQueue.postRunnable(() -> {
            boolean anyLiveSuccess = false;
            try {
                // 1. CoinGecko batch request (TON, GRAM, NOT, DOGS, USDT, BNB, BTC, ETH, SOL, PAXG)
                boolean cgOk = fetchFromCoinGecko();
                if (cgOk) {
                    anyLiveSuccess = true;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }

            try {
                // 2. Binance fallback / confirmation for top pairs + 24h change
                if (!anyLiveSuccess) {
                    boolean bnOk = fetchFromBinance();
                    if (bnOk) {
                        anyLiveSuccess = true;
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }

            try {
                // 3. TONAPI for GRAM & TON (fast dedicated Telegram ecosystem backup)
                fetchGramAndTonFromTonApi();
            } catch (Throwable ignore) {
            }

            try {
                // 4. Live USD -> EGP rate
                boolean fxOk = fetchUsdToEgp();
                if (fxOk) {
                    anyLiveSuccess = true;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }

            synchronized (LOCK) {
                if (anyLiveSuccess) {
                    lastUpdatedMs = System.currentTimeMillis();
                    lastSyncLive = true;
                    saveCacheLocked();
                }
            }
            fetching = false;
            if (onUiComplete != null) {
                AndroidUtilities.runOnUIThread(onUiComplete);
            }
        });
    }

    private static boolean fetchFromCoinGecko() throws Exception {
        String urlStr = "https://api.coingecko.com/api/v3/simple/price"
                + "?ids=the-open-network,gram,notcoin,dogs-2,tether,binancecoin,bitcoin,ethereum,solana,pax-gold"
                + "&vs_currencies=usd&include_24hr_change=true";
        String body = httpGet(urlStr);
        if (TextUtils.isEmpty(body)) {
            return false;
        }
        JSONObject root = new JSONObject(body);
        boolean updated = false;
        synchronized (LOCK) {
            for (int i = 0; i < ASSETS.size(); i++) {
                CurrencyAsset asset = ASSETS.get(i);
                if (TextUtils.isEmpty(asset.coinGeckoId) || !root.has(asset.coinGeckoId)) {
                    continue;
                }
                JSONObject obj = root.optJSONObject(asset.coinGeckoId);
                if (obj == null || !obj.has("usd")) {
                    continue;
                }
                double price = obj.optDouble("usd", 0);
                double change = obj.optDouble("usd_24h_change", asset.change24h);
                if (price > 0) {
                    applyPriceUpdateLocked(asset, price, change);
                    updated = true;
                }
            }
        }
        return updated;
    }

    private static boolean fetchFromBinance() throws Exception {
        String urlStr = "https://api.binance.com/api/v3/ticker/24hr?symbols="
                + "%5B%22TONUSDT%22,%22NOTUSDT%22,%22DOGSUSDT%22,%22BNBUSDT%22,%22BTCUSDT%22,%22ETHUSDT%22,%22SOLUSDT%22,%22PAXGUSDT%22%5D";
        String body = httpGet(urlStr);
        if (TextUtils.isEmpty(body)) {
            return false;
        }
        JSONArray arr = new JSONArray(body);
        boolean updated = false;
        synchronized (LOCK) {
            for (int j = 0; j < arr.length(); j++) {
                JSONObject item = arr.optJSONObject(j);
                if (item == null) continue;
                String sym = item.optString("symbol");
                double last = parseDoubleSafe(item.optString("lastPrice"));
                double pct = parseDoubleSafe(item.optString("priceChangePercent"));
                if (last <= 0) continue;
                for (int i = 0; i < ASSETS.size(); i++) {
                    CurrencyAsset a = ASSETS.get(i);
                    if (sym.equals(a.binanceSymbol)) {
                        applyPriceUpdateLocked(a, last, pct);
                        updated = true;
                        break;
                    }
                }
            }
        }
        return updated;
    }

    private static void fetchGramAndTonFromTonApi() throws Exception {
        String urlStr = "https://tonapi.io/v2/rates?tokens=ton,gram&currencies=usd";
        String body = httpGet(urlStr);
        if (TextUtils.isEmpty(body)) {
            return;
        }
        JSONObject root = new JSONObject(body);
        JSONObject rates = root.optJSONObject("rates");
        if (rates == null) return;
        synchronized (LOCK) {
            JSONObject gramObj = rates.optJSONObject("GRAM");
            if (gramObj != null) {
                JSONObject prices = gramObj.optJSONObject("prices");
                if (prices != null && prices.has("USD")) {
                    double p = prices.optDouble("USD", 0);
                    if (p > 0) {
                        for (int i = 0; i < ASSETS.size(); i++) {
                            if ("GRAM".equals(ASSETS.get(i).id)) {
                                applyPriceUpdateLocked(ASSETS.get(i), p, ASSETS.get(i).change24h);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean fetchUsdToEgp() throws Exception {
        String body = httpGet("https://open.er-api.com/v6/latest/USD");
        if (TextUtils.isEmpty(body)) {
            return false;
        }
        JSONObject root = new JSONObject(body);
        JSONObject rates = root.optJSONObject("rates");
        if (rates == null || !rates.has("EGP")) {
            return false;
        }
        double egp = rates.optDouble("EGP", 0);
        if (egp <= 1.0d) {
            return false;
        }
        synchronized (LOCK) {
            usdToEgpRate = egp;
            for (int i = 0; i < ASSETS.size(); i++) {
                CurrencyAsset a = ASSETS.get(i);
                if ("USD".equals(a.id)) {
                    double prev = a.currentPrice > 0 ? a.currentPrice : egp;
                    double pct = prev > 0 ? ((egp - prev) / prev) * 100.0d : 0d;
                    applyPriceUpdateLocked(a, egp, Math.abs(pct) > 0.001d ? pct : a.change24h);
                    break;
                }
            }
        }
        return true;
    }

    private static void applyPriceUpdateLocked(CurrencyAsset asset, double newPrice, double change24h) {
        if (newPrice <= 0) {
            return;
        }
        if (asset.currentPrice > 0 && Math.abs(newPrice - asset.currentPrice) / asset.currentPrice > 0.0002d) {
            asset.previousPrice = asset.currentPrice;
        }
        asset.currentPrice = newPrice;
        asset.change24h = change24h;
        if (asset.history.isEmpty() || Math.abs(asset.history.get(asset.history.size() - 1) - (float) newPrice) / Math.max(1e-6f, (float) newPrice) > 0.0005f) {
            asset.history.add((float) newPrice);
            while (asset.history.size() > 24) {
                asset.history.remove(0);
            }
        }
        asset.recomputeBounds();
    }

    private static double parseDoubleSafe(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Throwable ignore) {
            return 0d;
        }
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(4500);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "MidoTelegram/1.0");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static String formatPrice(double value, boolean isEgp) {
        String formatted;
        if (value >= 1000d) {
            formatted = String.format(Locale.US, "%,.0f", value);
        } else if (value >= 10d) {
            formatted = String.format(Locale.US, "%.2f", value);
        } else if (value >= 0.5d) {
            formatted = String.format(Locale.US, "%.3f", value);
        } else if (value >= 0.01d) {
            formatted = String.format(Locale.US, "%.4f", value);
        } else if (value >= 0.0005d) {
            formatted = String.format(Locale.US, "%.5f", value);
        } else {
            formatted = String.format(Locale.US, "%.6f", value);
        }
        // Trim trailing excess decimals for clean look when >= 0.5
        if (value >= 0.5d && value < 10d && formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return isEgp ? (formatted + " ج.م") : ("$" + formatted);
    }

    public static String formatPercent(double pct) {
        String sign = pct >= 0 ? "+" : "";
        return String.format(Locale.US, "%s%.2f%%", sign, pct);
    }
}
