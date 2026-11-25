package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

import java.util.HashSet;

/**
 * Anti-Oops Guard: requires confirmation before initiating calls, sending stickers/GIFs,
 * or adding reactions inside user-selected sensitive chats (e.g. work groups) or all groups.
 */
public class MidoAntiOopsGuard {

    private static final Object LOCK = new Object();
    private static final HashSet<Long> GUARDED_IDS = new HashSet<>();
    private static volatile boolean loaded = false;

    private static volatile boolean guardCalls = true;
    private static volatile boolean guardStickers = true;
    private static volatile boolean guardReactions = true;
    private static volatile boolean guardAllGroups = false;

    private static SharedPreferences getPrefs() {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return ApplicationLoader.applicationContext.getSharedPreferences("mido_anti_oops", 0);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            GUARDED_IDS.clear();
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                guardCalls = prefs.getBoolean("guard_calls", true);
                guardStickers = prefs.getBoolean("guard_stickers", true);
                guardReactions = prefs.getBoolean("guard_reactions", true);
                guardAllGroups = prefs.getBoolean("guard_all_groups", false);
                String raw = prefs.getString("guarded_ids", "");
                if (!TextUtils.isEmpty(raw)) {
                    for (String part : raw.split(",")) {
                        try {
                            long did = Long.parseLong(part.trim());
                            if (did != 0) GUARDED_IDS.add(did);
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }
            loaded = true;
        }
    }

    private static void saveIdsLocked() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return;
        StringBuilder sb = new StringBuilder();
        for (Long id : GUARDED_IDS) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        prefs.edit().putString("guarded_ids", sb.toString()).apply();
    }

    public static boolean isGuardCalls() {
        ensureLoaded();
        return guardCalls;
    }

    public static void setGuardCalls(boolean enabled) {
        ensureLoaded();
        guardCalls = enabled;
        SharedPreferences p = getPrefs();
        if (p != null) p.edit().putBoolean("guard_calls", enabled).apply();
    }

    public static boolean isGuardStickers() {
        ensureLoaded();
        return guardStickers;
    }

    public static void setGuardStickers(boolean enabled) {
        ensureLoaded();
        guardStickers = enabled;
        SharedPreferences p = getPrefs();
        if (p != null) p.edit().putBoolean("guard_stickers", enabled).apply();
    }

    public static boolean isGuardReactions() {
        ensureLoaded();
        return guardReactions;
    }

    public static void setGuardReactions(boolean enabled) {
        ensureLoaded();
        guardReactions = enabled;
        SharedPreferences p = getPrefs();
        if (p != null) p.edit().putBoolean("guard_reactions", enabled).apply();
    }

    public static boolean isGuardAllGroups() {
        ensureLoaded();
        return guardAllGroups;
    }

    public static void setGuardAllGroups(boolean enabled) {
        ensureLoaded();
        guardAllGroups = enabled;
        SharedPreferences p = getPrefs();
        if (p != null) p.edit().putBoolean("guard_all_groups", enabled).apply();
    }

    public static int getGuardedCount() {
        ensureLoaded();
        synchronized (LOCK) {
            return GUARDED_IDS.size();
        }
    }

    public static boolean isExplicitlyGuarded(long dialogId) {
        if (dialogId == 0) return false;
        ensureLoaded();
        synchronized (LOCK) {
            return GUARDED_IDS.contains(dialogId);
        }
    }

    public static boolean isDialogGuarded(long dialogId) {
        return isDialogGuarded(UserConfig.selectedAccount, dialogId);
    }

    public static boolean isDialogGuarded(int account, long dialogId) {
        if (dialogId == 0) return false;
        ensureLoaded();
        synchronized (LOCK) {
            if (GUARDED_IDS.contains(dialogId)) {
                return true;
            }
        }
        if (guardAllGroups && dialogId < 0) {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
            if (chat != null && (!ChatObject.isChannel(chat) || chat.megagroup)) {
                return true;
            }
        }
        return false;
    }

    public static boolean toggleDialogGuard(long dialogId) {
        if (dialogId == 0) return false;
        ensureLoaded();
        synchronized (LOCK) {
            boolean nowGuarded;
            if (GUARDED_IDS.contains(dialogId)) {
                GUARDED_IDS.remove(dialogId);
                nowGuarded = false;
            } else {
                GUARDED_IDS.add(dialogId);
                nowGuarded = true;
            }
            saveIdsLocked();
            return nowGuarded;
        }
    }

    public static boolean shouldGuardCalls(long dialogId) {
        return shouldGuardCalls(UserConfig.selectedAccount, dialogId);
    }

    public static boolean shouldGuardCalls(int account, long dialogId) {
        return isGuardCalls() && isDialogGuarded(account, dialogId);
    }

    public static boolean shouldGuardStickers(long dialogId) {
        return shouldGuardStickers(UserConfig.selectedAccount, dialogId);
    }

    public static boolean shouldGuardStickers(int account, long dialogId) {
        return isGuardStickers() && isDialogGuarded(account, dialogId);
    }

    public static boolean shouldGuardReactions(long dialogId) {
        return shouldGuardReactions(UserConfig.selectedAccount, dialogId);
    }

    public static boolean shouldGuardReactions(int account, long dialogId) {
        return isGuardReactions() && isDialogGuarded(account, dialogId);
    }

    public static void confirmAction(Context context, String actionTitle, String actionDesc, Runnable onConfirmed) {
        if (context == null) {
            if (onConfirmed != null) onConfirmed.run();
            return;
        }
        if (context instanceof android.app.Activity) {
            android.app.Activity act = (android.app.Activity) context;
            if (act.isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && act.isDestroyed())) {
                return;
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("🛡️ درع اللمس الخاطئ — " + actionTitle);
        builder.setMessage(actionDesc);
        builder.setPositiveButton("تأكيد وإرسال", (dialog, which) -> {
            if (onConfirmed != null) {
                onConfirmed.run();
            }
        });
        builder.setNegativeButton("إلغاء", null);
        try {
            builder.show();
        } catch (Throwable ignore) {
        }
    }
}
