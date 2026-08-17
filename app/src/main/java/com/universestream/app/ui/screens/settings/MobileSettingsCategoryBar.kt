package com.universestream.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.universestream.app.R
import com.universestream.app.ui.design.AppColors

@Composable
internal fun MobileSettingsCategoryBar(
    selectedCategory: Int,
    onCategorySelected: (Int) -> Unit
) {
    val labels = listOf(
        stringResource(R.string.settings_providers),
        stringResource(R.string.settings_playback),
        stringResource(R.string.settings_browsing),
        stringResource(R.string.settings_privacy),
        stringResource(R.string.settings_recording_title),
        stringResource(R.string.settings_backup_restore),
        stringResource(R.string.settings_sync_option_epg),
        stringResource(R.string.settings_about)
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(labels) { index, label ->
            TextButton(onClick = { onCategorySelected(index) }) {
                Text(
                    text = label,
                    color = if (selectedCategory == index) AppColors.Focus else AppColors.TextSecondary
                )
            }
        }
    }
}
