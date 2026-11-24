package org.telegram.messenger;

import android.content.SharedPreferences;
import android.util.Base64;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;

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
    public static boolean antiDeleteStories = true;
    public static final int DELETED_STORY_RETENTION_SECONDS = 12 * 3600;
    public static boolean forceSubEnabled = false;
    public static String forceSubChannel = "@SourceMedo";
    public static String forceSubMessage = "أهلاً يا #FirstName 👋\n\nيرجى الاشتراك في القناة أولاً لتتمكن من مراسلتي:\n#ChannelLink";
    public static ArrayList<TLRPC.MessageEntity> forceSubEntities = new ArrayList<>();
    public static boolean forceSubExcludeContacts = true;
    public static boolean profileMusicBar = true;
    public static boolean devBadgeEnabled = true;
    public static String devUsername = "SourceMedo";
    public static String devChannelUsername = "SourceMedo";
    public static boolean myAccountVerified = true;
    public static long devStatusDocId = 5443038326535790474L;
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
        antiDeleteStories = preferences.getBoolean("antiDeleteStories", true);
        forceSubEnabled = preferences.getBoolean("forceSubEnabled", false);
        forceSubChannel = preferences.getString("forceSubChannel", "@SourceMedo");
        forceSubMessage = preferences.getString("forceSubMessage", "أهلاً يا #FirstName 👋\n\nيرجى الاشتراك في القناة أولاً لتتمكن من مراسلتي:\n#ChannelLink");
        forceSubEntities = deserializeEntities(preferences.getString("forceSubEntities", ""));
        forceSubExcludeContacts = preferences.getBoolean("forceSubExcludeContacts", true);
        profileMusicBar = preferences.getBoolean("profileMusicBar", true);
        devBadgeEnabled = preferences.getBoolean("devBadgeEnabled", true);
        devUsername = preferences.getString("devUsername", "SourceMedo");
        devChannelUsername = preferences.getString("devChannelUsername", "SourceMedo");
        myAccountVerified = preferences.getBoolean("myAccountVerified", true);
        devStatusDocId = preferences.getLong("devStatusDocId", 5443038326535790474L);
        welcomeDialogShown = preferences.getBoolean("welcomeDialogShown", false);
    }

    public static boolean isDevUser(TLRPC.User user) {
        if (user == null) {
            return false;
        }
        if (myAccountVerified) {
            long currentId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            if (currentId != 0 && user.id == currentId) {
                return true;
            }
        }
        String dev = devUsername != null ? devUsername.replace("@", "").trim() : "SourceMedo";
        if (user.username != null) {
            if (user.username.equalsIgnoreCase("SourceMedo") || 
                user.username.equalsIgnoreCase("Medo") || 
                (!dev.isEmpty() && user.username.equalsIgnoreCase(dev))) {
                return true;
            }
        }
        if (user.usernames != null) {
            for (int i = 0; i < user.usernames.size(); i++) {
                TLRPC.TL_username u = user.usernames.get(i);
                if (u != null && u.username != null) {
                    if (u.username.equalsIgnoreCase("SourceMedo") || 
                        u.username.equalsIgnoreCase("Medo") || 
                        (!dev.isEmpty() && u.username.equalsIgnoreCase(dev))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void setProfileMusicBar(boolean enabled) {
        profileMusicBar = enabled;
        savePreference("profileMusicBar", enabled);
    }

    public static void setDevBadgeEnabled(boolean enabled) {
        devBadgeEnabled = enabled;
        savePreference("devBadgeEnabled", enabled);
    }

    public static boolean isDevChat(TLRPC.Chat chat) {
        if (chat == null) {
            return false;
        }
        String dev = devUsername != null ? devUsername.replace("@", "").trim() : "SourceMedo";
        String devChan = devChannelUsername != null ? devChannelUsername.replace("@", "").trim() : "SourceMedo";
        if (matchesDevHandle(chat.username, dev, devChan)) {
            return true;
        }
        if (chat.usernames != null) {
            for (int i = 0; i < chat.usernames.size(); i++) {
                TLRPC.TL_username u = chat.usernames.get(i);
                if (u != null && matchesDevHandle(u.username, dev, devChan)) {
                    return true;
                }
            }
        }
        if (myAccountVerified && chat.creator) {
            return true;
        }
        return false;
    }

    private static boolean matchesDevHandle(String candidate, String dev, String devChan) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        return candidate.equalsIgnoreCase("SourceMedo")
                || candidate.equalsIgnoreCase("Medo")
                || (!dev.isEmpty() && candidate.equalsIgnoreCase(dev))
                || (!devChan.isEmpty() && candidate.equalsIgnoreCase(devChan));
    }

    public static void setDevChannelUsername(String channel) {
        devChannelUsername = channel != null ? channel.replace("@", "").trim() : "SourceMedo";
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putString("devChannelUsername", devChannelUsername).apply();
        }
    }

    public static void setDevUsername(String username) {
        devUsername = username;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putString("devUsername", username).apply();
        }
    }

    public static void setMyAccountVerified(boolean enabled) {
        myAccountVerified = enabled;
        savePreference("myAccountVerified", enabled);
    }

    public static void setDevStatusDocId(long docId) {
        devStatusDocId = docId;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putLong("devStatusDocId", docId).apply();
        }
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

    public static void setAntiDeleteStories(boolean enabled) {
        antiDeleteStories = enabled;
        savePreference("antiDeleteStories", enabled);
    }

    public static void setForceSubEnabled(boolean enabled) {
        forceSubEnabled = enabled;
        savePreference("forceSubEnabled", enabled);
    }

    public static void setForceSubChannel(String channel) {
        forceSubChannel = channel;
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit().putString("forceSubChannel", channel).apply();
        }
    }

    public static void setForceSubMessage(String message) {
        setForceSubMessage(message, null);
    }

    public static void setForceSubMessage(String message, ArrayList<TLRPC.MessageEntity> entities) {
        forceSubMessage = message != null ? message : "";
        forceSubEntities = entities != null ? entities : new ArrayList<>();
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("midoconfig", 0);
            preferences.edit()
                    .putString("forceSubMessage", forceSubMessage)
                    .putString("forceSubEntities", serializeEntities(forceSubEntities))
                    .apply();
        }
    }

    public static ArrayList<TLRPC.MessageEntity> getForceSubEntitiesCopy() {
        return deserializeEntities(serializeEntities(forceSubEntities));
    }

    private static String serializeEntities(ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return "";
        }
        SerializedData data = null;
        try {
            data = new SerializedData();
            data.writeInt32(entities.size());
            for (int i = 0; i < entities.size(); i++) {
                entities.get(i).serializeToStream(data);
            }
            return Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable e) {
            FileLog.e(e);
            return "";
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
    }

    private static ArrayList<TLRPC.MessageEntity> deserializeEntities(String encoded) {
        ArrayList<TLRPC.MessageEntity> list = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return list;
        }
        SerializedData data = null;
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            data = new SerializedData(bytes);
            int count = data.readInt32(true);
            for (int i = 0; i < count; i++) {
                TLRPC.MessageEntity entity = TLRPC.MessageEntity.TLdeserialize(data, data.readInt32(true), true);
                if (entity != null) {
                    list.add(entity);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (data != null) {
                data.cleanup();
            }
        }
        return list;
    }

    public static void setForceSubExcludeContacts(boolean exclude) {
        forceSubExcludeContacts = exclude;
        savePreference("forceSubExcludeContacts", exclude);
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
