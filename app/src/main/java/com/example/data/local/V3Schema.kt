package com.example.data.local

/** v2 -> v3 statements, generated from schemas/3.json (Room export). */
object V3Schema {
    val STATEMENTS: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `note_blocks` (`id` TEXT NOT NULL, `noteId` TEXT NOT NULL, `position` INTEGER NOT NULL, `type` TEXT NOT NULL, `text` TEXT NOT NULL, `attachmentId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_note_blocks_noteId` ON `note_blocks` (`noteId`)",
        "ALTER TABLE `checklist_items` ADD COLUMN `blockId` TEXT DEFAULT NULL",
        "ALTER TABLE `note_revisions` ADD COLUMN `label` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `note_revisions` ADD COLUMN `blocksJson` TEXT NOT NULL DEFAULT ''"
    )
}
