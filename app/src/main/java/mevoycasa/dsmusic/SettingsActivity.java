package mevoycasa.dsmusic;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private ApiClient apiClient;
    private PlaylistDialogs playlistDialogs;
    private TextView buttonSettingsBack;
    private TextView buttonPickCacheLocation;
    private TextView buttonLyricOffset;
    private TextView buttonAudioExclusive;
    private TextView buttonAudioShared;
    private TextView buttonLanguageChinese;
    private TextView buttonLanguageEnglish;
    private ActivityResultLauncher<Intent> pickCacheLocationLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 设置应用语言
        String language = new ApiClient(this).getAppLanguage();
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration();
        config.locale = locale;
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        apiClient = new ApiClient(this);
        playlistDialogs = new PlaylistDialogs(this, apiClient);

        buttonSettingsBack = findViewById(R.id.buttonSettingsBack);
        buttonPickCacheLocation = findViewById(R.id.buttonPickCacheLocation);
        buttonLyricOffset = findViewById(R.id.buttonLyricOffset);
        TextView buttonSwitchAccount = findViewById(R.id.buttonSwitchAccount);
        TextView buttonPlaylistManager = findViewById(R.id.buttonPlaylistManager);
        TextView buttonCacheManager = findViewById(R.id.buttonCacheManager);
        buttonAudioExclusive = findViewById(R.id.buttonAudioExclusive);
        buttonAudioShared = findViewById(R.id.buttonAudioShared);
        buttonLanguageChinese = findViewById(R.id.buttonLanguageChinese);
        buttonLanguageEnglish = findViewById(R.id.buttonLanguageEnglish);

        pickCacheLocationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handlePickCacheLocationResult(result.getResultCode(), result.getData())
        );

        buttonSettingsBack.setOnClickListener(v -> finish());
        buttonPickCacheLocation.setOnClickListener(v -> launchPickCacheLocation());
        buttonLyricOffset.setOnClickListener(v -> showLyricOffsetDialog());
        buttonSwitchAccount.setOnClickListener(v -> showAccountSwitchDialog());
        buttonPlaylistManager.setOnClickListener(v -> playlistDialogs.showPlaylistManager());
        buttonCacheManager.setOnClickListener(v -> startActivity(new Intent(this, CacheManagerActivity.class)));
        buttonAudioExclusive.setOnClickListener(v -> setConcurrentPlayback(false));
        buttonAudioShared.setOnClickListener(v -> setConcurrentPlayback(true));
        buttonLanguageChinese.setOnClickListener(v -> setLanguage("zh"));
        buttonLanguageEnglish.setOnClickListener(v -> setLanguage("en"));

        refreshCacheLocationButton();
        refreshLyricOffsetButton();
        refreshAudioButtons();
        refreshLanguageButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCacheLocationButton();
        refreshLyricOffsetButton();
        refreshAudioButtons();
        refreshLanguageButtons();
    }

    private void setLanguage(String language) {
        apiClient.setAppLanguage(language);
        refreshLanguageButtons();
        toast(language.equals("zh") ? getString(R.string.switched_to_chinese) : getString(R.string.switched_to_english));
        restartApp();
    }

    private void refreshLanguageButtons() {
        String currentLanguage = apiClient.getAppLanguage();
        applyLanguageButton(buttonLanguageChinese, "zh".equals(currentLanguage));
        applyLanguageButton(buttonLanguageEnglish, "en".equals(currentLanguage));
    }

    private void applyLanguageButton(@Nullable TextView button, boolean active) {
        if (button == null) {
            return;
        }
        button.setBackgroundResource(active ? R.drawable.bg_tab_selected_pill : R.drawable.bg_chip_soft);
        button.setTextColor(getColor(active ? R.color.white : R.color.subtle));
    }

    private void restartApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void refreshLyricOffsetButton() {
        if (buttonLyricOffset == null) {
            return;
        }
        long offsetMs = apiClient.getLyricOffsetMs();
        buttonLyricOffset.setText(formatLyricOffsetLabel(offsetMs));
        if (offsetMs == 0L) {
            buttonLyricOffset.setBackgroundResource(R.drawable.bg_chip_soft);
            buttonLyricOffset.setTextColor(getColor(R.color.subtle));
        } else {
            buttonLyricOffset.setBackgroundResource(R.drawable.bg_tab_selected_pill);
            buttonLyricOffset.setTextColor(getColor(R.color.white));
        }
    }

    private String formatLyricOffsetLabel(long offsetMs) {
        if (offsetMs == 0L) {
            return getString(R.string.standard);
        }
        String value = String.format(Locale.getDefault(), "%.1f", Math.abs(offsetMs) / 1000f);
        return offsetMs > 0L ? getString(R.string.advance) + " " + value + "s" : getString(R.string.delay) + " " + value + "s";
    }

    private void refreshCacheLocationButton() {
        if (buttonPickCacheLocation == null) {
            return;
        }
        if (apiClient.hasCustomSongCacheDirectory()) {
            buttonPickCacheLocation.setText(apiClient.getSongCacheDirectoryLabel());
            buttonPickCacheLocation.setBackgroundResource(R.drawable.bg_tab_selected_pill);
            buttonPickCacheLocation.setTextColor(getColor(R.color.white));
        } else {
            buttonPickCacheLocation.setText(getString(R.string.internal));
            buttonPickCacheLocation.setBackgroundResource(R.drawable.bg_chip_soft);
            buttonPickCacheLocation.setTextColor(getColor(R.color.subtle));
        }
    }

    private void refreshAudioButtons() {
        boolean allowShared = apiClient.isConcurrentPlaybackAllowed();
        applyAudioButton(buttonAudioExclusive, !allowShared);
        applyAudioButton(buttonAudioShared, allowShared);
    }

    private void applyAudioButton(@Nullable TextView button, boolean active) {
        if (button == null) {
            return;
        }
        button.setBackgroundResource(active ? R.drawable.bg_tab_selected_pill : R.drawable.bg_chip_soft);
        button.setTextColor(getColor(active ? R.color.white : R.color.subtle));
    }

    private void setConcurrentPlayback(boolean allowShared) {
        apiClient.setConcurrentPlaybackAllowed(allowShared);
        PlaybackService.updateAudioFocusPolicy(this);
        refreshAudioButtons();
        toast(allowShared ? getString(R.string.switched_to_shared_playback) : getString(R.string.switched_to_exclusive_playback));
    }

    private void showLyricOffsetDialog() {
        final long[] values = new long[]{-500L, -300L, 0L, 300L, 500L};
        final String[] labels = new String[]{
                getString(R.string.delay_0_5s),
                getString(R.string.delay_0_3s),
                getString(R.string.standard),
                getString(R.string.advance_0_3s),
                getString(R.string.advance_0_5s)
        };
        long current = apiClient.getLyricOffsetMs();
        int checked = 2;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.lyric_offset_dialog_title))
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    long offset = values[which];
                    apiClient.setLyricOffsetMs(offset);
                    refreshLyricOffsetButton();
                    toast(offset == 0L ? getString(R.string.lyric_kept_standard) : getString(R.string.lyrics_have_been) + formatLyricOffsetLabel(offset));
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.button_cancel), null)
                .show();
    }

    private void launchPickCacheLocation() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        pickCacheLocationLauncher.launch(intent);
    }

    private void handlePickCacheLocationResult(int resultCode, @Nullable Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Uri treeUri = data.getData();
        if (treeUri == null) {
            return;
        }
        try {
            int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
        } catch (SecurityException ignored) {
        }
        apiClient.setSongCacheTreeUri(treeUri);
        refreshCacheLocationButton();
        toast(getString(R.string.cache_directory_set));
    }

    private void showAccountSwitchDialog() {
        List<ApiClient.AccountProfile> profiles = apiClient.readAccountProfiles();
        if (profiles.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            toast(getString(R.string.no_saved_accounts));
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_account_switch, null, false);
        TextView subtitle = view.findViewById(R.id.textAccountSwitchSubtitle);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerAccountProfiles);
        TextView buttonLogout = view.findViewById(R.id.buttonAccountSwitchLogout);
        TextView buttonAdd = view.findViewById(R.id.buttonAccountSwitchAdd);
        TextView buttonClose = view.findViewById(R.id.buttonAccountSwitchClose);

        subtitle.setText(getString(R.string.select_account_to_switch_quickly));
        buttonAdd.setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new SavedAccountAdapter(profiles, profile -> {
            dialog.dismiss();
            switchAccountProfile(profile);
        }));
        buttonLogout.setOnClickListener(v -> {
            dialog.dismiss();
            logoutCurrentSession();
        });
        buttonClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void logoutCurrentSession() {
        apiClient.prefs().edit()
                .remove(ApiClient.KEY_SID)
                .apply();
        PlaybackService.stopSession(this);
        apiClient.cancelSongCache();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_SKIP_AUTO_LOGIN, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        toast(getString(R.string.logout_successful));
    }

    private void switchAccountProfile(ApiClient.AccountProfile profile) {
        if (profile == null || TextUtils.isEmpty(profile.account) || TextUtils.isEmpty(profile.host)) {
            toast(getString(R.string.account_info_incomplete));
            return;
        }
        String normalizedHost = apiClient.normalizeHost(profile.host, profile.forceHttps);
        apiClient.prefs().edit()
                .putString(ApiClient.KEY_ACCOUNT, profile.account)
                .putString(ApiClient.KEY_PASSWORD, profile.password == null ? "" : profile.password)
                .putBoolean(ApiClient.KEY_FORCE_HTTPS, profile.forceHttps)
                .putBoolean(ApiClient.KEY_IGNORE_CERT, profile.ignoreCert)
                .putBoolean(ApiClient.KEY_REMEMBER_ACCOUNT, true)
                .putBoolean(ApiClient.KEY_REMEMBER_PASSWORD, !TextUtils.isEmpty(profile.password))
                .apply();

        PlaybackService.stopSession(this);
        apiClient.cancelSongCache();
        toast(getString(R.string.switching_account) + profile.account);
        apiClient.login(normalizedHost, profile.account, profile.password == null ? "" : profile.password, profile.forceHttps, profile.ignoreCert, new ApiClient.JsonCallback() {
            @Override
            public void onSuccess(org.json.JSONObject json) {
                toast(getString(R.string.switched_to) + profile.account);
                Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String message) {
                toast(message);
                startActivity(new Intent(SettingsActivity.this, LoginActivity.class));
            }
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final class SavedAccountAdapter extends RecyclerView.Adapter<SavedAccountAdapter.Holder> {
        private final List<ApiClient.AccountProfile> profiles;
        private final SavedAccountHandler handler;

        SavedAccountAdapter(List<ApiClient.AccountProfile> profiles, SavedAccountHandler handler) {
            this.profiles = profiles == null ? new ArrayList<>() : profiles;
            this.handler = handler;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
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
            meta.append(" · ");
            meta.append(profile.ignoreCert ? getString(R.string.ignore_certificate) : getString(R.string.verify_certificate));
            if (!TextUtils.isEmpty(profile.password)) {
                meta.append(" · " + getString(R.string.remember_password));
            }
            holder.textMeta.setText(meta.toString());
            holder.imageCurrent.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> {
                if (handler != null) {
                    handler.onPick(profile);
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
            final android.widget.ImageView imageAvatar;
            final android.widget.ImageView imageCurrent;

            Holder(View itemView) {
                super(itemView);
                textTitle = itemView.findViewById(R.id.textAccountTitle);
                textSubtitle = itemView.findViewById(R.id.textAccountSubtitle);
                textMeta = itemView.findViewById(R.id.textAccountMeta);
                imageAvatar = itemView.findViewById(R.id.imageAccountAvatar);
                imageCurrent = itemView.findViewById(R.id.imageAccountCurrent);
            }
        }
    }

    private interface SavedAccountHandler {
        void onPick(ApiClient.AccountProfile profile);
    }
}
