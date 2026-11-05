package com.example.reminders

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.NoteaApp
import com.example.R
import com.example.data.local.ReminderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Shows reminder notifications and handles snooze / done actions. Locked notes never reveal content. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NoteaApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = app.container.repository
                val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return@launch
                val nm = NotificationManagerCompat.from(context)
                val strings = app.container.notificationStrings()
                when (intent.action) {
                    ACTION_FIRE -> {
                        val r = app.container.db.reminderDao().get(reminderId) ?: return@launch
                        if (r.status == ReminderStatus.DONE) return@launch
                        val note = app.container.db.noteDao().getNote(r.noteId) ?: return@launch
                        if (note.deleted) return@launch
                        repo.markReminder(reminderId, ReminderStatus.FIRED)
                        val title = if (note.isLocked) strings.lockedTitle else note.title.ifBlank { strings.untitled }
                        val text = if (note.isLocked) strings.lockedBody else strings.reminderBody
                        val open = PendingIntent.getActivity(
                            context, reminderId.hashCode(),
                            Intent(context, MainActivity::class.java).apply {
                                action = MainActivity.ACTION_OPEN_NOTE
                                putExtra(MainActivity.EXTRA_NOTE_ID, r.noteId)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        val n = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_stat_notea)
                            .setContentTitle(title)
                            .setContentText(text)
                            .setContentIntent(open)
                            .setAutoCancel(true)
                            .setCategory(NotificationCompat.CATEGORY_REMINDER)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setVisibility(if (note.isLocked) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
                            .addAction(0, strings.snooze10, action(context, ACTION_SNOOZE_10, reminderId))
                            .addAction(0, strings.snooze60, action(context, ACTION_SNOOZE_60, reminderId))
                            .addAction(0, strings.done, action(context, ACTION_DONE, reminderId))
                            .build()
                        try { nm.notify(reminderId.hashCode(), n) } catch (_: SecurityException) { /* notifications not allowed */ }
                    }
                    ACTION_SNOOZE_10, ACTION_SNOOZE_60 -> {
                        val minutes = if (intent.action == ACTION_SNOOZE_10) 10 else 60
                        repo.snoozeReminder(reminderId, System.currentTimeMillis() + minutes * 60_000L)
                        nm.cancel(reminderId.hashCode())
                    }
                    ACTION_DONE -> {
                        repo.markReminder(reminderId, ReminderStatus.DONE)
                        nm.cancel(reminderId.hashCode())
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun action(context: Context, action: String, reminderId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, (action + reminderId).hashCode(),
            Intent(context, ReminderReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_REMINDER_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val ACTION_FIRE = "com.example.notea.REMINDER_FIRE"
        const val ACTION_SNOOZE_10 = "com.example.notea.REMINDER_SNOOZE_10"
        const val ACTION_SNOOZE_60 = "com.example.notea.REMINDER_SNOOZE_60"
        const val ACTION_DONE = "com.example.notea.REMINDER_DONE"
        const val EXTRA_REMINDER_ID = "reminderId"
        const val EXTRA_NOTE_ID = "noteId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_LOCKED = "locked"
    }
}

/** Re-arms all pending reminders after reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NoteaApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { app.container.rescheduleAllReminders() } finally { pending.finish() }
        }
    }
}
