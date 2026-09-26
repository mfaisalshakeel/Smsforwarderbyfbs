package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat

data class SimInfo(
    /** 1-based slot number as the user sees it, or 0 when unknown. */
    val slot: Int,
    val displayName: String
)

/**
 * Resolves which SIM an SMS arrived on.
 *
 * The original code read the undocumented `simSlot`/`slot`/`phone` intent extras, which most
 * modern devices do not set, so every message looked like it came from an unknown SIM and the
 * per-SIM rule filter silently never matched. The platform way is the `subscription` extra fed
 * into [SubscriptionManager].
 */
object SimHelper {

    private const val TAG = "SimHelper"

    fun fromIntent(context: Context, intent: Intent): SimInfo {
        val subscriptionId = intent.getIntExtra(
            "subscription",
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )
        if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            resolveBySubscriptionId(context, subscriptionId)?.let { return it }
        }

        // Fall back to the OEM extras some older ROMs still use.
        val rawSlot = intent.getIntExtra("simSlot", intent.getIntExtra("slot", intent.getIntExtra("phone", -1)))
        return when (rawSlot) {
            0 -> SimInfo(1, "SIM 1")
            1 -> SimInfo(2, "SIM 2")
            else -> SimInfo(0, "")
        }
    }

    private fun resolveBySubscriptionId(context: Context, subscriptionId: Int): SimInfo? {
        if (!hasPhoneStatePermission(context)) return null
        return try {
            val manager = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
                ?: return null
            val info = manager.getActiveSubscriptionInfo(subscriptionId) ?: return null
            val label = listOfNotNull(
                info.displayName?.toString()?.takeIf { it.isNotBlank() },
                info.carrierName?.toString()?.takeIf { it.isNotBlank() }
            ).firstOrNull() ?: "SIM ${info.simSlotIndex + 1}"
            SimInfo(slot = info.simSlotIndex + 1, displayName = label)
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_PHONE_STATE not granted; cannot identify the SIM")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Unable to resolve subscription $subscriptionId", e)
            null
        }
    }

    /** SIM labels for the rule editor, so the user picks a carrier rather than a slot number. */
    fun activeSims(context: Context): List<SimInfo> {
        if (!hasPhoneStatePermission(context)) return emptyList()
        return try {
            val manager = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
                ?: return emptyList()
            manager.activeSubscriptionInfoList.orEmpty().map { info ->
                SimInfo(
                    slot = info.simSlotIndex + 1,
                    displayName = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                        ?: info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                        ?: "SIM ${info.simSlotIndex + 1}"
                )
            }.sortedBy { it.slot }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to list active SIMs", e)
            emptyList()
        }
    }

    fun isDualSimDevice(context: Context): Boolean = activeSims(context).size > 1

    private fun hasPhoneStatePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    /** True when the device reports more than one SIM slot, regardless of permissions. */
    fun deviceSupportsMultipleSims(context: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
            (manager?.activeSubscriptionInfoCountMax ?: 1) > 1
        } else {
            true
        }
    } catch (e: Exception) {
        true
    }
}
