package com.mysecurepass.ui.generator

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.mysecurepass.ui.components.Panel
import com.mysecurepass.ui.components.PillButton
import com.mysecurepass.ui.components.ToggleRow
import com.mysecurepass.ui.components.fieldColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Escapes a value for the `WIFI:` QR payload format. */
private fun wifiEscape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")

private fun encodeQr(content: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )
    val w = matrix.width; val h = matrix.height
    val pixels = IntArray(w * h) { i -> if (matrix[i % w, i / w]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Shows a secret as a QR code for moving it to another device (e.g. a TV or a guest's phone),
 * optionally as a standard Wi‑Fi join code. Generated locally; nothing is transmitted.
 */
@Composable
fun QrDialog(secret: String, initialSsid: String? = null, security: String = "WPA", hidden: Boolean = false, onDismiss: () -> Unit) {
    var wifi by remember { mutableStateOf(initialSsid != null) }
    var ssid by remember { mutableStateOf(initialSsid.orEmpty()) }
    val content = if (wifi && ssid.isNotBlank()) {
        val pass = if (security == "nopass") "" else "P:${wifiEscape(secret)};"
        "WIFI:T:$security;S:${wifiEscape(ssid)};$pass${if (hidden) "H:true;" else ""};"
    } else secret
    val bitmap by produceState<ImageBitmap?>(null, content) {
        value = withContext(Dispatchers.Default) { runCatching { encodeQr(content) }.getOrNull() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Panel(Modifier.verticalScroll(rememberScrollState()), padding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
            Text("Share via QR", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Anyone who can see this code can read the secret. Only show it to a device you trust.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(Color.White).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let { Image(it, "QR code", Modifier.fillMaxWidth().aspectRatio(1f), filterQuality = FilterQuality.None) }
            }
            Spacer(Modifier.height(12.dp))
            ToggleRow("Wi‑Fi network code", wifi, { wifi = it }, "Phones join the network by scanning")
            if (wifi) {
                OutlinedTextField(
                    ssid, { ssid = it }, label = { Text("Network name (SSID)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = false),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PillButton("Done", onDismiss, Modifier.fillMaxWidth())
            }
        }
    }
}
