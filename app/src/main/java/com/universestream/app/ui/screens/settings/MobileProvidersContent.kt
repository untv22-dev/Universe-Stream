package com.universestream.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.universestream.app.R
import com.universestream.app.ui.components.TvEmptyState
import com.universestream.app.ui.interaction.TvClickableSurface
import com.universestream.app.ui.design.AppColors
import com.universestream.app.ui.theme.ErrorColor
import com.universestream.app.ui.theme.OnBackground
import com.universestream.app.ui.theme.OnSurface
import com.universestream.app.ui.theme.OnSurfaceDim
import com.universestream.app.ui.theme.Primary
import com.universestream.app.ui.theme.SurfaceElevated
import com.universestream.app.ui.theme.SurfaceHighlight
import com.universestream.domain.model.Provider
import com.universestream.domain.model.ProviderStatus
import com.universestream.domain.model.ProviderType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MobileProvidersContent(
    uiState: SettingsUiState,
    onAddProvider: () -> Unit,
    onEditProvider: (Provider) -> Unit,
    onRefreshProvider: (Long) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_providers),
                style = MaterialTheme.typography.headlineSmall,
                color = OnBackground,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        if (uiState.providers.isEmpty()) {
            item {
                TvEmptyState(
                    title = stringResource(R.string.settings_no_providers),
                    subtitle = stringResource(R.string.settings_no_providers_subtitle),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(uiState.providers, key = { it.id }) { provider ->
                MobileProviderCard(
                    provider = provider,
                    isActive = provider.id == uiState.activeProviderId,
                    isSyncing = uiState.isSyncing && provider.id == uiState.activeProviderId,
                    onClick = { onEditProvider(provider) },
                    onRefresh = { onRefreshProvider(provider.id) }
                )
            }
        }

        item {
            TvClickableSurface(
                onClick = onAddProvider,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Primary.copy(alpha = 0.15f),
                    focusedContainerColor = Primary.copy(alpha = 0.3f)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.settings_add_provider),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp)
                )
            }
        }
    }
}

@Composable
private fun MobileProviderCard(
    provider: Provider,
    isActive: Boolean,
    isSyncing: Boolean,
    onClick: () -> Unit,
    onRefresh: () -> Unit = {}
) {
    val expirationDate = provider.expirationDate
    val expirationText = when (expirationDate) {
        null -> stringResource(R.string.settings_provider_expiry_unknown)
        Long.MAX_VALUE -> stringResource(R.string.settings_provider_expiry_never)
        else -> stringResource(
            R.string.settings_provider_expires,
            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(expirationDate))
        )
    }
    val isExpired = expirationDate != null &&
        expirationDate != Long.MAX_VALUE &&
        expirationDate < System.currentTimeMillis()

    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) SurfaceHighlight else SurfaceElevated,
            focusedContainerColor = AppColors.SurfaceEmphasis
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = OnBackground,
                        maxLines = 1
                    )
                    Text(
                        text = providerTypeLabel(provider.type),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                }
                ProviderStatusBadge(status = provider.status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = when {
                        isSyncing -> stringResource(R.string.settings_provider_syncing)
                        isActive -> stringResource(R.string.settings_active)
                        else -> providerStatusLabel(provider.status)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) Primary else OnSurface
                )
                Spacer(modifier = Modifier.size(1.dp))
                Text(
                    text = expirationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isExpired) ErrorColor else OnSurfaceDim
                )
                Spacer(modifier = Modifier.weight(1f))
                // Manual playlist refresh: re-syncs every section of this provider.
                TvClickableSurface(
                    onClick = onRefresh,
                    enabled = !isSyncing,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Primary.copy(alpha = 0.14f),
                        focusedContainerColor = Primary.copy(alpha = 0.30f)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = if (isSyncing) OnSurfaceDim else Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(
                                if (isSyncing) R.string.settings_provider_syncing
                                else R.string.settings_provider_refresh
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSyncing) OnSurfaceDim else Primary,
                            maxLines = 1
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_provider_manage),
                style = MaterialTheme.typography.labelMedium,
                color = Primary
            )
        }
    }
}

@Composable
private fun providerTypeLabel(type: ProviderType): String = stringResource(
    when (type) {
        ProviderType.XTREAM_CODES -> R.string.settings_provider_type_xtream
        ProviderType.M3U -> R.string.settings_provider_type_m3u
        ProviderType.STALKER_PORTAL -> R.string.settings_provider_type_stalker
        ProviderType.JELLYFIN -> R.string.settings_provider_type_jellyfin
    }
)

@Composable
private fun providerStatusLabel(status: ProviderStatus): String = stringResource(
    when (status) {
        ProviderStatus.ACTIVE -> R.string.settings_status_active
        ProviderStatus.PARTIAL -> R.string.settings_status_partial
        ProviderStatus.EXPIRED -> R.string.settings_status_expired
        ProviderStatus.DISABLED -> R.string.settings_status_disabled
        ProviderStatus.ERROR -> R.string.settings_status_error
        ProviderStatus.UNKNOWN -> R.string.settings_status_unknown
    }
)
