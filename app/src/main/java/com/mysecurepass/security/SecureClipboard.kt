package com.mysecurepass.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Copies secrets with the platform "sensitive" flag (Android 13+ hides the preview in the clipboard
 * overlay and keyboards' clipboard history) and clears them after a timeout.
 */
class SecureClipboard(context: Context) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val clearTask = Runnable { clearIfOurs() }

    /** True when the system shows its own copy confirmation (Android 13+), so the app shouldn't. */
    val systemShowsConfirmation: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun copy(text: String, clearAfterSeconds: Int, sensitive: Boolean = true) {
        val clip = ClipData.newPlainText(LABEL, text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                } else {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
        }
        clipboard.setPrimaryClip(clip)
        handler.removeCallbacks(clearTask)
        if (clearAfterSeconds > 0) handler.postDelayed(clearTask, clearAfterSeconds * 1000L)
    }

    /**
     * Clears the clipboard if it still holds our clip. When the label can't be read (Android 10+
     * blocks clipboard access from the background) we clear anyway: leaving a password in the
     * clipboard is the worse outcome.
     */
    fun clearIfOurs() {
        val label = runCatching { clipboard.primaryClipDescription?.label }.getOrNull()
        if (label == null || label == LABEL) clear()
    }

    private fun clear() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
            else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    companion object {
        const val LABEL = "MySecurePass"
    }
}
