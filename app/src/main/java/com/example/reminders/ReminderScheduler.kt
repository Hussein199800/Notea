package com.example.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.local.ReminderEntity

/**
 * Schedules note reminders with AlarmManager. Uses exact alarms when the user allowed them,
 * otherwise a doze-friendly inexact alarm (may fire a few minutes late).
 */
class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(reminder: ReminderEntity, title: String, locked: Boolean) {
        val pi = pendingIntent(reminder.id, reminder.noteId, title, locked)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (canExact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pi)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pi)
    }

    fun cancel(reminderId: String) {
        alarms.cancel(pendingIntent(reminderId, "", "", false))
    }

    private fun pendingIntent(reminderId: String, noteId: String, title: String, locked: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_LOCKED, locked)
            data = android.net.Uri.parse("notea://reminder/$reminderId")
        }
        return PendingIntent.getBroadcast(context, reminderId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_ID = "notea_reminders"

        fun createChannel(context: Context, name: String, description: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply { this.description = description }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            }
        }
    }
}
