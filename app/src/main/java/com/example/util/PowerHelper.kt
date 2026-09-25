package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Battery optimisation is the difference between a forwarder that works for weeks and one that
 * dies overnight. Nothing in the app asked for the exemption before.
 */
object PowerHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val manager = ContextCompat.getSystemService(context, PowerManager::class.java) ?: return true
        return manager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Intent that asks the user to exempt this app. Returns null when the exemption is already
     * granted or the platform is too old to need it.
     */
    fun buildExemptionIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        if (isIgnoringBatteryOptimizations(context)) return null
        @Suppress("BatteryLife")
        return Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
    }

    /** The system's battery-optimisation list, used as a fallback when the direct ask is blocked. */
    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** This app's own settings page, for permissions the user denied permanently. */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun notificationAccessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /**
     * Xiaomi, Oppo, Vivo and their relatives freeze background apps regardless of the platform
     * exemption, and each hides the switch in a different place.
     */
    fun manufacturerNeedsAutostart(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return AGGRESSIVE_MANUFACTURERS.any { manufacturer.contains(it) }
    }

    private val AGGRESSIVE_MANUFACTURERS = listOf(
        "xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "iqoo",
        "oneplus", "huawei", "honor", "meizu", "asus", "letv", "samsung"
    )
}
