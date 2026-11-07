package com.example.ocr

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Runs the native OCR engine in a SEPARATE PROCESS (":ocr", see the manifest).
 * A native crash inside Tesseract can't be caught in Kotlin; isolated here, it only kills this
 * helper process — Notea itself keeps running and the image is simply left without text.
 */
class OcrService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("ocr").apply { start() }
        messenger = Messenger(object : Handler(thread.looper) {
            override fun handleMessage(msg: Message) {
                if (msg.what != MSG_OCR) return
                val path = msg.data.getString(KEY_PATH).orEmpty()
                val pdf = msg.data.getBoolean(KEY_PDF)
                val text = runCatching {
                    val engine = OcrEngine(applicationContext)
                    if (pdf) engine.recognizePdf(File(path)) else engine.recognizeImageFile(File(path))
                }.getOrElse { "" }
                runCatching { msg.replyTo?.send(Message.obtain(null, MSG_RESULT).apply { data = Bundle().apply { putString(KEY_TEXT, text) } }) }
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder
    override fun onDestroy() { thread.quitSafely(); super.onDestroy() }

    companion object {
        private const val MSG_OCR = 1
        private const val MSG_RESULT = 2
        private const val KEY_PATH = "path"
        private const val KEY_PDF = "pdf"
        private const val KEY_TEXT = "text"

        sealed interface Outcome {
            data class Text(val value: String) : Outcome
            /** The OCR process died (native crash) or never answered. */
            data object Crashed : Outcome
        }

        /** Binds to the isolated OCR process, sends one job, waits for the answer. */
        suspend fun recognize(context: Context, file: File, pdf: Boolean, timeoutMs: Long = 180_000): Outcome {
            val result = CompletableDeferred<Outcome>()
            val replyHandler = Handler(Looper.getMainLooper()) { m ->
                if (m.what == MSG_RESULT) result.complete(Outcome.Text(m.data.getString(KEY_TEXT).orEmpty())); true
            }
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    try {
                        Messenger(binder).send(Message.obtain(null, MSG_OCR).apply {
                            data = Bundle().apply { putString(KEY_PATH, file.path); putBoolean(KEY_PDF, pdf) }
                            replyTo = Messenger(replyHandler)
                        })
                    } catch (e: Exception) { result.complete(Outcome.Crashed) }
                }
                override fun onServiceDisconnected(name: ComponentName?) { result.complete(Outcome.Crashed) }
                override fun onBindingDied(name: ComponentName?) { result.complete(Outcome.Crashed) }
            }
            val bound = withContext(Dispatchers.Main) {
                context.bindService(Intent(context, OcrService::class.java), conn, Context.BIND_AUTO_CREATE)
            }
            if (!bound) return Outcome.Crashed
            return try {
                withTimeoutOrNull(timeoutMs) { result.await() } ?: Outcome.Crashed
            } finally {
                withContext(Dispatchers.Main) { runCatching { context.unbindService(conn) } }
            }
        }
    }
}
