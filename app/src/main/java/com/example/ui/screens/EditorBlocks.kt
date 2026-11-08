package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.AttachmentEntity
import com.example.data.local.AttachmentKind
import com.example.ocr.OcrWorker
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.ExpandableText
import com.example.ui.editor.MarkdownEngine
import com.example.ui.i18n.LocalStrings
import com.example.ui.platform.AudioPlayer
import com.example.ui.platform.FileOpener
import com.example.ui.viewmodel.EditorBlock
import com.example.ui.viewmodel.EditorState
import com.example.ui.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Renders a note as an ordered list of blocks (text, checklists, attachments). */
@Composable
fun BlocksContent(vm: EditorViewModel, st: EditorState, readOnly: Boolean) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val palette = MarkdownEngine.Palette(
        marker = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        link = MaterialTheme.colorScheme.primary,
        quote = MaterialTheme.colorScheme.onSurfaceVariant,
        accent = MaterialTheme.colorScheme.secondary
    )
    val onlyText = st.blocks.size == 1 && st.blocks.single() is EditorBlock.Text
    var confirmRemove by remember { mutableStateOf<AttachmentEntity?>(null) }
    val attachmentsById = st.attachments.associateBy { it.id }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        st.blocks.forEachIndexed { index, block ->
            val canUp = index > 0
            val canDown = index < st.blocks.lastIndex
            when (block) {
                is EditorBlock.Text -> if (block.kind != com.example.data.local.BlockType.TEXT) {
                    BlockFrame(readOnly, canUp, canDown, onMove = { vm.moveBlock(block.id, it) }, onRemove = { vm.removeBlock(block.id) }) {
                        SpecialTextBlock(vm, block, st.focusedBlockId == block.id, readOnly, palette)
                    }
                } else {
                    val transformation = remember(palette, block.id) { MarkdownEngine.Transformation(palette) }
                    BasicTextField(
                        value = block.value, onValueChange = { vm.setText(block.id, it) }, readOnly = readOnly,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (onlyText || index == st.blocks.lastIndex) 120.dp else 32.dp)
                            .padding(vertical = 4.dp)
                            .onFocusChanged { if (it.isFocused) vm.focus(block.id) },
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth()) {
                                if (block.value.text.isEmpty() && (onlyText || index == 0)) Text(s.bodyHint, style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                inner()
                            }
                        }
                    )
                    if (st.focusedBlockId == block.id) {
                        val url = MarkdownEngine.urlAt(block.value.text, block.value.selection.start)
                        AnimatedVisibility(url != null) {
                            AssistChip(onClick = { url?.let { FileOpener.openUrl(context, it) } }, label = { Text(url.orEmpty(), modifier = Modifier.padding(vertical = 6.dp)) },
                                leadingIcon = { Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp)) })
                        }
                    }
                }
                is EditorBlock.Checklist -> BlockFrame(readOnly, canUp, canDown, onMove = { vm.moveBlock(block.id, it) }, onRemove = { vm.removeBlock(block.id) }) {
                    ChecklistBlock(vm, block.id, st, readOnly)
                }
                is EditorBlock.Attachment -> {
                    val a = attachmentsById[block.attachmentId]
                    if (a != null) BlockFrame(readOnly, canUp, canDown, onMove = { vm.moveBlock(block.id, it) }, onRemove = { confirmRemove = a }) {
                        AttachmentBlock(vm, a, block.id, st.isLocked, readOnly)
                    }
                }
            }
        }
    }
    confirmRemove?.let { a -> ConfirmDialog(s.removeAttachment, a.displayName, s.delete, true, { vm.removeAttachment(a.id) }) { confirmRemove = null } }
}

/** Common frame with a small menu: move up / move down / remove. */
@Composable
private fun BlockFrame(readOnly: Boolean, canUp: Boolean, canDown: Boolean, onMove: (Int) -> Unit, onRemove: () -> Unit, content: @Composable () -> Unit) {
    val s = LocalStrings.current
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        content()
        if (!readOnly) Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                    Icon(Icons.Rounded.MoreVert, s.more, Modifier.padding(4.dp).size(18.dp))
                }
            }
            DropdownMenu(menu, { menu = false }) {
                if (canUp) DropdownMenuItem(text = { Text(s.moveUp) }, leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) }, onClick = { menu = false; onMove(-1) })
                if (canDown) DropdownMenuItem(text = { Text(s.moveDown) }, leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) }, onClick = { menu = false; onMove(1) })
                DropdownMenuItem(text = { Text(s.removeBlock) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun ChecklistBlock(vm: EditorViewModel, blockId: String, st: EditorState, readOnly: Boolean) {
    val s = LocalStrings.current
    val items = st.checklist.filter { (it.blockId ?: com.example.data.repository.Blocks.DEFAULT_CHECKLIST) == blockId }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 4.dp, end = 40.dp, top = 6.dp, bottom = 2.dp)) {
            if (items.isNotEmpty()) Text(s.completedItems(items.count { it.checked }, items.size), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
            (items.filter { !it.checked } + items.filter { it.checked }).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.toggleChecklistItem(item.id) }, enabled = !readOnly) {
                        Icon(if (item.checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    BasicTextField(
                        value = item.text, onValueChange = { vm.updateChecklistItem(item.id, it.replace("\n", " ")) }, readOnly = readOnly,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.checked) 0.5f else 1f),
                            textDecoration = if (item.checked) TextDecoration.LineThrough else null
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { vm.addChecklistItem(blockId) }),
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        decorationBox = { inner -> if (item.text.isEmpty()) Text(s.itemHint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)); inner() }
                    )
                    if (!readOnly) IconButton(onClick = { vm.removeChecklistItem(item.id) }) { Icon(Icons.Rounded.Close, s.delete, Modifier.size(18.dp)) }
                }
            }
            if (!readOnly) TextButton(onClick = { vm.addChecklistItem(blockId) }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.addItem)
            }
        }
    }
}

@Composable
private fun AttachmentBlock(vm: EditorViewModel, a: AttachmentEntity, blockId: String, noteLocked: Boolean, readOnly: Boolean) {
    val s = LocalStrings.current
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (a.kind) {
            AttachmentKind.IMAGE -> {
                val file by produceState<java.io.File?>(null, a.id, a.localPath) { value = withContext(Dispatchers.IO) { vm.viewableFile(a) } }
                AsyncImage(
                    model = file, contentDescription = a.displayName, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 420.dp).clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                        .clickable { file?.let { if (!FileOpener.open(context, it, a.mimeType)) android.widget.Toast.makeText(context, s.noAppToOpen, android.widget.Toast.LENGTH_SHORT).show() } }
                )
            }
            else -> {
                var playing by remember(a.id) { mutableStateOf(false) }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.clickable {
                        when (a.kind) {
                            AttachmentKind.LINK -> FileOpener.openUrl(context, a.url)
                            AttachmentKind.AUDIO -> vm.viewableFile(a)?.let { f -> AudioPlayer.toggle(f) { playing = it } }
                            else -> { val f = vm.viewableFile(a); if (f == null || !FileOpener.open(context, f, a.mimeType)) android.widget.Toast.makeText(context, s.noAppToOpen, android.widget.Toast.LENGTH_SHORT).show() }
                        }
                    }.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(when (a.kind) {
                            AttachmentKind.AUDIO -> if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow
                            AttachmentKind.PDF -> Icons.Rounded.PictureAsPdf
                            AttachmentKind.LINK -> Icons.Rounded.Link
                            else -> Icons.Rounded.Description
                        }, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (a.kind == AttachmentKind.LINK) a.linkTitle.ifBlank { a.url } else a.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            val sub = when (a.kind) {
                                AttachmentKind.LINK -> a.linkDescription.ifBlank { a.url }
                                AttachmentKind.AUDIO -> "%d:%02d".format(a.durationMs / 60000, (a.durationMs / 1000) % 60)
                                else -> android.text.format.Formatter.formatShortFileSize(context, a.sizeBytes)
                            }
                            ExpandableText(sub, collapsedLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if ((a.kind == AttachmentKind.IMAGE || a.kind == AttachmentKind.PDF) && !noteLocked && !a.encrypted) OcrPanel(vm, a, blockId, readOnly)
        if (a.kind == AttachmentKind.AUDIO && !noteLocked && !a.encrypted) AudioPanel(vm, a, blockId, readOnly)
    }
}

/** Text read from an image/PDF on the device: copy it or insert it into the note. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OcrPanel(vm: EditorViewModel, a: AttachmentEntity, blockId: String, readOnly: Boolean) {
    val s = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val text = a.extractedText
    val reading by vm.preciseOcr.collectAsState()
    var consent by remember { mutableStateOf(false) }
    val precise: () -> Unit = { if (vm.needsConsent()) consent = true else vm.readImagePrecisely(a.id, false, { s.aiError(it) }, s.preciseDone) }
    if (consent) ConsentDialog(s.consentImageBody, onSend = { r -> vm.readImagePrecisely(a.id, r, { s.aiError(it) }, s.preciseDone) }) { consent = false }
    val canPrecise = !readOnly && a.kind == AttachmentKind.IMAGE && !a.encrypted && vm.preciseOcrAvailable
    if (a.id in reading) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp)); Text(s.preciseReading, style = MaterialTheme.typography.labelMedium)
        }
        return
    }
    @Composable fun PreciseButton() {
        if (canPrecise) TextButton(onClick = precise, modifier = Modifier.heightIn(min = 44.dp)) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(s.preciseRead)
        }
    }
    when {
        text.isBlank() -> Column {
            Text(s.ocrPending, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
            PreciseButton()
        }
        text == OcrWorker.NO_TEXT -> Column {
            Text(s.ocrNoClearText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp))
            if (canPrecise) PreciseButton()
            else if (a.kind == AttachmentKind.IMAGE && !a.encrypted && !readOnly) Text(s.preciseHint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        }
        else -> Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.TextSnippet, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(6.dp))
                    Text(s.ocrTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ExpandableText(text, collapsedLines = 4, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(text))
                        android.widget.Toast.makeText(context, s.copied, android.widget.Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(s.copy) }
                    if (!readOnly) TextButton(onClick = { vm.insertTextAfter(blockId, text) }) { Text(s.insertIntoNote) }
                    PreciseButton()
                }
            }
        }
    }
}

/** Toggle (first line = heading, the rest folds away) and callout (highlighted) blocks. */
@Composable
private fun SpecialTextBlock(vm: EditorViewModel, block: EditorBlock.Text, focused: Boolean, readOnly: Boolean, palette: MarkdownEngine.Palette) {
    val s = LocalStrings.current
    val isToggle = block.kind == com.example.data.local.BlockType.TOGGLE
    var open by androidx.compose.runtime.saveable.rememberSaveable(block.id) { mutableStateOf(block.value.text.isEmpty()) }
    val concept = if (isToggle) com.example.ui.theme.Concept.FOLDERS else com.example.ui.theme.Concept.SECURITY
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isToggle) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f) else concept.g1.copy(alpha = if (dark) 0.16f else 0.22f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 6.dp, end = 40.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            if (isToggle) IconButton(onClick = { open = !open }, modifier = Modifier.size(44.dp)) {
                Icon(if (open) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronLeft, if (open) s.collapse else s.expand,
                    modifier = Modifier.then(if (!open && !s.isArabic) Modifier.graphicsLayer(scaleX = -1f) else Modifier))
            } else Text("💡", modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp))
            val heading = block.value.text.substringBefore('\n')
            if (isToggle && !open) {
                Text(heading.ifBlank { s.toggleHint }, style = MaterialTheme.typography.titleMedium,
                    color = if (heading.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).clickable { open = true }.padding(vertical = 10.dp))
            } else {
                val transformation = remember(palette, block.id) { MarkdownEngine.Transformation(palette) }
                BasicTextField(
                    value = block.value, onValueChange = { vm.setText(block.id, it) }, readOnly = readOnly,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).padding(vertical = 10.dp).onFocusChanged { if (it.isFocused) vm.focus(block.id) },
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            if (block.value.text.isEmpty()) Text(if (isToggle) s.toggleHint else s.calloutHint, style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            inner()
                        }
                    }
                )
            }
        }
    }
}

/** Transcript of a recording (Gemini, after consent): stored with the recording, searchable, insertable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioPanel(vm: EditorViewModel, a: AttachmentEntity, blockId: String, readOnly: Boolean) {
    val s = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val busy by vm.transcribing.collectAsState()
    var consent by remember { mutableStateOf(false) }
    val run: (Boolean) -> Unit = { r -> vm.transcribe(a.id, r, s.summaryLabel, { s.aiError(it) }, s.transcribeDone) }
    if (consent) ConsentDialog(s.consentAudioBody, onSend = run) { consent = false }
    val text = a.extractedText
    when {
        a.id in busy -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp)); Text(s.transcribing, style = MaterialTheme.typography.labelMedium)
        }
        text.isBlank() || text == OcrWorker.NO_TEXT -> if (!readOnly) {
            if (vm.assistantReady) TextButton(onClick = { if (vm.needsConsent()) consent = true else run(false) }, modifier = Modifier.heightIn(min = 44.dp)) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(s.transcribe)
            } else Text(s.transcribeHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        }
        else -> Surface(shape = MaterialTheme.shapes.medium, color = com.example.ui.theme.Concept.ASSISTANT.g1.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.transcriptTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExpandableText(text, collapsedLines = 5, style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(s.copy) }
                    if (!readOnly) TextButton(onClick = { vm.insertTextAfter(blockId, text) }) { Text(s.insertIntoNote) }
                }
            }
        }
    }
}
