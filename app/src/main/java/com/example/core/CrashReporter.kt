package com.example.core

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the reason of the last unexpected close so the user can send it (shown once on the next
 * launch). Java crashes are written by an uncaught-exception handler; native crashes and system
 * kills come from Android's own exit records (Android 11+). Stays on the device unless shared.
 */
object CrashReporter {
    private fun file(c: Context) = File(c.filesDir, "crash/last.txt")
    private fun prefs(c: Context) = c.getSharedPreferences("notea_crash", Context.MODE_PRIVATE)

    fun install(c: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
                file(c).apply { parentFile?.mkdirs() }.writeText(header(c) + "Thread: ${t.name}\n\n" + sw.toString().take(20_000))
            }
            runCatching { Diagnostics.log(c, "crash", "Thread ${t.name}", e) }
            previous?.uncaughtException(t, e)
        }
    }

    private fun header(c: Context): String {
        val v = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull()
        return "Notea $v · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Time: ${java.util.Date()}\n"
    }

    /** The report not shown yet, or null. */
    fun pending(c: Context): String? {
        file(c).takeIf { it.exists() }?.readText()?.let { return it }
        if (Build.VERSION.SDK_INT < 30) return null
        val am = c.getSystemService(ActivityManager::class.java) ?: return null
        val seen = prefs(c).getLong("seen", 0L)
        val exit = runCatching { am.getHistoricalProcessExitReasons(c.packageName, 0, 10) }.getOrNull().orEmpty()
            .firstOrNull { it.timestamp > seen && it.reason in setOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR) }
            ?: return null
        val kind = when (exit.reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
            ApplicationExitInfo.REASON_ANR -> "not responding (ANR)"
            else -> "crash"
        }
        return header(c) + "Process: ${exit.processName}\nType: $kind\nWhen: ${java.util.Date(exit.timestamp)}\n" +
            "Details: ${exit.description.orEmpty()}\n" + "Status: ${exit.status}\n"
    }

    fun markSeen(c: Context) {
        file(c).delete()
        prefs(c).edit().putLong("seen", System.currentTimeMillis()).apply()
    }
}
