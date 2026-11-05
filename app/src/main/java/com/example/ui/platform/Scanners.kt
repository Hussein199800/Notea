package com.example.ui.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Google's document / QR scanners are optional Play-services modules downloaded on first use.
 * On phones where that download can't happen (no Play services, blocked region, no internet)
 * Google shows "Something went wrong". We check first and use our own fallbacks instead.
 */
object GmsModules {
    suspend fun ready(context: Context, api: OptionalModuleApi): Boolean = withTimeoutOrNull(4_000) {
        runCatching {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) return@runCatching false
            val client = ModuleInstall.getClient(context)
            val ok = client.areModulesAvailable(api).await().areModulesAvailable()
            // Ask Play services to fetch it quietly so it may be ready next time.
            if (!ok) runCatching { client.deferredInstall(api) }
            ok
        }.getOrDefault(false)
    } ?: false
}

/** Reads a QR code from a photo, fully on the device (no Google services). */
object QrDecoder {
    fun decode(file: File): String? {
        val bmp = sampled(file) ?: return null
        return try { decode(bmp) } finally { bmp.recycle() }
    }

    fun decode(bmp: Bitmap): String? {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
        }
        val source = RGBLuminanceSource(bmp.width, bmp.height, px)
        return runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
            ?: runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert()))).text }.getOrNull()
    }

    private fun sampled(file: File, maxSide: Int = 1600): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / s > maxSide) s *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
    }
}
