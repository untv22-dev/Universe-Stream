package com.universestream.app.ui.screens.settings

import android.app.Application
import com.universestream.app.R
import com.universestream.app.ui.model.LiveTvChannelMode
import com.universestream.app.ui.model.LiveTvQuickFilterVisibilityMode
import com.universestream.app.ui.model.VodViewMode
import com.universestream.domain.model.AppTimeFormat
import com.universestream.domain.model.AppHomeDashboardShelf
import com.universestream.domain.model.AppLandingDestination
import com.universestream.domain.model.AppTopLevelDestination
import com.universestream.domain.model.AudioOutputPreference
import com.universestream.domain.model.Category
import com.universestream.domain.model.ExternalPlaybackMode
import com.universestream.domain.model.ChannelNumberingMode
import com.universestream.domain.model.DecoderMode
import com.universestream.domain.model.GroupedChannelLabelMode
import com.universestream.domain.model.LiveChannelGroupingMode
import com.universestream.domain.model.LiveVariantPreferenceMode
import com.universestream.domain.model.PlaybackBufferMode
import com.universestream.domain.model.VodDuplicateHandlingMode
import com.universestream.domain.model.VodHttpProtocolMode
import com.universestream.domain.model.VodVariantPreferenceMode
import com.universestream.domain.model.PlayerSurfaceMode
import com.universestream.domain.model.Provider
import com.universestream.domain.model.RemoteShortcutPreferences
import com.universestream.domain.model.TimeshiftBackendPreference

enum class ProviderWarningAction {
    EPG,
    MOVIES,
    SERIES
}

enum class ProviderSyncSelection {
    SYNC_NOW,
    REBUILD_INDEX,
    TV,
    MOVIES,
    SERIES,
    EPG
}

internal data class SettingsPreferenceSnapshot(
    val providers: List<Provider>,
    val activeProviderId: Long?,
    val parentalControlLevel: Int,
    val hasParentalPin: Boolean,
    val appLanguage: String,
    val appLandingDestination: AppLandingDestination,
    val appTopLevelDestinations: List<AppTopLevelDestination>,
    val appHomeDashboardShelves: List<AppHomeDashboardShelf>,
    val appTimeFormat: AppTimeFormat,
    val preferredAudioLanguage: String,
    val playerMediaSessionEnabled: Boolean,
    val playerFastRetryOnTransientFailures: Boolean,
    val playerAudioDecoderMode: DecoderMode,
    val playerVideoDecoderMode: DecoderMode,
    val playerPlaybackBufferMode: PlaybackBufferMode,
    val playerAudioOutputPreference: AudioOutputPreference,
    val playerCompatibilityMemoryEnabled: Boolean,
    val playerSurfaceMode: PlayerSurfaceMode,
    val playerVodHttpProtocolMode: VodHttpProtocolMode,
    val playerPlaybackSpeed: Float,
    val playerExternalPlaybackMode: ExternalPlaybackMode,
    val playerAudioVideoSyncEnabled: Boolean,
    val playerAudioVideoOffsetMs: Int,
    val centerTwoSlotMultiviewLayout: Boolean,
    val multiViewRespectProviderConnectionLimit: Boolean,
    val playerControlsTimeoutSeconds: Int,
    val playerLiveOverlayTimeoutSeconds: Int,
    val playerNoticeTimeoutSeconds: Int,
    val playerDiagnosticsTimeoutSeconds: Int,
    val subtitleTextScale: Float,
    val subtitleTextColor: Int,
    val subtitleBackgroundColor: Int,
    val playerLiveTranslationEnabled: Boolean,
    val playerLiveTranslationEndpoint: String,
    val wifiMaxVideoHeight: Int?,
    val ethernetMaxVideoHeight: Int?,
    val playerTimeshiftEnabled: Boolean,
    val playerTimeshiftDepthMinutes: Int,
    val playerTimeshiftBackend: TimeshiftBackendPreference,
    val defaultStopPlaybackTimerMinutes: Int,
    val defaultIdleStandbyTimerMinutes: Int,
    val isIncognitoMode: Boolean,
    val useXtreamTextClassification: Boolean,
    val xtreamBase64TextCompatibility: Boolean,
    val liveTvChannelMode: LiveTvChannelMode,
    val showLiveSourceSwitcher: Boolean,
    val showFavoritesCategory: Boolean,
    val showAllChannelsCategory: Boolean,
    val showRecentChannelsCategory: Boolean,
    val remoteShortcutPreferences: RemoteShortcutPreferences,
    val liveTvCategoryFilters: List<String>,
    val liveTvQuickFilterVisibilityMode: LiveTvQuickFilterVisibilityMode,
    val hideDecorativeLiveRows: Boolean,
    val liveChannelNumberingMode: ChannelNumberingMode,
    val liveChannelGroupingMode: LiveChannelGroupingMode,
    val groupedChannelLabelMode: GroupedChannelLabelMode,
    val liveVariantPreferenceMode: LiveVariantPreferenceMode,
    val vodViewMode: VodViewMode,
    val vodInfiniteScroll: Boolean,
    val vodDuplicateHandlingMode: VodDuplicateHandlingMode,
    val vodVariantPreferenceMode: VodVariantPreferenceMode,
    val guideDefaultCategoryId: Long,
    val guideDefaultCategoryOptions: List<Category>,
    val preventStandbyDuringPlayback: Boolean,
    val zapAutoRevert: Boolean,
    val autoPlayNextEpisode: Boolean,
    val autoCheckAppUpdates: Boolean,
    val autoDownloadAppUpdates: Boolean,
    val lastAppUpdateCheckAt: Long?,
    val cachedAppUpdateVersionName: String?,
    val cachedAppUpdateVersionCode: Int?,
    val cachedAppUpdateReleaseUrl: String?,
    val cachedAppUpdateDownloadUrl: String?,
    val cachedAppUpdateDownloadSha256: String?,
    val cachedAppUpdateReleaseNotes: String,
    val cachedAppUpdatePublishedAt: String?
)

internal fun ProviderSyncSelection.label(application: Application): String = when (this) {
    ProviderSyncSelection.SYNC_NOW -> application.getString(R.string.settings_sync_option_sync_now)
    ProviderSyncSelection.REBUILD_INDEX -> application.getString(R.string.settings_sync_option_rebuild_index)
    ProviderSyncSelection.TV -> application.getString(R.string.settings_sync_option_tv)
    ProviderSyncSelection.MOVIES -> application.getString(R.string.settings_sync_option_movies)
    ProviderSyncSelection.SERIES -> application.getString(R.string.settings_sync_option_series)
    ProviderSyncSelection.EPG -> application.getString(R.string.settings_sync_option_epg)
}
