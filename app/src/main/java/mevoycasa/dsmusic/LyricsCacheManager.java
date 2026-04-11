package mevoycasa.dsmusic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class LyricsCacheManager {
    private static final String TAG = "LyricsCacheManager";
    private static final String CACHE_DIR_NAME = "lyrics_cache";
    private static final String CACHE_META_FILE = "cache_meta.json";
    
    private static LyricsCacheManager instance;
    
    private final Context context;
    private final File cacheDir;
    private final Handler mainHandler;
    private final Map<String, CachedLyrics> memoryCache;
    
    private NetworkCallback networkCallback;
    private boolean isNetworkAvailable = true;
    
    private final Map<String, RetryTask> pendingRetries;
    
    public static class CachedLyrics {
        public String songId;
        public List<ApiClient.LyricLine> lyrics;
        public long timestamp;
        public boolean hasError;
        public String errorMessage;
        
        public CachedLyrics(String songId) {
            this.songId = songId;
            this.timestamp = System.currentTimeMillis();
            this.hasError = false;
        }
    }
    
    private static class RetryTask {
        String songId;
        ApiClient apiClient;
        LyricsCallback callback;
        AtomicInteger retryCount;
        long nextRetryTime;
        Runnable runnable;
        
        RetryTask(String songId, ApiClient apiClient, LyricsCallback callback) {
            this.songId = songId;
            this.apiClient = apiClient;
            this.callback = callback;
            this.retryCount = new AtomicInteger(0);
        }
    }
    
    public interface LyricsCallback {
        void onSuccess(List<ApiClient.LyricLine> lyrics);
        void onError(String message);
    }
    
    private LyricsCacheManager(Context context) {
        this.context = context.getApplicationContext();
        this.cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.memoryCache = new HashMap<>();
        this.pendingRetries = new HashMap<>();
        
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        
        loadCacheMeta();
        registerNetworkListener();
    }
    
    public static synchronized LyricsCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new LyricsCacheManager(context);
        }
        return instance;
    }
    
    private void registerNetworkListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                networkCallback = new NetworkCallback();
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                cm.registerNetworkCallback(request, networkCallback);
            }
        } else {
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    checkNetworkStatus();
                }
            }, filter);
        }
        checkNetworkStatus();
    }
    
    private void checkNetworkStatus() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                isNetworkAvailable = capabilities != null && 
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            } else {
                android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                isNetworkAvailable = info != null && info.isConnected();
            }
        }
        if (isNetworkAvailable) {
            retryPendingRequests();
        }
    }
    
    private class NetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            isNetworkAvailable = true;
            retryPendingRequests();
        }
        
        @Override
        public void onLost(Network network) {
            isNetworkAvailable = false;
        }
    }
    
    public void fetchLyrics(String songId, ApiClient apiClient, LyricsCallback callback) {
        if (songId == null || songId.isEmpty()) {
            callback.onError(context.getString(R.string.invalid_song_id));
            return;
        }
        
        CachedLyrics cached = memoryCache.get(songId);
        if (cached == null) {
            cached = loadFromDisk(songId);
        }
        
        if (cached != null && !cached.hasError && cached.lyrics != null && !cached.lyrics.isEmpty()) {
            callback.onSuccess(cached.lyrics);
            if (isNetworkAvailable && System.currentTimeMillis() - cached.timestamp > 3600000) {
                refreshInBackground(songId, apiClient);
            }
            return;
        }
        
        if (!isNetworkAvailable) {
            if (cached != null && cached.lyrics != null && !cached.lyrics.isEmpty()) {
                callback.onSuccess(cached.lyrics);
            } else if (cached != null && cached.hasError) {
                callback.onError(cached.errorMessage);
            } else {
                callback.onError(context.getString(R.string.network_unavailable_retry_later));
            }
            return;
        }
        
        fetchFromNetwork(songId, apiClient, callback);
    }
    
    private void fetchFromNetwork(String songId, ApiClient apiClient, LyricsCallback callback) {
        apiClient.fetchLyrics(songId, new ApiClient.LyricsCallback() {
            @Override
            public void onSuccess(List<ApiClient.LyricLine> lyrics) {
                CachedLyrics cached = new CachedLyrics(songId);
                cached.lyrics = lyrics;
                saveToCache(cached);
                callback.onSuccess(lyrics);
                cancelRetry(songId);
            }
            
            @Override
            public void onError(String message) {
                CachedLyrics cached = memoryCache.get(songId);
                if (cached != null && cached.lyrics != null && !cached.lyrics.isEmpty()) {
                    callback.onSuccess(cached.lyrics);
                } else {
                    CachedLyrics errorCache = new CachedLyrics(songId);
                    errorCache.hasError = true;
                    errorCache.errorMessage = message;
                    saveToCache(errorCache);
                    callback.onError(message);
                }
                scheduleRetry(songId, apiClient, callback);
            }
        });
    }
    
    private void refreshInBackground(String songId, ApiClient apiClient) {
        apiClient.fetchLyrics(songId, new ApiClient.LyricsCallback() {
            @Override
            public void onSuccess(List<ApiClient.LyricLine> lyrics) {
                CachedLyrics cached = new CachedLyrics(songId);
                cached.lyrics = lyrics;
                saveToCache(cached);
            }
            
            @Override
            public void onError(String message) {
            }
        });
    }
    
    private void scheduleRetry(String songId, ApiClient apiClient, LyricsCallback callback) {
        RetryTask existing = pendingRetries.get(songId);
        if (existing != null) {
            if (existing.runnable != null) {
                mainHandler.removeCallbacks(existing.runnable);
            }
        }
        
        RetryTask task = new RetryTask(songId, apiClient, callback);
        pendingRetries.put(songId, task);
        
        scheduleNextRetry(task);
    }
    
    private void scheduleNextRetry(RetryTask task) {
        int retryCount = task.retryCount.getAndIncrement();
        if (retryCount >= 10) {
            pendingRetries.remove(task.songId);
            return;
        }
        
        long delay = (long) Math.min(30000 * Math.pow(2, retryCount), 600000);
        task.nextRetryTime = System.currentTimeMillis() + delay;
        
        task.runnable = () -> {
            if (isNetworkAvailable) {
                fetchFromNetwork(task.songId, task.apiClient, task.callback);
            } else {
                scheduleNextRetry(task);
            }
        };
        
        mainHandler.postDelayed(task.runnable, delay);
    }
    
    private void cancelRetry(String songId) {
        RetryTask task = pendingRetries.remove(songId);
        if (task != null && task.runnable != null) {
            mainHandler.removeCallbacks(task.runnable);
        }
    }
    
    private void retryPendingRequests() {
        List<RetryTask> tasks = new ArrayList<>(pendingRetries.values());
        for (RetryTask task : tasks) {
            if (task.runnable != null) {
                mainHandler.removeCallbacks(task.runnable);
            }
            fetchFromNetwork(task.songId, task.apiClient, task.callback);
        }
    }
    
    private void saveToCache(CachedLyrics cached) {
        memoryCache.put(cached.songId, cached);
        saveToDisk(cached);
        saveCacheMeta();
    }
    
    private CachedLyrics loadFromDisk(String songId) {
        try {
            File file = new File(cacheDir, songId + ".json");
            if (!file.exists()) {
                return null;
            }
            
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[(int) file.length()];
            fis.read(buffer);
            fis.close();
            
            JSONObject json = new JSONObject(new String(buffer, "UTF-8"));
            CachedLyrics cached = new CachedLyrics(songId);
            cached.timestamp = json.optLong("timestamp", System.currentTimeMillis());
            cached.hasError = json.optBoolean("hasError", false);
            cached.errorMessage = json.optString("errorMessage", "");
            
            if (!cached.hasError) {
                JSONArray lyricsArray = json.optJSONArray("lyrics");
                if (lyricsArray != null) {
                    cached.lyrics = new ArrayList<>();
                    for (int i = 0; i < lyricsArray.length(); i++) {
                        JSONObject lineJson = lyricsArray.getJSONObject(i);
                        ApiClient.LyricLine line = new ApiClient.LyricLine();
                        line.timeMs = lineJson.optLong("timeMs", 0);
                        line.primary = lineJson.optString("primary", "");
                        line.secondary = lineJson.optString("secondary", "");
                        cached.lyrics.add(line);
                    }
                }
            }
            
            return cached;
        } catch (Throwable e) {
            return null;
        }
    }
    
    private void saveToDisk(CachedLyrics cached) {
        try {
            JSONObject json = new JSONObject();
            json.put("timestamp", cached.timestamp);
            json.put("hasError", cached.hasError);
            json.put("errorMessage", cached.errorMessage != null ? cached.errorMessage : "");
            
            if (!cached.hasError && cached.lyrics != null) {
                JSONArray lyricsArray = new JSONArray();
                for (ApiClient.LyricLine line : cached.lyrics) {
                    JSONObject lineJson = new JSONObject();
                    lineJson.put("timeMs", line.timeMs);
                    lineJson.put("primary", line.primary != null ? line.primary : "");
                    lineJson.put("secondary", line.secondary != null ? line.secondary : "");
                    lyricsArray.put(lineJson);
                }
                json.put("lyrics", lyricsArray);
            }
            
            File file = new File(cacheDir, cached.songId + ".json");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(json.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Throwable e) {
        }
    }
    
    private void loadCacheMeta() {
        try {
            File file = new File(cacheDir, CACHE_META_FILE);
            if (!file.exists()) {
                return;
            }
            
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[(int) file.length()];
            fis.read(buffer);
            fis.close();
            
            JSONObject json = new JSONObject(new String(buffer, "UTF-8"));
            JSONArray idsArray = json.optJSONArray("songIds");
            if (idsArray != null) {
                for (int i = 0; i < idsArray.length(); i++) {
                    String songId = idsArray.getString(i);
                    CachedLyrics cached = loadFromDisk(songId);
                    if (cached != null) {
                        memoryCache.put(songId, cached);
                    }
                }
            }
        } catch (Throwable e) {
        }
    }
    
    private void saveCacheMeta() {
        try {
            JSONObject json = new JSONObject();
            JSONArray idsArray = new JSONArray();
            for (String songId : memoryCache.keySet()) {
                idsArray.put(songId);
            }
            json.put("songIds", idsArray);
            
            File file = new File(cacheDir, CACHE_META_FILE);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(json.toString().getBytes("UTF-8"));
            fos.close();
        } catch (Throwable e) {
        }
    }
    
    public void clearCache() {
        memoryCache.clear();
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    public int removeCachedLyricsBySongIds(Collection<String> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (String songId : new ArrayList<>(songIds)) {
            if (songId == null || songId.isEmpty()) {
                continue;
            }
            memoryCache.remove(songId);
            File file = new File(cacheDir, songId + ".json");
            if (file.exists() && file.delete()) {
                deleted++;
            }
        }
        saveCacheMeta();
        return deleted;
    }

    public boolean isNetworkAvailable() {
        return isNetworkAvailable;
    }
}
