package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Attachment
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.AttachmentKind
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalIsDark
import com.example.ui.theme.NoteCardDark
import com.example.ui.theme.NoteCardLight
import com.example.ui.theme.noteColor
import com.example.ui.viewmodel.NoteCardModel
import kotlinx.coroutines.launch

/**
 * Project-wide rule: text always wraps (never cut to one line with "…"). Long text is collapsed
 * to [collapsedLines] and gets a "Show more / Show less" toggle — only when it actually overflows.
 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 4,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null
) {
    val s = LocalStrings.current
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column(modifier.animateContentSize()) {
        Text(
            text, style = style, color = color, fontWeight = fontWeight, textDecoration = textDecoration,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow }
        )
        if (overflows || expanded) {
            Text(
                if (expanded) s.showLess else s.showMore,
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { expanded = !expanded }
                    .heightIn(min = 40.dp).padding(vertical = 10.dp, horizontal = 2.dp)
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
    )
}

/** Grouped preference container (rounded card with rows), in the spirit of clean settings lists. */
@Composable
fun PreferenceGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) { Column(content = content) }
}

@Composable
fun PreferenceRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            if (subtitle != null) ExpandableText(subtitle, collapsedLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
    }
}

@Composable
fun SwitchRow(icon: ImageVector?, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    PreferenceRow(icon, title, subtitle, onClick = { onChange(!checked) }) {
        Row(verticalAlignment = Alignment.CenterVertically) { SavedTick(checked); Switch(checked, onCheckedChange = onChange) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    val palette = if (LocalIsDark.current) NoteCardDark else NoteCardLight
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        palette.forEachIndexed { i, c ->
            val sel = i == selected
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(c)
                    .border(if (sel) 2.5.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(role = Role.RadioButton) { onSelect(i) }
                    .semantics { contentDescription = "color $i" },
                contentAlignment = Alignment.Center
            ) { if (sel) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NoteCard(
    model: NoteCardModel,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val s = LocalStrings.current
    val n = model.note
    val bg by animateColorAsState(noteColor(n.colorIndex), label = "card")
    Surface(
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        color = bg,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = if (n.colorIndex == 0) 0.dp else 0.5.dp
    ) {
        Column {
            if (model.firstImagePath != null) {
                AsyncImage(
                    model = java.io.File(model.firstImagePath), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        (if (n.icon.isNotBlank()) n.icon + " " else "") +
                            n.title.ifBlank { if (model.preview.isBlank() && model.details.checklist.isEmpty()) s.untitled else "" },
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)
                    )
                    if (n.isPinned) Icon(Icons.Rounded.PushPin, s.pinned, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    if (n.isFavorite) Icon(Icons.Rounded.Star, s.favorites, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                }
                if (model.childCount > 0) Text("↳ " + s.subNotesCount(model.childCount), style = MaterialTheme.typography.labelMedium,
                    color = com.example.ui.theme.Concept.FOLDERS.g2)
                if (n.isLocked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(s.lockedPreview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    if (model.preview.isNotBlank()) {
                        ExpandableText(model.preview, collapsedLines = 6, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val items = model.details.sortedChecklist
                    if (items.isNotEmpty()) {
                        var allItems by androidx.compose.runtime.saveable.rememberSaveable(n.id) { mutableStateOf(false) }
                        (if (allItems) items else items.take(4)).forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (item.checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = if (item.checked) 0.5f else 1f))
                                Spacer(Modifier.width(6.dp))
                                Text(item.text, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.checked) 0.5f else 0.9f))
                            }
                        }
                        if (items.size > 4) {
                            Text(s.completedItems(items.count { it.checked }, items.size), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (allItems) s.showLess else s.showAllItems(items.size), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { allItems = !allItems }.padding(vertical = 8.dp))
                        }
                    }
                }
                val reminder = model.details.activeReminder
                val tags = model.details.tags
                if (reminder != null || tags.isNotEmpty() || model.counts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (reminder != null) MetaPill(Icons.Rounded.Notifications, s.date(reminder.triggerAt))
                        model.counts[AttachmentKind.IMAGE]?.let { if (model.firstImagePath == null || it > 1) MetaPill(Icons.Rounded.Image, s.num(it)) }
                        model.counts[AttachmentKind.AUDIO]?.let { MetaPill(Icons.Rounded.Mic, s.num(it)) }
                        val files = (model.counts[AttachmentKind.PDF] ?: 0) + (model.counts[AttachmentKind.FILE] ?: 0) + (model.counts[AttachmentKind.LINK] ?: 0)
                        if (files > 0) MetaPill(Icons.Rounded.Attachment, s.num(files))
                        tags.take(3).forEach { MetaPill(null, "#" + it.name) }
                    }
                }
            }
        }
    }
}

@Composable
fun MetaPill(icon: ImageVector?, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String?, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, destructive: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { ExpandableText(text, collapsedLines = 8) } },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
fun TextInputDialog(title: String, label: String, initial: String = "", confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it.replace("\n", " ") }, label = { Text(label) }, maxLines = 4, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
fun SecretField(value: String, onChange: (String) -> Unit, label: String, numeric: Boolean = false, isError: Boolean = false, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password),
        trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) } },
        modifier = modifier.fillMaxWidth()
    )
}

/** Sets a 4–8 digit PIN with confirmation and optional hint. */
@Composable
fun SetPinDialog(title: String, withHint: Boolean, onSet: (pin: String, hint: String) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecretField(pin, { com.example.core.text.Digits.pin(it)?.let { v -> pin = v } }, s.newPin, numeric = true)
                SecretField(confirmPin, { com.example.core.text.Digits.pin(it)?.let { v -> confirmPin = v } }, s.confirmPin, numeric = true, isError = error != null)
                if (withHint) OutlinedTextField(hint, { hint = it.take(60).replace("\n", " ") }, label = { Text(s.pinHint) }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    pin.length < 4 -> s.pinTooShort
                    pin != confirmPin -> s.pinMismatch
                    else -> null
                }
                if (error == null) { onSet(pin, hint.trim()); onDismiss() }
            }) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

/** Asks for a PIN; [verify] runs off the main thread. */
@Composable
fun VerifyPinDialog(title: String, hint: String?, verify: suspend (String) -> Boolean, onSuccess: () -> Unit, onDeviceCredential: (() -> Unit)?, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecretField(pin, { com.example.core.text.Digits.pin(it)?.let { v -> pin = v; error = false } }, s.enterPin, numeric = true, isError = error)
                if (error) Text(s.wrongPin, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (!hint.isNullOrBlank()) Text(s.hintLabel(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onDeviceCredential != null) TextButton(onClick = onDeviceCredential, contentPadding = PaddingValues(0.dp)) { Text(s.useDeviceLock) }
            }
        },
        confirmButton = {
            TextButton(enabled = pin.length >= 4 && !busy, onClick = {
                busy = true
                scope.launch {
                    if (verify(pin)) { onSuccess(); onDismiss() } else { error = true; pin = "" }
                    busy = false
                }
            }) { Text(s.confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

/** Vault passphrase dialog: create (with confirmation) or unlock. */
@Composable
fun PassphraseDialog(create: Boolean, title: String, body: String, submit: suspend (CharArray) -> Boolean, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExpandableText(body, collapsedLines = 4, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecretField(p1, { p1 = it; error = null }, s.passphrase, isError = error != null)
                if (create) SecretField(p2, { p2 = it; error = null }, s.confirmPassphrase, isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) Text(s.working, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                error = when {
                    p1.length < 8 -> s.passphraseShort
                    create && p1 != p2 -> s.passphraseMismatch
                    else -> null
                }
                if (error == null) {
                    busy = true
                    scope.launch {
                        val ok = submit(p1.toCharArray())
                        busy = false
                        if (ok) { onSuccess(); onDismiss() } else error = s.wrongPassphrase
                    }
                }
            }) { Text(if (create) s.save else s.confirm) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer, onClick: (() -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(50), color = color, modifier = if (onClick != null) Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick) else Modifier) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/** Rounded square with the concept's vivid gradient and a white icon (no text on it). */
@Composable
fun ConceptIcon(icon: ImageVector, concept: com.example.ui.theme.Concept, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(concept.g1, concept.g2))),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.55f)) }
}

/** ⓘ next to a number: explains where it comes from and how it was computed. */
@Composable
fun InfoButton(title: String, explanation: String) {
    val s = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(44.dp)) {
        Icon(Icons.Rounded.Info, s.explain, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        icon = { Icon(Icons.Rounded.Info, null) },
        title = { Text(title) },
        text = { ExpandableText(explanation, collapsedLines = 10) },
        confirmButton = { TextButton(onClick = { open = false }) { Text(s.ok) } }
    )
}

/** "Saved ✓" shown for a moment after a setting changes. [key] changes on every save. */
@Composable
fun SavedTick(key: Any?) {
    val s = LocalStrings.current
    var visible by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(key) {
        if (first) { first = false; return@LaunchedEffect }
        visible = true; kotlinx.coroutines.delay(1600); visible = false
    }
    androidx.compose.animation.AnimatedVisibility(visible, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
        Text(s.saved, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp))
    }
}
