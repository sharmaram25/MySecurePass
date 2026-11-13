package com.mysecurepass.security

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import com.mysecurepass.core.security.CheckResult
import java.io.File

/** Facts about the device and app that affect how well secrets are protected. All checks are local. */
object DeviceSecurity {

    /** Verified at runtime from the installed package: the app holds no network permission. */
    fun hasNoNetworkAccess(context: Context): Boolean {
        val granted = context.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
        val requested = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        }.getOrNull().orEmpty()
        return !granted && Manifest.permission.INTERNET !in requested
    }

    /** Without a screen lock, the Keystore can't bind keys to the user and the phone itself is open. */
    fun hasScreenLock(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun usbDebuggingOn(context: Context): Boolean =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 }.getOrDefault(false)

    fun autofillEnabled(context: Context): Boolean =
        context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true

    /**
     * Heuristic root detection. Root breaks Android's app sandbox, so other apps could read memory
     * or files. This is advisory only: determined root hiding can defeat any local check.
     */
    fun likelyRooted(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/sd/xbin/su",
            "/data/local/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/app/Superuser.apk",
            "/system/bin/.ext/su", "/system/usr/we-need-root/su", "/cache/su", "/dev/su",
        )
        return paths.any { runCatching { File(it).exists() }.getOrDefault(false) }
    }

}

data class SelfTestReport(
    val results: List<CheckResult>,
    val kdfMillis: Long,
    val hardware: HardwareLevel,
    val at: Long,
) {
    val passed: Boolean get() = results.all { it.passed }
}
