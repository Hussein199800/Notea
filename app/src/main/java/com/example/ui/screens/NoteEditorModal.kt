package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.ChecklistItem
import com.example.data.model.Note
import com.example.data.model.NoteCategory
import com.example.data.model.NoteRichMetadata
import com.example.data.model.PinSecurity
import com.example.security.CryptoManager
import com.example.ui.i18n.LocalVaultStrings
import com.example.ui.theme.NoteCardDarkColors
import com.example.ui.theme.NoteCardLightColors
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun NoteEditorScreen(
    note: Note?,
    isCompact: Boolean,
    onClose: () -> Unit,
    onSave: (
        title: String,
        content: String,
        baseCategory: String,
        colorIndex: Int,
        isPinned: Boolean,
        isFavorite: Boolean,
        isLocked: Boolean,
        notePinHash: String,
        lockHint: String,
        isArchived: Boolean
    ) -> Unit,
    onMoveToTrash: (Note) -> Unit,
    onRestoreFromTrash: (Note) -> Unit,
    onDeletePermanently: (String) -> Unit
) {
    val strings = LocalVaultStrings.current
    val isDark = isSystemInDarkTheme()
    val palette = if (isDark) NoteCardDarkColors else NoteCardLightColors

    val decryptedRawContent = remember(note?.id, note?.content) {
        CryptoManager.decrypt(note?.content ?: "")
    }
    val parsedMeta = remember(note?.id, decryptedRawContent) {
        NoteRichMetadata.parse(decryptedRawContent)
    }

    var title by remember(note?.id) { mutableStateOf(note?.title ?: "") }
    var bodyText by remember(note?.id) { mutableStateOf(parsedMeta.bodyText) }
    var baseCategory by remember(note?.id) { mutableStateOf(note?.baseCategory ?: NoteCategory.PERSONAL.key) }
    var colorIndex by remember(note?.id) { mutableIntStateOf((note?.colorIndex ?: 0L).toInt().coerceIn(0, 5)) }
    var isPinned by remember(note?.id) { mutableStateOf(note?.isPinned ?: false) }
    var isFavorite by remember(note?.id) { mutableStateOf(note?.isFavorite ?: false) }
    var isLocked by remember(note?.id) { mutableStateOf(note?.isLocked ?: false) }
    var notePinHash by remember(note?.id) { mutableStateOf(note?.notePinHash ?: "") }
    var lockHint by remember(note?.id) { mutableStateOf(note?.cleanLockHint() ?: "") }
    var isArchived by remember(note?.id) { mutableStateOf(note?.isArchived ?: false) }

    val checklistItems = remember(note?.id) {
        mutableStateListOf<ChecklistItem>().apply { addAll(parsedMeta.checklistItems) }
    }
    var newChecklistText by remember(note?.id) { mutableStateOf("") }
    var reminderLabel by remember(note?.id) { mutableStateOf(parsedMeta.reminderLabel) }
    var imageUri by remember(note?.id) { mutableStateOf(parsedMeta.imageUri) }
    var voiceMemoLabel by remember(note?.id) { mutableStateOf(parsedMeta.voiceMemoLabel) }
    var tagsText by remember(note?.id) { mutableStateOf(parsedMeta.tags.joinToString(", ")) }

    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var showSetNotePinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingSeconds = 0
            while (isRecordingVoice) {
                delay(1000L)
                recordingSeconds++
            }
        }
    }

    // Zero-permission Android Photo Picker (Google Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageUri = uri.toString()
        }
    }

    if (isCompact) {
        BackHandler(onBack = onClose)
    }

    val backgroundColor = palette.getOrElse(colorIndex) { palette.first() }

    fun buildSerializedContent(): String {
        val parsedTags = tagsText.split(",", "،")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return NoteRichMetadata(
            bodyText = bodyText,
            checklistItems = checklistItems.toList(),
            reminderLabel = reminderLabel.trim(),
            imageUri = imageUri,
            voiceMemoLabel = voiceMemoLabel,
            tags = parsedTags
        ).serialize()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Top Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("editor_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.cancel
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Favorite Star Toggle
                    IconButton(
                        onClick = { isFavorite = !isFavorite },
                        modifier = Modifier.testTag("editor_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (isFavorite) strings.unfavoriteNote else strings.favoriteNote,
                            tint = if (isFavorite) Color(0xFFD99B00) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Pin Toggle
                    IconButton(
                        onClick = { isPinned = !isPinned },
                        modifier = Modifier.testTag("editor_pin_button")
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (isPinned) strings.unpinNote else strings.pinNote,
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Per-Note Lock & AES-256 Encryption Toggle
                    FilledTonalIconButton(
                        onClick = {
                            if (isLocked) {
                                isLocked = false
                                notePinHash = ""
                                lockHint = ""
                            } else {
                                showSetNotePinDialog = true
                            }
                        },
                        modifier = Modifier.testTag("editor_lock_button")
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isLocked) strings.removeNoteLockAction else strings.lockNoteAction,
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Archive Toggle
                    IconButton(
                        onClick = { isArchived = !isArchived },
                        modifier = Modifier.testTag("editor_archive_button")
                    ) {
                        Icon(
                            imageVector = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = if (isArchived) strings.unarchiveNote else strings.archiveNote
                        )
                    }

                    if (note != null && note.id.isNotBlank()) {
                        if (note.isTrashed) {
                            IconButton(
                                onClick = {
                                    onRestoreFromTrash(note)
                                    onClose()
                                },
                                modifier = Modifier.testTag("editor_restore_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestoreFromTrash,
                                    contentDescription = strings.restoreFromTrash,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    onDeletePermanently(note.id)
                                    onClose()
                                },
                                modifier = Modifier.testTag("editor_delete_forever_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = strings.deletePermanently,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    onMoveToTrash(note)
                                    onClose()
                                },
                                modifier = Modifier.testTag("editor_trash_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = strings.moveToTrash,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val finalTitle = title.trim().ifBlank { strings.untitledNote }
                            onSave(
                                finalTitle,
                                buildSerializedContent(),
                                baseCategory,
                                colorIndex,
                                isPinned,
                                isFavorite,
                                isLocked,
                                notePinHash,
                                lockHint,
                                isArchived
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.saveNote)
                    }
                }
            }

            if (isLocked) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.lockedNoteLabel +
                                if (lockHint.isNotBlank()) " (${strings.hintPrefix}$lockHint)" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Color Picker Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.colorLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    palette.forEachIndexed { idx, swatchColor ->
                        val selected = idx == colorIndex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { colorIndex = idx }
                                .testTag("color_swatch_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Category Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val editableCategories = NoteCategory.entries.filter { it != NoteCategory.ALL }
                    editableCategories.forEach { cat ->
                        FilterChip(
                            selected = baseCategory == cat.key,
                            onClick = { baseCategory = cat.key },
                            label = { Text(strings.categoryName(cat.key)) }
                        )
                    }
                }

                // Rich Text Formatting & Attachment Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { bodyText = "$bodyText **نص عريض** " },
                        label = { Text("B") },
                        leadingIcon = { Icon(Icons.Default.FormatBold, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { bodyText = "$bodyText _نص مائل_ " },
                        label = { Text("I") },
                        leadingIcon = { Icon(Icons.Default.FormatItalic, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { bodyText = "$bodyText\n## " },
                        label = { Text("H2") },
                        leadingIcon = { Icon(Icons.Default.Title, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { bodyText = "$bodyText\n• " },
                        label = { Text("•") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = { bodyText = "$bodyText\n> " },
                        label = { Text("“”") },
                        leadingIcon = { Icon(Icons.Default.FormatQuote, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        label = { Text(strings.attachPhotoButton) },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            if (isRecordingVoice) {
                                isRecordingVoice = false
                                voiceMemoLabel = "${recordingSeconds}s Audio Memo"
                            } else {
                                isRecordingVoice = true
                            }
                        },
                        label = {
                            Text(
                                if (isRecordingVoice) "${strings.stopVoiceRecordingButton} (${recordingSeconds}s)"
                                else strings.recordVoiceNoteButton
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isRecordingVoice) Icons.Default.StopCircle else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isRecordingVoice) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                // Attached Media Badges (Photo / Voice Memo)
                if (imageUri.isNotBlank() || voiceMemoLabel.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (imageUri.isNotBlank()) {
                            AssistChip(
                                onClick = { imageUri = "" },
                                label = { Text(strings.photoAttachedLabel) },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                        if (voiceMemoLabel.isNotBlank()) {
                            AssistChip(
                                onClick = { voiceMemoLabel = "" },
                                label = { Text("${strings.voiceNoteAttachedLabel}: $voiceMemoLabel") },
                                leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }

                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(200) },
                    placeholder = {
                        Text(
                            text = strings.noteTitlePlaceholder,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // Body Content Field
                OutlinedTextField(
                    value = bodyText,
                    onValueChange = { bodyText = it.take(18000) },
                    placeholder = {
                        Text(
                            text = strings.noteContentPlaceholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    minLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_content_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Interactive Checklist Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckBox,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.checklistTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        checklistItems.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = item.isChecked,
                                    onCheckedChange = { checked ->
                                        checklistItems[idx] = item.copy(isChecked = checked)
                                    }
                                )
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { checklistItems.removeAt(idx) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = strings.deleteNote,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newChecklistText,
                                onValueChange = { newChecklistText = it.take(120) },
                                placeholder = { Text(strings.addChecklistItemPlaceholder) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_checklist_input")
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    if (newChecklistText.isNotBlank()) {
                                        checklistItems.add(
                                            ChecklistItem(
                                                id = UUID.randomUUID().toString().take(8),
                                                text = newChecklistText.trim(),
                                                isChecked = false
                                            )
                                        )
                                        newChecklistText = ""
                                    }
                                },
                                modifier = Modifier.testTag("add_checklist_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = strings.addChecklistButton)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tags & Reminder Inputs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it.take(120) },
                        label = { Text(strings.tagsLabel) },
                        placeholder = { Text(strings.tagsPlaceholder) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = reminderLabel,
                        onValueChange = { reminderLabel = it.take(60) },
                        label = { Text(strings.reminderLabel) },
                        placeholder = { Text(strings.reminderPlaceholder) },
                        leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showSetNotePinDialog) {
        SetPinDialog(
            title = strings.setNotePinTitle,
            includeHint = true,
            initialHint = lockHint,
            onDismiss = { showSetNotePinDialog = false },
            onPinConfirmed = { newPin, newHint ->
                isLocked = true
                notePinHash = PinSecurity.hashPin(newPin)
                lockHint = newHint
                showSetNotePinDialog = false
            }
        )
    }
}
