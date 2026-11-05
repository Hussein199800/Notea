package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.data.local.RevisionEntity
import com.example.data.local.RevisionReason
import com.example.data.repository.DecryptResult
import com.example.ui.components.TextInputDialog
import com.example.ui.editor.MarkdownEngine
import com.example.ui.editor.TextDiff
import com.example.ui.i18n.LocalStrings
import com.example.ui.viewmodel.EditorViewModel

/** Plain comparable text of a version: title, body and checklist lines. */
private fun comparable(title: String, body: String, checklist: List<com.example.data.repository.ChecklistDraft>) =
    buildString {
        if (title.isNotBlank()) append(title).append("\n\n")
        append(MarkdownEngine.plain(body))
        checklist.forEach { append("\n").append(if (it.checked) "☑ " else "☐ ").append(it.text) }
    }.trim()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VersionHistorySheet(vm: EditorViewModel, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val revisions by vm.revisions.collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<RevisionEntity?>(null) }
    var nameDialog by remember { mutableStateOf(false) }
    var renameFor by remember { mutableStateOf<RevisionEntity?>(null) }
    val st by vm.state.collectAsState()
    val current = remember(st.title, st.blocks, st.checklist) { comparable(st.title, st.bodyText, st.checklist) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        val r = selected
        if (r == null) {
            LazyColumn(Modifier.padding(bottom = 24.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.history, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { nameDialog = true }) {
                            Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.saveNamedVersion)
                        }
                    }
                    if (revisions.isEmpty()) Text(s.noHistory, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Timeline grouped by day; named versions are highlighted and never auto-deleted.
                val byDay = revisions.groupBy { s.date(it.createdAt, withTime = false) }
                byDay.forEach { (day, list) ->
                    item(key = "d$day") {
                        Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp))
                    }
                    items(list, key = { it.id }) { rev ->
                        val stats = remember(rev.id, current) {
                            (vm.decryptRevision(rev) as? DecryptResult.Plain)?.let { d ->
                                TextDiff.stats(TextDiff.diff(comparable(rev.title, d.body, d.checklist), current))
                            }
                        }
                        Row(Modifier.fillMaxWidth().clickable { selected = rev }.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (rev.label.isNotBlank()) Icons.Rounded.Bookmark else Icons.Rounded.History, null,
                                tint = if (rev.label.isNotBlank()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(rev.label.ifBlank { reasonLabel(rev.reason) }, style = MaterialTheme.typography.bodyLarge)
                                val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, s.locale).format(java.util.Date(rev.createdAt))
                                Text(listOfNotNull(time, stats?.let { s.diffStats(it.added, it.removed) }).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            val dec = remember(r.id) { vm.decryptRevision(r) }
            var compare by remember(r.id) { mutableStateOf(true) }
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).heightIn(max = 640.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.date(r.createdAt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label.ifBlank { reasonLabel(r.reason) }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { renameFor = r }) { Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(s.rename) }
                }
                when (dec) {
                    is DecryptResult.Plain -> {
                        val old = comparable(r.title, dec.body, dec.checklist)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(compare, { compare = true }, { Text(s.compareWithCurrent) })
                            FilterChip(!compare, { compare = false }, { Text(s.showVersionOnly) })
                        }
                        if (compare) {
                            val parts = remember(old, current) { TextDiff.diff(old, current) }
                            val stats = TextDiff.stats(parts)
                            Text(s.diffExplain(stats.added, stats.removed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DiffText(parts)
                        } else {
                            Text(old, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row {
                            TextButton(onClick = { selected = null }) { Text(s.back) }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.restoreRevision(r) { onDismiss() } }) {
                                Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(6.dp)); Text(s.restoreVersion)
                            }
                        }
                    }
                    else -> { Text(s.vaultNeedsUnlock); TextButton(onClick = { selected = null }) { Text(s.back) } }
                }
            }
        }
    }
    if (nameDialog) TextInputDialog(s.saveNamedVersion, s.versionName, "", s.save, { vm.saveNamedVersion(it) }) { nameDialog = false }
    renameFor?.let { rev ->
        TextInputDialog(s.rename, s.versionName, rev.label, s.save, { vm.renameVersion(rev.id, it); if (selected?.id == rev.id) selected = rev.copy(label = it) }) { renameFor = null }
    }
}

/** Removed words: red + struck through; added words: emerald highlight; unchanged: normal. */
@Composable
private fun DiffText(parts: List<TextDiff.Part>) {
    val added = MaterialTheme.colorScheme.primaryContainer
    val removedColor = MaterialTheme.colorScheme.error
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), modifier = Modifier.fillMaxWidth()) {
        Text(
            buildAnnotatedString {
                parts.forEach { p ->
                    when (p.op) {
                        TextDiff.Op.SAME -> append(p.text)
                        TextDiff.Op.ADDED -> withStyle(SpanStyle(background = added)) { append(p.text) }
                        TextDiff.Op.REMOVED -> withStyle(SpanStyle(color = removedColor, textDecoration = TextDecoration.LineThrough, background = removedColor.copy(alpha = 0.08f))) { append(p.text) }
                    }
                }
            },
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun reasonLabel(reason: String): String {
    val s = LocalStrings.current
    return when (reason) {
        RevisionReason.CONFLICT -> s.reasonConflict
        RevisionReason.BEFORE_RESTORE -> s.reasonBeforeRestore
        RevisionReason.IMPORT_MERGE -> s.reasonMerge
        RevisionReason.NAMED -> s.reasonNamed
        RevisionReason.ASSISTANT -> s.reasonAssistant
        else -> s.reasonAuto
    }
}

@Suppress("unused")
private val unusedColor = Color.Unspecified
