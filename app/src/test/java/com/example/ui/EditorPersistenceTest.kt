package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.AppContainer
import com.example.LOCAL_UID
import com.example.data.local.BlockType
import com.example.data.local.RevisionReason
import com.example.data.repository.BlockDraft
import com.example.data.repository.ChecklistDraft
import com.example.data.repository.Ids
import com.example.data.repository.NoteDraft
import com.example.data.repository.blocksToJson
import com.example.ui.viewmodel.EditorViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditorPersistenceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val container = AppContainer(app)
    private val repository = container.repository

    @Test fun closingAnEmptyEditorKeepsExistingNotesAndDiscardsTemporaryRows() = runBlocking {
        val existingId = Ids.newId()
        repository.save(LOCAL_UID, NoteDraft(existingId, title = "Existing note"))
        val existingStore = ViewModelStore()
        val existing = openEditor(existingStore, existingId)
        existing.setTitle("")
        closeEditor(existingStore)
        val saved = repository.getNote(existingId)
        assertNotNull(saved)
        assertEquals("", saved!!.note.title)

        val temporaryId = Ids.newId()
        val temporaryStore = ViewModelStore()
        val temporary = openEditor(temporaryStore, temporaryId)
        assertTrue(temporary.saveNow(force = true))
        assertNotNull(repository.getNote(temporaryId))
        closeEditor(temporaryStore)
        assertNull(repository.getNote(temporaryId))
    }

    @Test fun editingMetadataDuringAWriteSchedulesAnotherSave() = runBlocking {
        val id = Ids.newId()
        repository.save(LOCAL_UID, NoteDraft(id, title = "Before"))
        val store = ViewModelStore()
        try {
            val editor = openEditor(store, id)
            val transactionStarted = CompletableDeferred<Unit>()
            val releaseTransaction = CompletableDeferred<Unit>()
            val transaction = launch(Dispatchers.IO) {
                container.db.withTransaction {
                    transactionStarted.complete(Unit)
                    releaseTransaction.await()
                }
            }
            try {
                withTimeout(5_000) { transactionStarted.await() }
                editor.setTitle("After")
                val save = async(start = CoroutineStart.UNDISPATCHED) { editor.saveNow() }
                editor.toggleFavorite()
                releaseTransaction.complete(Unit)
                assertTrue(save.await())
                assertTrue(editor.saveNow())
                val saved = repository.getNote(id)!!.note
                assertEquals("After", saved.title)
                assertTrue(saved.isFavorite)
            } finally {
                releaseTransaction.complete(Unit)
            }
            transaction.join()
        } finally {
            closeEditor(store)
        }
    }

    @Test fun classicChecklistsKeepTheirBlocksAcrossNotesAndOlderSyncWrites() = runBlocking {
        val ids = List(2) { Ids.newId() }
        for ((index, id) in ids.withIndex()) {
            repository.save(LOCAL_UID, NoteDraft(id, checklist = listOf(ChecklistDraft(text = "Task $index"))))
            val store = ViewModelStore()
            try {
                val editor = openEditor(store, id)
                editor.setTitle("Note $index")
                assertTrue(editor.saveNow())
            } finally {
                closeEditor(store)
            }
        }

        val synced = repository.getNote(ids.first())!!
        repository.applyStored(
            LOCAL_UID, synced.note, listOf(ChecklistDraft(synced.checklist.single().id, "Task 0", checked = true)),
            emptyList(), null
        )
        val blockIds = ids.mapIndexed { index, id ->
            val saved = repository.getNote(id)!!
            val block = saved.blocks.single { it.type == BlockType.CHECKLIST }
            assertEquals("Task $index", saved.checklist.single().text)
            assertEquals(block.id, saved.checklist.single().blockId)
            block.id
        }
        assertEquals(ids.size, blockIds.distinct().size)
    }

    @Test fun restoringAnImportedRevisionKeepsTheSourceNoteIntact() = runBlocking {
        val original = Ids.newId()
        val copy = Ids.newId()
        val taskBlock = BlockDraft(type = BlockType.CHECKLIST)
        val blocks = listOf(BlockDraft(type = BlockType.TEXT, text = "Before the task"), taskBlock,
            BlockDraft(type = BlockType.TEXT, text = "After the task"))
        val items = listOf(ChecklistDraft(text = "Task", blockId = taskBlock.id))
        repository.save(LOCAL_UID, NoteDraft(original, title = "Original", checklist = items, blocks = blocks))
        repository.save(LOCAL_UID, NoteDraft(copy, title = "Copy"))
        repository.addRevision(copy, "Older version", "Before the task\n\nAfter the task", items,
            RevisionReason.NAMED, label = "Backup", blocksJson = blocks.blocksToJson())

        repository.restoreRevision(LOCAL_UID, container.db.revisionDao().forNote(copy).single())

        val source = repository.getNote(original)!!
        val restored = repository.getNote(copy)!!
        assertEquals(blocks.map { it.id }, source.blocks.sortedBy { it.position }.map { it.id })
        assertEquals(items.single().id, source.checklist.single().id)
        assertEquals(blocks.map { it.type }, restored.blocks.sortedBy { it.position }.map { it.type })
        assertEquals("Older version", restored.note.title)
        assertEquals(source.note.body, restored.note.body)
        assertEquals(restored.blocks.single { it.type == BlockType.CHECKLIST }.id, restored.checklist.single().blockId)
        assertEquals("Task", restored.checklist.single().text)
    }

    @Test fun closingABlankPageThatHoldsSubNotesKeepsIt() = runBlocking {
        val parentId = Ids.newId()
        val store = ViewModelStore()
        val parent = openEditor(store, parentId)
        assertTrue(parent.saveNow(force = true))          // blank temporary row, as when «sub-note» is tapped first
        repository.save(LOCAL_UID, NoteDraft(Ids.newId(), title = "Child", parentId = parentId))
        closeEditor(store)
        assertNotNull(repository.getNote(parentId))       // not discarded: a child points to it
    }

    private suspend fun openEditor(store: ViewModelStore, id: String): EditorViewModel {
        val factory = viewModelFactory { initializer { EditorViewModel(container, LOCAL_UID, id) } }
        return ViewModelProvider(store, factory)[EditorViewModel::class.java].also { editor ->
            withTimeout(5_000) { editor.state.first { !it.loading } }
        }
    }

    private suspend fun closeEditor(store: ViewModelStore) {
        store.clear()
        withTimeout(5_000) { container.scope.coroutineContext[Job]!!.children.toList().joinAll() }
    }
}
