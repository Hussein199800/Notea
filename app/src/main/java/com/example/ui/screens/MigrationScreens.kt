package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhonelinkSetup
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.ai.AiError
import com.example.core.ai.GeminiClient
import com.example.data.transfer.DeviceTransfer
import com.example.ui.components.ExpandableText
import com.example.ui.components.PreferenceGroup
import com.example.ui.components.PreferenceRow
import com.example.ui.components.SecretField
import com.example.ui.components.SectionLabel
import com.example.ui.components.SwitchRow
import com.example.ui.i18n.LocalStrings
import com.example.ui.platform.FileOpener
import com.example.ui.viewmodel.DataViewModel
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.SessionViewModel
import com.example.ui.viewmodel.TransferState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Migration center (also shown once on first run when the account has no notes). */
@Composable
fun WelcomeScreen(session: SessionViewModel, firstRun: Boolean, onDone: () -> Unit) {
    val s = LocalStrings.current
    PageScaffold(if (firstRun) s.welcomeTitle else s.migrationCenter, onBack = onDone) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (firstRun) Box(
                    Modifier.size(96.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(com.example.ui.theme.NoteaPalette.Emerald700, com.example.ui.theme.NoteaPalette.Emerald900))),
                    contentAlignment = Alignment.Center
                ) { NoteaMark(Modifier.size(72.dp)) }
                Spacer(Modifier.size(16.dp))
                ExpandableText(s.welcomeBody, collapsedLines = 5, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceCard(Icons.Rounded.PhonelinkSetup, s.fromOtherPhone, s.fromOtherPhoneDesc) { session.markOnboarded(); session.navigate(Screen.Transfer) }
                ChoiceCard(Icons.Rounded.Download, s.fromApps, s.fromAppsDesc) { session.markOnboarded(); session.navigate(Screen.ImportCenter) }
                ChoiceCard(Icons.Rounded.Backup, s.fromBackupFile, s.fromBackupFileDesc) { session.markOnboarded(); session.navigate(Screen.Backup) }
                if (firstRun) TextButton(onClick = { session.markOnboarded(); onDone() }, modifier = Modifier.fillMaxWidth()) { Text(s.startFresh) }
            }
        }
    }
}

@Composable
private fun ChoiceCard(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(10.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Phone-to-phone transfer: QR + code on the old phone, scan or type on the new one. */
@Composable
fun TransferScreen(session: SessionViewModel, data: DataViewModel) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val state by data.transfer.collectAsState()
    val task by data.task.collectAsState()
    var mode by remember { mutableStateOf<Boolean?>(null) } // true = send, false = receive
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current
    DisposableEffect(state) {
        view.keepScreenOn = state is TransferState.Ready || state is TransferState.Progress
        onDispose { view.keepScreenOn = false }
    }
    fun goPreview() = session.navigate(Screen.ImportPreview)
    fun connect(offer: DeviceTransfer.Offer?) {
        if (offer == null) { error = s.invalidTransferCode; return }
        error = null
        data.receive(offer, s.analyzing) { goPreview() }
    }
    val scope = rememberCoroutineScope()
    val qrFile = remember { java.io.File(context.cacheDir, "camera/qr.jpg") }
    val qrCapture = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch {
            val text = withContext(Dispatchers.Default) { com.example.ui.platform.QrDecoder.decode(qrFile) }
            val offer = text?.let { DeviceTransfer.parse(it) }
            if (offer == null) error = s.qrNotFound else connect(offer)
        }
    }
    fun qrPhoto() {
        qrFile.parentFile?.mkdirs()
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", qrFile)
            qrCapture.launch(uri)
        }.onFailure { error = s.scannerUnavailable }
    }

    PageScaffold(s.transferTitle, onBack = { data.cancelTransfer(); session.back() }) {
        item { ExpandableText(s.transferIntro, Modifier.padding(horizontal = 20.dp), collapsedLines = 4, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (mode) {
                    null -> {
                        ChoiceCard(Icons.Rounded.Send, s.sendFromThis, s.fromOtherPhoneDesc) { mode = true; data.startSending() }
                        ChoiceCard(Icons.Rounded.Smartphone, s.receiveOnThis, s.fromOtherPhoneDesc) { mode = false }
                    }
                    true -> when (val st = state) {
                        TransferState.Preparing, TransferState.Idle -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text(s.preparingTransfer)
                        }
                        is TransferState.Ready -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExpandableText(s.scanOnNewPhone, collapsedLines = 3)
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                                Image(st.qr.asImageBitmap(), null, Modifier.padding(12.dp).size(240.dp))
                            }
                            // Address and code are always LTR (numbers/latin), even in Arabic UI.
                            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(st.offer.address, style = MaterialTheme.typography.titleLarge)
                                    Text(st.offer.prettyCode, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text(s.waitingForPhone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                        is TransferState.Progress -> ProgressBlock(if (st.sending) s.sending else s.receiving, st.done, st.total)
                        TransferState.Sent -> Text(s.transferDone, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        TransferState.NoNetwork -> ErrorBlock(s.noWifi) { data.startSending() }
                        TransferState.Failed -> ErrorBlock(s.transferFailed) { data.startSending() }
                    }
                    false -> {
                        when (val st = state) {
                            is TransferState.Progress -> ProgressBlock(s.receiving, st.done, st.total)
                            TransferState.Failed -> ExpandableText(s.transferFailed, collapsedLines = 3, color = MaterialTheme.colorScheme.error)
                            else -> Unit
                        }
                        Button(onClick = {
                            val scanner = runCatching { com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context) }.getOrNull()
                            scope.launch {
                                if (scanner != null && com.example.ui.platform.GmsModules.ready(context, scanner)) {
                                    scanner.startScan()
                                        .addOnSuccessListener { b -> connect(b.rawValue?.let { DeviceTransfer.parse(it) }) }
                                        .addOnFailureListener { qrPhoto() }
                                } else qrPhoto() // no Google scanner: photo + on-device decoding
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text(s.scanQr)
                        }
                        Text(s.enterManually, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(address, { address = it.replace("\n", "") }, label = { Text(s.transferAddress) }, placeholder = { Text("192.168.1.5:40123") },
                                    maxLines = 2, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(code, { code = it.uppercase().replace("\n", "") }, label = { Text(s.transferCode) }, placeholder = { Text("ABCD-EFGH-JKLM") },
                                    maxLines = 2, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        FilledTonalButton(onClick = { connect(DeviceTransfer.parseManual(address, code)) }, modifier = Modifier.fillMaxWidth()) { Text(s.connect) }
                        error?.let { ExpandableText(it, collapsedLines = 3, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    TaskOverlay(task, data) { goPreview() }
}

@Composable
private fun ProgressBlock(label: String, done: Long, total: Long) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { if (total > 0) done.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth())
        Text(android.text.format.Formatter.formatShortFileSize(context, done) + " / " + android.text.format.Formatter.formatShortFileSize(context, total),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorBlock(message: String, retry: () -> Unit) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpandableText(message, collapsedLines = 4, color = MaterialTheme.colorScheme.error)
        FilledTonalButton(onClick = retry) { Text(s.retry) }
    }
}

/** Assistant settings: enable, 3-step key wizard, test, privacy notes. */
@Composable
fun AssistantSettingsScreen(session: SessionViewModel) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val keys = session.container.aiKeys
    var enabled by remember { mutableStateOf(keys.enabled) }
    var hasKey by remember { mutableStateOf(keys.hasKey()) }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var askEveryTime by remember { mutableStateOf(!keys.rememberConsent) }

    PageScaffold(s.assistant, onBack = { session.back() }) {
        item {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(10.dp))
                ExpandableText(s.assistantIntro, collapsedLines = 5, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionLabel(s.apiKey)
            PreferenceGroup {
                if (hasKey) {
                    PreferenceRow(Icons.Rounded.Key, s.keySaved, keys.model?.removePrefix("models/")?.let { s.modelInUse(it) })
                    PreferenceRow(Icons.Rounded.Key, s.removeKey, null, onClick = { keys.clear(); hasKey = false; enabled = false; status = null })
                } else Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(s.keyStep1, style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = { FileOpener.openUrl(context, "https://aistudio.google.com/apikey") }) {
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.getFreeKey)
                    }
                    Text(s.keyStep2, style = MaterialTheme.typography.bodyMedium)
                    Text(s.keyStep3, style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                        SecretField(key, { key = it.trim() }, s.apiKey)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { clipboard.getText()?.text?.trim()?.let { key = it } }) {
                            Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.paste)
                        }
                        Button(enabled = key.length >= 20 && !busy, onClick = {
                            busy = true; status = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { runCatching { GeminiClient().pickModel(key) } }
                                busy = false
                                result.onSuccess { model ->
                                    keys.setKey(key); keys.model = model; keys.enabled = true
                                    hasKey = true; enabled = true; key = ""; status = s.keyWorks
                                }.onFailure { e -> status = if (e is AiError) s.aiError(e) else s.errGeneric }
                            }
                        }) { Text(if (busy) s.working else s.testAndSave) }
                    }
                }
                status?.let { ExpandableText(it, Modifier.padding(horizontal = 18.dp, vertical = 8.dp), collapsedLines = 3,
                    color = if (it == s.keyWorks) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            }
        }
        item {
            SectionLabel(s.security)
            PreferenceGroup {
                SwitchRow(Icons.Rounded.AutoAwesome, s.enableAssistant, s.assistantDesc, enabled && hasKey) { on ->
                    if (hasKey) { keys.enabled = on; enabled = on }
                }
                SwitchRow(Icons.Rounded.Key, s.askBeforeSending, null, askEveryTime) { on -> keys.rememberConsent = !on; askEveryTime = on }
                ExpandableText(s.freeTierNote, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), collapsedLines = 3,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Suppress("unused") private val keepBox: @Composable () -> Unit = { Box(Modifier.widthIn(max = 1.dp)) {} }
