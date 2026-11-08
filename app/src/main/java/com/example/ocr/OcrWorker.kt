package com.example.ocr

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.NoteaApp
import com.example.data.local.AttachmentKind

/**
 * Extracts text from an image/PDF attachment in the background, then indexes it for search.
 * Attachments of locked notes are never OCR'd (their text would be stored unencrypted).
 */
class OcrWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val c = (applicationContext as NoteaApp).container
        val a = c.db.attachmentDao().get(id) ?: return Result.success()
        val note = c.db.noteDao().getNote(a.noteId) ?: return Result.success()
        if (note.isLocked || a.encrypted || a.localPath.isBlank() || a.extractedText.isNotBlank()) return Result.success()
        val file = c.attachmentStore.file(a.userId, a.localPath)
        if (!file.exists()) return Result.success()
        if (OcrGuard.disabled(applicationContext)) return Result.success()
        val pdf = a.kind == AttachmentKind.PDF
        runCatching { setForeground(foregroundInfo()) }
        if (!pdf && (a.kind != AttachmentKind.IMAGE || a.mimeType == "image/svg+xml")) return Result.success()
        val text = when (val r = OcrService.recognize(applicationContext, file, pdf)) {
            is OcrService.Companion.Outcome.Text -> r.value
            OcrService.Companion.Outcome.Crashed -> {
                // The engine crashed on this file: never retry it, and pause OCR after repeated crashes.
                OcrGuard.recordCrash(applicationContext)
                com.example.core.Diagnostics.log(applicationContext, "ocr", "Text recognition engine stopped on one image (skipped)")
                ""
            }
        }
        c.repository.setExtractedText(a.id, text.ifBlank { NO_TEXT })
        return Result.success()
    }

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo = foregroundInfo()

    private fun foregroundInfo(): androidx.work.ForegroundInfo {
        val ctx = applicationContext
        val ar = (ctx as NoteaApp).container.isArabic()
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, if (ar) "المهام في الخلفية" else "Background work", android.app.NotificationManager.IMPORTANCE_LOW))
        }
        val n = androidx.core.app.NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(com.example.R.drawable.ic_stat_notea)
            .setContentTitle(if (ar) "قراءة نص الصور…" else "Reading text in images…")
            .setProgress(0, 0, true).setOngoing(true).setSilent(true).build()
        return if (android.os.Build.VERSION.SDK_INT >= 29)
            androidx.work.ForegroundInfo(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else androidx.work.ForegroundInfo(NOTIF_ID, n)
    }

    companion object {
        private const val CHANNEL = "background"
        private const val NOTIF_ID = 4101
        private const val KEY_ID = "attachmentId"
        /** Marker meaning "OCR ran, nothing found" (prevents re-running). */
        const val NO_TEXT = "​"

        fun enqueue(context: Context, attachmentId: String) {
            val req = OneTimeWorkRequestBuilder<OcrWorker>().setInputData(workDataOf(KEY_ID to attachmentId)).build()
            // One heavy job at a time: every image joins a single serial queue.
            WorkManager.getInstance(context).enqueueUniqueWork("ocr-queue", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}

/** Remembers native OCR crashes on this phone; after 2 the feature pauses (user can re-enable). */
object OcrGuard {
    private fun prefs(c: Context) = c.getSharedPreferences("notea_ocr", Context.MODE_PRIVATE)
    fun disabled(c: Context) = prefs(c).getBoolean("disabled", false)
    fun crashCount(c: Context) = prefs(c).getInt("crashes", 0)
    fun recordCrash(c: Context) {
        val n = crashCount(c) + 1
        prefs(c).edit().putInt("crashes", n).putBoolean("disabled", n >= 2).commit()
    }
    fun setEnabled(c: Context, on: Boolean) { prefs(c).edit().putBoolean("disabled", !on).putInt("crashes", 0).apply() }
}
