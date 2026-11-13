package com.mysecurepass.ui.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a QR code from an image the user picked with the system photo picker, typically a
 * screenshot of a site's 2FA setup page. No camera permission, nothing leaves the device.
 */
object QrImageDecoder {
    private const val MAX_SIDE = 1600

    suspend fun decode(context: Context, uri: Uri): String? = withContext(Dispatchers.Default) {
        val bitmap = load(context, uri) ?: return@withContext null
        try {
            val w = bitmap.width; val h = bitmap.height
            val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
            val source = RGBLuminanceSource(w, h, pixels)
            val hints = mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            )
            val reader = MultiFormatReader().apply { setHints(hints) }
            listOf(BinaryBitmap(HybridBinarizer(source)), BinaryBitmap(GlobalHistogramBinarizer(source)), BinaryBitmap(HybridBinarizer(source.invert())))
                .firstNotNullOfOrNull { img -> runCatching { reader.decodeWithState(img).text }.getOrNull().also { reader.reset() } }
        } finally {
            bitmap.recycle()
        }
    }

    private fun load(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE || bounds.outHeight / sample > MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}
