package com.universestream.app.ui.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AppSpacing(
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 40.dp,
    val screenGutter: Dp = 56.dp,
    val railWidth: Dp = 220.dp,
    val sectionGap: Dp = 32.dp,
    val cardGap: Dp = 16.dp,
    val chipGap: Dp = 10.dp,
    val safeTop: Dp = 32.dp,
    val safeBottom: Dp = 32.dp,
    val safeHoriz: Dp = 56.dp
) {
    companion object {
        fun forWindowSizeClass(windowSizeClass: AppWindowSizeClass): AppSpacing = when (windowSizeClass) {
            AppWindowSizeClass.Television -> AppSpacing()
            AppWindowSizeClass.Expanded -> AppSpacing(
                screenGutter = 40.dp,
                railWidth = 112.dp,
                sectionGap = 28.dp,
                safeTop = 24.dp,
                safeBottom = 24.dp,
                safeHoriz = 40.dp
            )
            AppWindowSizeClass.Medium -> AppSpacing(
                lg = 20.dp,
                xl = 24.dp,
                xxl = 32.dp,
                screenGutter = 24.dp,
                railWidth = 96.dp,
                sectionGap = 24.dp,
                cardGap = 12.dp,
                safeTop = 16.dp,
                safeBottom = 16.dp,
                safeHoriz = 24.dp
            )
            AppWindowSizeClass.Compact -> AppSpacing(
                xs = 6.dp,
                sm = 8.dp,
                md = 12.dp,
                lg = 16.dp,
                xl = 20.dp,
                xxl = 24.dp,
                screenGutter = 16.dp,
                railWidth = 0.dp,
                sectionGap = 20.dp,
                cardGap = 10.dp,
                chipGap = 8.dp,
                safeTop = 10.dp,
                safeBottom = 10.dp,
                safeHoriz = 16.dp
            )
        }
    }
}

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
