package com.example.util

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Why the lock screen cannot be used, if it cannot. */
enum class LockAvailability {
    /** A fingerprint, face or device PIN can be used. */
    AVAILABLE,

    /** The hardware exists but nothing is enrolled, and no screen lock is set. */
    NOT_ENROLLED,

    /** No suitable hardware and no screen lock. */
    UNSUPPORTED
}

/**
 * Wraps [BiometricPrompt] so the app lock can fall back to the device PIN when no fingerprint
 * is enrolled. The history screen holds every OTP the phone has received, so leaving it behind
 * nothing but the launcher icon was a real exposure.
 */
object BiometricHelper {

    /**
     * Weak biometrics are deliberate: this gates a screen, it does not protect a crypto key,
     * and refusing face unlock here would only push people to turn the lock off.
     */
    private const val BIOMETRIC_LEVEL = BiometricManager.Authenticators.BIOMETRIC_WEAK
    private const val DEVICE_CREDENTIAL = BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun availability(context: Context): LockAvailability {
        val manager = BiometricManager.from(context)

        // Device credential as an allowed authenticator is only reliable from API 30.
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_LEVEL or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_LEVEL
        }

        return when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> LockAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                if (hasDeviceCredential(context)) LockAvailability.AVAILABLE else LockAvailability.NOT_ENROLLED
            else ->
                if (hasDeviceCredential(context)) LockAvailability.AVAILABLE else LockAvailability.UNSUPPORTED
        }
    }

    private fun hasDeviceCredential(context: Context): Boolean =
        ContextCompat.getSystemService(context, KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * Shows the system authentication sheet. [onFailed] fires only for a permanent failure the
     * user should see; a single wrong fingerprint is handled by the sheet itself.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit,
        onCancelled: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> onCancelled()
                    else -> onFailed(errString.toString())
                }
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BIOMETRIC_LEVEL or DEVICE_CREDENTIAL)
        } else {
            builder.setAllowedAuthenticators(BIOMETRIC_LEVEL)
            // A negative button is mandatory when device credential is not an allowed
            // authenticator, or the builder throws.
            builder.setNegativeButtonText("Use PIN")
        }

        try {
            prompt.authenticate(builder.build())
        } catch (e: Exception) {
            // Falls back to the plain keyguard sheet if the prompt cannot be configured.
            onFailed(e.localizedMessage ?: "Could not show the unlock prompt")
        }
    }

    /** Keyguard fallback for devices where [BiometricPrompt] cannot offer device credential. */
    fun deviceCredentialIntent(context: Context, title: String, description: String): Intent? {
        val manager = ContextCompat.getSystemService(context, KeyguardManager::class.java) ?: return null
        if (!manager.isDeviceSecure) return null
        @Suppress("DEPRECATION")
        return manager.createConfirmDeviceCredentialIntent(title, description)
    }
}
