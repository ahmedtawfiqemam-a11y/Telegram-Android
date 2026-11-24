package org.telegram.messenger;

import android.content.SharedPreferences;

public class MidoConfig {
    public static boolean antiDelete = true;
    public static boolean unlimitedAccounts = true;
    public static boolean stealthStories = true;
    public static boolean hideReadReceipts = true;
    public static boolean freezeLastSeen = true;
    public static boolean hideOnlineStatus = true;
    public static boolean hideTyping = true;
    public static boolean ghostCall = true;
    public static boolean saveRestrictedContent = true;
    public static boolean allowScreenshots = true;
    public static boolean saveExpiringMedia = true;
    public static boolean forwardWithoutQuote = true;
    public static boolean showEditHistory = true;
    public static boolean confirmCall = true;
    public static boolean keepKickedChats = true;
    public static boolean antiExpireStories = true;
    public static boolean welcomeDialogShown = false;

    public static void loadConfig() {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
        antiDelete = preferences.getBoolean("antiDelete", true);
        unlimitedAccounts = preferences.getBoolean("unlimitedAccounts", true);
        stealthStories = preferences.getBoolean("stealthStories", true);
        hideReadReceipts = preferences.getBoolean("hideReadReceipts", true);
        freezeLastSeen = preferences.getBoolean("freezeLastSeen", true);
        hideOnlineStatus = preferences.getBoolean("hideOnlineStatus", true);
        hideTyping = preferences.getBoolean("hideTyping", true);
        ghostCall = preferences.getBoolean("ghostCall", true);
        saveRestrictedContent = preferences.getBoolean("saveRestrictedContent", true);
        allowScreenshots = preferences.getBoolean("allowScreenshots", true);
        saveExpiringMedia = preferences.getBoolean("saveExpiringMedia", true);
        forwardWithoutQuote = preferences.getBoolean("forwardWithoutQuote", true);
        showEditHistory = preferences.getBoolean("showEditHistory", true);
        confirmCall = preferences.getBoolean("confirmCall", true);
        keepKickedChats = preferences.getBoolean("keepKickedChats", true);
        antiExpireStories = preferences.getBoolean("antiExpireStories", true);
        welcomeDialogShown = preferences.getBoolean("welcomeDialogShown", false);
    }

    public static void setAntiDelete(boolean enabled) {
        antiDelete = enabled;
        savePreference("antiDelete", enabled);
    }

    public static void setUnlimitedAccounts(boolean enabled) {
        unlimitedAccounts = enabled;
        savePreference("unlimitedAccounts", enabled);
    }

    public static void setStealthStories(boolean enabled) {
        stealthStories = enabled;
        savePreference("stealthStories", enabled);
    }

    public static void setHideReadReceipts(boolean enabled) {
        hideReadReceipts = enabled;
        savePreference("hideReadReceipts", enabled);
    }

    public static void setFreezeLastSeen(boolean enabled) {
        freezeLastSeen = enabled;
        savePreference("freezeLastSeen", enabled);
    }

    public static void setHideOnlineStatus(boolean enabled) {
        hideOnlineStatus = enabled;
        savePreference("hideOnlineStatus", enabled);
    }

    public static void setHideTyping(boolean enabled) {
        hideTyping = enabled;
        savePreference("hideTyping", enabled);
    }

    public static void setGhostCall(boolean enabled) {
        ghostCall = enabled;
        savePreference("ghostCall", enabled);
    }

    public static void setSaveRestrictedContent(boolean enabled) {
        saveRestrictedContent = enabled;
        savePreference("saveRestrictedContent", enabled);
    }

    public static void setAllowScreenshots(boolean enabled) {
        allowScreenshots = enabled;
        savePreference("allowScreenshots", enabled);
    }

    public static void setSaveExpiringMedia(boolean enabled) {
        saveExpiringMedia = enabled;
        savePreference("saveExpiringMedia", enabled);
    }

    public static void setForwardWithoutQuote(boolean enabled) {
        forwardWithoutQuote = enabled;
        savePreference("forwardWithoutQuote", enabled);
    }

    public static void setShowEditHistory(boolean enabled) {
        showEditHistory = enabled;
        savePreference("showEditHistory", enabled);
    }

    public static void setConfirmCall(boolean enabled) {
        confirmCall = enabled;
        savePreference("confirmCall", enabled);
    }

    public static void setKeepKickedChats(boolean enabled) {
        keepKickedChats = enabled;
        savePreference("keepKickedChats", enabled);
    }

    public static void setAntiExpireStories(boolean enabled) {
        antiExpireStories = enabled;
        savePreference("antiExpireStories", enabled);
    }

    public static void setWelcomeDialogShown(boolean shown) {
        welcomeDialogShown = shown;
        savePreference("welcomeDialogShown", shown);
    }

    private static void savePreference(String key, boolean val) {
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putBoolean(key, val).apply();
        }
    }
}
