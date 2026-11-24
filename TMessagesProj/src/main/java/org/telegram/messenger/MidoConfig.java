package org.telegram.messenger;

import android.content.SharedPreferences;

public class MidoConfig {
    public static boolean antiDelete = true;
    public static boolean unlimitedAccounts = true;
    public static boolean welcomeDialogShown = false;

    public static void loadConfig() {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
        antiDelete = preferences.getBoolean("antiDelete", true);
        unlimitedAccounts = preferences.getBoolean("unlimitedAccounts", true);
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

    public static void setWelcomeDialogShown(boolean shown) {
        welcomeDialogShown = shown;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putBoolean("welcomeDialogShown", shown).apply();
        }
    }
}
