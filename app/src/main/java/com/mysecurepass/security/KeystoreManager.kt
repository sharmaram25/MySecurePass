package com.mysecurepass.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed keys in the Android Keystore (TEE, or StrongBox where available).
 * Key material never leaves secure hardware; the app can only ask it to encrypt/decrypt.
 *
 * - **Device key**: wraps the vault file at rest. Binds the file to this device, so a copied file
 *   cannot be brute-forced offline even with a weak master password. No user auth required.
 * - **Biometric key**: wraps the vault key for fingerprint/face unlock. Requires a Class 3 (strong)
 *   biometric for every use and is destroyed automatically when biometrics are re-enrolled.
 */
enum class HardwareLevel(val label: String, val detail: String) {
    STRONGBOX("StrongBox", "Dedicated tamper-resistant security chip"),
    TEE("Secure hardware (TEE)", "Isolated Trusted Execution Environment"),
    SOFTWARE("Software only", "This device has no hardware key storage"),
    UNKNOWN("Unknown", "The device did not report its key storage"),
}

class KeystoreManager {
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun deviceEncrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey())
        return cipher.iv + cipher.doFinal(plaintext)
    }

    fun deviceDecrypt(blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES) { "Invalid device blob" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(128, blob, 0, IV_BYTES))
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    /**
     * Where the vault's device key physically lives, as reported by the Keystore itself.
     * Uses the existing device key if present, otherwise a short-lived probe key.
     */
    @Suppress("DEPRECATION") // isInsideSecureHardware is the only signal below API 31
    fun hardwareLevel(): HardwareLevel = runCatching {
        val existing = keyStore.getKey(DEVICE_ALIAS, null) as? SecretKey
        val key = existing ?: run {
            val spec = KeyGenParameterSpec.Builder(PROBE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build()
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run { init(spec); generateKey() }
        }
        val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> HardwareLevel.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> HardwareLevel.TEE
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> HardwareLevel.SOFTWARE
                else -> if (info.isInsideSecureHardware) HardwareLevel.TEE else HardwareLevel.UNKNOWN
            }
        } else {
            @Suppress("DEPRECATION")
            if (info.isInsideSecureHardware) HardwareLevel.TEE else HardwareLevel.SOFTWARE
        }
        if (existing == null) deleteKey(PROBE_ALIAS)
        level
    }.getOrDefault(HardwareLevel.UNKNOWN)

    /** Whether this device has a StrongBox secure element at all. */
    fun hasStrongBox(context: android.content.Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    /** End-to-end check: encrypt and decrypt through the Keystore with a throwaway key. */
    fun roundTripTest(): Boolean = runCatching {
        val spec = KeyGenParameterSpec.Builder(PROBE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build()
        val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run { init(spec); generateKey() }
        try {
            val enc = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
            val ct = enc.doFinal("keystore".toByteArray())
            val dec = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, enc.iv)) }
            dec.doFinal(ct).contentEquals("keystore".toByteArray())
        } finally {
            deleteKey(PROBE_ALIAS)
        }
    }.getOrDefault(false)

    /** Cipher for enrolling biometrics; must be authorised through BiometricPrompt before use. */
    fun biometricEncryptCipher(): Cipher {
        deleteKey(BIO_ALIAS)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, createBiometricKey())
        return cipher
    }

    /**
     * Cipher for biometric unlock, or null if the key was invalidated (biometrics changed) or is
     * missing — in that case the caller must fall back to the master password.
     */
    fun biometricDecryptCipher(iv: ByteArray): Cipher? = try {
        val key = keyStore.getKey(BIO_ALIAS, null) as? SecretKey ?: return null
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
    } catch (e: KeyPermanentlyInvalidatedException) {
        deleteKey(BIO_ALIAS)
        null
    }

    fun deleteBiometricKey() = deleteKey(BIO_ALIAS)

    fun deleteAll() {
        deleteKey(BIO_ALIAS)
        deleteKey(DEVICE_ALIAS)
    }

    private fun deviceKey(): SecretKey {
        (keyStore.getKey(DEVICE_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(DEVICE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run { init(spec); generateKey() }
    }

    private fun createBiometricKey(): SecretKey {
        fun build(strongBox: Boolean): SecretKey {
            val builder = KeyGenParameterSpec.Builder(BIO_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setIsStrongBoxBacked(true)
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run { init(builder.build()); generateKey() }
        }
        return try {
            build(strongBox = true)
        } catch (e: Exception) {
            // StrongBoxUnavailableException, or ProviderException on some OEM builds: use the TEE.
            build(strongBox = false)
        }
    }

    private fun deleteKey(alias: String) {
        runCatching { if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias) }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val DEVICE_ALIAS = "msp_device_v1"
        private const val BIO_ALIAS = "msp_biometric_v1"
        private const val PROBE_ALIAS = "msp_probe"
        const val IV_BYTES = 12
    }
}
