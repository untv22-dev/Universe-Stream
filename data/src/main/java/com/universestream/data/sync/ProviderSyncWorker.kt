package com.universestream.data.sync

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.universestream.data.local.dao.ProviderDao
import com.universestream.data.local.dao.ChannelDao
import com.universestream.data.local.dao.CategoryDao
import com.universestream.data.local.dao.XtreamIndexJobDao
import com.universestream.data.local.dao.XtreamLiveOnboardingDao
import com.universestream.domain.model.ProviderStatus
import com.universestream.domain.model.ContentType
import com.universestream.domain.model.ProviderEpgSyncMode
import com.universestream.domain.model.ProviderType
import com.universestream.domain.model.SyncState
import com.universestream.domain.repository.SyncMetadataRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

internal suspend fun reconcileTargetedProviderStatus(
    providerDao: ProviderDao,
    channelDao: ChannelDao,
    categoryDao: CategoryDao,
    syncMetadataRepository: SyncMetadataRepository,
    syncManager: SyncManager,
    provider: com.universestream.data.local.entity.ProviderEntity,
    result: com.universestream.domain.model.Result<Unit>,
    currentTimeMillis: Long = System.currentTimeMillis()
) {
    when (result) {
        is com.universestream.domain.model.Result.Success -> {
            val finalStatus = if (syncManager.currentSyncState(provider.id) is SyncState.Partial) {
                ProviderStatus.PARTIAL
            } else {
                ProviderStatus.ACTIVE
            }
            val hasUsableLiveCatalog = hasUsableLiveCatalogForActivation(
                provider.id,
                provider.type,
                channelDao,
                categoryDao,
                syncMetadataRepository
            )
            // Xtream onboarding may complete authentication before the first catalog
            // batches are committed. Keep it active and partial so Home does not send
            // the user back to Add Provider while the automatic sync is retrying.
            if (!hasUsableLiveCatalog && provider.type != ProviderType.XTREAM_CODES) {
                providerDao.update(
                    provider.copy(
                        isActive = false,
                        status = ProviderStatus.PARTIAL,
                        lastSyncedAt = currentTimeMillis
                    )
                )
                return
            }
            providerDao.update(
                provider.copy(
                    isActive = true,
                    status = if (hasUsableLiveCatalog) finalStatus else ProviderStatus.PARTIAL,
                    lastSyncedAt = currentTimeMillis
                )
            )
        }
        is com.universestream.domain.model.Result.Error -> {
            if (provider.status != ProviderStatus.PARTIAL) {
                providerDao.update(provider.copy(isActive = false, status = ProviderStatus.ERROR))
            }
        }
        is com.universestream.domain.model.Result.Loading -> Unit
    }
}

internal suspend fun shouldTrackInitialLiveOnboarding(
    provider: com.universestream.data.local.entity.ProviderEntity,
    onboardingDao: XtreamLiveOnboardingDao
): Boolean = provider.type == ProviderType.XTREAM_CODES &&
    onboardingDao.getIncompleteByProvider(provider.id) != null

internal fun isFreshXtreamProvider(
    provider: com.universestream.data.local.entity.ProviderEntity
): Boolean = provider.type == ProviderType.XTREAM_CODES &&
    provider.status == ProviderStatus.PARTIAL &&
    provider.lastSyncedAt == 0L

class ProviderSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderSyncWorkerEntryPoint {
        fun providerDao(): ProviderDao
        fun channelDao(): ChannelDao
        fun categoryDao(): CategoryDao
        fun syncManager(): SyncManager
        fun syncMetadataRepository(): SyncMetadataRepository
        fun xtreamIndexJobDao(): XtreamIndexJobDao
        fun xtreamLiveOnboardingDao(): XtreamLiveOnboardingDao
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                ProviderSyncWorkerEntryPoint::class.java
            )
            val requestedProviderId = inputData.getLong(KEY_PROVIDER_ID, INVALID_PROVIDER_ID)
            val providers = if (requestedProviderId != INVALID_PROVIDER_ID) {
                entryPoint.providerDao().getById(requestedProviderId)?.let(::listOf).orEmpty()
            } else {
                entryPoint.providerDao().getAllSync()
            }
            if (providers.isEmpty()) {
                return Result.success()
            }

            // Reap zombie index jobs left RUNNING by a killed process. A zombie blocks the
            // mobile category delta check and keeps VOD screens in "sync pending" forever.
            // FAILED_RETRYABLE lets shouldRunIndexJob resume it on this pass.
            val reaped = entryPoint.xtreamIndexJobDao().reapZombieRunningJobs(
                staleThresholdMs = System.currentTimeMillis() - STALE_RUNNING_JOB_MILLIS,
                now = System.currentTimeMillis()
            )
            if (reaped > 0) {
                Log.i(TAG, "Reaped $reaped zombie RUNNING index job(s) for retry.")
            }

            val foregroundStalenessGateMs = inputData.getLong(KEY_MIN_STALENESS_MS, 0L)

            var sawRetryableFailure = false
            providers.forEach { provider ->
                val hasIncompleteLiveOnboarding = shouldTrackInitialLiveOnboarding(
                    provider = provider,
                    onboardingDao = entryPoint.xtreamLiveOnboardingDao()
                )
                val isFreshXtream = isFreshXtreamProvider(provider)
                val isTelevision = applicationContext.isTelevisionDeviceForSync()

                // Mobile foreground check gate: a quick app switch (open -> close -> open)
                // must not hit the network again while every catalog is still fresh.
                if (foregroundStalenessGateMs > 0L &&
                    requestedProviderId == INVALID_PROVIDER_ID &&
                    !hasIncompleteLiveOnboarding &&
                    !isFreshXtream &&
                    provider.isActive &&
                    provider.lastSyncedAt > 0L &&
                    System.currentTimeMillis() - provider.lastSyncedAt < foregroundStalenessGateMs
                ) {
                    Log.i(
                        "SyncDiag",
                        "provider=${provider.id} foreground-check skipped: synced ${System.currentTimeMillis() - provider.lastSyncedAt}ms ago (< ${foregroundStalenessGateMs}ms gate)"
                    )
                    return@forEach
                }

                val prioritizeInitialLiveOnboarding = !isTelevision &&
                    (hasIncompleteLiveOnboarding || isFreshXtream)
                // Preserve the existing TV tracking behavior. The fresh-provider fallback
                // is intentionally promoted to tracked onboarding only on non-TV devices.
                val trackInitialLiveOnboarding = hasIncompleteLiveOnboarding ||
                    prioritizeInitialLiveOnboarding
                Log.i(
                    "SyncDiag",
                    "provider=${provider.id} initialLive track=$trackInitialLiveOnboarding " +
                        "prioritize=$prioritizeInitialLiveOnboarding fresh=$isFreshXtream " +
                        "incomplete=$hasIncompleteLiveOnboarding tv=$isTelevision"
                )
                val result = if (requestedProviderId == provider.id) {
                    entryPoint.syncManager().sync(
                        provider.id,
                        force = false,
                        trackInitialLiveOnboarding = trackInitialLiveOnboarding,
                        prioritizeInitialLiveOnboarding = prioritizeInitialLiveOnboarding
                    )
                } else if (provider.type == ProviderType.XTREAM_CODES) {
                    syncXtreamProviderIfStale(
                        entryPoint = entryPoint,
                        provider = provider,
                        trackInitialLiveOnboarding = trackInitialLiveOnboarding,
                        prioritizeInitialLiveOnboarding = prioritizeInitialLiveOnboarding
                    )
                } else if (provider.type == ProviderType.STALKER_PORTAL) {
                    syncStalkerProviderIfStale(entryPoint, provider)
                } else {
                    entryPoint.syncManager().sync(provider.id, force = false)
                }
                if (requestedProviderId == provider.id) {
                    reconcileTargetedProviderStatus(entryPoint, provider, result)
                }
                when (result) {
                    is com.universestream.domain.model.Result.Error -> {
                        Log.w(TAG, "Provider sync worker failed for provider ${provider.id}: ${result.message}")
                        if (shouldRetry(result.exception)) {
                            sawRetryableFailure = true
                        }
                    }
                    else -> Unit
                }
            }

            if (sawRetryableFailure) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Provider sync worker failed", e)
            if (shouldRetry(e)) Result.retry() else Result.failure()
        }
    }

    private fun shouldRetry(error: Throwable?): Boolean {
        return when (error) {
            is java.io.IOException -> true
            is SQLiteException -> error.message.orEmpty().contains("locked", ignoreCase = true) ||
                error.message.orEmpty().contains("busy", ignoreCase = true)
            else -> false
        }
    }

    companion object {
        private const val TAG = "ProviderSyncWorker"
        private const val STALE_RUNNING_JOB_MILLIS = 15 * 60 * 1000L
        private const val UNIQUE_WORK_NAME = "provider-sync-worker"
        private const val UNIQUE_LAUNCH_STALE_WORK_NAME = "provider-sync-launch-stale-check"
        private const val UNIQUE_MOBILE_LIGHTWEIGHT_WORK_NAME = "provider-sync-mobile-lightweight-check"
        private const val UNIQUE_PROVIDER_WORK_PREFIX = "provider-sync-provider-"
        private const val KEY_PROVIDER_ID = "provider_id"
        private const val KEY_MIN_STALENESS_MS = "min_staleness_ms"
        private const val INVALID_PROVIDER_ID = -1L

        /**
         * App-open checks remain automatic, but repeated foreground transitions within
         * this window do not wake the network again. This keeps the refresh responsive
         * without turning rapid app switching into a battery drain. The TV path is
         * unchanged because this value is only passed by the mobile lightweight worker.
         */
        private const val MOBILE_FOREGROUND_STALENESS_MS = 15 * 60 * 1000L

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProviderSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueLaunchStaleCheck(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProviderSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(10, TimeUnit.SECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_LAUNCH_STALE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Schedules only the mobile app-open category check. The existing launch stale check
         * remains KEEP so the TV/default path and its timing are unchanged.
         */
        fun enqueueMobileLightweightCheck(context: Context) {
            if (context.isTelevisionDeviceForSync()) return

            val request = OneTimeWorkRequestBuilder<ProviderSyncWorker>()
                .setInputData(
                    workDataOf(KEY_MIN_STALENESS_MS to MOBILE_FOREGROUND_STALENESS_MS)
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setInitialDelay(5, TimeUnit.SECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_MOBILE_LIGHTWEIGHT_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueProvider(context: Context, providerId: Long) {
            val request = OneTimeWorkRequestBuilder<ProviderSyncWorker>()
                .setInputData(workDataOf(KEY_PROVIDER_ID to providerId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_PROVIDER_WORK_PREFIX + providerId,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    private suspend fun syncXtreamProviderIfStale(
        entryPoint: ProviderSyncWorkerEntryPoint,
        provider: com.universestream.data.local.entity.ProviderEntity,
        trackInitialLiveOnboarding: Boolean = false,
        prioritizeInitialLiveOnboarding: Boolean = false
    ): com.universestream.domain.model.Result<Unit> {
        val now = System.currentTimeMillis()
        if (trackInitialLiveOnboarding) {
            return entryPoint.syncManager().sync(
                provider.id,
                force = false,
                trackInitialLiveOnboarding = true,
                prioritizeInitialLiveOnboarding = prioritizeInitialLiveOnboarding
            )
        }
        if (!applicationContext.isTelevisionDeviceForSync() && provider.isActive) {
            runCatching {
                entryPoint.syncManager().checkMobileXtreamCategoryDelta(provider.id)
            }.onFailure { error ->
                Log.w(
                    "SyncDiag",
                    "Mobile category delta check failed provider=${provider.id}: ${error.message}"
                )
            }
        }
        val metadata = entryPoint.syncMetadataRepository().getMetadata(provider.id)
        val liveStale = ContentCachePolicy.shouldRefresh(
            metadata?.lastLiveSuccess ?: 0L,
            ContentCachePolicy.CATALOG_TTL_MILLIS,
            now
        )
        val epgStale = provider.epgSyncMode != ProviderEpgSyncMode.SKIP &&
            ContentCachePolicy.shouldRefresh(
                metadata?.lastEpgSuccess ?: 0L,
                ContentCachePolicy.EPG_TTL_MILLIS,
                now
            )
        val movieIndexDue = shouldRunIndexJob(entryPoint, provider.id, ContentType.MOVIE, now)
        val seriesIndexDue = shouldRunIndexJob(entryPoint, provider.id, ContentType.SERIES, now)

        if (!provider.isActive) {
            return com.universestream.domain.model.Result.success(Unit)
        }

        if (liveStale) {
            when (val liveResult = entryPoint.syncManager().retrySection(
                provider.id,
                SyncRepairSection.LIVE,
                syncReason = XtreamLiveSyncReason.BACKGROUND_STALE
            )) {
                is com.universestream.domain.model.Result.Error -> return liveResult
                else -> Unit
            }
        }
        if (epgStale) {
            when (val epgResult = entryPoint.syncManager().syncEpg(provider.id, force = false)) {
                is com.universestream.domain.model.Result.Error -> return epgResult
                else -> Unit
            }
        }
        if (movieIndexDue) {
            entryPoint.syncManager().scheduleXtreamIndexSync(provider.id, ContentType.MOVIE)
        }
        if (seriesIndexDue) {
            entryPoint.syncManager().scheduleXtreamIndexSync(provider.id, ContentType.SERIES)
        }
        return com.universestream.domain.model.Result.success(Unit)
    }

    private suspend fun shouldRunIndexJob(
        entryPoint: ProviderSyncWorkerEntryPoint,
        providerId: Long,
        section: ContentType,
        now: Long
    ): Boolean {
        val job = entryPoint.xtreamIndexJobDao().get(providerId, section.name) ?: return true
        if (job.state in setOf("QUEUED", "PARTIAL", "STALE", "FAILED_RETRYABLE")) return true
        if (job.state == "RUNNING" && (now - job.updatedAt) < STALE_RUNNING_JOB_MILLIS) return false
        return ContentCachePolicy.shouldRefresh(job.lastSuccessAt, ContentCachePolicy.CATALOG_TTL_MILLIS, now)
    }

    private suspend fun syncStalkerProviderIfStale(
        entryPoint: ProviderSyncWorkerEntryPoint,
        provider: com.universestream.data.local.entity.ProviderEntity
    ): com.universestream.domain.model.Result<Unit> {
        val now = System.currentTimeMillis()
        val metadata = entryPoint.syncMetadataRepository().getMetadata(provider.id)
        val liveStale = ContentCachePolicy.shouldRefresh(
            metadata?.lastLiveSuccess ?: 0L,
            ContentCachePolicy.CATALOG_TTL_MILLIS,
            now
        )
        val epgStale = provider.epgSyncMode != ProviderEpgSyncMode.SKIP &&
            ContentCachePolicy.shouldRefresh(
                metadata?.lastEpgSuccess ?: 0L,
                ContentCachePolicy.EPG_TTL_MILLIS,
                now
            )
        val movieIndexDue = shouldRunIndexJob(entryPoint, provider.id, ContentType.MOVIE, now)
        val seriesIndexDue = shouldRunIndexJob(entryPoint, provider.id, ContentType.SERIES, now)

        if (!provider.isActive) {
            return com.universestream.domain.model.Result.success(Unit)
        }

        if (liveStale) {
            when (val liveResult = entryPoint.syncManager().retrySection(provider.id, SyncRepairSection.LIVE)) {
                is com.universestream.domain.model.Result.Error -> return liveResult
                else -> Unit
            }
        }
        if (movieIndexDue) {
            entryPoint.syncManager().scheduleStalkerIndexSync(provider.id, ContentType.MOVIE)
        }
        if (seriesIndexDue) {
            entryPoint.syncManager().scheduleStalkerIndexSync(provider.id, ContentType.SERIES)
        }
        if (epgStale) {
            entryPoint.syncManager().scheduleBackgroundEpgSync(provider.id)
        }
        return com.universestream.domain.model.Result.success(Unit)
    }

    private suspend fun reconcileTargetedProviderStatus(
        entryPoint: ProviderSyncWorkerEntryPoint,
        provider: com.universestream.data.local.entity.ProviderEntity,
        result: com.universestream.domain.model.Result<Unit>
    ) {
        reconcileTargetedProviderStatus(
            providerDao = entryPoint.providerDao(),
            channelDao = entryPoint.channelDao(),
            categoryDao = entryPoint.categoryDao(),
            syncMetadataRepository = entryPoint.syncMetadataRepository(),
            syncManager = entryPoint.syncManager(),
            provider = provider,
            result = result
        )
    }
}
