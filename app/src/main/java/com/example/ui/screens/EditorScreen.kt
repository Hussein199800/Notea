package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.example.core.security.PinHasher
import com.example.core.security.VaultState
import com.example.data.local.AttachmentEntity
import com.example.data.local.AttachmentKind
import com.example.data.local.RevisionReason
import com.example.data.repository.DecryptResult
import com.example.ui.components.ColorPicker
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.PassphraseDialog
import com.example.ui.components.SetPinDialog
import com.example.ui.components.TextInputDialog
import com.example.ui.editor.MarkdownEngine
import com.example.ui.i18n.LocalStrings
import com.example.ui.platform.AudioPlayer
import com.example.ui.platform.AudioRecorder
import com.example.ui.platform.FileOpener
import com.example.ui.platform.LinkPreviewFetcher
import com.example.ui.theme.noteColor
import com.example.ui.viewmodel.EditorEvent
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.SessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(session: SessionViewModel, vm: EditorViewModel, folders: List<com.example.data.local.FolderEntity>, onClose: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val st by vm.state.collectAsState()
    val vaultState by session.vaultState.collectAsState()
    val scope = rememberCoroutineScope()

    var menu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<String?>(null) } // color, folder, tags, reminder, history
    var linkDialog by remember { mutableStateOf(false) }
    var templateDialog by remember { mutableStateOf(false) }
    var pinDialog by remember { mutableStateOf(false) }
    var vaultDialog by remember { mutableStateOf(false) }
    var confirmUnlock by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    var timePickerFor by remember { mutableStateOf<Long?>(null) }
    val recorder = remember { AudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableStateOf(0) }

    fun close() { recorder.cancel(); AudioPlayer.stop(); vm.flush(); onClose() }
    BackHandler { close() }

    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.flush() }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                EditorEvent.EncryptionFailed -> session.message(s.encryptionFailedNotSaved)
                EditorEvent.VaultRequired -> vaultDialog = true
                is EditorEvent.Message -> session.message(e.text)
            }
        }
    }
    LaunchedEffect(recording) { while (recording) { delay(1000); recordSeconds++ } }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        uris.forEach { vm.addAttachmentFromUri(it, AttachmentKind.IMAGE) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> uris.forEach { vm.addAttachmentFromUri(it) } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { recording = recorder.start(); recordSeconds = 0 } else session.message(s.micPermission)
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) session.message(s.notificationPermission)
    }
    fun ensureNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun toggleRecord() {
        if (recording) {
            recording = false
            recorder.stop()?.let { (f, d) -> vm.addAudio(f, d, s.record + " " + s.date(System.currentTimeMillis())) }
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recording = recorder.start(); recordSeconds = 0
        } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    val readOnly = st.trashedAt != null
    val bg = noteColor(st.colorIndex)

    Surface(Modifier.fillMaxSize(), color = bg) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            // ------------------------------------------------ top bar
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, s.back) }
                Spacer(Modifier.weight(1f))
                if (!readOnly && st.blocked == null) {
                    IconButton(onClick = vm::undo, enabled = st.canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, s.undo) }
                    IconButton(onClick = vm::redo, enabled = st.canRedo) { Icon(Icons.AutoMirrored.Rounded.Redo, s.redo) }
                    IconButton(onClick = vm::togglePin) { Icon(if (st.isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, if (st.isPinned) s.unpin else s.pin,
                        tint = if (st.isPinned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = vm::toggleFavorite) { Icon(if (st.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, s.favorite,
                        tint = if (st.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, s.more) }
                        DropdownMenu(menu, { menu = false }) {
                            MenuItem(Icons.Rounded.Palette, s.color) { sheet = "color"; menu = false }
                            MenuItem(Icons.Rounded.Folder, s.moveToFolder) { sheet = "folder"; menu = false }
                            MenuItem(Icons.Rounded.Tag, s.addTags) { sheet = "tags"; menu = false }
                            MenuItem(Icons.Rounded.Notifications, s.reminder) { sheet = "reminder"; menu = false }
                            MenuItem(if (st.isLocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, if (st.isLocked) s.unlockNote else s.lockNote) {
                                menu = false
                                if (st.isLocked) confirmUnlock = true
                                else if (vaultState != VaultState.READY) vaultDialog = true else pinDialog = true
                            }
                            MenuItem(Icons.Rounded.History, s.history) { sheet = "history"; menu = false }
                            MenuItem(Icons.Rounded.ViewQuilt, s.saveAsTemplate) { templateDialog = true; menu = false }
                            if (!st.isLocked) MenuItem(Icons.Rounded.Share, s.share) {
                                menu = false
                                val text = buildString {
                                    append(MarkdownEngine.plain(st.body.text))
                                    st.checklist.forEach { append("\n").append(if (it.checked) "☑ " else "☐ ").append(it.text) }
                                }
                                FileOpener.shareText(context, st.title, (st.title + "\n\n" + text).trim())
                            }
                            MenuItem(Icons.Rounded.Archive, if (st.isArchived) s.unarchive else s.archiveAction) { menu = false; vm.toggleArchive { onClose() } }
                            MenuItem(Icons.Rounded.DeleteOutline, s.moveToTrash) { menu = false; vm.moveToTrash { session.message(s.movedToTrash); onClose() } }
                        }
                    }
                }
            }
            if (st.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            when {
                st.loading -> Spacer(Modifier.weight(1f))
                st.blocked != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (st.blocked) {
                        DecryptResult.VaultLocked -> EmptyState(Icons.Rounded.Lock, s.vaultUnlockTitle, s.vaultUnlockBody) {
                            TextButton(onClick = { vaultDialog = true }) { Text(s.confirm) }
                        }
                        DecryptResult.ForeignLegacy -> EmptyState(Icons.Rounded.Lock, s.lockedPreview, s.foreignLegacy)
                        else -> EmptyState(Icons.Rounded.Lock, s.lockedPreview, s.decryptFailed)
                    }
                }
                else -> Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                        .widthIn(max = 820.dp)
                ) {
                    if (readOnly) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(s.readOnlyTrash, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = vm::restoreFromTrash) { Text(s.restore) }
                            }
                        }
                    }
                    BasicTextField(
                        value = st.title, onValueChange = vm::setTitle, readOnly = readOnly,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                        decorationBox = { inner ->
                            if (st.title.isEmpty()) Text(s.titleHint, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            inner()
                        }
                    )
                    MetaLine(st, folders)
                    val palette = MarkdownEngine.Palette(
                        marker = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        link = MaterialTheme.colorScheme.primary,
                        quote = MaterialTheme.colorScheme.onSurfaceVariant,
                        accent = MaterialTheme.colorScheme.secondary
                    )
                    val transformation = remember(palette) { MarkdownEngine.Transformation(palette) }
                    BasicTextField(
                        value = st.body, onValueChange = vm::setBody, readOnly = readOnly,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth()) {
                                if (st.body.text.isEmpty()) Text(s.bodyHint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                inner()
                            }
                        }
                    )
                    val url = MarkdownEngine.urlAt(st.body.text, st.body.selection.start)
                    AnimatedVisibility(url != null) {
                        AssistChip(onClick = { url?.let { FileOpener.openUrl(context, it) } }, label = { Text(url.orEmpty(), modifier = Modifier.padding(vertical = 6.dp)) },
                            leadingIcon = { Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp)) })
                    }
                    if (st.checklist.isNotEmpty()) ChecklistSection(vm, st.checklist, readOnly)
                    if (st.attachments.isNotEmpty()) AttachmentsSection(vm, st.attachments, readOnly)
                    if (recording) {
                        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Mic, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text("${s.recording} ${"%d:%02d".format(recordSeconds / 60, recordSeconds % 60)}", Modifier.weight(1f))
                                TextButton(onClick = { toggleRecord() }) { Text(s.stopRecording) }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    st.updatedAt?.let {
                        Text(s.edited(s.relative(it)) + " • " + s.savedLocally, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(48.dp))
                }
            }

            // ------------------------------------------------ formatting toolbar
            if (!readOnly && st.blocked == null && !st.loading) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f))
                        .horizontalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Tool(Icons.Rounded.CheckBox, s.checklist) { vm.convertToChecklist() }
                    Tool(Icons.Rounded.FormatBold, s.bold) { vm.applyBody { MarkdownEngine.toggleInline(it, "**") } }
                    Tool(Icons.Rounded.FormatItalic, s.italic) { vm.applyBody { MarkdownEngine.toggleInline(it, "*") } }
                    Tool(Icons.Rounded.FormatUnderlined, s.underline) { vm.applyBody { MarkdownEngine.toggleInline(it, "++") } }
                    Tool(Icons.Rounded.Title, s.heading) { vm.applyBody { MarkdownEngine.toggleLinePrefix(it, "## ") } }
                    Tool(Icons.AutoMirrored.Rounded.FormatListBulleted, s.bullets) { vm.applyBody { MarkdownEngine.toggleLinePrefix(it, "- ") } }
                    Tool(Icons.Rounded.FormatListNumbered, s.numbered) { vm.applyBody { MarkdownEngine.toggleLinePrefix(it, "1. ") } }
                    Tool(Icons.Rounded.FormatQuote, s.quote) { vm.applyBody { MarkdownEngine.toggleLinePrefix(it, "> ") } }
                    Tool(Icons.Rounded.Link, s.link) { linkDialog = true }
                    Box(Modifier.height(24.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Tool(Icons.Rounded.Image, s.image) { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    Tool(Icons.Rounded.AttachFile, s.file) { pickFile.launch(arrayOf("*/*")) }
                    Tool(if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, if (recording) s.stopRecording else s.record) { toggleRecord() }
                    Tool(Icons.Rounded.Palette, s.color) { sheet = "color" }
                }
            }
        }
    }

    // ---------------------------------------------------------------- sheets & dialogs
    when (sheet) {
        "color" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
                Text(s.color, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(12.dp))
                ColorPicker(st.colorIndex) { vm.setColor(it) }
            }
        }
        "folder" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            FlowRow(Modifier.padding(20.dp).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(st.folderId == null, onClick = { vm.setFolder(null); sheet = null }, label = { Text(s.noFolder) })
                folders.forEach { f -> FilterChip(st.folderId == f.id, onClick = { vm.setFolder(f.id); sheet = null }, label = { Text(f.name) }) }
            }
        }
        "tags" -> ModalBottomSheet(onDismissRequest = { sheet = null }) { TagEditor(st.tags, vm::setTags) }
        "reminder" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(s.setReminder, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp, 8.dp))
                fun at(c: Calendar) { ensureNotifications(); vm.setReminder(c.timeInMillis); sheet = null }
                SheetRow(Icons.Rounded.Notifications, s.laterToday) { at(Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 3) }) }
                SheetRow(Icons.Rounded.Notifications, s.tomorrowMorning) { at(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0) }) }
                SheetRow(Icons.Rounded.Notifications, s.nextWeek) { at(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0) }) }
                SheetRow(Icons.Rounded.Notifications, s.customTime) { datePicker = true; sheet = null }
                if (st.reminderAt != null) SheetRow(Icons.Rounded.Close, s.removeReminder) { vm.setReminder(null); sheet = null }
            }
        }
        "history" -> HistorySheet(vm) { sheet = null }
    }
    if (datePicker) {
        val dp = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(onDismissRequest = { datePicker = false },
            confirmButton = { TextButton(onClick = { timePickerFor = dp.selectedDateMillis; datePicker = false }) { Text(s.next) } },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text(s.cancel) } }) { DatePicker(dp) }
    }
    timePickerFor?.let { day ->
        val tp = rememberTimePickerState(9, 0)
        AlertDialog(onDismissRequest = { timePickerFor = null }, title = { Text(s.pickTime) }, text = { TimePicker(tp) },
            confirmButton = { TextButton(onClick = {
                val c = Calendar.getInstance().apply {
                    val d = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = day }
                    set(d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH), tp.hour, tp.minute, 0)
                }
                ensureNotifications(); vm.setReminder(c.timeInMillis); timePickerFor = null
            }) { Text(s.save) } },
            dismissButton = { TextButton(onClick = { timePickerFor = null }) { Text(s.cancel) } })
    }
    if (linkDialog) LinkDialog(onDismiss = { linkDialog = false }, onInsert = { u, t -> vm.applyBody { MarkdownEngine.insertLink(it, u, t) } },
        onCard = { u ->
            scope.launch {
                val p = withContext(Dispatchers.IO) { LinkPreviewFetcher.fetch(u) }
                vm.addLinkCard(u, p?.title.orEmpty(), p?.description.orEmpty())
            }
        })
    if (templateDialog) TextInputDialog(s.saveAsTemplate, s.templateName, st.title, s.save, { vm.saveAsTemplate(it); session.message(s.templateSaved) }) { templateDialog = false }
    if (pinDialog) SetPinDialog(s.lockNote, withHint = true, onSet = { pin, hint ->
        scope.launch { val h = withContext(Dispatchers.Default) { PinHasher.hash(pin) }; vm.lock(h, hint); session.markOpened(vm.noteId) }
    }) { pinDialog = false }
    if (confirmUnlock) ConfirmDialog(s.unlockNote, s.unlockNote + "?", s.confirm, false, { vm.unlockPermanently() }) { confirmUnlock = false }
    if (vaultDialog) {
        val create = vaultState == VaultState.NOT_SET_UP
        PassphraseDialog(
            create = create, title = if (create) s.vaultSetupTitle else s.vaultUnlockTitle, body = if (create) s.vaultSetupBody else s.vaultUnlockBody,
            submit = { if (create) session.setUpVault(it) else session.unlockVault(it) },
            onSuccess = { if (st.blocked != null) session.message(s.vaultReady) else if (!st.isLocked) pinDialog = true },
            onDismiss = { vaultDialog = false }
        )
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)

@Composable
private fun Tool(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaLine(st: com.example.ui.viewmodel.EditorState, folders: List<com.example.data.local.FolderEntity>) {
    val s = LocalStrings.current
    val folder = folders.firstOrNull { it.id == st.folderId }
    if (folder == null && st.tags.isEmpty() && st.reminderAt == null && !st.isLocked) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (st.isLocked) com.example.ui.components.MetaPill(Icons.Rounded.Lock, s.typeLocked)
        folder?.let { com.example.ui.components.MetaPill(Icons.Rounded.Folder, it.name) }
        st.reminderAt?.let { com.example.ui.components.MetaPill(Icons.Rounded.Notifications, s.date(it)) }
        st.tags.forEach { com.example.ui.components.MetaPill(null, "#$it") }
    }
}

@Composable
private fun ChecklistSection(vm: EditorViewModel, items: List<com.example.data.repository.ChecklistDraft>, readOnly: Boolean) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(s.completedItems(items.count { it.checked }, items.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (items.filter { !it.checked } + items.filter { it.checked }).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.toggleChecklistItem(item.id) }, enabled = !readOnly) {
                    Icon(if (item.checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = MaterialTheme.colorScheme.primary)
                }
                BasicTextField(
                    value = item.text, onValueChange = { vm.updateChecklistItem(item.id, it) }, readOnly = readOnly,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (item.checked) 0.5f else 1f),
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { vm.addChecklistItem() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> if (item.text.isEmpty()) Text(s.itemHint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)); inner() }
                )
                if (!readOnly) IconButton(onClick = { vm.removeChecklistItem(item.id) }) { Icon(Icons.Rounded.Close, s.delete, Modifier.size(18.dp)) }
            }
        }
        if (!readOnly) TextButton(onClick = { vm.addChecklistItem() }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.addItem)
        }
    }
}

@Composable
private fun AttachmentsSection(vm: EditorViewModel, items: List<AttachmentEntity>, readOnly: Boolean) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf<AttachmentEntity?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.attachments, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val images = items.filter { it.kind == AttachmentKind.IMAGE }
        if (images.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                images.forEach { a ->
                    val file by produceState<java.io.File?>(null, a.id) { value = withContext(Dispatchers.IO) { vm.viewableFile(a) } }
                    Box {
                        AsyncImage(file, a.displayName, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(140.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { file?.let { if (!FileOpener.open(context, it, a.mimeType)) android.widget.Toast.makeText(context, s.noAppToOpen, android.widget.Toast.LENGTH_SHORT).show() } })
                        if (!readOnly) IconButton(onClick = { confirmRemove = a }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)) { Icon(Icons.Rounded.Close, s.removeAttachment, Modifier.padding(4.dp).size(16.dp)) }
                        }
                    }
                }
            }
        }
        items.filter { it.kind != AttachmentKind.IMAGE }.forEach { a ->
            var playing by remember(a.id) { mutableStateOf(false) }
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.clickable {
                    when (a.kind) {
                        AttachmentKind.LINK -> FileOpener.openUrl(context, a.url)
                        AttachmentKind.AUDIO -> vm.viewableFile(a)?.let { f -> AudioPlayer.toggle(f) { playing = it } }
                        else -> { val f = vm.viewableFile(a); if (f == null || !FileOpener.open(context, f, a.mimeType)) android.widget.Toast.makeText(context, s.noAppToOpen, android.widget.Toast.LENGTH_SHORT).show() }
                    }
                }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when (a.kind) {
                        AttachmentKind.AUDIO -> if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow
                        AttachmentKind.PDF -> Icons.Rounded.PictureAsPdf
                        AttachmentKind.LINK -> Icons.Rounded.Link
                        else -> Icons.Rounded.Description
                    }, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (a.kind == AttachmentKind.LINK) a.linkTitle.ifBlank { a.url } else a.displayName, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                        val sub = when (a.kind) {
                            AttachmentKind.LINK -> a.linkDescription.ifBlank { a.url }
                            AttachmentKind.AUDIO -> "%d:%02d".format(a.durationMs / 60000, (a.durationMs / 1000) % 60)
                            else -> android.text.format.Formatter.formatShortFileSize(context, a.sizeBytes)
                        }
                        com.example.ui.components.ExpandableText(sub, collapsedLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!readOnly) IconButton(onClick = { confirmRemove = a }) { Icon(Icons.Rounded.Close, s.removeAttachment, Modifier.size(18.dp)) }
                }
            }
        }
    }
    confirmRemove?.let { a -> ConfirmDialog(s.removeAttachment, a.displayName, s.delete, true, { vm.removeAttachment(a.id) }) { confirmRemove = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditor(tags: List<String>, onChange: (List<String>) -> Unit) {
    val s = LocalStrings.current
    var input by remember { mutableStateOf("") }
    Column(Modifier.padding(20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(s.addTags, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { t -> InputChip(true, onClick = { onChange(tags - t) }, label = { Text("#$t") }, trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(16.dp)) }) }
        }
        OutlinedTextField(input, { v ->
            if (v.endsWith(",") || v.endsWith("،") || v.endsWith(" ")) { val t = v.dropLast(1).trim().removePrefix("#"); if (t.isNotBlank()) onChange(tags + t); input = "" } else input = v
        }, label = { Text(s.tagHint) }, maxLines = 3, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { val t = input.trim().removePrefix("#"); if (t.isNotBlank()) onChange(tags + t); input = "" }))
    }
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onInsert: (String, String) -> Unit, onCard: (String) -> Unit) {
    val s = LocalStrings.current
    var url by remember { mutableStateOf("https://") }
    var label by remember { mutableStateOf("") }
    val valid = Regex("^https?://[^\\s]+\\.[^\\s]+").matches(url.trim())
    AlertDialog(onDismissRequest = onDismiss, title = { Text(s.addLink) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(url, { url = it.replace("\n", "") }, label = { Text(s.linkUrl) }, maxLines = 4, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(label, { label = it.replace("\n", " ") }, label = { Text(s.linkText) }, maxLines = 3, modifier = Modifier.fillMaxWidth())
            Text(s.linkPreviewNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = {
        Column(horizontalAlignment = Alignment.End) {
            TextButton(enabled = valid, onClick = { onInsert(url.trim(), label); onDismiss() }) { Text(s.insertInText) }
            TextButton(enabled = valid, onClick = { onCard(url.trim()); onDismiss() }) { Text(s.addAsCard) }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(vm: EditorViewModel, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val revisions by vm.revisions.collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<com.example.data.local.RevisionEntity?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (selected == null) {
            LazyColumn(Modifier.padding(bottom = 24.dp)) {
                item { Text(s.history, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp, 8.dp)) }
                if (revisions.isEmpty()) item { Text(s.noHistory, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(revisions, key = { it.id }) { r ->
                    SheetRow(Icons.Rounded.History, s.date(r.createdAt) + " • " + when (r.reason) {
                        RevisionReason.CONFLICT -> s.reasonConflict; RevisionReason.BEFORE_RESTORE -> s.reasonBeforeRestore
                        RevisionReason.IMPORT_MERGE -> s.reasonMerge; else -> s.reasonAuto
                    }) { selected = r }
                }
            }
        } else {
            val r = selected!!
            val dec = remember(r.id) { vm.decryptRevision(r) }
            Column(Modifier.padding(20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.date(r.createdAt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(r.title.ifBlank { s.untitled }, style = MaterialTheme.typography.titleLarge)
                when (dec) {
                    is DecryptResult.Plain -> {
                        com.example.ui.components.ExpandableText(MarkdownEngine.plain(dec.body), collapsedLines = 14, style = MaterialTheme.typography.bodyMedium)
                        dec.checklist.forEach { Text((if (it.checked) "☑ " else "☐ ") + it.text, style = MaterialTheme.typography.bodySmall) }
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
}
