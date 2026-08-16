package com.universestream.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.universestream.app.BuildConfig
import com.universestream.data.local.UniverseStreamDatabase
import com.universestream.data.local.dao.*
import com.universestream.data.remote.jellyfin.JellyfinProvider
import com.google.gson.Gson
import okhttp3.OkHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DEBUG_SLOW_QUERY_THRESHOLD_MS = 100L

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): UniverseStreamDatabase =
        Room.databaseBuilder(
            context,
            UniverseStreamDatabase::class.java,
            "universestream.db"
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .openHelperFactory(
                if (BuildConfig.DEBUG) {
                    SlowQueryLoggingOpenHelperFactory(
                        delegate = FrameworkSQLiteOpenHelperFactory(),
                        slowQueryThresholdMs = DEBUG_SLOW_QUERY_THRESHOLD_MS
                    )
                } else {
                    FrameworkSQLiteOpenHelperFactory()
                }
            )
            .addMigrations(
                UniverseStreamDatabase.MIGRATION_1_2,
                UniverseStreamDatabase.MIGRATION_2_3,
                UniverseStreamDatabase.MIGRATION_3_4,
                UniverseStreamDatabase.MIGRATION_4_5,
                UniverseStreamDatabase.MIGRATION_5_6,
                UniverseStreamDatabase.MIGRATION_6_7,
                UniverseStreamDatabase.MIGRATION_7_8,
                UniverseStreamDatabase.MIGRATION_8_9,
                UniverseStreamDatabase.MIGRATION_9_10,
                UniverseStreamDatabase.MIGRATION_10_11,
                UniverseStreamDatabase.MIGRATION_11_12,
                UniverseStreamDatabase.MIGRATION_12_13,
                UniverseStreamDatabase.MIGRATION_13_14,
                UniverseStreamDatabase.MIGRATION_14_15,
                UniverseStreamDatabase.MIGRATION_15_16,
                UniverseStreamDatabase.MIGRATION_16_17,
                UniverseStreamDatabase.MIGRATION_17_18,
                UniverseStreamDatabase.MIGRATION_18_19,
                UniverseStreamDatabase.MIGRATION_19_20,
                UniverseStreamDatabase.MIGRATION_20_21,
                UniverseStreamDatabase.MIGRATION_21_22,
                UniverseStreamDatabase.MIGRATION_22_23,
                UniverseStreamDatabase.MIGRATION_23_24,
                UniverseStreamDatabase.MIGRATION_24_25,
                UniverseStreamDatabase.MIGRATION_25_26,
                UniverseStreamDatabase.MIGRATION_26_27,
                UniverseStreamDatabase.MIGRATION_27_28,
                UniverseStreamDatabase.MIGRATION_28_29,
                UniverseStreamDatabase.MIGRATION_29_30,
                UniverseStreamDatabase.MIGRATION_30_31,
                UniverseStreamDatabase.MIGRATION_31_32,
                UniverseStreamDatabase.MIGRATION_32_33,
                UniverseStreamDatabase.MIGRATION_33_34,
                UniverseStreamDatabase.MIGRATION_34_35,
                UniverseStreamDatabase.MIGRATION_35_36,
                UniverseStreamDatabase.MIGRATION_36_37,
                UniverseStreamDatabase.MIGRATION_37_38,
                UniverseStreamDatabase.MIGRATION_38_39,
                UniverseStreamDatabase.MIGRATION_39_40,
                UniverseStreamDatabase.MIGRATION_40_41,
                UniverseStreamDatabase.MIGRATION_41_42,
                UniverseStreamDatabase.MIGRATION_42_43,
                UniverseStreamDatabase.MIGRATION_43_44,
                UniverseStreamDatabase.MIGRATION_44_45,
                UniverseStreamDatabase.MIGRATION_45_46,
                UniverseStreamDatabase.MIGRATION_46_47,
                UniverseStreamDatabase.MIGRATION_47_48,
                UniverseStreamDatabase.MIGRATION_48_49,
                UniverseStreamDatabase.MIGRATION_49_50,
                UniverseStreamDatabase.MIGRATION_50_51,
                UniverseStreamDatabase.MIGRATION_51_52,
                UniverseStreamDatabase.MIGRATION_52_53,
                UniverseStreamDatabase.MIGRATION_53_54,
                UniverseStreamDatabase.MIGRATION_54_55,
                UniverseStreamDatabase.MIGRATION_55_56,
                UniverseStreamDatabase.MIGRATION_56_57,
                UniverseStreamDatabase.MIGRATION_57_58,
                UniverseStreamDatabase.MIGRATION_58_59,
                UniverseStreamDatabase.MIGRATION_59_60,
                UniverseStreamDatabase.MIGRATION_60_61,
                UniverseStreamDatabase.MIGRATION_61_62
            )
            // NOTE: fallbackToDestructiveMigration() intentionally removed.
            // All future schema changes MUST add a corresponding Migration in UniverseStreamDatabase.
            .build()

    @Provides @Singleton
    fun provideJellyfinProvider(okHttpClient: OkHttpClient, gson: Gson): JellyfinProvider = JellyfinProvider(okHttpClient, gson)

    @Provides fun provideProviderDao(db: UniverseStreamDatabase): ProviderDao = db.providerDao()
    @Provides fun provideChannelDao(db: UniverseStreamDatabase): ChannelDao = db.channelDao()
    @Provides fun provideChannelPreferenceDao(db: UniverseStreamDatabase): ChannelPreferenceDao = db.channelPreferenceDao()
    @Provides fun provideMovieDao(db: UniverseStreamDatabase): MovieDao = db.movieDao()
    @Provides fun provideSeriesDao(db: UniverseStreamDatabase): SeriesDao = db.seriesDao()
    @Provides fun provideEpisodeDao(db: UniverseStreamDatabase): EpisodeDao = db.episodeDao()
    @Provides fun provideCategoryDao(db: UniverseStreamDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideCatalogSyncDao(db: UniverseStreamDatabase): CatalogSyncDao = db.catalogSyncDao()
    @Provides fun provideProgramDao(db: UniverseStreamDatabase): ProgramDao = db.programDao()
    @Provides fun provideFavoriteDao(db: UniverseStreamDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideVirtualGroupDao(db: UniverseStreamDatabase): VirtualGroupDao = db.virtualGroupDao()
    @Provides fun providePlaybackHistoryDao(db: UniverseStreamDatabase): PlaybackHistoryDao = db.playbackHistoryDao()
    @Provides fun provideTmdbIdentityDao(db: UniverseStreamDatabase): TmdbIdentityDao = db.tmdbIdentityDao()
    @Provides fun provideSearchHistoryDao(db: UniverseStreamDatabase): SearchHistoryDao = db.searchHistoryDao()
    @Provides fun provideSearchDao(db: UniverseStreamDatabase): SearchDao = db.searchDao()
    @Provides fun provideSyncMetadataDao(db: UniverseStreamDatabase): SyncMetadataDao = db.syncMetadataDao()
    @Provides fun provideMovieCategoryHydrationDao(db: UniverseStreamDatabase): MovieCategoryHydrationDao = db.movieCategoryHydrationDao()
    @Provides fun provideSeriesCategoryHydrationDao(db: UniverseStreamDatabase): SeriesCategoryHydrationDao = db.seriesCategoryHydrationDao()
    @Provides fun provideEpgSourceDao(db: UniverseStreamDatabase): EpgSourceDao = db.epgSourceDao()
    @Provides fun provideProviderEpgSourceDao(db: UniverseStreamDatabase): ProviderEpgSourceDao = db.providerEpgSourceDao()
    @Provides fun provideEpgChannelDao(db: UniverseStreamDatabase): EpgChannelDao = db.epgChannelDao()
    @Provides fun provideEpgProgrammeDao(db: UniverseStreamDatabase): EpgProgrammeDao = db.epgProgrammeDao()
    @Provides fun provideChannelEpgMappingDao(db: UniverseStreamDatabase): ChannelEpgMappingDao = db.channelEpgMappingDao()
    @Provides fun provideCombinedM3uProfileDao(db: UniverseStreamDatabase): CombinedM3uProfileDao = db.combinedM3uProfileDao()
    @Provides fun provideCombinedM3uProfileMemberDao(db: UniverseStreamDatabase): CombinedM3uProfileMemberDao = db.combinedM3uProfileMemberDao()
    @Provides fun provideRecordingScheduleDao(db: UniverseStreamDatabase): RecordingScheduleDao = db.recordingScheduleDao()
    @Provides fun provideRecordingRunDao(db: UniverseStreamDatabase): RecordingRunDao = db.recordingRunDao()
    @Provides fun provideProgramReminderDao(db: UniverseStreamDatabase): ProgramReminderDao = db.programReminderDao()
    @Provides fun provideRecordingStorageDao(db: UniverseStreamDatabase): RecordingStorageDao = db.recordingStorageDao()
    @Provides fun providePlaybackCompatibilityDao(db: UniverseStreamDatabase): PlaybackCompatibilityDao = db.playbackCompatibilityDao()
    @Provides fun provideXtreamContentIndexDao(db: UniverseStreamDatabase): XtreamContentIndexDao = db.xtreamContentIndexDao()
    @Provides fun provideXtreamIndexJobDao(db: UniverseStreamDatabase): XtreamIndexJobDao = db.xtreamIndexJobDao()
    @Provides fun provideXtreamLiveOnboardingDao(db: UniverseStreamDatabase): XtreamLiveOnboardingDao = db.xtreamLiveOnboardingDao()
    @Provides fun provideDownloadDao(db: UniverseStreamDatabase): DownloadDao = db.downloadDao()
}
