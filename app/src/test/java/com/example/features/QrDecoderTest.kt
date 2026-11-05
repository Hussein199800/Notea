package com.example.features

import com.example.data.transfer.DeviceTransfer
import com.example.ui.platform.QrDecoder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QrDecoderTest {
    @Test fun decodesTransferQr_withoutGoogleServices() {
        val offer = DeviceTransfer.Offer("192.168.43.1", 41234, DeviceTransfer.newCode())
        val qr = DeviceTransfer.qrBitmap(offer.toUri(), size = 600)
        // Simulate a photo: QR placed on a larger light background.
        val photo = android.graphics.Bitmap.createBitmap(1200, 1500, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(photo).apply { drawColor(0xFFEEEEEE.toInt()); drawBitmap(qr, 300f, 450f, null) }
        assertEquals(offer, DeviceTransfer.parse(QrDecoder.decode(photo)!!))
    }
}
