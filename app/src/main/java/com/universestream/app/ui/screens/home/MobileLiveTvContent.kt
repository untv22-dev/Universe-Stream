package com.universestream.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.universestream.app.R
import com.universestream.app.ui.components.SearchInput
import com.universestream.app.ui.components.LiveSourceSwitcher
import com.universestream.app.ui.components.shell.LiveChannelRowSurface
import com.universestream.app.ui.components.TvEmptyState
import com.universestream.app.ui.design.AppColors
import com.universestream.app.ui.interaction.TvClickableSurface
import com.universestream.domain.model.ActiveLiveSource
import com.universestream.domain.model.Category
import com.universestream.domain.model.Channel
import com.universestream.domain.model.Provider
import com.universestream.domain.repository.ChannelRepository

/**
 * Compact / phone Live TV screen.
 *
 * Layout intent (phone-only, Television path is never routed here):
 *  - A real horizontal category strip at the top, the way native IPTV apps
 *    present categories, so a user can move between categories with one tap
 *    instead of opening a dialog.
 *  - The first *real* category is selected on entry, not the aggregated
 *    "All channels" surface, so a cold start renders a single reasonably
 *    sized category instead of the full multi-thousand channel list.
 *  - "All channels" stays available as the first chip, but it is a choice,
 *    not the default.
 *  - Channels for the selected category are loaded page-first from Room on mobile;
 *    the total count remains visible while more pages are loaded on scroll.
 */
@Composable
fun MobileLiveTvContent(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    onChannelClick: (Channel, Category?, Provider?, Long?, Long?) -> Unit,
    onNavigate: (String) -> Unit,
    resolveProviderForChannel: (Channel) -> Provider?,
    isCategoryLocked: (Category) -> Boolean,
    isChannelLocked: (Channel) -> Boolean
) {
    // Enable the bounded, page-first Room flow for the mobile renderer.
    // Television and the existing non-compact Home renderer never enable this.
    LaunchedEffect(Unit) {
        viewModel.enableMobileDatabaseFirst()
    }

    val allChannelsCategory = uiState.categories.firstOrNull { it.id == ChannelRepository.ALL_CHANNELS_ID }

    // Categories shown in the strip: real, unlocked categories, de-duplicated,
    // with the aggregated "All channels" surface handled separately as a leading chip.
    val stripCategories = remember(uiState.categories, uiState.parentalControlLevel, uiState.unlockedCategoryIds) {
        uiState.categories
            .asSequence()
            .distinctBy { it.id }
            .filter { it.id != ChannelRepository.ALL_CHANNELS_ID }
            .filterNot(isCategoryLocked)
            .toList()
    }

    // On entry, land on the first real category instead of All channels.
    // Only auto-selects when nothing is selected yet, so it never fights a user tap
    // or a saved selection the user deliberately left on.
    LaunchedEffect(uiState.isCategoriesLoading, stripCategories, uiState.selectedCategory?.id) {
        if (uiState.isCategoriesLoading) return@LaunchedEffect
        if (uiState.selectedCategory == null) {
            val firstReal = stripCategories.firstOrNull()
            when {
                firstReal != null -> viewModel.selectCategory(firstReal)
                allChannelsCategory != null -> viewModel.selectCategory(allChannelsCategory)
            }
        }
    }

    val selectedCategory = uiState.selectedCategory?.takeIf { !isCategoryLocked(it) }
    val selectedId = selectedCategory?.id
    val isAllSelected = selectedId == ChannelRepository.ALL_CHANNELS_ID
    val channels = uiState.filteredChannels
    val channelListState = rememberLazyListState()

    // The ViewModel owns paging and Room remains the source of truth. The list
    // below is already bounded, so Compose never receives all thousands of rows
    // before the first mobile emission.

    var loadingTimedOut by rememberSaveable(selectedId) { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading, uiState.isCategoriesLoading, selectedId) {
        loadingTimedOut = false
        if (uiState.isLoading && !uiState.isCategoriesLoading) {
            kotlinx.coroutines.delay(15_000)
            if (uiState.filteredChannels.isEmpty()) loadingTimedOut = true
        }
    }

    // Never hide real cached channels behind a synchronization placeholder.
    val hasCachedChannels = channels.isNotEmpty()
    val showLoading = !hasCachedChannels && (
        uiState.isCategoriesLoading ||
            uiState.isLocalChannelQueryLoading ||
            (uiState.isLoading && !loadingTimedOut)
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchInput(
                value = uiState.channelSearchQuery,
                onValueChange = viewModel::updateChannelSearchQuery,
                placeholder = "Search channels",
                modifier = Modifier.weight(1f)
            )
            if (uiState.showLiveSourceSwitcher && uiState.liveSourceOptions.isNotEmpty()) {
                LiveSourceSwitcher(
                    currentSource = uiState.activeLiveSource,
                    options = uiState.liveSourceOptions,
                    onSourceSelected = viewModel::switchLiveSource,
                    compact = true
                )
            }
        }

        // Horizontal category strip. "All" is the leading chip; every real
        // category follows. The selected chip is filled with the brand accent.
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
        ) {
            if (allChannelsCategory != null) {
                item(key = "all-channels-chip") {
                    CategoryChip(
                        label = stringResource(R.string.home_all_channels),
                        selected = isAllSelected,
                        onClick = {
                            viewModel.clearCategorySearchQuery()
                            viewModel.selectCategory(allChannelsCategory)
                        }
                    )
                }
            }
            items(stripCategories, key = { it.id }) { category ->
                CategoryChip(
                    label = category.name,
                    selected = category.id == selectedId,
                    onClick = {
                        viewModel.clearCategorySearchQuery()
                        viewModel.selectCategory(category)
                    }
                )
            }
        }

        Text(
            text = stringResource(
                R.string.player_channel_count_format,
                uiState.channelTotalCount.coerceAtLeast(channels.size)
            ),
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium
        )

        if (uiState.isLocalChannelQueryLoading && hasCachedChannels) {
            Text(
                text = stringResource(R.string.home_loading_local_catalog),
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }

        if (showLoading) {
            TvEmptyState(
                title = stringResource(R.string.home_loading_channels),
                subtitle = if (uiState.isLocalChannelQueryLoading) {
                    stringResource(R.string.home_loading_local_catalog)
                } else {
                    stringResource(R.string.home_live_retry_subtitle)
                }
            )
        } else if (!hasCachedChannels && (uiState.errorMessage != null || loadingTimedOut)) {
            TvEmptyState(
                title = if (loadingTimedOut) {
                    stringResource(R.string.home_live_taking_longer)
                } else {
                    stringResource(R.string.home_error_load_failed)
                },
                subtitle = uiState.errorMessage ?: stringResource(R.string.home_live_retry_subtitle),
                actionLabel = stringResource(R.string.home_live_retry),
                onAction = viewModel::retryLiveTv
            )
        } else if (!hasCachedChannels) {
            TvEmptyState(
                title = stringResource(R.string.home_no_channels_found),
                subtitle = stringResource(R.string.home_no_channels_found_subtitle),
                actionLabel = stringResource(R.string.home_live_retry),
                onAction = viewModel::retryLiveTv
            )
        } else {
            LazyColumn(
                state = channelListState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(channels, key = { it.id }) { channel ->
                    LiveChannelRowSurface(
                        channel = channel,
                        onClick = {
                            if (!isChannelLocked(channel)) {
                                onChannelClick(
                                    channel,
                                    selectedCategory,
                                    resolveProviderForChannel(channel),
                                    (uiState.activeLiveSource as? ActiveLiveSource.CombinedM3uSource)?.profileId,
                                    uiState.selectedCombinedSourceProviderId
                                )
                            }
                        },
                        isLocked = isChannelLocked(channel),
                        rowHeight = 68.dp,
                        logoTargetSizeDp = 96.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TvClickableSurface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) AppColors.Brand else AppColors.SurfaceElevated,
            focusedContainerColor = AppColors.SurfaceEmphasis
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) AppColors.Surface else AppColors.TextPrimary
        )
    }
}
