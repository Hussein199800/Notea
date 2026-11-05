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
        val engine = OcrEngine(applicationContext)
        val text = try {
            when {
                a.kind == AttachmentKind.IMAGE && a.mimeType != "image/svg+xml" -> engine.recognizeImageFile(file)
                a.kind == AttachmentKind.PDF -> engine.recognizePdf(file)
                else -> ""
            }
        } catch (e: Throwable) {
            return if (runAttemptCount < 2) Result.retry() else Result.success()
        }
        c.repository.setExtractedText(a.id, text.ifBlank { NO_TEXT })
        return Result.success()
    }

    companion object {
        private const val KEY_ID = "attachmentId"
        /** Marker meaning "OCR ran, nothing found" (prevents re-running). */
        const val NO_TEXT = "​"

        fun enqueue(context: Context, attachmentId: String) {
            val req = OneTimeWorkRequestBuilder<OcrWorker>().setInputData(workDataOf(KEY_ID to attachmentId)).build()
            WorkManager.getInstance(context).enqueueUniqueWork("ocr-$attachmentId", ExistingWorkPolicy.KEEP, req)
        }
    }
}
