package mevoycasa.dsmusic;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

final class CacheCleanupScheduler {

    private static final String WORK_NAME = "cache_cleanup";

    private CacheCleanupScheduler() {
    }

    static void schedule(Context context, int intervalHours) {
        if (context == null) {
            return;
        }
        if (intervalHours <= 0) {
            cancel(context);
            return;
        }
        // WorkManager minimum interval is 15 minutes. We clamp to 1 hour for stability.
        int safeHours = Math.max(1, intervalHours);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(CacheCleanupWorker.class, safeHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    static void cancel(Context context) {
        if (context == null) {
            return;
        }
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
}

