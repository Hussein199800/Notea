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
import com.example.data.local.BlockType
import com.example.data.repository.BlockDraft
import com.example.data.repository.Blocks
import com.example.data.repository.ChecklistDraft
import com.example.data.repository.Ids
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

/** One piece of a note's content, in display order. */
sealed interface EditorBlock {
    val id: String
    data class Text(override val id: String, val value: TextFieldValue) : EditorBlock
    data class Checklist(override val id: String) : EditorBlock
    data class Attachment(override val id: String, val attachmentId: String) : EditorBlock
}

data class EditorContent(val title: String, val blocks: List<EditorBlock>, val checklist: List<ChecklistDraft>)

data class EditorState(
    val loading: Boolean = true,
    val blocked: DecryptResult? = null,
    val title: String = "",
    val blocks: List<EditorBlock> = listOf(EditorBlock.Text(com.example.data.repository.Ids.newId(), TextFieldValue(""))),
    val focusedBlockId: String? = null,
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
    val blockDrafts: List<BlockDraft> get() = blocks.map { b ->
        when (b) {
            is EditorBlock.Text -> BlockDraft(b.id, BlockType.TEXT, b.value.text)
            is EditorBlock.Checklist -> BlockDraft(b.id, BlockType.CHECKLIST)
            is EditorBlock.Attachment -> BlockDraft(b.id, BlockType.ATTACHMENT, attachmentId = b.attachmentId)
        }
    }

    /** Plain text of all text blocks (what search, share and the assistant see). */
    val bodyText: String get() = Blocks.joinText(blockDrafts)

    val focusedText: EditorBlock.Text? get() = blocks.firstOrNull { it.id == focusedBlockId } as? EditorBlock.Text
        ?: blocks.lastOrNull { it is EditorBlock.Text } as? EditorBlock.Text

    fun toDraft(id: String) = NoteDraft(
        id = id, title = title, body = bodyText, colorIndex = colorIndex, folderId = folderId, isPinned = isPinned,
        isFavorite = isFavorite, isArchived = isArchived, trashedAt = trashedAt, isLocked = isLocked,
        lockPinHash = lockPinHash, lockHint = lockHint, tags = tags, checklist = checklist, createdAt = createdAt,
        blocks = blockDrafts
    )
}

private fun List<BlockDraft>.toEditorBlocks(): List<EditorBlock> = map { b ->
    when (b.type) {
        BlockType.CHECKLIST -> EditorBlock.Checklist(b.id)
        BlockType.ATTACHMENT -> EditorBlock.Attachment(b.id, b.attachmentId.orEmpty())
        else -> EditorBlock.Text(b.id, TextFieldValue(b.text))
    }
}

sealed interface AssistantUi {
    data object Idle : AssistantUi
    data object Loading : AssistantUi
    data class SummaryResult(val value: com.example.core.ai.NoteAssistant.Summary) : AssistantUi
    data class TasksResult(val tasks: List<com.example.core.ai.NoteAssistant.Task>) : AssistantUi
    data class PlanResult(val plan: com.example.core.ai.NoteAssistant.Plan) : AssistantUi
    data class CompareResult(val otherTitle: String, val value: com.example.core.ai.NoteAssistant.Comparison) : AssistantUi
    data class Failed(val error: Throwable) : AssistantUi
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

    private val _assistant = MutableStateFlow<AssistantUi>(AssistantUi.Idle)
    val assistant: StateFlow<AssistantUi> = _assistant.asStateFlow()
    private val _related = MutableStateFlow<List<com.example.core.memory.NoteMemory.Related>>(emptyList())
    val related: StateFlow<List<com.example.core.memory.NoteMemory.Related>> = _related.asStateFlow()

    val assistantAvailable: Boolean get() {
        val s = _state.value
        return container.assistantReady() && !s.isLocked && s.blocked == null &&
            (s.title + s.bodyText).length >= com.example.core.ai.NoteAssistant.MIN_NOTE_CHARS
    }

    fun refreshRelated() = viewModelScope.launch(Dispatchers.Default) {
        if (_state.value.isLocked) { _related.value = emptyList(); return@launch }
        _related.value = runCatching { container.memoryIndex(uid).related(noteId) }.getOrDefault(emptyList())
    }

    fun needsConsent() = !container.aiKeys.rememberConsent

    fun assistantPayload(): Pair<String, Boolean> = container.assistant.prepare(_state.value.title, buildString {
        append(_state.value.bodyText)
        _state.value.checklist.forEach { append("\n- ").append(if (it.checked) "[x] " else "[ ] ").append(it.text) }
    })

    fun runAssistant(action: com.example.core.ai.NoteAssistant.Action, rememberConsent: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (!assistantAvailable) return@launch
        if (rememberConsent) container.aiKeys.rememberConsent = true
        _assistant.value = AssistantUi.Loading
        val (text, _) = assistantPayload()
        _assistant.value = try {
            when (action) {
                com.example.core.ai.NoteAssistant.Action.SUMMARY -> AssistantUi.SummaryResult(container.assistant.summarize(text))
                com.example.core.ai.NoteAssistant.Action.TASKS -> AssistantUi.TasksResult(container.assistant.extractTasks(text))
                com.example.core.ai.NoteAssistant.Action.PLAN -> AssistantUi.PlanResult(container.assistant.plan(text))
            }
        } catch (e: Throwable) { AssistantUi.Failed(e) }
    }

    fun compareWith(otherId: String, rememberConsent: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (!container.assistantReady() || _state.value.isLocked) return@launch
        if (rememberConsent) container.aiKeys.rememberConsent = true
        val other = repo.getNote(otherId) ?: return@launch
        if (other.note.isLocked) return@launch
        _assistant.value = AssistantUi.Loading
        val a = assistantPayload().first
        val b = container.assistant.prepare(other.note.title, other.note.body).first
        _assistant.value = try { AssistantUi.CompareResult(other.note.title, container.assistant.compare(a, b)) } catch (e: Throwable) { AssistantUi.Failed(e) }
    }

    fun dismissAssistant() { _assistant.value = AssistantUi.Idle }

    /** Applies assistant output; the previous state is kept as a version first. */
    private fun withAssistantSnapshot(change: (EditorState) -> EditorState) = viewModelScope.launch(Dispatchers.IO) {
        saveNow(force = true)
        repo.snapshot(noteId, com.example.data.local.RevisionReason.ASSISTANT)
        withContext(Dispatchers.Main) { edit(coalesce = false, change = change) }
        _assistant.value = AssistantUi.Idle
    }

    fun insertSummary(label: String, sum: com.example.core.ai.NoteAssistant.Summary) = withAssistantSnapshot { s ->
        val text = buildString {
            append("**").append(label).append(":** ").append(sum.summary)
            sum.points.forEach { append("\n- ").append(it) }
        }
        s.copy(blocks = listOf(EditorBlock.Text(Ids.newId(), TextFieldValue(text))) + s.blocks)
    }

    fun addTasks(tasks: List<String>) = withAssistantSnapshot { s ->
        val blockId = Ids.newId()
        s.copy(blocks = s.blocks + EditorBlock.Checklist(blockId), checklist = s.checklist + tasks.map { ChecklistDraft(text = it, blockId = blockId) })
    }

    fun insertPlan(planLabel: String, goalLabel: String, plan: com.example.core.ai.NoteAssistant.Plan) = withAssistantSnapshot { s ->
        val text = buildString {
            append("## ").append(planLabel)
            if (plan.goal.isNotBlank()) append("\n**").append(goalLabel).append(":** ").append(plan.goal)
            plan.steps.forEachIndexed { i, st -> append("\n").append(i + 1).append(". ").append(st) }
        }
        s.copy(blocks = s.blocks + EditorBlock.Text(Ids.newId(), TextFieldValue(text)))
    }

    private var history = EditHistory(EditorContent("", emptyList(), emptyList()))
    private var saveJob: Job? = null
    private val saveMutex = Mutex()
    private var lastSnapshotAt = 0L

    init {
        load()
        // Attachments change in the background (OCR results, sync, imports): keep blocks consistent.
        viewModelScope.launch {
            repo.observeAttachments(noteId).collect { list ->
                val s = _state.value
                if (s.loading || s.blocked != null) return@collect
                val ids = list.map { it.id }
                val reconciled = Blocks.reconcile(s.blockDrafts, s.bodyText, s.checklist, ids)
                val changedLayout = reconciled.map { it.id } != s.blocks.map { it.id }
                _state.value = s.copy(attachments = list, blocks = if (changedLayout) mergeLayout(s.blocks, reconciled) else s.blocks)
            }
        }
    }

    /** Applies a reconciled layout while keeping the live TextFieldValues of existing text blocks. */
    private fun mergeLayout(current: List<EditorBlock>, layout: List<BlockDraft>): List<EditorBlock> {
        val byId = current.associateBy { it.id }
        return layout.map { b -> byId[b.id] ?: listOf(b).toEditorBlocks().single() }
    }

    private fun load() = viewModelScope.launch(Dispatchers.IO) {
        val d = repo.getNote(noteId)
        if (d == null) {
            val draft = initial ?: NoteDraft(id = noteId)
            _state.value = EditorState(
                loading = false, title = draft.title, checklist = draft.checklist,
                blocks = Blocks.reconcile(draft.blocks, draft.body, draft.checklist, emptyList()).toEditorBlocks(),
                tags = draft.tags, folderId = draft.folderId, isFavorite = draft.isFavorite, colorIndex = draft.colorIndex
            )
        } else {
            when (val dec = repo.decrypt(uid, d.note, d.checklist, d.blocks)) {
                is DecryptResult.Plain -> {
                    val n = d.note
                    val attachments = d.attachments.sortedBy { it.position }
                    _state.value = EditorState(
                        loading = false, title = n.title, checklist = dec.checklist,
                        blocks = Blocks.reconcile(dec.blocks, dec.body, dec.checklist, attachments.map { it.id }).toEditorBlocks(),
                        tags = d.tags.map { it.name }, folderId = n.folderId, colorIndex = n.colorIndex, isPinned = n.isPinned,
                        isFavorite = n.isFavorite, isArchived = n.isArchived, trashedAt = n.trashedAt, isLocked = n.isLocked,
                        lockPinHash = n.lockPinHash, lockHint = n.lockHint, createdAt = n.createdAt, updatedAt = n.updatedAt,
                        attachments = attachments, reminderAt = d.activeReminder?.triggerAt, persisted = true
                    )
                }
                else -> _state.value = EditorState(loading = false, blocked = dec, title = d.note.title, isLocked = true, persisted = true)
            }
        }
        val s = _state.value
        history.reset(EditorContent(s.title, s.blocks, s.checklist))
    }

    // ------------------------------------------------------------------ content edits
    private fun edit(content: Boolean = true, coalesce: Boolean = true, change: (EditorState) -> EditorState) {
        val before = _state.value
        if (before.loading || before.blocked != null || before.trashedAt != null) return
        var after = change(before).copy(dirty = true)
        if (content) {
            history.record(EditorContent(after.title, after.blocks, after.checklist), if (coalesce) 900 else 0)
        }
        after = after.copy(canUndo = history.canUndo, canRedo = history.canRedo)
        _state.value = after
        scheduleSave()
    }

    fun setTitle(t: String) = edit { it.copy(title = t.take(200)) }

    fun focus(blockId: String) { _state.value = _state.value.copy(focusedBlockId = blockId) }

    fun setText(blockId: String, v: TextFieldValue) {
        val old = (_state.value.blocks.firstOrNull { it.id == blockId } as? EditorBlock.Text)?.value ?: return
        if (v.text == old.text) { replaceBlockQuiet(blockId, v); return } // selection only
        val smart = com.example.ui.editor.MarkdownEngine.continueList(old, v) ?: v
        edit { s -> s.copy(blocks = s.blocks.map { if (it.id == blockId) EditorBlock.Text(blockId, smart) else it }, focusedBlockId = blockId) }
    }

    private fun replaceBlockQuiet(blockId: String, v: TextFieldValue) {
        val s = _state.value
        _state.value = s.copy(blocks = s.blocks.map { if (it.id == blockId) EditorBlock.Text(blockId, v) else it }, focusedBlockId = blockId)
    }

    /** Formatting toolbar: applies to the focused text block. */
    fun applyFormat(transform: (TextFieldValue) -> TextFieldValue) = edit(coalesce = false) { s ->
        val target = s.focusedText ?: return@edit s
        s.copy(blocks = s.blocks.map { if (it.id == target.id) EditorBlock.Text(it.id, transform(target.value)) else it })
    }

    /**
     * Inserts [block] at the cursor: the focused text block is split in two around it, so content
     * can be placed anywhere in the note. Without focus it goes to the end.
     */
    private fun insertAtCursor(s: EditorState, block: EditorBlock): EditorState {
        val target = s.focusedText
        val list = s.blocks.toMutableList()
        if (target == null) {
            list += block; list += EditorBlock.Text(Ids.newId(), TextFieldValue(""))
            return s.copy(blocks = list)
        }
        val idx = list.indexOfFirst { it.id == target.id }
        val text = target.value.text
        val cut = target.value.selection.max.coerceIn(0, text.length)
        val before = text.substring(0, cut).trimEnd('\n')
        val after = text.substring(cut).trimStart('\n')
        val afterBlock = EditorBlock.Text(Ids.newId(), TextFieldValue(after))
        list[idx] = EditorBlock.Text(target.id, TextFieldValue(before, androidx.compose.ui.text.TextRange(before.length)))
        list.add(idx + 1, block)
        list.add(idx + 2, afterBlock)
        return s.copy(blocks = list, focusedBlockId = afterBlock.id)
    }

    private suspend fun insertAttachmentBlock(attachmentId: String) = withContext(Dispatchers.Main) {
        if (_state.value.blocks.any { it is EditorBlock.Attachment && it.attachmentId == attachmentId }) {
            // Already appended by the attachment observer: move it to the cursor instead.
            edit(coalesce = false) { s ->
                val existing = s.blocks.first { it is EditorBlock.Attachment && it.attachmentId == attachmentId }
                insertAtCursor(s.copy(blocks = s.blocks - existing), existing)
            }
        } else edit(coalesce = false) { s -> insertAtCursor(s, EditorBlock.Attachment(Ids.newId(), attachmentId)) }
    }

    fun moveBlock(blockId: String, delta: Int) = edit(coalesce = false) { s ->
        val l = s.blocks.toMutableList()
        val i = l.indexOfFirst { it.id == blockId }
        val j = i + delta
        if (i < 0 || j !in l.indices) s else { l.add(j, l.removeAt(i)); s.copy(blocks = l) }
    }

    /** Removes a checklist block (and its items) or an empty text block. Attachments use [removeAttachment]. */
    fun removeBlock(blockId: String) = edit(coalesce = false) { s ->
        val b = s.blocks.firstOrNull { it.id == blockId } ?: return@edit s
        val rest = s.blocks - b
        s.copy(
            blocks = rest.ifEmpty { listOf(EditorBlock.Text(Ids.newId(), TextFieldValue(""))) },
            checklist = if (b is EditorBlock.Checklist) s.checklist.filterNot { (it.blockId ?: Blocks.DEFAULT_CHECKLIST) == b.id } else s.checklist
        )
    }

    private fun itemsOf(s: EditorState, blockId: String) = s.checklist.filter { (it.blockId ?: Blocks.DEFAULT_CHECKLIST) == blockId }

    fun checklistItems(blockId: String): List<ChecklistDraft> = itemsOf(_state.value, blockId)

    fun addChecklistItem(blockId: String, text: String = "") = edit(coalesce = false) { it.copy(checklist = it.checklist + ChecklistDraft(text = text, blockId = blockId)) }
    fun updateChecklistItem(id: String, text: String) = edit { s -> s.copy(checklist = s.checklist.map { if (it.id == id) it.copy(text = text.take(1000)) else it }) }
    fun toggleChecklistItem(id: String) = edit(coalesce = false) { s -> s.copy(checklist = s.checklist.map { if (it.id == id) it.copy(checked = !it.checked) else it }) }
    fun removeChecklistItem(id: String) = edit(coalesce = false) { s -> s.copy(checklist = s.checklist.filterNot { it.id == id }) }

    /**
     * Toolbar "checklist": list lines selected in the focused text block become a checklist at that
     * place; otherwise an empty checklist is inserted at the cursor.
     */
    fun insertChecklist() = edit(coalesce = false) { s ->
        val target = s.focusedText
        val blockId = Ids.newId()
        val listRegex = Regex("^\\s*([-*•]|\\d+[.)]) ")
        if (target != null && !target.value.selection.collapsed) {
            val t = target.value.text
            val start = t.lastIndexOf('\n', (target.value.selection.min - 1).coerceAtLeast(0)).let { if (target.value.selection.min == 0 || it < 0) 0 else it + 1 }
            val end = t.indexOf('\n', target.value.selection.max).let { if (it < 0) t.length else it }
            val lines = t.substring(start, end).lines().filter { it.isNotBlank() }
            val items = lines.map { ChecklistDraft(text = it.replaceFirst(listRegex, ""), blockId = blockId) }
            val idx = s.blocks.indexOfFirst { it.id == target.id }
            val list = s.blocks.toMutableList()
            val before = t.substring(0, start).trimEnd('\n'); val after = t.substring(end).trimStart('\n')
            list[idx] = EditorBlock.Text(target.id, TextFieldValue(before))
            list.add(idx + 1, EditorBlock.Checklist(blockId))
            list.add(idx + 2, EditorBlock.Text(Ids.newId(), TextFieldValue(after)))
            s.copy(blocks = list, checklist = s.checklist + items)
        } else {
            insertAtCursor(s, EditorBlock.Checklist(blockId)).let { it.copy(checklist = it.checklist + ChecklistDraft(text = "", blockId = blockId)) }
        }
    }

    /** Inserts plain text (e.g. OCR result) as a text block right after [afterBlockId]. */
    fun insertTextAfter(afterBlockId: String, text: String) = edit(coalesce = false) { s ->
        val idx = s.blocks.indexOfFirst { it.id == afterBlockId }
        val list = s.blocks.toMutableList()
        val nb = EditorBlock.Text(Ids.newId(), TextFieldValue(text))
        if (idx < 0) list += nb else list.add(idx + 1, nb)
        s.copy(blocks = list, focusedBlockId = nb.id)
    }

    fun undo() { history.undo()?.let { restore(it) } }
    fun redo() { history.redo()?.let { restore(it) } }
    private fun restore(c: EditorContent) {
        _state.value = _state.value.copy(title = c.title, blocks = c.blocks, checklist = c.checklist, dirty = true,
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
            // Copy to a temp file first so images can be normalized (HEIC/AVIF/TIFF -> JPEG/PNG).
            val tmp = File(container.app.cacheDir, "attach_" + System.nanoTime())
            container.app.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } } ?: return@launch
            var name = info.name; var mime = info.mime; var file = tmp
            val wantsImage = kindHint == AttachmentKind.IMAGE || com.example.core.media.ImageNormalizer.isImageName(info.name, info.mime)
            var isImage = false
            if (wantsImage) {
                val p = com.example.core.media.ImageNormalizer.prepare(tmp, info.name, info.mime)
                name = p.name; mime = p.mime; file = p.file; isImage = p.isImage
            }
            val kind = when {
                isImage -> AttachmentKind.IMAGE
                mime == "application/pdf" -> AttachmentKind.PDF
                mime.startsWith("audio/") -> AttachmentKind.AUDIO
                else -> AttachmentKind.FILE
            }
            val added = repo.addAttachment(uid, noteId, kind, name, mime, file.inputStream())
            tmp.delete(); if (file != tmp) file.delete()
            refreshAttachments()
            insertAttachmentBlock(added.id)
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
            val added = repo.addAttachment(uid, noteId, AttachmentKind.AUDIO, name, "audio/mp4", file.inputStream(), durationMs = durationMs)
            file.delete()
            refreshAttachments()
            insertAttachmentBlock(added.id)
        } catch (e: VaultLockedException) {
            _events.tryEmit(EditorEvent.VaultRequired)
        }
    }

    fun addLinkCard(url: String, title: String, description: String) = viewModelScope.launch(Dispatchers.IO) {
        ensurePersisted()
        val added = repo.addAttachment(uid, noteId, AttachmentKind.LINK, title.ifBlank { url }, "text/uri-list", null,
            url = url, linkTitle = title, linkDescription = description)
        refreshAttachments()
        insertAttachmentBlock(added.id)
    }

    fun removeAttachment(id: String) = viewModelScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            edit(coalesce = false) { s -> s.copy(blocks = s.blocks.filterNot { it is EditorBlock.Attachment && it.attachmentId == id }) }
        }
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

    /** Saves the current state as a named version (kept forever, never trimmed). */
    fun saveNamedVersion(label: String) = viewModelScope.launch(Dispatchers.IO) {
        saveNow(force = _state.value.persisted.not())
        repo.snapshot(noteId, com.example.data.local.RevisionReason.NAMED, label.ifBlank { "•" })
    }

    fun renameVersion(id: String, label: String) = viewModelScope.launch(Dispatchers.IO) { repo.renameRevision(id, label) }

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
                if (it.title == s.title && it.blocks == s.blocks && it.checklist == s.checklist) it.copy(dirty = false, persisted = true, updatedAt = saved.updatedAt, createdAt = saved.createdAt)
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
