// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
)

/** Reads TV apps from the PackageManager. Nothing leaves the device. */
class InstalledAppsRepository(private val context: Context) {

    suspend fun load(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        queryActivities(pm, intent)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { info -> toInstalledApp(pm, info) }
            .toList()
    }

    fun isInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun toInstalledApp(pm: PackageManager, info: ResolveInfo): InstalledApp? = try {
        val activity = info.activityInfo
        val banner = (activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm))
            ?.toBitmap(BANNER_WIDTH, BANNER_HEIGHT)
            ?.asImageBitmap()
        val icon = info.loadIcon(pm)?.toBitmap(ICON_SIZE, ICON_SIZE)?.asImageBitmap()
        InstalledApp(activity.packageName, info.loadLabel(pm).toString(), banner, icon)
    } catch (e: RuntimeException) {
        // A broken app resource must not take the home screen down with it.
        Log.w(TAG, "Skipping app ${info.activityInfo.packageName}: ${e.javaClass.simpleName}")
        null
    }

    private fun queryActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private companion object {
        const val TAG = "InstalledApps"
        const val BANNER_WIDTH = 320
        const val BANNER_HEIGHT = 180
        const val ICON_SIZE = 96
    }
}
