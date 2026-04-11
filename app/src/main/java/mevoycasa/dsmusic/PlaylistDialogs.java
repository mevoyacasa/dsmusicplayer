package mevoycasa.dsmusic;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class PlaylistDialogs {

    private static final int SORT_NAME = 0;
    private static final int SORT_DURATION = 1;
    private static final int SORT_ARTIST = 2;

    private final AppCompatActivity activity;
    private final ApiClient apiClient;
    @Nullable
    private AlertDialog panelDialog;

    PlaylistDialogs(AppCompatActivity activity, ApiClient apiClient) {
        this.activity = activity;
        this.apiClient = apiClient;
    }

    void showPlaylistManager() {
        apiClient.fetchPersonalPlaylists(new ApiClient.ArrayCallback() {
            @Override
            public void onSuccess(List<ApiClient.MediaItemModel> items, boolean hasMore, int total) {
                List<ApiClient.MediaItemModel> playlists = filterRegularPlaylists(items);
                showPlaylistPanel(
                        activity.getString(R.string.playlist_management),
                        playlists.isEmpty() ? activity.getString(R.string.no_playlists_yet) : activity.getString(R.string.click_playlist_to_manage_songs),
                        playlists, false, null, null);
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    void showCreatePlaylist() {
        promptPlaylistName(
                activity.getString(R.string.create_new_playlist),
                activity.getString(R.string.input_playlist_name), "",
                activity.getString(R.string.create), name ->
                apiClient.createPlaylist(name, new ApiClient.StringCallback() {
                    @Override
                    public void onSuccess(String value) {
                        toast(activity.getString(R.string.playlist_created));
                        showPlaylistManager();
                    }

                    @Override
                    public void onError(String message) {
                        toast(message);
                    }
                }));
    }

    void showAccountInfo(String account, String host, int cachedCount, int historyCount) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_account_info, null, false);
        ((TextView) view.findViewById(R.id.textAccountNameValue)).setText(activity.getString(R.string.account_colon) + safe(account));
        ((TextView) view.findViewById(R.id.textAccountHostValue)).setText(activity.getString(R.string.server_colon) + safe(host));
        boolean hasSid = !TextUtils.isEmpty(apiClient.prefs().getString(ApiClient.KEY_SID, ""));
        ((TextView) view.findViewById(R.id.textAccountSidValue)).setText("SID：" + (hasSid ? activity.getString(R.string.sid_saved) : activity.getString(R.string.sid_missing)));
        ((TextView) view.findViewById(R.id.textAccountCacheValue)).setText(activity.getString(R.string.offline_cache_with_count, cachedCount));
        ((TextView) view.findViewById(R.id.textAccountHistoryValue)).setText(activity.getString(R.string.play_history_with_count, historyCount));
        boolean rememberAccount = apiClient.prefs().getBoolean(ApiClient.KEY_REMEMBER_ACCOUNT, true);
        boolean rememberPassword = apiClient.prefs().getBoolean(ApiClient.KEY_REMEMBER_PASSWORD, true);
        ((TextView) view.findViewById(R.id.textAccountRememberValue)).setText(
                activity.getString(R.string.remember_login)
                        + (rememberAccount ? activity.getString(R.string.on) : activity.getString(R.string.off))
                        + activity.getString(R.string.slash_password)
                        + (rememberPassword ? activity.getString(R.string.on) : activity.getString(R.string.off)));

        AlertDialog dialog = createStyledDialog(view);
        view.findViewById(R.id.buttonAccountClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    void showSongPlaylistChooser(ApiClient.MediaItemModel song) {
        if (song == null || TextUtils.isEmpty(song.id)) {
            toast(activity.getString(R.string.invalid_song));
            return;
        }
        apiClient.fetchPersonalPlaylists(new ApiClient.ArrayCallback() {
            @Override
            public void onSuccess(List<ApiClient.MediaItemModel> items, boolean hasMore, int total) {
                List<ApiClient.MediaItemModel> playlists = filterRegularPlaylists(items);
                if (playlists.isEmpty()) {
                    promptCreatePlaylist(song);
                    return;
                }
                showPlaylistPanel(activity.getString(R.string.add_to_playlist), activity.getString(R.string.song_colon) + safe(song.title), playlists, true, song, null);
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    void showSongsPlaylistChooser(List<ApiClient.MediaItemModel> songs) {
        if (songs == null || songs.isEmpty()) {
            toast(activity.getString(R.string.please_select_songs_first));
            return;
        }
        List<String> songIds = collectSongIds(songs);
        apiClient.fetchPersonalPlaylists(new ApiClient.ArrayCallback() {
            @Override
            public void onSuccess(List<ApiClient.MediaItemModel> items, boolean hasMore, int total) {
                List<ApiClient.MediaItemModel> playlists = filterRegularPlaylists(items);
                if (playlists.isEmpty()) {
                    showCreatePlaylist();
                    return;
                }
                showPlaylistPanel(
                        activity.getString(R.string.batch_add),
                        activity.getString(R.string.selected_colon) + songIds.size() + activity.getString(R.string.songs_count),
                        playlists, true, null, songIds);
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    private void showPlaylistPanel(
            String title,
            String subtitle,
            List<ApiClient.MediaItemModel> playlists,
            boolean selectMode,
            @Nullable ApiClient.MediaItemModel pendingSong,
            @Nullable List<String> pendingSongIds
    ) {
        dismissPanelDialog();
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_playlist_manager, null, false);
        TextView textTitle = view.findViewById(R.id.textPanelTitle);
        TextView textSubtitle = view.findViewById(R.id.textPanelSubtitle);
        TextView buttonPrimary = view.findViewById(R.id.buttonPanelPrimary);
        TextView buttonSecondary = view.findViewById(R.id.buttonPanelSecondary);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerPanel);

        textTitle.setText(title);
        textSubtitle.setText(subtitle);
        buttonPrimary.setText(activity.getString(R.string.create_new_playlist));
        buttonSecondary.setText(activity.getString(R.string.close));

        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setVerticalScrollBarEnabled(true);
        enableEdgeFastScroll(recyclerView);
        recyclerView.setAdapter(new PlaylistPanelAdapter(playlists, selectMode, pendingSong, pendingSongIds));

        panelDialog = createStyledDialog(view);
        buttonSecondary.setOnClickListener(v -> dismissPanelDialog());
        buttonPrimary.setOnClickListener(v -> {
            dismissPanelDialog();
            promptCreatePlaylist(pendingSong);
        });
        panelDialog.show();
    }

    private void showPlaylistSongsDialog(ApiClient.MediaItemModel playlist) {
        apiClient.fetchBrowserItems("songs", playlist.id, "playlist", null, 0, 1000, "title", "ASC", new ApiClient.ArrayCallback() {
            @Override
            public void onSuccess(List<ApiClient.MediaItemModel> items, boolean hasMore, int total) {
                showSongSelectionDialog(
                        playlist.title,
                        items.isEmpty() ? activity.getString(R.string.no_songs_in_playlist) : activity.getString(R.string.songs_count_label, items.size()),
                        items,
                        activity.getString(R.string.button_back),
                        activity.getString(R.string.remove_selected),
                        selected -> removeSelectedSongsFromPlaylist(playlist, items, selected),
                        activity.getString(R.string.add_songs),
                        () -> showAddSongsToPlaylistDialog(playlist),
                        () -> showPlaylistManager()
                );
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    private void showAddSongsToPlaylistDialog(ApiClient.MediaItemModel playlist) {
        apiClient.fetchAllSongs(new ApiClient.ArrayCallback() {
            @Override
            public void onSuccess(List<ApiClient.MediaItemModel> items, boolean hasMore, int total) {
                if (items.isEmpty()) {
                    toast(activity.getString(R.string.no_songs_to_add));
                    return;
                }
                showSongSelectionDialog(
                        activity.getString(R.string.add_to) + safe(playlist.title),
                        activity.getString(R.string.select_songs_and_confirm),
                        items,
                        activity.getString(R.string.button_back),
                        activity.getString(R.string.add_selected),
                        selected -> addSongsToPlaylist(playlist, selected, true),
                        null,
                        null,
                        () -> showPlaylistSongsDialog(playlist)
                );
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    private void showSongSelectionDialog(
            String title,
            String subtitle,
            List<ApiClient.MediaItemModel> items,
            String cancelLabel,
            String confirmLabel,
            SelectionHandler handler,
            @Nullable String extraLabel,
            @Nullable Runnable extraAction,
            @Nullable Runnable cancelAction
    ) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_song_picker, null, false);
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

        textTitle.setText(title);
        textSubtitle.setText(subtitle);
        buttonCancel.setText(cancelLabel);
        buttonConfirm.setText(confirmLabel);

        if (TextUtils.isEmpty(extraLabel) || extraAction == null) {
            buttonExtra.setVisibility(View.GONE);
        } else {
            buttonExtra.setVisibility(View.VISIBLE);
            buttonExtra.setText(extraLabel);
        }

        SongPickerAdapter adapter = new SongPickerAdapter(items);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setVerticalScrollBarEnabled(true);
        enableEdgeFastScroll(recyclerView);
        recyclerView.setAdapter(adapter);

        adapter.setSelectionChangeListener(() -> updateSelectAllButton(buttonSelectAll, adapter));
        bindSortChip(buttonSortName, buttonSortDuration, buttonSortArtist, adapter, SORT_NAME);
        buttonSortName.setOnClickListener(v -> bindSortChip(buttonSortName, buttonSortDuration, buttonSortArtist, adapter, SORT_NAME));
        buttonSortDuration.setOnClickListener(v -> bindSortChip(buttonSortName, buttonSortDuration, buttonSortArtist, adapter, SORT_DURATION));
        buttonSortArtist.setOnClickListener(v -> bindSortChip(buttonSortName, buttonSortDuration, buttonSortArtist, adapter, SORT_ARTIST));
        buttonSelectAll.setOnClickListener(v -> {
            adapter.toggleVisibleSelection();
            updateSelectAllButton(buttonSelectAll, adapter);
        });
        updateSelectAllButton(buttonSelectAll, adapter);

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
                updateSelectAllButton(buttonSelectAll, adapter);
            }
        });

        AlertDialog dialog = createStyledDialog(view);
        buttonCancel.setOnClickListener(v -> {
            dialog.dismiss();
            if (cancelAction != null) {
                cancelAction.run();
            }
        });
        buttonExtra.setOnClickListener(v -> {
            dialog.dismiss();
            if (extraAction != null) {
                extraAction.run();
            }
        });
        buttonConfirm.setOnClickListener(v -> {
            List<String> selected = adapter.getSelectedSongIds();
            if (selected.isEmpty()) {
                toast(activity.getString(R.string.please_select_at_least_one_song));
                return;
            }
            dialog.dismiss();
            handler.onSelected(selected);
        });
        dialog.show();
    }

    private void bindSortChip(TextView name, TextView duration, TextView artist, SongPickerAdapter adapter, int sortType) {
        name.setBackgroundResource(sortType == SORT_NAME ? R.drawable.bg_button : R.drawable.bg_chip_soft);
        duration.setBackgroundResource(sortType == SORT_DURATION ? R.drawable.bg_button : R.drawable.bg_chip_soft);
        artist.setBackgroundResource(sortType == SORT_ARTIST ? R.drawable.bg_button : R.drawable.bg_chip_soft);

        int activeColor = ContextCompat.getColor(activity, android.R.color.white);
        int normalColor = ContextCompat.getColor(activity, R.color.seed);
        name.setTextColor(sortType == SORT_NAME ? activeColor : normalColor);
        duration.setTextColor(sortType == SORT_DURATION ? activeColor : normalColor);
        artist.setTextColor(sortType == SORT_ARTIST ? activeColor : normalColor);
        adapter.sortBy(sortType);
    }

    private void updateSelectAllButton(TextView buttonSelectAll, SongPickerAdapter adapter) {
        if (buttonSelectAll == null || adapter == null) {
            return;
        }
        boolean allVisibleSelected = adapter.hasVisibleSongs() && adapter.areAllVisibleSongsSelected();
        buttonSelectAll.setText(allVisibleSelected ? activity.getString(R.string.deselect_all) : activity.getString(R.string.select_all));
        buttonSelectAll.setBackgroundResource(allVisibleSelected ? R.drawable.bg_button : R.drawable.bg_chip_soft);
        int activeColor = ContextCompat.getColor(activity, android.R.color.white);
        int normalColor = ContextCompat.getColor(activity, R.color.seed);
        buttonSelectAll.setTextColor(allVisibleSelected ? activeColor : normalColor);
    }

    private void removeSelectedSongsFromPlaylist(ApiClient.MediaItemModel playlist, List<ApiClient.MediaItemModel> currentSongs, List<String> selectedSongIds) {
        List<String> remaining = new ArrayList<>();
        for (ApiClient.MediaItemModel item : currentSongs) {
            if (!selectedSongIds.contains(item.id)) {
                remaining.add(item.id);
            }
        }
        int removed = currentSongs.size() - remaining.size();
        if (removed <= 0) {
            toast(activity.getString(R.string.no_songs_selected));
            return;
        }
        apiClient.replacePlaylistSongs(playlist.id, remaining, currentSongs.size(), new ApiClient.StringCallback() {
            @Override
            public void onSuccess(String value) {
                toast(activity.getString(R.string.removed_count_songs, removed));
                showPlaylistSongsDialog(playlist);
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    private void addSongsToPlaylist(ApiClient.MediaItemModel playlist, List<String> songIds, boolean backToPlaylistSongs) {
        List<String> normalized = normalizeSongIds(songIds);
        if (normalized.isEmpty()) {
            toast(activity.getString(R.string.no_songs_selected));
            return;
        }
        apiClient.fetchPlaylistSongIds(playlist.id, new ApiClient.StringListCallback() {
            @Override
            public void onSuccess(List<String> values) {
                HashSet<String> existing = new HashSet<>(values == null ? Collections.emptyList() : values);
                List<String> toAdd = new ArrayList<>();
                for (String songId : normalized) {
                    if (!existing.contains(songId)) {
                        toAdd.add(songId);
                    }
                }
                if (toAdd.isEmpty()) {
                    toast(activity.getString(R.string.songs_already_in_playlist));
                    if (backToPlaylistSongs) {
                        showPlaylistSongsDialog(playlist);
                    } else {
                        showPlaylistManager();
                    }
                    return;
                }
                apiClient.addSongsToPlaylist(playlist.id, toAdd, new ApiClient.StringCallback() {
                    @Override
                    public void onSuccess(String value) {
                        toast(activity.getString(R.string.added_count_songs, toAdd.size()));
                        if (backToPlaylistSongs) {
                            showPlaylistSongsDialog(playlist);
                        } else {
                            showPlaylistManager();
                        }
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
    }

    private void promptCreatePlaylist(@Nullable ApiClient.MediaItemModel pendingSong) {
        promptPlaylistName(
                activity.getString(R.string.create_new_playlist),
                activity.getString(R.string.input_playlist_name), "",
                activity.getString(R.string.create), name ->
                apiClient.createPlaylist(name, new ApiClient.StringCallback() {
                    @Override
                    public void onSuccess(String playlistId) {
                        if (pendingSong == null || TextUtils.isEmpty(pendingSong.id)) {
                            toast(activity.getString(R.string.playlist_created));
                            showPlaylistManager();
                            return;
                        }
                        ArrayList<String> single = new ArrayList<>();
                        single.add(pendingSong.id);
                        ApiClient.MediaItemModel m = new ApiClient.MediaItemModel();
                        m.id = playlistId;
                        m.title = name;
                        addSongsToPlaylist(m, single, false);
                    }

                    @Override
                    public void onError(String message) {
                        toast(message);
                    }
                }));
    }

    private void promptPlaylistName(String title, String subtitle, String initial, String confirm, NameHandler handler) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_text_input, null, false);
        TextView textTitle = view.findViewById(R.id.textInputTitle);
        TextView textSubtitle = view.findViewById(R.id.textInputSubtitle);
        EditText editValue = view.findViewById(R.id.editInputValue);
        TextView buttonCancel = view.findViewById(R.id.buttonInputCancel);
        TextView buttonConfirm = view.findViewById(R.id.buttonInputConfirm);

        textTitle.setText(title);
        textSubtitle.setText(subtitle);
        editValue.setText(initial == null ? "" : initial);
        editValue.setSelection(editValue.getText().length());
        buttonConfirm.setText(confirm);

        AlertDialog dialog = createStyledDialog(view);
        buttonCancel.setOnClickListener(v -> dialog.dismiss());
        buttonConfirm.setOnClickListener(v -> {
            String value = editValue.getText() == null ? "" : editValue.getText().toString().trim();
            if (TextUtils.isEmpty(value)) {
                toast(activity.getString(R.string.name_cannot_be_empty));
                return;
            }
            dialog.dismiss();
            handler.onName(value);
        });
        dialog.show();
    }

    private void enableEdgeFastScroll(RecyclerView recyclerView) {
        final float edgeWidthPx = activity.getResources().getDisplayMetrics().density * 56f;
        recyclerView.setOnTouchListener((v, event) -> {
            RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
            if (adapter == null || adapter.getItemCount() < 2) {
                return false;
            }
            if (event.getX() < recyclerView.getWidth() - edgeWidthPx) {
                return false;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                float y = Math.max(0f, Math.min(event.getY(), recyclerView.getHeight()));
                float ratio = recyclerView.getHeight() <= 0 ? 0f : y / (float) recyclerView.getHeight();
                int position = Math.max(0, Math.min(adapter.getItemCount() - 1, Math.round((adapter.getItemCount() - 1) * ratio)));
                recyclerView.scrollToPosition(position);
                recyclerView.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            return action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        });
    }

    private List<String> normalizeSongIds(List<String> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String songId : songIds) {
            if (!TextUtils.isEmpty(songId)) {
                seen.add(songId);
            }
        }
        return new ArrayList<>(seen);
    }

    private List<ApiClient.MediaItemModel> filterRegularPlaylists(List<ApiClient.MediaItemModel> items) {
        List<ApiClient.MediaItemModel> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (ApiClient.MediaItemModel item : items) {
            if (item == null) {
                continue;
            }
            if (!"playlist".equals(item.type)) {
                continue;
            }
            if (TextUtils.isEmpty(item.id)) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    private List<String> collectSongIds(List<ApiClient.MediaItemModel> songs) {
        List<String> ids = new ArrayList<>();
        if (songs == null) {
            return ids;
        }
        for (ApiClient.MediaItemModel song : songs) {
            if (song != null && !TextUtils.isEmpty(song.id)) {
                ids.add(song.id);
            }
        }
        return ids;
    }

    private AlertDialog createStyledDialog(View view) {
        AlertDialog dialog = new AlertDialog.Builder(activity).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    private void dismissPanelDialog() {
        if (panelDialog != null && panelDialog.isShowing()) {
            panelDialog.dismiss();
        }
        panelDialog = null;
    }

    private String safe(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private interface SelectionHandler {
        void onSelected(List<String> selectedSongIds);
    }

    private interface NameHandler {
        void onName(String name);
    }

    private final class PlaylistPanelAdapter extends RecyclerView.Adapter<PlaylistPanelAdapter.Holder> {

        private final List<ApiClient.MediaItemModel> playlists;
        private final boolean selectMode;
        @Nullable
        private final ApiClient.MediaItemModel pendingSong;
        @Nullable
        private final List<String> pendingSongIds;

        PlaylistPanelAdapter(List<ApiClient.MediaItemModel> playlists, boolean selectMode, @Nullable ApiClient.MediaItemModel pendingSong, @Nullable List<String> pendingSongIds) {
            this.playlists = playlists == null ? new ArrayList<>() : playlists;
            this.selectMode = selectMode;
            this.pendingSong = pendingSong;
            this.pendingSongIds = pendingSongIds;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_manager, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ApiClient.MediaItemModel playlist = playlists.get(position);
            holder.textTitle.setText(safe(playlist.title));
            holder.textSubtitle.setText(TextUtils.isEmpty(playlist.subtitle) ? activity.getString(R.string.playlist) : playlist.subtitle);

            if (selectMode) {
                holder.imageAction.setVisibility(View.GONE);
                holder.itemView.setOnClickListener(v -> {
                    dismissPanelDialog();
                    if (pendingSongIds != null && !pendingSongIds.isEmpty()) {
                        addSongsToPlaylist(playlist, pendingSongIds, false);
                        return;
                    }
                    if (pendingSong != null && !TextUtils.isEmpty(pendingSong.id)) {
                        ArrayList<String> one = new ArrayList<>();
                        one.add(pendingSong.id);
                        addSongsToPlaylist(playlist, one, false);
                    }
                });
                return;
            }

            holder.imageAction.setVisibility(View.VISIBLE);
            holder.itemView.setOnClickListener(v -> {
                dismissPanelDialog();
                showPlaylistSongsDialog(playlist);
            });
            holder.itemView.setOnLongClickListener(v -> {
                showPlaylistActionsMenu(holder.imageAction, playlist);
                return true;
            });
            holder.imageAction.setOnClickListener(v -> showPlaylistActionsMenu(v, playlist));
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView textTitle;
            final TextView textSubtitle;
            final ImageView imageAction;

            Holder(@NonNull View itemView) {
                super(itemView);
                textTitle = itemView.findViewById(R.id.textPlaylistTitle);
                textSubtitle = itemView.findViewById(R.id.textPlaylistSubtitle);
                imageAction = itemView.findViewById(R.id.imagePlaylistAction);
            }
        }
    }

    private void showPlaylistActionsMenu(View anchor, ApiClient.MediaItemModel playlist) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenu().add(0, 1, 0, activity.getString(R.string.view_songs));
        menu.getMenu().add(0, 2, 1, activity.getString(R.string.add_songs));
        menu.getMenu().add(0, 3, 2, activity.getString(R.string.rename));
        menu.getMenu().add(0, 4, 3, activity.getString(R.string.delete));
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                dismissPanelDialog();
                showPlaylistSongsDialog(playlist);
                return true;
            }
            if (id == 2) {
                dismissPanelDialog();
                showAddSongsToPlaylistDialog(playlist);
                return true;
            }
            if (id == 3) {
                promptPlaylistName(
                        activity.getString(R.string.rename_playlist),
                        activity.getString(R.string.enter_new_name), playlist.title,
                        activity.getString(R.string.save), name ->
                        apiClient.renamePlaylist(playlist.id, name, new ApiClient.StringCallback() {
                            @Override
                            public void onSuccess(String value) {
                                toast(activity.getString(R.string.playlist_renamed));
                                showPlaylistManager();
                            }

                            @Override
                            public void onError(String message) {
                                toast(message);
                            }
                        }));
                return true;
            }
            if (id == 4) {
                new AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.delete_playlist))
                        .setMessage(activity.getString(R.string.confirm_delete_playlist_formatted, safe(playlist.title)))
                        .setNegativeButton(activity.getString(R.string.button_cancel), null)
                        .setPositiveButton(activity.getString(R.string.delete), (d, which) ->
                                apiClient.deletePlaylist(playlist.id, new ApiClient.StringCallback() {
                                    @Override
                                    public void onSuccess(String value) {
                                        toast(activity.getString(R.string.playlist_deleted));
                                        showPlaylistManager();
                                    }

                                    @Override
                                    public void onError(String message) {
                                        toast(message);
                                    }
                                }))
                        .show();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private static final class SongPickerAdapter extends RecyclerView.Adapter<SongPickerAdapter.Holder> {

        private final List<ApiClient.MediaItemModel> source = new ArrayList<>();
        private final List<ApiClient.MediaItemModel> display = new ArrayList<>();
        private final Set<String> selectedSongIds = new HashSet<>();
        private String keyword = "";
        private int sortType = SORT_NAME;
        @Nullable
        private Runnable selectionChangeListener;

        SongPickerAdapter(List<ApiClient.MediaItemModel> items) {
            if (items != null) {
                source.addAll(items);
            }
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

        void setSelectionChangeListener(@Nullable Runnable selectionChangeListener) {
            this.selectionChangeListener = selectionChangeListener;
        }

        List<String> getSelectedSongIds() {
            return new ArrayList<>(selectedSongIds);
        }

        boolean hasVisibleSongs() {
            return !display.isEmpty();
        }

        boolean areAllVisibleSongsSelected() {
            if (display.isEmpty()) {
                return false;
            }
            for (ApiClient.MediaItemModel item : display) {
                if (item == null || TextUtils.isEmpty(item.id) || !selectedSongIds.contains(item.id)) {
                    return false;
                }
            }
            return true;
        }

        void toggleVisibleSelection() {
            if (display.isEmpty()) {
                return;
            }
            boolean selectAll = !areAllVisibleSongsSelected();
            for (ApiClient.MediaItemModel item : display) {
                if (item == null || TextUtils.isEmpty(item.id)) {
                    continue;
                }
                if (selectAll) {
                    selectedSongIds.add(item.id);
                } else {
                    selectedSongIds.remove(item.id);
                }
            }
            notifyDataSetChanged();
            notifySelectionChange();
        }

        private void rebuild() {
            display.clear();
            for (ApiClient.MediaItemModel item : source) {
                if (item == null || TextUtils.isEmpty(item.id)) {
                    continue;
                }
                if (!"song".equals(item.type)) {
                    continue;
                }
                if (TextUtils.isEmpty(keyword)) {
                    display.add(item);
                    continue;
                }
                String title = safeText(item.title);
                String artist = safeText(item.artist);
                String search = (title + " " + artist).toLowerCase(Locale.ROOT);
                if (search.contains(keyword)) {
                    display.add(item);
                }
            }

            Comparator<ApiClient.MediaItemModel> comparator;
            if (sortType == SORT_DURATION) {
                comparator = Comparator.comparingLong(a -> a.durationMs);
            } else if (sortType == SORT_ARTIST) {
                comparator = Comparator.comparing((ApiClient.MediaItemModel a) -> safeText(a.artist), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing((ApiClient.MediaItemModel a) -> safeText(a.title), String.CASE_INSENSITIVE_ORDER);
            } else {
                comparator = Comparator.comparing((ApiClient.MediaItemModel a) -> safeText(a.title), String.CASE_INSENSITIVE_ORDER);
            }
            Collections.sort(display, comparator);
            notifyDataSetChanged();
            notifySelectionChange();
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
            holder.textDuration.setText(formatDuration(item.durationMs));
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(selectedSongIds.contains(item.id));
            holder.itemView.setOnClickListener(v -> {
                boolean next = !holder.checkBox.isChecked();
                holder.checkBox.setChecked(next);
            });
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedSongIds.add(item.id);
                } else {
                    selectedSongIds.remove(item.id);
                }
                notifySelectionChange();
            });
        }

        @Override
        public int getItemCount() {
            return display.size();
        }

        private static String safeText(String value) {
            return TextUtils.isEmpty(value) ? "-" : value;
        }

        private static String formatDuration(long durationMs) {
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

        private void notifySelectionChange() {
            if (selectionChangeListener != null) {
                selectionChangeListener.run();
            }
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
}
