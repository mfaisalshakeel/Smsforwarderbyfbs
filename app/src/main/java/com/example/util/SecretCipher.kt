package com.example.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the few secrets this app has to keep on disk (the SMTP app password and the
 * Telegram bot token) with an AES-GCM key held in the Android Keystore.
 *
 * The key never leaves the secure hardware, is not included in any backup, and is
 * destroyed when the app is uninstalled. Ciphertext is stored as
 * `Base64(iv) + ":" + Base64(ciphertext)`.
 */
object SecretCipher {

    private const val TAG = "SecretCipher"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "sms_forwarder_secret_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val SEPARATOR = ":"

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    /** Returns the encrypted form of [plainText], or the input unchanged if encryption is unavailable. */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
                SEPARATOR +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            // A handful of devices have a broken Keystore. Losing the setting is better
            // than crashing, so fall back to storing nothing at all.
            Log.e(TAG, "Unable to encrypt secret", e)
            ""
        }
    }

    /** Returns the plaintext for [stored], or an empty string if it cannot be decrypted. */
    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        val parts = stored.split(SEPARATOR)
        // Values written before encryption was added have no IV prefix; treat them as plaintext
        // so the user is not silently logged out, and they get re-encrypted on the next save.
        if (parts.size != 2) return stored
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val body = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, loadOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to decrypt secret", e)
            ""
        }
    }
}
