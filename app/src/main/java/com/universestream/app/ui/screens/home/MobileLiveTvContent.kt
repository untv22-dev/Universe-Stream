package com.universestream.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.universestream.domain.model.VirtualCategoryIds
import com.universestream.domain.repository.ChannelRepository

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
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    val visibleCategories = remember(uiState.categories, uiState.categorySearchQuery) {
        uiState.categories.filter {
            uiState.categorySearchQuery.isBlank() ||
                it.name.contains(uiState.categorySearchQuery, ignoreCase = true)
        }
    }
    val unlockedCategories = remember(visibleCategories, uiState.parentalControlLevel, uiState.unlockedCategoryIds) {
        visibleCategories.filterNot(isCategoryLocked)
    }
    val allChannelsCategory = uiState.categories.firstOrNull { it.id == ChannelRepository.ALL_CHANNELS_ID }
    val selectedCategory = uiState.selectedCategory?.takeIf { !isCategoryLocked(it) }
    val selectedLabel = selectedCategory?.name ?: "All channels"
    val channels = uiState.filteredChannels
    var loadingTimedOut by rememberSaveable(uiState.selectedCategory?.id) { mutableStateOf(false) }

    LaunchedEffect(uiState.categories, uiState.selectedCategory?.id, uiState.isCategoriesLoading) {
        val current = uiState.selectedCategory
        val shouldUseAllChannels = allChannelsCategory != null &&
            !uiState.isCategoriesLoading &&
            (current == null || (current.id == VirtualCategoryIds.RECENT && uiState.recentChannels.isEmpty()))
        if (shouldUseAllChannels && current?.id != allChannelsCategory?.id) {
            viewModel.selectCategory(allChannelsCategory!!)
        }
    }

    LaunchedEffect(uiState.isLoading, uiState.isCategoriesLoading, uiState.selectedCategory?.id) {
        loadingTimedOut = false
        if (uiState.isLoading && !uiState.isCategoriesLoading) {
            kotlinx.coroutines.delay(15_000)
            if (uiState.filteredChannels.isEmpty()) loadingTimedOut = true
        }
    }

    // Never hide real cached channels behind a synchronization placeholder.
    // A cold start may still be loading categories while Room already has channels.
    val hasCachedChannels = channels.isNotEmpty()
    val showLoading = !hasCachedChannels && (uiState.isCategoriesLoading || (uiState.isLoading && !loadingTimedOut))

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TvClickableSurface(
                onClick = { showCategoryPicker = true },
                modifier = Modifier.weight(1f),
                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                    containerColor = AppColors.SurfaceElevated,
                    focusedContainerColor = AppColors.SurfaceEmphasis
                )
            ) {
                Text(
                    text = selectedLabel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextPrimary
                )
            }
            TvClickableSurface(
                onClick = {
                    allChannelsCategory?.let(viewModel::selectCategory)
                    viewModel.clearCategorySearchQuery()
                },
                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                    containerColor = AppColors.SurfaceElevated,
                    focusedContainerColor = AppColors.SurfaceEmphasis
                )
            ) {
                Text(
                    text = "All",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.BrandStrong
                )
            }
        }

        Text(
            text = "${channels.size} channels",
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium
        )

        if (showLoading) {
            TvEmptyState(
                title = stringResource(R.string.home_loading_channels),
                subtitle = stringResource(R.string.home_live_retry_subtitle)
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { androidx.compose.material3.Text("Choose category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            allChannelsCategory?.let(viewModel::selectCategory)
                            viewModel.clearCategorySearchQuery()
                            showCategoryPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { androidx.compose.material3.Text("All channels") }
                    unlockedCategories.forEach { category ->
                        TextButton(
                            onClick = {
                                viewModel.selectCategory(category)
                                showCategoryPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { androidx.compose.material3.Text(category.name) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryPicker = false }) {
                    androidx.compose.material3.Text("Close")
                }
            }
        )
    }
}
