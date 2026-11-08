package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhonelinkSetup
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.FolderEntity
import com.example.data.local.TagEntity
import com.example.ui.components.ConceptIcon
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.ExpandableText
import com.example.ui.components.InfoButton
import com.example.ui.components.PreferenceGroup
import com.example.ui.components.SectionLabel
import com.example.ui.components.TextInputDialog
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.Concept
import com.example.ui.viewmodel.HomeView
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.SessionViewModel

private data class TabSpec(val tab: MainTab, val icon: ImageVector, val label: String, val concept: Concept)

/**
 * The four main tabs. Bottom bar on phones (portrait), navigation rail when there is room
 * (phone landscape, tablets). [content] draws the selected tab (lets the caller add a side editor).
 */
@Composable
fun TabsChrome(session: SessionViewModel, home: HomeViewModel, rail: Boolean, content: @Composable () -> Unit) {
    val s = LocalStrings.current
    val tab by session.tab.collectAsState()
    val tasks by home.tasks.collectAsState()
    val specs = listOf(
        TabSpec(MainTab.NOTES, Icons.AutoMirrored.Rounded.Notes, s.tabNotes, Concept.NOTES),
        TabSpec(MainTab.TASKS, Icons.Rounded.TaskAlt, if (rail) s.tabTasks else s.tabTasksShort, Concept.TASKS),
        TabSpec(MainTab.FOLDERS, Icons.Rounded.Folder, if (rail) s.tabFolders else s.tabFoldersShort, Concept.FOLDERS),
        TabSpec(MainTab.MORE, Icons.Rounded.MoreHoriz, s.tabMore, Concept.DATA)
    )
    BackHandler(enabled = tab != MainTab.NOTES) { session.selectTab(MainTab.NOTES) }

    @Composable fun TabIcon(sp: TabSpec, selected: Boolean) {
        val badge = if (sp.tab == MainTab.TASKS) tasks.open.size else 0
        BadgedBox(badge = { if (badge > 0) Badge { Text(s.num(badge)) } }) {
            Icon(sp.icon, null, tint = if (selected) sp.concept.g2 else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (rail) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(Modifier.fillMaxHeight().width(96.dp)) {
                Spacer(Modifier.weight(1f))
                specs.forEach { sp ->
                    NavigationRailItem(
                        selected = tab == sp.tab, onClick = { session.selectTab(sp.tab) },
                        icon = { TabIcon(sp, tab == sp.tab) },
                        label = { Text(sp.label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationRailItemDefaults.colors(indicatorColor = sp.concept.g1.copy(alpha = 0.22f)),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.navigationBars)) { content() }
            NavigationBar {
                specs.forEach { sp ->
                    NavigationBarItem(
                        selected = tab == sp.tab, onClick = { session.selectTab(sp.tab) },
                        icon = { TabIcon(sp, tab == sp.tab) },
                        label = { Text(sp.label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = sp.concept.g1.copy(alpha = 0.22f))
                    )
                }
            }
        }
    }
}

/** Draws the selected tab's page. */
@Composable
fun TabContent(session: SessionViewModel, home: HomeViewModel, selectedNoteId: String?, onOpenNote: (String, Boolean) -> Unit) {
    val tab by session.tab.collectAsState()
    when (tab) {
        MainTab.NOTES -> HomeScreen(session, home, selectedNoteId, onOpenNote)
        MainTab.TASKS -> TasksTab(session, home, onOpenNote)
        MainTab.FOLDERS -> FoldersTab(session, home)
        MainTab.MORE -> MoreTab(session)
    }
}

@Composable
private fun TabTitle(text: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Row with a concept icon, wrapping title/subtitle, optional count (opens the same list) and trailing. */
@Composable
private fun ConceptRow(icon: ImageVector, concept: Concept, title: String, subtitle: String? = null, count: Int? = null,
                       onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    val s = LocalStrings.current
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        ConceptIcon(icon, concept)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (count != null) Text(s.num(count), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp))
        trailing?.invoke()
    }
}

/** Tab page: title on top; one list on phones, two balanced columns when wide (landscape / tablet). */
@Composable
private fun TabPage(header: @Composable () -> Unit, first: LazyListScope.() -> Unit, second: LazyListScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        if (maxWidth >= 700.dp) {
            Column(Modifier.fillMaxSize()) {
                header()
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp), content = first)
                    LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp), content = second)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { header() }
                first(); second()
            }
        }
    }
}

// ------------------------------------------------------------------ Tasks & reminders

@Composable
fun TasksTab(session: SessionViewModel, home: HomeViewModel, onOpenNote: (String, Boolean) -> Unit) {
    val s = LocalStrings.current
    val t by home.tasks.collectAsState()
    val now = System.currentTimeMillis()
    TabPage({ TabTitle(s.tabTasks) { InfoButton(s.tabTasks, s.tasksInfo) } }, first = {
        item { SectionLabel(s.upcomingReminders) }
        if (t.reminders.isEmpty()) item {
            Text(s.noReminders, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        items(t.reminders, key = { "r" + it.first.note.id }) { (m, at) ->
            val late = at < now
            ConceptRow(Icons.Rounded.Notifications, Concept.REMINDERS, m.note.title.ifBlank { s.untitled },
                subtitle = (if (late) s.overdue + " · " else "") + s.date(at), onClick = {
                    if (!m.note.isLocked) onOpenNote(m.note.id, false) else { home.showView(HomeView.REMINDERS); session.selectTab(MainTab.NOTES) }
                })
        }
    }, second = {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(s.openTasks + " · " + s.tasksCount(t.open.size), Modifier.weight(1f))
            }
        }
        if (t.open.isEmpty()) item {
            EmptyState(Icons.Rounded.TaskAlt, s.noTasksTitle, s.noTasksBody, Modifier.fillMaxWidth().padding(vertical = 24.dp))
        }
        var lastNote: String? = null
        t.open.forEach { row ->
            if (row.noteId != lastNote) {
                lastNote = row.noteId
                item(key = "h" + row.noteId) {
                    Text(row.noteTitle.ifBlank { s.untitled }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenNote(row.noteId, false) }.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
            item(key = row.noteId + row.itemId) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(false, onCheckedChange = { home.completeTask(row.noteId, row.itemId) })
                    ExpandableText(row.text, collapsedLines = 3, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).clickable { onOpenNote(row.noteId, false) }.padding(vertical = 8.dp))
                }
            }
        }
        if (t.doneCount > 0) item {
            Text(s.doneTasks(t.doneCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    })
}

// ------------------------------------------------------------------ Folders & tags

@Composable
fun FoldersTab(session: SessionViewModel, home: HomeViewModel) {
    val s = LocalStrings.current
    val folders by home.folders.collectAsState()
    val tags by home.tags.collectAsState()
    val o by home.overview.collectAsState()
    var newFolder by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var deleteFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var renameTag by remember { mutableStateOf<TagEntity?>(null) }
    fun go(block: () -> Unit) { block(); session.selectTab(MainTab.NOTES) }

    TabPage({ TabTitle(s.tabFolders) { InfoButton(s.tabFolders, s.countsInfo) } }, first = {
        item {
            PreferenceGroup {
                ConceptRow(Icons.AutoMirrored.Rounded.Notes, Concept.NOTES, s.allNotes, count = o.total, onClick = { go { home.showView(HomeView.ALL) } })
                ConceptRow(Icons.Rounded.Star, Concept.SECURITY, s.favorites, count = o.favorites, onClick = { go { home.showView(HomeView.FAVORITES) } })
                ConceptRow(Icons.Rounded.Archive, Concept.ARCHIVE, s.archive, count = o.archived, onClick = { go { home.showView(HomeView.ARCHIVE) } })
                ConceptRow(Icons.Rounded.DeleteOutline, Concept.TRASH, s.trash, count = o.trashed, onClick = { go { home.showView(HomeView.TRASH) } })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                SectionLabel(s.folders, Modifier.weight(1f))
                TextButton(onClick = { newFolder = true }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(4.dp)); Text(s.newFolder) }
            }
        }
        if (folders.isEmpty()) item {
            Text(s.noFolders, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        } else item {
            PreferenceGroup {
                folders.forEachIndexed { i, f ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 70.dp))
                    var menu by remember(f.id) { mutableStateOf(false) }
                    ConceptRow(Icons.Rounded.Folder, Concept.FOLDERS, f.name, count = o.folderCounts[f.id] ?: 0, onClick = { go { home.showFolder(f.id) } }) {
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.heightIn(min = 44.dp)) { Icon(Icons.Rounded.MoreVert, s.more) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text(s.rename) }, onClick = { menu = false; renameFolder = f })
                                DropdownMenuItem(text = { Text(s.deleteFolder, color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleteFolder = f })
                            }
                        }
                    }
                }
            }
        }
    }, second = {
        item { SectionLabel(s.tags) }
        if (tags.isEmpty()) item {
            ExpandableText(s.noTags, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
        } else item {
            PreferenceGroup {
                tags.forEachIndexed { i, tg ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 70.dp))
                    ConceptRow(Icons.AutoMirrored.Rounded.Label, Concept.FOLDERS, "#" + tg.name, count = o.tagCounts[tg.id] ?: 0,
                        onClick = { go { home.showTag(tg.id) } }) {
                        IconButton(onClick = { renameTag = tg }) { Icon(Icons.Rounded.MoreVert, s.renameTag) }
                    }
                }
            }
        }
    })
    if (newFolder) TextInputDialog(s.newFolder, s.folderName, "", s.save, { home.createFolder(it) }) { newFolder = false }
    renameFolder?.let { f -> TextInputDialog(s.rename, s.folderName, f.name, s.save, { home.renameFolder(f.id, it) }) { renameFolder = null } }
    renameTag?.let { tg -> TextInputDialog(s.renameTag, s.tagName, tg.name, s.save, { home.renameTag(tg.id, it) }) { renameTag = null } }
    deleteFolder?.let { f -> ConfirmDialog(s.deleteFolder, s.deleteFolderConfirm, s.delete, true, { home.deleteFolder(f.id) }) { deleteFolder = null } }
}

// ------------------------------------------------------------------ More

@Composable
fun MoreTab(session: SessionViewModel) {
    val s = LocalStrings.current
    TabPage({ TabTitle(s.tabMore) }, first = {
        item { SectionLabel(s.moreData) }
        item {
            PreferenceGroup {
                ConceptRow(Icons.Rounded.PhoneAndroid, Concept.DATA, s.migrationCenter, s.migrationCenterDesc, onClick = { session.navigate(Screen.Welcome(firstRun = false)) })
                ConceptRow(Icons.Rounded.Download, Concept.DATA, s.importCenter, s.importCenterDesc, onClick = { session.navigate(Screen.ImportCenter) })
                ConceptRow(Icons.Rounded.Backup, Concept.DATA, s.backupTitle, s.backupDesc, onClick = { session.navigate(Screen.Backup) })
                ConceptRow(Icons.Rounded.PhonelinkSetup, Concept.DATA, s.transferTitle, s.transferDesc, onClick = { session.navigate(Screen.Transfer) })
            }
        }
    }, second = {
        item { SectionLabel(s.moreApp) }
        item {
            PreferenceGroup {
                ConceptRow(Icons.Rounded.AutoAwesome, Concept.ASSISTANT, s.assistant, s.assistantDesc, onClick = { session.navigate(Screen.AssistantSettings) })
                ConceptRow(Icons.Rounded.Settings, Concept.ARCHIVE, s.settings, s.settingsDesc, onClick = { session.navigate(Screen.Settings) })
                ConceptRow(Icons.Rounded.SystemUpdate, Concept.NOTES, s.updates, s.updatesDesc, onClick = { session.navigate(Screen.Update) })
                ConceptRow(Icons.Rounded.TouchApp, Concept.NOTES, s.tour, s.tourDesc, onClick = { session.startTour() })
                ConceptRow(Icons.Rounded.NewReleases, Concept.NOTES, s.releases, s.releasesDesc, onClick = { session.navigate(Screen.Releases) })
                ConceptRow(Icons.Rounded.BugReport, Concept.ARCHIVE, s.diagnostics, s.diagnosticsDesc, onClick = { session.navigate(Screen.Diagnostics) })
                ConceptRow(Icons.Rounded.Info, Concept.NOTES, s.about, s.versionLabel(com.example.BuildConfig.VERSION_NAME), onClick = { session.navigate(Screen.About) })
            }
        }
    })
}
