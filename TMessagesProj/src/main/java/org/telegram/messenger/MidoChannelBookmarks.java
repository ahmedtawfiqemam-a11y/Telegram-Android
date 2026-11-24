package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * Stores channels that the user follows WITHOUT joining, together with
 * the last read post ID and latest known message ID so reading resumes seamlessly.
 */
public class MidoChannelBookmarks {

    public static class ChannelBookmark {
        public long channelId;
        public long accessHash;
        public String title;
        public String username;
        public int lastReadMsgId;
        public int latestKnownMsgId;
        public long updatedAtMs;
    }

    private static final Object LOCK = new Object();
    private static final LinkedHashMap<Long, ChannelBookmark> BOOKMARKS = new LinkedHashMap<>();
    private static volatile boolean loaded = false;

    private static SharedPreferences getPrefs() {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return ApplicationLoader.applicationContext.getSharedPreferences("mido_followed_channels", 0);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            BOOKMARKS.clear();
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                String raw = prefs.getString("bookmarks_json", "");
                if (!TextUtils.isEmpty(raw)) {
                    try {
                        JSONArray arr = new JSONArray(raw);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o == null) continue;
                            ChannelBookmark b = new ChannelBookmark();
                            b.channelId = o.optLong("id", 0);
                            if (b.channelId == 0) continue;
                            b.accessHash = o.optLong("ah", 0);
                            b.title = o.optString("title", "Channel");
                            b.username = o.optString("user", "");
                            b.lastReadMsgId = o.optInt("lastMsg", 0);
                            b.latestKnownMsgId = o.optInt("topMsg", 0);
                            b.updatedAtMs = o.optLong("ts", 0);
                            BOOKMARKS.put(b.channelId, b);
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            }
            loaded = true;
        }
    }

    private static void saveLocked() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) {
            return;
        }
        JSONArray arr = new JSONArray();
        for (ChannelBookmark b : BOOKMARKS.values()) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", b.channelId);
                o.put("ah", b.accessHash);
                o.put("title", b.title != null ? b.title : "");
                o.put("user", b.username != null ? b.username : "");
                o.put("lastMsg", b.lastReadMsgId);
                o.put("topMsg", b.latestKnownMsgId);
                o.put("ts", b.updatedAtMs);
                arr.put(o);
            } catch (Throwable ignore) {
            }
        }
        prefs.edit().putString("bookmarks_json", arr.toString()).apply();
    }

    public static boolean isFollowed(long channelId) {
        if (channelId == 0) return false;
        ensureLoaded();
        synchronized (LOCK) {
            return BOOKMARKS.containsKey(Math.abs(channelId));
        }
    }

    public static int getLastReadMsgId(long channelId) {
        if (channelId == 0) return 0;
        ensureLoaded();
        synchronized (LOCK) {
            ChannelBookmark b = BOOKMARKS.get(Math.abs(channelId));
            return b != null ? b.lastReadMsgId : 0;
        }
    }

    public static int getCount() {
        ensureLoaded();
        synchronized (LOCK) {
            return BOOKMARKS.size();
        }
    }

    public static boolean toggleFollow(TLRPC.Chat chat, int visibleMsgId, int latestMsgId) {
        if (chat == null) return false;
        long cid = Math.abs(chat.id);
        ensureLoaded();
        synchronized (LOCK) {
            if (BOOKMARKS.containsKey(cid)) {
                BOOKMARKS.remove(cid);
                saveLocked();
                return false;
            }
            ChannelBookmark b = new ChannelBookmark();
            b.channelId = cid;
            b.accessHash = chat.access_hash;
            b.title = !TextUtils.isEmpty(chat.title) ? chat.title : ("Channel #" + cid);
            b.username = ChatObject.getPublicUsername(chat);
            b.lastReadMsgId = visibleMsgId > 0 ? visibleMsgId : latestMsgId;
            b.latestKnownMsgId = Math.max(latestMsgId, b.lastReadMsgId);
            b.updatedAtMs = System.currentTimeMillis();
            BOOKMARKS.put(cid, b);
            saveLocked();
            return true;
        }
    }

    public static void updateProgress(TLRPC.Chat chat, int visibleMsgId, int latestMsgId) {
        if (chat == null) return;
        long cid = Math.abs(chat.id);
        ensureLoaded();
        synchronized (LOCK) {
            ChannelBookmark b = BOOKMARKS.get(cid);
            if (b == null) {
                return;
            }
            if (chat.access_hash != 0) b.accessHash = chat.access_hash;
            if (!TextUtils.isEmpty(chat.title)) b.title = chat.title;
            String user = ChatObject.getPublicUsername(chat);
            if (!TextUtils.isEmpty(user)) b.username = user;
            if (visibleMsgId > 0) {
                b.lastReadMsgId = visibleMsgId;
            }
            if (latestMsgId > b.latestKnownMsgId) {
                b.latestKnownMsgId = latestMsgId;
            }
            b.updatedAtMs = System.currentTimeMillis();
            saveLocked();
        }
    }

    public static void unfollow(long channelId) {
        ensureLoaded();
        synchronized (LOCK) {
            if (BOOKMARKS.remove(Math.abs(channelId)) != null) {
                saveLocked();
            }
        }
    }

    public static ArrayList<ChannelBookmark> getAllBookmarks() {
        ensureLoaded();
        synchronized (LOCK) {
            ArrayList<ChannelBookmark> list = new ArrayList<>(BOOKMARKS.values());
            Collections.sort(list, (a, b) -> Long.compare(b.updatedAtMs, a.updatedAtMs));
            return list;
        }
    }
}
