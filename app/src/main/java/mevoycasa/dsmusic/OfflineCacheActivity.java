package mevoycasa.dsmusic;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class OfflineCacheActivity extends AppCompatActivity {

    private static final String KEY_OFFLINE_SORT_BY_PLAY_COUNT = "offline_sort_by_play_count";

    private ApiClient apiClient;
    private RecyclerView recyclerView;
    private TextView textEmpty;
    private TextView buttonSortByPlayCount;
    private boolean sortByPlayCount;
    private final List<ApiClient.MediaItemModel> cachedSongs = new ArrayList<>();
    private OfflineAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_cache);
        apiClient = new ApiClient(this);
        recyclerView = findViewById(R.id.recyclerOffline);
        textEmpty = findViewById(R.id.textOfflineEmpty);
        buttonSortByPlayCount = findViewById(R.id.buttonSortByPlayCount);
        adapter = new OfflineAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setVerticalScrollBarEnabled(true);
        recyclerView.setAdapter(adapter);

        sortByPlayCount = apiClient.prefs().getBoolean(KEY_OFFLINE_SORT_BY_PLAY_COUNT, false);
        refreshSortButton();

        findViewById(R.id.buttonOfflineBack).setOnClickListener(v -> finish());
        buttonSortByPlayCount.setOnClickListener(v -> {
            sortByPlayCount = !sortByPlayCount;
            apiClient.prefs().edit().putBoolean(KEY_OFFLINE_SORT_BY_PLAY_COUNT, sortByPlayCount).apply();
            refreshSortButton();
            loadCachedSongs();
        });
        findViewById(R.id.buttonClearHistory).setOnClickListener(v -> confirmClearHistory());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCachedSongs();
    }

    private void refreshSortButton() {
        if (buttonSortByPlayCount == null) {
            return;
        }
        buttonSortByPlayCount.setText(sortByPlayCount ? "按播放频率排序" : "默认排序");
        buttonSortByPlayCount.setBackgroundResource(sortByPlayCount ? R.drawable.bg_tab_selected_pill : R.drawable.bg_chip_soft);
        buttonSortByPlayCount.setTextColor(getColor(sortByPlayCount ? R.color.white : R.color.subtle));
    }

    private void loadCachedSongs() {
        cachedSongs.clear();
        List<ApiClient.MediaItemModel> items = apiClient.readCachedSongs();
        for (ApiClient.MediaItemModel item : items) {
            if (item == null || TextUtils.isEmpty(item.id)) {
                continue;
            }
            item.playCount = apiClient.getPlayCount(item.id);
            cachedSongs.add(item);
        }
        if (sortByPlayCount) {
            Collections.sort(cachedSongs, new Comparator<ApiClient.MediaItemModel>() {
                @Override
                public int compare(ApiClient.MediaItemModel a, ApiClient.MediaItemModel b) {
                    int pa = a == null ? 0 : a.playCount;
                    int pb = b == null ? 0 : b.playCount;
                    if (pa != pb) {
                        return Integer.compare(pb, pa);
                    }
                    String ta = a == null || a.title == null ? "" : a.title;
                    String tb = b == null || b.title == null ? "" : b.title;
                    return ta.compareToIgnoreCase(tb);
                }
            });
        }
        adapter.notifyDataSetChanged();
        textEmpty.setVisibility(cachedSongs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("清除播放记录")
                .setMessage("这会清空播放次数与播放历史，但不会删除已下载歌曲。")
                .setPositiveButton("清除", (dialog, which) -> {
                    apiClient.clearHistory();
                    loadCachedSongs();
                    Toast.makeText(this, "播放记录已清除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private final class OfflineAdapter extends RecyclerView.Adapter<OfflineAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_offline_song, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ApiClient.MediaItemModel item = cachedSongs.get(position);
            holder.textTitle.setText(item.title);
            holder.textSubtitle.setText(item.artist);
            holder.textPlayCount.setText("x" + Math.max(0, item.playCount));
            holder.imageCover.setImageResource(R.drawable.ic_music_note);
            loadCover(holder.imageCover, item);
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(OfflineCacheActivity.this, MainActivity.class);
                intent.putExtra("play_song_id", item.id);
                intent.putExtra("play_song_source", "offline");
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        @Override
        public int getItemCount() {
            return cachedSongs.size();
        }

        private void loadCover(ImageView imageView, ApiClient.MediaItemModel item) {
            File coverFile = !TextUtils.isEmpty(item.localCoverPath) ? new File(item.localCoverPath) : null;
            if (coverFile != null && coverFile.exists()) {
                Glide.with(OfflineCacheActivity.this).load(coverFile).into(imageView);
                return;
            }
            imageView.setImageResource(R.drawable.ic_music_note);
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView imageCover;
            final TextView textTitle;
            final TextView textSubtitle;
            final TextView textPlayCount;

            Holder(@NonNull View itemView) {
                super(itemView);
                imageCover = itemView.findViewById(R.id.imageCover);
                textTitle = itemView.findViewById(R.id.textTitle);
                textSubtitle = itemView.findViewById(R.id.textSubtitle);
                textPlayCount = itemView.findViewById(R.id.textPlayCount);
            }
        }
    }
}
