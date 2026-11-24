package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import androidx.collection.LongSparseArray;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

/**
 * Automatic (no toggle required) persistence for:
 * 1. Unlimited pinned chats, channels, groups, and bots beyond Telegram's 5/10 server limit.
 * 2. Unlimited favorite stickers in the Telegram keyboard beyond the 5/10 server limit.
 */
public class MidoLocalPins {

    private static final Object LOCK = new Object();

    private static class AccountState {
        boolean loaded = false;
        final LinkedHashMap<Long, Integer> pinnedDialogs = new LinkedHashMap<>();
        final LinkedHashMap<Long, String> favedStickersB64 = new LinkedHashMap<>();
        final HashSet<Long> removedFavedIds = new HashSet<>();
    }

    private static final HashMap<Integer, AccountState> STATES = new HashMap<>();

    private static SharedPreferences getPrefs(int account) {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return ApplicationLoader.applicationContext.getSharedPreferences("mido_local_pins_" + account, 0);
    }

    private static AccountState getStateLocked(int account) {
        AccountState st = STATES.get(account);
        if (st == null) {
            st = new AccountState();
            STATES.put(account, st);
        }
        if (!st.loaded) {
            st.loaded = true;
            SharedPreferences prefs = getPrefs(account);
            if (prefs != null) {
                try {
                    String pinsRaw = prefs.getString("pinned_dialogs", "");
                    if (!TextUtils.isEmpty(pinsRaw)) {
                        JSONArray arr = new JSONArray(pinsRaw);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o == null) continue;
                            long did = o.optLong("did", 0);
                            int num = o.optInt("num", 1);
                            if (did != 0) {
                                st.pinnedDialogs.put(did, num);
                            }
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                try {
                    String favsRaw = prefs.getString("faved_stickers", "");
                    if (!TextUtils.isEmpty(favsRaw)) {
                        JSONArray arr = new JSONArray(favsRaw);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o == null) continue;
                            long id = o.optLong("id", 0);
                            String b64 = o.optString("b64", "");
                            if (id != 0 && !TextUtils.isEmpty(b64)) {
                                st.favedStickersB64.put(id, b64);
                            }
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                try {
                    String remRaw = prefs.getString("removed_faved_ids", "");
                    if (!TextUtils.isEmpty(remRaw)) {
                        for (String part : remRaw.split(",")) {
                            try {
                                long id = Long.parseLong(part.trim());
                                if (id != 0) st.removedFavedIds.add(id);
                            } catch (Throwable ignore) {
                            }
                        }
                    }
                } catch (Throwable ignore) {
                }
            }
        }
        return st;
    }

    private static void saveDialogsLocked(int account, AccountState st) {
        SharedPreferences prefs = getPrefs(account);
        if (prefs == null) return;
        JSONArray arr = new JSONArray();
        for (HashMap.Entry<Long, Integer> e : st.pinnedDialogs.entrySet()) {
            try {
                JSONObject o = new JSONObject();
                o.put("did", e.getKey());
                o.put("num", e.getValue());
                arr.put(o);
            } catch (Throwable ignore) {
            }
        }
        prefs.edit().putString("pinned_dialogs", arr.toString()).apply();
    }

    private static void saveFavedLocked(int account, AccountState st) {
        SharedPreferences prefs = getPrefs(account);
        if (prefs == null) return;
        JSONArray arr = new JSONArray();
        for (HashMap.Entry<Long, String> e : st.favedStickersB64.entrySet()) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", e.getKey());
                o.put("b64", e.getValue());
                arr.put(o);
            } catch (Throwable ignore) {
            }
        }
        StringBuilder remSb = new StringBuilder();
        for (Long id : st.removedFavedIds) {
            if (remSb.length() > 0) remSb.append(',');
            remSb.append(id);
        }
        prefs.edit()
                .putString("faved_stickers", arr.toString())
                .putString("removed_faved_ids", remSb.toString())
                .apply();
    }

    // ---- Unlimited Pinned Chats / Channels / Bots ----

    public static void setDialogPinned(int account, long dialogId, boolean pinned, int pinnedNum) {
        if (dialogId == 0) return;
        synchronized (LOCK) {
            AccountState st = getStateLocked(account);
            if (pinned) {
                st.pinnedDialogs.put(dialogId, Math.max(1, pinnedNum));
            } else {
                st.pinnedDialogs.remove(dialogId);
            }
            saveDialogsLocked(account, st);
        }
    }

    public static void applyLocalPinsToDialogs(int account, LongSparseArray<TLRPC.Dialog> dialogsDict) {
        if (dialogsDict == null || dialogsDict.size() == 0) return;
        synchronized (LOCK) {
            AccountState st = getStateLocked(account);
            if (st.pinnedDialogs.isEmpty()) return;
            for (HashMap.Entry<Long, Integer> e : st.pinnedDialogs.entrySet()) {
                TLRPC.Dialog d = dialogsDict.get(e.getKey());
                if (d != null && !d.pinned) {
                    d.pinned = true;
                    d.pinnedNum = Math.max(1, e.getValue());
                }
            }
        }
    }

    // ---- Unlimited Favorite Stickers ----

    public static void saveFavedSticker(int account, TLRPC.Document doc) {
        if (doc == null || doc.id == 0) return;
        try {
            SerializedData sd = new SerializedData();
            doc.serializeToStream(sd);
            String b64 = Base64.encodeToString(sd.toByteArray(), Base64.NO_WRAP);
            sd.cleanup();
            if (TextUtils.isEmpty(b64)) return;
            synchronized (LOCK) {
                AccountState st = getStateLocked(account);
                st.removedFavedIds.remove(doc.id);
                // Re-insert so most recently faved stays first
                LinkedHashMap<Long, String> reordered = new LinkedHashMap<>();
                reordered.put(doc.id, b64);
                for (HashMap.Entry<Long, String> e : st.favedStickersB64.entrySet()) {
                    if (e.getKey() != doc.id) {
                        reordered.put(e.getKey(), e.getValue());
                    }
                }
                st.favedStickersB64.clear();
                st.favedStickersB64.putAll(reordered);
                saveFavedLocked(account, st);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void removeFavedSticker(int account, long docId) {
        if (docId == 0) return;
        synchronized (LOCK) {
            AccountState st = getStateLocked(account);
            st.favedStickersB64.remove(docId);
            st.removedFavedIds.add(docId);
            saveFavedLocked(account, st);
        }
    }

    public static ArrayList<TLRPC.Document> mergeFavedStickers(int account, ArrayList<TLRPC.Document> serverList) {
        ArrayList<TLRPC.Document> merged = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        synchronized (LOCK) {
            AccountState st = getStateLocked(account);
            if (serverList != null) {
                for (int i = 0; i < serverList.size(); i++) {
                    TLRPC.Document d = serverList.get(i);
                    if (d != null && d.id != 0 && !st.removedFavedIds.contains(d.id) && seen.add(d.id)) {
                        merged.add(d);
                    }
                }
            }
            for (HashMap.Entry<Long, String> e : st.favedStickersB64.entrySet()) {
                long docId = e.getKey();
                if (seen.contains(docId) || st.removedFavedIds.contains(docId)) {
                    continue;
                }
                try {
                    byte[] bytes = Base64.decode(e.getValue(), Base64.NO_WRAP);
                    SerializedData sd = new SerializedData(bytes);
                    TLRPC.Document doc = TLRPC.Document.TLdeserialize(sd, sd.readInt32(false), false);
                    sd.cleanup();
                    if (doc != null && doc.id != 0 && seen.add(doc.id)) {
                        merged.add(doc);
                    }
                } catch (Throwable ignore) {
                }
            }
        }
        return merged;
    }
}
