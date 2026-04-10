package mevoycasa.dsmusic;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class CacheCleanupWorker extends Worker {

    public CacheCleanupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        ApiClient apiClient = new ApiClient(getApplicationContext());
        boolean daysEnabled = apiClient.prefs().getBoolean(ApiClient.KEY_CACHE_RULE_DAYS_ENABLED, false);
        boolean sizeEnabled = apiClient.prefs().getBoolean(ApiClient.KEY_CACHE_RULE_SIZE_ENABLED, false);
        boolean scheduleEnabled;
        if (apiClient.prefs().contains(ApiClient.KEY_CACHE_SCHEDULE_ENABLED)) {
            scheduleEnabled = apiClient.prefs().getBoolean(ApiClient.KEY_CACHE_SCHEDULE_ENABLED, false);
        } else {
            scheduleEnabled = apiClient.prefs().getBoolean(ApiClient.KEY_CACHE_AUTO_CLEAN, false);
        }
        if (!scheduleEnabled || (!daysEnabled && !sizeEnabled)) {
            return Result.success();
        }
        int days = apiClient.prefs().getInt(ApiClient.KEY_CACHE_CLEAN_DAYS, 30);
        int maxMb = apiClient.prefs().getInt(ApiClient.KEY_CACHE_MAX_MB, 1024);
        if (daysEnabled && days > 0) {
            apiClient.clearResourceCacheNotPlayedSinceDays(days);
        }
        if (sizeEnabled && maxMb > 0) {
            apiClient.clearResourceCacheUntilUnderMb(maxMb);
        }
        return Result.success();
    }
}
