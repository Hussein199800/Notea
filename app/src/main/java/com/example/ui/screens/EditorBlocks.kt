package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.example.data.local.AttachmentEntity
import com.example.data.local.AttachmentKind
import com.example.ocr.OcrWorker
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.ExpandableText
import com.example.ui.editor.MarkdownEngine
import com.example.ui.i18n.LocalStrings
import com.example.ui.platform.FileOpener
import com.example.ui.viewmodel.EditorBlock
import com.example.ui.viewmodel.EditorState
import com.example.ui.viewmodel.EditorViewModel

/** Renders a note as an ordered list of blocks (text, checklists, attachments). */
@Composable
fun BlocksContent(vm: EditorViewModel, st: EditorState, readOnly: Boolean, readingDirection: TextDirection) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val palette = noteMarkdownPalette()
    val onlyText = st.blocks.size == 1 && st.blocks.single() is EditorBlock.Text
    var confirmRemove by remember { mutableStateOf<AttachmentEntity?>(null) }
    val attachmentsById = st.attachments.associateBy { it.id }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        st.blocks.forEachIndexed { index, block ->
            key(block.id) {
                val canUp = index > 0
                val canDown = index < st.blocks.lastIndex
                when (block) {
                    is EditorBlock.Text -> {
                        if (readOnly) {
                            if (block.value.text.isNotBlank()) NoteReadingText(block.value.text, palette, readingDirection)
                        } else {
                            val transformation = remember(palette, block.id) { MarkdownEngine.Transformation(palette) }
                            BasicTextField(
                                value = block.value, onValueChange = { vm.setText(block.id, it) },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Start,
                                    textDirection = TextDirection.Content
                                ),
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
                    }
                    is EditorBlock.Checklist -> BlockFrame(readOnly, canUp, canDown, onMove = { vm.moveBlock(block.id, it) }, onRemove = { vm.removeBlock(block.id) }) {
                        ChecklistBlock(vm, block.id, st, readOnly, readingDirection)
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
    }
    confirmRemove?.let { a -> ConfirmDialog(s.removeAttachment, a.displayName, s.delete, true, { vm.removeAttachment(a.id) }) { confirmRemove = null } }
}

@Composable
private fun noteMarkdownPalette() = MarkdownEngine.Palette(
    marker = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
    link = MaterialTheme.colorScheme.primary,
    quote = MaterialTheme.colorScheme.onSurfaceVariant,
    accent = MaterialTheme.colorScheme.secondary
)

@Composable
internal fun NoteReadingText(
    text: String,
    palette: MarkdownEngine.Palette = noteMarkdownPalette(),
    direction: TextDirection = TextDirection.Content
) {
    val context = LocalContext.current
    val rendered = remember(text, palette, context) {
        MarkdownEngine.render(text, palette) { FileOpener.openUrl(context, it) }
    }
    SelectionContainer {
        Text(
            rendered,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Start, textDirection = direction)
        )
    }
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
private fun ChecklistBlock(vm: EditorViewModel, blockId: String, st: EditorState, readOnly: Boolean, readingDirection: TextDirection) {
    val s = LocalStrings.current
    val items = st.checklist.filter { (it.blockId ?: com.example.data.repository.Blocks.DEFAULT_CHECKLIST) == blockId }
    if (readOnly && items.isEmpty()) return
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 4.dp, end = if (readOnly) 12.dp else 40.dp, top = 6.dp, bottom = 2.dp)) {
            if (items.isNotEmpty()) Text(s.completedItems(items.count { it.checked }, items.size), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
            (items.filter { !it.checked } + items.filter { it.checked }).forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val check = if (item.checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank
                    val style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.checked) 0.5f else 1f),
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        textAlign = TextAlign.Start,
                        textDirection = if (readOnly) readingDirection else TextDirection.Content
                    )
                    if (readOnly) {
                        Icon(check, if (item.checked) s.checkedItem else s.uncheckedItem,
                            Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(item.text, modifier = Modifier.weight(1f).padding(vertical = 8.dp), style = style)
                    } else {
                        IconButton(onClick = { vm.toggleChecklistItem(item.id) }) {
                            Icon(check, if (item.checked) s.checkedItem else s.uncheckedItem, tint = MaterialTheme.colorScheme.primary)
                        }
                        BasicTextField(
                            value = item.text, onValueChange = { vm.updateChecklistItem(item.id, it.replace("\n", " ")) },
                            textStyle = style,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { vm.addChecklistItem(blockId) }),
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                            decorationBox = { inner -> if (item.text.isEmpty()) Text(s.itemHint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)); inner() }
                        )
                        IconButton(onClick = { vm.removeChecklistItem(item.id) }) { Icon(Icons.Rounded.Close, s.delete, Modifier.size(18.dp)) }
                    }
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AttachmentContent(vm, a, noteLocked, readOnly)
        if ((a.kind == AttachmentKind.IMAGE || a.kind == AttachmentKind.PDF) && !noteLocked && !a.encrypted) OcrPanel(vm, a, blockId, readOnly)
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
    when {
        text.isBlank() -> Text(s.ocrPending, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
        text == OcrWorker.NO_TEXT -> Unit
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
                }
            }
        }
    }
}
