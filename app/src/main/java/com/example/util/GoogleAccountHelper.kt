package com.example.util

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.util.Log

object GoogleAccountHelper {
    private const val TAG = "GoogleAccountHelper"

    /**
     * Creates an Intent to launch the native Android system Google account chooser.
     * This displays the device's Google accounts in a native system dialog.
     */
    fun createGoogleAccountPickerIntent(): Intent {
        return AccountManager.newChooseAccountIntent(
            null, // selectedAccount
            null, // allowableAccounts
            arrayOf("com.google"), // allowableAccountTypes
            null, // descriptionOverrideText
            null, // addAccountAuthTokenType
            null, // addAccountRequiredFeatures
            null  // addAccountOptions
        )
    }

    /**
     * Retrieves any accessible Google accounts registered on the device.
     */
    fun getDeviceGoogleAccounts(context: Context): List<String> {
        return try {
            val accountManager = AccountManager.get(context)
            val accounts = accountManager.getAccountsByType("com.google")
            accounts.map { it.name }.filter { it.isNotBlank() }
        } catch (e: SecurityException) {
            Log.w(TAG, "GET_ACCOUNTS permission not granted or restricted: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching device accounts", e)
            emptyList()
        }
    }
}
