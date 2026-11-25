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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.core.ai.AiError
import com.example.core.ai.NoteAssistant
import com.example.ui.components.ExpandableText
import com.example.ui.i18n.LocalStrings
import com.example.ui.i18n.Strings
import com.example.ui.viewmodel.AssistantUi
import com.example.ui.viewmodel.EditorViewModel

fun Strings.aiError(e: Throwable): String = when (e) {
    is AiError.InvalidKey -> errInvalidKey
    is AiError.RegionUnsupported -> errRegion
    is AiError.Quota -> errQuota
    is AiError.Offline -> errOffline
    is AiError.Blocked -> errBlocked
    else -> errGeneric
}

/** Confirmation before anything leaves the device. */
@Composable
fun ConsentDialog(body: String, onSend: (remember: Boolean) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    var remember by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoAwesome, null) },
        title = { Text(s.consentTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpandableText(body, collapsedLines = 6)
                ExpandableText(s.freeTierNote, collapsedLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { remember = !remember }) {
                    Checkbox(remember, { remember = it }); Text(s.dontAskAgain, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSend(remember); onDismiss() }) { Text(s.send) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantSheet(vm: EditorViewModel, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val ui by vm.assistant.collectAsState()
    var pending by remember { mutableStateOf<NoteAssistant.Action?>(null) }
    fun start(a: NoteAssistant.Action) { if (vm.needsConsent()) pending = a else vm.runAssistant(a, false) }
    // Generic consent for the newer tools: body + what to run with the "remember" choice.
    var consent by remember { mutableStateOf<Pair<String, (Boolean) -> Unit>?>(null) }
    fun gated(body: String, run: (Boolean) -> Unit) { if (vm.needsConsent()) consent = body to run else run(false) }
    var showTools by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = { vm.dismissAssistant(); onDismiss() }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).heightIn(max = 640.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(s.assistant, style = MaterialTheme.typography.titleLarge)
            }
            when (val st = ui) {
                AssistantUi.Idle -> if (showTools) {
                    val target = vm.rewriteTarget()
                    if (target == null) Text(s.writeSomethingFirst, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        Text(if (target.isSelection) s.toolsOnSelection else s.toolsOnBlock, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ExpandableText(target.original, collapsedLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf(NoteAssistant.Rewrite.IMPROVE to s.rwImprove, NoteAssistant.Rewrite.FIX to s.rwFix, NoteAssistant.Rewrite.SHORTEN to s.rwShorten,
                            NoteAssistant.Rewrite.EXPAND to s.rwExpand, NoteAssistant.Rewrite.TRANSLATE to s.rwTranslate, NoteAssistant.Rewrite.FORMAL to s.rwFormal,
                            NoteAssistant.Rewrite.LEGAL to s.rwLegal).forEach { (m, label) ->
                            ActionRow(Icons.Rounded.EditNote, label, null) { gated(s.consentText(target.original.length)) { r -> vm.rewrite(m, target, r) } }
                        }
                    }
                    TextButton(onClick = { showTools = false }) { Text(s.back) }
                } else {
                    ActionRow(Icons.Rounded.QuestionAnswer, s.actAsk, s.actAskDesc) { vm.openChat() }
                    ActionRow(Icons.Rounded.EditNote, s.actTools, s.actToolsDesc) { showTools = true }
                    ActionRow(Icons.Rounded.Sell, s.actSuggest, s.actSuggestDesc) { gated(s.consentBody(vm.assistantPayload().first.length, vm.assistantPayload().second)) { r -> vm.suggest(r) } }
                    if (vm.assistantAvailable) {
                        ActionRow(Icons.AutoMirrored.Rounded.Notes, s.actSummary, s.actSummaryDesc) { start(NoteAssistant.Action.SUMMARY) }
                        ActionRow(Icons.Rounded.TaskAlt, s.actTasks, s.actTasksDesc) { start(NoteAssistant.Action.TASKS) }
                        ActionRow(Icons.Rounded.Route, s.actPlan, s.actPlanDesc) { start(NoteAssistant.Action.PLAN) }
                    } else Text(s.longNoteTools, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is AssistantUi.Chat -> {
                    st.turns.forEach { t ->
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                            Text(t.question, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                        SelectionContainerText(t.answer)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { clipboard.setText(AnnotatedString(t.answer)) }) { Text(s.copy) }
                            TextButton(onClick = { vm.insertAnswer(t.question, t.answer); onDismiss() }) { Text(s.insertIntoNote) }
                        }
                    }
                    if (st.turns.isEmpty()) Text(s.askHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    st.error?.let { ExpandableText(s.aiError(it), collapsedLines = 3, color = MaterialTheme.colorScheme.error) }
                    var q by remember { mutableStateOf("") }
                    if (st.loading) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)); Text(s.thinking) }
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.OutlinedTextField(q, { q = it.replace("\n", " ") }, label = { Text(s.yourQuestion) }, maxLines = 4, modifier = Modifier.weight(1f))
                        IconButton(enabled = q.isNotBlank(), onClick = {
                            val question = q
                            gated(s.consentBody(vm.assistantPayload().first.length, vm.assistantPayload().second)) { r -> vm.ask(question, r); q = "" }
                        }, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Rounded.Send, s.send, tint = com.example.ui.theme.Concept.ASSISTANT.g2) }
                    }
                }
                is AssistantUi.RewriteResult -> {
                    Text(s.rewriteResult, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    SelectionContainerText(st.result)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.applyRewrite(st, replace = true); onDismiss() }) { Text(if (st.target.isSelection) s.replaceSelection else s.replaceBlock) }
                        TextButton(onClick = { vm.applyRewrite(st, replace = false); onDismiss() }) { Text(s.insertBelow) }
                        TextButton(onClick = { clipboard.setText(AnnotatedString(st.result)) }) { Text(s.copy) }
                    }
                    Text(s.versionKept, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is AssistantUi.SuggestResult -> {
                    var useTitle by remember(st) { mutableStateOf(st.value.title.isNotBlank()) }
                    val tagSel = remember(st) { mutableStateListOf(*st.value.tags.toTypedArray()) }
                    var useFolder by remember(st) { mutableStateOf(st.value.folder != null) }
                    if (st.value.title.isNotBlank()) CheckRow(useTitle, s.suggestedTitle + ": " + st.value.title) { useTitle = it }
                    st.value.tags.forEach { t -> CheckRow(t in tagSel, "#$t") { on -> if (on) tagSel.add(t) else tagSel.remove(t) } }
                    st.value.folder?.let { f -> CheckRow(useFolder, s.suggestedFolder + ": " + f) { useFolder = it } }
                    if (st.value.title.isBlank() && st.value.tags.isEmpty() && st.value.folder == null) Text(s.nothingFound)
                    Button(onClick = { vm.applySuggestion(if (useTitle) st.value.title else null, tagSel.toList(), if (useFolder) st.value.folder else null); onDismiss() }) { Text(s.apply) }
                }
                AssistantUi.Loading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text(s.thinking)
                }
                is AssistantUi.SummaryResult -> {
                    Text(s.summaryLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ExpandableText(st.value.summary, collapsedLines = 8, style = MaterialTheme.typography.bodyLarge)
                    st.value.points.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.insertSummary(s.summaryLabel, st.value); onDismiss() }) { Text(s.insertAtTop) }
                        TextButton(onClick = { clipboard.setText(AnnotatedString(st.value.summary + st.value.points.joinToString("") { "\n• $it" })) }) { Text(s.copy) }
                    }
                }
                is AssistantUi.TasksResult -> {
                    if (st.tasks.isEmpty()) Text(s.noTasksFound)
                    else {
                        val chosen = remember(st) { mutableStateListOf(*st.tasks.indices.toList().toTypedArray()) }
                        st.tasks.forEachIndexed { i, t ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { if (i in chosen) chosen.remove(i) else chosen.add(i) }) {
                                Icon(if (i in chosen) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(t.text, style = MaterialTheme.typography.bodyLarge)
                                    t.due?.let { Text(s.dueLabel(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                        Button(enabled = chosen.isNotEmpty(), onClick = {
                            vm.addTasks(chosen.sorted().map { i -> st.tasks[i].let { t -> t.text + (t.due?.let { " — $it" } ?: "") } }); onDismiss()
                        }) { Text(s.addAsChecklist) }
                    }
                }
                is AssistantUi.PlanResult -> {
                    if (st.plan.goal.isNotBlank()) { Text(s.goalLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary); Text(st.plan.goal) }
                    st.plan.steps.forEachIndexed { i, step -> Text("${s.num(i + 1)}. $step", style = MaterialTheme.typography.bodyLarge) }
                    Button(onClick = { vm.insertPlan(s.planLabel, s.goalLabel, st.plan); onDismiss() }) { Text(s.insertPlan) }
                }
                is AssistantUi.CompareResult -> {
                    Text(st.otherTitle, style = MaterialTheme.typography.titleMedium)
                    if (st.value.note.isNotBlank()) ExpandableText(st.value.note, collapsedLines = 4)
                    if (st.value.contradictions.isEmpty() && st.value.outdated.isEmpty()) Text(s.nothingFound)
                    if (st.value.contradictions.isNotEmpty()) {
                        Text(s.contradictions, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                        st.value.contradictions.forEach { Text("• $it") }
                    }
                    if (st.value.outdated.isNotEmpty()) {
                        Text(s.outdatedInfo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        st.value.outdated.forEach { Text("• $it") }
                    }
                }
                is AssistantUi.Failed -> {
                    ExpandableText(s.aiError(st.error), collapsedLines = 4, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { vm.dismissAssistant() }) { Text(s.back) }
                }
            }
        }
    }
    consent?.let { (body, run) -> ConsentDialog(body, onSend = { r -> run(r) }) { consent = null } }
    pending?.let { a ->
        val (text, truncated) = vm.assistantPayload()
        ConsentDialog(s.consentBody(text.length, truncated), onSend = { remember -> vm.runAssistant(a, remember) }) { pending = null }
    }
}

@Composable
private fun CheckRow(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onChange(!checked) }) {
        Checkbox(checked, onChange); Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SelectionContainerText(text: String) {
    androidx.compose.foundation.text.selection.SelectionContainer { ExpandableText(text, collapsedLines = 14, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, desc: String?, onClick: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Notea Memory inside a note: related notes computed on the device. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RelatedNotesSection(vm: EditorViewModel, onOpen: (String) -> Unit, onCompare: ((String) -> Unit)?) {
    val s = LocalStrings.current
    val related by vm.related.collectAsState()
    if (related.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Hub, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(6.dp))
            Text(s.relatedNotes, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        related.forEach { r ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                modifier = Modifier.fillMaxWidth().clickable { onOpen(r.id) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(r.title.ifBlank { s.untitled }, style = MaterialTheme.typography.titleSmall)
                    if (r.sharedWords.isNotEmpty()) Text(s.sharedWords(r.sharedWords.joinToString("، ")), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (onCompare != null) TextButton(onClick = { onCompare(r.id) }) {
                        Icon(Icons.Rounded.CompareArrows, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(s.compareWithAssistant)
                    }
                }
            }
        }
    }
}
