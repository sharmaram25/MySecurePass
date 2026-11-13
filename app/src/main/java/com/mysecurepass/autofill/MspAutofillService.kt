package com.mysecurepass.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.mysecurepass.R
import com.mysecurepass.container
import com.mysecurepass.core.vault.Matching
import com.mysecurepass.core.vault.VaultEntry
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android Autofill provider. Credentials are only ever returned for the requesting app's package
 * or a matching web domain, and only while the vault is unlocked; otherwise the user is sent
 * through [AutofillActivity] to unlock (password or strong biometric) first.
 */
class MspAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        val fields = AutofillParser.parse(structure)
        if (fields.isEmpty || fields.packageName == packageName) return callback.onSuccess(null)

        val vault = container.vault
        val response = FillResponse.Builder()
        var hasDataset = false

        if (vault.isUnlocked) {
            vault.entries().filter { !it.isDeleted && it.type == com.mysecurepass.core.vault.EntryType.LOGIN }
                .filter { Matching.matches(it, fields.packageName, fields.webDomain) }
                .take(MAX_DIRECT)
                .forEach { entry ->
                    datasetFor(this, entry, fields)?.let { response.addDataset(it); hasDataset = true }
                }
        }

        // Always offer an entry point to unlock / search the vault.
        val label = getString(if (vault.isUnlocked) R.string.autofill_search else R.string.autofill_unlock)
        val auth = AutofillActivity.fillIntent(this, fields)
        @Suppress("DEPRECATION") // RemoteViews presentations are required below API 33
        val authDataset = Dataset.Builder(presentation(this, label, fields.webDomain ?: appLabel(fields.packageName)))
        fields.allIds.forEach { @Suppress("DEPRECATION") authDataset.setValue(it, null) }
        authDataset.setAuthentication(pendingIntentSender(auth))
        response.addDataset(authDataset.build())
        hasDataset = true

        if (fields.passwordIds.isNotEmpty()) {
            val type = if (fields.usernameIds.isNotEmpty()) SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD else SaveInfo.SAVE_DATA_TYPE_PASSWORD
            val save = SaveInfo.Builder(type, fields.passwordIds.toTypedArray())
            if (fields.usernameIds.isNotEmpty()) save.setOptionalIds(fields.usernameIds.toTypedArray())
            response.setSaveInfo(save.build())
        }

        callback.onSuccess(if (hasDataset) response.build() else null)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess()
        val fields = AutofillParser.parse(structure)
        val password = fields.passwordValue
        if (password.isNullOrEmpty() || fields.packageName == packageName) return callback.onSuccess()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val intent = AutofillActivity.saveIntent(this, fields)
            callback.onSuccess(pendingIntentSender(intent))
        } else {
            callback.onSuccess()
        }
    }

    private fun pendingIntentSender(intent: Intent): IntentSender {
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getActivity(this, requestCodes.incrementAndGet(), intent, flags).intentSender
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    companion object {
        private const val MAX_DIRECT = 5
        private val requestCodes = AtomicInteger(1000)

        fun presentation(context: Context, title: String, subtitle: String?): RemoteViews =
            RemoteViews(context.packageName, R.layout.autofill_item).apply {
                setTextViewText(R.id.title, title)
                setTextViewText(R.id.subtitle, subtitle.orEmpty())
            }

        /** Builds a dataset that fills username and password fields with [entry]. */
        @Suppress("DEPRECATION")
        fun datasetFor(context: Context, entry: VaultEntry, fields: ParsedFields): Dataset? {
            val builder = Dataset.Builder(presentation(context, entry.title, entry.username.ifEmpty { "Password only" }))
            var any = false
            fields.usernameIds.forEach { id: AutofillId ->
                if (entry.username.isNotEmpty()) { builder.setValue(id, AutofillValue.forText(entry.username)); any = true }
            }
            fields.passwordIds.forEach { id ->
                if (entry.password.isNotEmpty()) { builder.setValue(id, AutofillValue.forText(entry.password)); any = true }
            }
            return if (any) builder.build() else null
        }
    }
}
