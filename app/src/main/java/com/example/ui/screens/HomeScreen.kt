package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.prefs.NoteSort
import com.example.data.local.FolderEntity
import com.example.data.repository.ChecklistDraft
import com.example.data.repository.NoteDraft
import com.example.data.repository.checklistFromJson
import com.example.ui.components.ColorPicker
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.NoteCard
import com.example.ui.components.TextInputDialog
import com.example.ui.components.VerifyPinDialog
import com.example.ui.i18n.LocalStrings
import com.example.ui.i18n.Strings
import com.example.ui.platform.DeviceCredential
import com.example.ui.platform.FileOpener
import com.example.ui.viewmodel.DateRange
import com.example.ui.viewmodel.HomeView
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.NoteCardModel
import com.example.ui.viewmodel.NoteType
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.SessionViewModel
import kotlinx.coroutines.launch

data class BuiltInTemplate(val name: String, val title: String, val body: String, val checklist: List<String>, val tags: List<String> = emptyList())

fun builtInTemplates(s: Strings): List<BuiltInTemplate> = if (s.isArabic) listOf(
    BuiltInTemplate(s.tplMeeting, "اجتماع: ", "**التاريخ:** \n**الحضور:** \n\n## جدول الأعمال\n- \n\n## القرارات\n- ", listOf("متابعة المهام")),
    BuiltInTemplate(s.tplDaily, "يوميات", "## ما الذي أنجزته اليوم؟\n- \n\n## ما الذي أشكر عليه؟\n- \n\n## أولويات الغد\n1. ", emptyList()),
    BuiltInTemplate(s.tplShopping, "قائمة تسوق", "", listOf("خبز", "حليب", "خضار")),
    BuiltInTemplate(s.tplProject, "مشروع: ", "## الهدف\n\n## المراحل\n1. \n\n## الموارد\n- ", listOf("تحديد النطاق", "الخطة الزمنية")),
    BuiltInTemplate(s.tplDecision, "قرار: ", "## القرار\n\n## لماذا؟\n- \n\n## البدائل التي استبعدتها\n- \n\n## متى أراجعه؟\n", emptyList(), listOf("قرار"))
) else listOf(
    BuiltInTemplate(s.tplMeeting, "Meeting: ", "**Date:** \n**Attendees:** \n\n## Agenda\n- \n\n## Decisions\n- ", listOf("Follow-ups")),
    BuiltInTemplate(s.tplDaily, "Journal", "## What did I get done today?\n- \n\n## Grateful for\n- \n\n## Tomorrow's priorities\n1. ", emptyList()),
    BuiltInTemplate(s.tplShopping, "Shopping list", "", listOf("Bread", "Milk", "Vegetables")),
    BuiltInTemplate(s.tplProject, "Project: ", "## Goal\n\n## Milestones\n1. \n\n## Resources\n- ", listOf("Define scope", "Timeline")),
    BuiltInTemplate(s.tplDecision, "Decision: ", "## Decision\n\n## Why?\n- \n\n## Alternatives I ruled out\n- \n\n## When to review\n", emptyList(), listOf("decision"))
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(session: SessionViewModel, vm: HomeViewModel, selectedNoteId: String?, onOpenNote: (String, Boolean) -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val filter by vm.filter.collectAsState()
    val folders by vm.folders.collectAsState()
    val tags by vm.tags.collectAsState()
    val prefs by session.prefs.collectAsState()
    val memory by vm.memory.collectAsState()
    val scope = rememberCoroutineScope()

    var actionsFor by remember { mutableStateOf<NoteCardModel?>(null) }
    // Multi-select: long press starts it, taps add/remove, back or ✕ ends it.
    var selection by remember { mutableStateOf(emptySet<String>()) }
    fun toggle(id: String) { selection = if (id in selection) selection - id else selection + id }
    androidx.compose.runtime.LaunchedEffect(filter.view, filter.folderId, filter.tagId) { selection = emptySet() }
    var unlockFor by remember { mutableStateOf<NoteCardModel?>(null) }
    var showTemplates by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var confirmDeleteMany by remember { mutableStateOf<List<String>?>(null) }

    val deviceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val target = unlockFor
        if (DeviceCredential.isOk(r.resultCode) && target != null) { session.markOpened(target.note.id); onOpenNote(target.note.id, false) }
        unlockFor = null
    }

    fun open(m: NoteCardModel) {
        if (m.note.isLocked && !session.isOpened(m.note.id)) {
            if (m.note.lockPinHash.isBlank()) {
                val i = DeviceCredential.intent(context, s.unlockNoteTitle)
                if (i != null) { unlockFor = m; deviceLauncher.launch(i) } else { session.markOpened(m.note.id); onOpenNote(m.note.id, false) }
            } else unlockFor = m
        } else onOpenNote(m.note.id, false)
    }

    val viewTitle = when {
        filter.folderId != null -> folders.firstOrNull { it.id == filter.folderId }?.name ?: s.folders
        filter.tagId != null -> "#" + (tags.firstOrNull { it.id == filter.tagId }?.name ?: "")
        else -> when (filter.view) {
            HomeView.ALL -> s.allNotes; HomeView.FAVORITES -> s.favorites; HomeView.REMINDERS -> s.reminders
            HomeView.ARCHIVE -> s.archive; HomeView.TRASH -> s.trash
        }
    }

    // Back first clears a search, then leaves a folder/tag/archive/trash view (back to all notes).
    androidx.activity.compose.BackHandler(enabled = filter.query.isNotEmpty() || filter.folderId != null || filter.tagId != null ||
        filter.view !in setOf(HomeView.ALL, HomeView.FAVORITES)) {
        if (filter.query.isNotEmpty()) vm.setFilter { it.copy(query = "") } else vm.showView(HomeView.ALL)
    }

    androidx.activity.compose.BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    run {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // Search bar
                Surface(
                    shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            if (filter.query.isEmpty()) Text(s.searchHint, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                            BasicTextField(
                                value = filter.query, onValueChange = { q -> vm.setFilter { it.copy(query = q.replace("\n", " ")) } }, maxLines = 4,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (filter.query.isNotEmpty()) IconButton(onClick = { vm.setFilter { it.copy(query = "") } }) { Icon(Icons.Rounded.Close, s.clearFilters) }
                        else Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { showFilters = true }) {
                            Icon(Icons.Rounded.Tune, s.filters, tint = if (filter.hasRefinements) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (selection.isNotEmpty()) {
                    val all = state.pinned + state.others
                    val picked = all.filter { it.note.id in selection }
                    val ids = picked.map { it.note.id }
                    SelectionBar(
                        count = picked.size, inTrash = filter.view == HomeView.TRASH,
                        allPinned = picked.all { it.note.isPinned }, allFavorite = picked.all { it.note.isFavorite }, allArchived = picked.all { it.note.isArchived },
                        onClose = { selection = emptySet() },
                        onSelectAll = { selection = all.map { it.note.id }.toSet() },
                        onPin = { vm.setPinnedMany(ids, !picked.all { it.note.isPinned }); selection = emptySet() },
                        onFavorite = { vm.setFavoriteMany(ids, !picked.all { it.note.isFavorite }); selection = emptySet() },
                        onArchive = { vm.setArchivedMany(ids, !picked.all { it.note.isArchived }); selection = emptySet() },
                        onTrash = {
                            vm.trashMany(ids); selection = emptySet()
                            session.messageWithUndo(if (ids.size == 1) s.movedToTrash else s.movedManyToTrash(ids.size)) { vm.restoreMany(ids) }
                        },
                        onRestore = { vm.restoreMany(ids); selection = emptySet() },
                        onDeleteForever = { confirmDeleteMany = ids },
                        onMore = if (picked.size == 1) ({ actionsFor = picked.first(); selection = emptySet() }) else null
                    )
                }
                // Title row
                val filtered = filter.folderId != null || filter.tagId != null || filter.view !in setOf(HomeView.ALL, HomeView.FAVORITES)
                Row(Modifier.fillMaxWidth().padding(start = if (filtered) 4.dp else 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (filtered) IconButton(onClick = { vm.showView(HomeView.ALL) }) { Icon(Icons.Rounded.Close, s.allNotes) }
                    Text(viewTitle, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
                    if (filter.isSearching) Text(s.results(state.pinned.size + state.others.size), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var sortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, s.sort) }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            listOf(NoteSort.MODIFIED to s.sortModified, NoteSort.CREATED to s.sortCreated, NoteSort.TITLE to s.sortTitle, NoteSort.COLOR to s.sortColor).forEach { (o, l) ->
                                DropdownMenuItem(text = { Text(l, color = if (prefs.sort == o) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                    onClick = { session.updatePrefs { it.copy(sort = o) }; sortMenu = false })
                            }
                        }
                    }
                    IconButton(onClick = { session.updatePrefs { it.copy(gridView = !it.gridView) } }) {
                        Icon(if (prefs.gridView) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView, if (prefs.gridView) s.listView else s.gridView)
                    }
                    if (filter.view == HomeView.TRASH && state.others.isNotEmpty()) TextButton(onClick = { confirmEmptyTrash = true }) { Text(s.emptyTrash, color = MaterialTheme.colorScheme.error) }
                }
                if (!filtered && !filter.isSearching && selection.isEmpty()) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filter.view == HomeView.ALL, onClick = { vm.showView(HomeView.ALL) }, label = { Text(s.allNotes) })
                    FilterChip(filter.view == HomeView.FAVORITES, onClick = { vm.showView(HomeView.FAVORITES) }, label = { Text(s.favorites) },
                        leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(16.dp)) })
                }
                if (filter.hasRefinements) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(true, onClick = { vm.setFilter { it.copy(types = emptySet(), date = DateRange.ANY, color = null) } }, label = { Text(s.clearFilters) },
                            leadingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) }) }
                    }
                }
                if (filter.view == HomeView.TRASH) com.example.ui.components.ExpandableText(s.trashBanner, collapsedLines = 2, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

                val empty = !state.loading && state.pinned.isEmpty() && state.others.isEmpty()
                if (empty) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            filter.isSearching -> EmptyState(Icons.Rounded.Search, s.emptySearch, null)
                            filter.view == HomeView.ALL && filter.folderId == null && filter.tagId == null ->
                                EmptyState(Icons.Rounded.Notes, s.emptyAllTitle, s.emptyAllBody) {
                                    TextButton(onClick = { session.navigate(Screen.ImportCenter) }) { Text(s.importCenter) }
                                }
                            else -> EmptyState(Icons.Rounded.ViewQuilt, s.emptyFiltered, null)
                        }
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = if (prefs.gridView) StaggeredGridCells.Adaptive(168.dp) else StaggeredGridCells.Fixed(1),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalItemSpacing = 10.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (memory != null && filter.view == HomeView.ALL && !filter.isSearching && filter.folderId == null && filter.tagId == null) {
                            item(span = StaggeredGridItemSpan.FullLine, key = "memory") {
                                MemoryCard(memory!!, onOpen = { onOpenNote(memory!!.noteId, false) }, onHide = { vm.dismissMemory(memory!!.noteId) })
                            }
                        }
                        if (state.pinned.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) { Header(s.pinned) }
                            items(state.pinned, key = { it.note.id }) { m ->
                                NoteCard(m, m.note.id == selectedNoteId || m.note.id in selection, Modifier.animateItem(),
                                onClick = { if (selection.isNotEmpty()) toggle(m.note.id) else open(m) }, onLongClick = { toggle(m.note.id) })
                            }
                            item(span = StaggeredGridItemSpan.FullLine) { Header(s.others) }
                        }
                        items(state.others, key = { it.note.id }) { m ->
                            NoteCard(m, m.note.id == selectedNoteId || m.note.id in selection, Modifier.animateItem(),
                                onClick = { if (selection.isNotEmpty()) toggle(m.note.id) else open(m) }, onLongClick = { toggle(m.note.id) })
                        }
                    }
                }
            }
            if (filter.view != HomeView.TRASH && filter.view != HomeView.ARCHIVE && selection.isEmpty()) {
                Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = { vm.createNote({ NoteDraft(id = it) }) { id -> session.navigate(Screen.Editor(id, isNew = true, startScan = true)) } },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(Icons.Rounded.DocumentScanner, s.scanDocument)
                    }
                    SmallFloatingActionButton(onClick = { showTemplates = true }, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(Icons.Rounded.ViewQuilt, s.fromTemplate)
                    }
                    FloatingActionButton(onClick = { vm.createNote({ NoteDraft(id = it) }) { id -> onOpenNote(id, true) } },
                        containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Icon(Icons.Rounded.Add, s.newNote)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- dialogs & sheets
    unlockFor?.let { m ->
        if (m.note.lockPinHash.isNotBlank()) {
            VerifyPinDialog(
                title = s.unlockNoteTitle, hint = m.note.lockHint,
                verify = { session.verifyNotePin(it, m.note.lockPinHash) },
                onSuccess = { session.markOpened(m.note.id); onOpenNote(m.note.id, false) },
                onDeviceCredential = DeviceCredential.intent(context, s.unlockNoteTitle)?.let { i -> { deviceLauncher.launch(i) } },
                onDismiss = { if (unlockFor?.note?.id == m.note.id) unlockFor = null }
            )
        }
    }
    confirmDeleteMany?.let { ids ->
        ConfirmDialog(s.deleteForever, s.deleteForeverConfirm, s.deleteForever, true, { vm.deleteForeverMany(ids); selection = emptySet() }) { confirmDeleteMany = null }
    }
    if (confirmEmptyTrash) ConfirmDialog(s.emptyTrash, s.emptyTrashConfirm, s.deleteForever, true, { vm.emptyTrash() }) { confirmEmptyTrash = false }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(true)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.filters, style = MaterialTheme.typography.titleLarge)
                Text(s.typeFilter, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(NoteType.CHECKLIST to s.typeChecklist, NoteType.IMAGES to s.typeImages, NoteType.AUDIO to s.typeAudio,
                        NoteType.FILES to s.typeFiles, NoteType.LINKS to s.typeLinks, NoteType.REMINDER to s.typeReminder, NoteType.LOCKED to s.typeLocked
                    ).forEach { (t, l) ->
                        FilterChip(t in filter.types, onClick = { vm.setFilter { f -> f.copy(types = if (t in f.types) f.types - t else f.types + t) } }, label = { Text(l) })
                    }
                }
                Text(s.dateFilter, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DateRange.ANY to s.dateAny, DateRange.TODAY to s.dateToday, DateRange.WEEK to s.dateWeek, DateRange.MONTH to s.dateMonth, DateRange.YEAR to s.dateYear)
                        .forEach { (d, l) -> FilterChip(filter.date == d, onClick = { vm.setFilter { it.copy(date = d) } }, label = { Text(l) }) }
                }
                Text(s.colorFilter, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                ColorPicker(filter.color ?: -1) { c -> vm.setFilter { it.copy(color = if (it.color == c) null else c) } }
                TextButton(onClick = { vm.setFilter { it.copy(types = emptySet(), date = DateRange.ANY, color = null) } }) { Text(s.clearFilters) }
            }
        }
    }

    if (showTemplates) {
        val templates by vm.templates.collectAsState()
        ModalBottomSheet(onDismissRequest = { showTemplates = false }) {
            LazyColumn(Modifier.padding(bottom = 32.dp)) {
                item { Text(s.builtIn, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(20.dp, 8.dp)) }
                items(builtInTemplates(s)) { t ->
                    SheetRow(Icons.Rounded.ViewQuilt, t.name) {
                        showTemplates = false
                        vm.createNote({ id -> NoteDraft(id = id, title = t.title, body = t.body, checklist = t.checklist.map { ChecklistDraft(text = it) }, tags = t.tags) }) { onOpenNote(it, true) }
                    }
                }
                item { Text(s.yours, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(20.dp, 8.dp)) }
                if (templates.isEmpty()) item { Text(s.noTemplates, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(templates, key = { it.id }) { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            SheetRow(Icons.Rounded.ViewQuilt, t.name) {
                                showTemplates = false
                                vm.createNote({ id -> NoteDraft(id = id, title = t.title, body = t.body,
                                    checklist = checklistFromJson(t.checklistJson).map { it.copy(id = com.example.data.repository.Ids.newId(), checked = false) }) }) { onOpenNote(it, true) }
                            }
                        }
                        IconButton(onClick = { vm.deleteTemplate(t.id) }) { Icon(Icons.Rounded.DeleteOutline, s.delete) }
                    }
                }
            }
        }
    }

    actionsFor?.let { m ->
        var colorMode by remember(m.note.id) { mutableStateOf(false) }
        var folderMode by remember(m.note.id) { mutableStateOf(false) }
        var confirmDelete by remember(m.note.id) { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { actionsFor = null }) {
            Column(Modifier.padding(bottom = 28.dp)) {
                Text(m.note.title.ifBlank { s.untitled }, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                val close = { actionsFor = null }
                if (m.note.trashedAt != null) {
                    SheetRow(Icons.Rounded.Restore, s.restore) { vm.restore(m.note.id); close() }
                    SheetRow(Icons.Rounded.DeleteForever, s.deleteForever) { confirmDelete = true }
                } else {
                    SheetRow(Icons.Rounded.PushPin, if (m.note.isPinned) s.unpin else s.pin) { vm.togglePin(m); close() }
                    SheetRow(if (m.note.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (m.note.isFavorite) s.unfavorite else s.favorite) { vm.toggleFavorite(m); close() }
                    SheetRow(Icons.Rounded.Palette, s.color) { colorMode = !colorMode }
                    AnimatedVisibility(colorMode) { Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { ColorPicker(m.note.colorIndex) { vm.setColor(m.note.id, it); close() } } }
                    SheetRow(Icons.Rounded.Folder, s.moveToFolder) { folderMode = !folderMode }
                    AnimatedVisibility(folderMode) {
                        FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(m.note.folderId == null, onClick = { vm.moveToFolder(m.note.id, null); close() }, label = { Text(s.noFolder) })
                            folders.forEach { f -> FilterChip(m.note.folderId == f.id, onClick = { vm.moveToFolder(m.note.id, f.id); close() }, label = { Text(f.name) }) }
                        }
                    }
                    if (!m.note.isLocked) SheetRow(Icons.Rounded.Share, s.share) {
                        vm.plainTextForShare(m.details)?.let { FileOpener.shareText(context, m.note.title, it) }; close()
                    }
                    SheetRow(if (m.note.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, if (m.note.isArchived) s.unarchive else s.archiveAction) { vm.toggleArchive(m); close() }
                    SheetRow(Icons.Rounded.DeleteOutline, s.moveToTrash) { val id = m.note.id; vm.trash(id); session.messageWithUndo(s.movedToTrash) { vm.restore(id) }; close() }
                }
            }
        }
        if (confirmDelete) ConfirmDialog(s.deleteForever, s.deleteForeverConfirm, s.deleteForever, true, { vm.deleteForever(m.note.id); actionsFor = null }) { confirmDelete = false }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** Notea Memory: one quiet suggestion, dismissible for today. */
@Composable
private fun MemoryCard(m: com.example.core.memory.NoteMemory.Resurface, onOpen: () -> Unit, onHide: () -> Unit) {
    val s = LocalStrings.current
    val reason = when (m.reason) {
        com.example.core.memory.NoteMemory.Reason.ON_THIS_DAY -> s.memOnThisDay(m.yearsAgo)
        com.example.core.memory.NoteMemory.Reason.DECISION -> s.memDecision
        com.example.core.memory.NoteMemory.Reason.MAYBE_OUTDATED -> s.memOutdated
        com.example.core.memory.NoteMemory.Reason.FORGOTTEN -> s.memForgotten
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lightbulb, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(6.dp))
                Text(s.fromMemory, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
            Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(m.title.ifBlank { s.untitled }, style = MaterialTheme.typography.titleMedium)
            if (m.preview.isNotBlank()) com.example.ui.components.ExpandableText(m.preview, collapsedLines = 2, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = onOpen) { Text(s.open) }
                TextButton(onClick = onHide) { Text(s.hideToday) }
            }
        }
    }
}

/** Top bar shown while notes are selected (long press). Every action applies to all selected notes. */
@Composable
private fun SelectionBar(
    count: Int, inTrash: Boolean, allPinned: Boolean, allFavorite: Boolean, allArchived: Boolean,
    onClose: () -> Unit, onSelectAll: () -> Unit, onPin: () -> Unit, onFavorite: () -> Unit, onArchive: () -> Unit,
    onTrash: () -> Unit, onRestore: () -> Unit, onDeleteForever: () -> Unit, onMore: (() -> Unit)?
) {
    val s = LocalStrings.current
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, s.cancel) }
            Text(s.selectedCount(count), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
            IconButton(onClick = onSelectAll) { Icon(Icons.Rounded.SelectAll, s.selectAll) }
          }
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (inTrash) {
                IconButton(onClick = onRestore) { Icon(Icons.Rounded.Restore, s.restore) }
                IconButton(onClick = onDeleteForever) { Icon(Icons.Rounded.DeleteForever, s.deleteForever, tint = MaterialTheme.colorScheme.error) }
            } else {
                IconButton(onClick = onPin) { Icon(Icons.Rounded.PushPin, if (allPinned) s.unpin else s.pin, tint = if (allPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onFavorite) { Icon(if (allFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, if (allFavorite) s.unfavorite else s.favorite) }
                IconButton(onClick = onArchive) { Icon(if (allArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, if (allArchived) s.unarchive else s.archiveAction) }
                IconButton(onClick = onTrash) { Icon(Icons.Rounded.DeleteOutline, s.moveToTrash) }
                if (onMore != null) IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, s.more) }
            }
          }
        }
    }
}
