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
        welcomeDialogShown = preferences.getBoolean("welcomeDialogShown", false);
    }

    public static void setAntiDelete(boolean enabled) {
        antiDelete = enabled;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putBoolean("antiDelete", enabled).apply();
        }
    }

    public static void setUnlimitedAccounts(boolean enabled) {
        unlimitedAccounts = enabled;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putBoolean("unlimitedAccounts", enabled).apply();
        }
    }

    public static void setStealthStories(boolean enabled) {
        stealthStories = enabled;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putBoolean("stealthStories", enabled).apply();
        }
    }

    public static void setHideReadReceipts(boolean enabled) {
        hideReadReceipts = enabled;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putBoolean("hideReadReceipts", enabled).apply();
        }
    }

    public static void setFreezeLastSeen(boolean enabled) {
        freezeLastSeen = enabled;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putBoolean("freezeLastSeen", enabled).apply();
        }
    }

    public static void setHideOnlineStatus(boolean enabled) {
        hideOnlineStatus = enabled;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putBoolean("hideOnlineStatus", enabled).apply();
        }
    }

    public static void setHideTyping(boolean enabled) {
        hideTyping = enabled;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putBoolean("hideTyping", enabled).apply();
        }
    }

    public static void setGhostCall(boolean enabled) {
        ghostCall = enabled;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putBoolean("ghostCall", enabled).apply();
        }
    }

    public static void setWelcomeDialogShown(boolean shown) {
        welcomeDialogShown = shown;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putBoolean("welcomeDialogShown", shown).apply();
        }
    }
}
