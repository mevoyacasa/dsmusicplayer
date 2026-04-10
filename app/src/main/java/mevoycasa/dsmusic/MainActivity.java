package mevoycasa.dsmusic;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "DSMusic";
    private static final int PAGE_SIZE = 50;
    private static final String KEY_LAST_HOME_TAB = "last_home_tab";
    private static final String KEY_LAST_HOME_PARENT_ID = "last_home_parent_id";
    private static final String KEY_LAST_HOME_PARENT_TYPE = "last_home_parent_type";
    private static final String KEY_LAST_HOME_HEADER = "last_home_header";
    private static final RequestOptions MINI_COVER_OPTIONS = new RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .override(160, 160)
            .dontAnimate()
            .disallowHardwareConfig();
    private static final RequestOptions PLAYER_COVER_OPTIONS = new RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .override(960, 960)
            .dontAnimate()
            .disallowHardwareConfig();
    private static boolean rootSongsPrefetchedThisSession = false;
    private boolean uiActive = false;

    private ApiClient apiClient;
    private PlaylistDialogs playlistDialogs;
    private ExoPlayer player;

    private DrawerLayout drawerLayout;
    private TabLayout tabLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private RecyclerView lyricsRecyclerView;
    private TextView textHeader;
    private TextView textEyebrow;
    private TextView textIndexOverlay;
    private TextView textSummary;
    private TextView textEmpty;
    private View viewIndexOverlayScrim;
    private EditText editSearch;
    private View searchContainer;
    private TextView buttonSort;
    private TextView buttonRefresh;
    private TextView buttonRandomPlay;
    private TextView buttonPlaylistSearch;
    private TextView buttonSelectAll;
    private ImageButton buttonMenu;
    private TextView textDrawerAccount;
    private ImageView imageDrawerAvatar;
    private View actionDrawerSettings;
    private View actionDrawerLogout;
    private View actionDrawerOffline;
    private View actionDrawerPlaylists;
    private View actionDrawerCacheManager;
    private View actionDrawerAbout;
    private TextView actionDrawerAudioExclusive;
    private TextView actionDrawerAudioShared;

    private MaterialCardView miniPlayer;
    private ImageView imageMiniCover;
    private TextView textMiniTitle;
    private TextView textMiniSubtitle;
    private ProgressBar progressMini;
    private ImageButton buttonMiniShuffle;
    private ImageButton buttonMiniRepeat;
    private ImageButton buttonMiniPlay;
    private ImageButton buttonMiniNext;

    private View fullPlayer;
    private View playerPageControls;
    private View playerPageLyrics;
    private View playerInfoRow;
    private ImageView imagePlayerCover;
    private ImageView imageLyricsBackdrop;
    private TextView textPlayerAlbum;
    private TextView textPlayerTitle;
    private TextView textPlayerArtist;
    private TextView textPlayerQuality;
    private TextView textPlayerLyricLine;
    private TextView textCurrentTime;
    private TextView textDuration;
    private SeekBar seekBar;
    private ImageButton buttonShuffle;
    private ImageButton buttonRepeat;
    private ImageButton buttonRepeatOne;
    private ImageButton buttonPlayPause;

    private final List<ApiClient.MediaItemModel> browserItems = new ArrayList<>();
    private final List<ApiClient.LyricLine> lyricLines = new ArrayList<>();
    private final Set<String> cachedSongIds = new HashSet<>();
    private BrowserAdapter browserAdapter;
    private LyricsAdapter lyricsAdapter;
    private final DecelerateInterpolator panelInterpolator = new DecelerateInterpolator();

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private HandlerThread cacheWorkerThread;
    private Handler cacheWorkerHandler;
    private String currentMode = "songs";
    private String currentParentId = null;
    private String currentParentType = null;
    private String currentKeyword = "";
    private String currentSortBy = "title";
    private String currentSortDirection = "ASC";
    private int currentOffset = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private boolean rootSongsLoadedFromCache = false;
    private boolean internalModeSwitch = false;
    private String pendingHeader = null;
    private int currentLyricIndex = -1;
    private int playRequestToken = 0;
    private int cacheRequestToken = 0;
    private int playTransitionToken = 0;
    private boolean isSongCaching = false;
    private boolean shuffleEnabled = true;
    private boolean repeatAllEnabled = true;
    private boolean repeatOneEnabled = false;
    private long lastBackPressedAt = 0L;
    private boolean lyricsPageVisible = false;
    private boolean allowPendingCacheAutoPlay = true;
    private boolean suppressNextOnPlayerStop = false;
    private String lyricSongId;
    @Nullable
    private String lastNowPlayingUiSongId = null;
    private int nowPlayingCoverRefreshToken = 0;

    private ApiClient.MediaItemModel currentPlaying;
    private ApiClient.MediaItemModel currentCaching;
    private final ArrayList<ApiClient.MediaItemModel> playQueue = new ArrayList<>();
    private final ArrayList<ApiClient.MediaItemModel> selectedSearchSongs = new ArrayList<>();
    private int playQueueIndex = -1;
    private String playQueueMode = null;
    private String playQueueParentId = null;
    private String playQueueParentType = null;
    private String playQueueKeyword = "";
    private String playQueueSortBy = "title";
    private String playQueueSortDirection = "ASC";
    private boolean playQueueComplete = true;
    private boolean playQueueLoading = false;
    private int playQueueVersion = 0;
    private String playQueueContextKey = "";
    private String pendingForceNextSongId = null;
    private boolean isEdgeFastScrolling = false;
    private final ArrayList<FolderNavigationState> folderNavigationStack = new ArrayList<>();
    private final ArrayList<BrowserNavigationState> browserNavigationStack = new ArrayList<>();

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && currentPlaying != null) {
                long position = player.getCurrentPosition();
                long duration = Math.max(player.getDuration(), currentPlaying.durationMs);
                textCurrentTime.setText(formatDuration(position));
                textDuration.setText(formatDuration(duration));
                if (duration > 0L && !isSongCaching) {
                    int progress = (int) ((position * 1000L) / duration);
                    seekBar.setProgress(progress);
                    progressMini.setProgress(progress);
                }
                syncLyrics(position);
                PlaybackService.updatePlaybackProgress(MainActivity.this, position, duration);
                SysMediaPlayer.getInstance(MainActivity.this).updatePlaybackProgress(position, duration);
            }
            progressHandler.postDelayed(this, 500L);
        }
    };

    private final Runnable hideIndexOverlayRunnable = this::hideIndexOverlay;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        apiClient = new ApiClient(this);
        playlistDialogs = new PlaylistDialogs(this, apiClient);
        bindViews();
        setupLists();
        setupHomeTabs();
        setupPlayer();
        setupActions();
        setupDrawer();
        applyWindowInsets();
        requestNotificationPermissionIfNeeded();
        refreshCachedSongIndex();
        
        SysMediaPlayer sysPlayer = SysMediaPlayer.getInstance(this);
        sysPlayer.setMediaControlCallback(new SysMediaPlayer.MediaControlCallback() {
            @Override
            public void onPlay() {
                if (player != null && !player.isPlaying()) {
                    player.play();
                }
            }
            
            @Override
            public void onPause() {
                if (player != null && player.isPlaying()) {
                    player.pause();
                }
            }
            
            @Override
            public void onPrevious() {
                playPrevious();
            }
            
            @Override
            public void onNext() {
                playNext();
            }

            @Override
            public void onSeekTo(long positionMs) {
                if (player != null && currentPlaying != null) {
                    player.seekTo(positionMs);
                    long duration = Math.max(player.getDuration(), currentPlaying.durationMs);
                    PlaybackService.updatePlaybackProgress(MainActivity.this, player.getCurrentPosition(), duration);
                    SysMediaPlayer.getInstance(MainActivity.this).updatePlaybackProgress(player.getCurrentPosition(), duration);
                }
            }
        });
        sysPlayer.initialize();
        
        syncPlayerSessionUi();
        restoreInitialHomeState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiActive = true;
        PlaybackService.setUiActive(true);
        refreshCachedSongIndex();
        refreshDrawerAccount();
        progressHandler.post(this::runDeferredResumeSync);
    }

    @Override
    protected void onPause() {
        super.onPause();
        uiActive = false;
        PlaybackService.setUiActive(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        progressHandler.post(this::runDeferredResumeSync);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHandler.removeCallbacksAndMessages(null);
        if (cacheWorkerThread != null) {
            cacheWorkerThread.quitSafely();
            cacheWorkerThread = null;
            cacheWorkerHandler = null;
        }
        SysMediaPlayer.getInstance(this).release();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        if (fullPlayer.getVisibility() == View.VISIBLE) {
            if (lyricsPageVisible) {
                showLyricsPage(false);
                return;
            }
            hideFullPlayer();
            return;
        }
        if (navigateUpPlaylist()) {
            return;
        }
        if (navigateUpFolder()) {
            return;
        }
        if (navigateUpBrowserStack()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressedAt < 1800L) {
            super.onBackPressed();
            return;
        }
        lastBackPressedAt = now;
        toast("再按一次退出到桌面");
    }

    private void bindViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        tabLayout = findViewById(R.id.tabLayout);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        recyclerView = findViewById(R.id.recyclerView);
        lyricsRecyclerView = findViewById(R.id.recyclerLyrics);
        textHeader = findViewById(R.id.textHeader);
        textEyebrow = findViewById(R.id.textEyebrow);
        textIndexOverlay = findViewById(R.id.textIndexOverlay);
        textSummary = findViewById(R.id.textSummary);
        textEmpty = findViewById(R.id.textEmpty);
        viewIndexOverlayScrim = findViewById(R.id.viewIndexOverlayScrim);
        editSearch = findViewById(R.id.editSearch);
        searchContainer = findViewById(R.id.searchContainer);
        buttonSort = findViewById(R.id.buttonSort);
        buttonRefresh = findViewById(R.id.buttonRefresh);
        buttonRandomPlay = findViewById(R.id.buttonRandomPlay);
        buttonPlaylistSearch = findViewById(R.id.buttonPlaylistSearch);
        buttonSelectAll = findViewById(R.id.buttonSelectAll);
        buttonMenu = findViewById(R.id.buttonMenu);
        textDrawerAccount = findViewById(R.id.textDrawerAccount);
        imageDrawerAvatar = findViewById(R.id.imageDrawerAvatar);
        actionDrawerSettings = findViewById(R.id.actionDrawerSettings);
        actionDrawerLogout = findViewById(R.id.actionDrawerLogout);
        actionDrawerOffline = findViewById(R.id.actionDrawerOffline);
        actionDrawerPlaylists = findViewById(R.id.actionDrawerPlaylists);
        actionDrawerCacheManager = findViewById(R.id.actionDrawerCacheManager);
        actionDrawerAudioExclusive = findViewById(R.id.tabAudioExclusive);
        actionDrawerAudioShared = findViewById(R.id.tabAudioShared);
        actionDrawerAbout = findViewById(R.id.actionDrawerAbout);
        miniPlayer = findViewById(R.id.miniPlayer);
        imageMiniCover = findViewById(R.id.imageMiniCover);
        textMiniTitle = findViewById(R.id.textMiniTitle);
        textMiniSubtitle = findViewById(R.id.textMiniSubtitle);
        progressMini = findViewById(R.id.progressMini);
        buttonMiniShuffle = findViewById(R.id.buttonMiniShuffle);
        buttonMiniRepeat = findViewById(R.id.buttonMiniRepeat);
        buttonMiniPlay = findViewById(R.id.buttonMiniPlay);
        buttonMiniNext = findViewById(R.id.buttonMiniNext);
        fullPlayer = findViewById(R.id.fullPlayer);
        playerPageControls = findViewById(R.id.playerPageControls);
        playerPageLyrics = findViewById(R.id.playerPageLyrics);
        playerInfoRow = findViewById(R.id.playerInfoRow);
        imagePlayerCover = findViewById(R.id.imagePlayerCover);
        imageLyricsBackdrop = findViewById(R.id.imageLyricsBackdrop);
        textPlayerAlbum = findViewById(R.id.textPlayerAlbum);
        textPlayerTitle = findViewById(R.id.textPlayerTitle);
        textPlayerArtist = findViewById(R.id.textPlayerArtist);
        textPlayerQuality = findViewById(R.id.textPlayerQuality);
        textPlayerLyricLine = findViewById(R.id.textPlayerLyricLine);
        textCurrentTime = findViewById(R.id.textCurrentTime);
        textDuration = findViewById(R.id.textDuration);
        seekBar = findViewById(R.id.seekBar);
        if (fullPlayer != null) {
            fullPlayer.setClickable(true);
            fullPlayer.setFocusable(true);
            fullPlayer.setOnTouchListener((v, event) -> true);
        }
        buttonShuffle = findViewById(R.id.buttonShuffle);
        buttonRepeat = findViewById(R.id.buttonRepeat);
        buttonRepeatOne = findViewById(R.id.buttonRepeatOne);
        buttonPlayPause = findViewById(R.id.buttonPlayPause);
    }

    private void setupTabs() {
        addTab("songs", "歌曲");
        addTab("artists", "歌手");
        addTab("albums", "专辑");
        addTab("playlists", "歌单");
        addTab("search", "搜索");
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                setTabSelectedState(tab, true);
                currentMode = String.valueOf(tab.getTag());
                boolean keepHeader = internalModeSwitch && !TextUtils.isEmpty(pendingHeader);
                if (!internalModeSwitch) {
                    currentParentId = null;
                    currentParentType = null;
                    folderNavigationStack.clear();
                }
                internalModeSwitch = false;
                currentOffset = 0;
                hasMore = true;
                currentSortBy = defaultSortBy(currentMode);
                currentSortDirection = "ASC";
                currentKeyword = "search".equals(currentMode) ? editSearch.getText().toString().trim() : "";
                if ("search".equals(currentMode) && TextUtils.isEmpty(currentKeyword)) {
                    currentSortBy = "name";
                }
                textHeader.setText(keepHeader ? pendingHeader : String.valueOf(tab.getText()));
                pendingHeader = null;
                loadFirstPage(false);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                setTabSelectedState(tab, false);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                recyclerView.smoothScrollToPosition(0);
            }
        });
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) {
                setTabSelectedState(tab, tab.isSelected());
            }
        }
    }

    private void setupHomeTabs() {
        tabLayout.removeAllTabs();
        addTab("playlists", "歌单");
        addTab("songs", "歌曲");
        addTab("artists", "歌手");
        addTab("search", "搜索");
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                setTabSelectedState(tab, true);
                currentMode = String.valueOf(tab.getTag());
                boolean keepHeader = internalModeSwitch && !TextUtils.isEmpty(pendingHeader);
                if (!internalModeSwitch) {
                    currentParentId = null;
                    currentParentType = null;
                    folderNavigationStack.clear();
                }
                internalModeSwitch = false;
                currentOffset = 0;
                hasMore = true;
                currentSortBy = defaultSortBy(currentMode);
                currentSortDirection = "ASC";
                currentKeyword = "search".equals(currentMode) ? editSearch.getText().toString().trim() : "";
                if ("search".equals(currentMode) && TextUtils.isEmpty(currentKeyword)) {
                    currentSortBy = "name";
                }
                textHeader.setText(keepHeader ? pendingHeader : String.valueOf(tab.getText()));
                pendingHeader = null;
                saveHomeState();
                loadFirstPage(false);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                setTabSelectedState(tab, false);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                recyclerView.smoothScrollToPosition(0);
            }
        });
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) {
                setTabSelectedState(tab, tab.isSelected());
            }
        }
    }

    private void setupLists() {
        browserAdapter = new BrowserAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setVerticalScrollBarEnabled(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(browserAdapter);
        enableEdgeFastScroll(recyclerView);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || isRootSongsRequest()) {
                    updateScrollIndicator();
                    return;
                }
                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager != null && !isLoading && hasMore && manager.findLastVisibleItemPosition() >= browserItems.size() - 8) {
                    loadPage(false);
                }
                updateScrollIndicator();
            }
        });

        lyricsAdapter = new LyricsAdapter();
        lyricsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        lyricsRecyclerView.setAdapter(lyricsAdapter);
        lyricsRecyclerView.setItemAnimator(null);
        GestureDetector lyricsTapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return true;
            }
        });
        lyricsRecyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (!lyricsPageVisible) {
                    return false;
                }
                if (lyricsTapDetector.onTouchEvent(e)) {
                    showLyricsPage(false);
                    return true;
                }
                return false;
            }
        });
        lyricsRecyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top == oldBottom - oldTop) {
                return;
            }
            updateLyricsViewportPadding();
            if (currentLyricIndex >= 0) {
                centerLyricLine(currentLyricIndex, false);
            }
        });
        lyricsRecyclerView.post(this::updateLyricsViewportPadding);
    }

    private void enableEdgeFastScroll(RecyclerView targetView) {
        // Wider edge hotspot for curved displays so quick-scroll is easier to grab.
        final float edgeWidthPx = getResources().getDisplayMetrics().density * 56f;
        targetView.setOnTouchListener((v, event) -> {
            RecyclerView.Adapter<?> adapter = targetView.getAdapter();
            if (adapter == null || adapter.getItemCount() < 2) {
                return false;
            }
            int action = event.getActionMasked();
            float x = event.getX();
            boolean inEdge = x >= (targetView.getWidth() - edgeWidthPx);

            if (action == MotionEvent.ACTION_DOWN && inEdge) {
                isEdgeFastScrolling = true;
                targetView.getParent().requestDisallowInterceptTouchEvent(true);
                swipeRefreshLayout.setEnabled(false);
                fastScrollToTouch(targetView, event.getY(), adapter.getItemCount());
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE && isEdgeFastScrolling) {
                fastScrollToTouch(targetView, event.getY(), adapter.getItemCount());
                return true;
            }
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && isEdgeFastScrolling) {
                isEdgeFastScrolling = false;
                swipeRefreshLayout.setEnabled(true);
                progressHandler.postDelayed(hideIndexOverlayRunnable, 180L);
                return true;
            }
            return false;
        });
    }

    private void fastScrollToTouch(RecyclerView targetView, float touchY, int itemCount) {
        if (itemCount <= 0) {
            return;
        }
        float clamped = Math.max(0f, Math.min(touchY, targetView.getHeight()));
        float ratio = targetView.getHeight() <= 0 ? 0f : clamped / (float) targetView.getHeight();
        int position = Math.max(0, Math.min(itemCount - 1, Math.round((itemCount - 1) * ratio)));
        targetView.scrollToPosition(position);
        showIndexOverlayForPosition(position);
    }

    private void setupPlayer() {
        player = PlaybackService.getPlayer(this);
        player.setShuffleModeEnabled(shuffleEnabled);
        // Keep Media3 repeat off and let our queue logic handle next/loop behavior,
        // otherwise a single loaded media item may loop itself before queue advance runs.
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayButtons(isPlaying);
                SysMediaPlayer.getInstance(MainActivity.this).setPlaying(isPlaying);
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                syncPlayerSessionUi();
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED && suppressNextOnPlayerStop) {
                    suppressNextOnPlayerStop = false;
                    return;
                }
                if (playbackState == Player.STATE_IDLE && suppressNextOnPlayerStop) {
                    suppressNextOnPlayerStop = false;
                    return;
                }
                if (playbackState == Player.STATE_ENDED && !isSongCaching) {
                    // If the service is currently caching the next song (background continuation),
                    // do not interfere from UI when returning to foreground, otherwise we may rebuild
                    // the queue from the current page and jump to an unrelated track.
                    if (PlaybackService.isCachingActive()) {
                        Log.d(TAG, "Player ended but service is caching; skip UI auto-advance.");
                        return;
                    }
                    if (!uiActive) {
                        return;
                    }
                    Log.d(TAG, "Player ended (UI). repeatOne=" + repeatOneEnabled + " shuffle=" + shuffleEnabled + " repeatAll=" + repeatAllEnabled);
                    if (repeatOneEnabled && currentPlaying != null) {
                        replayCurrentSong();
                    } else {
                        playNext();
                    }
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "Player error: " + error.getErrorCodeName() + " " + error.getMessage(), error);
                toast("播放失败：" + error.getMessage());
            }
        });
        // Do not start PlaybackService here. Starting it during app bootstrap can delay
        // the service's foreground notification and trigger Android's timeout watchdog.
        updatePlaybackModeUi();
        progressHandler.post(progressRunnable);
    }

    private void setupActions() {
        swipeRefreshLayout.setOnRefreshListener(() -> loadFirstPage(true));
        buttonRefresh.setOnClickListener(v -> {
            if ("search".equals(currentMode) && !selectedSearchSongs.isEmpty()) {
                playlistDialogs.showSongsPlaylistChooser(selectedSearchSongs);
            } else {
                loadFirstPage(true);
            }
        });
        if (buttonRandomPlay != null) {
            buttonRandomPlay.setOnClickListener(v -> randomPlayCurrentPlaylist());
        }
        if (buttonPlaylistSearch != null) {
            buttonPlaylistSearch.setOnClickListener(v -> showPlaylistSearchCurrentPlaylist());
        }
        buttonSelectAll.setOnClickListener(v -> toggleSearchSelectAll());
        buttonSort.setOnClickListener(this::showSortMenu);
        buttonMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        miniPlayer.setOnClickListener(v -> {
            if (currentPlaying != null) {
                openFullPlayer();
            }
        });
        buttonMiniShuffle.setOnClickListener(v -> toggleShuffle());
        buttonMiniRepeat.setOnClickListener(v -> toggleRepeatMode());
        buttonMiniPlay.setOnClickListener(v -> togglePlayPause());
        buttonMiniNext.setOnClickListener(v -> playNext());
        buttonShuffle.setOnClickListener(v -> toggleShuffle());
        buttonRepeat.setOnClickListener(v -> toggleRepeatMode());
        buttonRepeatOne.setOnClickListener(v -> toggleRepeatOneMode());
        buttonPlayPause.setOnClickListener(v -> togglePlayPause());
        findViewById(R.id.buttonPrev).setOnClickListener(v -> playPrevious());
        findViewById(R.id.buttonNext).setOnClickListener(v -> playNext());
        findViewById(R.id.buttonClosePlayer).setOnClickListener(v -> hideFullPlayer());
        findViewById(R.id.buttonHistory).setOnClickListener(v -> showNowPlayingQueueDialog());
        imagePlayerCover.setOnClickListener(v -> showLyricsPage(true));
        playerPageLyrics.setOnClickListener(v -> showLyricsPage(false));
        imageLyricsBackdrop.setOnClickListener(v -> showLyricsPage(false));
        lyricsRecyclerView.setOnClickListener(v -> showLyricsPage(false));
        if (playerInfoRow != null) {
            playerInfoRow.setOnTouchListener((v, event) -> true);
        }
        if (textPlayerTitle != null) {
            textPlayerTitle.setOnTouchListener((v, event) -> true);
        }
        if (textPlayerArtist != null) {
            textPlayerArtist.setOnTouchListener((v, event) -> true);
        }
        if (textPlayerQuality != null) {
            textPlayerQuality.setOnTouchListener((v, event) -> true);
        }
        if (textPlayerLyricLine != null) {
            textPlayerLyricLine.setOnTouchListener((v, event) -> true);
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && currentPlaying != null) {
                    long duration = Math.max(player.getDuration(), currentPlaying.durationMs);
                    textCurrentTime.setText(formatDuration(duration * progress / 1000L));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (currentPlaying == null) {
                    return;
                }
                long duration = Math.max(player.getDuration(), currentPlaying.durationMs);
                long position = duration * seekBar.getProgress() / 1000L;
                player.seekTo(position);
                PlaybackService.updatePlaybackProgress(MainActivity.this, position, duration);
                SysMediaPlayer.getInstance(MainActivity.this).updatePlaybackProgress(position, duration);
            }
        });

        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            boolean submit = actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (!submit) {
                return false;
            }
            if (!"search".equals(currentMode)) {
                selectTabByMode("search");
            } else {
                currentKeyword = editSearch.getText().toString().trim();
                loadFirstPage(false);
            }
            return true;
        });
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if ("search".equals(currentMode) && s.length() == 0) {
                    currentKeyword = "";
                    loadFirstPage(false);
                }
            }
        });
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1002);
    }

    private void applyWindowInsets() {
        final int fullPlayerLeft = fullPlayer.getPaddingLeft();
        final int fullPlayerTop = fullPlayer.getPaddingTop();
        final int fullPlayerRight = fullPlayer.getPaddingRight();
        final int fullPlayerBottom = fullPlayer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(fullPlayer, (view, insets) -> {
            WindowInsetsCompat bars = insets;
            int topInset = bars.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottomInset = bars.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            view.setPadding(
                    fullPlayerLeft,
                    fullPlayerTop + topInset,
                    fullPlayerRight,
                    fullPlayerBottom + Math.max(bottomInset, 12)
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(fullPlayer);
    }

    private void setupDrawer() {
        refreshDrawerAccount();
        // Avatar feature removed: drawer keeps a fixed Apple Music-ish logo.
        actionDrawerSettings.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            startActivity(new Intent(this, SettingsActivity.class));
        });
        actionDrawerPlaylists.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            playlistDialogs.showPlaylistManager();
        });
        actionDrawerOffline.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            startActivity(new Intent(this, OfflineCacheActivity.class));
        });
        if (actionDrawerCacheManager != null) {
            actionDrawerCacheManager.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, CacheManagerActivity.class));
            });
        }
        if (actionDrawerAudioExclusive != null && actionDrawerAudioShared != null) {
            View.OnClickListener audioTabClick = v -> setConcurrentAudioPlayback(v == actionDrawerAudioShared);
            actionDrawerAudioExclusive.setOnClickListener(audioTabClick);
            actionDrawerAudioShared.setOnClickListener(audioTabClick);
            refreshAudioConflictTabs();
        }
        if (actionDrawerAbout != null) {
            actionDrawerAbout.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                showAboutDialog();
            });
        }
        actionDrawerLogout.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            // Exit app, do not log out.
            stopAllPendingPlayback();
            PlaybackService.stopSession(this);
            finishAffinity();
        });
    }

    private void refreshDrawerAccount() {
        String account = apiClient.prefs().getString(ApiClient.KEY_ACCOUNT, "test");
        String host = apiClient.prefs().getString(ApiClient.KEY_HOST, "https://frp-sea.com:46876");
        if (TextUtils.isEmpty(host)) {
            host = "https://frp-sea.com:46876";
        }
        textDrawerAccount.setText(account + "\n" + host.replace("https://", "").replace("http://", ""));
    }

    private void refreshAudioConflictTabs() {
        if (actionDrawerAudioExclusive == null || actionDrawerAudioShared == null) {
            return;
        }
        boolean allowShared = apiClient.isConcurrentPlaybackAllowed();
        applyAudioTabState(actionDrawerAudioExclusive, !allowShared);
        applyAudioTabState(actionDrawerAudioShared, allowShared);
    }

    private void applyAudioTabState(TextView tab, boolean active) {
        if (tab == null) {
            return;
        }
        tab.setBackgroundResource(active ? R.drawable.bg_tab_selected_pill : R.drawable.bg_chip_soft);
        tab.setTextColor(ContextCompat.getColor(this, active ? R.color.white : R.color.subtle));
    }

    private void setConcurrentAudioPlayback(boolean allowShared) {
        apiClient.setConcurrentPlaybackAllowed(allowShared);
        refreshAudioConflictTabs();
        PlaybackService.updateAudioFocusPolicy(this);
        toast(allowShared ? "已允许与其他媒体共存" : "已切换为独占播放");
    }

    // Avatar feature removed.

    private void showAccountSwitchDialog() {
        List<ApiClient.AccountProfile> profiles = apiClient.readAccountProfiles();
        if (profiles.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            toast("暂无已保存账号，请先登录");
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_account_switch, null, false);
        TextView textSubtitle = view.findViewById(R.id.textAccountSwitchSubtitle);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerAccountProfiles);
        TextView buttonLogout = view.findViewById(R.id.buttonAccountSwitchLogout);
        TextView buttonAdd = view.findViewById(R.id.buttonAccountSwitchAdd);
        TextView buttonClose = view.findViewById(R.id.buttonAccountSwitchClose);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        textSubtitle.setText("选择一个已保存账号快速切换");
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new AccountProfileAdapter(profiles, profile -> {
            dialog.dismiss();
            switchAccountProfile(profile);
        }));
        enableDialogEdgeFastScroll(recyclerView);

        buttonClose.setOnClickListener(v -> dialog.dismiss());
        buttonAdd.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, LoginActivity.class));
        });
        buttonLogout.setOnClickListener(v -> {
            dialog.dismiss();
            // Keep existing logout behavior (stop playback + clear sid).
            drawerLayout.closeDrawer(GravityCompat.START);
            PlaybackService.stopSession(this);
            apiClient.cancelSongCache();
            stopAllPendingPlayback();
            apiClient.prefs().edit().remove(ApiClient.KEY_SID).apply();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        dialog.show();
    }

    private final class AccountProfileAdapter extends RecyclerView.Adapter<AccountProfileAdapter.Holder> {
        private final List<ApiClient.AccountProfile> profiles;
        private final ProfileClickHandler handler;
        private final String currentHost = apiClient.prefs().getString(ApiClient.KEY_HOST, "");
        private final String currentAccount = apiClient.prefs().getString(ApiClient.KEY_ACCOUNT, "");

        AccountProfileAdapter(List<ApiClient.AccountProfile> profiles, ProfileClickHandler handler) {
            this.profiles = profiles == null ? new ArrayList<>() : profiles;
            this.handler = handler;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account_profile, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ApiClient.AccountProfile profile = profiles.get(position);
            holder.textTitle.setText(profile.account == null ? "" : profile.account);
            String hostLabel = (profile.host == null ? "" : profile.host)
                    .replace("https://", "")
                    .replace("http://", "");
            holder.textSubtitle.setText(hostLabel);
            StringBuilder meta = new StringBuilder();
            meta.append(profile.forceHttps ? "HTTPS" : "HTTP");
            meta.append(" \u00b7 ");
            meta.append(profile.ignoreCert ? "忽略证书" : "验证证书");
            if (!TextUtils.isEmpty(profile.password)) {
                meta.append(" · 已记住密码");
            }
            holder.textMeta.setText(meta.toString());
            boolean current = TextUtils.equals(currentHost, profile.host) && TextUtils.equals(currentAccount, profile.account);
            holder.imageCurrent.setVisibility(current ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> {
                if (handler != null) {
                    handler.onClick(profile);
                }
            });
        }

        @Override
        public int getItemCount() {
            return profiles.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView textTitle;
            final TextView textSubtitle;
            final TextView textMeta;
            final ImageView imageAvatar;
            final ImageView imageCurrent;

            Holder(@NonNull View itemView) {
                super(itemView);
                textTitle = itemView.findViewById(R.id.textAccountTitle);
                textSubtitle = itemView.findViewById(R.id.textAccountSubtitle);
                textMeta = itemView.findViewById(R.id.textAccountMeta);
                imageAvatar = itemView.findViewById(R.id.imageAccountAvatar);
                imageCurrent = itemView.findViewById(R.id.imageAccountCurrent);
            }
        }
    }

    private interface ProfileClickHandler {
        void onClick(ApiClient.AccountProfile profile);
    }

    private void switchAccountProfile(ApiClient.AccountProfile profile) {
        if (profile == null || TextUtils.isEmpty(profile.account) || TextUtils.isEmpty(profile.host)) {
            toast("账号信息不完整");
            return;
        }
        String normalizedHost = apiClient.normalizeHost(profile.host, profile.forceHttps);
        stopAllPendingPlayback();
        playQueue.clear();
        playQueueIndex = -1;
        playQueueContextKey = "";
        playQueueMode = null;
        playQueueParentId = null;
        playQueueParentType = null;
        playQueueKeyword = "";
        playQueueComplete = true;
        playQueueLoading = false;

        apiClient.prefs().edit()
                .putString(ApiClient.KEY_ACCOUNT, profile.account)
                .putString(ApiClient.KEY_PASSWORD, profile.password == null ? "" : profile.password)
                .putBoolean(ApiClient.KEY_FORCE_HTTPS, profile.forceHttps)
                .putBoolean(ApiClient.KEY_IGNORE_CERT, profile.ignoreCert)
                .putBoolean(ApiClient.KEY_REMEMBER_ACCOUNT, true)
                .putBoolean(ApiClient.KEY_REMEMBER_PASSWORD, !TextUtils.isEmpty(profile.password))
                .apply();

        toast("正在切换账号：" + profile.account);
        apiClient.login(normalizedHost, profile.account, profile.password == null ? "" : profile.password, profile.forceHttps, profile.ignoreCert, new ApiClient.JsonCallback() {
            @Override
            public void onSuccess(org.json.JSONObject json) {
                refreshDrawerAccount();
                browserItems.clear();
                browserAdapter.notifyDataSetChanged();
                loadFirstPage(true);
                toast("已切换到：" + profile.account);
            }

            @Override
            public void onError(String message) {
                toast(message);
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
            }
        });
    }

    private void showAboutDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_about, null, false);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        ((TextView) view.findViewById(R.id.textAboutTitle)).setText("DS Music");
        ((TextView) view.findViewById(R.id.textAboutSubtitle)).setText("mevoyacasa \u00b7 Codex");
        view.findViewById(R.id.buttonAboutClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void restoreInitialHomeState() {
        String savedParentType = apiClient.prefs().getString(KEY_LAST_HOME_PARENT_TYPE, "");
        String savedParentId = apiClient.prefs().getString(KEY_LAST_HOME_PARENT_ID, "");
        String savedHeader = apiClient.prefs().getString(KEY_LAST_HOME_HEADER, "");
        if ("playlist".equals(savedParentType) && !TextUtils.isEmpty(savedParentId)) {
            currentMode = "playlists";
            currentParentId = savedParentId;
            currentParentType = "playlist";
            currentSortBy = "title";
            currentSortDirection = "ASC";
            pendingHeader = TextUtils.isEmpty(savedHeader) ? "歌单" : savedHeader;
            internalModeSwitch = true;
            if (!selectTabByMode("playlists")) {
                internalModeSwitch = false;
                textHeader.setText(pendingHeader);
                pendingHeader = null;
                saveHomeState();
                loadFirstPage(false);
            }
            return;
        }

        currentParentId = null;
        currentParentType = null;
        String savedTab = apiClient.prefs().getString(KEY_LAST_HOME_TAB, "playlists");
        if ("folders".equals(savedTab)) {
            savedTab = "search";
        }
        currentMode = savedTab;
        currentSortBy = defaultSortBy(savedTab);
        currentSortDirection = "ASC";
        if (!selectTabByMode(savedTab)) {
            textHeader.setText(modeLabel(savedTab));
            saveHomeState();
            loadFirstPage(false);
        }
    }

    private void saveHomeState() {
        String tabMode = "playlist".equals(currentParentType) ? "playlists" : currentMode;
        apiClient.prefs().edit()
                .putString(KEY_LAST_HOME_TAB, tabMode == null ? "playlists" : tabMode)
                .putString(KEY_LAST_HOME_PARENT_ID, currentParentId == null ? "" : currentParentId)
                .putString(KEY_LAST_HOME_PARENT_TYPE, currentParentType == null ? "" : currentParentType)
                .putString(KEY_LAST_HOME_HEADER, textHeader == null ? "" : String.valueOf(textHeader.getText()))
                .apply();
    }

    private void handleIntentActions() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String playSongId = intent.getStringExtra("play_song_id");
        if (TextUtils.isEmpty(playSongId)) {
            return;
        }
        intent.removeExtra("play_song_id");
        intent.removeExtra("play_song_source");
        ApiClient.MediaItemModel target = findCachedSongById(playSongId);
        if (target != null) {
            playItem(target, apiClient.readCachedSongs());
        }
    }

    private void runDeferredResumeSync() {
        if (!uiActive || isFinishing()) {
            return;
        }
        syncPlayerSessionUi();
        resumeIfEndedStuck();
        handleIntentActions();
    }

    @Nullable
    private ApiClient.MediaItemModel findCachedSongById(String songId) {
        for (ApiClient.MediaItemModel item : apiClient.readCachedSongs()) {
            if (TextUtils.equals(item.id, songId)) {
                return item;
            }
        }
        for (ApiClient.MediaItemModel item : collectCachedSongs()) {
            if (TextUtils.equals(item.id, songId)) {
                return item;
            }
        }
        return null;
    }

    private String modeLabel(String mode) {
        if ("playlists".equals(mode)) {
            return "歌单";
        }
        if ("artists".equals(mode)) {
            return "歌手";
        }
        if ("folders".equals(mode)) {
            return "搜索";
        }
        if ("search".equals(mode)) {
            return "搜索";
        }
        return "歌曲";
    }

    private void syncPlayerSessionUi() {
        if (player == null) {
            return;
        }
        MediaItem mediaItem = player.getCurrentMediaItem();
        if (mediaItem != null) {
            // If a previous UI-triggered cache is still pending, do not allow it to auto-play and override
            // the actual background playback when returning to foreground.
            if (isSongCaching && currentCaching != null && !TextUtils.equals(currentCaching.id, mediaItem.mediaId)) {
                allowPendingCacheAutoPlay = false;
                ++cacheRequestToken; // invalidate any in-flight cache callbacks
                apiClient.cancelSongCache();
                currentCaching = null;
                isSongCaching = false;
            }
            ApiClient.MediaItemModel restored = currentPlaying != null
                    && TextUtils.equals(currentPlaying.id, mediaItem.mediaId)
                    ? currentPlaying
                    : new ApiClient.MediaItemModel();
            restored.id = mediaItem.mediaId;
            restored.type = "song";
            restored.title = mediaItem.mediaMetadata.title == null ? PlaybackService.getCurrentTitle() : String.valueOf(mediaItem.mediaMetadata.title);
            restored.artist = mediaItem.mediaMetadata.artist == null ? PlaybackService.getCurrentArtist() : String.valueOf(mediaItem.mediaMetadata.artist);
            restored.album = mediaItem.mediaMetadata.albumTitle == null ? "" : String.valueOf(mediaItem.mediaMetadata.albumTitle);
            restored.subtitle = restored.artist + (TextUtils.isEmpty(restored.album) ? "" : " \u00B7 " + restored.album);
            Object tag = mediaItem.localConfiguration == null ? null : mediaItem.localConfiguration.tag;
            restored.localCoverPath = tag == null ? PlaybackService.getCurrentCoverPath() : String.valueOf(tag);
            boolean songChanged = currentPlaying == null || !TextUtils.equals(currentPlaying.id, restored.id);
            currentPlaying = restored;
            currentCaching = null;
            isSongCaching = false;
            if (songChanged) {
                prepareLyricsForSong(restored.id);
            }
            updateNowPlayingUi();
            if (!TextUtils.isEmpty(restored.id) && !TextUtils.equals(restored.id, lyricSongId)) {
                requestLyrics(restored.id, ++playRequestToken);
            }
            return;
        }
        // Prefer UI-side caching display if user initiated a cache/play request in this activity.
        if (isSongCaching && currentCaching != null) {
            miniPlayer.setVisibility(View.VISIBLE);
            textMiniTitle.setText(currentCaching.title);
            textMiniSubtitle.setText("缓存中");
            if (!TextUtils.isEmpty(currentCaching.localCoverPath)) {
                loadLocalCoverInto(new File(currentCaching.localCoverPath), imageMiniCover, false, R.drawable.ic_music_note);
            }
            return;
        }
        if (PlaybackService.isCachingActive()) {
            if (currentCaching == null) {
                currentCaching = new ApiClient.MediaItemModel();
                currentCaching.title = PlaybackService.getCurrentTitle();
                currentCaching.artist = PlaybackService.getCurrentArtist();
                currentCaching.subtitle = firstNonEmpty(PlaybackService.getCurrentLyricLine(), PlaybackService.getCurrentArtist(), "缓存中");
                currentCaching.localCoverPath = PlaybackService.getCurrentCoverPath();
            }
            miniPlayer.setVisibility(View.VISIBLE);
            textMiniTitle.setText(currentCaching.title);
            textMiniSubtitle.setText("缓存中 " + PlaybackService.getCurrentPercent() + "%");
            if (!TextUtils.isEmpty(currentCaching.localCoverPath)) {
                loadLocalCoverInto(new File(currentCaching.localCoverPath), imageMiniCover, false, R.drawable.ic_music_note);
            }
            showCachingProgress(PlaybackService.getCurrentPercent());
            return;
        }
        if (currentPlaying == null) {
            miniPlayer.setVisibility(View.GONE);
        }
    }

    private void loadFirstPage(boolean forceRefresh) {
        currentOffset = 0;
        hasMore = true;
        browserItems.clear();
        selectedSearchSongs.clear();
        browserAdapter.notifyDataSetChanged();
        updateSearchUi();

        if (!forceRefresh && shouldUseCachedRootSongs()) {
            browserItems.addAll(apiClient.readRootSongCache());
            browserAdapter.notifyDataSetChanged();
                rootSongsLoadedFromCache = true;
                currentOffset = browserItems.size();
                hasMore = false;
                updateSummary(browserItems.size());
                updateSearchUi();
                if (!rootSongsPrefetchedThisSession) {
                rootSongsPrefetchedThisSession = true;
                loadPage(true);
            }
            return;
        }

        rootSongsLoadedFromCache = false;
        loadPage(forceRefresh);
    }

    private void loadPage(boolean forceRefresh) {
        if (isLoading) {
            return;
        }
        if ("search".equals(currentMode)) {
            currentKeyword = editSearch.getText().toString().trim();
            if (TextUtils.isEmpty(currentKeyword)) {
                currentSortBy = "name";
            }
        }
        if (forceRefresh && isRootSongsRequest()) {
            rootSongsLoadedFromCache = false;
        }
        isLoading = true;
        swipeRefreshLayout.setRefreshing(true);
            apiClient.fetchBrowserItems(effectiveBrowserMode(), currentParentId, currentParentType, currentKeyword, currentOffset, PAGE_SIZE, currentSortBy, currentSortDirection, new ApiClient.ArrayCallback() {
                @Override
                public void onSuccess(List<ApiClient.MediaItemModel> items, boolean more, int total) {
                    isLoading = false;
                    swipeRefreshLayout.setRefreshing(false);
                    int insertStart = browserItems.size();
                    if (currentOffset == 0) {
                        browserItems.clear();
                    }
                    browserItems.addAll(items);
                    if (currentOffset == 0) {
                        browserAdapter.notifyDataSetChanged();
                    } else if (!items.isEmpty()) {
                        browserAdapter.notifyItemRangeInserted(insertStart, items.size());
                    }
                    currentOffset += items.size();
                    hasMore = more;
                    if (isRootSongsRequest()) {
                        apiClient.saveRootSongCache(browserItems);
                        rootSongsPrefetchedThisSession = true;
                }
                updateSummary(total);
                updateSearchUi();
            }

            @Override
            public void onError(String message) {
                isLoading = false;
                swipeRefreshLayout.setRefreshing(false);
                if (browserItems.isEmpty() && shouldUseCachedRootSongs()) {
                    browserItems.addAll(apiClient.readRootSongCache());
                    browserAdapter.notifyDataSetChanged();
                    rootSongsLoadedFromCache = true;
                }
                updateSummary(browserItems.size());
                updateSearchUi();
                toast(message);
            }
        });
    }

    private boolean shouldUseCachedRootSongs() {
        return isRootSongsRequest() && !apiClient.readRootSongCache().isEmpty();
    }

    private boolean isRootSongsRequest() {
        return "songs".equals(currentMode)
                && TextUtils.isEmpty(currentParentId)
                && TextUtils.isEmpty(currentParentType)
                && TextUtils.isEmpty(currentKeyword);
    }

    private void updateSummary(int total) {
        textSummary.setText("");
        textEmpty.setVisibility(browserItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateSearchUi() {
        boolean searchMode = "search".equals(currentMode);
        if (searchContainer != null) {
            searchContainer.setVisibility(searchMode ? View.VISIBLE : View.GONE);
        }
        if (buttonRefresh instanceof TextView) {
            TextView actionView = (TextView) buttonRefresh;
            if (searchMode && !selectedSearchSongs.isEmpty()) {
                actionView.setText("添加到歌单");
            } else {
                actionView.setText("刷新");
            }
        }
        if (buttonSelectAll != null) {
            List<ApiClient.MediaItemModel> searchSongs = searchSongItems();
            if (searchMode && !searchSongs.isEmpty()) {
                buttonSelectAll.setVisibility(View.VISIBLE);
                buttonSelectAll.setText(selectedSearchSongs.size() == searchSongs.size() ? "取消全选" : "全选");
            } else {
                buttonSelectAll.setVisibility(View.GONE);
            }
        }
        if (buttonRandomPlay != null) {
            buttonRandomPlay.setVisibility(isPlaylistSongsView() ? View.VISIBLE : View.GONE);
        }
        if (buttonPlaylistSearch != null) {
            buttonPlaylistSearch.setVisibility(isPlaylistSongsView() ? View.VISIBLE : View.GONE);
        }
    }

    private void toggleSearchSelectAll() {
        List<ApiClient.MediaItemModel> songs = searchSongItems();
        if (songs.isEmpty()) {
            return;
        }
        if (selectedSearchSongs.size() == songs.size()) {
            selectedSearchSongs.clear();
        } else {
            selectedSearchSongs.clear();
            selectedSearchSongs.addAll(songs);
        }
        updateSearchUi();
        if (browserAdapter != null) {
            browserAdapter.notifyDataSetChanged();
        }
    }

    private List<ApiClient.MediaItemModel> searchSongItems() {
        List<ApiClient.MediaItemModel> songs = new ArrayList<>();
        if (!"search".equals(currentMode)) {
            return songs;
        }
        for (ApiClient.MediaItemModel item : browserItems) {
            if ("song".equals(item.type)) {
                songs.add(item);
            }
        }
        return songs;
    }

    private boolean isPlaylistSongsView() {
        return "playlists".equals(currentMode)
                && "playlist".equals(currentParentType)
                && !TextUtils.isEmpty(currentParentId);
    }

    private void setPlaylistActionButtonsEnabled(boolean enabled) {
        if (buttonRandomPlay != null) {
            buttonRandomPlay.setEnabled(enabled);
        }
        if (buttonPlaylistSearch != null) {
            buttonPlaylistSearch.setEnabled(enabled);
        }
    }

    private void showPlaylistSearchCurrentPlaylist() {
        if (!isPlaylistSongsView()) {
            return;
        }
        String playlistId = currentParentId;
        if (TextUtils.isEmpty(playlistId)) {
            return;
        }
        String playlistTitle = String.valueOf(textHeader.getText());
        setPlaylistActionButtonsEnabled(false);
        apiClient.fetchAllPlaylistSongs(playlistId, new ApiClient.ArrayCallback() {
            @Override
            public void onSuccess(List<ApiClient.MediaItemModel> items, boolean hasMore, int total) {
                setPlaylistActionButtonsEnabled(true);
                if (!isPlaylistSongsView() || !TextUtils.equals(playlistId, currentParentId)) {
                    return;
                }
                ArrayList<ApiClient.MediaItemModel> songs = collectPlayablePlaylistSongs(items);
                if (songs.isEmpty()) {
                    toast("歌单里没有可搜索歌曲");
                    return;
                }
                showPlaylistSearchDialog(playlistTitle, songs);
            }

            @Override
            public void onError(String message) {
                setPlaylistActionButtonsEnabled(true);
                toast(message);
            }
        });
    }

    private void showPlaylistSearchDialog(String playlistTitle, List<ApiClient.MediaItemModel> songs) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_song_picker, null, false);
        TextView textTitle = view.findViewById(R.id.textPickerTitle);
        TextView textSubtitle = view.findViewById(R.id.textPickerSubtitle);
        EditText editSearch = view.findViewById(R.id.editPickerSearch);
        TextView buttonSortName = view.findViewById(R.id.buttonSortName);
        TextView buttonSortDuration = view.findViewById(R.id.buttonSortDuration);
        TextView buttonSortArtist = view.findViewById(R.id.buttonSortArtist);
        TextView buttonSelectAll = view.findViewById(R.id.buttonPickerSelectAll);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerPicker);
        TextView buttonCancel = view.findViewById(R.id.buttonPickerCancel);
        TextView buttonExtra = view.findViewById(R.id.buttonPickerExtra);
        TextView buttonConfirm = view.findViewById(R.id.buttonPickerConfirm);

        textTitle.setText("搜索《" + playlistTitle + "》");
        textSubtitle.setText("在当前歌单内查找歌曲 · 共 " + songs.size() + " 首");
        buttonCancel.setText("关闭");
        buttonConfirm.setVisibility(View.GONE);
        buttonExtra.setVisibility(View.GONE);
        buttonSelectAll.setVisibility(View.GONE);

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        PlaylistSearchAdapter adapter = new PlaylistSearchAdapter(songs, item -> {
            if (dialogHolder[0] != null) {
                dialogHolder[0].dismiss();
            }
            startPlaylistPlaybackFromSongs(songs, item, "已播放");
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setVerticalScrollBarEnabled(true);
        recyclerView.setItemAnimator(null);
        enableEdgeFastScroll(recyclerView);
        recyclerView.setAdapter(adapter);

        bindPlaylistSearchSortChip(buttonSortName, buttonSortDuration, buttonSortArtist, adapter, PlaylistSearchAdapter.SORT_NAME);
        buttonSortName.setOnClickListener(v -> bindPlaylistSearchSortChip(buttonSortName, buttonSortDuration, buttonSortArtist, adapter, PlaylistSearchAdapter.SORT_NAME));
        buttonSortDuration.setOnClickListener(v -> bindPlaylistSearchSortChip(buttonSortName, buttonSortDuration, buttonSortArtist, adapter, PlaylistSearchAdapter.SORT_DURATION));
        buttonSortArtist.setOnClickListener(v -> bindPlaylistSearchSortChip(buttonSortName, buttonSortDuration, buttonSortArtist, adapter, PlaylistSearchAdapter.SORT_ARTIST));

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                adapter.filter(s == null ? "" : s.toString());
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        dialogHolder[0] = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        buttonCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void bindPlaylistSearchSortChip(TextView name, TextView duration, TextView artist, PlaylistSearchAdapter adapter, int sortType) {
        name.setBackgroundResource(sortType == PlaylistSearchAdapter.SORT_NAME ? R.drawable.bg_button : R.drawable.bg_chip_soft);
        duration.setBackgroundResource(sortType == PlaylistSearchAdapter.SORT_DURATION ? R.drawable.bg_button : R.drawable.bg_chip_soft);
        artist.setBackgroundResource(sortType == PlaylistSearchAdapter.SORT_ARTIST ? R.drawable.bg_button : R.drawable.bg_chip_soft);

        int activeColor = ContextCompat.getColor(this, android.R.color.white);
        int normalColor = ContextCompat.getColor(this, R.color.seed);
        name.setTextColor(sortType == PlaylistSearchAdapter.SORT_NAME ? activeColor : normalColor);
        duration.setTextColor(sortType == PlaylistSearchAdapter.SORT_DURATION ? activeColor : normalColor);
        artist.setTextColor(sortType == PlaylistSearchAdapter.SORT_ARTIST ? activeColor : normalColor);
        adapter.sortBy(sortType);
    }

    private void randomPlayCurrentPlaylist() {
        if (!isPlaylistSongsView()) {
            return;
        }
        String playlistId = currentParentId;
        if (TextUtils.isEmpty(playlistId)) {
            return;
        }
        setPlaylistActionButtonsEnabled(false);
        apiClient.fetchAllPlaylistSongs(playlistId, new ApiClient.ArrayCallback() {
            @Override
            public void onSuccess(List<ApiClient.MediaItemModel> items, boolean hasMore, int total) {
                setPlaylistActionButtonsEnabled(true);
                if (!isPlaylistSongsView() || !TextUtils.equals(playlistId, currentParentId)) {
                    return;
                }
                ArrayList<ApiClient.MediaItemModel> songs = collectPlayablePlaylistSongs(items);
                if (songs.isEmpty()) {
                    toast("歌单里没有可播放歌曲");
                    return;
                }
                int index = (int) (Math.random() * songs.size());
                startPlaylistPlaybackFromSongs(songs, songs.get(index), "已随机播放");
            }

            @Override
            public void onError(String message) {
                setPlaylistActionButtonsEnabled(true);
                toast(message);
            }
        });
    }

    private ArrayList<ApiClient.MediaItemModel> collectPlayablePlaylistSongs(@Nullable List<ApiClient.MediaItemModel> items) {
        ArrayList<ApiClient.MediaItemModel> songs = new ArrayList<>();
        if (items == null) {
            return songs;
        }
        for (ApiClient.MediaItemModel item : items) {
            if (item != null && "song".equals(item.type) && !TextUtils.isEmpty(item.id)) {
                songs.add(item);
            }
        }
        return songs;
    }

    private void startPlaylistPlaybackFromSongs(List<ApiClient.MediaItemModel> songs, ApiClient.MediaItemModel target, @Nullable String toastPrefix) {
        if (songs == null || songs.isEmpty() || target == null || TextUtils.isEmpty(target.id)) {
            return;
        }
        ArrayList<ApiClient.MediaItemModel> queue = new ArrayList<>(songs.size());
        for (ApiClient.MediaItemModel item : songs) {
            if (item != null && "song".equals(item.type) && !TextUtils.isEmpty(item.id)) {
                queue.add(item);
            }
        }
        if (queue.isEmpty()) {
            return;
        }
        sortPlaylistQueue(queue);
        int index = findQueueIndexByIdInList(queue, target.id);
        if (index < 0) {
            queue.add(0, target);
            index = 0;
        }
        playQueueVersion++;
        playQueueLoading = false;
        playQueue.clear();
        playQueue.addAll(queue);
        playQueueIndex = index;
        playQueueMode = effectiveBrowserMode();
        playQueueParentId = currentParentId;
        playQueueParentType = currentParentType;
        playQueueKeyword = currentKeyword;
        playQueueSortBy = currentSortBy;
        playQueueSortDirection = currentSortDirection;
        playQueueComplete = true;
        playQueueContextKey = currentBrowserQueueContextKey();
        pendingForceNextSongId = null;
        playItem(playQueue.get(playQueueIndex), playQueue);
        if (!TextUtils.isEmpty(toastPrefix)) {
            toast(toastPrefix + "：" + target.title);
        }
    }

    private void sortPlaylistQueue(List<ApiClient.MediaItemModel> songs) {
        if (songs == null || songs.size() <= 1) {
            return;
        }
        Collections.sort(songs, playlistQueueComparator());
    }

    private Comparator<ApiClient.MediaItemModel> playlistQueueComparator() {
        Comparator<ApiClient.MediaItemModel> comparator;
        String sortBy = TextUtils.isEmpty(currentSortBy) ? "title" : currentSortBy;
        if ("duration".equals(sortBy)) {
            comparator = Comparator.comparingLong((ApiClient.MediaItemModel item) -> item.durationMs);
        } else if ("artist".equals(sortBy)) {
            comparator = Comparator.comparing((ApiClient.MediaItemModel item) -> safeText(item.artist), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing((ApiClient.MediaItemModel item) -> safeText(item.title), String.CASE_INSENSITIVE_ORDER);
        } else if ("album".equals(sortBy)) {
            comparator = Comparator.comparing((ApiClient.MediaItemModel item) -> safeText(item.album), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing((ApiClient.MediaItemModel item) -> safeText(item.title), String.CASE_INSENSITIVE_ORDER);
        } else {
            comparator = Comparator.comparing((ApiClient.MediaItemModel item) -> safeText(item.title), String.CASE_INSENSITIVE_ORDER);
        }
        if (!"DESC".equalsIgnoreCase(currentSortDirection)) {
            return comparator;
        }
        return comparator.reversed();
    }

    private int findQueueIndexByIdInList(List<ApiClient.MediaItemModel> songs, String songId) {
        if (songs == null || TextUtils.isEmpty(songId)) {
            return -1;
        }
        for (int i = 0; i < songs.size(); i++) {
            ApiClient.MediaItemModel item = songs.get(i);
            if (item != null && TextUtils.equals(item.id, songId)) {
                return i;
            }
        }
        return -1;
    }

    private static String safeText(String value) {
        return TextUtils.isEmpty(value) ? "" : value;
    }

    private static final class PlaylistSearchAdapter extends RecyclerView.Adapter<PlaylistSearchAdapter.Holder> {

        static final int SORT_NAME = 0;
        static final int SORT_DURATION = 1;
        static final int SORT_ARTIST = 2;

        interface SongClickListener {
            void onSongClick(ApiClient.MediaItemModel item);
        }

        private final List<ApiClient.MediaItemModel> source = new ArrayList<>();
        private final List<ApiClient.MediaItemModel> display = new ArrayList<>();
        @Nullable
        private final SongClickListener songClickListener;
        private String keyword = "";
        private int sortType = SORT_NAME;

        PlaylistSearchAdapter(List<ApiClient.MediaItemModel> items, @Nullable SongClickListener songClickListener) {
            if (items != null) {
                source.addAll(items);
            }
            this.songClickListener = songClickListener;
            rebuild();
        }

        void filter(String key) {
            keyword = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
            rebuild();
        }

        void sortBy(int sortType) {
            this.sortType = sortType;
            rebuild();
        }

        private void rebuild() {
            display.clear();
            for (ApiClient.MediaItemModel item : source) {
                if (item == null || TextUtils.isEmpty(item.id) || !"song".equals(item.type)) {
                    continue;
                }
                if (TextUtils.isEmpty(keyword)) {
                    display.add(item);
                    continue;
                }
                String search = (safeText(item.title) + " " + safeText(item.artist) + " " + safeText(item.album)).toLowerCase(Locale.ROOT);
                if (search.contains(keyword)) {
                    display.add(item);
                }
            }

            Comparator<ApiClient.MediaItemModel> comparator;
            if (sortType == SORT_DURATION) {
                comparator = Comparator.comparingLong((ApiClient.MediaItemModel item) -> item.durationMs);
            } else if (sortType == SORT_ARTIST) {
                comparator = Comparator.comparing((ApiClient.MediaItemModel item) -> safeText(item.artist), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing((ApiClient.MediaItemModel item) -> safeText(item.title), String.CASE_INSENSITIVE_ORDER);
            } else {
                comparator = Comparator.comparing((ApiClient.MediaItemModel item) -> safeText(item.title), String.CASE_INSENSITIVE_ORDER);
            }
            Collections.sort(display, comparator);
            notifyDataSetChanged();
        }

        private static String formatDurationText(long durationMs) {
            if (durationMs <= 0L) {
                return "--:--";
            }
            long totalSeconds = durationMs / 1000L;
            long hours = totalSeconds / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            long seconds = totalSeconds % 60L;
            if (hours > 0L) {
                return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
            }
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song_picker, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ApiClient.MediaItemModel item = display.get(position);
            holder.textTitle.setText(safeText(item.title));
            holder.textSubtitle.setText(safeText(item.artist));
            holder.textDuration.setText(formatDurationText(item.durationMs));
            holder.checkBox.setVisibility(View.GONE);
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.itemView.setOnClickListener(v -> {
                if (songClickListener != null) {
                    songClickListener.onSongClick(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return display.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView textTitle;
            final TextView textSubtitle;
            final TextView textDuration;
            final CheckBox checkBox;

            Holder(@NonNull View itemView) {
                super(itemView);
                textTitle = itemView.findViewById(R.id.textSongPickTitle);
                textSubtitle = itemView.findViewById(R.id.textSongPickSubtitle);
                textDuration = itemView.findViewById(R.id.textSongPickDuration);
                checkBox = itemView.findViewById(R.id.checkSongPick);
            }
        }
    }

    private void updateScrollIndicator() {
        if (!isEdgeFastScrolling || browserItems.isEmpty()) {
            hideIndexOverlay();
            return;
        }
        RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) {
            return;
        }
        int first = ((LinearLayoutManager) manager).findFirstVisibleItemPosition();
        if (first < 0 || first >= browserItems.size()) {
            return;
        }
        showIndexOverlayForPosition(first);
    }

    private void showIndexOverlayForPosition(int position) {
        if (textIndexOverlay == null || viewIndexOverlayScrim == null || browserItems.isEmpty()) {
            return;
        }
        int safePosition = Math.max(0, Math.min(position, browserItems.size() - 1));
        ApiClient.MediaItemModel item = browserItems.get(safePosition);
        String token;
        if ("artist".equals(currentSortBy)) {
            token = indexToken(item.artist);
        } else if ("album".equals(currentSortBy)) {
            token = indexToken(item.album);
        } else {
            token = indexToken(item.title);
        }
        textIndexOverlay.setText(token);
        if (textIndexOverlay.getVisibility() != View.VISIBLE) {
            viewIndexOverlayScrim.setVisibility(View.VISIBLE);
            textIndexOverlay.setVisibility(View.VISIBLE);
            viewIndexOverlayScrim.animate().alpha(1f).setDuration(120L).start();
            textIndexOverlay.animate().alpha(1f).setDuration(120L).start();
        }
        progressHandler.removeCallbacks(hideIndexOverlayRunnable);
        progressHandler.postDelayed(hideIndexOverlayRunnable, 550L);
    }

    private void hideIndexOverlay() {
        if (textIndexOverlay == null || viewIndexOverlayScrim == null) {
            return;
        }
        progressHandler.removeCallbacks(hideIndexOverlayRunnable);
        if (textIndexOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        viewIndexOverlayScrim.animate().alpha(0f).setDuration(160L).withEndAction(() -> viewIndexOverlayScrim.setVisibility(View.GONE)).start();
        textIndexOverlay.animate().alpha(0f).setDuration(160L).withEndAction(() -> textIndexOverlay.setVisibility(View.GONE)).start();
    }

    private String indexToken(String value) {
        if (TextUtils.isEmpty(value)) {
            return "#";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "#";
        }
        return trimmed.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private void playItem(ApiClient.MediaItemModel item, List<ApiClient.MediaItemModel> source) {
        if (item == null) {
            return;
        }
        if (!"song".equals(item.type)) {
            drillDown(item);
            return;
        }
        int transitionToken = ++playTransitionToken;
        progressHandler.postDelayed(() -> {
            if (transitionToken != playTransitionToken) {
                return;
            }
            executePlayItem(item, source);
        }, 24L);
    }

    private void executePlayItem(ApiClient.MediaItemModel item, List<ApiClient.MediaItemModel> source) {
        PlaybackService.ensureStartedForPlayback(this);
        preparePlayQueue(item, source);
        prepareLyricsForSong(item == null ? null : item.id);

        if (cachedSongIds.contains(item.id)) {
            cancelActiveCaching();
            PlaybackService.cancelServiceCaching(this);
            int requestToken = ++playRequestToken;
            currentPlaying = item;
            playCachedSong(item);
            scheduleNowPlayingUiRefresh(item.id, requestToken);
            return;
        }
        beginCachingPlayback(item);
    }

    private void showSongQueueActionMenu(View anchor, ApiClient.MediaItemModel item) {
        if (item == null || !"song".equals(item.type)) {
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "下一首播放");
        menu.getMenu().add(0, 2, 1, "添加到正在播放列表");
        if ("playlist".equals(currentParentType) && !TextUtils.isEmpty(currentParentId)) {
            menu.getMenu().add(0, 3, 2, "从列表移除");
        }
        menu.setOnMenuItemClickListener(menuItem -> {
            int action = menuItem.getItemId();
            if (action == 1) {
                enqueueSongIntoNowPlaying(item, true);
                return true;
            }
            if (action == 2) {
                enqueueSongIntoNowPlaying(item, false);
                return true;
            }
            if (action == 3) {
                removeSongFromCurrentPlaylist(item);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void removeSongFromCurrentPlaylist(ApiClient.MediaItemModel item) {
        if (item == null || TextUtils.isEmpty(item.id) || !"playlist".equals(currentParentType) || TextUtils.isEmpty(currentParentId)) {
            return;
        }
        String playlistName = currentPlaylistName();
        String songName = firstNonEmpty(item.title, item.id, "歌曲");
        apiClient.removeSongFromPlaylist(currentParentId, item.id, new ApiClient.StringCallback() {
            @Override
            public void onSuccess(String value) {
                toast(songName + "从" + playlistName + "移除成功");
                loadFirstPage(false);
            }

            @Override
            public void onError(String message) {
                toast(songName + "从" + playlistName + "移除失败：" + message);
            }
        });
    }

    private String currentPlaylistName() {
        if (textHeader == null) {
            return "歌单";
        }
        String value = String.valueOf(textHeader.getText());
        return TextUtils.isEmpty(value) ? "歌单" : value;
    }

    private void enqueueSongIntoNowPlaying(ApiClient.MediaItemModel item, boolean nextPlay) {
        if (item == null || !"song".equals(item.type)) {
            return;
        }
        if (playQueue.isEmpty()) {
            if (currentPlaying != null && "song".equals(currentPlaying.type)) {
                playQueue.add(currentPlaying);
                playQueueIndex = 0;
            }
        }
        if (playQueue.isEmpty()) {
            playQueue.add(item);
            playQueueIndex = 0;
            playItem(item, playQueue);
            toast("已添加到正在播放列表");
            return;
        }

        if (currentPlaying != null) {
            int currentIndex = findQueueIndexById(currentPlaying.id);
            if (currentIndex >= 0) {
                playQueueIndex = currentIndex;
            }
        }
        if (playQueueIndex < 0 || playQueueIndex >= playQueue.size()) {
            playQueueIndex = 0;
        }

        int existingIndex = findQueueIndexById(item.id);
        if (existingIndex >= 0) {
            playQueue.remove(existingIndex);
            if (existingIndex < playQueueIndex) {
                playQueueIndex--;
            } else if (existingIndex == playQueueIndex) {
                playQueueIndex = Math.max(0, Math.min(playQueueIndex, playQueue.size() - 1));
            }
        }

        int insertIndex = nextPlay
                ? Math.min(playQueueIndex + 1, playQueue.size())
                : playQueue.size();
        playQueue.add(insertIndex, item);
        if (nextPlay) {
            pendingForceNextSongId = item.id;
        }

        if (currentPlaying != null) {
            int currentIndex = findQueueIndexById(currentPlaying.id);
            if (currentIndex >= 0) {
                playQueueIndex = currentIndex;
            }
        }
        syncQueueToService(currentPlaying == null ? null : currentPlaying.id);

        toast(nextPlay
                ? "已设置下一首：" + item.title
                : "已添加到正在播放列表：" + item.title);
    }

    private void beginCachingPlayback(ApiClient.MediaItemModel item) {
        cancelActiveCaching();
        PlaybackService.cancelServiceCaching(this);
        allowPendingCacheAutoPlay = true;
        currentPlaying = item;
        currentCaching = item;
        ++playRequestToken;
        stopPlayerForManualTransition();
        startCachingSong(item);
        scheduleNowPlayingUiRefresh(item.id, playRequestToken);
    }

    private void playCachedSong(ApiClient.MediaItemModel item) {
        playCachedSong(item, null);
    }

    private void playCachedSong(ApiClient.MediaItemModel item, @Nullable String cacheValue) {
        Uri cacheUri = resolveCacheUri(cacheValue);
        if (cacheUri == null) {
            cacheUri = apiClient.getSongCacheReadUri(item);
        }
        if (cacheUri == null) {
            beginCachingPlayback(item);
            return;
        }
        isSongCaching = false;
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist)
                .setAlbumTitle(item.album)
                .build();
        player.setMediaItem(new MediaItem.Builder()
                .setUri(cacheUri)
                .setMediaId(item.id)
                .setMediaMetadata(metadata)
                .setTag(item.localCoverPath == null ? "" : item.localCoverPath)
                .build());
        player.prepare();
        player.play();
        apiClient.pushHistory(item);
        clearCachingProgress();
        PlaybackService.updatePlaybackState(this, item.title, item.artist, currentNotificationLine(), item.localCoverPath);
        refreshBrowserSongState(item.id);
        syncQueueToService(item == null ? null : item.id);
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

    private void startCachingSong(ApiClient.MediaItemModel item) {
        int requestToken = ++cacheRequestToken;
        currentCaching = item;
        apiClient.cancelSongCache();
        isSongCaching = true;
        showCachingProgress(0);
        ensureCacheWorker();
        cacheWorkerHandler.post(() -> {
            if (requestToken != cacheRequestToken || currentCaching == null || !TextUtils.equals(currentCaching.id, item.id)) {
                return;
            }
            apiClient.cacheSongToConfiguredDirectory(item, new ApiClient.CacheCallback() {
                @Override
                public void onProgress(int percent) {
                    postToMain(() -> {
                        if (requestToken != cacheRequestToken || currentCaching == null || !TextUtils.equals(currentCaching.id, item.id)) {
                            return;
                        }
                        showCachingProgress(percent);
                    });
                }

                @Override
                public void onSuccess(String value) {
                    postToMain(() -> {
                        if (requestToken != cacheRequestToken || currentCaching == null || !TextUtils.equals(currentCaching.id, item.id)) {
                            return;
                        }
                        isSongCaching = false;
                        currentCaching = null;
                        cachedSongIds.add(item.id);
                        refreshBrowserSongState(item.id);
                        if (!allowPendingCacheAutoPlay) {
                            clearCachingProgress();
                            return;
                        }
                        int playToken = ++playRequestToken;
                        currentPlaying = item;
                        playCachedSong(item, value);
                        scheduleNowPlayingUiRefresh(item.id, playToken);
                    });
                }

                @Override
                public void onError(String message) {
                    postToMain(() -> {
                        if (requestToken != cacheRequestToken || currentCaching == null || !TextUtils.equals(currentCaching.id, item.id)) {
                            return;
                        }
                        isSongCaching = false;
                        currentCaching = null;
                        clearCachingProgress();
                        toast(message);
                    });
                }
            });
        });
    }

    private void cancelActiveCaching() {
        ++cacheRequestToken;
        apiClient.cancelSongCache();
        if (cacheWorkerHandler != null) {
            cacheWorkerHandler.removeCallbacksAndMessages(null);
        }
        currentCaching = null;
        isSongCaching = false;
        progressMini.setProgress(0);
    }

    private void ensureCacheWorker() {
        if (cacheWorkerThread != null) {
            return;
        }
        cacheWorkerThread = new HandlerThread("DSMusic-CacheWorker");
        cacheWorkerThread.start();
        cacheWorkerHandler = new Handler(cacheWorkerThread.getLooper());
    }

    private void stopAllPendingPlayback() {
        allowPendingCacheAutoPlay = false;
        cancelActiveCaching();
        if (player != null) {
            suppressNextOnPlayerStop = true;
            player.pause();
            player.stop();
            player.clearMediaItems();
        }
        currentPlaying = null;
        resetLyricsDisplay();
        updateNowPlayingUi();
        updatePlayButtons(false);
    }

    private void stopPlayerForManualTransition() {
        if (player == null) {
            return;
        }
        suppressNextOnPlayerStop = true;
        if (player.isPlaying()) {
            player.stop();
        } else {
            player.pause();
            player.stop();
        }
        player.clearMediaItems();
    }

    private boolean navigateUpPlaylist() {
        if (!"playlists".equals(currentMode) || !"playlist".equals(currentParentType)) {
            return false;
        }
        currentParentId = null;
        currentParentType = null;
        currentSortBy = defaultSortBy("playlists");
        currentSortDirection = "ASC";
        textHeader.setText("歌单");
        saveHomeState();
        loadFirstPage(false);
        return true;
    }

    private boolean navigateUpFolder() {
        if (!"search".equals(currentMode) || !"folder".equals(currentParentType)) {
            return false;
        }
        if (folderNavigationStack.isEmpty()) {
            currentParentId = null;
            currentParentType = null;
            textHeader.setText("搜索");
            loadFirstPage(false);
            return true;
        }
        FolderNavigationState previous = folderNavigationStack.remove(folderNavigationStack.size() - 1);
        currentMode = "search";
        currentParentId = previous.parentId;
        currentParentType = previous.parentType;
        textHeader.setText(previous.header);
        loadFirstPage(false);
        return true;
    }

    private boolean navigateUpBrowserStack() {
        if (browserNavigationStack.isEmpty()) {
            return false;
        }
        BrowserNavigationState prev = browserNavigationStack.remove(browserNavigationStack.size() - 1);
        internalModeSwitch = true;
        currentMode = prev.mode;
        currentParentId = prev.parentId;
        currentParentType = prev.parentType;
        currentKeyword = prev.keyword;
        currentSortBy = prev.sortBy;
        currentSortDirection = prev.sortDirection;
        pendingHeader = prev.header;
        if (!selectTabByMode(prev.mode)) {
            internalModeSwitch = false;
            textHeader.setText(prev.header);
            pendingHeader = null;
            loadFirstPage(false);
        }
        return true;
    }

    private void drillDown(ApiClient.MediaItemModel item) {
        if ("artist".equals(item.type)) {
            browserNavigationStack.add(new BrowserNavigationState(
                    currentMode,
                    currentParentId,
                    currentParentType,
                    currentKeyword,
                    currentSortBy,
                    currentSortDirection,
                    String.valueOf(textHeader.getText())
            ));
            currentMode = "songs";
            currentParentId = item.id;
            currentParentType = "artist";
            currentSortBy = "title";
            currentSortDirection = "ASC";
            pendingHeader = item.title;
            internalModeSwitch = true;
            if (!selectTabByMode("songs")) {
                internalModeSwitch = false;
                textHeader.setText(item.title);
                loadFirstPage(false);
            }
            return;
        }
        if ("album".equals(item.type)) {
            browserNavigationStack.add(new BrowserNavigationState(
                    currentMode,
                    currentParentId,
                    currentParentType,
                    currentKeyword,
                    currentSortBy,
                    currentSortDirection,
                    String.valueOf(textHeader.getText())
            ));
            currentMode = "songs";
            currentParentId = item.id;
            currentParentType = "album";
            currentSortBy = defaultSortBy(currentMode);
            currentSortDirection = "ASC";
            pendingHeader = item.title;
            internalModeSwitch = true;
            if (!selectTabByMode("songs")) {
                internalModeSwitch = false;
                textHeader.setText(item.title);
                loadFirstPage(false);
            }
            return;
        }
        if ("playlist".equals(item.type)) {
            currentMode = "playlists";
            currentParentId = item.id;
            currentParentType = "playlist";
            currentSortBy = "title";
            currentSortDirection = "ASC";
            pendingHeader = item.title;
            internalModeSwitch = true;
            saveHomeState();
            if (!selectTabByMode("playlists")) {
                internalModeSwitch = false;
                textHeader.setText(item.title);
                loadFirstPage(false);
            }
            return;
        }
        if ("folder".equals(item.type)) {
            folderNavigationStack.add(new FolderNavigationState(
                    currentParentId,
                    currentParentType,
                    String.valueOf(textHeader.getText())
            ));
            currentMode = "search";
            currentParentId = item.id;
            currentParentType = "folder";
            currentSortBy = "name";
            currentSortDirection = "ASC";
            pendingHeader = item.title;
            internalModeSwitch = true;
            if (!selectTabByMode("search")) {
                internalModeSwitch = false;
                textHeader.setText(item.title);
                loadFirstPage(false);
            }
        }
    }

    private void requestLyrics(String songId, int requestToken) {
        if (TextUtils.isEmpty(songId)) {
            return;
        }
        lyricSongId = songId;
        
        LyricsCacheManager.getInstance(this).fetchLyrics(songId, apiClient, new LyricsCacheManager.LyricsCallback() {
            @Override
            public void onSuccess(List<ApiClient.LyricLine> lyrics) {
                if (requestToken != playRequestToken) {
                    return;
                }
                lyricLines.clear();
                lyricLines.addAll(lyrics);
                lyricsAdapter.notifyDataSetChanged();
                currentLyricIndex = 0;
                centerLyricLine(currentLyricIndex, false);
                PlaybackService.updateLyricLine(MainActivity.this, currentNotificationLine());
            }

            @Override
            public void onError(String message) {
                if (requestToken != playRequestToken) {
                    return;
                }
                if (lyricLines.isEmpty()) {
                    lyricLines.clear();
                    ApiClient.LyricLine line = new ApiClient.LyricLine();
                    line.timeMs = 0L;
                    line.primary = message;
                    line.secondary = "";
                    lyricLines.add(line);
                    lyricsAdapter.notifyDataSetChanged();
                    currentLyricIndex = 0;
                    centerLyricLine(currentLyricIndex, false);
                    PlaybackService.updateLyricLine(MainActivity.this, currentNotificationLine());
                }
            }
        });
    }

    private void syncLyrics(long positionMs) {
        if (lyricLines.isEmpty()) {
            updatePlayerLyricLine("");
            return;
        }
        long effectivePositionMs = Math.max(0L, positionMs + apiClient.getLyricOffsetMs());
        int nextIndex = 0;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (effectivePositionMs >= lyricLines.get(i).timeMs) {
                nextIndex = i;
            } else {
                break;
            }
        }
        if (nextIndex != currentLyricIndex) {
            int previousIndex = currentLyricIndex;
            currentLyricIndex = nextIndex;
            if (previousIndex >= 0) {
                lyricsAdapter.notifyItemChanged(previousIndex);
            }
            lyricsAdapter.notifyItemChanged(nextIndex);
            centerLyricLine(nextIndex, true);
            PlaybackService.updateLyricLine(this, currentNotificationLine());
            updatePlayerLyricLine(currentNotificationLine());
            SysMediaPlayer.getInstance(this).updateLyricLine(currentNotificationLine());
        }
    }

    private void updatePlayerLyricLine(String line) {
        if (textPlayerLyricLine == null) {
            return;
        }
        if (TextUtils.isEmpty(line) || "暂无歌词".equals(line)) {
            textPlayerLyricLine.setVisibility(View.GONE);
            return;
        }
        if (lyricLines.size() <= 1 && !lyricLines.isEmpty() && lyricLines.get(0).timeMs == 0L) {
            textPlayerLyricLine.setVisibility(View.GONE);
            return;
        }
        textPlayerLyricLine.setText(line);
        textPlayerLyricLine.setVisibility(View.VISIBLE);
    }

    private void resetLyricsDisplay() {
        lyricSongId = null;
        lyricLines.clear();
        currentLyricIndex = -1;
        if (lyricsAdapter != null) {
            lyricsAdapter.notifyDataSetChanged();
        }
        updatePlayerLyricLine("");
        PlaybackService.updateLyricLine(this, "");
        SysMediaPlayer.getInstance(this).updateLyricLine("");
    }

    private void prepareLyricsForSong(@Nullable String nextSongId) {
        if (TextUtils.isEmpty(nextSongId)) {
            resetLyricsDisplay();
            return;
        }
        if (!TextUtils.equals(lyricSongId, nextSongId)) {
            resetLyricsDisplay();
        }
    }

    private void updateLyricsViewportPadding() {
        if (lyricsRecyclerView == null) {
            return;
        }
        int height = lyricsRecyclerView.getHeight();
        if (height <= 0) {
            return;
        }
        int centerPadding = Math.max(dp(24), height / 2 - dp(44));
        if (lyricsRecyclerView.getPaddingTop() == centerPadding && lyricsRecyclerView.getPaddingBottom() == centerPadding) {
            return;
        }
        lyricsRecyclerView.setPadding(
                lyricsRecyclerView.getPaddingLeft(),
                centerPadding,
                lyricsRecyclerView.getPaddingRight(),
                centerPadding
        );
        lyricsRecyclerView.setClipToPadding(false);
    }

    private void centerLyricLine(int index, boolean animate) {
        if (lyricsRecyclerView == null || index < 0 || index >= lyricLines.size()) {
            return;
        }
        RecyclerView.LayoutManager layoutManager = lyricsRecyclerView.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) {
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) layoutManager;
        View target = manager.findViewByPosition(index);
        if (target == null) {
            int fallbackOffset = Math.max(dp(24), lyricsRecyclerView.getHeight() / 2 - dp(42));
            manager.scrollToPositionWithOffset(index, fallbackOffset);
            if (animate) {
                lyricsRecyclerView.post(() -> centerLyricLine(index, false));
            }
            return;
        }
        int desiredTop = lyricsRecyclerView.getHeight() / 2 - target.getHeight() / 2;
        int dy = target.getTop() - desiredTop;
        if (dy == 0) {
            return;
        }
        if (animate) {
            lyricsRecyclerView.smoothScrollBy(0, dy);
        } else {
            lyricsRecyclerView.scrollBy(0, dy);
        }
    }

    private String currentNotificationLine() {
        if (currentLyricIndex >= 0 && currentLyricIndex < lyricLines.size()) {
            String primary = lyricLines.get(currentLyricIndex).primary;
            if (!TextUtils.isEmpty(primary) && !"暂无歌词".equals(primary)) {
                return primary;
            }
        }
        return currentPlaying == null ? "" : currentPlaying.subtitle;
    }

    private void openFullPlayer() {
        refreshNowPlayingCovers();
        showLyricsPage(false);
        setPlayerOverlayLocked(true);
        fullPlayer.animate().cancel();
        fullPlayer.setVisibility(View.VISIBLE);
        fullPlayer.setAlpha(0f);
        fullPlayer.setTranslationY(dp(28));
        fullPlayer.setScaleX(0.985f);
        fullPlayer.setScaleY(0.985f);
        fullPlayer.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(360L)
                .setInterpolator(panelInterpolator)
                .start();
        animatePlayerSurfaceEntrance();
    }

    private void hideFullPlayer() {
        if (fullPlayer.getVisibility() != View.VISIBLE) {
            fullPlayer.setVisibility(View.GONE);
            setPlayerOverlayLocked(false);
            return;
        }
        fullPlayer.animate().cancel();
        fullPlayer.animate()
                .alpha(0f)
                .translationY(dp(28))
                .scaleX(0.985f)
                .scaleY(0.985f)
                .setDuration(240L)
                .setInterpolator(panelInterpolator)
                .withEndAction(() -> {
                    fullPlayer.setVisibility(View.GONE);
                    fullPlayer.setAlpha(1f);
                    fullPlayer.setTranslationY(0f);
                    fullPlayer.setScaleX(1f);
                    fullPlayer.setScaleY(1f);
                    setPlayerOverlayLocked(false);
                })
                .start();
    }

    private void setPlayerOverlayLocked(boolean locked) {
        if (drawerLayout == null) {
            return;
        }
        drawerLayout.setDrawerLockMode(locked ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    private void animatePlayerSurfaceEntrance() {
        animateSurfacePiece(playerPageControls, 0f, dp(10), 0.985f, 0L, 300L, 80L);
        animateSurfacePiece(playerInfoRow, 0f, dp(8), 0.99f, 0L, 260L, 110L);
        animateSurfacePiece(imagePlayerCover, 0f, dp(14), 0.975f, 0L, 340L, 90L);
        if (textPlayerLyricLine != null && textPlayerLyricLine.getVisibility() == View.VISIBLE) {
            animateSurfacePiece(textPlayerLyricLine, 0f, dp(8), 0.99f, 0L, 240L, 150L);
        }
    }

    private void animateSurfacePiece(@Nullable View view, float startAlpha, float startTranslationY, float startScale, long startDelay, long duration, long delay) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setAlpha(startAlpha);
        view.setTranslationY(startTranslationY);
        view.setScaleX(startScale);
        view.setScaleY(startScale);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(delay)
                .setDuration(duration)
                .setInterpolator(panelInterpolator)
                .start();
    }

    private void showLyricsPage(boolean showLyrics) {
        if (lyricsPageVisible == showLyrics) {
            return;
        }
        lyricsPageVisible = showLyrics;
        View hideView = showLyrics ? playerPageControls : playerPageLyrics;
        View showView = showLyrics ? playerPageLyrics : playerPageControls;
        hideView.animate().cancel();
        showView.animate().cancel();
        if (showLyrics) {
            applyLyricsBackdropEffect(true);
            if (imageLyricsBackdrop != null) {
                imageLyricsBackdrop.animate().cancel();
                imageLyricsBackdrop.setVisibility(View.VISIBLE);
                imageLyricsBackdrop.setAlpha(0f);
                imageLyricsBackdrop.setScaleX(1.08f);
                imageLyricsBackdrop.setScaleY(1.08f);
                imageLyricsBackdrop.animate()
                        .alpha(0.24f)
                        .scaleX(1.02f)
                        .scaleY(1.02f)
                        .setDuration(320L)
                        .setInterpolator(panelInterpolator)
                        .start();
            }
        } else {
            applyLyricsBackdropEffect(false);
            if (imageLyricsBackdrop != null) {
                imageLyricsBackdrop.animate().cancel();
                imageLyricsBackdrop.animate()
                        .alpha(0f)
                        .scaleX(1.06f)
                        .scaleY(1.06f)
                        .setDuration(200L)
                        .setInterpolator(panelInterpolator)
                        .start();
            }
        }
        showView.setVisibility(View.VISIBLE);
        showView.setAlpha(0f);
        showView.setTranslationX(showLyrics ? 26f : -26f);
        showView.setScaleX(showLyrics ? 0.98f : 0.96f);
        showView.setScaleY(showLyrics ? 0.98f : 0.96f);
        hideView.animate()
                .alpha(0f)
                .translationX(showLyrics ? -18f : 18f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(200L)
                .setInterpolator(panelInterpolator)
                .withEndAction(() -> {
                    hideView.setVisibility(View.GONE);
                    hideView.setAlpha(1f);
                    hideView.setTranslationX(0f);
                    hideView.setScaleX(1f);
                    hideView.setScaleY(1f);
                })
                .start();
        showView.animate()
                .alpha(1f)
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280L)
                .setInterpolator(panelInterpolator)
                .withEndAction(() -> {
                    if (showLyrics && currentLyricIndex >= 0) {
                        centerLyricLine(currentLyricIndex, false);
                    }
                })
                .start();
    }

    private void updateNowPlayingUi() {
        if (currentPlaying == null) {
            miniPlayer.setVisibility(View.GONE);
            if (textPlayerQuality != null) {
                textPlayerQuality.setVisibility(View.GONE);
            }
            lastNowPlayingUiSongId = null;
            PlaybackService.updatePlaybackProgress(this, 0L, 0L);
            SysMediaPlayer.getInstance(this).updatePlaybackProgress(0L, 0L);
            return;
        }
        boolean songChanged = !TextUtils.equals(lastNowPlayingUiSongId, currentPlaying.id);
        lastNowPlayingUiSongId = currentPlaying.id;
        miniPlayer.setVisibility(View.VISIBLE);
        textMiniTitle.setText(currentPlaying.title);
        textMiniSubtitle.setText(currentPlaying.subtitle);
        textPlayerAlbum.setText(firstNonEmpty(currentPlaying.title, currentPlaying.album, "单曲"));
        textPlayerTitle.setText(currentPlaying.title);
        textPlayerArtist.setText(currentPlaying.subtitle);
        updatePlayerQualityLabel();
        updatePlayerLyricLine(currentNotificationLine());
        progressMini.setProgress(0);
        refreshNowPlayingCoversDeferred(songChanged);
        
        SysMediaPlayer.getInstance(this).updateMediaInfo(
                currentPlaying.title,
                currentPlaying.artist,
                currentNotificationLine(),
                currentPlaying.localCoverPath,
                player != null && player.isPlaying()
        );

        if (!isSongCaching) {
            clearCachingProgress();
        }
        if (songChanged) {
            animateNowPlayingTransition();
        }
    }

    private void refreshNowPlayingCoversDeferred(boolean defer) {
        if (!defer) {
            refreshNowPlayingCovers();
            return;
        }
        final String songId = currentPlaying == null ? null : currentPlaying.id;
        progressHandler.post(() -> {
            if (currentPlaying == null || !TextUtils.equals(currentPlaying.id, songId) || lastNowPlayingUiSongId == null) {
                return;
            }
            refreshNowPlayingCovers();
        });
    }

    private void animateNowPlayingTransition() {
        animateTransitionPiece(imageMiniCover, 0.96f, dp(4), 0L, 140L);
        animateTransitionPiece(imagePlayerCover, 0.96f, dp(4), 0L, 140L);
        animateTransitionPiece(textMiniTitle, 0f, dp(4), 0L, 120L);
        animateTransitionPiece(textMiniSubtitle, 0f, dp(2), 20L, 120L);
        animateTransitionPiece(textPlayerTitle, 0f, dp(4), 0L, 120L);
        animateTransitionPiece(textPlayerArtist, 0f, dp(2), 20L, 120L);
        if (textPlayerAlbum != null) {
            animateTransitionPiece(textPlayerAlbum, 0f, dp(2), 20L, 120L);
        }
    }

    private void animateTransitionPiece(@Nullable View view, float startAlpha, float startTranslationY, long startDelay, long duration) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setAlpha(startAlpha);
        view.setTranslationY(startTranslationY);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(startDelay)
                .setDuration(duration)
                .setInterpolator(panelInterpolator)
                .start();
    }

    private void scheduleNowPlayingUiRefresh(@Nullable String songId, int requestToken) {
        progressHandler.postDelayed(() -> {
            if (requestToken != playRequestToken || currentPlaying == null || !TextUtils.equals(currentPlaying.id, songId)) {
                return;
            }
            updateNowPlayingUi();
            requestLyrics(songId, requestToken);
        }, 32L);
    }

    private void applyLyricsBackdropEffect(boolean enabled) {
        if (imageLyricsBackdrop == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            imageLyricsBackdrop.setRenderEffect(enabled
                    ? RenderEffect.createBlurEffect(22f, 22f, Shader.TileMode.CLAMP)
                    : null);
        }
    }

    private void refreshNowPlayingCovers() {
        if (currentPlaying == null) {
            return;
        }
        int token = ++nowPlayingCoverRefreshToken;
        imageMiniCover.setImageResource(R.drawable.ic_music_note);
        imagePlayerCover.setImageResource(R.drawable.ic_music_note);
        imageLyricsBackdrop.setImageResource(R.drawable.ic_music_note);
        if (!TextUtils.isEmpty(currentPlaying.localCoverPath) && new File(currentPlaying.localCoverPath).exists()) {
            applyNowPlayingCover(new File(currentPlaying.localCoverPath), token, currentPlaying.localCoverPath);
        } else if (!TextUtils.isEmpty(currentPlaying.id)) {
            loadCurrentSongCover(currentPlaying.id, token);
        }
    }

    private void showCachingProgress(int percent) {
        isSongCaching = true;
        String progressText = percent >= 100 ? "缓存完成，准备播放" : "缓存中 " + percent + "%";
        if (currentPlaying != null) {
            textMiniSubtitle.setText(progressText);
            textPlayerArtist.setText(progressText);
            if (textPlayerQuality != null) {
                textPlayerQuality.setVisibility(View.GONE);
            }
        }
        progressMini.setProgress(percent * 10);
    }

    private void clearCachingProgress() {
        if (currentPlaying == null) {
            if (currentCaching == null) {
                progressMini.setProgress(0);
            }
            if (textPlayerQuality != null) {
                textPlayerQuality.setVisibility(View.GONE);
            }
            return;
        }
        isSongCaching = false;
        textMiniSubtitle.setText(currentPlaying.subtitle);
        textPlayerArtist.setText(currentPlaying.subtitle);
        updatePlayerQualityLabel();
        long duration = Math.max(player.getDuration(), currentPlaying.durationMs);
        int progress = duration > 0L ? (int) ((player.getCurrentPosition() * 1000L) / duration) : 0;
        progressMini.setProgress(progress);
        updateSummary(browserItems.size());
        PlaybackService.updatePlaybackState(this, currentPlaying.title, currentPlaying.artist, currentNotificationLine(), currentPlaying.localCoverPath);
    }

    private void updatePlayerQualityLabel() {
        if (textPlayerQuality == null) {
            return;
        }
        String quality = resolveQualityLabel(currentPlaying);
        if (currentPlaying == null || isSongCaching || TextUtils.isEmpty(quality)) {
            textPlayerQuality.setVisibility(View.GONE);
            return;
        }
        textPlayerQuality.setText(quality);
        textPlayerQuality.setVisibility(View.VISIBLE);
    }

    private String resolveQualityLabel(@Nullable ApiClient.MediaItemModel item) {
        if (item == null) {
            return "";
        }
        String format = "";
        String bitrate = "";
        if (item.raw != null) {
            JSONObject additional = item.raw.optJSONObject("additional");
            JSONObject audio = additional == null ? null : additional.optJSONObject("song_audio");
            if (audio != null) {
                format = firstNonEmpty(
                        audio.optString("format", ""),
                        audio.optString("codec", ""),
                        audio.optString("audio_codec", "")
                );
                bitrate = resolveBitrateLabel(audio);
            }
        }
        if (TextUtils.isEmpty(format)) {
            format = resolveFormatLabel(item.path);
        }
        if (TextUtils.isEmpty(format)) {
            return "";
        }
        return TextUtils.isEmpty(bitrate) ? format : format + " " + bitrate;
    }

    private String resolveBitrateLabel(JSONObject audio) {
        if (audio == null) {
            return "";
        }
        String[] keys = {"bitrate", "bit_rate", "audio_bitrate", "bitrate_kbps", "audio_bitrate_kbps"};
        for (String key : keys) {
            if (!audio.has(key)) {
                continue;
            }
            Long bitrate = parseBitrateKbps(audio.opt(key));
            if (bitrate != null && bitrate > 0L) {
                return bitrate + "K";
            }
        }
        return "";
    }

    private Long parseBitrateKbps(Object value) {
        if (value == null) {
            return null;
        }
        long raw;
        if (value instanceof Number) {
            raw = ((Number) value).longValue();
        } else {
            String text = String.valueOf(value).trim();
            if (TextUtils.isEmpty(text)) {
                return null;
            }
            try {
                raw = Long.parseLong(text.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (raw <= 0L) {
            return null;
        }
        if (raw >= 1000L) {
            return Math.max(1L, Math.round(raw / 1000.0));
        }
        return raw;
    }

    private String resolveFormatLabel(@Nullable String path) {
        if (TextUtils.isEmpty(path)) {
            return "";
        }
        String cleanPath = path.trim();
        int queryIndex = cleanPath.indexOf('?');
        if (queryIndex >= 0) {
            cleanPath = cleanPath.substring(0, queryIndex);
        }
        int dotIndex = cleanPath.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == cleanPath.length() - 1) {
            return "";
        }
        String extension = cleanPath.substring(dotIndex + 1).trim().toUpperCase(Locale.getDefault());
        if (TextUtils.isEmpty(extension) || extension.length() > 8) {
            return "";
        }
        return extension;
    }

    private void showCachingCompleted(ApiClient.MediaItemModel item) {
        currentCaching = null;
        if (currentPlaying != null) {
            clearCachingProgress();
        } else {
            progressMini.setProgress(1000);
        }
        PlaybackService.clearCachingState(this);
        toast("闁诲氦顫夐悺鏇犱焊濞嗘垶顫曢柨鏃堟暜閸?" + item.title);
    }

    private void loadCoverInto(String type, String id, ImageView imageView) {
        imageView.setTag(id);
        String cachedPath = apiClient.resolveLocalCoverPath(type, id);
        if (!TextUtils.isEmpty(cachedPath)) {
            if (currentPlaying != null && TextUtils.equals(currentPlaying.id, id)) {
                    currentPlaying.localCoverPath = cachedPath;
                    PlaybackService.updateCoverPath(MainActivity.this, cachedPath);
                    SysMediaPlayer.getInstance(MainActivity.this).updateMediaInfo(
                            currentPlaying.title,
                            currentPlaying.artist,
                            currentNotificationLine(),
                            cachedPath,
                            player != null && player.isPlaying()
                    );
                }
                loadLocalCoverInto(new File(cachedPath), imageView, imageView == imagePlayerCover || imageView == imageLyricsBackdrop, typeIcon(type));
                return;
        }

        apiClient.cacheCover(type, id, new ApiClient.StringCallback() {
            @Override
            public void onSuccess(String value) {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    return;
                }
                if (!TextUtils.equals(String.valueOf(imageView.getTag()), id)) {
                    return;
                }
                if (currentPlaying != null && TextUtils.equals(currentPlaying.id, id)) {
                    currentPlaying.localCoverPath = value;
                    PlaybackService.updateCoverPath(MainActivity.this, value);
                    SysMediaPlayer.getInstance(MainActivity.this).updateMediaInfo(
                            currentPlaying.title,
                            currentPlaying.artist,
                            currentNotificationLine(),
                            value,
                            player != null && player.isPlaying()
                    );
                }
                loadLocalCoverInto(new File(value), imageView, imageView == imagePlayerCover || imageView == imageLyricsBackdrop, typeIcon(type));
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void loadCurrentSongCover(String songId, int token) {
        apiClient.cacheCover("song", songId, new ApiClient.StringCallback() {
            @Override
            public void onSuccess(String value) {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    return;
                }
                if (token != nowPlayingCoverRefreshToken) {
                    return;
                }
                if (currentPlaying == null || !TextUtils.equals(currentPlaying.id, songId)) {
                    return;
                }
                applyNowPlayingCover(new File(value), token, value);
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    private void applyNowPlayingCover(File file, int token, String coverPath) {
        if (currentPlaying == null || token != nowPlayingCoverRefreshToken) {
            return;
        }
        if (file == null || !file.exists()) {
            return;
        }
        currentPlaying.localCoverPath = coverPath;
        PlaybackService.updateCoverPath(MainActivity.this, coverPath);
        SysMediaPlayer.getInstance(MainActivity.this).updateMediaInfo(
                currentPlaying.title,
                currentPlaying.artist,
                currentNotificationLine(),
                coverPath,
                player != null && player.isPlaying()
        );
        loadLocalCoverInto(file, imageMiniCover, false, R.drawable.ic_music_note);
        loadLocalCoverInto(file, imagePlayerCover, true, R.drawable.ic_music_note);
        loadLocalCoverInto(file, imageLyricsBackdrop, true, R.drawable.ic_music_note);
    }

    private void loadLocalCoverInto(File file, ImageView imageView, boolean fitCenter, int placeholderRes) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (file == null || !file.exists()) {
            imageView.setImageResource(placeholderRes);
            return;
        }
        Glide.with(this).clear(imageView);
        RequestOptions options = (fitCenter ? PLAYER_COVER_OPTIONS : MINI_COVER_OPTIONS).clone()
                .placeholder(placeholderRes)
                .error(placeholderRes);
        if (fitCenter) {
            Glide.with(this).load(file).apply(options).fitCenter().into(imageView);
        } else {
            Glide.with(this).load(file).apply(options).into(imageView);
        }
    }

    private void refreshBrowserSongState(@Nullable String songId) {
        postToMain(() -> {
            if (browserAdapter == null || TextUtils.isEmpty(songId)) {
                return;
            }
            for (int i = 0; i < browserItems.size(); i++) {
                ApiClient.MediaItemModel item = browserItems.get(i);
                if (item != null && TextUtils.equals(item.id, songId)) {
                    browserAdapter.notifyItemChanged(i);
                    break;
                }
            }
        });
    }

    private void updatePlayButtons(boolean isPlaying) {
        int icon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
        buttonMiniPlay.setImageResource(icon);
        buttonPlayPause.setImageResource(icon);
    }

    private void toggleShuffle() {
        shuffleEnabled = !shuffleEnabled;
        player.setShuffleModeEnabled(shuffleEnabled);
        updatePlaybackModeUi();
        syncQueueToService(currentPlaying == null ? null : currentPlaying.id);
        toast(shuffleEnabled ? "已开启随机播放" : "已关闭随机播放");
    }

    private void toggleRepeatMode() {
        repeatAllEnabled = !repeatAllEnabled;
        if (repeatAllEnabled) {
            repeatOneEnabled = false;
        }
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        updatePlaybackModeUi();
        syncQueueToService(currentPlaying == null ? null : currentPlaying.id);
        toast(repeatAllEnabled ? "已开启列表循环" : "已关闭列表循环");
    }

    private void toggleRepeatOneMode() {
        repeatOneEnabled = !repeatOneEnabled;
        if (repeatOneEnabled) {
            repeatAllEnabled = false;
        }
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        updatePlaybackModeUi();
        syncQueueToService(currentPlaying == null ? null : currentPlaying.id);
        toast(repeatOneEnabled ? "已开启单曲循环" : "已关闭单曲循环");
    }

    private void updatePlaybackModeUi() {
        int activeMini = ContextCompat.getColor(this, R.color.seed);
        int inactiveMini = ContextCompat.getColor(this, R.color.subtle);
        buttonMiniShuffle.setColorFilter(shuffleEnabled ? activeMini : inactiveMini);
        buttonMiniRepeat.setColorFilter(repeatAllEnabled ? activeMini : inactiveMini);
        applyModeButtonState(buttonShuffle, shuffleEnabled);
        applyModeButtonState(buttonRepeat, repeatAllEnabled);
        applyModeButtonState(buttonRepeatOne, repeatOneEnabled);
    }

    private void applyModeButtonState(ImageButton button, boolean active) {
        button.setColorFilter(active ? 0xFFFFFFFF : 0x99FFFFFF);
        button.setBackgroundResource(active ? R.drawable.bg_player_mode_button_active : R.drawable.bg_player_mode_button);
        Object last = button.getTag();
        boolean changed = !(last instanceof Boolean) || ((Boolean) last) != active;
        button.setTag(Boolean.valueOf(active));
        if (changed) {
            button.setScaleX(0.9f);
            button.setScaleY(0.9f);
            button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180L)
                    .setInterpolator(panelInterpolator)
                    .start();
        }
    }

    private void replayCurrentSong() {
        if (player == null || currentPlaying == null) {
            return;
        }
        player.seekTo(0L);
        player.play();
        updateNowPlayingUi();
        requestLyrics(currentPlaying.id, ++playRequestToken);
    }

    private void togglePlayPause() {
        if (isSongCaching) {
            stopAllPendingPlayback();
            toast("已停止缓存和待播");
            return;
        }
        if (currentPlaying == null) {
            return;
        }
        // ExoPlayer won't restart from STATE_ENDED on play(); we need to explicitly advance/replay.
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            if (repeatOneEnabled) {
                replayCurrentSong();
            } else {
                playNext();
            }
            return;
        }
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    private void resumeIfEndedStuck() {
        if (player == null) {
            return;
        }
        if (isSongCaching || PlaybackService.isCachingActive()) {
            return;
        }
        if (player.getPlaybackState() == Player.STATE_ENDED) {
            if (repeatOneEnabled && currentPlaying != null) {
                replayCurrentSong();
            } else {
                playNext();
            }
        }
    }

    private void playNext() {
        ensurePlayableQueue();
        if (playQueue.isEmpty()) {
            return;
        }
        if (shuffleEnabled && canExpandPlayQueue()) {
            expandPlayQueueAndThen(() -> continueQueuePlayback(true));
            return;
        }
        continueQueuePlayback(true);
    }

    private void continueQueuePlayback(boolean forward) {
        int nextIndex = findNextPlayableIndex(forward);
        if (nextIndex < 0) {
            if (forward && canExpandPlayQueue()) {
                expandPlayQueueAndThen(() -> continueQueuePlayback(true));
                return;
            }
            toast(forward ? "已经是最后一首" : "已经是第一首");
            return;
        }
        playQueueIndex = nextIndex;
        playItem(playQueue.get(playQueueIndex), playQueue);
    }

    private void playPrevious() {
        ensurePlayableQueue();
        if (playQueue.isEmpty()) {
            return;
        }
        continueQueuePlayback(false);
    }

    private int findNextPlayableIndex(boolean forward) {
        if (playQueue.isEmpty()) {
            return -1;
        }
        if (forward && !TextUtils.isEmpty(pendingForceNextSongId)) {
            int forcedIndex = findQueueIndexById(pendingForceNextSongId);
            pendingForceNextSongId = null;
            if (forcedIndex >= 0 && forcedIndex < playQueue.size() && forcedIndex != playQueueIndex) {
                return forcedIndex;
            }
        }
        if (shuffleEnabled && playQueue.size() > 1) {
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < playQueue.size(); i++) {
                if (i != playQueueIndex) {
                    candidates.add(i);
                }
            }
            if (candidates.isEmpty()) {
                return -1;
            }
            return candidates.get((int) (Math.random() * candidates.size()));
        }
        int index = playQueueIndex;
        for (int i = 0; i < playQueue.size(); i++) {
            index = forward ? index + 1 : index - 1;
            if (index >= playQueue.size()) {
                if (!repeatAllEnabled) {
                    return -1;
                }
                index = 0;
            }
            if (index < 0) {
                if (!repeatAllEnabled) {
                    return -1;
                }
                index = playQueue.size() - 1;
            }
            return index;
        }
        return -1;
    }

    private void ensurePlayableQueue() {
        if (playQueue.isEmpty()) {
            for (ApiClient.MediaItemModel entry : browserItems) {
                if ("song".equals(entry.type)) {
                    playQueue.add(entry);
                }
            }
        }
        if (playQueue.isEmpty()) {
            for (ApiClient.MediaItemModel entry : apiClient.readRootSongCache()) {
                if ("song".equals(entry.type)) {
                    playQueue.add(entry);
                }
            }
        }
        if (currentPlaying != null) {
            int currentIndex = findQueueIndexById(currentPlaying.id);
            if (currentIndex >= 0) {
                playQueueIndex = currentIndex;
            }
        }
        if (playQueueIndex < 0 && !playQueue.isEmpty()) {
            playQueueIndex = 0;
        }
    }

    private void preparePlayQueue(ApiClient.MediaItemModel item, List<ApiClient.MediaItemModel> source) {
        pendingForceNextSongId = null;
        if (source == playQueue) {
            int currentIndex = findQueueIndexById(item.id);
            playQueueIndex = currentIndex >= 0 ? currentIndex : 0;
            syncQueueToService(item == null ? null : item.id);
            return;
        }

        if (source == browserItems && isSameBrowserQueueContext()) {
            appendSongsToPlayQueue(source);
            int existingIndex = findQueueIndexById(item.id);
            if (existingIndex < 0) {
                playQueue.add(item);
                existingIndex = playQueue.size() - 1;
            }
            playQueueIndex = existingIndex;
            playQueueComplete = !hasMore;
            syncQueueToService(item == null ? null : item.id);
            return;
        }

        playQueueVersion++;
        playQueueLoading = false;
        playQueue.clear();
        for (ApiClient.MediaItemModel entry : source) {
            if ("song".equals(entry.type)) {
                playQueue.add(entry);
            }
        }
        int currentIndex = findQueueIndexById(item.id);
        playQueueIndex = currentIndex >= 0 ? currentIndex : 0;
        if (source == browserItems) {
            playQueueContextKey = currentBrowserQueueContextKey();
            playQueueMode = effectiveBrowserMode();
            playQueueParentId = currentParentId;
            playQueueParentType = currentParentType;
            playQueueKeyword = currentKeyword;
            playQueueSortBy = currentSortBy;
            playQueueSortDirection = currentSortDirection;
            playQueueComplete = !hasMore;
            Runnable notifyReady = () -> {
                if (!playQueue.isEmpty()) {
                    toast("已将" + currentQueueSourceLabel() + "中的" + playQueue.size() + "首歌添加到正在播放列表");
                }
            };
            if (canExpandPlayQueue()) {
                expandPlayQueueAndThen(notifyReady);
            } else {
                notifyReady.run();
            }
            syncQueueToService(item == null ? null : item.id);
            return;
        }
        playQueueContextKey = "";
        playQueueMode = null;
        playQueueParentId = null;
        playQueueParentType = null;
        playQueueKeyword = "";
        playQueueSortBy = "title";
        playQueueSortDirection = "ASC";
        playQueueContextKey = "";
        playQueueComplete = true;
        playQueueLoading = false;
        syncQueueToService(item == null ? null : item.id);
    }

    private String currentQueueSourceLabel() {
        String header = textHeader == null ? "" : String.valueOf(textHeader.getText()).trim();
        if (!TextUtils.isEmpty(header)) {
            return "《" + header + "》" ;
        }
        if ("playlists".equals(currentMode)) {
            return "歌单";
        }
        if ("artists".equals(currentMode)) {
            return "歌手";
        }
        if ("songs".equals(currentMode)) {
            return "所有音乐";
        }
        if ("search".equals(currentMode) && !TextUtils.isEmpty(currentKeyword)) {
            return "搜索结果";
        }
        if ("search".equals(currentMode)) {
            return "当前文件夹";
        }
        return "当前列表";
    }

    private int findQueueIndexById(String songId) {
        if (TextUtils.isEmpty(songId)) {
            return -1;
        }
        for (int i = 0; i < playQueue.size(); i++) {
            if (TextUtils.equals(playQueue.get(i).id, songId)) {
                return i;
            }
        }
        return -1;
    }

    private boolean canExpandPlayQueue() {
        return !playQueueComplete
                && !TextUtils.isEmpty(playQueueMode);
    }

    private void maybePrefetchPlayQueue() {
        if (!canExpandPlayQueue() || playQueueLoading) {
            return;
        }
        expandPlayQueueAndThen(null);
    }

    private void expandPlayQueueAndThen(@Nullable Runnable onComplete) {
        if (!canExpandPlayQueue()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        if (playQueueLoading) {
            return;
        }
        playQueueLoading = true;
        fetchNextPlayQueuePage(playQueueVersion, onComplete);
    }

    private void fetchNextPlayQueuePage(int queueVersion, @Nullable Runnable onComplete) {
        apiClient.fetchBrowserItems(
                playQueueMode,
                playQueueParentId,
                playQueueParentType,
                playQueueKeyword,
                playQueue.size(),
                PAGE_SIZE,
                playQueueSortBy,
                playQueueSortDirection,
                new ApiClient.ArrayCallback() {
                    @Override
                    public void onSuccess(List<ApiClient.MediaItemModel> items, boolean more, int total) {
                        if (queueVersion != playQueueVersion) {
                            return;
                        }
                        appendSongsToPlayQueue(items);
                        playQueueComplete = !more;
                        if (more) {
                            fetchNextPlayQueuePage(queueVersion, onComplete);
                            return;
                        }
                        playQueueLoading = false;
                        if (currentPlaying != null) {
                            int currentIndex = findQueueIndexById(currentPlaying.id);
                            if (currentIndex >= 0) {
                                playQueueIndex = currentIndex;
                            }
                        }
                        if (isRootSongsRequest()) {
                            apiClient.saveRootSongCache(playQueue);
                        }
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        syncQueueToService(currentPlaying == null ? null : currentPlaying.id);
                    }

                    @Override
                    public void onError(String message) {
                        if (queueVersion != playQueueVersion) {
                            return;
                        }
                        playQueueLoading = false;
                        toast("扩展播放队列失败：" + message);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }
                }
        );
    }

    private void appendSongsToPlayQueue(List<ApiClient.MediaItemModel> items) {
        for (ApiClient.MediaItemModel entry : items) {
            if (!"song".equals(entry.type)) {
                continue;
            }
            if (findQueueIndexById(entry.id) >= 0) {
                continue;
            }
            playQueue.add(entry);
        }
    }

    private void showNowPlayingQueueDialog() {
        ensurePlayableQueue();
        if (playQueue.isEmpty()) {
            toast("暂无正在播放的队列");
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_now_playing_queue, null, false);
        TextView textSubtitle = view.findViewById(R.id.textQueueSubtitle);
        RecyclerView recyclerQueue = view.findViewById(R.id.recyclerQueue);
        TextView buttonAddToPlaylist = view.findViewById(R.id.buttonQueueAddToPlaylist);
        TextView buttonSaveAsPlaylist = view.findViewById(R.id.buttonQueueSaveAsPlaylist);
        TextView buttonClose = view.findViewById(R.id.buttonQueueClose);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        NowPlayingQueueAdapter adapter = new NowPlayingQueueAdapter();
        recyclerQueue.setLayoutManager(new LinearLayoutManager(this));
        recyclerQueue.setAdapter(adapter);
        recyclerQueue.setVerticalScrollBarEnabled(true);
        enableDialogEdgeFastScroll(recyclerQueue);
        recyclerQueue.post(() -> centerQueueCurrentItem(recyclerQueue));

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                0
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from < 0 || to < 0 || from == to) {
                    return false;
                }
                moveQueueItem(from, to);
                adapter.notifyItemMoved(from, to);
                updateQueueSubtitle(textSubtitle);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }
        });
        touchHelper.attachToRecyclerView(recyclerQueue);

        adapter.setActionListener(new NowPlayingQueueAdapter.ActionListener() {
            @Override
            public void onPlayAt(int position) {
                if (position < 0 || position >= playQueue.size()) {
                    return;
                }
                playQueueIndex = position;
                playItem(playQueue.get(position), playQueue);
                dialog.dismiss();
            }

            @Override
            public void onShowActions(View anchor, int position) {
                showQueueItemActions(anchor, position, adapter, textSubtitle, dialog);
            }

            @Override
            public void onStartDrag(RecyclerView.ViewHolder holder) {
                touchHelper.startDrag(holder);
            }
        });

        updateQueueSubtitle(textSubtitle);
        buttonAddToPlaylist.setOnClickListener(v -> {
            if (playQueue.isEmpty()) {
                toast("队列为空");
                return;
            }
            dialog.dismiss();
            playlistDialogs.showSongsPlaylistChooser(new ArrayList<>(playQueue));
        });
        buttonSaveAsPlaylist.setOnClickListener(v -> saveQueueAsNewPlaylist());
        buttonClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void centerQueueCurrentItem(RecyclerView recyclerView) {
        if (recyclerView == null || playQueue.isEmpty()) {
            return;
        }
        int currentIndex = playQueueIndex;
        if (currentPlaying != null) {
            int found = findQueueIndexById(currentPlaying.id);
            if (found >= 0) {
                currentIndex = found;
            }
        }
        if (currentIndex < 0 || currentIndex >= playQueue.size()) {
            return;
        }
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) {
            recyclerView.scrollToPosition(currentIndex);
            return;
        }
        LinearLayoutManager manager = (LinearLayoutManager) layoutManager;
        int offset = Math.max(dp(20), recyclerView.getHeight() / 2 - dp(44));
        manager.scrollToPositionWithOffset(currentIndex, offset);
    }

    private void enableDialogEdgeFastScroll(RecyclerView targetView) {
        final float edgeWidthPx = getResources().getDisplayMetrics().density * 56f;
        final boolean[] dragging = {false};
        targetView.setOnTouchListener((v, event) -> {
            RecyclerView.Adapter<?> adapter = targetView.getAdapter();
            if (adapter == null || adapter.getItemCount() < 2) {
                dragging[0] = false;
                return false;
            }
            int action = event.getActionMasked();
            float x = event.getX();
            boolean inEdge = x >= (targetView.getWidth() - edgeWidthPx);

            if (action == MotionEvent.ACTION_DOWN && inEdge) {
                dragging[0] = true;
                targetView.getParent().requestDisallowInterceptTouchEvent(true);
                float clamped = Math.max(0f, Math.min(event.getY(), targetView.getHeight()));
                float ratio = targetView.getHeight() <= 0 ? 0f : clamped / (float) targetView.getHeight();
                int position = Math.max(0, Math.min(adapter.getItemCount() - 1, Math.round((adapter.getItemCount() - 1) * ratio)));
                targetView.scrollToPosition(position);
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE && dragging[0]) {
                float clamped = Math.max(0f, Math.min(event.getY(), targetView.getHeight()));
                float ratio = targetView.getHeight() <= 0 ? 0f : clamped / (float) targetView.getHeight();
                int position = Math.max(0, Math.min(adapter.getItemCount() - 1, Math.round((adapter.getItemCount() - 1) * ratio)));
                targetView.scrollToPosition(position);
                return true;
            }
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && dragging[0]) {
                dragging[0] = false;
                return true;
            }
            return false;
        });
    }

    private void showQueueItemActions(View anchor, int position, NowPlayingQueueAdapter adapter, TextView textSubtitle, AlertDialog dialog) {
        if (position < 0 || position >= playQueue.size()) {
            return;
        }
        ApiClient.MediaItemModel song = playQueue.get(position);
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "插放下一首");
        menu.getMenu().add(0, 2, 1, "添加到歌单");
        menu.getMenu().add(0, 3, 2, "从队列移除");
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                queueInsertNext(position);
                adapter.notifyDataSetChanged();
                updateQueueSubtitle(textSubtitle);
                return true;
            }
            if (id == 2) {
                dialog.dismiss();
                playlistDialogs.showSongPlaylistChooser(song);
                return true;
            }
            if (id == 3) {
                removeQueueAt(position);
                adapter.notifyDataSetChanged();
                updateQueueSubtitle(textSubtitle);
                if (playQueue.isEmpty()) {
                    dialog.dismiss();
                }
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void moveQueueItem(int from, int to) {
        if (from < 0 || to < 0 || from >= playQueue.size() || to >= playQueue.size() || from == to) {
            return;
        }
        ApiClient.MediaItemModel moved = playQueue.remove(from);
        playQueue.add(to, moved);
        if (currentPlaying != null) {
            int index = findQueueIndexById(currentPlaying.id);
            if (index >= 0) {
                playQueueIndex = index;
            }
        } else if (playQueueIndex == from) {
            playQueueIndex = to;
        } else if (from < playQueueIndex && to >= playQueueIndex) {
            playQueueIndex--;
        } else if (from > playQueueIndex && to <= playQueueIndex) {
            playQueueIndex++;
        }
        syncQueueToService(currentPlaying == null ? null : currentPlaying.id);
    }

    private void queueInsertNext(int sourceIndex) {
        if (sourceIndex < 0 || sourceIndex >= playQueue.size() || playQueue.size() < 2) {
            return;
        }
        if (playQueueIndex < 0 || playQueueIndex >= playQueue.size()) {
            playQueueIndex = findQueueIndexById(currentPlaying == null ? "" : currentPlaying.id);
        }
        if (playQueueIndex < 0 || playQueueIndex >= playQueue.size()) {
            return;
        }
        int targetIndex = Math.min(playQueueIndex + 1, playQueue.size() - 1);
        if (sourceIndex == targetIndex) {
            return;
        }
        ApiClient.MediaItemModel item = playQueue.remove(sourceIndex);
        if (sourceIndex < targetIndex) {
            targetIndex--;
        }
        playQueue.add(targetIndex, item);
        if (currentPlaying != null) {
            int index = findQueueIndexById(currentPlaying.id);
            if (index >= 0) {
                playQueueIndex = index;
            }
        }
        syncQueueToService(currentPlaying == null ? null : currentPlaying.id);
    }

    private void removeQueueAt(int index) {
        if (index < 0 || index >= playQueue.size()) {
            return;
        }
        ApiClient.MediaItemModel removed = playQueue.remove(index);
        if (removed != null && TextUtils.equals(removed.id, pendingForceNextSongId)) {
            pendingForceNextSongId = null;
        }
        if (playQueue.isEmpty()) {
            pendingForceNextSongId = null;
            stopAllPendingPlayback();
            toast("播放队列已清空");
            return;
        }
        boolean removedCurrent = currentPlaying != null && TextUtils.equals(currentPlaying.id, removed.id);
        if (removedCurrent) {
            int next = Math.min(index, playQueue.size() - 1);
            playQueueIndex = next;
            playItem(playQueue.get(next), playQueue);
            return;
        }
        if (playQueueIndex > index) {
            playQueueIndex--;
        } else if (playQueueIndex >= playQueue.size()) {
            playQueueIndex = playQueue.size() - 1;
        }
        syncQueueToService(currentPlaying == null ? null : currentPlaying.id);
    }

    private void syncQueueToService(@Nullable String currentSongId) {
        PlaybackService.syncQueueFromUi(
                playQueue,
                currentSongId,
                shuffleEnabled,
                repeatAllEnabled ? 1 : 0,
                repeatOneEnabled,
                pendingForceNextSongId
        );
    }

    private String currentBrowserQueueContextKey() {
        return effectiveBrowserMode()
                + "|" + safeKey(currentParentId)
                + "|" + safeKey(currentParentType)
                + "|" + safeKey(currentKeyword)
                + "|" + safeKey(currentSortBy)
                + "|" + safeKey(currentSortDirection);
    }

    private boolean isSameBrowserQueueContext() {
        return !TextUtils.isEmpty(playQueueContextKey)
                && TextUtils.equals(playQueueContextKey, currentBrowserQueueContextKey());
    }

    private String safeKey(String value) {
        return value == null ? "" : value;
    }

    private void updateQueueSubtitle(TextView textSubtitle) {
        int total = playQueue.size();
        int current = playQueueIndex >= 0 ? playQueueIndex + 1 : 0;
        if (currentPlaying != null) {
            int fromId = findQueueIndexById(currentPlaying.id);
            if (fromId >= 0) {
                current = fromId + 1;
            }
        }
        textSubtitle.setText("共 " + total + " 首 · 当前第 " + Math.max(current, 1) + " 首");
    }

    private void saveQueueAsNewPlaylist() {
        if (playQueue.isEmpty()) {
            toast("队列为空");
            return;
        }
        View inputView = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null, false);
        TextView textTitle = inputView.findViewById(R.id.textInputTitle);
        TextView textSubtitle = inputView.findViewById(R.id.textInputSubtitle);
        EditText editName = inputView.findViewById(R.id.editInputValue);
        TextView buttonCancel = inputView.findViewById(R.id.buttonInputCancel);
        TextView buttonConfirm = inputView.findViewById(R.id.buttonInputConfirm);

        textTitle.setText("保存正在播放列表");
        textSubtitle.setText("将当前队列保存为新歌单");
        editName.setText("我的正在播放队列");
        editName.setSelection(editName.getText() == null ? 0 : editName.getText().length());
        buttonConfirm.setText("保存");

        AlertDialog dialog = new AlertDialog.Builder(this).setView(inputView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        buttonCancel.setOnClickListener(v -> dialog.dismiss());
        buttonConfirm.setOnClickListener(v -> {
            String name = editName.getText() == null ? "" : editName.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                toast("歌单名称不能为空");
                return;
            }
            ArrayList<String> songIds = collectQueueSongIds();
            if (songIds.isEmpty()) {
                toast("队列中没有可保存歌曲");
                return;
            }
            apiClient.createPlaylist(name, new ApiClient.StringCallback() {
                @Override
                public void onSuccess(String playlistId) {
                    apiClient.addSongsToPlaylist(playlistId, songIds, new ApiClient.StringCallback() {
                        @Override
                        public void onSuccess(String value) {
                            toast("已保存为新歌单");
                        }

                        @Override
                        public void onError(String message) {
                            toast(message);
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    toast(message);
                }
            });
            dialog.dismiss();
        });
        dialog.show();
    }

    private ArrayList<String> collectQueueSongIds() {
        ArrayList<String> songIds = new ArrayList<>();
        for (ApiClient.MediaItemModel item : playQueue) {
            if (item == null || TextUtils.isEmpty(item.id)) {
                continue;
            }
            if (!songIds.contains(item.id)) {
                songIds.add(item.id);
            }
        }
        return songIds;
    }

    private void stopNowPlayingIndicatorAnimation(ImageView view) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    private void startNowPlayingIndicatorAnimation(ImageView view) {
        if (view == null) {
            return;
        }
        stopNowPlayingIndicatorAnimation(view);
        view.animate()
                .alpha(0.35f)
                .scaleX(0.84f)
                .scaleY(0.84f)
                .setDuration(520L)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(() -> view.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(520L)
                        .setInterpolator(new LinearInterpolator())
                        .withEndAction(() -> {
                            if (view.getVisibility() == View.VISIBLE) {
                                startNowPlayingIndicatorAnimation(view);
                            }
                        })
                        .start())
                .start();
    }

    private void showOfflineCacheDialog() {
        List<ApiClient.MediaItemModel> cached = collectCachedSongs();
        if (cached.isEmpty()) {
            toast("暂无已缓存的音乐");
            return;
        }
        String[] entries = new String[cached.size()];
        for (int i = 0; i < cached.size(); i++) {
            entries[i] = cached.get(i).title + " \u00B7 " + cached.get(i).artist;
        }
        new AlertDialog.Builder(this)
                .setTitle("离线已缓存音乐")
                .setItems(entries, (dialog, which) -> playItem(cached.get(which), cached))
                .setNegativeButton("取消", null)
                .show();
    }

    private List<ApiClient.MediaItemModel> collectCachedSongs() {
        List<ApiClient.MediaItemModel> all = new ArrayList<>();
        all.addAll(apiClient.readCachedSongs());
        all.addAll(apiClient.readRootSongCache());
        all.addAll(apiClient.readHistory());
        List<ApiClient.MediaItemModel> cached = new ArrayList<>();
        for (ApiClient.MediaItemModel item : all) {
            if (!"song".equals(item.type) || !apiClient.isSongCached(item)) {
                continue;
            }
            boolean duplicate = false;
            for (ApiClient.MediaItemModel existing : cached) {
                if (TextUtils.equals(existing.id, item.id)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                cached.add(item);
            }
        }
        return cached;
    }

    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "默认排序 A-Z");
        menu.getMenu().add(0, 2, 1, "默认排序 Z-A");
        menu.getMenu().add(0, 3, 2, "歌手优先 A-Z");
        menu.getMenu().add(0, 4, 3, "专辑优先 A-Z");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    currentSortBy = defaultSortBy(currentMode);
                    currentSortDirection = "ASC";
                    break;
                case 2:
                    currentSortBy = defaultSortBy(currentMode);
                    currentSortDirection = "DESC";
                    break;
                case 3:
                    currentSortBy = "artist";
                    currentSortDirection = "ASC";
                    break;
                case 4:
                    currentSortBy = "album";
                    currentSortDirection = "ASC";
                    break;
                default:
                    return false;
            }
            loadFirstPage(true);
            return true;
        });
        menu.show();
    }

    private String defaultSortBy(String mode) {
        if ("artists".equals(mode) || "albums".equals(mode) || "playlists".equals(mode)) {
            return "name";
        }
        return "title";
    }

    private String effectiveBrowserMode() {
        if ("playlists".equals(currentMode) && "playlist".equals(currentParentType)) {
            return "songs";
        }
        if ("search".equals(currentMode) && TextUtils.isEmpty(currentKeyword)) {
            return "folders";
        }
        return currentMode;
    }

    private void addTab(String mode, String label) {
        TabLayout.Tab tab = tabLayout.newTab();
        View custom = LayoutInflater.from(this).inflate(R.layout.item_tab_chip, tabLayout, false);
        TextView text = custom.findViewById(R.id.textTab);
        text.setText(label);
        tab.setCustomView(custom);
        tab.setText(label);
        tab.setTag(mode);
        tabLayout.addTab(tab);
    }

    private void setTabSelectedState(TabLayout.Tab tab, boolean selected) {
        if (tab == null) {
            return;
        }
        View view = tab.getCustomView();
        if (view != null) {
            view.setSelected(selected);
        }
    }

    private boolean selectTabByMode(String mode) {
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null && mode.equals(tab.getTag())) {
                if (tab.isSelected()) {
                    return false;
                }
                tab.select();
                return true;
            }
        }
        return false;
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static final class FolderNavigationState {
        final String parentId;
        final String parentType;
        final String header;

        FolderNavigationState(String parentId, String parentType, String header) {
            this.parentId = parentId;
            this.parentType = parentType;
            this.header = header;
        }
    }

    private int typeIcon(String type) {
        if ("artist".equals(type)) {
            return R.drawable.ic_artist;
        }
        if ("album".equals(type)) {
            return R.drawable.ic_album;
        }
        if ("playlist".equals(type)) {
            return R.drawable.ic_playlist;
        }
        if ("folder".equals(type)) {
            return R.drawable.ic_folder;
        }
        return R.drawable.ic_music_note;
    }

    private String typeLabel(ApiClient.MediaItemModel item) {
        if ("artist".equals(item.type)) {
            return "歌手";
        }
        if ("album".equals(item.type)) {
            return "专辑";
        }
        if ("playlist".equals(item.type)) {
            return "歌单";
        }
        if ("folder".equals(item.type)) {
            return "文件夹";
        }
        return "歌曲";
    }

    private String metaSuffix(ApiClient.MediaItemModel item) {
        if (item.durationMs > 0L) {
            return " \u00B7 " + formatDuration(item.durationMs);
        }
        if (!TextUtils.isEmpty(item.path)) {
            return " \u00B7 " + item.path;
        }
        return "";
    }

    private String formatDuration(long durationMs) {
        long totalSeconds = Math.max(durationMs, 0L) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private void toast(String message) {
        postToMain(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private void postToMain(Runnable runnable) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            runnable.run();
        } else {
            progressHandler.post(runnable);
        }
    }

    private final class BrowserAdapter extends RecyclerView.Adapter<BrowserAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_browser, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ApiClient.MediaItemModel item = browserItems.get(position);
            boolean searchSelectMode = "search".equals(currentMode) && "song".equals(item.type);
            applyBrowserItemStyle(holder, isCompactSongListMode());
            holder.textTitle.setText(item.title);
            holder.textSubtitle.setText(item.subtitle);
            holder.textMeta.setText(typeLabel(item) + metaSuffix(item));
            holder.imageCover.setImageResource(typeIcon(item.type));
            boolean searchAddEnabled = "search".equals(currentMode) && "song".equals(item.type);
            holder.imageCached.setImageResource(R.drawable.ic_cloud_cached);
            holder.imageCached.setOnClickListener(null);
            holder.imageCached.setVisibility(!searchAddEnabled && "song".equals(item.type) && cachedSongIds.contains(item.id) ? View.VISIBLE : View.GONE);
            holder.imageAction.setVisibility(searchAddEnabled ? View.VISIBLE : View.GONE);
            holder.imageAction.setOnClickListener(searchAddEnabled ? v -> playlistDialogs.showSongPlaylistChooser(item) : null);
            holder.checkSelect.setVisibility(searchSelectMode ? View.VISIBLE : View.GONE);
            holder.checkSelect.setOnCheckedChangeListener(null);
            holder.checkSelect.setChecked(isSongSelected(item.id));
            holder.checkSelect.setOnClickListener(v -> toggleSearchSelection(item));
            loadCoverInto(item.type, item.id, holder.imageCover);
            holder.itemView.setOnClickListener(v -> {
                playItem(item, browserItems);
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (!"song".equals(item.type)) {
                    return false;
                }
                showSongQueueActionMenu(v, item);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return browserItems.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            ImageView imageCover;
            ImageView imageCached;
            ImageView imageAction;
            android.widget.CheckBox checkSelect;
            TextView textTitle;
            TextView textSubtitle;
            TextView textMeta;

            Holder(@NonNull View itemView) {
                super(itemView);
                imageCover = itemView.findViewById(R.id.imageCover);
                imageCached = itemView.findViewById(R.id.imageCached);
                imageAction = itemView.findViewById(R.id.imageAction);
                checkSelect = itemView.findViewById(R.id.checkSelect);
                textTitle = itemView.findViewById(R.id.textTitle);
                textSubtitle = itemView.findViewById(R.id.textSubtitle);
                textMeta = itemView.findViewById(R.id.textMeta);
            }
        }
    }

    private boolean isCompactSongListMode() {
        return ("playlists".equals(currentMode) && TextUtils.isEmpty(currentParentType))
                || "playlist".equals(currentParentType)
                || "folder".equals(currentParentType)
                || "search".equals(currentMode);
    }

    private void applyBrowserItemStyle(BrowserAdapter.Holder holder, boolean compact) {
        ViewGroup.LayoutParams imageParams = holder.imageCover.getLayoutParams();
        imageParams.width = dp(compact ? 48 : 52);
        imageParams.height = dp(compact ? 48 : 52);
        holder.imageCover.setLayoutParams(imageParams);

        holder.textTitle.setTextSize(compact ? 14.5f : 15f);
        holder.textSubtitle.setTextSize(compact ? 11.5f : 12f);
        holder.textMeta.setTextSize(compact ? 11f : 12f);
    }

    private void toggleSearchSelection(ApiClient.MediaItemModel item) {
        int index = findSelectedSearchSongIndex(item.id);
        if (index >= 0) {
            selectedSearchSongs.remove(index);
        } else {
            selectedSearchSongs.add(item);
        }
        updateSearchUi();
        if (browserAdapter != null) {
            browserAdapter.notifyDataSetChanged();
        }
    }

    private void refreshCachedSongIndex() {
        cachedSongIds.clear();
        for (ApiClient.MediaItemModel item : apiClient.readCachedSongs()) {
            if (item != null && !TextUtils.isEmpty(item.id)) {
                cachedSongIds.add(item.id);
            }
        }
    }

    private boolean isSongSelected(String songId) {
        return findSelectedSearchSongIndex(songId) >= 0;
    }

    private int findSelectedSearchSongIndex(String songId) {
        for (int i = 0; i < selectedSearchSongs.size(); i++) {
            if (TextUtils.equals(selectedSearchSongs.get(i).id, songId)) {
                return i;
            }
        }
        return -1;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density);
    }

    private final class NowPlayingQueueAdapter extends RecyclerView.Adapter<NowPlayingQueueAdapter.Holder> {

        interface ActionListener {
            void onPlayAt(int position);
            void onShowActions(View anchor, int position);
            void onStartDrag(RecyclerView.ViewHolder holder);
        }

        @Nullable
        private ActionListener actionListener;

        void setActionListener(@Nullable ActionListener listener) {
            this.actionListener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_now_playing_queue, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ApiClient.MediaItemModel item = playQueue.get(position);
            holder.textTitle.setText(item.title);
            holder.textSubtitle.setText(firstNonEmpty(item.artist, "") + (TextUtils.isEmpty(item.album) ? "" : " \u00B7 " + item.album));
            holder.imageCover.setImageResource(R.drawable.ic_music_note);
            if (!TextUtils.isEmpty(item.localCoverPath) && new File(item.localCoverPath).exists()) {
                loadLocalCoverInto(new File(item.localCoverPath), holder.imageCover, false, R.drawable.ic_music_note);
            } else if (!TextUtils.isEmpty(item.id)) {
                loadCoverInto("song", item.id, holder.imageCover);
            }
            boolean nowPlaying = currentPlaying != null && TextUtils.equals(currentPlaying.id, item.id);
            holder.imageNowPlaying.setVisibility(nowPlaying ? View.VISIBLE : View.GONE);
            holder.itemView.setAlpha(nowPlaying ? 1f : 0.94f);
            stopNowPlayingIndicatorAnimation(holder.imageNowPlaying);
            if (nowPlaying) {
                startNowPlayingIndicatorAnimation(holder.imageNowPlaying);
            }

            holder.itemView.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onPlayAt(holder.getBindingAdapterPosition());
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onShowActions(holder.imageAction, holder.getBindingAdapterPosition());
                    return true;
                }
                return false;
            });
            holder.imageAction.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onShowActions(holder.imageAction, holder.getBindingAdapterPosition());
                }
            });
            holder.imageDrag.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && actionListener != null) {
                    actionListener.onStartDrag(holder);
                    return true;
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return playQueue.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView imageCover;
            final ImageView imageNowPlaying;
            final ImageView imageAction;
            final ImageView imageDrag;
            final TextView textTitle;
            final TextView textSubtitle;

            Holder(@NonNull View itemView) {
                super(itemView);
                imageCover = itemView.findViewById(R.id.imageQueueCover);
                imageNowPlaying = itemView.findViewById(R.id.imageQueueNowPlaying);
                imageAction = itemView.findViewById(R.id.imageQueueAction);
                imageDrag = itemView.findViewById(R.id.imageQueueDrag);
                textTitle = itemView.findViewById(R.id.textQueueSongTitle);
                textSubtitle = itemView.findViewById(R.id.textQueueSongSubtitle);
            }
        }
    }

    private static final class BrowserNavigationState {
        final String mode;
        final String parentId;
        final String parentType;
        final String keyword;
        final String sortBy;
        final String sortDirection;
        final String header;

        BrowserNavigationState(String mode, String parentId, String parentType, String keyword, String sortBy, String sortDirection, String header) {
            this.mode = mode;
            this.parentId = parentId;
            this.parentType = parentType;
            this.keyword = keyword;
            this.sortBy = sortBy;
            this.sortDirection = sortDirection;
            this.header = header;
        }
    }

    private final class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lyric, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ApiClient.LyricLine line = lyricLines.get(position);
            String text = line.primary;
            if (!TextUtils.isEmpty(line.secondary)) {
                text = text + "\n" + line.secondary;
                  }
            holder.textLyric.setText(text);
            boolean active = position == currentLyricIndex;
            holder.textLyric.animate().cancel();
            holder.textLyric.setTextColor(active ? 0xFFFFFFFF : 0x88FFFFFF);
            holder.textLyric.setShadowLayer(active ? 0f : 6f, 0f, 0f, 0x66000000);
            holder.textLyric.setTextSize(active ? 23f : 18f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                holder.textLyric.setRenderEffect(active ? null : RenderEffect.createBlurEffect(1.5f, 1.5f, Shader.TileMode.CLAMP));
            }
            holder.textLyric.animate()
                    .alpha(active ? 1f : 0.42f)
                    .scaleX(active ? 1.03f : 0.94f)
                    .scaleY(active ? 1.03f : 0.94f)
                    .translationY(active ? 0f : dp(2))
                    .setDuration(active ? 280L : 240L)
                    .setInterpolator(panelInterpolator)
                    .start();
        }

        @Override
        public int getItemCount() {
            return lyricLines.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            TextView textLyric;

            Holder(@NonNull View itemView) {
                super(itemView);
                textLyric = itemView.findViewById(R.id.textLyric);
            }
        }
    }
}
