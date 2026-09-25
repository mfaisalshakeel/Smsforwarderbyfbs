package com.example.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    /** Pre-rasterised on a background thread; drawing hundreds of Drawables on the main thread janks. */
    val icon: ImageBitmap?,
    val isSystemApp: Boolean
)

/**
 * Supplies the installed-app list for the notification rule picker.
 *
 * Before this existed the user had to guess and type an app's display label into a free-text
 * field, which is why notification rules almost never matched.
 */
object AppInfoHelper {

    private const val TAG = "AppInfoHelper"
    private const val ICON_PX = 96

    suspend fun loadInstalledApps(
        context: Context,
        includeSystemApps: Boolean = false
    ): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.packageName != context.packageName }
                .filter { info ->
                    val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    // A system app that was updated (Messages, Phone) behaves like a user app.
                    val isUpdatedSystem = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    includeSystemApps || !isSystem || isUpdatedSystem
                }
                .map { info ->
                    InstalledApp(
                        packageName = info.packageName,
                        label = runCatching { pm.getApplicationLabel(info).toString() }
                            .getOrDefault(info.packageName),
                        icon = runCatching {
                            pm.getApplicationIcon(info).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
                        }.getOrNull(),
                        isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to list installed apps", e)
            emptyList()
        }
    }

    /** Resolves a package name to its display label, falling back to the package itself. */
    fun labelFor(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        packageName
    }
}
