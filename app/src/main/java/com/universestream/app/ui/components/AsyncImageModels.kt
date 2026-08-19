package com.universestream.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.universestream.app.ui.accessibility.rememberReducedMotionEnabled
import kotlin.math.roundToInt

@Composable
fun rememberCrossfadeImageModel(
    data: Any?,
    targetSizeDp: Dp? = null
): Any? {
    val context = LocalContext.current
    val density = LocalDensity.current
    val reducedMotionEnabled = rememberReducedMotionEnabled()
    val targetSizePx = targetSizeDp?.let {
        (it.value * density.density).roundToInt()
    }
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
