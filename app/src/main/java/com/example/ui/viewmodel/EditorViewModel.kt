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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** One piece of a note's content, in display order. */
sealed interface EditorBlock {
    val id: String
    /** [kind]: BlockType.TEXT, TOGGLE (first line = heading) or CALLOUT. */
    data class Text(override val id: String, val value: TextFieldValue, val kind: String = BlockType.TEXT) : EditorBlock
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
            is EditorBlock.Text -> BlockDraft(b.id, b.kind, b.value.text)
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
        else -> EditorBlock.Text(b.id, TextFieldValue(b.text), if (b.type in BlockType.TEXTUAL) b.type else BlockType.TEXT)
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
    /** «اسأل عن الملاحظة»: conversation about this note only. */
    data class Chat(val turns: List<com.example.core.ai.NoteAssistant.Turn>, val loading: Boolean = false, val error: Throwable? = null) : AssistantUi
    data class RewriteResult(val mode: com.example.core.ai.NoteAssistant.Rewrite, val target: RewriteTarget, val result: String) : AssistantUi
    data class SuggestResult(val value: com.example.core.ai.NoteAssistant.Suggestion) : AssistantUi
}

/** The text a writing tool works on: a selection, or the whole focused text block. */
data class RewriteTarget(val blockId: String, val start: Int, val end: Int, val original: String, val isSelection: Boolean)

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

    /** Assistant tools that work on any (unlocked) note, however short. */
    val assistantReady: Boolean get() = container.assistantReady() && !_state.value.isLocked && _state.value.blocked == null
    val assistantConfigured: Boolean get() = container.assistantReady()

    fun openChat() { _assistant.value = AssistantUi.Chat(emptyList()) }

    fun ask(question: String, rememberConsent: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (!assistantReady || question.isBlank()) return@launch
        if (rememberConsent) container.aiKeys.rememberConsent = true
        val chat = _assistant.value as? AssistantUi.Chat ?: AssistantUi.Chat(emptyList())
        _assistant.value = chat.copy(loading = true, error = null)
        _assistant.value = try {
            val answer = container.assistant.ask(assistantPayload().first, chat.turns, question)
            AssistantUi.Chat(chat.turns + com.example.core.ai.NoteAssistant.Turn(question.trim(), answer))
        } catch (e: Throwable) { chat.copy(loading = false, error = e) }
    }

    fun insertAnswer(question: String, answer: String) = withAssistantSnapshot { s ->
        s.copy(blocks = s.blocks + EditorBlock.Text(Ids.newId(), TextFieldValue("> $question"), com.example.data.local.BlockType.CALLOUT)
            + EditorBlock.Text(Ids.newId(), TextFieldValue(answer)))
    }

    /** What a writing tool would change right now (selection, else the focused/first text block). */
    fun rewriteTarget(): RewriteTarget? {
        val s = _state.value
        val b = s.focusedText ?: s.blocks.firstOrNull { it is EditorBlock.Text && it.value.text.isNotBlank() } as? EditorBlock.Text ?: return null
        val sel = b.value.selection
        return if (!sel.collapsed && b.id == s.focusedBlockId) RewriteTarget(b.id, sel.min, sel.max, b.value.text.substring(sel.min, sel.max), true)
        else b.value.text.takeIf { it.isNotBlank() }?.let { RewriteTarget(b.id, 0, it.length, it, false) }
    }

    fun rewrite(mode: com.example.core.ai.NoteAssistant.Rewrite, target: RewriteTarget, rememberConsent: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (!assistantReady) return@launch
        if (rememberConsent) container.aiKeys.rememberConsent = true
        _assistant.value = AssistantUi.Loading
        _assistant.value = try { AssistantUi.RewriteResult(mode, target, container.assistant.rewrite(target.original, mode)) } catch (e: Throwable) { AssistantUi.Failed(e) }
    }

    /** Replaces the target text (if unchanged since) or inserts the result right after its block. A version is kept first. */
    fun applyRewrite(r: AssistantUi.RewriteResult, replace: Boolean) = withAssistantSnapshot { s ->
        val b = s.blocks.firstOrNull { it.id == r.target.blockId } as? EditorBlock.Text
        val t = b?.value?.text
        if (replace && b != null && t != null && r.target.end <= t.length && t.substring(r.target.start, r.target.end) == r.target.original) {
            val nt = t.substring(0, r.target.start) + r.result + t.substring(r.target.end)
            s.copy(blocks = s.blocks.map { if (it.id == b.id) b.copy(value = TextFieldValue(nt, TextRange(r.target.start + r.result.length))) else it })
        } else {
            val idx = s.blocks.indexOfFirst { it.id == r.target.blockId }
            val nb = EditorBlock.Text(Ids.newId(), TextFieldValue(r.result))
            val list = s.blocks.toMutableList(); if (idx < 0) list += nb else list.add(idx + 1, nb)
            s.copy(blocks = list)
        }
    }

    fun suggest(rememberConsent: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (!assistantReady) return@launch
        if (rememberConsent) container.aiKeys.rememberConsent = true
        _assistant.value = AssistantUi.Loading
        _assistant.value = try {
            val folders = container.db.folderDao().allIncludingDeleted(uid).filter { !it.deleted }.map { it.name }
            val tags = repo.observeTags(uid).first().map { it.name }
            AssistantUi.SuggestResult(container.assistant.suggestMeta(assistantPayload().first, folders, tags))
        } catch (e: Throwable) { AssistantUi.Failed(e) }
    }

    fun applySuggestion(title: String?, tags: List<String>, folderName: String?) = viewModelScope.launch(Dispatchers.IO) {
        val folderId = folderName?.let { repo.findFolderByName(uid, it)?.id }
        withContext(Dispatchers.Main) {
            edit(coalesce = false) { s ->
                s.copy(title = title?.takeIf { it.isNotBlank() } ?: s.title, tags = (s.tags + tags).distinct(), folderId = folderId ?: s.folderId)
            }
        }
        _assistant.value = AssistantUi.Idle
    }

    /** Recordings being transcribed. */
    private val _transcribing = MutableStateFlow<Set<String>>(emptySet())
    val transcribing: StateFlow<Set<String>> = _transcribing.asStateFlow()

    /** Transcribes one recording with Gemini (after consent); the text is stored with the recording and is searchable. */
    fun transcribe(attachmentId: String, rememberConsent: Boolean, summaryLabel: String, failMessage: (Throwable) -> String, doneMessage: String) = viewModelScope.launch(Dispatchers.IO) {
        if (!assistantReady) return@launch
        val a = _state.value.attachments.firstOrNull { it.id == attachmentId } ?: return@launch
        if (a.encrypted || a.kind != AttachmentKind.AUDIO) return@launch
        if (rememberConsent) container.aiKeys.rememberConsent = true
        _transcribing.value = _transcribing.value + attachmentId
        try {
            val f = repo.attachments.file(uid, a.localPath)
            if (f.length() > com.example.core.ai.NoteAssistant.MAX_AUDIO_BYTES) throw com.example.core.ai.AiError.Other("too long")
            val mime = a.mimeType.takeIf { it.startsWith("audio/") }?.let { if (it == "audio/m4a" || it == "audio/x-m4a") "audio/mp4" else it } ?: "audio/mp4"
            val t = container.assistant.transcribe(f, mime)
            val text = buildString {
                if (t.summary.isNotBlank()) append("**").append(summaryLabel).append(":** ").append(t.summary).append("\n\n")
                append(t.text)
            }.trim()
            repo.setExtractedText(a.id, text.ifBlank { com.example.ocr.OcrWorker.NO_TEXT })
            _events.tryEmit(EditorEvent.Message(doneMessage))
        } catch (e: Throwable) {
            com.example.core.Diagnostics.log(container.app, "ai-audio", "Transcription failed", e)
            _events.tryEmit(EditorEvent.Message(failMessage(e)))
        } finally { _transcribing.value = _transcribing.value - attachmentId }
    }

    /** Attachments currently being read by Gemini (precise OCR). */
    private val _preciseOcr = MutableStateFlow<Set<String>>(emptySet())
    val preciseOcr: StateFlow<Set<String>> = _preciseOcr.asStateFlow()
    val preciseOcrAvailable: Boolean get() = container.assistantReady() && !_state.value.isLocked && _state.value.blocked == null

    /** Reads one image accurately with the user's Gemini key (after consent); replaces the on-device text. */
    fun readImagePrecisely(attachmentId: String, rememberConsent: Boolean, failMessage: (Throwable) -> String, doneMessage: String) = viewModelScope.launch(Dispatchers.IO) {
        if (!preciseOcrAvailable) return@launch
        val a = _state.value.attachments.firstOrNull { it.id == attachmentId } ?: return@launch
        if (a.encrypted || a.kind != AttachmentKind.IMAGE) return@launch
        if (rememberConsent) container.aiKeys.rememberConsent = true
        _preciseOcr.value = _preciseOcr.value + attachmentId
        try {
            val text = container.assistant.readImage(repo.attachments.file(uid, a.localPath))
            repo.setExtractedText(a.id, text.ifBlank { com.example.ocr.OcrWorker.NO_TEXT })
            _events.tryEmit(EditorEvent.Message(doneMessage))
        } catch (e: Throwable) {
            com.example.core.Diagnostics.log(container.app, "ai-ocr", "Precise reading failed", e)
            _events.tryEmit(EditorEvent.Message(failMessage(e)))
        } finally { _preciseOcr.value = _preciseOcr.value - attachmentId }
    }

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
        detectTriggers(blockId, old, smart)
        edit { s -> s.copy(blocks = s.blocks.map { if (it.id == blockId && it is EditorBlock.Text) it.copy(value = smart) else it }, focusedBlockId = blockId) }
    }

    private fun replaceBlockQuiet(blockId: String, v: TextFieldValue) {
        val s = _state.value
        _state.value = s.copy(blocks = s.blocks.map { if (it.id == blockId && it is EditorBlock.Text) it.copy(value = v) else it }, focusedBlockId = blockId)
    }

    /** Formatting toolbar: applies to the focused text block. */
    fun applyFormat(transform: (TextFieldValue) -> TextFieldValue) = edit(coalesce = false) { s ->
        val target = s.focusedText ?: return@edit s
        s.copy(blocks = s.blocks.map { if (it.id == target.id) target.copy(value = transform(target.value)) else it })
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
        if (target.kind != BlockType.TEXT) {
            // Toggles and callouts aren't split: the new block goes right after them.
            list.add(idx + 1, block)
            return s.copy(blocks = list)
        }
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

    // ------------------------------------------------------------------ «/» menu, [[links]], special blocks
    /** Where the user just typed «/» at the start of a line, or «[[» (opens the note picker). */
    data class Trigger(val blockId: String, val at: Int, val kind: Kind) { enum class Kind { SLASH, LINK } }
    private val _trigger = MutableStateFlow<Trigger?>(null)
    val trigger: StateFlow<Trigger?> = _trigger.asStateFlow()
    fun dismissTrigger() { _trigger.value = null }

    private fun detectTriggers(blockId: String, old: TextFieldValue, new: TextFieldValue) {
        val t = new.text
        val cur = new.selection.start
        if (new.selection.collapsed && t.length == old.text.length + 1 && cur > 0) {
            val typed = t[cur - 1]
            if (typed == '/' && (cur == 1 || t[cur - 2] == '\n')) { _trigger.value = Trigger(blockId, cur - 1, Trigger.Kind.SLASH); return }
            if (typed == '[' && cur >= 2 && t[cur - 2] == '[') { _trigger.value = Trigger(blockId, cur - 2, Trigger.Kind.LINK); return }
        }
        _trigger.value?.let { tr -> if (tr.blockId != blockId || cur <= tr.at || cur > tr.at + 40) _trigger.value = null }
    }

    /** Removes the «/» or «[[» the user typed (and anything typed after it on that line) before running a command. */
    private fun consumeTrigger(s: EditorState): EditorState {
        val tr = _trigger.value ?: return s
        _trigger.value = null
        val b = s.blocks.firstOrNull { it.id == tr.blockId } as? EditorBlock.Text ?: return s
        val text = b.value.text
        if (tr.at >= text.length) return s
        val end = b.value.selection.start.coerceIn(tr.at, text.length)
        val nt = text.removeRange(tr.at, end)
        return s.copy(blocks = s.blocks.map { if (it.id == b.id) b.copy(value = TextFieldValue(nt, TextRange(tr.at))) else it }, focusedBlockId = b.id)
    }

    /** «/» commands that format the current line. */
    fun slashLinePrefix(prefix: String) = edit(coalesce = false) { s0 ->
        val s = consumeTrigger(s0)
        val b = s.focusedText ?: return@edit s
        s.copy(blocks = s.blocks.map { if (it.id == b.id) b.copy(value = com.example.ui.editor.MarkdownEngine.toggleLinePrefix(b.value, prefix)) else it })
    }

    fun slashChecklist() { edit(coalesce = false) { consumeTrigger(it) }; insertChecklist() }

    /** Inserts an empty toggle or callout block at the cursor and focuses it. */
    fun insertSpecial(kind: String, fromSlash: Boolean = false) = edit(coalesce = false) { s0 ->
        val s = if (fromSlash) consumeTrigger(s0) else s0
        val nb = EditorBlock.Text(Ids.newId(), TextFieldValue(""), kind)
        insertAtCursor(s, nb).copy(focusedBlockId = nb.id)
    }

    fun clearTrigger() { if (_trigger.value != null) edit(coalesce = false) { consumeTrigger(it) } }

    /** Replaces the typed «[[» with a link to [title]. */
    fun insertNoteLink(title: String) = edit(coalesce = false) { s0 ->
        val s = consumeTrigger(s0)
        val b = s.focusedText ?: return@edit s
        val v = b.value
        val link = "[[" + title.replace("]]", "") + "]]"
        val nt = v.text.substring(0, v.selection.min) + link + v.text.substring(v.selection.max)
        s.copy(blocks = s.blocks.map { if (it.id == b.id) b.copy(value = TextFieldValue(nt, TextRange(v.selection.min + link.length))) else it })
    }

    // ------------------------------------------------------------------ sub-notes, icon, links
    data class NoteRef(val id: String, val title: String, val icon: String)
    data class NoteGraph(
        val parent: NoteRef? = null, val children: List<NoteRef> = emptyList(),
        val links: List<NoteRef> = emptyList(), val missingLinks: List<String> = emptyList(),
        val backlinks: List<NoteRef> = emptyList(), val icon: String = "", val all: List<NoteRef> = emptyList()
    )

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val graph: StateFlow<NoteGraph> = kotlinx.coroutines.flow.combine(
        repo.observeNotes(uid),
        _state.map { it.title to it.bodyText }.distinctUntilChanged().debounce(300)
    ) { notes, (title, body) -> buildGraph(noteId, title, body, notes) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), NoteGraph())

    /** Creates a sub-note under this note and returns its id. */
    fun createChild(title: String = "", onCreated: (String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        saveNow(force = true)
        val id = repo.newNoteId()
        repo.save(uid, NoteDraft(id = id, title = title.take(200), parentId = noteId, folderId = _state.value.folderId))
        withContext(Dispatchers.Main) { onCreated(id) }
    }

    /** Creates a top-level note titled [title] (from a [[link]] to a note that doesn't exist yet), unless one exists. */
    fun createLinked(title: String) = viewModelScope.launch(Dispatchers.IO) {
        val t = title.trim().take(200)
        if (t.isEmpty() || graph.value.all.any { com.example.core.text.NoteLinks.key(it.title) == com.example.core.text.NoteLinks.key(t) }) return@launch
        repo.save(uid, NoteDraft(id = repo.newNoteId(), title = t, folderId = _state.value.folderId))
    }

    fun setIcon(icon: String) = viewModelScope.launch(Dispatchers.IO) { saveNow(force = true); repo.setIcon(uid, noteId, icon) }

    /** Moves this note under [parentId] (null = top level). Returns false (via callback) for an impossible move. */
    fun moveUnder(parentId: String?, onResult: (Boolean) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        saveNow(force = true)
        val ok = repo.setParent(uid, noteId, parentId)
        withContext(Dispatchers.Main) { onResult(ok) }
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

/** Parent, children, [[links]] and backlinks of one note (live notes only; locked bodies are never read). */
internal fun buildGraph(noteId: String, title: String, body: String, notes: List<com.example.data.local.NoteWithDetails>): EditorViewModel.NoteGraph {
    val live = notes.filter { it.note.trashedAt == null && !it.note.deleted }
    fun ref(n: com.example.data.local.NoteEntity) = EditorViewModel.NoteRef(n.id, n.title, n.icon)
    val self = live.firstOrNull { it.note.id == noteId }?.note
    val byKey = live.filter { it.note.id != noteId && it.note.title.isNotBlank() }.groupBy { com.example.core.text.NoteLinks.key(it.note.title) }
    val linked = com.example.core.text.NoteLinks.titles(body)
    val links = linked.mapNotNull { t -> byKey[com.example.core.text.NoteLinks.key(t)]?.maxByOrNull { it.note.updatedAt }?.note?.let(::ref) }
    val missing = linked.filter { t -> byKey[com.example.core.text.NoteLinks.key(t)] == null }
    val backlinks = if (title.isBlank()) emptyList() else live.filter { d ->
        d.note.id != noteId && !d.note.isLocked && com.example.core.text.NoteLinks.linksTo(d.note.body, title)
    }.map { ref(it.note) }
    return EditorViewModel.NoteGraph(
        parent = self?.parentId?.let { pid -> live.firstOrNull { it.note.id == pid }?.note?.let(::ref) },
        children = live.filter { it.note.parentId == noteId }.sortedBy { it.note.createdAt }.map { ref(it.note) },
        links = links, missingLinks = missing, backlinks = backlinks, icon = self?.icon.orEmpty(),
        all = live.filter { it.note.id != noteId }.sortedByDescending { it.note.updatedAt }.map { ref(it.note) }
    )
}
