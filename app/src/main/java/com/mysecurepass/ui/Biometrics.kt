package com.mysecurepass.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

fun Context.findActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Only Class 3 ("strong") biometrics can unlock Keystore keys, so that's all we accept. */
fun Context.canUseStrongBiometrics(): Boolean =
    BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

fun FragmentActivity.authenticateWithCipher(
    title: String,
    subtitle: String,
    cipher: Cipher,
    onSuccess: (Cipher) -> Unit,
    onError: (String?) -> Unit,
) {
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val c = result.cryptoObject?.cipher
            if (c != null) onSuccess(c) else onError("Authentication failed")
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_CANCELED
            onError(if (cancelled) null else errString.toString())
        }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setNegativeButtonText("Use master password")
        .setConfirmationRequired(false)
        .build()
    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
}
