package com.example.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device diagnostics log: the last [MAX] events (errors, crashes, background work outcomes).
 * Never leaves the device unless the user copies/shares it. Sensitive data is masked before writing.
 */
object Diagnostics {
    const val MAX = 50
    private val lock = Any()

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "diagnostics.log")

    fun log(ctx: Context, tag: String, message: String, error: Throwable? = null) {
        val line = buildString {
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append(" [").append(tag).append("] ").append(message)
            if (error != null) append(" — ").append(error.javaClass.simpleName).append(": ").append(error.message.orEmpty())
        }
        val masked = mask(line).replace('\n', ' ').take(500)
        synchronized(lock) {
            runCatching {
                val f = file(ctx)
                val lines = (if (f.exists()) f.readLines() else emptyList()) + masked
                f.writeText(lines.takeLast(MAX).joinToString("\n") + "\n")
            }
        }
    }

    fun read(ctx: Context): List<String> = synchronized(lock) { runCatching { file(ctx).readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList()) }
    fun clear(ctx: Context) { synchronized(lock) { file(ctx).delete() } }

    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val googleKey = Regex("AIza[0-9A-Za-z_\\-]{20,}")
    private val bearer = Regex("(?i)(bearer|token|key|password|passphrase|pin)([=: ]+)\\S+")
    private val longNumber = Regex("\\d{6,}")
    private val path = Regex("/(data|storage|sdcard)/\\S+")
    private val uri = Regex("content://\\S+")

    /** Masks e-mails, API keys, secrets after key words, long numbers (phones, ids) and file paths/URIs. */
    fun mask(s: String): String = s
        .replace(googleKey, "AIza***")
        .replace(email, "***@***")
        .replace(bearer) { it.groupValues[1] + it.groupValues[2] + "***" }
        .replace(uri, "content://***")
        .replace(path, "/***")
        .replace(longNumber, "***")
}
