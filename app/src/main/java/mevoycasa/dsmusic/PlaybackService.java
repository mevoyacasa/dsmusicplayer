package mevoycasa.dsmusic;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PlaybackService extends Service {

    private static final String TAG = "DSMusic.Service";
    private static final String CHANNEL_ID = "as_music_playback";
    private static final int NOTIFICATION_ID = 1001;

    private static PlaybackService serviceInstance;
    private static ExoPlayer sharedPlayer;
    private static AudioAttributes sharedAudioAttributes;
    private static NotificationManager notificationManager;
    private static boolean foregroundStarted = false;
    private static boolean uiActive = false;
    private static AudioManager audioManager;
    private static AudioFocusRequest audioFocusRequest;
    private static boolean hasAudioFocus = false;
    private static volatile boolean playbackIsPlaying = false;
    private static volatile String currentMediaIdCache = "";
    private static final String ACTION_PREVIOUS = "mevoycasa.dsmusic.ACTION_PREVIOUS";
    private static final String ACTION_PLAY_PAUSE = "mevoycasa.dsmusic.ACTION_PLAY_PAUSE";
    private static final String ACTION_NEXT = "mevoycasa.dsmusic.ACTION_NEXT";

    private PendingIntent actionPreviousIntent;
    private PendingIntent actionPlayPauseIntent;
    private PendingIntent actionNextIntent;
    private PendingIntent contentIntent;

    private static final Object QUEUE_LOCK = new Object();
    private static final List<ApiClient.MediaItemModel> nowPlayingQueue = new ArrayList<>();
    private static int nowPlayingIndex = -1;
    private static boolean shuffleEnabled = true;
    private static int repeatMode = 2; // 0 off, 1 all, 2 list loop (treated as all), keep compatible with MainActivity
    private static boolean repeatOneEnabled = false;
    private static String pendingForceNextSongId = "";
    private static final Random RNG = new Random();

    private static ApiClient apiClient;

    private static boolean cachingActive = false;
    private static String currentTitle = "";
    private static String currentArtist = "";
    private static String currentLyricLine = "";
    private static String currentCoverPath = "";
    private static long currentPositionMs = 0L;
    private static long currentDurationMs = 0L;
    private static int currentPercent = 0;
    private static String prefetchInFlightSongId = "";
    private static String lastPrefetchBaseSongId = "";
    private static PowerManager.WakeLock wakeLock;
    private BroadcastReceiver noisyReceiver;
    private BroadcastReceiver headsetReceiver;
    private boolean noisyReceiverRegistered = false;
    private boolean headsetReceiverRegistered = false;
    private static boolean resumeAfterNoisyEvent = false;
    private final Handler focusHandler = new Handler(Looper.getMainLooper());
    private HandlerThread serviceWorkThread;
    private Handler serviceWorkHandler;
    private int notificationActionToken = 0;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> {
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            hasAudioFocus = true;
            return;
        }
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            hasAudioFocus = false;
            runOnMainThread(() -> {
                if (sharedPlayer != null && sharedPlayer.isPlaying()) {
                    sharedPlayer.pause();
                }
            });
        }
    };

    public static void ensureStartedForPlayback(Context context) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, PlaybackService.class);
        boolean allowNotifications = notificationsAllowed(appContext);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 13+: if notification permission is denied, starting as a foreground-service
                // may crash the process because we cannot post the required notification.
                if (allowNotifications) {
                    ContextCompat.startForegroundService(appContext, intent);
                } else {
                    appContext.startService(intent);
                }
            } else {
                appContext.startService(intent);
            }
        } catch (Throwable ignored) {
            // Avoid crashing when background start is not allowed. Service will start next time from UI.
        }
    }

    public static ExoPlayer getPlayer(Context context) {
        if (sharedPlayer == null) {
            sharedPlayer = new ExoPlayer.Builder(context.getApplicationContext()).build();
        }
        return sharedPlayer;
    }

    public static void stopSession(Context context) {
        cachingActive = false;
        currentTitle = "";
        currentArtist = "";
        currentLyricLine = "";
        currentCoverPath = "";
        currentPositionMs = 0L;
        currentDurationMs = 0L;
        currentPercent = 0;
        resumeAfterNoisyEvent = false;
        playbackIsPlaying = false;
        currentMediaIdCache = "";
        if (sharedPlayer != null) {
            sharedPlayer.stop();
            sharedPlayer.clearMediaItems();
        }
        if (serviceInstance != null) {
            serviceInstance.abandonAudioFocus();
        }
        releaseWakeLockIfHeld();
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
            notificationManager = null;
        }
        Context appContext = context.getApplicationContext();
        appContext.stopService(new Intent(appContext, PlaybackService.class));
    }

    public static void updateCachingState(Context context, String title, String artist, String lyricLine, String coverPath, int percent) {
        cachingActive = true;
        currentTitle = safe(title);
        currentArtist = safe(artist);
        currentLyricLine = safe(lyricLine);
        currentCoverPath = safe(coverPath);
        currentPercent = Math.max(0, Math.min(100, percent));
        if (serviceInstance != null) {
            serviceInstance.ensureForegroundForWork();
            serviceInstance.pushCachingNotification();
        }
    }

    public static void updatePlaybackState(Context context, String title, String artist, String lyricLine, String coverPath) {
        cachingActive = false;
        currentTitle = safe(title);
        currentArtist = safe(artist);
        currentLyricLine = safe(lyricLine);
        currentCoverPath = safe(coverPath);
        currentPositionMs = 0L;
        currentDurationMs = 0L;
        if (serviceInstance != null) {
            serviceInstance.ensureForegroundForWork();
        }
    }

    public static void updatePlaybackProgress(Context context, long positionMs, long durationMs) {
        currentPositionMs = Math.max(0L, positionMs);
        currentDurationMs = Math.max(0L, durationMs);
        if (serviceInstance != null && !cachingActive) {
            serviceInstance.refreshNotification();
        }
    }

    public static void updateLyricLine(Context context, String lyricLine) {
        currentLyricLine = safe(lyricLine);
        if (serviceInstance != null) {
            if (cachingActive) {
                serviceInstance.ensureForegroundForWork();
            } else {
                serviceInstance.refreshNotification();
            }
        }
    }

    public static void updateCoverPath(Context context, String coverPath) {
        currentCoverPath = safe(coverPath);
        if (serviceInstance != null) {
            if (cachingActive) {
                serviceInstance.ensureForegroundForWork();
            } else {
                serviceInstance.refreshNotification();
            }
        }
    }

    private void applyAudioFocusBehavior() {
        if (sharedPlayer == null || apiClient == null || sharedAudioAttributes == null) {
            return;
        }
        boolean allowConcurrent = apiClient.isConcurrentPlaybackAllowed();
        sharedPlayer.setAudioAttributes(sharedAudioAttributes, !allowConcurrent);
        if (allowConcurrent) {
            abandonAudioFocus();
        } else {
            requestExclusiveAudioFocus();
        }
    }

    public static void updateAudioFocusPolicy(Context context) {
        if (serviceInstance != null) {
            serviceInstance.applyAudioFocusBehavior();
        }
    }

    private void ensureAudioManager() {
        if (audioManager == null) {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        }
    }

    private void requestExclusiveAudioFocus() {
        ensureAudioManager();
        if (audioManager == null || sharedAudioAttributes == null) {
            return;
        }
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.media.AudioAttributes focusAttr = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            if (audioFocusRequest == null) {
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(focusAttr)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener, focusHandler)
                        .setWillPauseWhenDucked(true)
                        .setAcceptsDelayedFocusGain(true)
                        .build();
            }
            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            hasAudioFocus = false;
            runOnMainThread(() -> {
                if (sharedPlayer != null && sharedPlayer.isPlaying()) {
                    sharedPlayer.pause();
                }
            });
        } else {
            hasAudioFocus = true;
        }
    }

    private void abandonAudioFocus() {
        ensureAudioManager();
        if (audioManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
        hasAudioFocus = false;
    }

    public static void clearCachingState(Context context) {
        cachingActive = false;
        currentPercent = 0;
        if (serviceInstance != null) {
            serviceInstance.ensureForegroundForWork();
            serviceInstance.refreshNotification();
        }
    }

    public static void cancelServiceCaching(Context context) {
        cachingActive = false;
        currentPercent = 0;
        prefetchInFlightSongId = "";
        if (serviceInstance != null && serviceInstance.apiClient != null) {
            serviceInstance.apiClient.cancelSongCache();
        }
        if (serviceInstance != null) {
            serviceInstance.refreshNotification();
        }
    }

    public static void syncQueueFromUi(@Nullable List<ApiClient.MediaItemModel> queue, @Nullable String currentSongId,
                                       boolean shuffle, int repeat, boolean repeatOne, @Nullable String forceNextSongId) {
        synchronized (QUEUE_LOCK) {
            nowPlayingQueue.clear();
            if (queue != null) {
                for (ApiClient.MediaItemModel item : queue) {
                    if (item != null && "song".equals(item.type) && item.id != null && !item.id.isEmpty()) {
                        nowPlayingQueue.add(item);
                    }
                }
            }
            shuffleEnabled = shuffle;
            repeatMode = repeat;
            repeatOneEnabled = repeatOne;
            pendingForceNextSongId = forceNextSongId == null ? "" : forceNextSongId;
            nowPlayingIndex = -1;
            if (currentSongId != null) {
                for (int i = 0; i < nowPlayingQueue.size(); i++) {
                    if (currentSongId.equals(nowPlayingQueue.get(i).id)) {
                        nowPlayingIndex = i;
                        break;
                    }
                }
            }
        }
    }

    public static void setUiActive(boolean active) {
        uiActive = active;
    }

    @Nullable
    public static String getCurrentMediaId() {
        if (sharedPlayer == null) {
            return null;
        }
        if (Looper.getMainLooper() != Looper.myLooper()) {
            return currentMediaIdCache.isEmpty() ? null : currentMediaIdCache;
        }
        MediaItem item = sharedPlayer.getCurrentMediaItem();
        String mediaId = item == null ? null : item.mediaId;
        currentMediaIdCache = mediaId == null ? "" : mediaId;
        return mediaId;
    }

    public static boolean isCachingActive() {
        return cachingActive;
    }

    public static String getCurrentTitle() {
        return currentTitle;
    }

    public static String getCurrentArtist() {
        return currentArtist;
    }

    public static String getCurrentLyricLine() {
        return currentLyricLine;
    }

    public static String getCurrentCoverPath() {
        return currentCoverPath;
    }

    public static int getCurrentPercent() {
        return currentPercent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        serviceInstance = this;
        createChannel();
        registerNoisyReceiverIfNeeded();
        registerHeadsetReceiverIfNeeded();
        notificationManager = getSystemService(NotificationManager.class);
        ensureNotificationActions();
        ensureForegroundForWork();
        ensureWakeLockState();
        if (sharedPlayer == null) {
            sharedPlayer = new ExoPlayer.Builder(getApplicationContext()).build();
            // Improve background playback robustness:
            // - Request/handle audio focus automatically
            // - Pause when headphones disconnect
            sharedAudioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build();
            sharedPlayer.setAudioAttributes(sharedAudioAttributes, true);
            sharedPlayer.setHandleAudioBecomingNoisy(true);
        }
        if (apiClient == null) {
            apiClient = new ApiClient(getApplicationContext());
        }
        applyAudioFocusBehavior();
        sharedPlayer.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                playbackIsPlaying = isPlaying;
                ensureWakeLockState();
                refreshNotification();
                if (isPlaying) {
                    requestExclusiveAudioFocus();
                    ensureForegroundForWork();
                } else if (!cachingActive) {
                    abandonAudioFocus();
                }
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                currentMediaIdCache = mediaItem == null || mediaItem.mediaId == null ? "" : mediaItem.mediaId;
                // When the next track begins, opportunistically prefetch the next one in background.
                maybePrefetchNextInBackground();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                ensureWakeLockState();
                if (playbackState == Player.STATE_ENDED) {
                    handleEndedInService();
                } else {
                    refreshNotification();
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                // In background we try to continue queue playback instead of stalling silently.
                if (!uiActive) {
                    handleEndedInService();
                }
                refreshNotification();
            }
        });
        refreshNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            postNotificationAction(intent.getAction());
        }
        ensureWakeLockState();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        serviceInstance = null;
        unregisterNoisyReceiverIfNeeded();
        unregisterHeadsetReceiverIfNeeded();
        // Do NOT release sharedPlayer here. Some devices aggressively stop the service when app is idle.
        // Releasing the static player makes the UI "fake-dead" until process restart.
        // Player is only released when user explicitly stops session (stopSession).
        releaseWakeLockIfHeld();
        if (serviceWorkThread != null) {
            serviceWorkThread.quitSafely();
            serviceWorkThread = null;
            serviceWorkHandler = null;
        }
        super.onDestroy();
    }

    private void ensureServiceWorkHandler() {
        if (serviceWorkThread == null) {
            serviceWorkThread = new HandlerThread("DSMusic-PlaybackService");
            serviceWorkThread.start();
            serviceWorkHandler = new Handler(serviceWorkThread.getLooper());
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.playback_channel_name));
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void registerNoisyReceiverIfNeeded() {
        if (noisyReceiverRegistered) {
            return;
        }
        noisyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                    return;
                }
                stopPlaybackForNoisyEvent();
            }
        };
        try {
            IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            registerReceiver(noisyReceiver, filter);
            noisyReceiverRegistered = true;
        } catch (Throwable ignored) {
            noisyReceiverRegistered = false;
        }
    }

    private void registerHeadsetReceiverIfNeeded() {
        if (headsetReceiverRegistered) {
            return;
        }
        headsetReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || !Intent.ACTION_HEADSET_PLUG.equals(intent.getAction())) {
                    return;
                }
                if (intent.getIntExtra("state", 0) == 1) {
                    resumePlaybackAfterHeadsetReturn();
                }
            }
        };
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
            registerReceiver(headsetReceiver, filter);
            headsetReceiverRegistered = true;
        } catch (Throwable ignored) {
            headsetReceiverRegistered = false;
        }
    }

    private void unregisterNoisyReceiverIfNeeded() {
        if (!noisyReceiverRegistered || noisyReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(noisyReceiver);
        } catch (Throwable ignored) {
        }
        noisyReceiverRegistered = false;
        noisyReceiver = null;
    }

    private void unregisterHeadsetReceiverIfNeeded() {
        if (!headsetReceiverRegistered || headsetReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(headsetReceiver);
        } catch (Throwable ignored) {
        }
        headsetReceiverRegistered = false;
        headsetReceiver = null;
    }

    private void stopPlaybackForNoisyEvent() {
        runOnMainThread(() -> {
            if (sharedPlayer == null) {
                return;
            }
            try {
                if (sharedPlayer.isPlaying() || sharedPlayer.getPlayWhenReady()) {
                    // Pause only so the current position can be resumed after output comes back.
                    sharedPlayer.pause();
                    sharedPlayer.setPlayWhenReady(false);
                    resumeAfterNoisyEvent = true;
                }
                refreshNotification();
            } catch (Throwable ignored) {
            }
        });
    }

    private void resumePlaybackAfterHeadsetReturn() {
        runOnMainThread(() -> {
            if (sharedPlayer == null || !resumeAfterNoisyEvent) {
                return;
            }
            if (sharedPlayer.getPlaybackState() != Player.STATE_READY
                    && sharedPlayer.getPlaybackState() != Player.STATE_BUFFERING) {
                return;
            }
            resumeAfterNoisyEvent = false;
            sharedPlayer.play();
            refreshNotification();
        });
    }

    private void ensureForegroundForWork() {
        if (!notificationsAllowed(this)) {
            return;
        }
        if (notificationManager == null) {
            notificationManager = getSystemService(NotificationManager.class);
        }
        Notification notification = foregroundStarted ? buildNowPlayingNotification() : buildBootstrapNotification();
        if (notification == null) {
            return;
        }
        try {
            if (!foregroundStarted) {
                startForeground(NOTIFICATION_ID, notification);
                foregroundStarted = true;
            } else {
                notificationManager.notify(NOTIFICATION_ID, notification);
            }
        } catch (Throwable ignored) {
        }
    }

    private Notification buildBootstrapNotification() {
        if (!notificationsAllowed(this)) {
            return null;
        }
        if (notificationManager == null) {
            notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager == null) {
                return null;
            }
        }
        ensureNotificationActions();
        String title = currentTitle.isEmpty() ? getString(R.string.app_name) : buildDisplayTitle(sanitizeDisplayTitle(currentTitle), currentArtist);
        String content = cachingActive ? getString(R.string.caching_song) : getString(R.string.preparing_to_play);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setSubText(currentArtist)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(contentIntent == null ? buildContentIntent() : contentIntent)
                .setStyle(new MediaStyle().setShowActionsInCompactView(1, 2))
                .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_previous, getString(R.string.previous_track), actionPreviousIntent))
                .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_pause, cachingActive ? getString(R.string.pause) : getString(R.string.play), actionPlayPauseIntent))
                .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_next, getString(R.string.next_track), actionNextIntent));
        builder.setProgress(cachingActive ? 100 : 0, cachingActive ? currentPercent : 0, false);
        return builder.build();
    }

    private void pushCachingNotification() {
        refreshNotification();
    }

    private void pushCachingNotificationThrottled(boolean immediate) {
        refreshNotification();
    }

    private void ensureNotificationActions() {
        if (actionPreviousIntent == null) {
            actionPreviousIntent = buildActionIntent(ACTION_PREVIOUS);
            actionPlayPauseIntent = buildActionIntent(ACTION_PLAY_PAUSE);
            actionNextIntent = buildActionIntent(ACTION_NEXT);
            contentIntent = buildContentIntent();
        }
    }

    private PendingIntent buildActionIntent(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent buildContentIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void handleNotificationAction(String action) {
        if (sharedPlayer == null) {
            return;
        }
        switch (action) {
            case ACTION_PREVIOUS:
                playPreviousTrack();
                break;
            case ACTION_PLAY_PAUSE:
                runOnMainThread(() -> {
                    if (sharedPlayer == null) {
                        return;
                    }
                    if (sharedPlayer.isPlaying()) {
                        sharedPlayer.pause();
                    } else {
                        sharedPlayer.play();
                    }
                    refreshNotification();
                });
                return;
            case ACTION_NEXT:
                playNextTrack(true);
                break;
        }
        refreshNotification();
    }

    private void postNotificationAction(String action) {
        ensureServiceWorkHandler();
        int token = ++notificationActionToken;
        serviceWorkHandler.removeCallbacksAndMessages(null);
        serviceWorkHandler.post(() -> {
            if (token != notificationActionToken) {
                return;
            }
            handleNotificationAction(action);
        });
    }

    private void playPreviousTrack() {
        if (nowPlayingQueue.isEmpty()) {
            return;
        }
        int currentIndex = resolveCurrentQueueIndex();
        if (currentIndex >= 0) {
            nowPlayingIndex = currentIndex;
        }
        int target = nowPlayingIndex - 1;
        if (target < 0) {
            if (repeatMode != 0 || repeatOneEnabled) {
                target = nowPlayingQueue.size() - 1;
            } else {
                runOnMainThread(() -> {
                    if (sharedPlayer != null) {
                        sharedPlayer.seekTo(0);
                    }
                });
                refreshNotification();
                return;
            }
        }
        if (target < 0 || target >= nowPlayingQueue.size()) {
            return;
        }
        ApiClient.MediaItemModel item = nowPlayingQueue.get(target);
        nowPlayingIndex = target;
        playOrCacheThenPlay(item);
    }

    private void playNextTrack(boolean manual) {
        ApiClient.MediaItemModel next = null;
        synchronized (QUEUE_LOCK) {
            if (nowPlayingQueue.isEmpty()) {
                return;
            }
            int currentIndex = resolveCurrentQueueIndex();
            if (currentIndex >= 0) {
                nowPlayingIndex = currentIndex;
            }
            if (!manual && uiActive && isDeviceInteractive()) {
                return;
            }
            if (!manual && repeatOneEnabled && nowPlayingIndex >= 0 && nowPlayingIndex < nowPlayingQueue.size()) {
                next = nowPlayingQueue.get(nowPlayingIndex);
            } else {
                int targetIndex = -1;
                if (!manual && pendingForceNextSongId != null && !pendingForceNextSongId.isEmpty()) {
                    for (int i = 0; i < nowPlayingQueue.size(); i++) {
                        if (pendingForceNextSongId.equals(nowPlayingQueue.get(i).id)) {
                            targetIndex = i;
                            break;
                        }
                    }
                    pendingForceNextSongId = "";
                }
                if (targetIndex < 0) {
                    if (shuffleEnabled && nowPlayingQueue.size() > 1) {
                        int base = Math.max(0, nowPlayingIndex);
                        int pick = base;
                        int tries = 0;
                        while (pick == base && tries < 6) {
                            pick = RNG.nextInt(nowPlayingQueue.size());
                            tries++;
                        }
                        targetIndex = pick;
                    } else {
                        targetIndex = nowPlayingIndex + 1;
                    }
                }
                if (targetIndex >= nowPlayingQueue.size()) {
                    if (repeatMode == 0 && !manual) {
                        return;
                    }
                    targetIndex = 0;
                }
                if (targetIndex < 0) {
                    targetIndex = 0;
                }
                nowPlayingIndex = targetIndex;
                next = nowPlayingQueue.get(targetIndex);
            }
        }
        if (next != null) {
            playOrCacheThenPlay(next);
        }
    }

    private int resolveCurrentQueueIndex() {
        if (!isMainThread()) {
            return nowPlayingIndex;
        }
        String currentId = getCurrentMediaId();
        if (currentId != null) {
            for (int i = 0; i < nowPlayingQueue.size(); i++) {
                if (currentId.equals(nowPlayingQueue.get(i).id)) {
                    return i;
                }
            }
        }
        return nowPlayingIndex;
    }

    private void refreshNotification() {
        if (!notificationsAllowed(this) || notificationManager == null || !foregroundStarted) {
            return;
        }
        Notification notification = buildNowPlayingNotification();
        if (notification != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNowPlayingNotification() {
        if (!notificationsAllowed(this)) {
            return null;
        }
        if (notificationManager == null) {
            notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager == null) {
                return null;
            }
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        String title = buildDisplayTitle(sanitizeDisplayTitle(currentTitle), currentArtist);
        String artist = defaultText(currentArtist, "");
        String content = TextUtils.isEmpty(currentLyricLine) ? artist : currentLyricLine;
        boolean playing = playbackIsPlaying;
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(content.isEmpty() ? artist : content)
                .setSubText(currentArtist)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(playing || cachingActive)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(contentIntent == null ? buildContentIntent() : contentIntent)
                .setStyle(new MediaStyle().setShowActionsInCompactView(1, 2))
                .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_previous, getString(R.string.previous_track), actionPreviousIntent))
                .addAction(new NotificationCompat.Action(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? getString(R.string.pause) : getString(R.string.play), actionPlayPauseIntent))
                .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_next, getString(R.string.next_track), actionNextIntent));
        if (cachingActive) {
            builder.setProgress(100, currentPercent, false);
        } else if (currentDurationMs > 0L) {
            int progress = (int) Math.min(1000L, (currentPositionMs * 1000L) / Math.max(1L, currentDurationMs));
            builder.setProgress(1000, progress, false);
        } else {
            builder.setProgress(0, 0, false);
        }
        Bitmap cover = decodeCover();
        if (cover != null) {
            builder.setLargeIcon(cover);
        }
        return builder.build();
    }

    private void ensureWakeLockState() {
        boolean shouldHold = cachingActive || playbackIsPlaying;
        if (shouldHold) {
            acquireWakeLockIfNeeded();
        } else {
            releaseWakeLockIfHeld();
        }
    }

    private void acquireWakeLockIfNeeded() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DSMusic:Playback");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(10 * 60 * 1000L);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void releaseWakeLockIfHeld() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Throwable ignored) {
        }
    }

    private void handleEndedInService() {
        // Some devices keep the activity "resumed" while screen is off / keyguard is shown,
        // so uiActive can be true even though user isn't interacting. In that case we still need
        // the service to advance the queue to avoid silent stop.
        if (uiActive && isDeviceInteractive()) {
            return;
        }
        Log.d(TAG, "handleEndedInService uiActive=" + uiActive + " interactive=" + isDeviceInteractive());
        ApiClient.MediaItemModel next = null;
        synchronized (QUEUE_LOCK) {
            if (nowPlayingQueue.isEmpty()) {
                return;
            }
            String currentId = getCurrentMediaId();
            if (currentId != null) {
                for (int i = 0; i < nowPlayingQueue.size(); i++) {
                    if (currentId.equals(nowPlayingQueue.get(i).id)) {
                        nowPlayingIndex = i;
                        break;
                    }
                }
            }
            if (repeatOneEnabled && nowPlayingIndex >= 0 && nowPlayingIndex < nowPlayingQueue.size()) {
                next = nowPlayingQueue.get(nowPlayingIndex);
            } else {
                int targetIndex = -1;
                if (pendingForceNextSongId != null && !pendingForceNextSongId.isEmpty()) {
                    for (int i = 0; i < nowPlayingQueue.size(); i++) {
                        if (pendingForceNextSongId.equals(nowPlayingQueue.get(i).id)) {
                            targetIndex = i;
                            break;
                        }
                    }
                    pendingForceNextSongId = "";
                }
                if (targetIndex < 0) {
                    if (shuffleEnabled && nowPlayingQueue.size() > 1) {
                        int base = Math.max(0, nowPlayingIndex);
                        int pick = base;
                        int tries = 0;
                        while (pick == base && tries < 6) {
                            pick = RNG.nextInt(nowPlayingQueue.size());
                            tries++;
                        }
                        targetIndex = pick;
                    } else {
                        targetIndex = nowPlayingIndex + 1;
                    }
                }
                if (targetIndex >= nowPlayingQueue.size()) {
                    boolean loop = repeatMode != 0; // any loop mode wraps
                    if (!loop) {
                        return;
                    }
                    targetIndex = 0;
                }
                if (targetIndex < 0) {
                    targetIndex = 0;
                }
                nowPlayingIndex = targetIndex;
                next = nowPlayingQueue.get(targetIndex);
            }
        }
        if (next != null) {
            playOrCacheThenPlay(next);
        }
    }

    private boolean isDeviceInteractive() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isInteractive();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private void playOrCacheThenPlay(@NonNull ApiClient.MediaItemModel item) {
        if (apiClient == null) {
            apiClient = new ApiClient(getApplicationContext());
        }
        if (apiClient.isSongCached(item)) {
            playCachedInService(item);
            return;
        }
        cachingActive = true;
        currentTitle = safe(item.title);
        currentArtist = safe(item.artist);
        currentCoverPath = safe(item.localCoverPath);
        currentPercent = 0;
        ensureForegroundForWork();
        pushCachingNotificationThrottled(true);
        apiClient.cacheSongToConfiguredDirectory(item, new ApiClient.CacheCallback() {
            @Override
            public void onProgress(int percent) {
                currentPercent = Math.max(0, Math.min(100, percent));
                pushCachingNotificationThrottled(false);
            }

            @Override
            public void onSuccess(String value) {
                cachingActive = false;
                currentPercent = 0;
                playCachedInService(item, value);
            }

            @Override
            public void onError(String message) {
                cachingActive = false;
                currentPercent = 0;
                refreshNotification();
                ensureWakeLockState();
            }
        });
    }

    private void playCachedInService(@NonNull ApiClient.MediaItemModel item) {
        playCachedInService(item, null);
    }

    private void playCachedInService(@NonNull ApiClient.MediaItemModel item, @Nullable String cacheValue) {
        Uri cacheUri = resolveCacheUri(cacheValue);
        if (cacheUri == null) {
            cacheUri = apiClient.getSongCacheReadUri(item);
        }
        if (cacheUri == null) {
            return;
        }
        if (!isMainThread()) {
            final Uri finalCacheUri = cacheUri;
            runOnMainThread(() -> playCachedInService(item, finalCacheUri.toString()));
            return;
        }
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(cacheUri)
                .setMediaId(item.id)
                .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .build())
                .setTag(item.localCoverPath == null ? "" : item.localCoverPath)
                .build();
        currentMediaIdCache = item.id == null ? "" : item.id;
        sharedPlayer.setMediaItem(mediaItem);
        requestExclusiveAudioFocus();
        sharedPlayer.prepare();
        applyAudioFocusBehavior();
        sharedPlayer.play();
        apiClient.pushHistory(item);
        updatePlaybackState(getApplicationContext(), item.title, item.artist, "", item.localCoverPath);
        maybePrefetchNextInBackground();
        ensureWakeLockState();
    }

    @Nullable
    private Uri resolveCacheUri(@Nullable String cacheValue) {
        if (TextUtils.isEmpty(cacheValue)) {
            return null;
        }
        try {
            if (cacheValue.contains("://")) {
                return Uri.parse(cacheValue);
            }
        } catch (Throwable ignored) {
        }
        File file = new File(cacheValue);
        return file.exists() ? Uri.fromFile(file) : null;
    }

    private boolean isMainThread() {
        return Looper.getMainLooper() == Looper.myLooper();
    }

    private void runOnMainThread(Runnable runnable) {
        if (isMainThread()) {
            runnable.run();
            return;
        }
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    private void maybePrefetchNextInBackground() {
        if (apiClient == null || sharedPlayer == null) {
            return;
        }
        if (cachingActive) {
            return;
        }
        String baseId = getCurrentMediaId();
        if (baseId == null || baseId.isEmpty()) {
            return;
        }
        if (baseId.equals(lastPrefetchBaseSongId)) {
            return;
        }
        lastPrefetchBaseSongId = baseId;

        ApiClient.MediaItemModel candidate = null;
        synchronized (QUEUE_LOCK) {
            if (nowPlayingQueue.isEmpty()) {
                return;
            }
            int baseIndex = -1;
            for (int i = 0; i < nowPlayingQueue.size(); i++) {
                if (baseId.equals(nowPlayingQueue.get(i).id)) {
                    baseIndex = i;
                    break;
                }
            }
            if (baseIndex >= 0) {
                nowPlayingIndex = baseIndex;
            }
            if (repeatOneEnabled) {
                return;
            }
            if (pendingForceNextSongId != null && !pendingForceNextSongId.isEmpty()) {
                for (int i = 0; i < nowPlayingQueue.size(); i++) {
                    if (pendingForceNextSongId.equals(nowPlayingQueue.get(i).id)) {
                        candidate = nowPlayingQueue.get(i);
                        break;
                    }
                }
            }
            if (candidate == null) {
                if (shuffleEnabled && nowPlayingQueue.size() > 1) {
                    int base = Math.max(0, baseIndex);
                    int pick = base;
                    int tries = 0;
                    while (pick == base && tries < 6) {
                        pick = RNG.nextInt(nowPlayingQueue.size());
                        tries++;
                    }
                    candidate = nowPlayingQueue.get(pick);
                } else {
                    int idx = (baseIndex < 0 ? 0 : baseIndex + 1);
                    if (idx >= nowPlayingQueue.size()) {
                        boolean loop = repeatMode != 0;
                        if (!loop) {
                            return;
                        }
                        idx = 0;
                    }
                    candidate = nowPlayingQueue.get(Math.max(0, Math.min(idx, nowPlayingQueue.size() - 1)));
                }
            }
        }

        if (candidate == null || candidate.id == null || candidate.id.isEmpty()) {
            return;
        }
        if (candidate.id.equals(prefetchInFlightSongId)) {
            return;
        }
        if (apiClient.isSongCached(candidate)) {
            prefetchInFlightSongId = "";
            return;
        }
        prefetchInFlightSongId = candidate.id;
        apiClient.cacheSongToConfiguredDirectory(candidate, new ApiClient.CacheCallback() {
            @Override
            public void onProgress(int percent) {
                // Prefetch: no UI/notification updates.
            }

            @Override
            public void onSuccess(String value) {
                prefetchInFlightSongId = "";
            }

            @Override
            public void onError(String message) {
                prefetchInFlightSongId = "";
            }
        });
    }

    private static boolean notificationsAllowed(Context context) {
        if (Build.VERSION.SDK_INT < 33) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private Bitmap decodeCover() {
        if (currentCoverPath.isEmpty()) {
            return null;
        }
        File file = new File(currentCoverPath);
        if (!file.exists()) {
            return null;
        }
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String sanitizeDisplayTitle(String value) {
        String cleaned = safe(value);
        if (cleaned.isEmpty()) {
            return cleaned;
        }
        cleaned = cleaned.replaceAll("\\s*[.·_-]*\\s*\\d{1,2}:\\d{2}$", "");
        cleaned = cleaned.replaceAll("\\s*[.·_-]+$", "");
        return cleaned.trim();
    }

    private String defaultText(String value, String fallback) {
        return value.isEmpty() ? fallback : value;
    }

    private String buildDisplayTitle(String title, String artist) {
        String cleanTitle = defaultText(sanitizeDisplayTitle(title), "");
        String cleanArtist = defaultText(safe(artist), "");
        if (cleanTitle.isEmpty()) {
            return cleanArtist.isEmpty() ? getString(R.string.app_name) : cleanArtist;
        }
        if (cleanArtist.isEmpty()) {
            return cleanTitle;
        }
        if (cleanTitle.contains(cleanArtist)) {
            return cleanTitle;
        }
        return cleanTitle + " - " + cleanArtist;
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

}



