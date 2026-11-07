package com.example.features

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.CrashReporter
import com.example.ocr.OcrGuard
import com.example.ocr.OcrService
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CrashSafetyTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun ocrService_answersEvenWhenEngineFails() = runBlocking {
        // Robolectric has no native Tesseract: the engine throws inside the service, which must still reply.
        val service = org.robolectric.Robolectric.setupService(OcrService::class.java)
        Shadows.shadowOf(ctx as android.app.Application).setComponentNameAndServiceForBindService(
            android.content.ComponentName(ctx, OcrService::class.java), service.onBind(null))
        val f = File(ctx.cacheDir, "x.jpg").apply { writeBytes(ByteArray(10)) }
        val out = kotlinx.coroutines.withTimeout(10_000) {
            val job = async { OcrService.recognize(ctx, f, pdf = false, timeoutMs = 8_000) }
            while (!job.isCompleted) { org.robolectric.shadows.ShadowLooper.idleMainLooper(); Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); kotlinx.coroutines.delay(20) }
            job.await()
        }
        assertEquals(OcrService.Companion.Outcome.Text(""), out)
    }

    @Test fun guard_pausesAfterTwoCrashes_andCanBeReEnabled() {
        OcrGuard.setEnabled(ctx, true)
        OcrGuard.recordCrash(ctx); assertFalse(OcrGuard.disabled(ctx))
        OcrGuard.recordCrash(ctx); assertTrue(OcrGuard.disabled(ctx))
        OcrGuard.setEnabled(ctx, true); assertFalse(OcrGuard.disabled(ctx)); assertEquals(0, OcrGuard.crashCount(ctx))
    }

    @Test fun crashReport_isKeptUntilSeen() {
        CrashReporter.markSeen(ctx)
        File(ctx.filesDir, "crash").mkdirs()
        File(ctx.filesDir, "crash/last.txt").writeText("Notea 1.1.2\njava.lang.IllegalStateException: boom")
        assertTrue(CrashReporter.pending(ctx)!!.contains("boom"))
        CrashReporter.markSeen(ctx)
        assertNull(CrashReporter.pending(ctx))
    }
}
