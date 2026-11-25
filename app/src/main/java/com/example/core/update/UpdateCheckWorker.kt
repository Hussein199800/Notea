package com.example.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.NoteaApp
import com.example.R
import java.util.concurrent.TimeUnit

/** Checks GitHub for a newer release every 12 hours (on any network) and posts one notification per version. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as NoteaApp).container
        val r = c.updater.checkQuietly() ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences("notea_update", Context.MODE_PRIVATE)
        if (prefs.getString("notified", null) == r.version) return Result.success()
        prefs.edit().putString("notified", r.version).apply()
        val ar = c.isArabic()
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, if (ar) "التحديثات" else "Updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(applicationContext, 7001,
            Intent(applicationContext, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_UPDATE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(applicationContext, CHANNEL).setSmallIcon(R.drawable.ic_stat_notea)
            .setContentTitle(if (ar) "تحديث متاح: الإصدار ${r.version.removePrefix("v")}" else "Update available: ${r.version.removePrefix("v")}")
            .setContentText(if (ar) "اضغط لعرض ما الجديد وتنزيله" else "Tap to see what's new and download it")
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { nm.notify(7001, n) }
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "updates"
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateCheckWorker>(UpdateRules.CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("notea-update-check", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
