package com.example.ui.viewmodel

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.core.security.CryptoException
import com.example.data.local.AttachmentEntity
import com.example.data.local.AttachmentKind
import com.example.data.local.RevisionEntity
import com.example.data.repository.ChecklistDraft
import com.example.data.repository.DecryptResult
import com.example.data.repository.NoteDraft
import com.example.data.repository.VaultLockedException
import com.example.ui.editor.EditHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class EditorContent(val title: String, val body: TextFieldValue, val checklist: List<ChecklistDraft>)

data class EditorState(
    val loading: Boolean = true,
    val blocked: DecryptResult? = null,
    val title: String = "",
    val body: TextFieldValue = TextFieldValue(""),
    val checklist: List<ChecklistDraft> = emptyList(),
    val tags: List<String> = emptyList(),
    val folderId: String? = null,
    val colorIndex: Int = 0,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val trashedAt: Long? = null,
    val isLocked: Boolean = false,
    val lockPinHash: String = "",
    val lockHint: String = "",
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val attachments: List<AttachmentEntity> = emptyList(),
    val reminderAt: Long? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val dirty: Boolean = false,
    val persisted: Boolean = false,
    val busy: Boolean = false
) {
    fun toDraft(id: String) = NoteDraft(
        id = id, title = title, body = body.text, colorIndex = colorIndex, folderId = folderId, isPinned = isPinned,
        isFavorite = isFavorite, isArchived = isArchived, trashedAt = trashedAt, isLocked = isLocked,
        lockPinHash = lockPinHash, lockHint = lockHint, tags = tags, checklist = checklist, createdAt = createdAt
    )
}

sealed interface EditorEvent {
    data object EncryptionFailed : EditorEvent
    data object VaultRequired : EditorEvent
    data class Message(val text: String) : EditorEvent
}

class EditorViewModel(
    private val container: AppContainer,
    private val uid: String,
    val noteId: String,
    private val initial: NoteDraft? = null
) : ViewModel() {
    private val repo = container.repository
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val revisions = repo.observeRevisions(noteId)

    private var history = EditHistory(EditorContent("", TextFieldValue(""), emptyList()))
    private var saveJob: Job? = null
    private val saveMutex = Mutex()
    private var lastSnapshotAt = 0L

    init { load() }

    private fun load() = viewModelScope.launch(Dispatchers.IO) {
        val d = repo.getNote(noteId)
        if (d == null) {
            val draft = initial ?: NoteDraft(id = noteId)
            _state.value = EditorState(
                loading = false, title = draft.title, body = TextFieldValue(draft.body), checklist = draft.checklist,
                tags = draft.tags, folderId = draft.folderId, isFavorite = draft.isFavorite, colorIndex = draft.colorIndex
            )
        } else {
            when (val dec = repo.decrypt(uid, d.note, d.checklist)) {
                is DecryptResult.Plain -> {
                    val n = d.note
                    _state.value = EditorState(
                        loading = false, title = n.title, body = TextFieldValue(dec.body), checklist = dec.checklist,
                        tags = d.tags.map { it.name }, folderId = n.folderId, colorIndex = n.colorIndex, isPinned = n.isPinned,
                        isFavorite = n.isFavorite, isArchived = n.isArchived, trashedAt = n.trashedAt, isLocked = n.isLocked,
                        lockPinHash = n.lockPinHash, lockHint = n.lockHint, createdAt = n.createdAt, updatedAt = n.updatedAt,
                        attachments = d.attachments.sortedBy { it.position }, reminderAt = d.activeReminder?.triggerAt, persisted = true
                    )
                }
                else -> _state.value = EditorState(loading = false, blocked = dec, title = d.note.title, isLocked = true, persisted = true)
            }
        }
        val s = _state.value
        history.reset(EditorContent(s.title, s.body, s.checklist))
    }

    // ------------------------------------------------------------------ content edits
    private fun edit(content: Boolean = true, coalesce: Boolean = true, change: (EditorState) -> EditorState) {
        val before = _state.value
        if (before.loading || before.blocked != null || before.trashedAt != null) return
        var after = change(before).copy(dirty = true)
        if (content) {
            history.record(EditorContent(after.title, after.body, after.checklist), if (coalesce) 900 else 0)
        }
        after = after.copy(canUndo = history.canUndo, canRedo = history.canRedo)
        _state.value = after
        scheduleSave()
    }

    fun setTitle(t: String) = edit { it.copy(title = t.take(200)) }

    fun setBody(v: TextFieldValue) {
        val old = _state.value.body
        if (v.text == old.text) { _state.value = _state.value.copy(body = v); return } // selection only
        val smart = com.example.ui.editor.MarkdownEngine.continueList(old, v) ?: v
        edit { it.copy(body = smart) }
    }

    fun applyBody(transform: (TextFieldValue) -> TextFieldValue) = edit(coalesce = false) { it.copy(body = transform(it.body)) }

    fun addChecklistItem(text: String = "") = edit(coalesce = false) { it.copy(checklist = it.checklist + ChecklistDraft(text = text)) }
    fun updateChecklistItem(id: String, text: String) = edit { s -> s.copy(checklist = s.checklist.map { if (it.id == id) it.copy(text = text.take(1000)) else it }) }
    fun toggleChecklistItem(id: String) = edit(coalesce = false) { s -> s.copy(checklist = s.checklist.map { if (it.id == id) it.copy(checked = !it.checked) else it }) }
    fun removeChecklistItem(id: String) = edit(coalesce = false) { s -> s.copy(checklist = s.checklist.filterNot { it.id == id }) }
    fun moveChecklistItem(from: Int, to: Int) = edit(coalesce = false) { s ->
        val l = s.checklist.toMutableList(); if (from in l.indices && to in l.indices) l.add(to, l.removeAt(from)); s.copy(checklist = l)
    }
    /** Converts the body's list lines into checklist items (or starts an empty checklist). */
    fun convertToChecklist() = edit(coalesce = false) { s ->
        val lines = s.body.text.lines()
        val listLines = lines.filter { Regex("^\\s*([-*•]|\\d+[.)]) ").containsMatchIn(it) }
        if (listLines.isEmpty()) s.copy(checklist = s.checklist + ChecklistDraft(text = ""))
        else s.copy(
            checklist = s.checklist + listLines.map { ChecklistDraft(text = it.replaceFirst(Regex("^\\s*([-*•]|\\d+[.)]) "), "")) },
            body = TextFieldValue(lines.filterNot { it in listLines }.joinToString("\n").trim())
        )
    }

    fun undo() { history.undo()?.let { restore(it) } }
    fun redo() { history.redo()?.let { restore(it) } }
    private fun restore(c: EditorContent) {
        _state.value = _state.value.copy(title = c.title, body = c.body, checklist = c.checklist, dirty = true,
            canUndo = history.canUndo, canRedo = history.canRedo)
        scheduleSave()
    }

    // ------------------------------------------------------------------ properties
    fun setColor(i: Int) = edit(content = false) { it.copy(colorIndex = i) }
    fun togglePin() = edit(content = false) { it.copy(isPinned = !it.isPinned) }
    fun toggleFavorite() = edit(content = false) { it.copy(isFavorite = !it.isFavorite) }
    fun setFolder(id: String?) = edit(content = false) { it.copy(folderId = id) }
    fun setTags(t: List<String>) = edit(content = false) { it.copy(tags = t.distinct()) }
    fun toggleArchive(onDone: () -> Unit) { edit(content = false) { it.copy(isArchived = !it.isArchived, isPinned = false) }; flush(onDone) }

    fun lock(pinHash: String, hint: String) {
        if (!repo.vaultReady(uid)) { _events.tryEmit(EditorEvent.VaultRequired); return }
        edit(content = false) { it.copy(isLocked = true, lockPinHash = pinHash, lockHint = hint) }
        flush()
    }

    fun unlockPermanently() = edit(content = false) { it.copy(isLocked = false, lockPinHash = "", lockHint = "") }

    fun moveToTrash(onDone: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        saveNow()
        if (_state.value.persisted) repo.moveToTrash(uid, noteId)
        withContext(Dispatchers.Main) { onDone() }
    }

    fun restoreFromTrash() = viewModelScope.launch(Dispatchers.IO) {
        repo.restoreFromTrash(uid, noteId)
        _state.value = _state.value.copy(trashedAt = null)
    }

    fun setReminder(at: Long?) = viewModelScope.launch(Dispatchers.IO) {
        ensurePersisted()
        repo.setReminder(uid, noteId, at)
        _state.value = _state.value.copy(reminderAt = at)
    }

    // ------------------------------------------------------------------ attachments
    fun addAttachmentFromUri(uri: Uri, kindHint: String? = null) = viewModelScope.launch(Dispatchers.IO) {
        try {
            _state.value = _state.value.copy(busy = true)
            ensurePersisted()
            val info = repo.attachments.describe(uri)
            val kind = kindHint ?: when {
                info.mime.startsWith("image/") -> AttachmentKind.IMAGE
                info.mime == "application/pdf" -> AttachmentKind.PDF
                info.mime.startsWith("audio/") -> AttachmentKind.AUDIO
                else -> AttachmentKind.FILE
            }
            val input = container.app.contentResolver.openInputStream(uri) ?: return@launch
            repo.addAttachment(uid, noteId, kind, info.name, info.mime, input)
            refreshAttachments()
        } catch (e: VaultLockedException) {
            _events.tryEmit(EditorEvent.VaultRequired)
        } catch (e: Exception) {
            _events.tryEmit(EditorEvent.Message(e.message ?: "error"))
        } finally {
            _state.value = _state.value.copy(busy = false)
        }
    }

    fun addAudio(file: File, durationMs: Long, name: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            ensurePersisted()
            repo.addAttachment(uid, noteId, AttachmentKind.AUDIO, name, "audio/mp4", file.inputStream(), durationMs = durationMs)
            file.delete()
            refreshAttachments()
        } catch (e: VaultLockedException) {
            _events.tryEmit(EditorEvent.VaultRequired)
        }
    }

    fun addLinkCard(url: String, title: String, description: String) = viewModelScope.launch(Dispatchers.IO) {
        ensurePersisted()
        repo.addAttachment(uid, noteId, AttachmentKind.LINK, title.ifBlank { url }, "text/uri-list", null,
            url = url, linkTitle = title, linkDescription = description)
        refreshAttachments()
    }

    fun removeAttachment(id: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.removeAttachment(uid, id); refreshAttachments()
    }

    fun viewableFile(a: AttachmentEntity): File? = try {
        repo.attachments.viewableFile(uid, a.localPath, com.example.data.repository.AttachmentStore.extensionFor(a.displayName, a.mimeType),
            if (a.encrypted) container.keyVault.masterKey(uid) else null)
    } catch (_: Exception) { null }

    private suspend fun refreshAttachments() {
        _state.value = _state.value.copy(attachments = repo.attachmentsFor(noteId), persisted = true)
    }

    // ------------------------------------------------------------------ revisions / templates
    fun decryptRevision(r: RevisionEntity) = repo.decryptRevision(uid, r)

    fun restoreRevision(r: RevisionEntity, onDone: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            saveNow()
            repo.restoreRevision(uid, r)
            load()
            withContext(Dispatchers.Main) { onDone() }
        } catch (_: Exception) {
            _events.tryEmit(EditorEvent.VaultRequired)
        }
    }

    fun saveAsTemplate(name: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.saveTemplate(uid, name, _state.value.toDraft(noteId).copy(isLocked = false))
    }

    // ------------------------------------------------------------------ saving
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(AUTOSAVE_DEBOUNCE_MS)
            saveNow()
        }
    }

    private suspend fun ensurePersisted() {
        if (!_state.value.persisted) saveNow(force = true)
    }

    /** Writes the current draft. Encryption failures abort the write (never plaintext). */
    suspend fun saveNow(force: Boolean = false) = saveMutex.withLock {
        val s = _state.value
        if (s.loading || s.blocked != null) return@withLock
        if (!s.dirty && !force) return@withLock
        val draft = s.toDraft(noteId)
        if (draft.isBlank && !s.persisted && !force) return@withLock
        try {
            val now = System.currentTimeMillis()
            if (s.persisted && (lastSnapshotAt == 0L || now - lastSnapshotAt > SNAPSHOT_INTERVAL_MS)) {
                repo.snapshot(noteId)
                lastSnapshotAt = now
            }
            val saved = repo.save(uid, draft)
            _state.value = _state.value.let {
                if (it.title == s.title && it.body.text == s.body.text && it.checklist == s.checklist) it.copy(dirty = false, persisted = true, updatedAt = saved.updatedAt, createdAt = saved.createdAt)
                else it.copy(persisted = true, createdAt = saved.createdAt)
            }
        } catch (e: VaultLockedException) {
            _events.tryEmit(EditorEvent.VaultRequired)
        } catch (e: CryptoException) {
            _events.tryEmit(EditorEvent.EncryptionFailed)
        }
    }

    /** Saves immediately on an app-wide scope (survives the screen closing). */
    fun flush(onDone: (() -> Unit)? = null) {
        saveJob?.cancel()
        container.scope.launch {
            saveNow()
            discardIfEmptyNew()
            onDone?.let { withContext(Dispatchers.Main) { it() } }
        }
    }

    private suspend fun discardIfEmptyNew() {
        val s = _state.value
        if (s.persisted && s.toDraft(noteId).isBlank && s.attachments.isEmpty() && s.reminderAt == null && s.trashedAt == null && !s.isLocked) {
            val d = repo.getNote(noteId)
            if (d != null && d.note.createdAt >= (s.createdAt ?: 0) && (System.currentTimeMillis() - d.note.createdAt) < 24 * 3600_000L && d.note.baseRemoteUpdatedAt == 0L) {
                repo.deletePermanently(uid, noteId)
            }
        }
    }

    override fun onCleared() {
        flush()
        super.onCleared()
    }

    companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 600L
        const val SNAPSHOT_INTERVAL_MS = 5 * 60_000L
    }
}
