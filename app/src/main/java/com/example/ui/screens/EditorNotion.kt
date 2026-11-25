package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.SubdirectoryArrowLeft
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.components.ConceptIcon
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.Concept
import com.example.ui.viewmodel.EditorViewModel.NoteGraph
import com.example.ui.viewmodel.EditorViewModel.NoteRef

/** Parent breadcrumb above the title. */
@Composable
fun ParentCrumb(parent: NoteRef, onOpen: (String) -> Unit) {
    val s = LocalStrings.current
    Row(Modifier.padding(top = 8.dp).heightIn(min = 44.dp).clickable { onOpen(parent.id) }, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.SubdirectoryArrowLeft, null, Modifier.size(18.dp), tint = Concept.FOLDERS.g2)
        Spacer(Modifier.width(6.dp))
        Text(s.insideNote(parent.icon.ifBlank { "" } + " " + parent.title.ifBlank { s.untitled }), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun NoteRef.label(untitled: String) = (if (icon.isNotBlank()) "$icon " else "") + title.ifBlank { untitled }

/** Sub-notes, links from this note and notes that link here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteGraphSection(g: NoteGraph, readOnly: Boolean, onOpen: (String) -> Unit, onNewChild: () -> Unit, onCreateLinked: (String) -> Unit) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (g.children.isNotEmpty() || !readOnly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.subNotes + if (g.children.isNotEmpty()) " · " + s.notesCount(g.children.size) else "", style = MaterialTheme.typography.labelLarge,
                    color = Concept.FOLDERS.g2, modifier = Modifier.weight(1f))
                if (!readOnly) TextButton(onClick = onNewChild, modifier = Modifier.heightIn(min = 44.dp)) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(s.newSubNote)
                }
            }
            g.children.forEach { c ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), onClick = { onOpen(c.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.icon.ifBlank { "📄" }); Spacer(Modifier.width(10.dp))
                        Text(c.title.ifBlank { s.untitled }, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        if (g.links.isNotEmpty() || g.missingLinks.isNotEmpty()) {
            Text(s.linksFromNote, style = MaterialTheme.typography.labelLarge, color = Concept.REMINDERS.t1, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                g.links.forEach { l -> AssistChip(onClick = { onOpen(l.id) }, label = { Text(l.label(s.untitled), Modifier.padding(vertical = 6.dp)) },
                    leadingIcon = { Icon(Icons.Rounded.Link, null, Modifier.size(16.dp)) }) }
                if (!readOnly) g.missingLinks.forEach { t -> AssistChip(onClick = { onCreateLinked(t) }, label = { Text(s.createNoteNamed(t), Modifier.padding(vertical = 6.dp)) },
                    leadingIcon = { Icon(Icons.Rounded.NoteAdd, null, Modifier.size(16.dp)) }) }
            }
        }
        if (g.backlinks.isNotEmpty()) {
            Text(s.backlinks, style = MaterialTheme.typography.labelLarge, color = Concept.REMINDERS.t1, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                g.backlinks.forEach { l -> AssistChip(onClick = { onOpen(l.id) }, label = { Text(l.label(s.untitled), Modifier.padding(vertical = 6.dp)) }) }
            }
        }
    }
}

/** One «/» command. */
data class SlashItem(val key: String, val icon: ImageVector, val label: String, val concept: Concept)

fun slashItems(s: com.example.ui.i18n.Strings) = listOf(
    SlashItem("h", Icons.Rounded.Title, s.heading, Concept.NOTES),
    SlashItem("todo", Icons.Rounded.CheckBox, s.checklist, Concept.TASKS),
    SlashItem("ul", Icons.AutoMirrored.Rounded.FormatListBulleted, s.bullets, Concept.NOTES),
    SlashItem("ol", Icons.Rounded.FormatListNumbered, s.numbered, Concept.NOTES),
    SlashItem("quote", Icons.Rounded.FormatQuote, s.quote, Concept.NOTES),
    SlashItem("toggle", Icons.Rounded.UnfoldMore, s.toggleBlock, Concept.FOLDERS),
    SlashItem("callout", Icons.Rounded.Lightbulb, s.calloutBlock, Concept.SECURITY),
    SlashItem("sub", Icons.Rounded.NoteAdd, s.newSubNote, Concept.FOLDERS),
    SlashItem("link", Icons.Rounded.Link, s.linkToNote, Concept.REMINDERS),
    SlashItem("image", Icons.Rounded.Image, s.image, Concept.DATA),
    SlashItem("scan", Icons.Rounded.DocumentScanner, s.scanDocument, Concept.DATA),
    SlashItem("file", Icons.Rounded.AttachFile, s.file, Concept.DATA)
)

/** Panel above the toolbar: «/» commands, filtered by what is typed after the slash. */
@Composable
fun SlashPanel(query: String, onPick: (SlashItem) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val items = slashItems(s).filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.insertBlock, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(s.cancel) }
            }
            if (items.isEmpty()) Text(s.noMatches, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.heightIn(max = 260.dp)) {
                items(items, key = { it.key }) { it ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(it) }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ConceptIcon(it.icon, it.concept, size = 32.dp); Spacer(Modifier.width(12.dp))
                        Text(it.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** Note picker for «[[» links and for choosing a parent note. */
@Composable
fun NotePickerPanel(title: String, notes: List<NoteRef>, query: String, onPick: (NoteRef) -> Unit, onCreate: ((String) -> Unit)?, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val k = com.example.core.text.ArabicNormalizer.normalize(query.trim()).lowercase()
    val shown = notes.filter { k.isEmpty() || com.example.core.text.ArabicNormalizer.normalize(it.title).lowercase().contains(k) }.take(30)
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(s.cancel) }
            }
            LazyColumn(Modifier.heightIn(max = 260.dp)) {
                if (onCreate != null && query.isNotBlank() && shown.none { com.example.core.text.NoteLinks.key(it.title) == com.example.core.text.NoteLinks.key(query) }) item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onCreate(query.trim()) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NoteAdd, null, tint = Concept.NOTES.g2); Spacer(Modifier.width(12.dp)); Text(s.createNoteNamed(query.trim()))
                    }
                }
                if (shown.isEmpty() && (onCreate == null || query.isBlank())) item { Text(s.noMatches, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(shown, key = { it.id }) { n ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(n) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(n.icon.ifBlank { "📄" }); Spacer(Modifier.width(12.dp)); Text(n.title.ifBlank { s.untitled }, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

private val EMOJIS = listOf(
    "📒", "📓", "📔", "📕", "📗", "📘", "📙", "📄", "📝", "✏️", "📌", "📎", "📁", "🗂️", "🗃️", "📚",
    "⚖️", "🏛️", "💼", "📊", "📈", "💰", "🧾", "🏠", "🛒", "🍽️", "✈️", "🚗", "🏥", "💊", "🎓", "🧠",
    "💡", "⭐", "❤️", "✅", "⏰", "📅", "🔒", "🔑", "🎯", "🚀", "🌱", "🕌", "🎁", "📷", "🎵", "⚽"
)

/** Emoji icon for a note (shown next to its title everywhere). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPickerDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.noteIcon) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EMOJIS.forEach { e ->
                    Surface(shape = MaterialTheme.shapes.small, onClick = { onPick(e); onDismiss() },
                        color = if (e == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp)) {
                        Text(e, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(10.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
        dismissButton = { if (current.isNotBlank()) TextButton(onClick = { onPick(""); onDismiss() }) { Text(s.removeIcon) } }
    )
}

@Composable
fun GraphDivider() = HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
