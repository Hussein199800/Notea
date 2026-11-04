package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.core.prefs.NoteSort
import com.example.core.text.ArabicNormalizer
import com.example.data.local.AttachmentKind
import com.example.data.local.FolderEntity
import com.example.data.local.NoteWithDetails
import com.example.data.local.TagEntity
import com.example.data.repository.DecryptResult
import com.example.data.repository.NoteDraft
import com.example.ui.editor.MarkdownEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class HomeView { ALL, FAVORITES, REMINDERS, ARCHIVE, TRASH }
enum class NoteType { CHECKLIST, IMAGES, AUDIO, FILES, LINKS, REMINDER, LOCKED }
enum class DateRange { ANY, TODAY, WEEK, MONTH, YEAR }

data class HomeFilter(
    val view: HomeView = HomeView.ALL,
    val folderId: String? = null,
    val tagId: String? = null,
    val query: String = "",
    val types: Set<NoteType> = emptySet(),
    val date: DateRange = DateRange.ANY,
    val color: Int? = null
) {
    val hasRefinements get() = types.isNotEmpty() || date != DateRange.ANY || color != null
    val isSearching get() = query.isNotBlank() || hasRefinements
}

data class NoteCardModel(
    val details: NoteWithDetails,
    val preview: String,
    val firstImagePath: String?,
    val counts: Map<String, Int>
) {
    val note get() = details.note
}

data class HomeState(
    val loading: Boolean = true,
    val pinned: List<NoteCardModel> = emptyList(),
    val others: List<NoteCardModel> = emptyList(),
    val totalInView: Int = 0
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer, private val uid: String) : ViewModel() {
    private val repo = container.repository

    private val _filter = MutableStateFlow(HomeFilter())
    val filter: StateFlow<HomeFilter> = _filter.asStateFlow()

    val folders: StateFlow<List<FolderEntity>> = repo.observeFolders(uid).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tags: StateFlow<List<TagEntity>> = repo.observeTags(uid).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val templates = repo.observeTemplates(uid).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val searchIds = _filter.map { it.query.trim() }.distinctUntilChanged().debounce(120).mapLatest { q ->
        val match = ArabicNormalizer.toFtsQuery(q) ?: return@mapLatest null
        runCatching { container.db.searchDao().search(uid, match).toSet() }.getOrDefault(emptySet())
    }.flowOn(Dispatchers.IO)

    val state: StateFlow<HomeState> = combine(repo.observeNotes(uid), _filter, searchIds, container.settings.state) { notes, f, ids, prefs ->
        val now = System.currentTimeMillis()
        val inView = notes.filter { d ->
            val n = d.note
            when (f.view) {
                HomeView.TRASH -> n.trashedAt != null
                HomeView.ARCHIVE -> n.trashedAt == null && n.isArchived
                HomeView.FAVORITES -> n.trashedAt == null && n.isFavorite
                HomeView.REMINDERS -> n.trashedAt == null && d.activeReminder != null
                HomeView.ALL -> n.trashedAt == null && !n.isArchived
            } && (f.folderId == null || n.folderId == f.folderId) && (f.tagId == null || d.tags.any { it.id == f.tagId })
        }
        val filtered = inView.filter { d ->
            (ids == null || d.note.id in ids) &&
                (f.color == null || d.note.colorIndex == f.color) &&
                matchesDate(d.note.updatedAt, f.date, now) &&
                f.types.all { t -> matchesType(d, t) }
        }
        val sorted = when (if (f.view == HomeView.REMINDERS) null else prefs.sort) {
            null -> filtered.sortedBy { it.activeReminder?.triggerAt ?: Long.MAX_VALUE }
            NoteSort.MODIFIED -> filtered.sortedByDescending { it.note.updatedAt }
            NoteSort.CREATED -> filtered.sortedByDescending { it.note.createdAt }
            NoteSort.TITLE -> filtered.sortedBy { it.note.title.lowercase() }
            NoteSort.COLOR -> filtered.sortedWith(compareBy<NoteWithDetails> { it.note.colorIndex == 0 }.thenBy { it.note.colorIndex })
        }
        val models = sorted.map { toCard(it) }
        val showPinned = f.view == HomeView.ALL && !f.isSearching
        HomeState(
            loading = false,
            pinned = if (showPinned) models.filter { it.note.isPinned } else emptyList(),
            others = if (showPinned) models.filter { !it.note.isPinned } else models,
            totalInView = inView.size
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    private fun toCard(d: NoteWithDetails): NoteCardModel {
        val n = d.note
        val preview = if (n.isLocked) "" else MarkdownEngine.plain(n.body).take(400)
        val img = if (n.isLocked) null else d.attachments.firstOrNull { it.kind == AttachmentKind.IMAGE && !it.encrypted && it.localPath.isNotBlank() }
            ?.let { repo.attachments.file(uid, it.localPath).path }
        return NoteCardModel(d, preview, img, d.attachments.groupingBy { it.kind }.eachCount())
    }

    private fun matchesType(d: NoteWithDetails, t: NoteType) = when (t) {
        NoteType.CHECKLIST -> d.checklist.isNotEmpty()
        NoteType.IMAGES -> d.attachments.any { it.kind == AttachmentKind.IMAGE }
        NoteType.AUDIO -> d.attachments.any { it.kind == AttachmentKind.AUDIO }
        NoteType.FILES -> d.attachments.any { it.kind == AttachmentKind.PDF || it.kind == AttachmentKind.FILE }
        NoteType.LINKS -> d.attachments.any { it.kind == AttachmentKind.LINK } || d.note.body.contains("http")
        NoteType.REMINDER -> d.activeReminder != null
        NoteType.LOCKED -> d.note.isLocked
    }

    private fun matchesDate(ts: Long, r: DateRange, now: Long): Boolean {
        if (r == DateRange.ANY) return true
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val from = when (r) {
            DateRange.TODAY -> cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            DateRange.WEEK -> now - 7L * 86_400_000
            DateRange.MONTH -> now - 30L * 86_400_000
            DateRange.YEAR -> cal.apply { set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis
            DateRange.ANY -> 0L
        }
        return ts >= from
    }

    fun setFilter(change: (HomeFilter) -> HomeFilter) { _filter.value = change(_filter.value) }
    fun showView(v: HomeView) { _filter.value = HomeFilter(view = v) }
    fun showFolder(id: String?) { _filter.value = HomeFilter(folderId = id) }
    fun showTag(id: String?) { _filter.value = HomeFilter(tagId = id) }

    // Actions
    private fun io(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block() }
    fun togglePin(n: NoteCardModel) = io { repo.setPinned(uid, n.note.id, !n.note.isPinned) }
    fun toggleFavorite(n: NoteCardModel) = io { repo.setFavorite(uid, n.note.id, !n.note.isFavorite) }
    fun toggleArchive(n: NoteCardModel) = io { repo.setArchived(uid, n.note.id, !n.note.isArchived) }
    fun trash(id: String) = io { repo.moveToTrash(uid, id) }
    fun restore(id: String) = io { repo.restoreFromTrash(uid, id) }
    fun deleteForever(id: String) = io { repo.deletePermanently(uid, id) }
    fun emptyTrash() = io { repo.emptyTrash(uid) }
    fun setColor(id: String, c: Int) = io { repo.setColor(uid, id, c) }
    fun moveToFolder(id: String, folderId: String?) = io { repo.moveToFolder(uid, id, folderId) }
    fun createFolder(name: String) = io { repo.createFolder(uid, name) }
    fun renameFolder(id: String, name: String) = io { repo.renameFolder(uid, id, name) }
    fun deleteFolder(id: String) = io {
        repo.deleteFolder(uid, id)
        if (_filter.value.folderId == id) _filter.value = HomeFilter()
    }
    fun renameTag(id: String, name: String) = io { repo.renameTag(uid, id, name) }
    fun deleteTemplate(id: String) = io { repo.deleteTemplate(id) }

    /** Creates a note (optionally from a template / shared text) and returns its id via [onCreated]. */
    fun createNote(draft: (String) -> NoteDraft, onCreated: (String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val id = repo.newNoteId()
        val f = _filter.value
        val d = draft(id).let { it.copy(folderId = it.folderId ?: f.folderId, isFavorite = it.isFavorite || f.view == HomeView.FAVORITES) }
        if (!d.isBlank) repo.save(uid, d)
        kotlinx.coroutines.withContext(Dispatchers.Main) { onCreated(id) }
    }

    fun plainTextForShare(d: NoteWithDetails): String? {
        val dec = repo.decrypt(uid, d.note, d.checklist) as? DecryptResult.Plain ?: return null
        return buildString {
            if (d.note.title.isNotBlank()) appendLine(d.note.title).appendLine()
            append(MarkdownEngine.plain(dec.body))
            if (dec.checklist.isNotEmpty()) {
                appendLine().appendLine()
                dec.checklist.forEach { appendLine((if (it.checked) "☑ " else "☐ ") + it.text) }
            }
        }.trim()
    }
}
