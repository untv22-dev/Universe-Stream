package com.universestream.app.ui.components.shell

import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.universestream.app.R
import com.universestream.app.MainActivity
import com.universestream.app.navigation.toAppRoute
import com.universestream.app.navigation.Routes
import com.universestream.app.ui.design.AppColors
import com.universestream.app.ui.design.AppMotion
import com.universestream.app.ui.design.AppSpacing
import com.universestream.app.ui.design.AppWindowSizeClass
import com.universestream.app.ui.design.FocusSpec
import com.universestream.app.ui.design.rememberAppWindowSizeClass
import com.universestream.app.ui.interaction.mouseClickable
import com.universestream.app.ui.interaction.rememberTvInteractionSounds
import com.universestream.app.ui.interaction.TvIconButton
import com.universestream.app.ui.design.LocalAppShapes
import com.universestream.app.ui.design.LocalAppSpacing
import com.universestream.domain.model.AppTopLevelDestination

enum class AppNavigationChrome {
    Auto,
    Rail,
    TopBar
}

/**
 * Status bar plus display cutout, excluding the bottom edge.
 *
 * Applied only on the phone and tablet branches of [AppScreenScaffold]; the television
 * rail branch is left untouched because a TV reports no cutout and overscan is handled
 * by the launcher. The bottom side is omitted on purpose so it does not stack with the
 * `navigationBarsPadding()` the navigation bars already apply.
 */
private val MobileContentInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

@Composable
fun AppScreenScaffold(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    navigationChrome: AppNavigationChrome = AppNavigationChrome.Auto,
    topBarVisible: Boolean = true,
    compactHeader: Boolean = false,
    showScreenHeader: Boolean = true,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    topBarActions: (@Composable RowScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit
) {
    val windowSizeClass = rememberAppWindowSizeClass()
    val spacing = AppSpacing.forWindowSizeClass(windowSizeClass)
    val resolvedChrome = when {
        windowSizeClass == AppWindowSizeClass.Television -> AppNavigationChrome.Rail
        navigationChrome == AppNavigationChrome.Rail -> AppNavigationChrome.Rail
        else -> AppNavigationChrome.TopBar
    }
    val compactPhone = windowSizeClass == AppWindowSizeClass.Compact

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        AppColors.Canvas,
                        AppColors.CanvasElevated,
                        AppColors.Surface
                    )
                )
            )
    ) {
        if (resolvedChrome == AppNavigationChrome.Rail) {
            Row(modifier = Modifier.fillMaxSize()) {
                DestinationRail(
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(spacing.railWidth)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = spacing.lg,
                            end = spacing.screenGutter,
                            top = spacing.safeTop,
                            bottom = spacing.safeBottom
                        )
                ) {
                    if (showScreenHeader) {
                        AppScreenHeader(
                            title = title,
                            subtitle = subtitle,
                            modifier = Modifier.fillMaxWidth(),
                            compact = compactHeader
                        )
                        if (header != null) {
                            Spacer(modifier = Modifier.height(spacing.lg))
                            header()
                        }
                        Spacer(modifier = Modifier.height(spacing.lg))
                    } else if (header != null) {
                        header()
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                    ) {
                        content()
                    }
                }
            }
        } else if (compactPhone) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Edge-to-edge is enabled in MainActivity and enforced by the platform at
                    // this targetSdk, so the status bar and any display cutout overlap the
                    // content unless it is inset here. The bottom is deliberately excluded:
                    // the navigation bars already apply navigationBarsPadding() themselves.
                    .windowInsetsPadding(MobileContentInsets)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(
                            start = spacing.screenGutter,
                            end = spacing.screenGutter,
                            top = spacing.safeTop,
                            bottom = spacing.xs
                        )
                ) {
                    if (showScreenHeader) {
                        AppScreenHeader(
                            title = title,
                            subtitle = subtitle,
                            modifier = Modifier.fillMaxWidth(),
                            compact = true
                        )
                        if (header != null) {
                            Spacer(modifier = Modifier.height(spacing.sm))
                            header()
                        }
                        Spacer(modifier = Modifier.height(spacing.sm))
                    } else if (header != null) {
                        header()
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                    ) {
                        content()
                    }
                }
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    actions = topBarActions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenGutter, vertical = spacing.xs)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Medium/Expanded here is a phone in landscape or a tablet, never a
                    // television: the Rail branch above claims every TV. In landscape the
                    // cutout sits on a side edge, so horizontal insets matter as much as top.
                    .windowInsetsPadding(MobileContentInsets)
                    .padding(
                        horizontal = spacing.screenGutter,
                        vertical = spacing.safeTop
                    )
            ) {
                if (topBarVisible) {
                    TopNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = onNavigate,
                        actions = topBarActions,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(spacing.sm))
                }
                if (showScreenHeader) {
                    AppScreenHeader(
                        title = title,
                        subtitle = subtitle,
                        modifier = Modifier.fillMaxWidth(),
                        compact = true
                    )
                    if (header != null) {
                        Spacer(modifier = Modifier.height(spacing.xs))
                        header()
                    }
                    Spacer(modifier = Modifier.height(spacing.xs))
                } else if (header != null) {
                    header()
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun AppScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    compact: Boolean = false
) {
    // `compact` is not a phone flag: the television rail passes compactHeader through it
    // too, so the size bump below is gated on the window size class instead. Television,
    // tablets and the rail keep the title style they already had.
    val phoneCompact = compact && rememberAppWindowSizeClass() == AppWindowSizeClass.Compact
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!eyebrow.isNullOrBlank()) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.Brand
            )
        }
        Text(
            text = title,
            // On a phone, titleMedium is also what card titles use, which left a screen
            // title and the cards under it reading at the same level. headlineSmall is one
            // step up at 18sp/SemiBold and costs 2sp of height.
            style = when {
                phoneCompact -> MaterialTheme.typography.headlineSmall
                compact -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.displaySmall
            },
            color = AppColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
                color = AppColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TopNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val items = rememberDestinationItems()
    val scrollState = rememberScrollState()

    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    
    Surface(
        modifier = modifier.focusProperties {
            onEnter = {
                val activeItem = findActiveDestinationItem(items, currentRoute)
                focusRequesters[activeItem?.route] ?: FocusRequester.Default
            }
        },
        shape = RoundedCornerShape(18.dp),
        colors = SurfaceDefaults.colors(containerColor = AppColors.Surface.copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.TextPrimary,
                modifier = Modifier.wrapContentWidth(Alignment.Start)
            )
            Spacer(modifier = Modifier.width(32.dp)) // Increased spacing to prevent overlap
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items.forEach { item ->
                    val requester = focusRequesters.getOrPut(item.route) { FocusRequester() }
                    TopNavigationButton(
                        label = stringResource(item.labelRes),
                        icon = item.icon,
                        selected = currentRoute.startsWith(item.route),
                        focusRequester = requester,
                        onClick = {
                            if (!currentRoute.startsWith(item.route)) {
                                onNavigate(item.route)
                            }
                        }
                    )
                }
            }
            if (actions != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val items = rememberDestinationItems()
    if (rememberAppWindowSizeClass() == AppWindowSizeClass.Compact) {
        CompactBottomNavigationBar(
            items = items,
            currentRoute = currentRoute,
            onNavigate = onNavigate,
            actions = actions,
            modifier = modifier
        )
        return
    }
    val scrollState = rememberScrollState()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    Surface(
        modifier = modifier.navigationBarsPadding(),
        shape = RoundedCornerShape(18.dp),
        colors = SurfaceDefaults.colors(containerColor = AppColors.Surface.copy(alpha = 0.96f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .horizontalScroll(scrollState)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val requester = focusRequesters.getOrPut(item.route) { FocusRequester() }
                TopNavigationButton(
                    label = stringResource(item.labelRes),
                    icon = item.icon,
                    selected = currentRoute.startsWith(item.route),
                    focusRequester = requester,
                    modifier = Modifier.heightIn(min = 52.dp),
                    useMouseSupport = false,
                    onClick = {
                        if (!currentRoute.startsWith(item.route)) {
                            onNavigate(item.route)
                        }
                    }
                )
            }
            if (actions != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

@Composable
private fun CompactBottomNavigationBar(
    items: List<DestinationItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    actions: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val navigationScrollState = rememberScrollState()
    Surface(
        modifier = modifier.navigationBarsPadding(),
        shape = RoundedCornerShape(18.dp),
        colors = SurfaceDefaults.colors(containerColor = AppColors.Surface.copy(alpha = 0.98f))
    ) {
        BoxWithConstraints {
            // A fixed item width made the bar scroll horizontally on every phone: nine
            // destinations at 68dp need ~630dp against a ~360dp screen, so most tabs sat
            // off-screen with no affordance pointing to them. Measure instead, and only
            // fall back to scrolling when the destinations genuinely cannot fit.
            val actionsWidth = if (actions != null) CompactNavActionsWidth else 0.dp
            val available = maxWidth - CompactNavBarPadding * 2 - actionsWidth
            val gap = CompactNavItemGap
            val naturalWidth = CompactNavItemMinWidth * items.size + gap * (items.size - 1).coerceAtLeast(0)
            val fits = items.isNotEmpty() && naturalWidth <= available

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fits) Modifier else Modifier.horizontalScroll(navigationScrollState))
                    .padding(horizontal = CompactNavBarPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val selected = currentRoute.startsWith(item.route)
                    Column(
                        modifier = Modifier
                            .then(
                                // weight() only exists for the non-scrolling case; inside a
                                // horizontal scroll the row is measured with infinite width.
                                if (fits) Modifier.weight(1f) else Modifier.width(CompactNavItemMinWidth)
                            )
                            .heightIn(min = CompactNavItemMinHeight)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (!selected) onNavigate(item.route)
                            }
                            .padding(horizontal = 2.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = stringResource(item.labelRes),
                            tint = if (selected) AppColors.Brand else AppColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(item.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (actions != null) {
                    Row(
                        modifier = Modifier.widthIn(min = 56.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
        }
    }
}

/**
 * Compact bottom-navigation metrics.
 *
 * The minimum height clears the 48dp touch target Material asks for, which the previous
 * 56dp box met only because of its padding; the width floor is what a two-word label
 * needs before it starts truncating.
 */
private val CompactNavItemMinWidth = 64.dp
private val CompactNavItemMinHeight = 56.dp
private val CompactNavItemGap = 2.dp
private val CompactNavBarPadding = 4.dp
private val CompactNavActionsWidth = 56.dp

@Composable
fun AppTopBarCloseAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.settings_close_app)
) {
    TvIconButton(
        onClick = onClick,
        modifier = modifier,
        colors = androidx.tv.material3.IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = AppColors.SurfaceEmphasis,
            contentColor = AppColors.TextSecondary,
            focusedContentColor = AppColors.TextPrimary
        ),
        border = androidx.tv.material3.IconButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(FocusSpec.BorderWidth, AppColors.Focus),
                shape = RoundedCornerShape(14.dp)
            )
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TopNavigationButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    useMouseSupport: Boolean = true,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val sounds = rememberTvInteractionSounds()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) FocusSpec.FocusedScale else 1f,
        animationSpec = AppMotion.FocusSpec,
        label = "topNavScale"
    )

    Surface(
        onClick = {
            sounds.playSelect()
            onClick()
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .then(
                if (useMouseSupport) {
                    Modifier.mouseClickable(
                        focusRequester = focusRequester,
                        onClick = {
                            sounds.playSelect()
                            onClick()
                        }
                    )
                } else {
                    Modifier
                }
            )
            .zIndex(if (isFocused) 1f else 0f) // Keep focused button on top
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                if (it.isFocused && !isFocused) {
                    sounds.playNavigate()
                }
                isFocused = it.isFocused
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) AppColors.BrandMuted else Color.Transparent,
            focusedContainerColor = AppColors.SurfaceEmphasis
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(FocusSpec.BorderWidth, AppColors.Focus),
                shape = RoundedCornerShape(14.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) AppColors.Brand else AppColors.TextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary
            )
        }
    }
}

@Composable
fun AppHeroHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = SurfaceDefaults.colors(containerColor = AppColors.SurfaceElevated)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            AppColors.Canvas,
                            AppColors.SurfaceAccent,
                            AppColors.SurfaceEmphasis
                        )
                    )
                )
                .padding(32.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                AppScreenHeader(
                    title = title,
                    subtitle = subtitle,
                    eyebrow = eyebrow
                )
                if (actions != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
                if (footer != null) {
                    footer()
                }
            }
        }
    }
}

@Composable
fun AppSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionContentColor: Color = AppColors.TextTertiary
) {
    val shapes = LocalAppShapes.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = if (onActionClick != null && !actionLabel.isNullOrBlank()) Modifier.weight(1f) else Modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary
                )
            }
        }

        if (onActionClick != null && !actionLabel.isNullOrBlank()) {
            val actionFocusRequester = remember { FocusRequester() }
            Surface(
                onClick = onActionClick,
                modifier = Modifier
                    .focusRequester(actionFocusRequester)
                    .mouseClickable(
                        focusRequester = actionFocusRequester,
                        onClick = onActionClick
                    ),
                shape = ClickableSurfaceDefaults.shape(shapes.pill),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = AppColors.Brand.copy(alpha = 0.12f),
                    focusedContainerColor = AppColors.Brand.copy(alpha = 0.22f),
                    contentColor = actionContentColor
                )
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = AppColors.SurfaceEmphasis,
    contentColor: Color = AppColors.TextPrimary,
    cornerRadius: Dp = 999.dp,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 4.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

@Composable
fun AppMessageState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    shape: RoundedCornerShape? = null,
    containerBrush: Brush? = null,
    borderColor: Color? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodySmall,
    titleColor: Color = AppColors.TextPrimary,
    subtitleColor: Color = AppColors.TextSecondary,
    titleTextAlign: TextAlign = TextAlign.Start,
    subtitleTextAlign: TextAlign = TextAlign.Start
) {
    val resolvedShape = shape ?: LocalAppShapes.current.large
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = resolvedShape,
        border = Border(
            border = BorderStroke(
                width = if (borderColor != null) 1.dp else 0.dp,
                color = borderColor ?: Color.Transparent
            ),
            shape = resolvedShape
        ),
        colors = SurfaceDefaults.colors(containerColor = AppColors.SurfaceElevated)
    ) {
        Column(
            modifier = Modifier
                .then(if (containerBrush != null) Modifier.background(containerBrush) else Modifier)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = titleColor,
                textAlign = titleTextAlign,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = subtitle,
                style = subtitleStyle,
                color = subtitleColor,
                textAlign = subtitleTextAlign,
                modifier = Modifier.fillMaxWidth()
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(8.dp))
                action()
            }
        }
    }
}

@Composable
fun LoadMoreCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shapes = LocalAppShapes.current
    val focusRequester = remember { FocusRequester() }
    Surface(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focusRequester)
            .mouseClickable(
                focusRequester = focusRequester,
                onClick = onClick
            ),
        shape = ClickableSurfaceDefaults.shape(shapes.medium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.SurfaceElevated,
            focusedContainerColor = AppColors.SurfaceEmphasis
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(FocusSpec.BorderWidth, AppColors.Focus),
                shape = shapes.medium
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = label,
                tint = AppColors.Brand,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary
            )
        }
    }
}

@Composable
fun ContentMetadataStrip(
    values: List<String>,
    modifier: Modifier = Modifier
) {
    val filteredValues = values.filter { it.isNotBlank() }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filteredValues.forEachIndexed { index, value ->
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextSecondary
            )
            if (index < filteredValues.lastIndex) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(AppColors.TextTertiary)
                )
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun DestinationRail(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val items = rememberDestinationItems()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val railScrollState = rememberScrollState()

    Box(
        modifier = modifier
            .padding(start = spacing.lg, top = spacing.safeTop, bottom = spacing.safeBottom)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AppColors.SurfaceElevated,
                        AppColors.Surface
                    )
                )
            )
            .focusProperties {
                onEnter = {
                    val activeItem = findActiveDestinationItem(items, currentRoute)
                    focusRequesters[activeItem?.route] ?: FocusRequester.Default
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(railScrollState)
                .padding(horizontal = 12.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.label_tv),
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextTertiary
            )
            Spacer(modifier = Modifier.height(10.dp))
            items.forEach { item ->
                val requester = focusRequesters.getOrPut(item.route) { FocusRequester() }
                RailButton(
                    label = stringResource(item.labelRes),
                    icon = item.icon,
                    selected = currentRoute.startsWith(item.route),
                    focusRequester = requester,
                    onClick = {
                        if (!currentRoute.startsWith(item.route)) {
                            onNavigate(item.route)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RailButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val focusScope = rememberCoroutineScope()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) FocusSpec.FocusedScale else 1f,
        animationSpec = AppMotion.FocusSpec,
        label = "railButtonScale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focusRequester)
            .bringIntoViewRequester(bringIntoViewRequester)
            .mouseClickable(
                focusRequester = focusRequester,
                onClick = onClick
            )
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    focusScope.launch {
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) AppColors.BrandMuted else Color.Transparent,
            focusedContainerColor = AppColors.SurfaceEmphasis
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(FocusSpec.BorderWidth, AppColors.Focus),
                shape = RoundedCornerShape(18.dp)
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) AppColors.Brand else AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class DestinationItem(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
)

private fun findActiveDestinationItem(
    items: List<DestinationItem>,
    currentRoute: String
): DestinationItem? =
    items
        .filter { currentRoute.startsWith(it.route) }
        .maxByOrNull { it.route.length }
        ?: items.firstOrNull { it.route == currentRoute }

private fun buildDestinationItems(): List<DestinationItem> =
    AppTopLevelDestination.defaultOrder.map { it.toDestinationItem() }

@Composable
private fun rememberDestinationItems(): List<DestinationItem> {
    val context = LocalContext.current
    val mainActivity = remember(context) { context.findMainActivity() }
    val configuredDestinations = mainActivity?.preferencesRepository?.appTopLevelDestinations
        ?.collectAsStateWithLifecycle(initialValue = AppTopLevelDestination.defaultOrder)
        ?.value
        ?: AppTopLevelDestination.defaultOrder
    return remember(configuredDestinations) {
        configuredDestinations.map { it.toDestinationItem() }
    }
}

private fun AppTopLevelDestination.toDestinationItem(): DestinationItem = when (this) {
    AppTopLevelDestination.HOME -> DestinationItem(Routes.HOME, R.string.nav_home, Icons.Default.Home)
    AppTopLevelDestination.LIVE_TV -> DestinationItem(Routes.LIVE_TV, R.string.nav_live_tv, Icons.Default.PlayArrow)
    AppTopLevelDestination.MOVIES -> DestinationItem(Routes.MOVIES, R.string.nav_movies, Icons.Default.Star)
    AppTopLevelDestination.SERIES -> DestinationItem(Routes.SERIES, R.string.nav_series, Icons.Default.Menu)
    AppTopLevelDestination.DOWNLOADS -> DestinationItem(Routes.DOWNLOADS, R.string.nav_downloads, Icons.Default.Download)
    AppTopLevelDestination.GUIDE -> DestinationItem(Routes.EPG, R.string.nav_epg, Icons.Default.Info)
    AppTopLevelDestination.SEARCH -> DestinationItem(Routes.SEARCH, R.string.search_title, Icons.Default.Search)
    AppTopLevelDestination.PLUGINS -> DestinationItem(Routes.PLUGINS, R.string.nav_plugins, PluginBlocksIcon)
    AppTopLevelDestination.SETTINGS -> DestinationItem(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings)
}

private fun Context.findMainActivity(): MainActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is MainActivity) return current
        current = current.baseContext
    }
    return null
}

private val PluginBlocksIcon: ImageVector
    get() {
        if (_pluginBlocksIcon != null) return _pluginBlocksIcon!!
        _pluginBlocksIcon = ImageVector.Builder(
            name = "PluginBlocks",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 4f)
                horizontalLineTo(10f)
                verticalLineTo(11f)
                horizontalLineTo(3f)
                close()
                moveTo(14f, 4f)
                horizontalLineTo(21f)
                verticalLineTo(11f)
                horizontalLineTo(14f)
                close()
                moveTo(8.5f, 13f)
                horizontalLineTo(15.5f)
                verticalLineTo(20f)
                horizontalLineTo(8.5f)
                close()
            }
        }.build()
        return _pluginBlocksIcon!!
    }

private var _pluginBlocksIcon: ImageVector? = null
