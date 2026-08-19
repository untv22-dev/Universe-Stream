package com.universestream.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.roundToPx
import androidx.compose.ui.platform.LocalDensity
import com.universestream.app.ui.accessibility.rememberReducedMotionEnabled
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun rememberCrossfadeImageModel(
    data: Any?,
    targetSizeDp: Dp? = null
): Any? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val reducedMotionEnabled = rememberReducedMotionEnabled()
    val targetSizePx = targetSizeDp?.let { with(density) { it.roundToPx() } }
    return remember(context, data, reducedMotionEnabled, targetSizePx) {
        data?.let {
            ImageRequest.Builder(context)
                .data(it)
                .apply {
                    targetSizePx?.let { size(it, it) }
                }
                .crossfade(!reducedMotionEnabled)
                .build()
        }
    }
}