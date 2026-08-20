package com.universestream.data.sync

import android.util.Log
import com.universestream.data.mapper.toEntity
import com.universestream.data.remote.dto.XtreamCategory
import com.universestream.data.remote.dto.XtreamLiveStreamRow
import com.universestream.data.remote.dto.XtreamStream
import com.universestream.data.remote.xtream.OkHttpXtreamApiService
import com.universestream.data.remote.xtream.XtreamApiService
import com.universestream.data.remote.xtream.XtreamProvider
import com.universestream.data.remote.xtream.XtreamUrlFactory
import com.universestream.domain.model.Channel
import com.universestream.domain.model.ContentType
import com.universestream.domain.model.Provider
import com.universestream.domain.model.SyncMetadata
import com.universestream.domain.sync.Section
import com.universestream.domain.sync.SyncProgress
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import kotlin.system.measureTimeMillis

private const val XTREAM_LIVE_STRATEGY_TAG = "SyncManager"

private enum class XtreamLiveDecodeMode {
    THIN_WITH_LEGACY_FALLBACK,
    LEGACY_ONLY
}

internal data class LiveCategoryMapping(
    val metadataIds: Set<Long> = emptySet(),
    val idByNormalizedName: Map<String, Long> = emptyMap(),
    val nameById: Map<Long, String> = emptyMap()
) {
    companion object {
        val EMPTY = LiveCategoryMapping()
    }
}

private data class LiveCategoryRemapStats(
    val observedChannelIds: MutableSet<Long> = linkedSetOf(),
    var nameRemappedCount: Int = 0,
    var unresolvedNameCount: Int = 0
)

internal class LowMemoryCatalogAbortException(message: String) : IllegalStateException(message)

private fun normalizeLiveCategoryName(value: String?): String? {
    val normalized = value
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotBlank() }
    return normalized
}

private fun buildLiveCategoryMapping(categories: List<XtreamCategory>?): LiveCategoryMapping {
    val metadata = categories.orEmpty().mapNotNull { category ->
        val id = category.categoryId.trim().toLongOrNull() ?: return@mapNotNull null
        id to normalizeLiveCategoryName(category.categoryName)
    }
    val names = metadata
        .mapNotNull { (id, name) -> name?.let { it to id } }
        .groupBy({ it.first }, { it.second })
        .filterValues { ids -> ids.distinct().size == 1 }
        .mapValues { (_, ids) -> ids.first() }
    return LiveCategoryMapping(
        metadataIds = metadata.map { it.first }.toSet(),
        idByNormalizedName = names,
        nameById = metadata.mapNotNull { (id, name) -> name?.let { id to it } }.toMap()
    )
}

private fun remapLiveChannels(
    channels: List<Channel>,
    mapping: LiveCategoryMapping,
    stats: LiveCategoryRemapStats
): List<Channel> {
    return channels.map { channel ->
        val rawId = channel.categoryId
        rawId?.let(stats.observedChannelIds::add)
        if (rawId != null && rawId in mapping.metadataIds) {
            return@map channel
        }
        val mappedId = mapping.idByNormalizedName[normalizeLiveCategoryName(channel.categoryName)]
        if (mappedId != null) {
            stats.nameRemappedCount++
            channel.copy(
                categoryId = mappedId,
                categoryName = mapping.nameById[mappedId] ?: channel.categoryName
            )
        } else {
            stats.unresolvedNameCount += if (rawId != null) 1 else 0
            channel
        }
    }
}

private fun logLiveCategoryMapping(
    providerId: Long,
    source: String,
    mapping: LiveCategoryMapping,
    stats: LiveCategoryRemapStats,
    streamedRawCount: Int,
    acceptedCount: Int
) {
    val matched = stats.observedChannelIds.intersect(mapping.metadataIds)
    val unmatched = stats.observedChannelIds - mapping.metadataIds
    Log.i(
        "SyncDiag",
        "Live category ID comparison provider=$providerId source=$source " +
            "metadataIds=${mapping.metadataIds.size} channelIds=${stats.observedChannelIds.size} " +
            "matched=${matched.size} unmatched=${unmatched.size} " +
            "nameRemapped=${stats.nameRemappedCount} unresolved=${stats.unresolvedNameCount} " +
            "raw=$streamedRawCount accepted=$acceptedCount"
    )
}

internal class SyncManagerXtreamLiveStrategy(
    private val xtreamCatalogApiService: XtreamApiService,
    private val xtreamCatalogHttpService: OkHttpXtreamApiService,
    private val xtreamAdaptiveSyncPolicy: XtreamAdaptiveSyncPolicy,
    private val xtreamSupport: SyncManagerXtreamSupport,
    private val xtreamFetcher: SyncManagerXtreamFetcher,
    private val catalogStrategySupport: SyncManagerCatalogStrategySupport,
    private val syncCatalogStore: SyncCatalogStore,
    private val progress: (Long, ((String) -> Unit)?, String) -> Unit,
    private val sanitizeThrowableMessage: (Throwable?) -> String,
    private val fullCatalogFallbackWarning: (String, Throwable?) -> String,
    private val categoryFailureWarning: (String, String, Throwable) -> String,
    private val liveCategorySequentialModeWarning: String,
    private val isCurrentlyLowOnMemory: () -> Boolean = { false },
    private val stageChannelItems: suspend (Long, List<Channel>, MutableSet<Long>, FallbackCategoryCollector, Long?) -> StagedCatalogSnapshot,
    private val syncProgressBus: SyncProgressBus,
    private val publishStagedLiveBatch: suspend (Long, Long) -> Int = { _, _ -> 0 }
) {
    suspend fun syncXtreamLiveCatalog(
        provider: Provider,
        api: XtreamProvider,
        existingMetadata: SyncMetadata,
        hiddenLiveCategoryIds: Set<Long>,
        onProgress: ((String) -> Unit)?,
        runtimeProfile: CatalogSyncRuntimeProfile,
        trackInitialLiveOnboarding: Boolean,
        prioritizeInitialLiveOnboarding: Boolean = false,
        publishInitialLiveBatch: Boolean = false,
        onFirstBatchPublished: (suspend (stagedSessionId: Long?) -> Unit)? = null,
        onCategoryCheckpoint: (suspend (completedCategoryKeys: List<String>, totalCategories: Int) -> Unit)? = null,
        existingStagedSessionId: Long? = null,
        categoryKeyFilter: Set<String>? = null,
        effectiveLiveSyncMethod: EffectiveXtreamLiveSyncMethod = EffectiveXtreamLiveSyncMethod.STREAM_ALL
    ): CatalogSyncPayload<Channel> {
        Log.i(XTREAM_LIVE_STRATEGY_TAG, "Xtream live strategy start for provider ${provider.id}.")
        val mobileInitialFullFirst = prioritizeInitialLiveOnboarding &&
            trackInitialLiveOnboarding &&
            !runtimeProfile.snapshot.isTelevision
        var rawLiveCategories: List<XtreamCategory>? = null
        var categoriesRequestAttempted = false

        suspend fun loadLiveCategoriesIfNeeded(): List<XtreamCategory>? {
            if (categoriesRequestAttempted) return rawLiveCategories
            categoriesRequestAttempted = true
            rawLiveCategories = when (val attempt = xtreamSupport.attemptNonCancellation {
                xtreamSupport.retryXtreamCatalogTransient(provider.id) {
                    xtreamSupport.executeXtreamRequest(provider.id, XtreamAdaptiveSyncPolicy.Stage.LIGHTWEIGHT) {
                        xtreamCatalogApiService.getLiveCategories(
                            XtreamUrlFactory.buildPlayerApiUrl(
                                serverUrl = provider.serverUrl,
                                username = provider.username,
                                password = provider.password,
                                action = "get_live_categories"
                            )
                        )
                    }
                }
            }) {
                is Attempt.Success -> attempt.value
                is Attempt.Failure -> {
                    Log.w(
                        XTREAM_LIVE_STRATEGY_TAG,
                        "Xtream live categories request failed for provider ${provider.id}: ${sanitizeThrowableMessage(attempt.error)}"
                    )
                    null
                }
            }
            return rawLiveCategories
        }

        // Category metadata is a single lightweight request and is required even for
        // mobile full-first onboarding so the UI can use the provider's real names.
        // The expensive channel request remains one get_live_streams call on success.
        loadLiveCategoriesIfNeeded()

        var resolvedCategories = rawLiveCategories
            ?.let { categories -> api.mapCategories(ContentType.LIVE, categories) }
            ?.map { category -> category.toEntity(provider.id) }
            ?.takeIf { it.isNotEmpty() }
        var filteredRawLiveCategories = rawLiveCategories.orEmpty().filterNot { category ->
            category.categoryId.toLongOrNull() in hiddenLiveCategoryIds
        }
        var visibleResolvedCategories = resolvedCategories
            ?.filterNot { category -> category.categoryId in hiddenLiveCategoryIds }
            ?.takeIf { it.isNotEmpty() }
        val liveCategoryMapping = buildLiveCategoryMapping(rawLiveCategories)
        Log.i(
            "SyncDiag",
            "Live categories provider=${provider.id} rawApi=${rawLiveCategories?.size ?: -1} " +
                "resolved=${resolvedCategories?.size ?: -1} hidden=${hiddenLiveCategoryIds.size} " +
                "visiblePreferred=${visibleResolvedCategories?.size ?: 0} " +
                "rawRequest=${if (rawLiveCategories != null) "ok" else "deferred"}"
        )

        var fullPayload = CatalogSyncPayload<Channel>(
            catalogResult = CatalogStrategyResult.EmptyValid("full"),
            categories = null
        )
        // Mobile Live-first onboarding now tries the single get_live_streams request
        // first. The streamed decoder publishes batches while the request is open, so
        // the first usable channels can appear without issuing one request per category.
        // TV/default onboarding keeps the existing category-first behavior unchanged.
        val shouldAttemptFullCatalog = hiddenLiveCategoryIds.isEmpty() &&
            runtimeProfile.shouldAttemptFullLiveCatalog(trackInitialLiveOnboarding) &&
            (mobileInitialFullFirst || (!trackInitialLiveOnboarding &&
                effectiveLiveSyncMethod == EffectiveXtreamLiveSyncMethod.STREAM_ALL))
        if (shouldAttemptFullCatalog) {
            Log.i(
                "SyncDiag",
                "Live initial request provider=${provider.id} action=get_live_streams mode=FULL_STREAM " +
                    "mobileFirst=$mobileInitialFullFirst"
            )
            progress(provider.id, onProgress, "Downloading Live TV...")
            fullPayload = loadXtreamLiveFull(
                provider = provider,
                api = api,
                runtimeProfile = runtimeProfile,
                onProgress = onProgress,
                publishInitialLiveBatch = publishInitialLiveBatch,
                onFirstBatchPublished = onFirstBatchPublished,
                categoryMapping = liveCategoryMapping
            )
            when (val fullResult = fullPayload.catalogResult) {
                is CatalogStrategyResult.Success -> return fullPayload.copy(
                    categories = catalogStrategySupport.mergePreferredAndFallbackCategories(
                        visibleResolvedCategories,
                        fullPayload.categories ?: catalogStrategySupport.buildFallbackLiveCategories(provider.id, fullResult.items)
                    ),
                    warnings = emptyList(),
                    strategyFeedback = XtreamStrategyFeedback(
                        attemptedFullCatalog = true,
                        fullCatalogUnsafe = false
                    )
                )
                is CatalogStrategyResult.Partial -> return fullPayload.copy(
                    categories = catalogStrategySupport.mergePreferredAndFallbackCategories(
                        visibleResolvedCategories,
                        fullPayload.categories ?: catalogStrategySupport.buildFallbackLiveCategories(provider.id, fullResult.items)
                    ),
                    warnings = emptyList(),
                    strategyFeedback = XtreamStrategyFeedback(
                        attemptedFullCatalog = true,
                        fullCatalogUnsafe = false
                    )
                )
                else -> Unit
            }
        } else {
            Log.i(
                XTREAM_LIVE_STRATEGY_TAG,
                "Xtream live full catalog skipped for provider ${provider.id}: hiddenCategories=${hiddenLiveCategoryIds.size} profile=${runtimeProfile.diagnosticsLabel} initial=$trackInitialLiveOnboarding."
            )
        }

        Log.i(
            "SyncDiag",
            "Live fallback request provider=${provider.id} mode=CATEGORY_BY_CATEGORY " +
                "categoryCount=${filteredRawLiveCategories.size}"
        )
        progress(provider.id, onProgress, "Downloading Live TV by category...")
        val categoryPayload = loadXtreamLiveByCategory(
            provider = provider,
            api = api,
            rawCategories = filteredRawLiveCategories,
            onProgress = onProgress,
            preferSequential = existingMetadata.liveSequentialFailuresRemembered || runtimeProfile.maxCategoryConcurrency <= 1,
            runtimeProfile = runtimeProfile,
            publishInitialLiveBatch = publishInitialLiveBatch,
            onFirstBatchPublished = if (fullPayload.stagedSessionId == null) onFirstBatchPublished else null,
            onCategoryCheckpoint = onCategoryCheckpoint,
            existingStagedSessionId = fullPayload.stagedSessionId ?: existingStagedSessionId,
            categoryKeyFilter = categoryKeyFilter,
            categoryMapping = liveCategoryMapping
        )
        return CatalogSyncPayload(
            catalogResult = categoryPayload.catalogResult,
            categories = catalogStrategySupport.mergePreferredAndFallbackCategories(
                visibleResolvedCategories,
                categoryPayload.categories
            ),
            warnings = (categoryPayload.warnings + catalogStrategySupport.strategyWarnings(fullPayload.catalogResult)).distinct(),
            stagedSessionId = categoryPayload.stagedSessionId,
            stagedAcceptedCount = categoryPayload.stagedAcceptedCount,
            strategyFeedback = XtreamStrategyFeedback(
                attemptedFullCatalog = shouldAttemptFullCatalog,
                preferredSegmentedFirst = !shouldAttemptFullCatalog,
                fullCatalogUnsafe = (fullPayload.catalogResult as? CatalogStrategyResult.Failure)?.error?.let(catalogStrategySupport::shouldAvoidFullCatalogStrategy) == true,
                segmentedStressDetected = catalogStrategySupport.sawSegmentedStress(
                    warnings = catalogStrategySupport.strategyWarnings(fullPayload.catalogResult),
                    result = categoryPayload.catalogResult,
                    sequentialWarnings = setOf(liveCategorySequentialModeWarning)
                )
            )
        )
    }

    suspend fun loadXtreamLiveFull(
        provider: Provider,
        api: XtreamProvider,
        runtimeProfile: CatalogSyncRuntimeProfile,
        onProgress: ((String) -> Unit)? = null,
        publishInitialLiveBatch: Boolean = false,
        onFirstBatchPublished: (suspend (stagedSessionId: Long?) -> Unit)? = null,
        categoryMapping: LiveCategoryMapping = LiveCategoryMapping.EMPTY
    ): CatalogSyncPayload<Channel> {
        val endpoint = XtreamUrlFactory.buildPlayerApiUrl(
            serverUrl = provider.serverUrl,
            username = provider.username,
            password = provider.password,
            action = "get_live_streams"
        )
        val decodeMode = XtreamLiveDecodeMode.THIN_WITH_LEGACY_FALLBACK
        if (decodeMode == XtreamLiveDecodeMode.LEGACY_ONLY) {
            return loadXtreamLiveFullStreaming(
                provider = provider,
                decoderLabel = "legacy",
                failureNextStep = "category-bulk",
                streamItems = { item -> xtreamCatalogHttpService.streamLiveStreams(endpoint, onItem = item) },
                mapRawBatch = { batch -> api.mapLiveStreamsSequence(batch) },
                runtimeProfile = runtimeProfile,
                onProgress = onProgress,
                publishInitialLiveBatch = publishInitialLiveBatch,
                onFirstBatchPublished = onFirstBatchPublished,
                categoryMapping = categoryMapping
            )
        }

        val thinPayload = loadXtreamLiveFullStreaming(
            provider = provider,
            decoderLabel = "thin",
            failureNextStep = "legacy-full",
            streamItems = { item -> xtreamCatalogHttpService.streamLiveStreamRows(endpoint, onItem = item) },
            mapRawBatch = { batch -> api.mapLiveStreamRowsSequence(batch) },
            runtimeProfile = runtimeProfile,
            onProgress = onProgress,
            publishInitialLiveBatch = publishInitialLiveBatch,
            onFirstBatchPublished = onFirstBatchPublished
        )
        if (!thinPayload.shouldRetryLegacyFullDecode()) {
            return thinPayload
        }

        Log.w(
            XTREAM_LIVE_STRATEGY_TAG,
            "Xtream live thin full-catalog decode did not produce a usable result for provider ${provider.id}; retrying legacy decode."
        )
        return loadXtreamLiveFullStreaming(
            provider = provider,
            decoderLabel = "legacy",
            failureNextStep = "category-bulk",
            streamItems = { item -> xtreamCatalogHttpService.streamLiveStreams(endpoint, onItem = item) },
            mapRawBatch = { batch -> api.mapLiveStreamsSequence(batch) },
            runtimeProfile = runtimeProfile,
            onProgress = onProgress,
            publishInitialLiveBatch = publishInitialLiveBatch,
            onFirstBatchPublished = onFirstBatchPublished
        )
    }

    private fun CatalogSyncPayload<Channel>.shouldRetryLegacyFullDecode(): Boolean {
        // If the thin decoder already staged usable rows, go straight to the category
        // fallback with the same session. Retrying the full endpoint would issue another
        // large request and could duplicate work or trigger provider throttling.
        if (stagedSessionId != null && stagedAcceptedCount > 0) return false
        val result = catalogResult
        if ((result as? CatalogStrategyResult.Failure)?.error is LowMemoryCatalogAbortException) return false
        return result is CatalogStrategyResult.Failure ||
            (result is CatalogStrategyResult.EmptyValid && result.warnings.any { warning ->
                warning.contains("raw items but none were usable", ignoreCase = true)
            })
    }

    private suspend fun <RawItem> loadXtreamLiveFullStreaming(
        provider: Provider,
        decoderLabel: String,
        failureNextStep: String,
        streamItems: suspend (suspend (RawItem) -> Unit) -> Int,
        mapRawBatch: suspend (Sequence<RawItem>) -> Sequence<Channel>,
        runtimeProfile: CatalogSyncRuntimeProfile,
        onProgress: ((String) -> Unit)?,
        publishInitialLiveBatch: Boolean,
        onFirstBatchPublished: (suspend (stagedSessionId: Long?) -> Unit)?,
        categoryMapping: LiveCategoryMapping
    ): CatalogSyncPayload<Channel> {
        val fallbackCollector = FallbackCategoryCollector(provider.id, ContentType.LIVE)
        val seenStreamIds = HashSet<Long>()
        val rawBatch = ArrayList<RawItem>(runtimeProfile.stageBatchSize)
        var stagedSessionId: Long? = null
        var acceptedCount = 0
        var streamedRawCount = 0
        var fullChannelsFailure: Throwable? = null
        var flushCount = 0
        var initialBatchPublished = false
        var mappingElapsedMs = 0L
        var stagingElapsedMs = 0L
        val remapStats = LiveCategoryRemapStats()

        fun abortIfLowMemory() {
            if (isCurrentlyLowOnMemory()) {
                // D12 — emission finale avant l'avortement low-memory : reflete l'etat
                // au moment de l'echec (section LIVE, mode indetermine, nombre d'items
                // deja acceptes). Le `reset()` du finally cote SyncManager (T3) viendra
                // ensuite ramener le flow a null — c'est volontaire (D7) pour eviter que
                // l'ecran suivant n'herite d'un etat partiel.
                syncProgressBus.emit(
                    SyncProgress(
                        section = Section.LIVE,
                        current = 0,
                        total = 0,
                        currentLabel = "",
                        itemsIndexed = acceptedCount
                    )
                )
                throw LowMemoryCatalogAbortException(
                    "Device entered low-memory state while streaming Live catalog; falling back to category sync."
                )
            }
        }

        suspend fun flushRawBatch() {
            if (rawBatch.isEmpty()) return
            lateinit var mappedChannels: List<Channel>
            mappingElapsedMs += measureTimeMillis {
                mappedChannels = remapLiveChannels(
                    channels = mapRawBatch(rawBatch.asSequence()).toList(),
                    mapping = categoryMapping,
                    stats = remapStats
                )
            }
            lateinit var staged: StagedCatalogSnapshot
            stagingElapsedMs += measureTimeMillis {
                staged = stageChannelItems(
                    provider.id,
                    mappedChannels,
                    seenStreamIds,
                    fallbackCollector,
                    stagedSessionId
                )
            }
            stagedSessionId = staged.sessionId
            acceptedCount += staged.acceptedCount
            flushCount++
            rawBatch.clear()
            val sessionIdForPublish = stagedSessionId
            val visibleChannelCount = if (publishInitialLiveBatch && sessionIdForPublish != null) {
                syncCatalogStore.publishStagedLiveBatchUpsertOnly(
                    providerId = provider.id,
                    sessionId = sessionIdForPublish
                )
            } else {
                null
            }
            if (publishInitialLiveBatch && staged.acceptedCount > 0 && !initialBatchPublished) {
                initialBatchPublished = true
                onFirstBatchPublished?.invoke(staged.sessionId)
            }
            val displayedChannelCount = visibleChannelCount ?: acceptedCount
            if (publishInitialLiveBatch) {
                onProgress?.invoke("Live TV channels $displayedChannelCount")
            }
            // Full catalog has no category denominator; emit an indeterminate total,
            // but keep itemsIndexed equal to the visible DB count after each flush.
            syncProgressBus.emit(
                SyncProgress(
                    section = Section.LIVE,
                    current = 0,
                    total = 0,
                    currentLabel = "",
                    itemsIndexed = displayedChannelCount
                )
            )
            abortIfLowMemory()
        }

        val fullChannelsElapsedMs = measureTimeMillis {
            when (val attempt = xtreamSupport.attemptNonCancellation {
                xtreamSupport.retryXtreamCatalogTransient(provider.id) {
                    xtreamSupport.executeXtreamRequest(provider.id, XtreamAdaptiveSyncPolicy.Stage.HEAVY) {
                        streamItems { item ->
                            rawBatch += item
                            streamedRawCount++
                            if (rawBatch.size >= runtimeProfile.stageBatchSize) {
                                flushRawBatch()
                            }
                        }.also {
                            flushRawBatch()
                        }
                    }
                }
            }) {
                is Attempt.Success -> Unit
                is Attempt.Failure -> {
                    fullChannelsFailure = attempt.error
                    val preserveStagedImportForFallback = stagedSessionId != null && acceptedCount > 0
                    if (preserveStagedImportForFallback) {
                        Log.w(
                            "SyncDiag",
                            "Live full request failed after partial staging; preserving session for category fallback " +
                                "provider=${provider.id} session=$stagedSessionId accepted=$acceptedCount"
                        )
                    } else {
                        stagedSessionId?.let { sessionId ->
                            syncCatalogStore.discardStagedImport(provider.id, sessionId)
                            stagedSessionId = null
                        }
                    }
                }
            }
        }

        logLiveCategoryMapping(
            providerId = provider.id,
            source = "full-$decoderLabel",
            mapping = categoryMapping,
            stats = remapStats,
            streamedRawCount = streamedRawCount,
            acceptedCount = acceptedCount
        )

        if (fullChannelsFailure != null) {
            xtreamSupport.logXtreamCatalogFallback(
                provider = provider,
                section = "live",
                stage = "full catalog ($decoderLabel)",
                elapsedMs = fullChannelsElapsedMs,
                itemCount = streamedRawCount,
                error = fullChannelsFailure,
                nextStep = failureNextStep
            )
            return CatalogSyncPayload(
                catalogResult = CatalogStrategyResult.Failure(
                    strategyName = "full",
                    error = fullChannelsFailure!!,
                    warnings = listOf(fullCatalogFallbackWarning("Live TV", fullChannelsFailure))
                ),
                categories = null,
                stagedSessionId = stagedSessionId,
                stagedAcceptedCount = acceptedCount
            )
        }

        if (streamedRawCount > 0) {
            Log.i(
                XTREAM_LIVE_STRATEGY_TAG,
                "Xtream live full catalog succeeded for provider ${provider.id} using $decoderLabel decoder in ${fullChannelsElapsedMs}ms " +
                    "with $acceptedCount accepted items from $streamedRawCount raw items " +
                    "across $flushCount flushes; mapping=${mappingElapsedMs}ms staging=${stagingElapsedMs}ms profile=${runtimeProfile.diagnosticsLabel}."
            )
            Log.i(
                "SyncDiag",
                    "Live sync provider=${provider.id} decoder=$decoderLabel raw=$streamedRawCount accepted=$acceptedCount " +
                        "flushes=$flushCount elapsedMs=$fullChannelsElapsedMs"
            )
            if (acceptedCount == 0) {
                return CatalogSyncPayload(
                    catalogResult = CatalogStrategyResult.EmptyValid(
                        strategyName = "full",
                        warnings = listOf("Live full catalog returned raw items but none were usable after mapping.")
                    ),
                    categories = null
                )
            }
            return CatalogSyncPayload(
                catalogResult = CatalogStrategyResult.Success(
                    strategyName = "full",
                    items = emptyList()
                ),
                categories = fallbackCollector.entities().takeIf { it.isNotEmpty() },
                stagedSessionId = stagedSessionId,
                stagedAcceptedCount = acceptedCount
            )
        }
        xtreamSupport.logXtreamCatalogFallback(
            provider = provider,
            section = "live",
            stage = "full catalog ($decoderLabel)",
            elapsedMs = fullChannelsElapsedMs,
            itemCount = streamedRawCount,
            error = null,
            nextStep = failureNextStep
        )
        return CatalogSyncPayload(
            catalogResult = CatalogStrategyResult.EmptyValid(
                strategyName = "full",
                warnings = listOf("Live full catalog returned an empty valid result.")
            ),
            categories = null
        )
    }

    suspend fun loadXtreamLiveByCategory(
        provider: Provider,
        api: XtreamProvider,
        rawCategories: List<XtreamCategory>,
        onProgress: ((String) -> Unit)?,
        preferSequential: Boolean,
        runtimeProfile: CatalogSyncRuntimeProfile,
        publishInitialLiveBatch: Boolean = false,
        onFirstBatchPublished: (suspend (stagedSessionId: Long?) -> Unit)? = null,
        onCategoryCheckpoint: (suspend (completedCategoryKeys: List<String>, totalCategories: Int) -> Unit)? = null,
        existingStagedSessionId: Long? = null,
        categoryKeyFilter: Set<String>? = null,
        categoryMapping: LiveCategoryMapping = LiveCategoryMapping.EMPTY
    ): CatalogSyncPayload<Channel> {
        val categories = rawCategories.filter {
            it.categoryId.isNotBlank() && (categoryKeyFilter == null || it.categoryId in categoryKeyFilter)
        }
        if (categories.isEmpty()) {
            return CatalogSyncPayload(
                catalogResult = CatalogStrategyResult.Failure(
                    strategyName = "category_bulk",
                    error = IllegalStateException("No live categories available"),
                    warnings = listOf("Live category-bulk strategy was unavailable because no categories were returned.")
                ),
                categories = null
            )
        }

        val fallbackCollector = FallbackCategoryCollector(provider.id, ContentType.LIVE)
        val seenStreamIds = HashSet<Long>()
        val stageMutex = Mutex()
        var stagedSessionId: Long? = existingStagedSessionId
        var stagedAcceptedCount = 0
        var initialBatchPublished = false
        var mappedChannelCount = 0
        val remapStats = LiveCategoryRemapStats()

        suspend fun stageMappedBatch(channels: List<Channel>) {
            if (channels.isEmpty()) return
            var publishSessionId: Long? = null
            stageMutex.withLock {
                val remappedChannels = remapLiveChannels(
                    channels = channels,
                    mapping = categoryMapping,
                    stats = remapStats
                )
                mappedChannelCount += channels.size
                val staged = stageChannelItems(
                    provider.id,
                    remappedChannels,
                    seenStreamIds,
                    fallbackCollector,
                    stagedSessionId
                )
                stagedSessionId = staged.sessionId
                stagedAcceptedCount += staged.acceptedCount
                if (publishInitialLiveBatch && !initialBatchPublished && staged.acceptedCount > 0) {
                    initialBatchPublished = true
                    publishSessionId = staged.sessionId
                }
            }
            publishSessionId?.let { sessionId ->
                try {
                    val visibleCount = publishStagedLiveBatch(provider.id, sessionId)
                    Log.i(
                        "SyncDiag",
                        "Live first batch published provider=${provider.id} session=$sessionId " +
                            "batchAccepted=$stagedAcceptedCount visibleDb=$visibleCount"
                    )
                    try {
                        onFirstBatchPublished?.invoke(sessionId)
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.w(
                            "SyncDiag",
                            "Live first batch follow-up scheduling failed provider=${provider.id}: " +
                                sanitizeThrowableMessage(error)
                        )
                    }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(
                        "SyncDiag",
                        "Live first batch publish failed provider=${provider.id}: ${sanitizeThrowableMessage(error)}"
                    )
                }
            }
        }

        val adaptiveConcurrency = xtreamAdaptiveSyncPolicy.concurrencyFor(
            providerId = provider.id,
            workloadSize = categories.size,
            preferSequential = preferSequential,
            stage = XtreamAdaptiveSyncPolicy.Stage.CATEGORY
        )
        val concurrency = adaptiveConcurrency.coerceAtMost(runtimeProfile.maxCategoryConcurrency)
        progress(provider.id, onProgress, "Downloading Live TV by category 0/${categories.size}...")

        // D11 — le total de catégories LIVE est fige une seule fois ici : la liste
        // `categories` ne mute plus pendant la recuperation, donc la lambda
        // `onCategoryCompleted` recoit le meme denominateur a chaque fenetre.
        val executionPlan = xtreamSupport.executeCategoryRecoveryPlan(
            provider = provider,
            categories = categories,
            initialConcurrency = concurrency,
            sectionLabel = "Live TV",
            sequentialModeWarning = liveCategorySequentialModeWarning,
            onProgress = onProgress,
            fetch = { category ->
                xtreamFetcher.fetchLiveCategoryOutcome(
                    provider = provider,
                    api = api,
                    category = category,
                    stageBatchSize = runtimeProfile.stageBatchSize,
                    onMappedBatch = ::stageMappedBatch
                )
            },
            onCategoryCompleted = { completed, total, currentLabel, completedCategoryKeys ->
                // D6 — emission structuree en parallele du callback string deja emis par
                // `executeCategoryRecoveryPlan` via `progress(...)`. Le compteur cumulatif
                // `stagedAcceptedCount` est mis a jour de maniere thread-safe sous le
                // `stageMutex` (cf `stageMappedBatch`), la lecture ici est best-effort
                // (snapshot UX, pas une metrique business).
                syncProgressBus.emit(
                    SyncProgress(
                        section = Section.LIVE,
                        current = completed,
                        total = total,
                        currentLabel = currentLabel,
                        itemsIndexed = stagedAcceptedCount
                    )
                )
                onCategoryCheckpoint?.invoke(completedCategoryKeys, total)
            }
        )
        var timedOutcomes = executionPlan.outcomes

        val categoryOutcomes = timedOutcomes.map { it.outcome }
        val failureCount = timedOutcomes.count { it.outcome is CategoryFetchOutcome.Failure }
        val fastFailureCount = timedOutcomes.count {
            it.elapsedMs <= 5_000L && it.outcome is CategoryFetchOutcome.Failure
        }
        val downgradeRecommended = catalogStrategySupport.shouldDowngradeCategorySync(
            categories.size,
            failureCount,
            fastFailureCount,
            categoryOutcomes
        )
        var fallbackWarnings = executionPlan.warnings
        if (concurrency > 1 && catalogStrategySupport.shouldRetryFailedCategories(
                categories.size,
                failureCount,
                downgradeRecommended,
                categoryOutcomes
            )
        ) {
            val failedRetryTotal = timedOutcomes.count { it.outcome is CategoryFetchOutcome.Failure }
            Log.w(
                XTREAM_LIVE_STRATEGY_TAG,
                "Xtream live category sync is continuing in sequential mode for failed categories on provider ${provider.id}."
            )
            progress(provider.id, onProgress, "Retrying failed Live TV categories 0/$failedRetryTotal...")
            timedOutcomes = xtreamSupport.continueFailedCategoryOutcomes(
                provider = provider,
                timedOutcomes = timedOutcomes,
                fetchSequentially = { category ->
                    xtreamFetcher.fetchLiveCategoryOutcome(
                        provider = provider,
                        api = api,
                        category = category,
                        stageBatchSize = runtimeProfile.stageBatchSize,
                        onMappedBatch = ::stageMappedBatch
                    )
                },
                onCategoryRetried = { completed, total, _ ->
                    progress(provider.id, onProgress, "Retrying failed Live TV categories $completed/$total...")
                }
            )
            fallbackWarnings = (fallbackWarnings + if (downgradeRecommended) listOf(liveCategorySequentialModeWarning) else emptyList()).distinct()
        }

        val completedCategoryKeys = timedOutcomes
            .filter { it.outcome !is CategoryFetchOutcome.Failure }
            .map { it.category.categoryId }
            .filter(String::isNotBlank)
            .distinct()
        onCategoryCheckpoint?.invoke(completedCategoryKeys, categories.size)

        val finalOutcomes = timedOutcomes.map { it.outcome }
        val warnings = finalOutcomes
            .filterIsInstance<CategoryFetchOutcome.Failure>()
            .map { failure -> categoryFailureWarning("Live TV", failure.categoryName, failure.error) } +
            fallbackWarnings

        val failedCategories = finalOutcomes.count { it is CategoryFetchOutcome.Failure }
        val emptyCategories = finalOutcomes.count { it is CategoryFetchOutcome.Empty }
        val successfulCategories = finalOutcomes.count { it is CategoryFetchOutcome.Success }
        Log.i(
            XTREAM_LIVE_STRATEGY_TAG,
            "Xtream live category strategy summary for provider ${provider.id}: successful=$successfulCategories empty=$emptyCategories failed=$failedCategories stagedChannels=$stagedAcceptedCount concurrency=$concurrency"
        )
        Log.i(
            "SyncDiag",
            "Live category sync provider=${provider.id} categoriesRaw=${categories.size} successful=$successfulCategories " +
                "empty=$emptyCategories failed=$failedCategories accepted=$stagedAcceptedCount"
        )
        logLiveCategoryMapping(
            providerId = provider.id,
            source = "category-bulk",
            mapping = categoryMapping,
            stats = remapStats,
            streamedRawCount = mappedChannelCount,
            acceptedCount = stagedAcceptedCount
        )

        return when {
            stagedAcceptedCount > 0 && failedCategories == 0 -> CatalogSyncPayload(
                catalogResult = CatalogStrategyResult.Success("category_bulk", emptyList(), warnings.toList()),
                categories = fallbackCollector.entities().takeIf { it.isNotEmpty() },
                stagedSessionId = stagedSessionId,
                stagedAcceptedCount = stagedAcceptedCount
            )
            stagedAcceptedCount > 0 -> CatalogSyncPayload(
                catalogResult = CatalogStrategyResult.Partial("category_bulk", emptyList(), warnings.toList()),
                categories = fallbackCollector.entities().takeIf { it.isNotEmpty() },
                stagedSessionId = stagedSessionId,
                stagedAcceptedCount = stagedAcceptedCount
            )
            failedCategories > 0 -> CatalogSyncPayload(
                catalogResult = CatalogStrategyResult.Failure(
                    strategyName = "category_bulk",
                    error = IllegalStateException("Live category-bulk sync failed for all usable categories"),
                    warnings = warnings.toList()
                ),
                categories = null
            )
            else -> CatalogSyncPayload(
                catalogResult = CatalogStrategyResult.EmptyValid(
                    strategyName = "category_bulk",
                    warnings = listOf("All live categories returned valid empty results.")
                ),
                categories = null
            )
        }
    }
}
