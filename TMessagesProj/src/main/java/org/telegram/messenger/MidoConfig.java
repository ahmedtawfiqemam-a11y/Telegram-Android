package org.telegram.messenger;

import android.content.SharedPreferences;

public class MidoConfig {
    public static boolean antiDelete = true;

    public static void loadConfig() {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
        antiDelete = preferences.getBoolean("antiDelete", true);
    }

    public static void setAntiDelete(boolean enabled) {
        antiDelete = enabled;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putBoolean("antiDelete", enabled).apply();
        }
    }
}
