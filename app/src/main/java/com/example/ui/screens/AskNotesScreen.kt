package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.ai.NoteAssistant
import com.example.ui.components.ExpandableText
import com.example.ui.editor.MarkdownEngine
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.Concept
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.SessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One answered library question with the notes it was answered from. */
private data class LibraryTurn(val question: String, val answer: String, val sources: List<Pair<String, String>>)

/** Text of a note sent to the assistant: title, plain body, checklist and attachment text (unlocked notes only). */
internal fun libraryDocText(d: com.example.data.local.NoteWithDetails): String = buildString {
    append(MarkdownEngine.plain(d.note.body))
    d.sortedChecklist.forEach { append("\n- ").append(if (it.checked) "[x] " else "[ ] ").append(it.text) }
    d.attachments.filter { !it.encrypted && it.extractedText.isNotBlank() && it.extractedText != com.example.ocr.OcrWorker.NO_TEXT }
        .forEach { append("\n").append(it.extractedText.take(2000)) }
}.trim()

/** «اسأل ملاحظاتك»: on-device retrieval of the closest notes, then one Gemini request with only those. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskNotesScreen(session: SessionViewModel) {
    val s = LocalStrings.current
    val c = session.container
    val scope = rememberCoroutineScope()
    val turns = remember { mutableStateListOf<LibraryTurn>() }
    var q by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, List<com.example.data.local.NoteWithDetails>>?>(null) }
    var remember by remember { mutableStateOf(false) }

    fun send(question: String, docs: List<com.example.data.local.NoteWithDetails>) {
        loading = true; error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { c.assistant.askLibrary(question, docs.map { it.note.title.ifBlank { s.untitled } to libraryDocText(it) }) }
            }
            result.onSuccess { a ->
                turns += LibraryTurn(question, a.answer, a.sources.map { i -> docs[i - 1].note.id to docs[i - 1].note.title.ifBlank { s.untitled } })
                q = ""
            }.onFailure { e ->
                com.example.core.Diagnostics.log(c.app, "ai-library", "Library question failed", e)
                error = s.aiError(e)
            }
            loading = false
        }
    }

    fun prepare() {
        val question = q.trim()
        if (question.isEmpty() || loading) return
        loading = true; error = null
        scope.launch {
            val docs = withContext(Dispatchers.IO) {
                val ids = c.memoryIndex(session.uid).search(question, NoteAssistant.LIBRARY_NOTES)
                ids.mapNotNull { c.db.noteDao().getNoteWithDetails(it) }.filter { !it.note.isLocked && it.note.trashedAt == null && !it.note.deleted }
            }
            loading = false
            when {
                docs.isEmpty() -> error = s.noMatchingNotes
                c.aiKeys.rememberConsent -> send(question, docs)
                else -> confirm = question to docs
            }
        }
    }

    PageScaffold(s.askLibrary, onBack = { session.back() }) {
        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!c.assistantReady()) {
                    ExpandableText(s.askLibraryIntro, collapsedLines = 4, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { session.navigate(Screen.AssistantSettings) }) { Text(s.setUpAssistant) }
                    return@Column
                }
                ExpandableText(s.askLibraryIntro, collapsedLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                turns.forEach { t ->
                    Surface(shape = MaterialTheme.shapes.medium, color = Concept.ASSISTANT.g1.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
                        Text(t.question, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        ExpandableText(t.answer, collapsedLines = 14, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (t.sources.isNotEmpty()) {
                        Text(s.sources, style = MaterialTheme.typography.labelLarge, color = Concept.REMINDERS.t1)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            t.sources.forEach { (id, title) -> AssistChip(onClick = { session.navigate(Screen.Editor(id)) }, label = { Text(title, Modifier.padding(vertical = 6.dp)) }) }
                        }
                    }
                }
                if (turns.isEmpty()) Text(s.askLibraryHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { ExpandableText(it, collapsedLines = 3, color = MaterialTheme.colorScheme.error) }
                if (loading) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)); Text(s.thinking) }
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(q, { q = it.replace("\n", " ") }, label = { Text(s.yourQuestion) }, maxLines = 4, modifier = Modifier.weight(1f))
                    IconButton(enabled = q.isNotBlank(), onClick = { prepare() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.Send, s.send, tint = Concept.ASSISTANT.g2)
                    }
                }
            }
        }
    }

    confirm?.let { (question, docs) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(s.consentTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.notesToSend, style = MaterialTheme.typography.labelLarge)
                    docs.forEach { Text("• " + it.note.title.ifBlank { s.untitled }, style = MaterialTheme.typography.bodyMedium) }
                    ExpandableText(s.freeTierNote, collapsedLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                        Checkbox(remember, { remember = it }); Text(s.dontAskAgain, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { if (remember) c.aiKeys.rememberConsent = true; confirm = null; send(question, docs) }) { Text(s.send) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(s.cancel) } }
        )
    }
}
