package com.mysecurepass

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mysecurepass.core.generator.Generator
import com.mysecurepass.core.vault.VaultCrypto
import com.mysecurepass.data.SettingsRepository
import com.mysecurepass.data.StrengthAnalyzer
import com.mysecurepass.data.VaultRepository
import com.mysecurepass.security.Argon2Kdf
import com.mysecurepass.security.KeystoreManager
import com.mysecurepass.security.SecureClipboard
import kotlinx.coroutines.launch

/** Manual dependency container: no reflection-based DI, faster cold start on low-end devices. */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val securityLog = com.mysecurepass.data.SecurityLog(context)
    val keystore = KeystoreManager()
    val kdf = Argon2Kdf()
    val vault = VaultRepository(context, VaultCrypto(kdf), keystore, settings, securityLog)

    /** Result of the most recent on-device self-test (memory only). */
    val lastSelfTest = kotlinx.coroutines.flow.MutableStateFlow<com.mysecurepass.security.SelfTestReport?>(null)

    /** Keystore hardware level, probed once in the background. */
    val hardwareLevel = kotlinx.coroutines.flow.MutableStateFlow<com.mysecurepass.security.HardwareLevel?>(null)
    val generator = Generator()
    val clipboard = SecureClipboard(context)
    val analyzer = StrengthAnalyzer()
    val history = SessionHistory()

    /** In-memory hand-off of a generated password to the "new entry" screen (never put in nav args, which can be persisted). */
    @Volatile var draftPassword: String? = null

    /** Outlives screens so a save started on one screen completes even if the user navigates away. */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)

    /** zxcvbn scores keyed by "entryId:passwordChangedAt" (no secrets as keys). Cleared on lock. */
    val healthScores = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Set after vault creation; the vault tab offers biometric enrolment. */
    val offerBiometric = kotlinx.coroutines.flow.MutableStateFlow(false)
}

/** Generated values for the current session. Memory only: never written to disk, gone when the process dies. */
class SessionHistory {
    data class Item(val value: String, val bits: Double, val at: Long)

    private val _items = kotlinx.coroutines.flow.MutableStateFlow<List<Item>>(emptyList())
    val items: kotlinx.coroutines.flow.StateFlow<List<Item>> = _items

    fun add(value: String, bits: Double) {
        _items.value = (listOf(Item(value, bits, System.currentTimeMillis())) + _items.value).take(MAX)
    }

    fun clear() { _items.value = emptyList() }

    private companion object { const val MAX = 25 }
}

class MspApp : Application() {
    lateinit var container: AppContainer
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val lockTask = Runnable { lockNow() }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            container.hardwareLevel.value = container.keystore.hardwareLevel()
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                handler.removeCallbacks(lockTask)
            }

            override fun onStop(owner: LifecycleOwner) {
                val timeout = container.settings.settings.value.autoLockSeconds
                if (timeout == 0) lockNow() else if (timeout > 0) handler.postDelayed(lockTask, timeout * 1000L)
            }
        })

        androidx.core.content.ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (container.settings.settings.value.lockOnScreenOff) lockNow()
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun lockNow() {
        val wasUnlocked = container.vault.isUnlocked
        container.vault.lock()
        // Don't leave a copied secret behind once the vault is locked (unless the user chose "Never").
        if (wasUnlocked && container.settings.settings.value.clipboardClearSeconds > 0) container.clipboard.clearIfOurs()
        container.history.clear()
        container.healthScores.clear()
        container.draftPassword = null
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The system is about to reclaim memory while we're in the background: lock proactively.
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_MODERATE) lockNow()
    }
}

val Context.container: AppContainer get() = (applicationContext as MspApp).container
