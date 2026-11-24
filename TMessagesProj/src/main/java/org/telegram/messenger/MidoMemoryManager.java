package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * Local storage manager for:
 * 1) Per-chat Memory Cards (private notes + dated appointments/reminders).
 * 2) Cross-chat Virtual Topic Folders gathering related messages from channels, groups, and private chats.
 */
public class MidoMemoryManager {

    public static class NoteEntry {
        public long id;
        public String text;
        public long createdAtMs;
    }

    public static class AppointmentEntry {
        public long id;
        public String title;
        public String whenText;
        public long timestampMs;
        public boolean done;
    }

    public static class MemoryCard {
        public long dialogId;
        public String title;
        public String username;
        public long updatedAtMs;
        public final ArrayList<NoteEntry> notes = new ArrayList<>();
        public final ArrayList<AppointmentEntry> appointments = new ArrayList<>();
    }

    public static class TopicMessage {
        public long dialogId;
        public long accessHash;
        public int messageId;
        public String chatTitle;
        public String chatUsername;
        public String senderName;
        public String previewText;
        public long savedAtMs;
    }

    public static class TopicFolder {
        public String id;
        public String name;
        public int color;
        public long createdAtMs;
        public final ArrayList<TopicMessage> messages = new ArrayList<>();
    }

    private static final Object LOCK = new Object();
    private static final LinkedHashMap<Long, MemoryCard> CARDS = new LinkedHashMap<>();
    private static final ArrayList<TopicFolder> FOLDERS = new ArrayList<>();
    private static volatile boolean loaded = false;

    public static final int[] FOLDER_COLORS = new int[]{
            0xFF2AABEE, 0xFF1FA75A, 0xFF8C45FF, 0xFFF59E0B, 0xFFE53935, 0xFFEC4899
    };

    private static SharedPreferences getPrefs() {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return ApplicationLoader.applicationContext.getSharedPreferences("mido_memory_and_topics", 0);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            CARDS.clear();
            FOLDERS.clear();
            SharedPreferences prefs = getPrefs();
            if (prefs != null) {
                loadCardsLocked(prefs.getString("cards_json", ""));
                loadFoldersLocked(prefs.getString("folders_json", ""));
            }
            if (FOLDERS.isEmpty()) {
                TopicFolder def1 = new TopicFolder();
                def1.id = "topic_work";
                def1.name = "العمل والمشاريع 💼";
                def1.color = FOLDER_COLORS[0];
                def1.createdAtMs = System.currentTimeMillis();
                FOLDERS.add(def1);

                TopicFolder def2 = new TopicFolder();
                def2.id = "topic_refs";
                def2.name = "مراجع ومقالات مهمة 📌";
                def2.color = FOLDER_COLORS[2];
                def2.createdAtMs = System.currentTimeMillis();
                FOLDERS.add(def2);
            }
            loaded = true;
        }
    }

    private static void loadCardsLocked(String raw) {
        if (TextUtils.isEmpty(raw)) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                MemoryCard card = new MemoryCard();
                card.dialogId = o.optLong("did", 0);
                if (card.dialogId == 0) continue;
                card.title = o.optString("title", "Chat");
                card.username = o.optString("user", "");
                card.updatedAtMs = o.optLong("ts", 0);

                JSONArray notesArr = o.optJSONArray("notes");
                if (notesArr != null) {
                    for (int j = 0; j < notesArr.length(); j++) {
                        JSONObject no = notesArr.optJSONObject(j);
                        if (no == null) continue;
                        NoteEntry n = new NoteEntry();
                        n.id = no.optLong("id", System.nanoTime() + j);
                        n.text = no.optString("text", "");
                        n.createdAtMs = no.optLong("ts", 0);
                        if (!TextUtils.isEmpty(n.text)) {
                            card.notes.add(n);
                        }
                    }
                }
                JSONArray apArr = o.optJSONArray("apps");
                if (apArr != null) {
                    for (int j = 0; j < apArr.length(); j++) {
                        JSONObject ao = apArr.optJSONObject(j);
                        if (ao == null) continue;
                        AppointmentEntry a = new AppointmentEntry();
                        a.id = ao.optLong("id", System.nanoTime() + j);
                        a.title = ao.optString("title", "");
                        a.whenText = ao.optString("when", "");
                        a.timestampMs = ao.optLong("ts", 0);
                        a.done = ao.optBoolean("done", false);
                        if (!TextUtils.isEmpty(a.title)) {
                            card.appointments.add(a);
                        }
                    }
                }
                CARDS.put(card.dialogId, card);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void loadFoldersLocked(String raw) {
        if (TextUtils.isEmpty(raw)) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                TopicFolder f = new TopicFolder();
                f.id = o.optString("id", "t_" + i);
                f.name = o.optString("name", "مجلد");
                f.color = o.optInt("color", FOLDER_COLORS[0]);
                f.createdAtMs = o.optLong("ts", 0);

                JSONArray msgs = o.optJSONArray("msgs");
                if (msgs != null) {
                    for (int j = 0; j < msgs.length(); j++) {
                        JSONObject mo = msgs.optJSONObject(j);
                        if (mo == null) continue;
                        TopicMessage m = new TopicMessage();
                        m.dialogId = mo.optLong("did", 0);
                        m.accessHash = mo.optLong("ah", 0);
                        m.messageId = mo.optInt("mid", 0);
                        m.chatTitle = mo.optString("ct", "");
                        m.chatUsername = mo.optString("cu", "");
                        m.senderName = mo.optString("sn", "");
                        m.previewText = mo.optString("txt", "");
                        m.savedAtMs = mo.optLong("ts", 0);
                        if (m.dialogId != 0 && m.messageId != 0) {
                            f.messages.add(m);
                        }
                    }
                }
                FOLDERS.add(f);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void saveLocked() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return;
        JSONArray cardsArr = new JSONArray();
        for (MemoryCard card : CARDS.values()) {
            if (card.notes.isEmpty() && card.appointments.isEmpty()) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("did", card.dialogId);
                o.put("title", card.title != null ? card.title : "");
                o.put("user", card.username != null ? card.username : "");
                o.put("ts", card.updatedAtMs);

                JSONArray nArr = new JSONArray();
                for (NoteEntry n : card.notes) {
                    JSONObject no = new JSONObject();
                    no.put("id", n.id);
                    no.put("text", n.text);
                    no.put("ts", n.createdAtMs);
                    nArr.put(no);
                }
                o.put("notes", nArr);

                JSONArray aArr = new JSONArray();
                for (AppointmentEntry a : card.appointments) {
                    JSONObject ao = new JSONObject();
                    ao.put("id", a.id);
                    ao.put("title", a.title);
                    ao.put("when", a.whenText);
                    ao.put("ts", a.timestampMs);
                    ao.put("done", a.done);
                    aArr.put(ao);
                }
                o.put("apps", aArr);
                cardsArr.put(o);
            } catch (Throwable ignore) {
            }
        }

        JSONArray foldersArr = new JSONArray();
        for (TopicFolder f : FOLDERS) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", f.id);
                o.put("name", f.name);
                o.put("color", f.color);
                o.put("ts", f.createdAtMs);

                JSONArray mArr = new JSONArray();
                for (TopicMessage m : f.messages) {
                    JSONObject mo = new JSONObject();
                    mo.put("did", m.dialogId);
                    mo.put("ah", m.accessHash);
                    mo.put("mid", m.messageId);
                    mo.put("ct", m.chatTitle != null ? m.chatTitle : "");
                    mo.put("cu", m.chatUsername != null ? m.chatUsername : "");
                    mo.put("sn", m.senderName != null ? m.senderName : "");
                    mo.put("txt", m.previewText != null ? m.previewText : "");
                    mo.put("ts", m.savedAtMs);
                    mArr.put(mo);
                }
                o.put("msgs", mArr);
                foldersArr.put(o);
            } catch (Throwable ignore) {
            }
        }

        prefs.edit()
                .putString("cards_json", cardsArr.toString())
                .putString("folders_json", foldersArr.toString())
                .apply();
    }

    // ---- Memory Card APIs ----

    public static MemoryCard getCard(long dialogId) {
        ensureLoaded();
        synchronized (LOCK) {
            return CARDS.get(dialogId);
        }
    }

    public static int getCardItemCount(long dialogId) {
        ensureLoaded();
        synchronized (LOCK) {
            MemoryCard card = CARDS.get(dialogId);
            return card != null ? (card.notes.size() + card.appointments.size()) : 0;
        }
    }

    private static MemoryCard getOrCreateCardLocked(long dialogId, String title, String username) {
        MemoryCard card = CARDS.get(dialogId);
        if (card == null) {
            card = new MemoryCard();
            card.dialogId = dialogId;
            CARDS.put(dialogId, card);
        }
        if (!TextUtils.isEmpty(title)) card.title = title;
        if (username != null) card.username = username;
        card.updatedAtMs = System.currentTimeMillis();
        return card;
    }

    public static void addNote(long dialogId, String title, String username, String text) {
        if (dialogId == 0 || TextUtils.isEmpty(text)) return;
        ensureLoaded();
        synchronized (LOCK) {
            MemoryCard card = getOrCreateCardLocked(dialogId, title, username);
            NoteEntry n = new NoteEntry();
            n.id = System.currentTimeMillis() ^ System.nanoTime();
            n.text = text.trim();
            n.createdAtMs = System.currentTimeMillis();
            card.notes.add(0, n);
            saveLocked();
        }
    }

    public static void deleteNote(long dialogId, long noteId) {
        ensureLoaded();
        synchronized (LOCK) {
            MemoryCard card = CARDS.get(dialogId);
            if (card == null) return;
            for (int i = 0; i < card.notes.size(); i++) {
                if (card.notes.get(i).id == noteId) {
                    card.notes.remove(i);
                    break;
                }
            }
            saveLocked();
        }
    }

    public static void addAppointment(long dialogId, String title, String username, String apTitle, String whenText, long timestampMs) {
        if (dialogId == 0 || TextUtils.isEmpty(apTitle)) return;
        ensureLoaded();
        synchronized (LOCK) {
            MemoryCard card = getOrCreateCardLocked(dialogId, title, username);
            AppointmentEntry a = new AppointmentEntry();
            a.id = System.currentTimeMillis() ^ System.nanoTime();
            a.title = apTitle.trim();
            a.whenText = whenText != null ? whenText.trim() : "";
            a.timestampMs = timestampMs;
            a.done = false;
            card.appointments.add(0, a);
            saveLocked();
        }
    }

    public static void toggleAppointmentDone(long dialogId, long apId) {
        ensureLoaded();
        synchronized (LOCK) {
            MemoryCard card = CARDS.get(dialogId);
            if (card == null) return;
            for (int i = 0; i < card.appointments.size(); i++) {
                AppointmentEntry a = card.appointments.get(i);
                if (a.id == apId) {
                    a.done = !a.done;
                    break;
                }
            }
            card.updatedAtMs = System.currentTimeMillis();
            saveLocked();
        }
    }

    public static void deleteAppointment(long dialogId, long apId) {
        ensureLoaded();
        synchronized (LOCK) {
            MemoryCard card = CARDS.get(dialogId);
            if (card == null) return;
            for (int i = 0; i < card.appointments.size(); i++) {
                if (card.appointments.get(i).id == apId) {
                    card.appointments.remove(i);
                    break;
                }
            }
            saveLocked();
        }
    }

    public static ArrayList<MemoryCard> getAllCards() {
        ensureLoaded();
        synchronized (LOCK) {
            ArrayList<MemoryCard> list = new ArrayList<>();
            for (MemoryCard c : CARDS.values()) {
                if (!c.notes.isEmpty() || !c.appointments.isEmpty()) {
                    list.add(c);
                }
            }
            Collections.sort(list, (a, b) -> Long.compare(b.updatedAtMs, a.updatedAtMs));
            return list;
        }
    }

    // ---- Cross-Chat Virtual Topic Folder APIs ----

    public static ArrayList<TopicFolder> getFolders() {
        ensureLoaded();
        synchronized (LOCK) {
            return new ArrayList<>(FOLDERS);
        }
    }

    public static TopicFolder getFolderById(String folderId) {
        if (TextUtils.isEmpty(folderId)) return null;
        ensureLoaded();
        synchronized (LOCK) {
            for (int i = 0; i < FOLDERS.size(); i++) {
                if (folderId.equals(FOLDERS.get(i).id)) {
                    return FOLDERS.get(i);
                }
            }
            return null;
        }
    }

    public static TopicFolder createFolder(String name, int color) {
        if (TextUtils.isEmpty(name)) return null;
        ensureLoaded();
        synchronized (LOCK) {
            TopicFolder f = new TopicFolder();
            f.id = "topic_" + System.currentTimeMillis();
            f.name = name.trim();
            f.color = color != 0 ? color : FOLDER_COLORS[FOLDERS.size() % FOLDER_COLORS.length];
            f.createdAtMs = System.currentTimeMillis();
            FOLDERS.add(0, f);
            saveLocked();
            return f;
        }
    }

    public static void deleteFolder(String folderId) {
        ensureLoaded();
        synchronized (LOCK) {
            for (int i = 0; i < FOLDERS.size(); i++) {
                if (folderId.equals(FOLDERS.get(i).id)) {
                    FOLDERS.remove(i);
                    saveLocked();
                    break;
                }
            }
        }
    }

    public static boolean saveMessageToFolder(
            String folderId,
            long dialogId,
            long accessHash,
            int messageId,
            String chatTitle,
            String chatUsername,
            String senderName,
            String previewText
    ) {
        if (TextUtils.isEmpty(folderId) || dialogId == 0 || messageId == 0) return false;
        ensureLoaded();
        synchronized (LOCK) {
            TopicFolder f = null;
            for (int i = 0; i < FOLDERS.size(); i++) {
                if (folderId.equals(FOLDERS.get(i).id)) {
                    f = FOLDERS.get(i);
                    break;
                }
            }
            if (f == null) return false;

            for (int i = 0; i < f.messages.size(); i++) {
                TopicMessage existing = f.messages.get(i);
                if (existing.dialogId == dialogId && existing.messageId == messageId) {
                    return false; // Already in this topic folder
                }
            }
            TopicMessage m = new TopicMessage();
            m.dialogId = dialogId;
            m.accessHash = accessHash;
            m.messageId = messageId;
            m.chatTitle = !TextUtils.isEmpty(chatTitle) ? chatTitle : ("Chat " + dialogId);
            m.chatUsername = chatUsername != null ? chatUsername : "";
            m.senderName = senderName != null ? senderName : "";
            m.previewText = !TextUtils.isEmpty(previewText) ? previewText : "[وسائط / رسالة]";
            m.savedAtMs = System.currentTimeMillis();
            f.messages.add(0, m);
            saveLocked();
            return true;
        }
    }

    public static void removeMessageFromFolder(String folderId, long dialogId, int messageId) {
        ensureLoaded();
        synchronized (LOCK) {
            for (int i = 0; i < FOLDERS.size(); i++) {
                TopicFolder f = FOLDERS.get(i);
                if (folderId.equals(f.id)) {
                    for (int j = 0; j < f.messages.size(); j++) {
                        TopicMessage m = f.messages.get(j);
                        if (m.dialogId == dialogId && m.messageId == messageId) {
                            f.messages.remove(j);
                            saveLocked();
                            return;
                        }
                    }
                }
            }
        }
    }
}
