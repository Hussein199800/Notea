package com.example

import android.app.Application
import com.example.core.prefs.LanguagePreference
import com.example.core.prefs.SettingsStore
import com.example.core.security.KeyVault
import com.example.data.backup.AutoBackupWorker
import com.example.data.backup.BackupManager
import com.example.data.importer.ImportManager
import com.example.data.legacy.LegacyUpgrader
import com.example.data.local.NoteaDatabase
import com.example.data.local.ReminderEntity
import com.example.data.repository.AttachmentStore
import com.example.data.repository.ChangeListener
import com.example.data.repository.NoteRepository
import com.example.data.sync.SyncEngine
import com.example.data.sync.SyncWorker
import com.example.reminders.ReminderScheduler
import com.example.widget.NoteaWidgetUpdater
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

open class NoteaApp : Application(), coil.ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // The isolated OCR helper process (":ocr") must not start the app's database, sync or jobs.
        if (isHelperProcess()) return
        com.example.core.CrashReporter.install(this)
        container = AppContainer(this)
        container.onAppStart()
    }

    private fun isHelperProcess(): Boolean {
        val name = if (android.os.Build.VERSION.SDK_INT >= 28) getProcessName() else runCatching {
            val pid = android.os.Process.myPid()
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }.getOrNull()
        return name?.endsWith(":ocr") == true
    }

    /** Images: animated GIF/WebP (ImageDecoder on 28+, GifDecoder before) and SVG everywhere. */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .components {
            if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory()) else add(coil.decode.GifDecoder.Factory())
            add(coil.decode.SvgDecoder.Factory())
        }
        .crossfade(true)
        .build()
}

/** Manual dependency container (no DI framework needed for this app size). */
const val LOCAL_UID = "local-device"

class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsStore(app)
    val db: NoteaDatabase = NoteaDatabase.getInstance(app)
    val keyVault = KeyVault(app)
    val reminderScheduler = ReminderScheduler(app)
    val attachmentStore = AttachmentStore(app)

    val repository: NoteRepository = NoteRepository(db, keyVault, attachmentStore, object : ChangeListener {
        override fun onNotesChanged(uid: String) {
            if (syncAvailable(uid)) SyncWorker.enqueue(app, uid)
            NoteaWidgetUpdater.requestUpdate(app)
        }
        override fun onReminderChanged(reminder: ReminderEntity, noteTitle: String, locked: Boolean) =
            reminderScheduler.schedule(reminder, noteTitle, locked)
        override fun onReminderCancelled(reminderId: String) = reminderScheduler.cancel(reminderId)
        override fun onScannableAttachment(attachment: com.example.data.local.AttachmentEntity) = onImageAttachmentAdded(attachment)
    })

    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id)) }
    val syncEngine: SyncEngine by lazy { SyncEngine(firestore, db, repository, keyVault, settings.deviceId) }
    val importManager by lazy { ImportManager(app, repository) }
    val backupManager by lazy { BackupManager(app, db, repository, keyVault) }

    /** True when this build has a Firebase configuration (app/google-services.json). */
    val firebaseAvailable: Boolean by lazy { runCatching { com.google.firebase.FirebaseApp.getApps(app).isNotEmpty() }.getOrDefault(false) }

    /** Signed-in Firebase account, if any. */
    /** Called after an image attachment is stored (OCR hook). */
    fun onImageAttachmentAdded(a: com.example.data.local.AttachmentEntity) {
        com.example.ocr.OcrWorker.enqueue(app, a.id)
    }

    fun currentUid(): String? = if (!firebaseAvailable) null else runCatching { Firebase.auth.currentUser?.uid }.getOrNull()

    /** The account whose notes are shown: the signed-in one, or the on-device local account. */
    fun activeUid(): String? = currentUid() ?: if (!firebaseAvailable || settings.localMode()) LOCAL_UID else null

    fun syncAvailable(uid: String) = firebaseAvailable && uid != LOCAL_UID

    val aiKeys by lazy { com.example.core.ai.AiKeyStore(app) }
    val assistant by lazy { com.example.core.ai.NoteAssistant(com.example.core.ai.GeminiClient(), aiKeys) }

    /** Assistant is offered only when the user enabled it and saved a key. */
    fun assistantReady() = aiKeys.enabled && aiKeys.hasKey()

    @Volatile private var memoryCache: Pair<Long, com.example.core.memory.NoteMemory.Index>? = null

    /** Related-notes index, rebuilt only when notes changed (cheap: on-device, no network). */
    suspend fun memoryIndex(uid: String): com.example.core.memory.NoteMemory.Index {
        val notes = db.noteDao().getAllWithDetails(uid)
        val stamp = notes.size * 31L + (notes.maxOfOrNull { it.note.updatedAt } ?: 0L) + notes.sumOf { n -> n.attachments.sumOf { it.extractedText.length.toLong() } }
        memoryCache?.let { if (it.first == stamp) return it.second }
        return com.example.core.memory.NoteMemory.buildIndex(notes).also { memoryCache = stamp to it }
    }

    val accountLinker by lazy { com.example.data.repository.AccountLinker(this) }

    fun isArabic(): Boolean = when (settings.state.value.language) {
        LanguagePreference.ARABIC -> true
        LanguagePreference.ENGLISH -> false
        LanguagePreference.SYSTEM -> Locale.getDefault().language == "ar"
    }

    data class NotificationStrings(
        val lockedTitle: String, val lockedBody: String, val untitled: String, val reminderBody: String,
        val snooze10: String, val snooze60: String, val done: String
    )

    fun notificationStrings() = if (isArabic()) NotificationStrings(
        "ملاحظة مقفلة", "لديك تذكير لملاحظة مقفلة", "ملاحظة بلا عنوان", "حان موعد التذكير",
        "بعد ١٠ دقائق", "بعد ساعة", "تم"
    ) else NotificationStrings(
        "Locked note", "You have a reminder for a locked note", "Untitled note", "Reminder is due",
        "In 10 min", "In 1 hour", "Done"
    )

    fun onAppStart() {
        ReminderScheduler.createChannel(app, if (isArabic()) "التذكيرات" else "Reminders", if (isArabic()) "تذكيرات الملاحظات والمهام" else "Note and task reminders")
        scope.launch {
            // Expand rows migrated from schema v1 (idempotent).
            LegacyUpgrader(db, repository, isArabic()).run()
            if (db.searchDao().count() == 0) db.noteDao().knownUserIds().forEach { repository.reindexAll(it) }
            rescheduleAllReminders()
        }
        AutoBackupWorker.schedule(app, settings.state.value.autoBackup)
    }

    suspend fun rescheduleAllReminders() {
        val now = System.currentTimeMillis()
        repository.activeReminders().filter { it.status == com.example.data.local.ReminderStatus.SCHEDULED }.forEach { r ->
            val note = db.noteDao().getNote(r.noteId) ?: return@forEach
            reminderScheduler.schedule(if (r.triggerAt < now) r.copy(triggerAt = now + 5_000) else r, note.title, note.isLocked)
        }
    }
}
