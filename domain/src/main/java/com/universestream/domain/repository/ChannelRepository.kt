package com.universestream.domain.repository

import com.universestream.domain.model.Category
import com.universestream.domain.model.Channel
import com.universestream.domain.model.Result
import com.universestream.domain.model.StreamInfo
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    fun getChannels(providerId: Long): Flow<List<Channel>>
    fun getChannelCount(providerId: Long): Flow<Int>
    fun getChannelCountByCategory(providerId: Long, categoryId: Long): Flow<Int>
    fun getChannelsByCategory(providerId: Long, categoryId: Long): Flow<List<Channel>>

    // Mobile-only ordered variant: adds name/id tie-breakers so channels with
    // no provider number still appear in a stable, readable order. TV keeps
    // getChannelsByCategory.
    fun getChannelsByCategoryMobileOrdered(providerId: Long, categoryId: Long): Flow<List<Channel>>
    /** Lazy mobile-only channel stream; TV continues to use the list-based APIs above. */
    fun getMobileChannels(providerId: Long, categoryId: Long = ALL_CHANNELS_ID): Flow<PagingData<Channel>>
    fun getChannelsByCategoryPage(providerId: Long, categoryId: Long, limit: Int): Flow<List<Channel>>
    fun getChannelsByNumber(providerId: Long, categoryId: Long = ALL_CHANNELS_ID): Flow<List<Channel>>
    fun getChannelsWithoutErrors(providerId: Long, categoryId: Long = ALL_CHANNELS_ID): Flow<List<Channel>>
    fun getChannelsWithoutErrorsPage(providerId: Long, categoryId: Long = ALL_CHANNELS_ID, limit: Int): Flow<List<Channel>>
    suspend fun getChannelsByCategoryPageOffset(providerId: Long, categoryId: Long, limit: Int, offset: Int): List<Channel>
    suspend fun getChannelsWithoutErrorsPageOffset(providerId: Long, categoryId: Long, limit: Int, offset: Int): List<Channel>
    fun searchChannelsByCategory(providerId: Long, categoryId: Long, query: String): Flow<List<Channel>>
    fun searchChannelsByCategoryPaged(providerId: Long, categoryId: Long, query: String, limit: Int): Flow<List<Channel>>
    fun getCategories(providerId: Long): Flow<List<Category>>
    fun searchChannels(providerId: Long, query: String): Flow<List<Channel>>
    suspend fun getChannel(channelId: Long): Channel?
    suspend fun getStreamInfo(channel: Channel, preferStableUrl: Boolean = false): Result<StreamInfo>
    suspend fun refreshChannels(providerId: Long): Result<Unit>
    fun getChannelsByIds(ids: List<Long>): Flow<List<Channel>>
    suspend fun incrementChannelErrorCount(channelId: Long): Result<Unit>
    suspend fun resetChannelErrorCount(channelId: Long): Result<Unit>

    companion object {
        const val ALL_CHANNELS_ID = -1_000_000L
    }
}
