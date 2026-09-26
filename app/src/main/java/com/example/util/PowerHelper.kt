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
     * This app's notification settings. Needed when notifications are blocked, because the
     * ongoing notification is what lets the foreground service keep running at all.
     */
    fun appNotificationSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            appSettingsIntent(context)
        }

    /** Where each manufacturer hides its own background-app switch. */
    fun autostartInstructions(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
                manufacturer.contains("poco") ->
                "Settings > Apps > Manage apps > SMS Forwarder > Autostart (turn on), then " +
                    "Battery saver > No restrictions."
            manufacturer.contains("oppo") || manufacturer.contains("realme") ->
                "Settings > Battery > App battery management > SMS Forwarder > Allow background " +
                    "activity, and turn on Auto-launch."
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
                "Settings > Battery > Background power consumption management > SMS Forwarder > " +
                    "Allow high background power consumption."
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                "Settings > Battery > App launch > SMS Forwarder > Manage manually, and turn on " +
                    "all three switches."
            manufacturer.contains("samsung") ->
                "Settings > Battery > Background usage limits > make sure SMS Forwarder is not in " +
                    "\"Sleeping apps\" or \"Deep sleeping apps\"."
            manufacturer.contains("oneplus") ->
                "Settings > Battery > Battery optimisation > SMS Forwarder > Don't optimise, and " +
                    "turn off Advanced optimisation."
            else ->
                "Open your phone's battery or security settings and allow SMS Forwarder to run in " +
                    "the background and start automatically."
        }
    }

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
