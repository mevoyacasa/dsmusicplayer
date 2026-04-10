package mevoycasa.dsmusic;

import android.os.Bundle;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CacheSongPickerActivity extends AppCompatActivity {

    private ApiClient apiClient;
    private RecyclerView recyclerView;
    private TextView textEmpty;
    private TextView textSubtitle;
    private TextView buttonSelectAllCache;
    private final List<ApiClient.MediaItemModel> cachedSongs = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private PickerAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cache_song_picker);
        apiClient = new ApiClient(this);
        recyclerView = findViewById(R.id.recyclerPicker);
        textEmpty = findViewById(R.id.textPickerEmpty);
        textSubtitle = findViewById(R.id.textPickerSubtitle);
        buttonSelectAllCache = findViewById(R.id.buttonSelectAllCache);
        adapter = new PickerAdapter();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setVerticalScrollBarEnabled(true);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.buttonPickerBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonDeleteSelected).setOnClickListener(v -> deleteSelected());
        buttonSelectAllCache.setOnClickListener(v -> toggleCacheSelectAll());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCachedSongs();
    }

    private void loadCachedSongs() {
        cachedSongs.clear();
        List<ApiClient.MediaItemModel> items = apiClient.readCachedSongs();
        for (ApiClient.MediaItemModel item : items) {
            if (item == null || !"song".equals(item.type)) {
                continue;
            }
            cachedSongs.add(item);
        }
        adapter.notifyDataSetChanged();
        textEmpty.setVisibility(cachedSongs.isEmpty() ? View.VISIBLE : View.GONE);
        updateSubtitle();
    }

    private void updateSubtitle() {
        textSubtitle.setText("Cached " + cachedSongs.size() + " · Selected " + selectedIds.size());
        if (buttonSelectAllCache != null) {
            buttonSelectAllCache.setText(selectedIds.size() == cachedSongs.size() && !cachedSongs.isEmpty() ? "取消全选" : "全选");
        }
    }

    private void deleteSelected() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "鏈€夋嫨姝屾洸", Toast.LENGTH_SHORT).show();
            return;
        }
        int deleted = 0;
        for (ApiClient.MediaItemModel item : new ArrayList<>(cachedSongs)) {
            if (item == null || TextUtils.isEmpty(item.id) || !selectedIds.contains(item.id)) {
                continue;
            }
            Uri uri = apiClient.getSongCacheReadUri(item);
            if (uri == null) {
                continue;
            }
            boolean removed = false;
            if ("file".equals(uri.getScheme())) {
                File file = new File(uri.getPath() == null ? "" : uri.getPath());
                removed = file.exists() && file.delete();
            } else {
                DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri);
                removed = documentFile != null && documentFile.delete();
            }
            if (removed) {
                deleted++;
            }
        }
        selectedIds.clear();
        loadCachedSongs();
        Toast.makeText(this, "Deleted " + deleted + " cached files", Toast.LENGTH_SHORT).show();
    }

    private void toggleCacheSelectAll() {
        if (cachedSongs.isEmpty()) {
            return;
        }
        if (selectedIds.size() == cachedSongs.size()) {
            selectedIds.clear();
        } else {
            selectedIds.clear();
            for (ApiClient.MediaItemModel item : cachedSongs) {
                if (item != null && !TextUtils.isEmpty(item.id)) {
                    selectedIds.add(item.id);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateSubtitle();
    }
    private final class PickerAdapter extends RecyclerView.Adapter<PickerAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_browser, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ApiClient.MediaItemModel item = cachedSongs.get(position);
            holder.textTitle.setText(item.title);
            holder.textSubtitle.setText(item.artist + "  " + item.album);
            holder.textMeta.setText("宸");
            holder.imageCached.setVisibility(View.VISIBLE);
            holder.imageAction.setVisibility(View.GONE);
            holder.imageCover.setImageResource(R.drawable.ic_music_note);
            loadCover(holder.imageCover, item);

            holder.checkSelect.setVisibility(View.VISIBLE);
            holder.checkSelect.setOnCheckedChangeListener(null);
            holder.checkSelect.setChecked(selectedIds.contains(item.id));
            holder.checkSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedIds.add(item.id);
                } else {
                    selectedIds.remove(item.id);
                }
                updateSubtitle();
            });
            holder.itemView.setOnClickListener(v -> holder.checkSelect.performClick());
        }

        @Override
        public int getItemCount() {
            return cachedSongs.size();
        }

        private void loadCover(ImageView imageView, ApiClient.MediaItemModel item) {
            if (TextUtils.isEmpty(item.localCoverPath)) {
                item.localCoverPath = apiClient.resolveLocalCoverPath("song", item.id);
            }
            File coverFile = !TextUtils.isEmpty(item.localCoverPath) ? new File(item.localCoverPath) : null;
            if (coverFile != null && coverFile.exists()) {
                Glide.with(CacheSongPickerActivity.this).load(coverFile).into(imageView);
                return;
            }
            imageView.setImageResource(R.drawable.ic_music_note);
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView imageCover;
            final ImageView imageCached;
            final ImageView imageAction;
            final TextView textTitle;
            final TextView textSubtitle;
            final TextView textMeta;
            final CheckBox checkSelect;

            Holder(@NonNull View itemView) {
                super(itemView);
                imageCover = itemView.findViewById(R.id.imageCover);
                imageCached = itemView.findViewById(R.id.imageCached);
                imageAction = itemView.findViewById(R.id.imageAction);
                textTitle = itemView.findViewById(R.id.textTitle);
                textSubtitle = itemView.findViewById(R.id.textSubtitle);
                textMeta = itemView.findViewById(R.id.textMeta);
                checkSelect = itemView.findViewById(R.id.checkSelect);
                // Make checkbox easier to see on this screen.
                checkSelect.setButtonTintList(ContextCompat.getColorStateList(CacheSongPickerActivity.this, R.color.seed));
            }
        }
    }
}
