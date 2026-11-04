package com.example.data.local

/** Generated from schemas/2.json (Room export). Keep in sync when the v2 schema is touched. */
object V2Schema {
    val CREATE_STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, `folderId` TEXT, `isPinned` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, `trashedAt` INTEGER, `isLocked` INTEGER NOT NULL, `lockPinHash` TEXT NOT NULL, `lockHint` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `syncState` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `baseRemoteUpdatedAt` INTEGER NOT NULL, `legacyCategory` TEXT NOT NULL, `legacyPending` INTEGER NOT NULL, `importSource` TEXT NOT NULL, `contentHash` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_notes_userId` ON `notes` (`userId`)",
        "CREATE INDEX IF NOT EXISTS `index_notes_folderId` ON `notes` (`folderId`)",
        "CREATE INDEX IF NOT EXISTS `index_notes_userId_syncState` ON `notes` (`userId`, `syncState`)",
        "CREATE TABLE IF NOT EXISTS `checklist_items` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, `text` TEXT NOT NULL, `checked` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_checklist_items_noteId` ON `checklist_items` (`noteId`)",
        "CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `normalized` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_userId_normalized` ON `tags` (`userId`, `normalized`)",
        "CREATE TABLE IF NOT EXISTS `note_tags` (`noteId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`noteId`, `tagId`), FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_note_tags_tagId` ON `note_tags` (`tagId`)",
        "CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `syncState` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_folders_userId` ON `folders` (`userId`)",
        "CREATE TABLE IF NOT EXISTS `attachments` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, `userId` TEXT NOT NULL, `kind` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `displayName` TEXT NOT NULL, `localPath` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `url` TEXT NOT NULL, `linkTitle` TEXT NOT NULL, `linkDescription` TEXT NOT NULL, `extractedText` TEXT NOT NULL, `encrypted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_attachments_noteId` ON `attachments` (`noteId`)",
        "CREATE INDEX IF NOT EXISTS `index_attachments_userId` ON `attachments` (`userId`)",
        "CREATE TABLE IF NOT EXISTS `reminders` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, `userId` TEXT NOT NULL, `triggerAt` INTEGER NOT NULL, `status` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_reminders_noteId` ON `reminders` (`noteId`)",
        "CREATE INDEX IF NOT EXISTS `index_reminders_userId` ON `reminders` (`userId`)",
        "CREATE TABLE IF NOT EXISTS `note_revisions` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `checklistJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `reason` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_note_revisions_noteId` ON `note_revisions` (`noteId`)",
        "CREATE TABLE IF NOT EXISTS `templates` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `checklistJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_templates_userId` ON `templates` (`userId`)",
        "CREATE VIRTUAL TABLE IF NOT EXISTS `notes_fts` USING FTS4(`noteId` TEXT NOT NULL, `userId` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `tags` TEXT NOT NULL, `extra` TEXT NOT NULL, notindexed=`noteId`, notindexed=`userId`)"
    )
}
