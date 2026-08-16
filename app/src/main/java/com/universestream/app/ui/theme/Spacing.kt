package com.universestream.app.ui.theme

import com.universestream.app.ui.design.AppSpacing
import com.universestream.app.ui.design.LocalAppSpacing

typealias Spacing = AppSpacing

val LocalSpacing = LocalAppSpacing

fun defaultSpacing(): Spacing = AppSpacing()
