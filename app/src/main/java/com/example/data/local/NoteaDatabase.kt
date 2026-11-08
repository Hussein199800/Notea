package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NoteEntity::class,
        ChecklistItemEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        FolderEntity::class,
        AttachmentEntity::class,
        ReminderEntity::class,
        RevisionEntity::class,
        TemplateEntity::class,
        NoteFtsEntity::class,
        NoteBlockEntity::class,
        LegacyNoteEntity::class
    ],
    version = NoteaDatabase.VERSION,
    exportSchema = true
)
abstract class NoteaDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun tagDao(): TagDao
    abstract fun folderDao(): FolderDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun reminderDao(): ReminderDao
    abstract fun revisionDao(): RevisionDao
    abstract fun templateDao(): TemplateDao
    abstract fun searchDao(): SearchDao
    abstract fun ownershipDao(): OwnershipDao
    abstract fun blockDao(): BlockDao

    companion object {
        const val VERSION = 4
        /** Same file name as v1 so existing installs are migrated in place. */
        const val FILE_NAME = "arabic_notes_offline.db"

        @Volatile
        private var INSTANCE: NoteaDatabase? = null

        fun getInstance(context: Context): NoteaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext, FILE_NAME).also { INSTANCE = it }
            }

        fun build(context: Context, name: String): NoteaDatabase =
            Room.databaseBuilder(context, NoteaDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS)
                // Deliberately NO fallbackToDestructiveMigration: a missing migration must crash
                // in development rather than silently wipe user data in production.
                .build()

        val ALL_MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}

/**
 * v1 -> v2: introduces normalized tables. The v1 `local_notes` table is left untouched as a
 * safety copy; its rows are copied into `notes` flagged `legacyPending = 1` and then expanded
 * (metadata block, category flags, checklists, tags) by LegacyUpgrader in Kotlin.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        V2Schema.CREATE_STATEMENTS.forEach { db.execSQL(it) }
        db.execSQL(
            """
            INSERT OR IGNORE INTO notes (id, userId, title, body, colorIndex, folderId, isPinned, isFavorite,
                isArchived, trashedAt, isLocked, lockPinHash, lockHint, createdAt, updatedAt, syncState,
                deleted, baseRemoteUpdatedAt, legacyCategory, legacyPending, importSource, contentHash)
            SELECT id, userId, title, content, colorIndex, NULL, isPinned, 0,
                isArchived, NULL, isLocked, notePinHash, lockHint, createdAtMillis, updatedAtMillis,
                CASE WHEN pendingSync THEN 1 ELSE 0 END,
                0, updatedAtMillis, category, 1, '', ''
            FROM local_notes
            """.trimIndent()
        )
    }
}

/**
 * v2 -> v3 (additive only): ordered content blocks, checklist grouping, named versions.
 * Existing notes need no conversion: a note without block rows is shown with the classic
 * layout (text, then checklist, then attachments).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        V3Schema.STATEMENTS.forEach { db.execSQL(it) }
    }
}

/** v3 -> v4 (additive only): sub-notes (parentId) and a per-note emoji icon. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `parentId` TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `icon` TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_parentId` ON `notes` (`parentId`)")
    }
}
