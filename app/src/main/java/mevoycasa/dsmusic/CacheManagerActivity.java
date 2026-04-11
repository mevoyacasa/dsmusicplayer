package mevoycasa.dsmusic;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

public class CacheManagerActivity extends AppCompatActivity {

    private ApiClient apiClient;
    private TextView textCacheSummary;
    private MaterialCheckBox checkRuleDays;
    private MaterialCheckBox checkRuleSize;
    private SwitchMaterial switchScheduleClean;
    private EditText editCleanDays;
    private EditText editMaxMb;
    private EditText editIntervalHours;
    private TextView buttonRunClean;
    private TextView buttonCustomClean;
    private boolean updatingUi;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cache_manager);

        apiClient = new ApiClient(this);
        bindViews();
        bindListeners();
        loadPrefs();
        loadSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSummary();
    }

    private void bindViews() {
        TextView buttonBack = findViewById(R.id.buttonCacheBack);
        buttonBack.setOnClickListener(v -> finish());

        textCacheSummary = findViewById(R.id.textCacheSummary);

        checkRuleDays = findViewById(R.id.checkRuleDays);
        checkRuleSize = findViewById(R.id.checkRuleSize);
        switchScheduleClean = findViewById(R.id.switchScheduleClean);
        editCleanDays = findViewById(R.id.editCleanDays);
        editMaxMb = findViewById(R.id.editMaxMb);
        editIntervalHours = findViewById(R.id.editIntervalHours);
        buttonRunClean = findViewById(R.id.buttonRunClean);
        buttonCustomClean = findViewById(R.id.buttonCustomClean);
    }

    private void bindListeners() {
        if (checkRuleDays != null) {
            checkRuleDays.setOnCheckedChangeListener((buttonView, isChecked) -> savePrefs());
        }
        if (checkRuleSize != null) {
            checkRuleSize.setOnCheckedChangeListener((buttonView, isChecked) -> savePrefs());
        }
        if (switchScheduleClean != null) {
            switchScheduleClean.setOnCheckedChangeListener((buttonView, isChecked) -> savePrefs());
        }
        addSavingWatcher(editCleanDays);
        addSavingWatcher(editMaxMb);
        addSavingWatcher(editIntervalHours);
        if (buttonRunClean != null) {
            buttonRunClean.setOnClickListener(v -> runCleanNow());
        }
        if (buttonCustomClean != null) {
            buttonCustomClean.setOnClickListener(v -> showCustomCleanDialog());
        }
    }

    private void addSavingWatcher(EditText editText) {
        if (editText == null) {
            return;
        }
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!updatingUi) {
                    savePrefs();
                }
            }
        });
    }

    private void loadPrefs() {
        updatingUi = true;
        SharedPreferences prefs = apiClient.prefs();
        boolean hasNewRulePrefs = prefs.contains(ApiClient.KEY_CACHE_RULE_DAYS_ENABLED)
                || prefs.contains(ApiClient.KEY_CACHE_RULE_SIZE_ENABLED);
        boolean legacyRulesEnabled = prefs.getBoolean(ApiClient.KEY_CACHE_AUTO_CLEAN, false);

        boolean daysEnabled = prefs.getBoolean(ApiClient.KEY_CACHE_RULE_DAYS_ENABLED, hasNewRulePrefs ? false : legacyRulesEnabled);
        boolean sizeEnabled = prefs.getBoolean(ApiClient.KEY_CACHE_RULE_SIZE_ENABLED, hasNewRulePrefs ? false : legacyRulesEnabled);
        boolean scheduleEnabled = prefs.contains(ApiClient.KEY_CACHE_SCHEDULE_ENABLED)
                ? prefs.getBoolean(ApiClient.KEY_CACHE_SCHEDULE_ENABLED, false)
                : legacyRulesEnabled;

        int days = prefs.getInt(ApiClient.KEY_CACHE_CLEAN_DAYS, 30);
        int maxMb = prefs.getInt(ApiClient.KEY_CACHE_MAX_MB, 1024);
        int intervalHours = prefs.getInt(ApiClient.KEY_CACHE_INTERVAL_HOURS, 12);

        if (!prefs.contains(ApiClient.KEY_CACHE_RULE_DAYS_ENABLED) && !prefs.contains(ApiClient.KEY_CACHE_RULE_SIZE_ENABLED)) {
            prefs.edit()
                    .putBoolean(ApiClient.KEY_CACHE_RULE_DAYS_ENABLED, daysEnabled)
                    .putBoolean(ApiClient.KEY_CACHE_RULE_SIZE_ENABLED, sizeEnabled)
                    .apply();
        }

        if (checkRuleDays != null) {
            checkRuleDays.setChecked(daysEnabled);
        }
        if (checkRuleSize != null) {
            checkRuleSize.setChecked(sizeEnabled);
        }
        if (switchScheduleClean != null) {
            switchScheduleClean.setChecked(scheduleEnabled);
        }
        if (editCleanDays != null) {
            editCleanDays.setText(String.valueOf(Math.max(days, 1)));
        }
        if (editMaxMb != null) {
            editMaxMb.setText(String.valueOf(Math.max(maxMb, 1)));
        }
        if (editIntervalHours != null) {
            editIntervalHours.setText(String.valueOf(Math.max(intervalHours, 0)));
        }
        updatingUi = false;
    }

    private void savePrefs() {
        if (updatingUi) {
            return;
        }
        boolean daysEnabled = checkRuleDays != null && checkRuleDays.isChecked();
        boolean sizeEnabled = checkRuleSize != null && checkRuleSize.isChecked();
        boolean scheduleEnabled = switchScheduleClean != null && switchScheduleClean.isChecked();
        int days = parseInt(editCleanDays, 30, 1, 3650);
        int maxMb = parseInt(editMaxMb, 1024, 1, 1024 * 50);
        int intervalHours = parseInt(editIntervalHours, 12, 1, 24 * 30);

        apiClient.prefs().edit()
                .putBoolean(ApiClient.KEY_CACHE_AUTO_CLEAN, daysEnabled || sizeEnabled)
                .putBoolean(ApiClient.KEY_CACHE_RULE_DAYS_ENABLED, daysEnabled)
                .putBoolean(ApiClient.KEY_CACHE_RULE_SIZE_ENABLED, sizeEnabled)
                .putBoolean(ApiClient.KEY_CACHE_SCHEDULE_ENABLED, scheduleEnabled)
                .putInt(ApiClient.KEY_CACHE_CLEAN_DAYS, days)
                .putInt(ApiClient.KEY_CACHE_MAX_MB, maxMb)
                .putInt(ApiClient.KEY_CACHE_INTERVAL_HOURS, intervalHours)
                .apply();

        if (scheduleEnabled && (daysEnabled || sizeEnabled)) {
            CacheCleanupScheduler.schedule(this, intervalHours);
        } else {
            CacheCleanupScheduler.cancel(this);
        }
    }

    private int parseInt(@Nullable EditText editText, int fallback, int min, int max) {
        String raw = editText == null || editText.getText() == null ? "" : editText.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw);
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void runCleanNow() {
        savePrefs();
        boolean daysEnabled = checkRuleDays != null && checkRuleDays.isChecked();
        boolean sizeEnabled = checkRuleSize != null && checkRuleSize.isChecked();
        if (!daysEnabled && !sizeEnabled) {
            Toast.makeText(this, getString(R.string.please_select_cleanup_rule_first), Toast.LENGTH_SHORT).show();
            return;
        }

        int deleted = 0;
        if (daysEnabled) {
            deleted += apiClient.clearResourceCacheNotPlayedSinceDays(parseInt(editCleanDays, 30, 1, 3650));
        }
        if (sizeEnabled) {
            deleted += apiClient.clearResourceCacheUntilUnderMb(parseInt(editMaxMb, 1024, 1, 1024 * 50));
        }
        long sizeBytes = apiClient.getResourceCacheSizeBytes();
        String sizeLabel = String.format(Locale.getDefault(), "%.1fMB", sizeBytes / 1024f / 1024f);
        Toast.makeText(this, getString(R.string.cleaned_count_cache, deleted, sizeLabel), Toast.LENGTH_SHORT).show();
    }

    private void showCustomCleanDialog() {
        final boolean[] selected = new boolean[]{true, true, true};
        final CharSequence[] items = new CharSequence[]{getString(R.string.cover_cache), getString(R.string.lyrics_cache), getString(R.string.other_cache)};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.custom_cleanup))
                .setMultiChoiceItems(items, selected, (dialog, which, isChecked) -> selected[which] = isChecked)
                .setMessage(getString(R.string.cleanup_description))
                .setPositiveButton(getString(R.string.clean), (dialog, which) -> {
                    int deleted = apiClient.clearSelectedResourceCache(selected[0], selected[1], selected[2]);
                    Toast.makeText(this, getString(R.string.cleaned_count_only, deleted), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void loadSummary() {
        if (textCacheSummary == null) {
            return;
        }
        ApiClient.ResourceCacheStats stats = apiClient.getResourceCacheStats();
        textCacheSummary.setText(String.format(
                Locale.getDefault(),
                getString(R.string.current_cache_info),
                stats.typeCount(),
                stats.totalCount,
                formatSize(stats.totalBytes),
                formatSize(stats.coverBytes),
                formatSize(stats.lyricsBytes),
                formatSize(stats.otherBytes)
        ));
    }

    private String formatSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + "B";
        }
        float kb = bytes / 1024f;
        if (kb < 1024f) {
            return String.format(Locale.getDefault(), "%.1fKB", kb);
        }
        float mb = kb / 1024f;
        if (mb < 1024f) {
            return String.format(Locale.getDefault(), "%.1fMB", mb);
        }
        float gb = mb / 1024f;
        return String.format(Locale.getDefault(), "%.1fGB", gb);
    }
}
