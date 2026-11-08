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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.EmojiEmotions
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
fun EditorScreen(session: SessionViewModel, vm: EditorViewModel, folders: List<com.example.data.local.FolderEntity>, startScan: Boolean = false,
                 onOpenNote: (String) -> Unit = {}, onClose: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val st by vm.state.collectAsState()
    val vaultState by session.vaultState.collectAsState()
    val scope = rememberCoroutineScope()

    var menu by remember { mutableStateOf(false) }
    var compareTarget by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) } // color, folder, tags, reminder, history
    var linkDialog by remember { mutableStateOf(false) }
    var iconDialog by remember { mutableStateOf(false) }
    var parentPicker by remember { mutableStateOf(false) }
    var linkPicker by remember { mutableStateOf(false) }
    var manualSlash by remember { mutableStateOf(false) }
    val graph by vm.graph.collectAsState()
    val trigger by vm.trigger.collectAsState()
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
    // Document scanner (edge detection + perspective fix, on device via Google Play services);
    // falls back to the system camera on devices without Play services.
    val cameraUri = remember { mutableStateOf<android.net.Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri.value?.let { vm.addAttachmentFromUri(it, AttachmentKind.IMAGE) }
    }
    var scanFallback by remember { mutableStateOf(false) }
    var installProgress by remember { mutableStateOf<Int?>(null) }
    // The phone's own camera app (Samsung/Xiaomi/Huawei have a «Document» mode there); when the user
    // comes back, the gallery picker opens on the newest photos so the scan can be added in one tap.
    val phoneCamera = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        runCatching { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val pages = if (r.resultCode == android.app.Activity.RESULT_OK)
            com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pages.orEmpty() else emptyList()
        if (pages.isEmpty() && r.resultCode != android.app.Activity.RESULT_CANCELED) scanFallback = true // Google's "Something went wrong" screen
        pages.forEach { vm.addAttachmentFromUri(it.imageUri, AttachmentKind.IMAGE) }
    }
    fun launchCamera() {
        val f = java.io.File(context.cacheDir, "camera/scan_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        // Never let a camera hand-off problem close the app: show a message instead.
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", f)
            cameraUri.value = uri
            takePicture.launch(uri)
        }.onFailure { session.message(s.noAppToOpen) }
    }
    fun startScan() {
        val activity = context.findActivity()
        if (activity == null) { launchCamera(); return }
        val options = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true).setPageLimit(20)
            .setResultFormats(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
        val client = runCatching { com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(options) }.getOrNull()
        if (client == null) { scanFallback = true; return }
        scope.launch {
            // Only open Google's scanner when its module is really installed; otherwise go straight to the camera.
            if (!com.example.ui.platform.GmsModules.ready(context, client)) { scanFallback = true; return@launch }
            client.getStartScanIntent(activity)
                .addOnSuccessListener { scanLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { launchCamera() }
        }
    }
    LaunchedEffect(startScan, st.loading) { if (startScan && !st.loading) startScan() }
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
                            if (vm.assistantAvailable) MenuItem(Icons.Rounded.AutoAwesome, s.assistant, com.example.ui.theme.Concept.ASSISTANT) { sheet = "assistant"; menu = false }
                            MenuItem(Icons.Rounded.EmojiEmotions, s.noteIcon, com.example.ui.theme.Concept.SECURITY) { iconDialog = true; menu = false }
                            MenuItem(Icons.Rounded.DriveFileMove, s.moveUnderNote, com.example.ui.theme.Concept.FOLDERS) { parentPicker = true; menu = false }
                            if (graph.parent != null) MenuItem(Icons.Rounded.VerticalAlignTop, s.moveToTop, com.example.ui.theme.Concept.FOLDERS) {
                                menu = false; vm.moveUnder(null) { session.message(s.movedUnder) }
                            }
                            MenuItem(Icons.Rounded.History, s.history, com.example.ui.theme.Concept.REMINDERS) { sheet = "history"; menu = false }
                            MenuItem(Icons.Rounded.ViewQuilt, s.saveAsTemplate) { templateDialog = true; menu = false }
                            if (!st.isLocked) MenuItem(Icons.Rounded.Share, s.share) {
                                menu = false
                                val text = buildString {
                                    append(MarkdownEngine.plain(st.bodyText))
                                    st.checklist.forEach { append("\n").append(if (it.checked) "☑ " else "☐ ").append(it.text) }
                                }
                                FileOpener.shareText(context, st.title, (st.title + "\n\n" + text).trim())
                            }
                            MenuItem(Icons.Rounded.Archive, if (st.isArchived) s.unarchive else s.archiveAction, com.example.ui.theme.Concept.ARCHIVE) { menu = false; vm.toggleArchive { onClose() } }
                            MenuItem(Icons.Rounded.DeleteOutline, s.moveToTrash, com.example.ui.theme.Concept.TRASH) { menu = false; vm.moveToTrash { val id = vm.noteId; session.messageWithUndo(s.movedToTrash) { session.restoreFromTrash(id) }; onClose() } }
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
                    graph.parent?.let { p -> ParentCrumb(p) { id -> vm.flush(); onOpenNote(id) } }
                    if (graph.icon.isNotBlank()) Text(graph.icon, style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(top = 8.dp).clickable(enabled = !readOnly) { iconDialog = true })
                    else if (!readOnly) TextButton(onClick = { iconDialog = true }, modifier = Modifier.heightIn(min = 44.dp)) {
                        Icon(Icons.Rounded.EmojiEmotions, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.addIcon, style = MaterialTheme.typography.labelLarge)
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
                    BlocksContent(vm, st, readOnly)
                    if (!st.isLocked) NoteGraphSection(graph, readOnly, onOpen = { id -> vm.flush(); onOpenNote(id) },
                        onNewChild = { vm.createChild { id -> onOpenNote(id) } },
                        onCreateLinked = { t -> vm.createLinked(t) })
                    LaunchedEffect(st.loading, st.persisted) { if (!st.loading && st.persisted) vm.refreshRelated() }
                    RelatedNotesSection(vm, onOpen = { id -> vm.flush(); onOpenNote(id) },
                        onCompare = if (com.example.ui.screens.assistantOn(vm)) { id -> if (vm.needsConsent()) compareTarget = id else { vm.compareWith(id, false); sheet = "assistant" } } else null)
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

            // ------------------------------------------------ «/» menu and note pickers (above the keyboard)
            val tr = trigger
            val trBlock = tr?.let { t -> st.blocks.firstOrNull { it.id == t.blockId } as? com.example.ui.viewmodel.EditorBlock.Text }
            fun triggerQuery(skip: Int): String = trBlock?.value?.let { v ->
                val from = (tr!!.at + skip).coerceAtMost(v.text.length)
                v.text.substring(from, v.selection.start.coerceIn(from, v.text.length))
            }.orEmpty()
            if (!readOnly && (manualSlash || tr?.kind == com.example.ui.viewmodel.EditorViewModel.Trigger.Kind.SLASH)) {
                SlashPanel(if (manualSlash) "" else triggerQuery(1), onPick = { item ->
                    manualSlash = false
                    when (item.key) {
                        "h" -> vm.slashLinePrefix("## ")
                        "ul" -> vm.slashLinePrefix("- ")
                        "ol" -> vm.slashLinePrefix("1. ")
                        "quote" -> vm.slashLinePrefix("> ")
                        "todo" -> vm.slashChecklist()
                        "toggle" -> vm.insertSpecial(com.example.data.local.BlockType.TOGGLE, fromSlash = true)
                        "callout" -> vm.insertSpecial(com.example.data.local.BlockType.CALLOUT, fromSlash = true)
                        "sub" -> { vm.clearTrigger(); vm.createChild { id -> onOpenNote(id) } }
                        "link" -> { vm.clearTrigger(); linkPicker = true }
                        "image" -> { vm.clearTrigger(); pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        "scan" -> { vm.clearTrigger(); startScan() }
                        "file" -> { vm.clearTrigger(); pickFile.launch(arrayOf("*/*")) }
                    }
                }, onDismiss = { manualSlash = false; vm.dismissTrigger() })
            } else if (!readOnly && (linkPicker || tr?.kind == com.example.ui.viewmodel.EditorViewModel.Trigger.Kind.LINK)) {
                NotePickerPanel(s.linkToNote, graph.all, if (linkPicker) "" else triggerQuery(2),
                    onPick = { n -> linkPicker = false; vm.insertNoteLink(n.title.ifBlank { s.untitled }) },
                    onCreate = { t -> linkPicker = false; vm.insertNoteLink(t); vm.createLinked(t) },
                    onDismiss = { linkPicker = false; vm.dismissTrigger() })
            } else if (parentPicker) {
                NotePickerPanel(s.pickParent, graph.all.filter { it.id !in graph.children.map { c -> c.id } }, "",
                    onPick = { n -> parentPicker = false; vm.moveUnder(n.id) { ok -> session.message(if (ok) s.movedUnder else s.cantMoveUnder) } },
                    onCreate = null, onDismiss = { parentPicker = false })
            }

            // ------------------------------------------------ formatting toolbar
            if (!readOnly && st.blocked == null && !st.loading) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f))
                        .horizontalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Tool(Icons.Rounded.AddBox, s.insertBlock) { manualSlash = !manualSlash }
                    Tool(Icons.Rounded.CheckBox, s.checklist) { vm.insertChecklist() }
                    Tool(Icons.Rounded.FormatBold, s.bold) { vm.applyFormat { MarkdownEngine.toggleInline(it, "**") } }
                    Tool(Icons.Rounded.FormatItalic, s.italic) { vm.applyFormat { MarkdownEngine.toggleInline(it, "*") } }
                    Tool(Icons.Rounded.FormatUnderlined, s.underline) { vm.applyFormat { MarkdownEngine.toggleInline(it, "++") } }
                    Tool(Icons.Rounded.Title, s.heading) { vm.applyFormat { MarkdownEngine.toggleLinePrefix(it, "## ") } }
                    Tool(Icons.AutoMirrored.Rounded.FormatListBulleted, s.bullets) { vm.applyFormat { MarkdownEngine.toggleLinePrefix(it, "- ") } }
                    Tool(Icons.Rounded.FormatListNumbered, s.numbered) { vm.applyFormat { MarkdownEngine.toggleLinePrefix(it, "1. ") } }
                    Tool(Icons.Rounded.FormatQuote, s.quote) { vm.applyFormat { MarkdownEngine.toggleLinePrefix(it, "> ") } }
                    Tool(Icons.Rounded.Link, s.link) { linkDialog = true }
                    Box(Modifier.height(24.dp).width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Tool(Icons.Rounded.Image, s.image) { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    Tool(Icons.Rounded.DocumentScanner, s.scanDocument) { startScan() }
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
        "history" -> VersionHistorySheet(vm) { sheet = null }
        "assistant" -> AssistantSheet(vm) { sheet = null }
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
    if (linkDialog) LinkDialog(onDismiss = { linkDialog = false }, onInsert = { u, t -> vm.applyFormat { MarkdownEngine.insertLink(it, u, t) } },
        onCard = { u ->
            scope.launch {
                val p = withContext(Dispatchers.IO) { LinkPreviewFetcher.fetch(u) }
                vm.addLinkCard(u, p?.title.orEmpty(), p?.description.orEmpty())
            }
        })
    if (scanFallback) ScanChooser(
        onInstallGoogle = { scope.launch {
            installProgress = 0
            val client = runCatching { com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(
                com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder().build()) }.getOrNull()
            val ok = client != null && com.example.ui.platform.installGmsModule(context, client) { p: Int -> installProgress = p }
            installProgress = null
            if (ok) { scanFallback = false; startScan() } else session.message(s.googleScannerUnavailable)
        } },
        installProgress = installProgress,
        onPhoneCamera = { scanFallback = false; runCatching { phoneCamera.launch(android.content.Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }.onFailure { launchCamera() } },
        onQuickCamera = { scanFallback = false; launchCamera() },
        onGallery = { scanFallback = false; pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onDismiss = { if (installProgress == null) scanFallback = false }
    )
    compareTarget?.let { id ->
        ConsentDialog(s.consentCompareBody, onSend = { remember -> vm.compareWith(id, remember); sheet = "assistant" }) { compareTarget = null }
    }
    if (iconDialog) IconPickerDialog(graph.icon, onPick = { vm.setIcon(it) }) { iconDialog = false }
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
private fun MenuItem(icon: ImageVector, label: String, concept: com.example.ui.theme.Concept? = null, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(label, color = if (concept == com.example.ui.theme.Concept.TRASH) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified) },
        leadingIcon = { Icon(icon, null, tint = concept?.g2 ?: MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = onClick)

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

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c = this
    while (c is android.content.ContextWrapper) { if (c is android.app.Activity) return c; c = c.baseContext }
    return null
}

/** Compare is offered only when the assistant is set up and the note is not locked. */
fun assistantOn(vm: EditorViewModel) = vm.assistantAvailable

/** Scanning options when Google's scanner isn't available (or failed): explained, never a dead end. */
@Composable
private fun ScanChooser(onInstallGoogle: () -> Unit, installProgress: Int?, onPhoneCamera: () -> Unit, onQuickCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DocumentScanner, null) },
        title = { Text(s.scanChooseTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                com.example.ui.components.ExpandableText(s.scanChooseBody, collapsedLines = 4, style = MaterialTheme.typography.bodyMedium)
                if (installProgress != null) {
                    Text(s.installingScanner(installProgress), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.LinearProgressIndicator(progress = { installProgress / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    ScanOption(s.scanOptGoogle, s.scanOptGoogleDesc, onInstallGoogle)
                    ScanOption(s.scanOptPhoneCamera, s.scanOptPhoneCameraDesc, onPhoneCamera)
                    ScanOption(s.scanOptQuick, s.scanOptQuickDesc, onQuickCamera)
                    ScanOption(s.scanOptGallery, s.scanOptGalleryDesc, onGallery)
                }
            }
        },
        confirmButton = {},
        dismissButton = { if (installProgress == null) TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}

@Composable
private fun ScanOption(title: String, desc: String, onClick: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
