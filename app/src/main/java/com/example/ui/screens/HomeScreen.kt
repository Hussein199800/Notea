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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.material3.DrawerValue
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
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
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

data class BuiltInTemplate(val name: String, val title: String, val body: String, val checklist: List<String>)

fun builtInTemplates(s: Strings): List<BuiltInTemplate> = if (s.isArabic) listOf(
    BuiltInTemplate(s.tplMeeting, "اجتماع: ", "**التاريخ:** \n**الحضور:** \n\n## جدول الأعمال\n- \n\n## القرارات\n- ", listOf("متابعة المهام")),
    BuiltInTemplate(s.tplDaily, "يوميات", "## ما الذي أنجزته اليوم؟\n- \n\n## ما الذي أشكر عليه؟\n- \n\n## أولويات الغد\n1. ", emptyList()),
    BuiltInTemplate(s.tplShopping, "قائمة تسوق", "", listOf("خبز", "حليب", "خضار")),
    BuiltInTemplate(s.tplProject, "مشروع: ", "## الهدف\n\n## المراحل\n1. \n\n## الموارد\n- ", listOf("تحديد النطاق", "الخطة الزمنية"))
) else listOf(
    BuiltInTemplate(s.tplMeeting, "Meeting: ", "**Date:** \n**Attendees:** \n\n## Agenda\n- \n\n## Decisions\n- ", listOf("Follow-ups")),
    BuiltInTemplate(s.tplDaily, "Journal", "## What did I get done today?\n- \n\n## Grateful for\n- \n\n## Tomorrow's priorities\n1. ", emptyList()),
    BuiltInTemplate(s.tplShopping, "Shopping list", "", listOf("Bread", "Milk", "Vegetables")),
    BuiltInTemplate(s.tplProject, "Project: ", "## Goal\n\n## Milestones\n1. \n\n## Resources\n- ", listOf("Define scope", "Timeline"))
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
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var actionsFor by remember { mutableStateOf<NoteCardModel?>(null) }
    var unlockFor by remember { mutableStateOf<NoteCardModel?>(null) }
    var showTemplates by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf<FolderEntity?>(null) }
    var newFolderDialog by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf<FolderEntity?>(null) }

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

    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
                item {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        NoteaMark(Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(s.appName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                fun close() = scope.launch { drawer.close() }
                item {
                    DrawerItem(Icons.Rounded.Notes, s.allNotes, filter.view == HomeView.ALL && filter.folderId == null && filter.tagId == null) { vm.showView(HomeView.ALL); close() }
                    DrawerItem(Icons.Rounded.Star, s.favorites, filter.view == HomeView.FAVORITES) { vm.showView(HomeView.FAVORITES); close() }
                    DrawerItem(Icons.Rounded.Notifications, s.reminders, filter.view == HomeView.REMINDERS) { vm.showView(HomeView.REMINDERS); close() }
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.folders, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        IconButton(onClick = { newFolderDialog = true }) { Icon(Icons.Rounded.Add, s.newFolder) }
                    }
                }
                items(folders, key = { it.id }) { f ->
                    Box {
                        DrawerItem(Icons.Rounded.Folder, f.name, filter.folderId == f.id, onLongClick = { folderMenu = f }) { vm.showFolder(f.id); close() }
                        DropdownMenu(expanded = folderMenu?.id == f.id, onDismissRequest = { folderMenu = null }) {
                            DropdownMenuItem(text = { Text(s.rename) }, onClick = { folderDialog = f; folderMenu = null })
                            DropdownMenuItem(text = { Text(s.delete) }, onClick = { vm.deleteFolder(f.id); folderMenu = null })
                        }
                    }
                }
                if (tags.isNotEmpty()) {
                    item { Text(s.tags, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp)) }
                    items(tags, key = { it.id }) { t ->
                        DrawerItem(Icons.AutoMirrored.Rounded.Label, t.name, filter.tagId == t.id) { vm.showTag(t.id); close() }
                    }
                }
                item {
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    DrawerItem(Icons.Rounded.Archive, s.archive, filter.view == HomeView.ARCHIVE) { vm.showView(HomeView.ARCHIVE); close() }
                    DrawerItem(Icons.Rounded.DeleteOutline, s.trash, filter.view == HomeView.TRASH) { vm.showView(HomeView.TRASH); close() }
                    DrawerItem(Icons.Rounded.Settings, s.settings, false) { session.navigate(Screen.Settings); close() }
                }
            }
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // Search bar
                Surface(
                    shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(56.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Rounded.Menu, s.menu) }
                        Box(Modifier.weight(1f)) {
                            if (filter.query.isEmpty()) Text(s.searchHint, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicTextField(
                                value = filter.query, onValueChange = { q -> vm.setFilter { it.copy(query = q) } }, singleLine = true,
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
                // Title row
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(viewTitle, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                if (filter.hasRefinements) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(true, onClick = { vm.setFilter { it.copy(types = emptySet(), date = DateRange.ANY, color = null) } }, label = { Text(s.clearFilters) },
                            leadingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) }) }
                    }
                }
                if (filter.view == HomeView.TRASH) Text(s.trashBanner, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

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
                        if (state.pinned.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) { Header(s.pinned) }
                            items(state.pinned, key = { it.note.id }) { m ->
                                NoteCard(m, m.note.id == selectedNoteId, Modifier.animateItem(), onClick = { open(m) }, onLongClick = { actionsFor = m })
                            }
                            item(span = StaggeredGridItemSpan.FullLine) { Header(s.others) }
                        }
                        items(state.others, key = { it.note.id }) { m ->
                            NoteCard(m, m.note.id == selectedNoteId, Modifier.animateItem(), onClick = { open(m) }, onLongClick = { actionsFor = m })
                        }
                    }
                }
            }
            if (filter.view != HomeView.TRASH && filter.view != HomeView.ARCHIVE) {
                Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    if (confirmEmptyTrash) ConfirmDialog(s.emptyTrash, s.emptyTrashConfirm, s.deleteForever, true, { vm.emptyTrash() }) { confirmEmptyTrash = false }
    if (newFolderDialog) TextInputDialog(s.newFolder, s.folderName, "", s.save, { vm.createFolder(it) }) { newFolderDialog = false }
    folderDialog?.let { f -> TextInputDialog(s.rename, s.folderName, f.name, s.save, { vm.renameFolder(f.id, it) }) { folderDialog = null } }

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
                        vm.createNote({ id -> NoteDraft(id = id, title = t.title, body = t.body, checklist = t.checklist.map { ChecklistDraft(text = it) }) }) { onOpenNote(it, true) }
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
                Text(m.note.title.ifBlank { s.untitled }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                    SheetRow(Icons.Rounded.DeleteOutline, s.moveToTrash) { vm.trash(m.note.id); session.message(s.movedToTrash); close() }
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
private fun DrawerItem(icon: ImageVector, label: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, null) }, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, selected = selected, onClick = onClick,
        badge = if (onLongClick != null) { { IconButton(onClick = onLongClick, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.MoreVert, null, Modifier.size(18.dp)) } } } else null,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

@Composable
fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
