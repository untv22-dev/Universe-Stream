package com.universestream.app.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.universestream.app.device.isTelevisionDevice

/**
 * Responsive layout buckets used by the shared TV/mobile Compose shell.
 *
 * Television is resolved before width so that large touchless devices keep the
 * D-pad-first rail even when their reported width falls into Expanded.
 */
enum class AppWindowSizeClass {
    Compact,
    Medium,
    Expanded,
    Television
}

@Composable
fun rememberAppWindowSizeClass(): AppWindowSizeClass {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val television = remember(context) { context.isTelevisionDevice() }
    val widthDp = configuration.screenWidthDp

    return when {
        television -> AppWindowSizeClass.Television
        widthDp < 600 -> AppWindowSizeClass.Compact
        widthDp < 840 -> AppWindowSizeClass.Medium
        else -> AppWindowSizeClass.Expanded
    }
}
