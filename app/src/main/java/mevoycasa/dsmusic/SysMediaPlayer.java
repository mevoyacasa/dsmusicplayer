package mevoycasa.dsmusic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import java.io.File;

public class SysMediaPlayer {
    private static final String TAG = "SysMediaPlayer";
    private static final String CHANNEL_ID = "sys_media_player_channel";
    private static final int NOTIFICATION_ID = 2001;
    
    private static final String ACTION_PREVIOUS = "mevoycasa.dsmusic.sys.ACTION_PREVIOUS";
    private static final String ACTION_PLAY_PAUSE = "mevoycasa.dsmusic.sys.ACTION_PLAY_PAUSE";
    private static final String ACTION_NEXT = "mevoycasa.dsmusic.sys.ACTION_NEXT";
    
    private static SysMediaPlayer instance;
    
    private final Context context;
    private NotificationManager notificationManager;
    private MediaSessionCompat mediaSession;
    private boolean isInitialized = false;
    private boolean isPlaying = false;
    private long currentPositionMs = 0L;
    private long currentDurationMs = 0L;
    
    private String currentTitle = "";
    private String currentArtist = "";
    private String currentLyricLine = "";
    private String currentCoverPath = "";
    
    private MediaControlCallback controlCallback;
    
    public interface MediaControlCallback {
        void onPlay();
        void onPause();
        void onPrevious();
        void onNext();
        void onSeekTo(long positionMs);
    }
    
    private final BroadcastReceiver actionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            handleAction(intent.getAction());
        }
    };
    
    private SysMediaPlayer(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public static synchronized SysMediaPlayer getInstance(Context context) {
        if (instance == null) {
            instance = new SysMediaPlayer(context);
        }
        return instance;
    }
    
    public void setMediaControlCallback(@Nullable MediaControlCallback callback) {
        this.controlCallback = callback;
    }
    
    public void initialize() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        if (isInitialized) {
            return;
        }
        
        notificationManager = context.getSystemService(NotificationManager.class);
        createNotificationChannel();
        createMediaSession();
        registerActionReceiver();
        
        isInitialized = true;
    }
    
    public void release() {
        if (!isInitialized) {
            return;
        }
        
        try {
            context.unregisterReceiver(actionReceiver);
        } catch (Throwable ignored) {
        }
        
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        
        isInitialized = false;
    }
    
    public void updateMediaInfo(String title, String artist, String lyricLine, String coverPath, boolean playing) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        if (!isInitialized) {
            initialize();
        }
        
        this.currentTitle = title != null ? title : "";
        this.currentArtist = artist != null ? artist : "";
        this.currentLyricLine = lyricLine != null ? lyricLine : "";
        this.currentCoverPath = coverPath != null ? coverPath : "";
        this.isPlaying = playing;
        currentPositionMs = 0L;
        currentDurationMs = 0L;
        
        updateMediaSession();
        updateNotification();
    }
    
    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        if (isInitialized) {
            updateMediaSession();
            updateNotification();
        }
    }
    
    public void updateLyricLine(String lyricLine) {
        this.currentLyricLine = lyricLine != null ? lyricLine : "";
        if (isInitialized) {
            updateNotification();
        }
    }
    
    public void updateCoverPath(String coverPath) {
        this.currentCoverPath = coverPath != null ? coverPath : "";
        if (isInitialized) {
            updateMediaSession();
            updateNotification();
        }
    }

    public void updatePlaybackProgress(long positionMs, long durationMs) {
        this.currentPositionMs = Math.max(0L, positionMs);
        this.currentDurationMs = Math.max(0L, durationMs);
        if (isInitialized) {
            updateMediaSession();
            updateNotification();
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.system_media_control),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(context.getString(R.string.system_media_playback_control));
        channel.setShowBadge(false);
        channel.setSound(null, null);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private void createMediaSession() {
        mediaSession = new MediaSessionCompat(context, TAG);
        
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                isPlaying = true;
                if (controlCallback != null) {
                    controlCallback.onPlay();
                }
                updateMediaSession();
                updateNotification();
            }
            
            @Override
            public void onPause() {
                isPlaying = false;
                if (controlCallback != null) {
                    controlCallback.onPause();
                }
                updateMediaSession();
                updateNotification();
            }
            
            @Override
            public void onSkipToPrevious() {
                if (controlCallback != null) {
                    controlCallback.onPrevious();
                }
            }
            
            @Override
            public void onSkipToNext() {
                if (controlCallback != null) {
                    controlCallback.onNext();
                }
            }

            @Override
            public void onSeekTo(long pos) {
                currentPositionMs = Math.max(0L, pos);
                if (controlCallback != null) {
                    controlCallback.onSeekTo(currentPositionMs);
                }
                updateMediaSession();
                updateNotification();
            }
        });
        
        mediaSession.setActive(true);
    }
    
    private void registerActionReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PREVIOUS);
        filter.addAction(ACTION_PLAY_PAUSE);
        filter.addAction(ACTION_NEXT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(actionReceiver, filter);
        }
    }
    
    private void handleAction(String action) {
        switch (action) {
            case ACTION_PREVIOUS:
                if (controlCallback != null) {
                    controlCallback.onPrevious();
                }
                break;
            case ACTION_PLAY_PAUSE:
                if (isPlaying) {
                    isPlaying = false;
                    if (controlCallback != null) {
                        controlCallback.onPause();
                    }
                } else {
                    isPlaying = true;
                    if (controlCallback != null) {
                        controlCallback.onPlay();
                    }
                }
                updateMediaSession();
                updateNotification();
                break;
            case ACTION_NEXT:
                if (controlCallback != null) {
                    controlCallback.onNext();
                }
                break;
        }
    }
    
    private void updateMediaSession() {
        if (mediaSession == null) {
            return;
        }
        
        MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDurationMs);
        String displayTitle = buildDisplayTitle(currentTitle, currentArtist);
        if (!currentTitle.isEmpty()) {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, displayTitle);
        }
        if (!currentArtist.isEmpty()) {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, currentArtist);
        }
        if (!currentLyricLine.isEmpty()) {
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, currentLyricLine);
        }
        
        Bitmap cover = decodeCover();
        if (cover != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover);
        }
        
        mediaSession.setMetadata(metadataBuilder.build());
        
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(
                        isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                        currentPositionMs,
                        isPlaying ? 1.0f : 0.0f,
                        SystemClock.elapsedRealtime()
                );
        if (currentDurationMs > 0L) {
            stateBuilder.setBufferedPosition(currentDurationMs);
        }
        
        mediaSession.setPlaybackState(stateBuilder.build());
    }
    
    private void updateNotification() {
        if (notificationManager == null) {
            return;
        }
        
        Notification notification = buildNotification();
        if (notification != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    private PendingIntent buildActionIntent(String action) {
        Intent intent = new Intent(action);
        intent.setPackage(context.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, flags);
    }
    
    private Notification buildNotification() {
        PendingIntent actionPreviousIntent = buildActionIntent(ACTION_PREVIOUS);
        PendingIntent actionPlayPauseIntent = buildActionIntent(ACTION_PLAY_PAUSE);
        PendingIntent actionNextIntent = buildActionIntent(ACTION_NEXT);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);
        
        String displayLyric = !currentLyricLine.isEmpty() ? currentLyricLine : "";
        String displayTitle = buildDisplayTitle(currentTitle, currentArtist);
        
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(displayTitle)
                .setContentText(displayLyric)
                .setSubText(currentArtist)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession != null ? mediaSession.getSessionToken() : null)
                        .setShowActionsInCompactView(0, 1, 2))
                .addAction(new NotificationCompat.Action(
                        android.R.drawable.ic_media_previous,
                        context.getString(R.string.previous_track),
                        actionPreviousIntent))
                .addAction(new NotificationCompat.Action(
                        isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        isPlaying ? context.getString(R.string.pause) : context.getString(R.string.play),
                        actionPlayPauseIntent))
                .addAction(new NotificationCompat.Action(
                        android.R.drawable.ic_media_next,
                        context.getString(R.string.next_track),
                        actionNextIntent));
        
        Bitmap cover = decodeCover();
        if (cover != null) {
            builder.setLargeIcon(cover);
        }
        if (currentDurationMs > 0L) {
            int progress = (int) Math.min(1000L, (currentPositionMs * 1000L) / Math.max(1L, currentDurationMs));
            builder.setProgress(1000, progress, false);
        } else {
            builder.setProgress(0, 0, false);
        }
        
        return builder.build();
    }

    private String buildDisplayTitle(String title, String artist) {
        String cleanTitle = title == null ? "" : title.trim();
        String cleanArtist = artist == null ? "" : artist.trim();
        if (cleanTitle.isEmpty()) {
            return cleanArtist.isEmpty() ? context.getString(R.string.now_playing) : cleanArtist;
        }
        if (cleanArtist.isEmpty() || cleanTitle.contains(cleanArtist)) {
            return cleanTitle;
        }
        return cleanTitle + " - " + cleanArtist;
    }
    
    private Bitmap decodeCover() {
        if (currentCoverPath == null || currentCoverPath.isEmpty()) {
            return null;
        }
        File file = new File(currentCoverPath);
        if (!file.exists()) {
            return null;
        }
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
