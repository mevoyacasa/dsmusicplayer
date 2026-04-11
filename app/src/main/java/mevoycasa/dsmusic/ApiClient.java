package mevoycasa.dsmusic;

import android.content.Context;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ApiClient {
    public interface JsonCallback {
        void onSuccess(JSONObject json);
        void onError(String message);
    }

    public interface ArrayCallback {
        void onSuccess(List<MediaItemModel> items, boolean hasMore, int total);
        void onError(String message);
    }

    public interface LyricsCallback {
        void onSuccess(List<LyricLine> lyrics);
        void onError(String message);
    }

    public interface StringCallback {
        void onSuccess(String value);
        void onError(String message);
    }

    public interface CacheCallback {
        void onProgress(int percent);
        void onSuccess(String value);
        void onError(String message);
    }

    public interface StringListCallback {
        void onSuccess(List<String> values);
        void onError(String message);
    }

    public static class MediaItemModel {
        public String id;
        public String title;
        public String subtitle;
        public String coverUrl;
        public String type;
        public String album;
        public String artist;
        public String path;
        public String streamUrl;
        public String localCoverPath;
        public long durationMs;
        public int playCount;
        public JSONObject raw;
    }

    public static class LyricLine {
        public long timeMs;
        public String primary;
        public String secondary;
    }

    public static class AccountProfile {
        public String host;
        public String account;
        public String password;
        public boolean forceHttps;
        public boolean ignoreCert;
    }

    public static final String PREFS = "as_music_prefs";
    public static final String KEY_HOST = "host";
    public static final String KEY_ACCOUNT = "account";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_SID = "sid";
    public static final String KEY_FORCE_HTTPS = "force_https";
    public static final String KEY_IGNORE_CERT = "ignore_cert";
    public static final String KEY_REMEMBER_ACCOUNT = "remember_account";
    public static final String KEY_REMEMBER_PASSWORD = "remember_password";
    public static final String KEY_HISTORY = "play_history";
    public static final String KEY_ROOT_SONG_CACHE = "root_song_cache";
    public static final String KEY_CACHED_LIBRARY = "cached_library";
    public static final String KEY_ACCOUNT_PROFILES = "account_profiles";
    public static final String KEY_LAST_PLAYED_MAP = "last_played_map";
    public static final String KEY_CACHE_AUTO_CLEAN = "cache_auto_clean";
    public static final String KEY_CACHE_SCHEDULE_ENABLED = "cache_schedule_enabled";
    public static final String KEY_CACHE_CLEAN_DAYS = "cache_clean_days";
    public static final String KEY_CACHE_MAX_MB = "cache_max_mb";
    public static final String KEY_CACHE_INTERVAL_HOURS = "cache_interval_hours";
    public static final String KEY_ALLOW_CONCURRENT_MEDIA_PLAYBACK = "allow_concurrent_media_playback";
    public static final String KEY_SONG_CACHE_TREE_URI = "song_cache_tree_uri";
    public static final String KEY_CACHE_LOCATION_PROMPT_DONE = "cache_location_prompt_done";
    public static final String KEY_CACHE_RULE_DAYS_ENABLED = "cache_rule_days_enabled";
    public static final String KEY_CACHE_RULE_SIZE_ENABLED = "cache_rule_size_enabled";
    public static final String KEY_LYRIC_OFFSET_MS = "lyric_offset_ms";    
    public static final String KEY_APP_LANGUAGE = "app_language";

    private static final String INTERNAL_SONG_CACHE_DIR = "songs";
    private static final String INTERNAL_COVER_CACHE_DIR = "covers";
    private static final String INTERNAL_LYRICS_CACHE_DIR = "lyrics_cache";

    private final Context appContext;
    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, String> songCacheUriMemo = new ConcurrentHashMap<>();
    private Call currentSongCacheCall;
    private boolean songCacheCancelled = false;

    public ApiClient(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public SharedPreferences prefs() {
        return preferences;
    }

    public boolean isConcurrentPlaybackAllowed() {
        return preferences.getBoolean(KEY_ALLOW_CONCURRENT_MEDIA_PLAYBACK, false);
    }

    public void setConcurrentPlaybackAllowed(boolean allow) {
        preferences.edit().putBoolean(KEY_ALLOW_CONCURRENT_MEDIA_PLAYBACK, allow).apply();
    }

    public long getLyricOffsetMs() {
        return preferences.getLong(KEY_LYRIC_OFFSET_MS, 0L);
    }

    public void setLyricOffsetMs(long offsetMs) {
        preferences.edit().putLong(KEY_LYRIC_OFFSET_MS, offsetMs).apply();
    }

    public String getAppLanguage() {
        return preferences.getString(KEY_APP_LANGUAGE, "zh");
    }

    public void setAppLanguage(String language) {
        preferences.edit().putString(KEY_APP_LANGUAGE, language).apply();
    }

    public void setSongCacheTreeUri(@Nullable Uri treeUri) {
        SharedPreferences.Editor editor = preferences.edit();
        if (treeUri == null) {
            editor.remove(KEY_SONG_CACHE_TREE_URI);
        } else {
            editor.putString(KEY_SONG_CACHE_TREE_URI, treeUri.toString());
        }
        editor.apply();
    }

    @Nullable
    public Uri getSongCacheTreeUri() {
        String raw = preferences.getString(KEY_SONG_CACHE_TREE_URI, "");
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        try {
            return Uri.parse(raw);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean hasCustomSongCacheDirectory() {
        return getSongCacheTreeUri() != null;
    }

    public String getSongCacheDirectoryLabel() {
        Uri treeUri = getSongCacheTreeUri();
        if (treeUri == null) {
            return appContext.getString(R.string.app_internal_cache);
        }
        DocumentFile root = DocumentFile.fromTreeUri(appContext, treeUri);
        if (root != null) {
            String name = root.getName();
            if (!TextUtils.isEmpty(name)) {
                return name;
            }
        }
        return treeUri.toString();
    }

    public List<AccountProfile> readAccountProfiles() {
        List<AccountProfile> profiles = new ArrayList<>();
        String raw = preferences.getString(KEY_ACCOUNT_PROFILES, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                String account = entry.optString("account", "");
                String host = entry.optString("host", "");
                if (TextUtils.isEmpty(account) || TextUtils.isEmpty(host)) {
                    continue;
                }
                AccountProfile profile = new AccountProfile();
                profile.account = account;
                profile.host = host;
                profile.password = entry.optString("password", "");
                profile.forceHttps = entry.optBoolean("forceHttps", true);
                profile.ignoreCert = entry.optBoolean("ignoreCert", true);
                profiles.add(profile);
            }
        } catch (JSONException ignored) {
        }
        return profiles;
    }

    public void saveAccountProfile(String host, String account, String password, boolean forceHttps, boolean ignoreCert) {
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(account)) {
            return;
        }
        JSONArray next = new JSONArray();
        JSONObject current = new JSONObject();
        try {
            current.put("host", host);
            current.put("account", account);
            current.put("password", password == null ? "" : password);
            current.put("forceHttps", forceHttps);
            current.put("ignoreCert", ignoreCert);
            next.put(current);
        } catch (JSONException ignored) {
            return;
        }
        List<AccountProfile> existing = readAccountProfiles();
        int kept = 1;
        for (AccountProfile profile : existing) {
            if (profile == null) {
                continue;
            }
            if (TextUtils.equals(profile.account, account) && TextUtils.equals(profile.host, host)) {
                continue;
            }
            JSONObject entry = new JSONObject();
            try {
                entry.put("host", profile.host);
                entry.put("account", profile.account);
                entry.put("password", profile.password == null ? "" : profile.password);
                entry.put("forceHttps", profile.forceHttps);
                entry.put("ignoreCert", profile.ignoreCert);
                next.put(entry);
                kept++;
            } catch (JSONException ignored) {
            }
            if (kept >= 12) {
                break;
            }
        }
        preferences.edit().putString(KEY_ACCOUNT_PROFILES, next.toString()).apply();
    }

    private void writeAccountProfiles(List<AccountProfile> profiles) {
        JSONArray array = new JSONArray();
        int kept = 0;
        for (AccountProfile profile : profiles) {
            if (profile == null || TextUtils.isEmpty(profile.host) || TextUtils.isEmpty(profile.account)) {
                continue;
            }
            JSONObject entry = new JSONObject();
            try {
                entry.put("host", profile.host);
                entry.put("account", profile.account);
                entry.put("password", profile.password == null ? "" : profile.password);
                entry.put("forceHttps", profile.forceHttps);
                entry.put("ignoreCert", profile.ignoreCert);
            } catch (JSONException ignored) {
            }
            array.put(entry);
            kept++;
            if (kept >= 12) {
                break;
            }
        }
        preferences.edit().putString(KEY_ACCOUNT_PROFILES, array.toString()).apply();
    }

    public long getLastPlayed(String songId) {
        if (TextUtils.isEmpty(songId)) {
            return 0L;
        }
        String raw = preferences.getString(KEY_LAST_PLAYED_MAP, "{}");
        try {
            JSONObject map = new JSONObject(raw == null ? "{}" : raw);
            return map.optLong(songId, 0L);
        } catch (JSONException ignored) {
            return 0L;
        }
    }

    private void setLastPlayed(String songId, long timeMs) {
        if (TextUtils.isEmpty(songId) || timeMs <= 0L) {
            return;
        }
        String raw = preferences.getString(KEY_LAST_PLAYED_MAP, "{}");
        try {
            JSONObject map = new JSONObject(raw == null ? "{}" : raw);
            map.put(songId, timeMs);
            preferences.edit().putString(KEY_LAST_PLAYED_MAP, map.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private File getInternalSongCacheDir() {
        return new File(appContext.getCacheDir(), INTERNAL_SONG_CACHE_DIR);
    }

    private File getInternalCoverCacheDir() {
        return new File(appContext.getCacheDir(), INTERNAL_COVER_CACHE_DIR);
    }

    @Nullable
    private DocumentFile getCustomSongCacheSongsDir(boolean create) {
        Uri treeUri = getSongCacheTreeUri();
        if (treeUri == null) {
            return null;
        }
        DocumentFile root = DocumentFile.fromTreeUri(appContext, treeUri);
        if (root == null) {
            return null;
        }
        DocumentFile dir = findChildDirectory(root, "songs");
        if (dir == null && create) {
            dir = root.createDirectory("songs");
        }
        return dir;
    }

    @Nullable
    private DocumentFile findChildDirectory(DocumentFile parent, String name) {
        if (parent == null || TextUtils.isEmpty(name)) {
            return null;
        }
        for (DocumentFile child : parent.listFiles()) {
            if (child != null && child.isDirectory() && name.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    @Nullable
    private DocumentFile findChildFile(DocumentFile parent, String name) {
        if (parent == null || TextUtils.isEmpty(name)) {
            return null;
        }
        for (DocumentFile child : parent.listFiles()) {
            if (child != null && child.isFile() && name.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    private String songCacheFileName(MediaItemModel item) {
        String extension = guessFileExtension(item == null ? null : item.path);
        String artist = item == null ? "" : safeName(item.artist);
        String title = item == null ? "" : safeName(item.title);
        String id = item == null ? "unknown" : safeName(item.id);
        if (TextUtils.isEmpty(artist) && TextUtils.isEmpty(title)) {
            return id + extension;
        }
        if (TextUtils.isEmpty(artist)) {
            return title + extension;
        }
        if (TextUtils.isEmpty(title)) {
            return artist + extension;
        }
        return artist + "-" + title + extension;
    }

    @Nullable
    private DocumentFile findCustomSongCacheFile(MediaItemModel item) {
        DocumentFile songsDir = getCustomSongCacheSongsDir(false);
        if (songsDir == null) {
            return null;
        }
        return findChildFile(songsDir, songCacheFileName(item));
    }

    @Nullable
    private DocumentFile createCustomSongCacheFile(MediaItemModel item) {
        DocumentFile songsDir = getCustomSongCacheSongsDir(true);
        if (songsDir == null) {
            return null;
        }
        String name = songCacheFileName(item);
        String mime = guessMimeType(item == null ? null : item.path);
        DocumentFile existing = findChildFile(songsDir, name);
        if (existing != null && existing.exists()) {
            return existing;
        }
        return songsDir.createFile(mime, name);
    }

    private boolean deleteDocumentFileTree(@Nullable DocumentFile file) {
        if (file == null || !file.exists()) {
            return false;
        }
        boolean deleted = false;
        if (file.isDirectory()) {
            for (DocumentFile child : file.listFiles()) {
                deleted |= deleteDocumentFileTree(child);
            }
        }
        return file.delete() || deleted;
    }

    private List<CacheEntry> listSongCacheEntries() {
        List<CacheEntry> entries = new ArrayList<>();
        collectFileEntries(getInternalSongCacheDir(), entries);
        collectDocumentEntries(getCustomSongCacheSongsDir(false), entries);
        return entries;
    }

    private void collectFileEntries(File dir, List<CacheEntry> entries) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file != null && file.isFile()) {
                entries.add(CacheEntry.forFile(file));
            }
        }
    }

    private void collectDocumentEntries(@Nullable DocumentFile dir, List<CacheEntry> entries) {
        if (dir == null || !dir.exists()) {
            return;
        }
        for (DocumentFile child : dir.listFiles()) {
            if (child != null && child.isFile()) {
                entries.add(CacheEntry.forDocument(child));
            }
        }
    }

    private static final class CacheEntry {
        final File file;
        final DocumentFile documentFile;
        final String songId;
        final long sizeBytes;
        final long lastModified;

        private CacheEntry(File file, DocumentFile documentFile, String songId, long sizeBytes, long lastModified) {
            this.file = file;
            this.documentFile = documentFile;
            this.songId = songId;
            this.sizeBytes = sizeBytes;
            this.lastModified = lastModified;
        }

        static CacheEntry forFile(File file) {
            return new CacheEntry(file, null, fileSongId(file), Math.max(0L, file.length()), file.lastModified());
        }

        static CacheEntry forDocument(DocumentFile file) {
            return new CacheEntry(null, file, fileSongId(file), Math.max(0L, file.length()), file.lastModified());
        }

        boolean delete() {
            if (file != null) {
                return file.delete();
            }
            return documentFile != null && documentFile.delete();
        }

        private static String fileSongId(File file) {
            if (file == null) {
                return "";
            }
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }

        private static String fileSongId(DocumentFile file) {
            if (file == null || file.getName() == null) {
                return "";
            }
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
    }

    private static final class ResourceCacheEntry {
        static final String KIND_COVER = "cover";
        static final String KIND_LYRICS = "lyrics";
        static final String KIND_OTHER = "other";

        final File file;
        final String kind;
        final String songId;
        final long sizeBytes;
        final long lastModified;

        private ResourceCacheEntry(File file, String kind, String songId, long sizeBytes, long lastModified) {
            this.file = file;
            this.kind = kind;
            this.songId = songId;
            this.sizeBytes = sizeBytes;
            this.lastModified = lastModified;
        }

        static ResourceCacheEntry forFile(File file, String kind, String songId) {
            return new ResourceCacheEntry(file, kind, songId, Math.max(0L, file.length()), file.lastModified());
        }

        boolean delete() {
            return file != null && file.delete();
        }
    }

    public static final class ResourceCacheStats {
        public int totalCount;
        public long totalBytes;
        public int coverCount;
        public long coverBytes;
        public int lyricsCount;
        public long lyricsBytes;
        public int otherCount;
        public long otherBytes;

        public int typeCount() {
            int count = 0;
            if (coverCount > 0) {
                count++;
            }
            if (lyricsCount > 0) {
                count++;
            }
            if (otherCount > 0) {
                count++;
            }
            return count;
        }
    }

    public int clearAllCacheFiles() {
        cancelSongCache();
        int deleted = 0;
        deleted += deleteRecursively(getInternalSongCacheDir());
        deleted += deleteDocumentFileTree(getCustomSongCacheSongsDir(false)) ? 1 : 0;
        deleted += deleteRecursively(getInternalCoverCacheDir());
        preferences.edit().putString(KEY_CACHED_LIBRARY, "[]").apply();
        preserveCachedLibrary();
        return deleted;
    }

    public long getResourceCacheSizeBytes() {
        return dirSizeExceptSongs(appContext.getCacheDir());
    }

    public ResourceCacheStats getResourceCacheStats() {
        ResourceCacheStats stats = new ResourceCacheStats();
        List<ResourceCacheEntry> entries = listResourceCacheEntries();
        for (ResourceCacheEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            long size = Math.max(0L, entry.sizeBytes);
            stats.totalCount++;
            stats.totalBytes += size;
            if (ResourceCacheEntry.KIND_COVER.equals(entry.kind)) {
                stats.coverCount++;
                stats.coverBytes += size;
            } else if (ResourceCacheEntry.KIND_LYRICS.equals(entry.kind)) {
                stats.lyricsCount++;
                stats.lyricsBytes += size;
            } else {
                stats.otherCount++;
                stats.otherBytes += size;
            }
        }
        return stats;
    }

    public int clearResourceCacheAll() {
        LyricsCacheManager.getInstance(appContext).clearCache();
        int deleted = 0;
        File root = appContext.getCacheDir();
        File[] children = root == null ? null : root.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child == null || INTERNAL_SONG_CACHE_DIR.equals(child.getName())) {
                    continue;
                }
                deleted += deleteRecursively(child);
            }
        }
        return deleted;
    }

    public int clearResourceCacheNotPlayedSinceDays(int days) {
        if (days <= 0) {
            return 0;
        }
        long threshold = System.currentTimeMillis() - (days * 86400000L);
        List<ResourceCacheEntry> entries = listResourceCacheEntries();
        if (entries.isEmpty()) {
            return 0;
        }
        LinkedHashSet<String> deletedLyrics = new LinkedHashSet<>();
        int deleted = 0;
        for (ResourceCacheEntry entry : entries) {
            if (entry == null || !shouldDeleteResourceEntry(entry, threshold)) {
                continue;
            }
            if (entry.delete()) {
                deleted++;
                if (ResourceCacheEntry.KIND_LYRICS.equals(entry.kind) && !TextUtils.isEmpty(entry.songId)) {
                    deletedLyrics.add(entry.songId);
                }
            }
        }
        removeLyricsCacheEntries(deletedLyrics);
        return deleted;
    }

    public int clearResourceCacheUntilUnderMb(int maxMb) {
        if (maxMb <= 0) {
            return 0;
        }
        long limitBytes = maxMb * 1024L * 1024L;
        long size = getResourceCacheSizeBytes();
        if (size <= limitBytes) {
            return 0;
        }
        List<ResourceCacheEntry> entries = listResourceCacheEntries();
        if (entries.isEmpty()) {
            return 0;
        }
        entries.sort((a, b) -> {
            long ta = resourceEntryTimestamp(a);
            long tb = resourceEntryTimestamp(b);
            if (ta != tb) {
                return Long.compare(ta, tb);
            }
            return Long.compare(a == null ? 0L : a.sizeBytes, b == null ? 0L : b.sizeBytes);
        });
        LinkedHashSet<String> deletedLyrics = new LinkedHashSet<>();
        int deleted = 0;
        for (ResourceCacheEntry entry : entries) {
            if (size <= limitBytes) {
                break;
            }
            if (entry == null || !entry.delete()) {
                continue;
            }
            deleted++;
            size -= Math.max(0L, entry.sizeBytes);
            if (ResourceCacheEntry.KIND_LYRICS.equals(entry.kind) && !TextUtils.isEmpty(entry.songId)) {
                deletedLyrics.add(entry.songId);
            }
        }
        removeLyricsCacheEntries(deletedLyrics);
        return deleted;
    }

    public int clearSelectedResourceCache(boolean clearCover, boolean clearLyrics, boolean clearOther) {
        List<ResourceCacheEntry> entries = listResourceCacheEntries();
        if (entries.isEmpty()) {
            return 0;
        }
        LinkedHashSet<String> deletedLyrics = new LinkedHashSet<>();
        int deleted = 0;
        for (ResourceCacheEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            if (ResourceCacheEntry.KIND_COVER.equals(entry.kind) && !clearCover) {
                continue;
            }
            if (ResourceCacheEntry.KIND_LYRICS.equals(entry.kind) && !clearLyrics) {
                continue;
            }
            if (ResourceCacheEntry.KIND_OTHER.equals(entry.kind) && !clearOther) {
                continue;
            }
            if (entry.delete()) {
                deleted++;
                if (ResourceCacheEntry.KIND_LYRICS.equals(entry.kind) && !TextUtils.isEmpty(entry.songId)) {
                    deletedLyrics.add(entry.songId);
                }
            }
        }
        removeLyricsCacheEntries(deletedLyrics);
        return deleted;
    }

    public int clearCacheNotPlayedSinceDays(int days) {
        if (days <= 0) {
            return 0;
        }
        long threshold = System.currentTimeMillis() - (days * 86400000L);
        List<CacheEntry> entries = listSongCacheEntries();
        if (entries.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (CacheEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            long lastPlayed = getLastPlayed(entry.songId);
            if (lastPlayed <= 0L || lastPlayed < threshold) {
                if (entry.delete()) {
                    deleted++;
                }
            }
        }
        preserveCachedLibrary();
        return deleted;
    }

    public long getCacheSizeBytes() {
        long total = 0L;
        total += dirSize(getInternalSongCacheDir());
        total += dirSize(getInternalCoverCacheDir());
        total += dirSize(getCustomSongCacheSongsDir(false));
        return total;
    }

    public int clearCacheUntilUnderMb(int maxMb) {
        if (maxMb <= 0) {
            return 0;
        }
        long limitBytes = maxMb * 1024L * 1024L;
        long size = getCacheSizeBytes();
        if (size <= limitBytes) {
            return 0;
        }
        List<CacheEntry> songFiles = listSongCacheEntries();
        if (songFiles.isEmpty()) {
            return 0;
        }
        // Sort by last played asc (older first), then by file lastModified as fallback.
        songFiles.sort((a, b) -> {
            long la = getLastPlayed(a.songId);
            long lb = getLastPlayed(b.songId);
            if (la != lb) {
                return la < lb ? -1 : 1;
            }
            return Long.compare(a.lastModified, b.lastModified);
        });
        int deleted = 0;
        for (CacheEntry entry : songFiles) {
            if (size <= limitBytes) {
                break;
            }
            long len = entry.sizeBytes;
            if (entry.delete()) {
                deleted++;
                size -= Math.max(0L, len);
            }
        }
        preserveCachedLibrary();
        return deleted;
    }

    private long dirSize(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return Math.max(0L, file.length());
        }
        long sum = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                sum += dirSize(child);
            }
        }
        return sum;
    }

    private String fileSongId(File file) {
        if (file == null) {
            return "";
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private int deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        int deleted = 0;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleted += deleteRecursively(child);
                }
            }
        }
        if (file.delete()) {
            deleted++;
        }
        return deleted;
    }

    private long dirSizeExceptSongs(File dir) {
        if (dir == null || !dir.exists()) {
            return 0L;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return 0L;
        }
        long sum = 0L;
        for (File child : children) {
            if (child == null || INTERNAL_SONG_CACHE_DIR.equals(child.getName())) {
                continue;
            }
            sum += dirSize(child);
        }
        return sum;
    }

    private List<ResourceCacheEntry> listResourceCacheEntries() {
        List<ResourceCacheEntry> entries = new ArrayList<>();
        File root = appContext.getCacheDir();
        File[] children = root == null ? null : root.listFiles();
        if (children == null) {
            return entries;
        }
        for (File child : children) {
            if (child == null || INTERNAL_SONG_CACHE_DIR.equals(child.getName())) {
                continue;
            }
            collectResourceCacheEntries(child, inferResourceKind(child.getName(), null), entries);
        }
        return entries;
    }

    private void collectResourceCacheEntries(File file, String kind, List<ResourceCacheEntry> entries) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            String nextKind = inferResourceKind(file.getName(), kind);
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    collectResourceCacheEntries(child, nextKind, entries);
                }
            }
            return;
        }
        String actualKind = inferResourceKind(file.getName(), kind);
        entries.add(ResourceCacheEntry.forFile(file, actualKind, resourceSongId(file, actualKind)));
    }

    private String inferResourceKind(String name, @Nullable String currentKind) {
        if (TextUtils.isEmpty(name)) {
            return TextUtils.isEmpty(currentKind) ? ResourceCacheEntry.KIND_OTHER : currentKind;
        }
        if (INTERNAL_COVER_CACHE_DIR.equals(name)) {
            return ResourceCacheEntry.KIND_COVER;
        }
        if (INTERNAL_LYRICS_CACHE_DIR.equals(name)) {
            return ResourceCacheEntry.KIND_LYRICS;
        }
        if (!TextUtils.isEmpty(currentKind)) {
            return currentKind;
        }
        return ResourceCacheEntry.KIND_OTHER;
    }

    private String resourceSongId(File file, String kind) {
        if (file == null || TextUtils.isEmpty(kind)) {
            return "";
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        if (ResourceCacheEntry.KIND_LYRICS.equals(kind)) {
            return stem;
        }
        if (ResourceCacheEntry.KIND_COVER.equals(kind)) {
            int underscore = stem.indexOf('_');
            if (underscore >= 0 && underscore + 1 < stem.length()) {
                return stem.substring(underscore + 1);
            }
        }
        return "";
    }

    private boolean shouldDeleteResourceEntry(ResourceCacheEntry entry, long threshold) {
        if (entry == null) {
            return false;
        }
        long timestamp = resourceEntryTimestamp(entry);
        return timestamp > 0L && timestamp < threshold;
    }

    private long resourceEntryTimestamp(ResourceCacheEntry entry) {
        if (entry == null) {
            return 0L;
        }
        if (!TextUtils.isEmpty(entry.songId)) {
            long lastPlayed = getLastPlayed(entry.songId);
            if (lastPlayed > 0L) {
                return lastPlayed;
            }
        }
        return entry.lastModified;
    }

    private void removeLyricsCacheEntries(@Nullable LinkedHashSet<String> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return;
        }
        LyricsCacheManager.getInstance(appContext).removeCachedLyricsBySongIds(songIds);
    }

    public String normalizeHost(String host, boolean forceHttps) {
        String value = host == null ? "" : host.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = (forceHttps ? "https://" : "http://") + value;
        }
        if (forceHttps && value.startsWith("http://")) {
            value = "https://" + value.substring(7);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public void login(String host, String account, String password, boolean forceHttps, boolean ignoreCert, JsonCallback callback) {
        String normalizedHost = normalizeHost(host, forceHttps);
        HttpUrl base = HttpUrl.parse(normalizedHost + "/webapi/auth.cgi");
        if (base == null) {
            postError(callback, appContext.getString(R.string.server_address_invalid));
            return;
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("api", "SYNO.API.Auth")
                .addQueryParameter("version", "3")
                .addQueryParameter("method", "login")
                .addQueryParameter("session", "AudioStation")
                .addQueryParameter("format", "sid")
                .addQueryParameter("account", account)
                .addQueryParameter("passwd", password)
                .build();
        executeJsonOnce(url, ignoreCert, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!json.optBoolean("success")) {
                    postError(callback, extractSynologyError(json));
                    return;
                }
                JSONObject data = json.optJSONObject("data");
                String sid = data == null ? "" : data.optString("sid", "");
                preferences.edit()
                        .putString(KEY_HOST, normalizedHost)
                        .putString(KEY_SID, sid)
                        .putBoolean(KEY_FORCE_HTTPS, forceHttps)
                        .putBoolean(KEY_IGNORE_CERT, ignoreCert)
                        .apply();
                boolean rememberAccount = preferences.getBoolean(KEY_REMEMBER_ACCOUNT, true);
                boolean rememberPassword = preferences.getBoolean(KEY_REMEMBER_PASSWORD, true);
                if (rememberAccount) {
                    saveAccountProfile(
                            normalizedHost,
                            account,
                            rememberPassword ? password : "",
                            forceHttps,
                            ignoreCert
                    );
                }
                postSuccess(callback, json);
            }

            @Override
            public void onError(String message) {
                postError(callback, message);
            }
        });
    }

    public void fetchBrowserItems(String mode, String id, String parentType, String keyword, int offset, int limit, String sortBy, String sortDirection, ArrayCallback callback) {
        String host = preferences.getString(KEY_HOST, "");
        String sid = preferences.getString(KEY_SID, "");
        boolean ignoreCert = preferences.getBoolean(KEY_IGNORE_CERT, true);
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(sid)) {
            postError(callback, appContext.getString(R.string.login_expired_please_relogin));
            return;
        }
        if ("search".equals(mode)) {
            searchSongs(keyword, sortBy, sortDirection, callback);
            return;
        }
        if ("songs".equals(mode) && "playlist".equals(parentType) && !TextUtils.isEmpty(id)) {
            fetchPlaylistSongs(host, sid, ignoreCert, id, offset, limit, callback);
            return;
        }
        HttpUrl url = buildListUrl(host, mode, id, parentType, keyword, sid, offset, limit, sortBy, sortDirection);
        if (url == null) {
            postError(callback, appContext.getString(R.string.unsupported_browse_type));
            return;
        }
        executeJsonOnce(url, ignoreCert, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!json.optBoolean("success")) {
                    postError(callback, extractSynologyError(json));
                    return;
                }
                JSONObject data = json.optJSONObject("data");
                List<MediaItemModel> items = parseList(mode, data);
                int total = extractTotal(mode, data, items.size());
                boolean hasMore = offset + items.size() < total;
                if ("folders".equals(mode) && items.size() >= limit) {
                    hasMore = true;
                }
                postSuccess(callback, items, hasMore, total);
            }

            @Override
            public void onError(String message) {
                if ("search".equals(mode)) {
                    fetchSongFallbackSearch(host, sid, ignoreCert, keyword, offset, limit, sortBy, sortDirection, callback);
                } else {
                    postError(callback, message);
                }
            }
        });
    }

    public void fetchPersonalPlaylists(ArrayCallback callback) {
        fetchBrowserItems("playlists", null, null, null, 0, 1000, "name", "ASC", callback);
    }

    public void fetchAllSongs(ArrayCallback callback) {
        String host = preferences.getString(KEY_HOST, "");
        String sid = preferences.getString(KEY_SID, "");
        boolean ignoreCert = preferences.getBoolean(KEY_IGNORE_CERT, true);
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(sid)) {
            postError(callback, appContext.getString(R.string.login_expired_please_relogin));
            return;
        }
        fetchAllSongsPage(host, sid, ignoreCert, 0, 200, "title", "ASC", new ArrayList<>(), callback);
    }

    public void createPlaylist(String name, StringCallback callback) {
        if (TextUtils.isEmpty(name)) {
            postError(callback, appContext.getString(R.string.playlist_name_empty_error));
            return;
        }
        String host = preferences.getString(KEY_HOST, "");
        String sid = preferences.getString(KEY_SID, "");
        boolean ignoreCert = preferences.getBoolean(KEY_IGNORE_CERT, true);
        HttpUrl base = HttpUrl.parse(host + "/webapi/AudioStation/playlist.cgi");
        if (base == null) {
            postError(callback, appContext.getString(R.string.playlist_api_invalid));
            return;
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("api", "SYNO.AudioStation.Playlist")
                .addQueryParameter("version", "3")
                .addQueryParameter("method", "create")
                .addQueryParameter("_sid", sid)
                .addQueryParameter("library", "personal")
                .addQueryParameter("name", name.trim())
                .build();
        executeJsonOnce(url, ignoreCert, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!json.optBoolean("success")) {
                    postError(callback, extractSynologyError(json));
                    return;
                }
                JSONObject data = json.optJSONObject("data");
                String playlistId = data == null ? name.trim() : data.optString("id", name.trim());
                postSuccess(callback, playlistId);
            }

            @Override
            public void onError(String message) {
                postError(callback, message);
            }
        });
    }

    public void renamePlaylist(String playlistId, String newName, StringCallback callback) {
        if (TextUtils.isEmpty(playlistId) || TextUtils.isEmpty(newName)) {
            postError(callback, appContext.getString(R.string.playlist_params_incomplete));
            return;
        }
        String host = preferences.getString(KEY_HOST, "");
        String sid = preferences.getString(KEY_SID, "");
        boolean ignoreCert = preferences.getBoolean(KEY_IGNORE_CERT, true);
        HttpUrl base = HttpUrl.parse(host + "/webapi/AudioStation/playlist.cgi");
        if (base == null) {
            postError(callback, appContext.getString(R.string.playlist_api_invalid));
            return;
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("api", "SYNO.AudioStation.Playlist")
                .addQueryParameter("version", "3")
                .addQueryParameter("method", "rename")
                .addQueryParameter("_sid", sid)
                .addQueryParameter("id", playlistId)
                .addQueryParameter("new_name", newName.trim())
                .build();
        executeJsonOnce(url, ignoreCert, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!json.optBoolean("success")) {
                    postError(callback, extractSynologyError(json));
                    return;
                }
                postSuccess(callback, newName.trim());
            }

            @Override
            public void onError(String message) {
                postError(callback, message);
            }
        });
    }

    public void deletePlaylist(String playlistId, StringCallback callback) {
        if (TextUtils.isEmpty(playlistId)) {
            postError(callback, appContext.getString(R.string.playlist_params_incomplete));
            return;
        }
        String host = preferences.getString(KEY_HOST, "");
        String sid = preferences.getString(KEY_SID, "");
        boolean ignoreCert = preferences.getBoolean(KEY_IGNORE_CERT, true);
        HttpUrl base = HttpUrl.parse(host + "/webapi/AudioStation/playlist.cgi");
        if (base == null) {
            postError(callback, appContext.getString(R.string.playlist_api_invalid));
            return;
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("api", "SYNO.AudioStation.Playlist")
                .addQueryParameter("version", "3")
                .addQueryParameter("method", "delete")
                .addQueryParameter("_sid", sid)
                .addQueryParameter("id", playlistId)
                .build();
        executeJsonOnce(url, ignoreCert, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!json.optBoolean("success")) {
                    postError(callback, extractSynologyError(json));
                    return;
                }
                postSuccess(callback, playlistId);
            }

            @Override
            public void onError(String message) {
                postError(callback, message);
            }
        });
    }

    public void addSongsToPlaylist(String playlistId, List<String> songIds, StringCallback callback) {
        if (TextUtils.isEmpty(playlistId) || songIds == null || songIds.isEmpty()) {
            postError(callback, appContext.getString(R.string.no_songs_to_add_error));
            return;
        }
        addSongsToPlaylistWithRetry(playlistId, new ArrayList<>(songIds), callback, 0);
    }

    private void addSongsToPlaylistWithRetry(String playlistId, List<String> songIds, StringCallback callback, int attempt) {
        List<String> normalized = normalizeSongIds(songIds);
        if (normalized.isEmpty()) {
            postError(callback, appContext.getString(R.string.no_songs_to_add_error));
            return;
        }
        fetchPlaylistSongIds(playlistId, new StringListCallback() {
            @Override
            public void onSuccess(List<String> values) {
                LinkedHashSet<String> existing = new LinkedHashSet<>(values == null ? new ArrayList<>() : values);
                List<String> toAdd = new ArrayList<>();
                for (String songId : normalized) {
                    if (!existing.contains(songId)) {
                        toAdd.add(songId);
                    }
                }
                if (toAdd.isEmpty()) {
                    postSuccess(callback, playlistId);
                    return;
                }
                updatePlaylistSongs(playlistId, -1, Math.max(toAdd.size(), 1), joinSongIds(toAdd), new StringCallback() {
                    @Override
                    public void onSuccess(String value) {
                        postSuccess(callback, value);
                    }

                    @Override
                    public void onError(String message) {
                        if (attempt < NETWORK_RETRY_MAX && shouldRetryTransientMessage(message)) {
                            schedulePlaylistAddRetry(playlistId, normalized, callback, attempt + 1);
                            return;
                        }
                        postError(callback, message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (attempt < NETWORK_RETRY_MAX && shouldRetryTransientMessage(message)) {
                    schedulePlaylistAddRetry(playlistId, normalized, callback, attempt + 1);
                    return;
                }
                postError(callback, message);
            }
        });
    }

    public void removeSongFromPlaylist(String playlistId, String songId, StringCallback callback) {
        if (TextUtils.isEmpty(playlistId) || TextUtils.isEmpty(songId)) {
            postError(callback, appContext.getString(R.string.playlist_params_incomplete));
            return;
        }
        fetchPlaylistSongIds(playlistId, new StringListCallback() {
            @Override
            public void onSuccess(List<String> values) {
                if (values == null || values.isEmpty()) {
                    postError(callback, appContext.getString(R.string.no_removable_songs_in_playlist));
                    return;
                }
                ArrayList<String> remaining = new ArrayList<>();
                for (String value : values) {
                    if (!TextUtils.isEmpty(value) && !TextUtils.equals(value, songId)) {
                        remaining.add(value);
                    }
                }
                if (remaining.size() == values.size()) {
                    postError(callback, appContext.getString(R.string.song_not_found_in_playlist));
                    return;
                }
                replacePlaylistSongs(playlistId, remaining, values.size(), callback);
            }

            @Override
            public void onError(String message) {
                postError(callback, message);
            }
        });
    }

    public void replacePlaylistSongs(String playlistId, List<String> songIds, int currentCount, StringCallback callback) {
        if (TextUtils.isEmpty(playlistId)) {
            postError(callback, appContext.getString(R.string.playlist_params_incomplete));
            return;
        }
        updatePlaylistSongs(playlistId, 0, Math.max(currentCount, 0), joinSongIds(songIds), callback);
    }

    public void fetchLyrics(String songId, LyricsCallback callback) {
        String host = preferences.getString(KEY_HOST, "");
        String sid = preferences.getString(KEY_SID, "");
        boolean ignoreCert = preferences.getBoolean(KEY_IGNORE_CERT, true);
        HttpUrl base = HttpUrl.parse(host + "/webapi/AudioStation/lyrics.cgi");
        if (base == null) {
            postError(callback, appContext.getString(R.string.lyrics_api_invalid));
            return;
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("api", "SYNO.AudioStation.Lyrics")
                .addQueryParameter("version", "2")
                .addQueryParameter("method", "getlyrics")
                .addQueryParameter("_sid", sid)
                .addQueryParameter("id", songId)
                .build();
        executeJson(url, ignoreCert, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!json.optBoolean("success")) {
                    postError(callback, extractSynologyError(json));
                    return;
                }
                JSONObject data = json.optJSONObject("data");
                String raw = data == null ? "" : data.optString("lyrics", data.optString("full_lyrics", ""));
                postSuccess(callback, parseLyrics(raw));
            }

            @Override
            public void onError(String message) {
                postError(callback, message);
            }
        });
    }

    public void cacheSongToConfiguredDirectory(MediaItemModel item, CacheCallback callback) {
        if (item == null || TextUtils.isEmpty(item.id)) {
            postError(callback, appContext.getString(R.string.song_info_incomplete));
            return;
        }
        DocumentFile customTarget = createCustomSongCacheFile(item);
        if (customTarget != null && customTarget.exists() && customTarget.length() > 0L) {
            postProgress(callback, 100);
            songCacheUriMemo.put(item.id, customTarget.getUri().toString());
            upsertCachedSong(item);
            postSuccess(callback, customTarget.getUri().toString());
            return;
        }
        File legacyTarget = getSongCacheFile(item);
        if (legacyTarget.exists() && legacyTarget.length() > 0L) {
            postProgress(callback, 100);
            songCacheUriMemo.put(item.id, Uri.fromFile(legacyTarget).toString());
            upsertCachedSong(item);
            postSuccess(callback, legacyTarget.getAbsolutePath());
            return;
        }
        cancelSongCache();
        songCacheCancelled = false;
        if (customTarget != null) {
            currentSongCacheCall = downloadToDocumentFile(buildStreamUrl(item.id), customTarget, new CacheCallback() {
                @Override
                public void onProgress(int percent) {
                    callback.onProgress(percent);
                }

                @Override
                public void onSuccess(String value) {
                    songCacheUriMemo.put(item.id, value);
                    upsertCachedSong(item);
                    callback.onSuccess(value);
                }

                @Override
                public void onError(String message) {
                    if (appContext.getString(R.string.cannot_write_cache_file).equals(message)) {
                        startInternalSongCacheDownload(item, legacyTarget, callback);
                        return;
                    }
                    callback.onError(message);
                }
            }, true);
            if (currentSongCacheCall != null) {
                return;
            }
        }
        startInternalSongCacheDownload(item, legacyTarget, callback);
    }

    private void startInternalSongCacheDownload(MediaItemModel item, File legacyTarget, CacheCallback callback) {
        File dir = legacyTarget.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            postError(callback, appContext.getString(R.string.cannot_create_song_cache_dir));
            return;
        }
        currentSongCacheCall = downloadToFile(buildStreamUrl(item.id), legacyTarget, new CacheCallback() {
            @Override
            public void onProgress(int percent) {
                callback.onProgress(percent);
            }

            @Override
            public void onSuccess(String value) {
                songCacheUriMemo.put(item.id, value);
                upsertCachedSong(item);
                callback.onSuccess(value);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        }, true);
    }

    @Nullable
    public Uri getSongCacheReadUri(MediaItemModel item) {
        if (item == null || TextUtils.isEmpty(item.id)) {
            return null;
        }
        DocumentFile custom = findCustomSongCacheFile(item);
        if (custom != null && custom.exists() && custom.length() > 0L) {
            songCacheUriMemo.put(item.id, custom.getUri().toString());
            return custom.getUri();
        }
        File legacy = getSongCacheFile(item);
        if (legacy.exists() && legacy.length() > 0L) {
            songCacheUriMemo.put(item.id, Uri.fromFile(legacy).toString());
            return Uri.fromFile(legacy);
        }
        String memoized = songCacheUriMemo.get(item.id);
        if (!TextUtils.isEmpty(memoized)) {
            try {
                Uri cachedUri = Uri.parse(memoized);
                if ("file".equalsIgnoreCase(cachedUri.getScheme())) {
                    File file = new File(cachedUri.getPath() == null ? "" : cachedUri.getPath());
                    if (file.exists() && file.length() > 0L) {
                        return cachedUri;
                    }
                } else {
                    DocumentFile doc = DocumentFile.fromSingleUri(appContext, cachedUri);
                    if (doc != null && doc.exists() && doc.length() > 0L) {
                        return cachedUri;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public void cacheSong(MediaItemModel item, CacheCallback callback) {
        if (item == null || TextUtils.isEmpty(item.id)) {
            postError(callback, appContext.getString(R.string.song_info_incomplete));
            return;
        }
        File dir = new File(appContext.getCacheDir(), "songs");
        if (!dir.exists() && !dir.mkdirs()) {
            postError(callback, appContext.getString(R.string.cannot_create_song_cache_dir));
            return;
        }
        String extension = guessFileExtension(item.path);
        File target = new File(dir, safeName(item.id) + extension);
        cancelSongCache();
        songCacheCancelled = false;
        currentSongCacheCall = downloadToFile(buildStreamUrl(item.id), target, new CacheCallback() {
            @Override
            public void onProgress(int percent) {
                callback.onProgress(percent);
            }

            @Override
            public void onSuccess(String value) {
                songCacheUriMemo.put(item.id, Uri.fromFile(target).toString());
                upsertCachedSong(item);
                callback.onSuccess(value);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        }, true);
    }

    public boolean isSongCached(MediaItemModel item) {
        if (item == null || TextUtils.isEmpty(item.id)) {
            return false;
        }
        return getSongCacheReadUri(item) != null;
    }

    public List<MediaItemModel> readDownloadedSongs() {
        List<MediaItemModel> items = readMediaItemsFromPreference(KEY_CACHED_LIBRARY);
        for (MediaItemModel item : items) {
            if (item == null || TextUtils.isEmpty(item.id)) {
                continue;
            }
            if (TextUtils.isEmpty(item.localCoverPath) || !new File(item.localCoverPath).exists()) {
                item.localCoverPath = resolveLocalCoverPath("song", item.id);
            }
        }
        return items;
    }

    public File getSongCacheFile(MediaItemModel item) {
        File dir = new File(appContext.getCacheDir(), "songs");
        String extension = guessFileExtension(item == null ? null : item.path);
        String id = item == null ? "unknown" : item.id;
        return new File(dir, safeName(id) + extension);
    }

    public void cancelSongCache() {
        songCacheCancelled = true;
        if (currentSongCacheCall != null) {
            currentSongCacheCall.cancel();
            currentSongCacheCall = null;
        }
    }

    public File getCoverCacheFile(String type, String id) {
        if (TextUtils.isEmpty(type) || TextUtils.isEmpty(id)) {
            return null;
        }
        File dir = new File(appContext.getCacheDir(), "covers");
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        return new File(dir, safeName(type + "_" + id) + ".jpg");
    }

    public boolean isValidImageFile(@Nullable File file) {
        if (file == null || !file.exists() || file.length() <= 0L) {
            return false;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        return options.outWidth > 0 && options.outHeight > 0;
    }

    public String resolveLocalCoverPath(String type, String id) {
        File file = getCoverCacheFile(type, id);
        return (file != null && file.exists()) ? file.getAbsolutePath() : "";
    }

    public void cacheCover(String type, String id, StringCallback callback) {
        if (TextUtils.isEmpty(id)) {
            postError(callback, appContext.getString(R.string.cover_id_missing));
            return;
        }
        File target = getCoverCacheFile(type, id);
        if (target == null) {
            postError(callback, appContext.getString(R.string.cannot_create_cover_cache_dir));
            return;
        }
        if (target.exists() && isValidImageFile(target)) {
            postSuccess(callback, target.getAbsolutePath());
            return;
        }
        if (target.exists()) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
        }
        downloadToFile(buildCoverUrl(type, id), target, new CacheCallback() {
            @Override
            public void onProgress(int percent) {
            }

            @Override
            public void onSuccess(String value) {
                postSuccess(callback, value);
            }

            @Override
            public void onError(String message) {
                postError(callback, message);
            }
        });
    }

    public String buildCoverUrl(String type, String id) {
        String host = preferences.getString(KEY_HOST, "");
        String sid = preferences.getString(KEY_SID, "");
        String method = "getsongcover";
        if ("album".equals(type)) {
            method = "getalbumcover";
        } else if ("artist".equals(type)) {
            method = "getartistcover";
        } else if ("playlist".equals(type)) {
            method = "getplaylistcover";
        } else if ("folder".equals(type)) {
            method = "getfoldercover";
        }
        return host + "/webapi/AudioStation/cover.cgi?api=SYNO.AudioStation.Cover&version=3&method=" + method + "&id=" + Uri.encode(id) + "&_sid=" + Uri.encode(sid);
    }

    public String buildStreamUrl(String songId) {
        String host = preferences.getString(KEY_HOST, "");
        String sid = preferences.getString(KEY_SID, "");
        return host + "/webapi/AudioStation/stream.cgi?api=SYNO.AudioStation.Stream&version=2&method=stream&id=" + Uri.encode(songId) + "&_sid=" + Uri.encode(sid);
    }

    public List<MediaItemModel> readHistory() {
        List<MediaItemModel> items = new ArrayList<>();
        String raw = preferences.getString(KEY_HISTORY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json == null) {
                    continue;
                }
                MediaItemModel item = new MediaItemModel();
                item.id = json.optString("id");
                item.title = json.optString("title");
                item.subtitle = json.optString("subtitle");
                item.coverUrl = json.optString("coverUrl");
                item.type = json.optString("type");
                item.album = json.optString("album");
                item.artist = json.optString("artist");
                item.path = json.optString("path");
                item.streamUrl = json.optString("streamUrl");
                item.durationMs = json.optLong("durationMs");
                item.playCount = json.optInt("playCount", 0);
                item.localCoverPath = json.optString("localCoverPath");
                if (TextUtils.isEmpty(item.localCoverPath) || !new File(item.localCoverPath).exists()) {
                    item.localCoverPath = resolveLocalCoverPath(item.type, item.id);
                }
                items.add(item);
            }
        } catch (JSONException ignored) {
        }
        return items;
    }

    public void saveRootSongCache(List<MediaItemModel> items) {
        JSONArray array = new JSONArray();
        for (MediaItemModel item : items) {
            JSONObject json = new JSONObject();
            try {
                json.put("id", item.id);
                json.put("title", item.title);
                json.put("subtitle", item.subtitle);
                json.put("coverUrl", item.coverUrl);
                json.put("type", item.type);
                json.put("album", item.album);
                json.put("artist", item.artist);
                json.put("path", item.path);
                json.put("streamUrl", item.streamUrl);
                json.put("durationMs", item.durationMs);
                json.put("playCount", item.playCount);
                json.put("localCoverPath", item.localCoverPath == null ? "" : item.localCoverPath);
            } catch (JSONException ignored) {
            }
            array.put(json);
        }
        preferences.edit().putString(KEY_ROOT_SONG_CACHE, array.toString()).apply();
    }

    public List<MediaItemModel> readRootSongCache() {
        List<MediaItemModel> items = new ArrayList<>();
        String raw = preferences.getString(KEY_ROOT_SONG_CACHE, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json == null) {
                    continue;
                }
                MediaItemModel item = new MediaItemModel();
                item.id = json.optString("id");
                item.title = json.optString("title");
                item.subtitle = json.optString("subtitle");
                item.coverUrl = json.optString("coverUrl");
                item.type = json.optString("type", "song");
                item.album = json.optString("album");
                item.artist = json.optString("artist");
                item.path = json.optString("path");
                item.streamUrl = json.optString("streamUrl");
                item.durationMs = json.optLong("durationMs");
                item.playCount = json.optInt("playCount", 0);
                items.add(item);
            }
        } catch (JSONException ignored) {
        }
        return items;
    }

    public void pushHistory(MediaItemModel item) {
        if (item == null || TextUtils.isEmpty(item.id)) {
            return;
        }
        setLastPlayed(item.id, System.currentTimeMillis());
        int nextPlayCount = 1;
        for (MediaItemModel old : readHistory()) {
            if (TextUtils.equals(item.id, old.id)) {
                nextPlayCount = Math.max(1, old.playCount + 1);
                break;
            }
        }
        item.playCount = nextPlayCount;
        List<MediaItemModel> merged = new ArrayList<>();
        merged.add(item);
        for (MediaItemModel old : readHistory()) {
            if (!item.id.equals(old.id) && merged.size() < 40) {
                merged.add(old);
            }
        }
        JSONArray array = new JSONArray();
        for (MediaItemModel value : merged) {
            JSONObject json = new JSONObject();
            try {
                json.put("id", value.id);
                json.put("title", value.title);
                json.put("subtitle", value.subtitle);
                json.put("coverUrl", value.coverUrl);
                json.put("type", value.type);
                json.put("album", value.album);
                json.put("artist", value.artist);
                json.put("path", value.path);
                json.put("streamUrl", value.streamUrl);
                json.put("durationMs", value.durationMs);
                json.put("playCount", value.playCount);
            } catch (JSONException ignored) {
            }
            array.put(json);
        }
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply();
    }

    public int getPlayCount(String songId) {
        if (TextUtils.isEmpty(songId)) {
            return 0;
        }
        for (MediaItemModel item : readHistory()) {
            if (TextUtils.equals(songId, item.id)) {
                return Math.max(item.playCount, 0);
            }
        }
        return 0;
    }

    public void clearHistory() {
        preserveCachedLibrary();
        preferences.edit().putString(KEY_HISTORY, "[]").apply();
    }

    public List<MediaItemModel> readCachedSongs() {
        return readDownloadedSongs();
    }

    public List<MediaItemModel> readOfflineLibrary() {
        List<MediaItemModel> merged = new ArrayList<>();
        mergeUniqueSongs(merged, readMediaItemsFromPreference(KEY_CACHED_LIBRARY));
        mergeUniqueSongs(merged, readRootSongCache());
        mergeUniqueSongs(merged, readHistory());
        return merged;
    }

    private void upsertCachedSong(MediaItemModel item) {
        if (item == null || TextUtils.isEmpty(item.id)) {
            return;
        }
        List<MediaItemModel> items = readMediaItemsFromPreference(KEY_CACHED_LIBRARY);
        List<MediaItemModel> merged = new ArrayList<>();
        merged.add(item);
        for (MediaItemModel old : items) {
            if (!TextUtils.equals(old.id, item.id)) {
                merged.add(old);
            }
        }
        writeMediaItemsToPreference(KEY_CACHED_LIBRARY, merged);
    }

    private void preserveCachedLibrary() {
        List<MediaItemModel> merged = new ArrayList<>();
        mergeUniqueSongs(merged, readMediaItemsFromPreference(KEY_CACHED_LIBRARY));
        mergeUniqueSongs(merged, readRootSongCache());
        mergeUniqueSongs(merged, readHistory());
        List<MediaItemModel> cachedOnly = new ArrayList<>();
        for (MediaItemModel item : merged) {
            if (isSongCached(item)) {
                cachedOnly.add(item);
            }
        }
        writeMediaItemsToPreference(KEY_CACHED_LIBRARY, cachedOnly);
    }

    private void mergeUniqueSongs(List<MediaItemModel> target, List<MediaItemModel> source) {
        if (source == null) {
            return;
        }
        for (MediaItemModel item : source) {
            if (item == null || TextUtils.isEmpty(item.id) || !"song".equals(item.type)) {
                continue;
            }
            boolean exists = false;
            for (MediaItemModel existing : target) {
                if (TextUtils.equals(existing.id, item.id)) {
                    exists = true;
                    if (TextUtils.isEmpty(existing.title)) {
                        existing.title = item.title;
                    }
                    if (TextUtils.isEmpty(existing.artist)) {
                        existing.artist = item.artist;
                    }
                    if (TextUtils.isEmpty(existing.album)) {
                        existing.album = item.album;
                    }
                    if (TextUtils.isEmpty(existing.path)) {
                        existing.path = item.path;
                    }
                    if (existing.durationMs <= 0L) {
                        existing.durationMs = item.durationMs;
                    }
                    if (existing.playCount <= 0) {
                        existing.playCount = item.playCount;
                    }
                    break;
                }
            }
            if (!exists) {
                target.add(item);
            }
        }
    }

    private List<MediaItemModel> readMediaItemsFromPreference(String key) {
        List<MediaItemModel> items = new ArrayList<>();
        String raw = preferences.getString(key, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json == null) {
                    continue;
                }
                MediaItemModel item = new MediaItemModel();
                item.id = json.optString("id");
                item.title = json.optString("title");
                item.subtitle = json.optString("subtitle");
                item.coverUrl = json.optString("coverUrl");
                item.type = json.optString("type", "song");
                item.album = json.optString("album");
                item.artist = json.optString("artist");
                item.path = json.optString("path");
                item.streamUrl = json.optString("streamUrl");
                item.durationMs = json.optLong("durationMs");
                item.playCount = json.optInt("playCount", 0);
                items.add(item);
            }
        } catch (JSONException ignored) {
        }
        return items;
    }

    private void writeMediaItemsToPreference(String key, List<MediaItemModel> items) {
        JSONArray array = new JSONArray();
        for (MediaItemModel item : items) {
            JSONObject json = new JSONObject();
            try {
                json.put("id", item.id);
                json.put("title", item.title);
                json.put("subtitle", item.subtitle);
                json.put("coverUrl", item.coverUrl);
                json.put("type", item.type);
                json.put("album", item.album);
                json.put("artist", item.artist);
                json.put("path", item.path);
                json.put("streamUrl", item.streamUrl);
                json.put("durationMs", item.durationMs);
                json.put("playCount", item.playCount);
            } catch (JSONException ignored) {
            }
            array.put(json);
        }
        preferences.edit().putString(key, array.toString()).apply();
    }

    private HttpUrl buildListUrl(String host, String mode, String id, String parentType, String keyword, String sid, int offset, int limit, String sortBy, String sortDirection) {
        String path;
        String api;
        switch (mode) {
            case "songs":
            case "search":
                path = "/webapi/AudioStation/song.cgi";
                api = "SYNO.AudioStation.Song";
                break;
            case "artists":
                path = "/webapi/AudioStation/artist.cgi";
                api = "SYNO.AudioStation.Artist";
                break;
            case "albums":
                path = "/webapi/AudioStation/album.cgi";
                api = "SYNO.AudioStation.Album";
                break;
            case "playlists":
                path = "/webapi/AudioStation/playlist.cgi";
                api = "SYNO.AudioStation.Playlist";
                break;
            case "folders":
                path = "/webapi/AudioStation/folder.cgi";
                api = "SYNO.AudioStation.Folder";
                break;
            default:
                return null;
        }
        HttpUrl base = HttpUrl.parse(host + path);
        if (base == null) {
            return null;
        }
        HttpUrl.Builder builder = base.newBuilder()
                .addQueryParameter("api", api)
                .addQueryParameter("version", "3")
                .addQueryParameter("method", "list")
                .addQueryParameter("_sid", sid)
                .addQueryParameter("offset", String.valueOf(offset))
                .addQueryParameter("limit", String.valueOf(limit))
                .addQueryParameter("sort_by", TextUtils.isEmpty(sortBy) ? "title" : sortBy)
                .addQueryParameter("sort_direction", TextUtils.isEmpty(sortDirection) ? "ASC" : sortDirection);
        if ("songs".equals(mode) || "search".equals(mode) || "folders".equals(mode)) {
            builder.addQueryParameter("additional", "song_tag,song_audio");
        }
        if ("playlists".equals(mode)) {
            builder.addQueryParameter("library", "personal");
            builder.addQueryParameter("additional", "sharing_info");
        }
        if (!TextUtils.isEmpty(id)) {
            if ("albums".equals(mode) && "artist".equals(parentType)) {
                builder.addQueryParameter("artist", id);
            } else if ("songs".equals(mode) && "album".equals(parentType)) {
                builder.addQueryParameter("album", id);
            } else if ("songs".equals(mode) && "artist".equals(parentType)) {
                builder.addQueryParameter("artist", id);
            } else if ("folders".equals(mode) && "folder".equals(parentType)) {
                builder.addQueryParameter("id", id);
            } else {
                builder.addQueryParameter("id", id);
            }
        }
        if ("search".equals(mode) && !TextUtils.isEmpty(keyword)) {
            builder.addQueryParameter("keyword", keyword);
        }
        return builder.build();
    }

    private void searchSongs(String keyword, String sortBy, String sortDirection, ArrayCallback callback) {
        String host = preferences.getString(KEY_HOST, "");
        String sid = preferences.getString(KEY_SID, "");
        boolean ignoreCert = preferences.getBoolean(KEY_IGNORE_CERT, true);
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(sid)) {
            postError(callback, appContext.getString(R.string.login_expired_please_relogin));
            return;
        }
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            postSuccess(callback, new ArrayList<>(), false, 0);
            return;
        }
        String lowerKeyword = trimmed.toLowerCase(Locale.getDefault());
        fetchAllSongsPage(host, sid, ignoreCert, 0, 200, sortBy, sortDirection, new ArrayList<>(), new ArrayCallback() {
            @Override
            public void onSuccess(List<MediaItemModel> items, boolean hasMore, int total) {
                List<MediaItemModel> matches = new ArrayList<>();
                for (MediaItemModel item : items) {
                    if (item == null) {
                        continue;
                    }
                    String haystack = (item.title + " " + item.artist + " " + item.album).toLowerCase(Locale.getDefault());
                    if (haystack.contains(lowerKeyword)) {
                        matches.add(item);
                    }
                }
                postSuccess(callback, matches, false, matches.size());
            }

            @Override
            public void onError(String message) {
                fetchSongFallbackSearch(host, sid, ignoreCert, lowerKeyword, 0, 50, sortBy, sortDirection, callback);
            }
        });
    }

    private void fetchSongFallbackSearch(String host, String sid, boolean ignoreCert, String keyword, int offset, int limit, String sortBy, String sortDirection, ArrayCallback callback) {
        HttpUrl url = buildListUrl(host, "songs", null, null, null, sid, offset, limit, sortBy, sortDirection);
        if (url == null) {
            postError(callback, appContext.getString(R.string.search_failed));
            return;
        }
        executeJson(url, ignoreCert, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                JSONObject data = json.optJSONObject("data");
                JSONArray songs = data == null ? null : data.optJSONArray("songs");
                List<MediaItemModel> items = new ArrayList<>();
                String lower = keyword == null ? "" : keyword.toLowerCase();
                if (songs != null) {
                    for (int i = 0; i < songs.length(); i++) {
                        JSONObject song = songs.optJSONObject(i);
                        if (song == null) {
                            continue;
                        }
                        MediaItemModel item = parseSong(song);
                        String haystack = (item.title + " " + item.artist + " " + item.album).toLowerCase();
                        if (haystack.contains(lower)) {
                            items.add(item);
                        }
                    }
                }
                int pageCount = songs == null ? 0 : songs.length();
                int total = extractTotal("songs", data, pageCount);
                boolean hasNext = (offset + pageCount < total)
                        || (pageCount >= limit && pageCount > 0 && total <= pageCount);
                if (hasNext) {
                    fetchSongFallbackSearch(host, sid, ignoreCert, keyword, offset + pageCount, limit, sortBy, sortDirection, new ArrayCallback() {
                        @Override
                        public void onSuccess(List<MediaItemModel> nextItems, boolean hasMore, int totalAll) {
                            items.addAll(nextItems);
                            postSuccess(callback, items, false, items.size());
                        }

                        @Override
                        public void onError(String message) {
                            postError(callback, message);
                        }
                    });
                    return;
                }
                postSuccess(callback, items, false, items.size());
            }

            @Override
            public void onError(String message) {
                postError(callback, message);
            }
        });
    }

    private void fetchSongSearchPage(String host, String sid, boolean ignoreCert, int offset, int limit, String sortBy, String sortDirection, String lowerKeyword, List<MediaItemModel> matches, ArrayCallback callback) {
        HttpUrl url = buildListUrl(host, "songs", null, null, null, sid, offset, limit, sortBy, sortDirection);
        if (url == null) {
            postError(callback, appContext.getString(R.string.search_failed));
            return;
        }
        executeJson(url, ignoreCert, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!json.optBoolean("success")) {
                    postError(callback, extractSynologyError(json));
                    return;
                }
                JSONObject data = json.optJSONObject("data");
                JSONArray songs = data == null ? null : data.optJSONArray("songs");
                int pageCount = songs == null ? 0 : songs.length();
                if (songs != null) {
                    for (int i = 0; i < songs.length(); i++) {
                        JSONObject song = songs.optJSONObject(i);
                        if (song == null) {
                            continue;
                        }
                        MediaItemModel item = parseSong(song);
                        String haystack = (item.title + " " + item.artist + " " + item.album).toLowerCase(Locale.getDefault());
                        if (haystack.contains(lowerKeyword)) {
                            matches.add(item);
                        }
                    }
                }
                int total = extractTotal("songs", data, pageCount);
                boolean hasNext = (offset + pageCount < total)
                        || (pageCount >= limit && pageCount > 0 && total <= pageCount);
                if (hasNext) {
                    fetchSongSearchPage(host, sid, ignoreCert, offset + pageCount, limit, sortBy, sortDirection, lowerKeyword, matches, callback);
                    return;
                }
                postSuccess(callback, matches, false, matches.size());
            }

            @Override
            public void onError(String message) {
                if (offset == 0) {
                    fetchSongFallbackSearch(host, sid, ignoreCert, lowerKeyword, 0, 50, sortBy, sortDirection, callback);
                } else {
                    postError(callback, message);
                }
            }
        });
    }

    private void fetchAllSongsPage(String host, String sid, boolean ignoreCert, int offset, int limit, String sortBy, String sortDirection, List<MediaItemModel> songs, ArrayCallback callback) {
        HttpUrl url = buildListUrl(host, "songs", null, null, null, sid, offset, limit, sortBy, sortDirection);
        if (url == null) {
            postError(callback, appContext.getString(R.string.fetch_song_failed));
            return;
        }
        executeJson(url, ignoreCert, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!json.optBoolean("success")) {
                    postError(callback, extractSynologyError(json));
                    return;
                }
                JSONObject data = json.optJSONObject("data");
                JSONArray array = data == null ? null : data.optJSONArray("songs");
                int pageCount = array == null ? 0 : array.length();
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject song = array.optJSONObject(i);
                        if (song != null) {
                            songs.add(parseSong(song));
                        }
                    }
                }
                int total = extractTotal("songs", data, pageCount);
                boolean hasNext = (offset + pageCount < total)
                        || (pageCount >= limit && pageCount > 0 && total <= pageCount);
                if (hasNext && offset + pageCount < 20000) {
                    fetchAllSongsPage(host, sid, ignoreCert, offset + pageCount, limit, sortBy, sortDirection, songs, callback);
                    return;
                }
                postSuccess(callback, songs, false, songs.size());
            }

            @Override
            public void onError(String message) {
                postError(callback, message);
            }
        });
    }

    private void fetchPlaylistSongs(String host, String sid, boolean ignoreCert, String playlistId, int offset, int limit, ArrayCallback callback) {
        HttpUrl base = HttpUrl.parse(host + "/webapi/AudioStation/playlist.cgi");
        if (base == null) {
            postError(callback, appContext.getString(R.string.playlist_api_invalid));
            return;
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("api", "SYNO.AudioStation.Playlist")
                .addQueryParameter("version", "3")
                .addQueryParameter("method", "getinfo")
                .addQueryParameter("_sid", sid)
                .addQueryParameter("id", playlistId)
                .addQueryParameter("additional", "songs_song_tag,songs_song_audio")
                .build();
        executeJson(url, ignoreCert, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!json.optBoolean("success")) {
                    postError(callback, extractSynologyError(json));
                    return;
                }
                JSONObject data = json.optJSONObject("data");
                JSONArray playlists = data == null ? null : data.optJSONArray("playlists");
                JSONObject playlist = playlists == null ? null : playlists.optJSONObject(0);
                JSONObject additional = playlist == null ? null : playlist.optJSONObject("additional");
                JSONArray songs = additional == null ? null : additional.optJSONArray("songs");
                List<MediaItemModel> items = new ArrayList<>();
                if (songs != null) {
                    int end = Math.min(songs.length(), offset + limit);
                    for (int i = offset; i < end; i++) {
                        JSONObject song = songs.optJSONObject(i);
                        if (song != null) {
                            items.add(parseSong(song));
                        }
                    }
                }
                int total = songs == null ? 0 : songs.length();
                postSuccess(callback, items, offset + items.size() < total, total);
            }

            @Override
            public void onError(String message) {
                postError(callback, message);
            }
        });
    }

    public void fetchAllPlaylistSongs(String playlistId, ArrayCallback callback) {
        if (TextUtils.isEmpty(playlistId)) {
            postError(callback, appContext.getString(R.string.playlist_params_incomplete));
            return;
        }
        String host = preferences.getString(KEY_HOST, "");
        String sid = preferences.getString(KEY_SID, "");
        boolean ignoreCert = preferences.getBoolean(KEY_IGNORE_CERT, true);
        fetchAllPlaylistSongsPage(host, sid, ignoreCert, playlistId, 0, 200, new ArrayList<>(), callback);
    }

    private void fetchAllPlaylistSongsPage(String host, String sid, boolean ignoreCert, String playlistId, int offset, int limit, List<MediaItemModel> songs, ArrayCallback callback) {
        HttpUrl base = HttpUrl.parse(host + "/webapi/AudioStation/playlist.cgi");
        if (base == null) {
            postError(callback, appContext.getString(R.string.playlist_api_invalid));
            return;
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("api", "SYNO.AudioStation.Playlist")
                .addQueryParameter("version", "3")
                .addQueryParameter("method", "getinfo")
                .addQueryParameter("_sid", sid)
                .addQueryParameter("id", playlistId)
                .addQueryParameter("offset", String.valueOf(offset))
                .addQueryParameter("limit", String.valueOf(limit))
                .addQueryParameter("additional", "songs_song_tag,songs_song_audio")
                .build();
        executeJson(url, ignoreCert, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!json.optBoolean("success")) {
                    postError(callback, extractSynologyError(json));
                    return;
                }
                JSONObject data = json.optJSONObject("data");
                JSONArray playlists = data == null ? null : data.optJSONArray("playlists");
                JSONObject playlist = playlists == null ? null : playlists.optJSONObject(0);
                JSONObject additional = playlist == null ? null : playlist.optJSONObject("additional");
                JSONArray songsArray = additional == null ? null : additional.optJSONArray("songs");
                int pageCount = songsArray == null ? 0 : songsArray.length();
                if (songsArray != null) {
                    for (int i = 0; i < songsArray.length(); i++) {
                        JSONObject song = songsArray.optJSONObject(i);
                        if (song != null) {
                            songs.add(parseSong(song));
                        }
                    }
                }
                int total = songsArray == null ? 0 : songsArray.length();
                if (data != null) {
                    total = data.optInt("total", songs.size());
                }
                boolean hasNext = (offset + pageCount < total)
                        || (pageCount >= limit && pageCount > 0 && total <= pageCount);
                if (hasNext && pageCount > 0) {
                    fetchAllPlaylistSongsPage(host, sid, ignoreCert, playlistId, offset + pageCount, limit, songs, callback);
                    return;
                }
                postSuccess(callback, songs, false, songs.size());
            }

            @Override
            public void onError(String message) {
                postError(callback, message);
            }
        });
    }

    public void fetchPlaylistSongIds(String playlistId, StringListCallback callback) {
        if (TextUtils.isEmpty(playlistId)) {
            postError(callback, appContext.getString(R.string.playlist_params_incomplete));
            return;
        }
        String host = preferences.getString(KEY_HOST, "");
        String sid = preferences.getString(KEY_SID, "");
        boolean ignoreCert = preferences.getBoolean(KEY_IGNORE_CERT, true);
        HttpUrl base = HttpUrl.parse(host + "/webapi/AudioStation/playlist.cgi");
        if (base == null) {
            postError(callback, appContext.getString(R.string.playlist_api_invalid));
            return;
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("api", "SYNO.AudioStation.Playlist")
                .addQueryParameter("version", "3")
                .addQueryParameter("method", "getinfo")
                .addQueryParameter("_sid", sid)
                .addQueryParameter("id", playlistId)
                .addQueryParameter("additional", "songs_song_tag")
                .build();
        executeJson(url, ignoreCert, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!json.optBoolean("success")) {
                    postError(callback, extractSynologyError(json));
                    return;
                }
                JSONObject data = json.optJSONObject("data");
                JSONArray playlists = data == null ? null : data.optJSONArray("playlists");
                JSONObject playlist = playlists == null ? null : playlists.optJSONObject(0);
                JSONObject additional = playlist == null ? null : playlist.optJSONObject("additional");
                JSONArray songs = additional == null ? null : additional.optJSONArray("songs");
                List<String> ids = new ArrayList<>();
                if (songs != null) {
                    for (int i = 0; i < songs.length(); i++) {
                        JSONObject song = songs.optJSONObject(i);
                        if (song != null) {
                            String id = song.optString("id");
                            if (!TextUtils.isEmpty(id)) {
                                ids.add(id);
                            }
                        }
                    }
                }
                postSuccess(callback, ids);
            }

            @Override
            public void onError(String message) {
                postError(callback, message);
            }
        });
    }

    private void updatePlaylistSongs(String playlistId, int offset, int limit, String songsValue, StringCallback callback) {
        String host = preferences.getString(KEY_HOST, "");
        String sid = preferences.getString(KEY_SID, "");
        boolean ignoreCert = preferences.getBoolean(KEY_IGNORE_CERT, true);
        HttpUrl base = HttpUrl.parse(host + "/webapi/AudioStation/playlist.cgi");
        if (base == null) {
            postError(callback, appContext.getString(R.string.playlist_api_invalid));
            return;
        }
        HttpUrl.Builder builder = base.newBuilder()
                .addQueryParameter("api", "SYNO.AudioStation.Playlist")
                .addQueryParameter("version", "3")
                .addQueryParameter("method", "updatesongs")
                .addQueryParameter("_sid", sid)
                .addQueryParameter("id", playlistId)
                .addQueryParameter("offset", String.valueOf(offset))
                .addQueryParameter("limit", String.valueOf(limit));
        if (songsValue != null) {
            builder.addQueryParameter("songs", songsValue);
        }
        executeJsonOnce(builder.build(), ignoreCert, new JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (!json.optBoolean("success")) {
                    postError(callback, extractSynologyError(json));
                    return;
                }
                postSuccess(callback, playlistId);
            }

            @Override
            public void onError(String message) {
                postError(callback, message);
            }
        });
    }

    private String joinSongIds(List<String> songIds) {
        StringBuilder builder = new StringBuilder();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (songIds == null) {
            return "";
        }
        for (String songId : songIds) {
            if (TextUtils.isEmpty(songId) || seen.contains(songId)) {
                continue;
            }
            seen.add(songId);
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(songId);
        }
        return builder.toString();
    }

    private List<String> normalizeSongIds(List<String> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String songId : songIds) {
            if (!TextUtils.isEmpty(songId)) {
                seen.add(songId);
            }
        }
        return new ArrayList<>(seen);
    }

    private List<MediaItemModel> parseList(String mode, JSONObject data) {
        JSONArray array = null;
        if (data != null) {
            switch (mode) {
                case "songs":
                case "search":
                    array = data.optJSONArray("songs");
                    break;
                case "artists":
                    array = data.optJSONArray("artists");
                    break;
                case "albums":
                    array = data.optJSONArray("albums");
                    break;
                case "playlists":
                    array = data.optJSONArray("playlists");
                    break;
                case "folders":
                    array = data.optJSONArray("items");
                    if (array == null) {
                        array = data.optJSONArray("folders");
                    }
                    break;
            }
        }
        List<MediaItemModel> items = new ArrayList<>();
        if (array == null) {
            return items;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            if ("artists".equals(mode)) {
                items.add(parseArtist(entry));
            } else if ("albums".equals(mode)) {
                items.add(parseAlbum(entry));
            } else if ("playlists".equals(mode)) {
                items.add(parsePlaylist(entry));
            } else if ("folders".equals(mode)) {
                items.add(parseFolderItem(entry));
            } else {
                items.add(parseSong(entry));
            }
        }
        return items;
    }

    private int extractTotal(String mode, JSONObject data, int fallback) {
        return data == null ? fallback : data.optInt("total", fallback);
    }

    private MediaItemModel parseSong(JSONObject song) {
        MediaItemModel item = new MediaItemModel();
        item.id = song.optString("id");
        item.title = song.optString("title", appContext.getString(R.string.unknown_song));
        item.type = "song";
        item.raw = song;
        JSONObject additional = song.optJSONObject("additional");
        JSONObject tag = additional == null ? null : additional.optJSONObject("song_tag");
        JSONObject audio = additional == null ? null : additional.optJSONObject("song_audio");
        String artist = tag == null ? "" : tag.optString("artist", "");
        String album = tag == null ? "" : tag.optString("album", "");
        if (TextUtils.isEmpty(artist)) {
            artist = song.optString("artist", song.optString("artist_name", ""));
        }
        if (TextUtils.isEmpty(album)) {
            album = song.optString("album", song.optString("album_name", ""));
        }
        item.artist = TextUtils.isEmpty(artist) ? appContext.getString(R.string.unknown_artist) : artist;
        item.album = TextUtils.isEmpty(album) ? appContext.getString(R.string.unknown_album) : album;
        item.subtitle = item.artist + " · " + item.album;
        item.coverUrl = buildCoverUrl("song", item.id);
        item.streamUrl = buildStreamUrl(item.id);
        item.durationMs = audio == null ? 0L : audio.optLong("duration", 0L) * 1000L;
        item.path = song.optString("path", "");
        return item;
    }

    private MediaItemModel parseArtist(JSONObject artist) {
        MediaItemModel item = new MediaItemModel();
        item.id = artist.optString("name", artist.optString("id"));
        item.title = artist.optString("name", appContext.getString(R.string.unknown_artist));
        item.type = "artist";
        item.subtitle = appContext.getString(R.string.artists);
        item.coverUrl = buildCoverUrl("artist", item.id);
        item.raw = artist;
        return item;
    }

    private MediaItemModel parseAlbum(JSONObject album) {
        MediaItemModel item = new MediaItemModel();
        item.id = album.optString("id", album.optString("name"));
        item.title = album.optString("name", appContext.getString(R.string.unknown_album));
        item.artist = album.optString("artist", "");
        item.album = item.title;
        item.type = "album";
        item.subtitle = TextUtils.isEmpty(item.artist) ? appContext.getString(R.string.album) : item.artist;
        item.coverUrl = buildCoverUrl("album", item.id);
        item.raw = album;
        return item;
    }

    private MediaItemModel parsePlaylist(JSONObject playlist) {
        MediaItemModel item = new MediaItemModel();
        item.id = playlist.optString("id");
        item.title = displayPlaylistTitle(playlist.optString("name", playlist.optString("id", appContext.getString(R.string.playlist))), item.id);
        item.type = "playlist";
        item.subtitle = appContext.getString(R.string.playlist);
        item.coverUrl = buildCoverUrl("playlist", item.id);
        item.raw = playlist;
        return item;
    }

    private String displayPlaylistTitle(String name, String id) {
        String raw = TextUtils.isEmpty(name) ? id : name;
        if (TextUtils.isEmpty(raw)) {
            return appContext.getString(R.string.playlist);
        }
        if ("__SYNO_AUDIO_SHARED_SONGS__".equals(raw) || "__SYNO_AUDIO_SHARED_SONGS__".equals(id)) {
            return appContext.getString(R.string.synology_public_playlist);
        }
        return raw;
    }

    private MediaItemModel parseFolder(JSONObject folder) {
        MediaItemModel item = new MediaItemModel();
        item.id = folder.optString("id", folder.optString("path"));
        item.title = folder.optString("name", folder.optString("path", appContext.getString(R.string.folder)));
        item.type = "folder";
        item.path = folder.optString("path", "");
        item.subtitle = TextUtils.isEmpty(item.path) ? appContext.getString(R.string.folder) : item.path;
        item.coverUrl = buildCoverUrl("folder", item.id);
        item.raw = folder;
        return item;
    }

    private MediaItemModel parseFolderItem(JSONObject entry) {
        String type = entry.optString("type", "").toLowerCase();
        boolean isFolder = "folder".equals(type)
                || "dir".equals(type)
                || "directory".equals(type)
                || entry.optBoolean("isdir", false)
                || entry.optBoolean("is_folder", false);
        if (isFolder) {
            return parseFolder(entry);
        }
        JSONObject additional = entry.optJSONObject("additional");
        if (additional != null && (additional.has("song_tag") || additional.has("song_audio"))) {
            return parseSong(entry);
        }
        if (entry.has("name") && !entry.has("title")) {
            return parseFolder(entry);
        }
        return parseSong(entry);
    }

    private List<LyricLine> parseLyrics(String rawLyrics) {
        List<LyricLine> lines = new ArrayList<>();
        if (TextUtils.isEmpty(rawLyrics)) {
            LyricLine line = new LyricLine();
            line.primary = appContext.getString(R.string.no_lyrics);
            line.secondary = "";
            line.timeMs = 0L;
            lines.add(line);
            return lines;
        }
        String[] rows = rawLyrics.replace("\r", "").split("\n");
        for (String row : rows) {
            if (TextUtils.isEmpty(row.trim())) {
                continue;
            }
            int end = row.indexOf(']');
            if (row.startsWith("[") && end > 0) {
                String time = row.substring(1, end).trim();
                if (!isTimestampTag(time)) {
                    continue;
                }
                String content = row.substring(end + 1).trim();
                if (TextUtils.isEmpty(content) || isLyricsHintLine(content)) {
                    continue;
                }
                long timeMs = parseTimestamp(time);
                String[] parts = content.split(" / ", 2);
                String primary = parts.length > 0 ? parts[0].trim() : "";
                String inlineSecondary = parts.length > 1 ? parts[1].trim() : "";
                LyricLine line = findLyricLineByTime(lines, timeMs);
                if (line == null) {
                    line = new LyricLine();
                    line.timeMs = timeMs;
                    line.primary = primary;
                    line.secondary = inlineSecondary;
                    lines.add(line);
                    continue;
                }
                if (!TextUtils.isEmpty(primary)) {
                    if (TextUtils.isEmpty(line.primary)) {
                        line.primary = primary;
                    } else if (!primary.equals(line.primary)) {
                        line.secondary = appendSecondary(line.secondary, primary);
                    }
                }
                if (!TextUtils.isEmpty(inlineSecondary)) {
                    line.secondary = appendSecondary(line.secondary, inlineSecondary);
                }
            }
        }
        if (lines.isEmpty()) {
            LyricLine line = new LyricLine();
            line.primary = rawLyrics;
            line.secondary = "";
            line.timeMs = 0L;
            lines.add(line);
        }
        return lines;
    }

    private long parseTimestamp(String value) {
        try {
            String[] minuteSecond = value.split(":");
            int minute = Integer.parseInt(minuteSecond[0]);
            float second = Float.parseFloat(minuteSecond[1]);
            return (long) ((minute * 60f + second) * 1000f);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private boolean isTimestampTag(String tag) {
        if (TextUtils.isEmpty(tag)) {
            return false;
        }
        int colon = tag.indexOf(':');
        if (colon <= 0 || colon >= tag.length() - 1) {
            return false;
        }
        try {
            Integer.parseInt(tag.substring(0, colon).trim());
            Float.parseFloat(tag.substring(colon + 1).trim());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isLyricsHintLine(String content) {
        if (TextUtils.isEmpty(content)) {
            return true;
        }
        String text = content.trim();
        return text.contains("歌词标注")
                || text.contains("以下歌词")
                || text.contains("由AI工具");
    }

    private LyricLine findLyricLineByTime(List<LyricLine> lines, long timeMs) {
        for (LyricLine line : lines) {
            if (line != null && line.timeMs == timeMs) {
                return line;
            }
        }
        return null;
    }

    private String appendSecondary(String current, String incoming) {
        if (TextUtils.isEmpty(incoming)) {
            return current;
        }
        if (TextUtils.isEmpty(current)) {
            return incoming;
        }
        if (current.contains(incoming)) {
            return current;
        }
        return current + "\n" + incoming;
    }

    private static final int NETWORK_RETRY_MAX = 2;
    private static final long NETWORK_RETRY_BASE_DELAY_MS = 600L;

    private void executeJson(HttpUrl url, boolean ignoreCert, JsonCallback callback) {
        executeJsonWithRetry(url, ignoreCert, callback, 0);
    }

    private void executeJsonOnce(HttpUrl url, boolean ignoreCert, JsonCallback callback) {
        Request request = new Request.Builder().url(url).get().build();
        buildClient(ignoreCert).newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                postError(callback, e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    postError(callback, appContext.getString(R.string.network_error, String.valueOf(response.code())));
                    return;
                }
                String body = response.body() == null ? "" : response.body().string();
                try {
                    postSuccess(callback, new JSONObject(body));
                } catch (JSONException e) {
                    postError(callback, appContext.getString(R.string.response_data_unparseable));
                }
            }
        });
    }

    private void executeJsonWithRetry(HttpUrl url, boolean ignoreCert, JsonCallback callback, int attempt) {
        Request request = new Request.Builder().url(url).get().build();
        buildClient(ignoreCert).newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (attempt < NETWORK_RETRY_MAX && shouldRetryNetworkFailure(e)) {
                    scheduleJsonRetry(url, ignoreCert, callback, attempt + 1);
                    return;
                }
                postError(callback, e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    if (attempt < NETWORK_RETRY_MAX && shouldRetryStatus(response.code())) {
                        response.close();
                        scheduleJsonRetry(url, ignoreCert, callback, attempt + 1);
                        return;
                    }
                    postError(callback, appContext.getString(R.string.network_error, String.valueOf(response.code())));
                    return;
                }
                String body = response.body() == null ? "" : response.body().string();
                try {
                    postSuccess(callback, new JSONObject(body));
                } catch (JSONException e) {
                    postError(callback, appContext.getString(R.string.response_data_unparseable));
                }
            }
        });
    }

    private void scheduleJsonRetry(HttpUrl url, boolean ignoreCert, JsonCallback callback, int attempt) {
        long delayMs = retryDelayMs(attempt);
        mainHandler.postDelayed(() -> executeJsonWithRetry(url, ignoreCert, callback, attempt), delayMs);
    }

    private void scheduleDownloadRetry(String url, Object target, CacheCallback callback, boolean cancelAware, int attempt, boolean documentTarget) {
        long delayMs = retryDelayMs(attempt);
        mainHandler.postDelayed(() -> {
            if (documentTarget) {
                downloadToDocumentFile(url, (DocumentFile) target, callback, cancelAware, attempt);
            } else {
                downloadToFile(url, (File) target, callback, cancelAware, attempt);
            }
        }, delayMs);
    }

    private void schedulePlaylistAddRetry(String playlistId, List<String> songIds, StringCallback callback, int attempt) {
        long delayMs = retryDelayMs(attempt);
        mainHandler.postDelayed(() -> addSongsToPlaylistWithRetry(playlistId, songIds, callback, attempt), delayMs);
    }

    private boolean shouldRetryStatus(int code) {
        return code == 408 || code == 425 || code == 429 || code == 500 || code == 502 || code == 503 || code == 504;
    }

    private boolean shouldRetryNetworkFailure(@Nullable IOException e) {
        if (e == null) {
            return false;
        }
        if (e instanceof SocketTimeoutException
                || e instanceof SocketException
                || e instanceof EOFException
                || e instanceof InterruptedIOException) {
            return true;
        }
        String message = e.getMessage();
        if (TextUtils.isEmpty(message)) {
            return false;
        }
        String lower = message.toLowerCase(Locale.US);
        return lower.contains("connection closed")
                || lower.contains("unexpected end of stream")
                || lower.contains("stream was reset")
                || lower.contains("broken pipe")
                || lower.contains("eof")
                || lower.contains("timeout")
                || lower.contains("socket closed")
                || lower.contains("connection rest")
                || lower.contains("connection reset");
    }

    private boolean shouldRetryTransientMessage(@Nullable String message) {
        if (TextUtils.isEmpty(message)) {
            return false;
        }
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("connection closed")
                || lower.contains("connection rest")
                || lower.contains("connection reset")
                || lower.contains("unexpected end of stream")
                || lower.contains("stream was reset")
                || lower.contains("broken pipe")
                || lower.contains("socket closed")
                || lower.contains("eof")
                || lower.contains("timeout")) {
            return true;
        }
        return lower.matches(".*(408|425|429|500|502|503|504).*");
    }

    private long retryDelayMs(int attempt) {
        return NETWORK_RETRY_BASE_DELAY_MS * (1L << Math.max(0, attempt - 1));
    }

    private Call downloadToDocumentFile(String url, DocumentFile target, CacheCallback callback) {
        return downloadToDocumentFile(url, target, callback, false, 0);
    }

    private Call downloadToDocumentFile(String url, DocumentFile target, CacheCallback callback, boolean cancelAware) {
        return downloadToDocumentFile(url, target, callback, cancelAware, 0);
    }

    private Call downloadToDocumentFile(String url, DocumentFile target, CacheCallback callback, boolean cancelAware, int attempt) {
        if (target.exists() && target.length() > 0L) {
            postProgress(callback, 100);
            postSuccess(callback, target.getUri().toString());
            return null;
        }
        boolean ignoreCert = preferences.getBoolean(KEY_IGNORE_CERT, true);
        Request request = new Request.Builder().url(url).get().build();
        Call call = buildClient(ignoreCert).newCall(request);
        if (cancelAware) {
            currentSongCacheCall = call;
        }
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled() || (cancelAware && songCacheCancelled)) {
                    return;
                }
                if (attempt < NETWORK_RETRY_MAX && shouldRetryNetworkFailure(e)) {
                    scheduleDownloadRetry(url, target, callback, cancelAware, attempt + 1, true);
                    return;
                }
                postError(callback, e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (call.isCanceled() || (cancelAware && songCacheCancelled)) {
                    return;
                }
                if (!response.isSuccessful()) {
                    if (attempt < NETWORK_RETRY_MAX && shouldRetryStatus(response.code())) {
                        response.close();
                        scheduleDownloadRetry(url, target, callback, cancelAware, attempt + 1, true);
                        return;
                    }
                    postError(callback, appContext.getString(R.string.download_failed, String.valueOf(response.code())));
                    return;
                }
                if (response.body() == null) {
                    postError(callback, appContext.getString(R.string.response_content_empty));
                    return;
                }
                ContentResolver resolver = appContext.getContentResolver();
                try (InputStream inputStream = response.body().byteStream();
                     OutputStream outputStream = resolver.openOutputStream(target.getUri(), "w")) {
                    if (outputStream == null) {
                        postError(callback, appContext.getString(R.string.cannot_write_cache_file));
                        return;
                    }
                    byte[] buffer = new byte[8192];
                    int read;
                    long totalBytes = response.body().contentLength();
                    long downloadedBytes = 0L;
                    int lastPercent = -1;
                    while ((read = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                        downloadedBytes += read;
                        if (totalBytes > 0L) {
                            int percent = (int) ((downloadedBytes * 100L) / totalBytes);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                postProgress(callback, percent);
                            }
                        }
                    }
                    outputStream.flush();
                    postProgress(callback, 100);
                    postSuccess(callback, target.getUri().toString());
                } catch (IOException e) {
                    target.delete();
                    if (attempt < NETWORK_RETRY_MAX && shouldRetryNetworkFailure(e)) {
                        scheduleDownloadRetry(url, target, callback, cancelAware, attempt + 1, true);
                        return;
                    }
                    postError(callback, e.getMessage());
                } catch (Throwable t) {
                    target.delete();
                    postError(callback, appContext.getString(R.string.cannot_write_cache_file));
                }
            }
        });
        return call;
    }

    private Call downloadToFile(String url, File target, CacheCallback callback) {
        return downloadToFile(url, target, callback, false, 0);
    }

    private Call downloadToFile(String url, File target, CacheCallback callback, boolean cancelAware) {
        return downloadToFile(url, target, callback, cancelAware, 0);
    }

    private Call downloadToFile(String url, File target, CacheCallback callback, boolean cancelAware, int attempt) {
        if (target.exists() && target.length() > 0L) {
            postProgress(callback, 100);
            postSuccess(callback, target.getAbsolutePath());
            return null;
        }
        boolean ignoreCert = preferences.getBoolean(KEY_IGNORE_CERT, true);
        Request request = new Request.Builder().url(url).get().build();
        Call call = buildClient(ignoreCert).newCall(request);
        if (cancelAware) {
            currentSongCacheCall = call;
        }
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled() || (cancelAware && songCacheCancelled)) {
                    return;
                }
                if (attempt < NETWORK_RETRY_MAX && shouldRetryNetworkFailure(e)) {
                    scheduleDownloadRetry(url, target, callback, cancelAware, attempt + 1, false);
                    return;
                }
                postError(callback, e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (call.isCanceled() || (cancelAware && songCacheCancelled)) {
                    return;
                }
                if (!response.isSuccessful()) {
                    if (attempt < NETWORK_RETRY_MAX && shouldRetryStatus(response.code())) {
                        response.close();
                        scheduleDownloadRetry(url, target, callback, cancelAware, attempt + 1, false);
                        return;
                    }
                    postError(callback, appContext.getString(R.string.download_failed, String.valueOf(response.code())));
                    return;
                }
                if (response.body() == null) {
                    postError(callback, appContext.getString(R.string.response_content_empty));
                    return;
                }
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    postError(callback, appContext.getString(R.string.cannot_create_song_cache_dir));
                    return;
                }
                try (InputStream inputStream = response.body().byteStream();
                     FileOutputStream outputStream = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    long totalBytes = response.body().contentLength();
                    long downloadedBytes = 0L;
                    int lastPercent = -1;
                    while ((read = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, read);
                        downloadedBytes += read;
                        if (totalBytes > 0L) {
                            int percent = (int) ((downloadedBytes * 100L) / totalBytes);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                postProgress(callback, percent);
                            }
                        }
                    }
                    outputStream.flush();
                    postProgress(callback, 100);
                    postSuccess(callback, target.getAbsolutePath());
                } catch (IOException e) {
                    if (target.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        target.delete();
                    }
                    if (attempt < NETWORK_RETRY_MAX && shouldRetryNetworkFailure(e)) {
                        scheduleDownloadRetry(url, target, callback, cancelAware, attempt + 1, false);
                        return;
                    }
                    postError(callback, e.getMessage());
                }
            }
        });
        return call;
    }

    private String safeName(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String cleaned = value.trim()
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", " ")
                .replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^\\.+", "");
        cleaned = cleaned.replaceAll("\\.+$", "");
        cleaned = cleaned.trim();
        return cleaned.isEmpty() ? "_" : cleaned;
    }

    private String guessFileExtension(String path) {
        if (TextUtils.isEmpty(path)) {
            return ".mp3";
        }
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == path.length() - 1) {
            return ".mp3";
        }
        String extension = path.substring(dotIndex).toLowerCase();
        if (extension.length() > 8) {
            return ".mp3";
        }
        return extension;
    }

    private String guessMimeType(String path) {
        String extension = guessFileExtension(path);
        if (".flac".equals(extension)) {
            return "audio/flac";
        }
        if (".ogg".equals(extension)) {
            return "audio/ogg";
        }
        if (".aac".equals(extension)) {
            return "audio/aac";
        }
        if (".m4a".equals(extension)) {
            return "audio/mp4";
        }
        if (".wav".equals(extension)) {
            return "audio/wav";
        }
        return "audio/mpeg";
    }

    private long dirSize(@Nullable DocumentFile file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return Math.max(0L, file.length());
        }
        long sum = 0L;
        for (DocumentFile child : file.listFiles()) {
            sum += dirSize(child);
        }
        return sum;
    }

    private OkHttpClient buildClient(boolean ignoreCert) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS);
        if (ignoreCert) {
            try {
                X509TrustManager trustManager = trustAllManager();
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
                SSLSocketFactory socketFactory = sslContext.getSocketFactory();
                builder.sslSocketFactory(socketFactory, trustManager);
                builder.hostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });
            } catch (Exception ignored) {
            }
        }
        return builder.build();
    }

    private X509TrustManager trustAllManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    private String extractSynologyError(JSONObject json) {
        JSONObject error = json.optJSONObject("error");
        int code = error == null ? -1 : error.optInt("code", -1);
        switch (code) {
            case 400:
                return appContext.getString(R.string.error_invalid_param);
            case 401:
                return appContext.getString(R.string.error_auth_failed);
            case 402:
                return appContext.getString(R.string.error_permission_denied);
            case 404:
                return appContext.getString(R.string.error_api_not_found);
            case 406:
                return appContext.getString(R.string.error_session_timeout);
            default:
                return code == -1 ? appContext.getString(R.string.request_failed) : appContext.getString(R.string.request_failed_with_code, code);
        }
    }

    private void postSuccess(JsonCallback callback, JSONObject json) {
        mainHandler.post(() -> callback.onSuccess(json));
    }

    private void postError(JsonCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message == null ? appContext.getString(R.string.request_failed) : message));
    }

    private void postSuccess(ArrayCallback callback, List<MediaItemModel> items, boolean hasMore, int total) {
        mainHandler.post(() -> callback.onSuccess(items, hasMore, total));
    }

    private void postError(ArrayCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message == null ? appContext.getString(R.string.request_failed) : message));
    }

    private void postSuccess(LyricsCallback callback, List<LyricLine> lines) {
        mainHandler.post(() -> callback.onSuccess(lines));
    }

    private void postError(LyricsCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message == null ? appContext.getString(R.string.request_failed) : message));
    }

    private void postSuccess(StringListCallback callback, List<String> values) {
        mainHandler.post(() -> callback.onSuccess(values));
    }

    private void postError(StringListCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message == null ? appContext.getString(R.string.request_failed) : message));
    }

    private void postSuccess(StringCallback callback, String value) {
        mainHandler.post(() -> callback.onSuccess(value));
    }

    private void postError(StringCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message == null ? appContext.getString(R.string.request_failed) : message));
    }

    private void postProgress(CacheCallback callback, int percent) {
        mainHandler.post(() -> callback.onProgress(percent));
    }

    private void postSuccess(CacheCallback callback, String value) {
        mainHandler.post(() -> callback.onSuccess(value));
    }

    private void postError(CacheCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message == null ? appContext.getString(R.string.request_failed) : message));
    }
}
