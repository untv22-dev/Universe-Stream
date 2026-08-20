package com.universestream.data.sync

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

fun Context.isTelevisionDeviceForSync(): Boolean {
    val packageManager = packageManager
    if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
    if (packageManager.hasSystemFeature("android.software.leanback_only")) return true
    if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) return true
    if (packageManager.hasSystemFeature("amazon.hardware.fire_tv")) return true
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val screenWidthDp = resources.configuration.screenWidthDp
    return !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) && screenWidthDp >= 900
}
