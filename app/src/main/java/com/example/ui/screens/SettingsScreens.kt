package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.rounded.FormatSize
import com.example.ui.components.SavedTick
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.prefs.AutoLockTimeout
import com.example.core.prefs.LanguagePreference
import com.example.core.prefs.ThemeMode
import com.example.core.security.VaultState
import com.example.data.backup.BackupException
import com.example.data.importer.DuplicateKind
import com.example.data.importer.ImportAction
import com.example.data.importer.ImportSource
import com.example.data.importer.ImportWarning
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.ExpandableText
import com.example.ui.components.PassphraseDialog
import com.example.ui.components.PreferenceGroup
import com.example.ui.components.PreferenceRow
import com.example.ui.components.SecretField
import com.example.ui.components.SectionLabel
import com.example.ui.components.SetPinDialog
import com.example.ui.components.SwitchRow
import com.example.ui.i18n.LocalStrings
import com.example.ui.viewmodel.DataTask
import com.example.ui.viewmodel.DataViewModel
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.SessionViewModel
import kotlinx.coroutines.launch

@Composable
fun PageScaffold(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val s = LocalStrings.current
    BackHandler { onBack() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.fillMaxSize().widthIn(max = 720.dp).statusBarsPadding().navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)) {
                item {
                    Column(Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, s.back) }
                        Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 12.dp, top = 12.dp))
                    }
                }
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(session: SessionViewModel, email: String?, onSignOut: () -> Unit, onSignIn: (() -> Unit)?) {
    val s = LocalStrings.current
    val prefs by session.prefs.collectAsState()
    val vault by session.vaultState.collectAsState()
    val pending by session.pendingSync.collectAsState()
    val unprotected by session.unprotectedLocked.collectAsState()
    val scope = rememberCoroutineScope()
    var pinDialog by remember { mutableStateOf(false) }
    var vaultDialog by remember { mutableStateOf<String?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var lockMenu by remember { mutableStateOf(false) }
    var appLock by remember { mutableStateOf(session.appLockEnabled) }

    PageScaffold(s.settings, onBack = { session.back() }) {
        item {
            SectionLabel(s.appearance)
            PreferenceGroup {
                PreferenceRow(Icons.Rounded.DarkMode, s.theme, null) {}
                FlowRow(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.SYSTEM to s.themeSystem, ThemeMode.LIGHT to s.themeLight, ThemeMode.DARK to s.themeDark).forEach { (m, l) ->
                        FilterChip(prefs.theme == m, onClick = { session.updatePrefs { it.copy(theme = m) } }, label = { Text(l) })
                    }
                }
                PreferenceRow(Icons.Rounded.FormatSize, s.textSize, s.textSizeDesc) { SavedTick(prefs.textScale) }
                FlowRow(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.9f to s.textSmall, 1f to s.textDefault, 1.15f to s.textLarge, 1.3f to s.textHuge).forEach { (v, l) ->
                        FilterChip(prefs.textScale == v, onClick = { session.updatePrefs { it.copy(textScale = v) } }, label = { Text(l) })
                    }
                }
                PreferenceRow(Icons.Rounded.Language, s.language, null) {}
                FlowRow(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(LanguagePreference.ARABIC to s.langArabic, LanguagePreference.ENGLISH to s.langEnglish, LanguagePreference.SYSTEM to s.langSystem).forEach { (m, l) ->
                        FilterChip(prefs.language == m, onClick = { session.updatePrefs { it.copy(language = m) } }, label = { Text(l) })
                    }
                }
                val defaults = com.example.core.prefs.AppPrefs()
                if (prefs.theme != defaults.theme || prefs.textScale != defaults.textScale || prefs.gridView != defaults.gridView || prefs.sort != defaults.sort)
                    TextButton(onClick = { session.updatePrefs { it.copy(theme = defaults.theme, textScale = defaults.textScale, gridView = defaults.gridView, sort = defaults.sort) } },
                        modifier = Modifier.padding(start = 60.dp, bottom = 8.dp)) { Text(s.restoreDefaults) }
            }
        }
        item {
            SectionLabel(s.security)
            PreferenceGroup {
                SwitchRow(Icons.Rounded.Lock, s.appLock, s.appLockDesc, appLock) { on ->
                    if (on) pinDialog = true else { session.setAppLock(false, null); appLock = false }
                }
                if (appLock) {
                    PreferenceRow(Icons.Rounded.Password, s.changePin, null, onClick = { pinDialog = true })
                    Box {
                        PreferenceRow(Icons.Rounded.Schedule, s.autoLock, if (prefs.autoLock == AutoLockTimeout.IMMEDIATELY) s.immediately else s.minutes(prefs.autoLock.minutes),
                            onClick = { lockMenu = true })
                        DropdownMenu(lockMenu, { lockMenu = false }) {
                            AutoLockTimeout.entries.forEach { t ->
                                DropdownMenuItem(text = { Text(if (t == AutoLockTimeout.IMMEDIATELY) s.immediately else s.minutes(t.minutes)) },
                                    onClick = { session.updatePrefs { it.copy(autoLock = t) }; lockMenu = false })
                            }
                        }
                    }
                }
                PreferenceRow(Icons.Rounded.Key, s.vaultTitle, when (vault) {
                    VaultState.READY -> s.vaultReady; VaultState.NEEDS_UNLOCK -> s.vaultNeedsUnlock; VaultState.NOT_SET_UP -> s.vaultNotSet
                }, onClick = { vaultDialog = when (vault) { VaultState.READY -> "change"; VaultState.NEEDS_UNLOCK -> "unlock"; VaultState.NOT_SET_UP -> "create" } })
                if (unprotected > 0) {
                    PreferenceRow(Icons.Rounded.Warning, s.protectOldNotes(unprotected), null) {
                        TextButton(onClick = {
                            if (vault != VaultState.READY) vaultDialog = if (vault == VaultState.NOT_SET_UP) "create" else "unlock"
                            else scope.launch { val (u, f) = session.upgradeOldLockedNotes(); session.message(s.protectedResult(u, f)) }
                        }) { Text(s.protectNow) }
                    }
                }
            }
        }
        item {
            SectionLabel(s.dataSection)
            PreferenceGroup {
                PreferenceRow(Icons.Rounded.PhoneAndroid, s.migrationCenter, s.migrationCenterDesc, onClick = { session.navigate(Screen.Welcome(firstRun = false)) })
                PreferenceRow(Icons.Rounded.Download, s.importCenter, s.importCenterDesc, onClick = { session.navigate(Screen.ImportCenter) })
                PreferenceRow(Icons.Rounded.AutoAwesome, s.assistant, s.assistantDesc, onClick = { session.navigate(Screen.AssistantSettings) })
                val ctx = androidx.compose.ui.platform.LocalContext.current
                var ocrOn by remember { mutableStateOf(!com.example.ocr.OcrGuard.disabled(ctx)) }
                val paused = !ocrOn && com.example.ocr.OcrGuard.crashCount(ctx) >= 2
                SwitchRow(Icons.Rounded.Download, s.ocrSetting, if (paused) s.ocrPausedDesc else s.ocrSettingDesc, ocrOn) { on ->
                    com.example.ocr.OcrGuard.setEnabled(ctx, on); ocrOn = on
                }
                PreferenceRow(Icons.Rounded.Backup, s.backupRestore, s.backupRestoreDesc, onClick = { session.navigate(Screen.Backup) })
                if (session.online) PreferenceRow(Icons.Rounded.CloudSync, s.sync, if (pending > 0) s.syncPending(pending) else s.syncUpToDate) {
                    TextButton(onClick = { session.syncNow() }) { Text(s.syncNow) }
                } else PreferenceRow(Icons.Rounded.CloudSync, s.sync, if (onSignIn != null) s.localSyncInfo else s.syncNotConfigured)
                if (session.online) ExpandableText(s.syncInfo + " " + s.attachmentsLocalNote, collapsedLines = 2, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 72.dp, end = 18.dp, bottom = 14.dp))
            }
        }
        item {
            SectionLabel(s.account)
            PreferenceGroup {
                if (session.isLocalAccount) {
                    PreferenceRow(Icons.Rounded.PhoneAndroid, s.localAccount, null)
                    if (onSignIn != null) PreferenceRow(Icons.Rounded.CloudSync, s.signInToSync, s.localSyncInfo, onClick = onSignIn)
                } else {
                    PreferenceRow(Icons.Rounded.PhoneAndroid, email ?: "", null)
                    PreferenceRow(Icons.AutoMirrored.Rounded.Logout, s.signOut, null, onClick = { confirmSignOut = true })
                }
            }
            SectionLabel(s.about)
            PreferenceGroup {
                PreferenceRow(Icons.Rounded.Info, s.aboutNotea, s.versionLabel(com.example.BuildConfig.VERSION_NAME), onClick = { session.navigate(Screen.About) })
            }
            DeveloperSignature(Modifier.padding(top = 28.dp, bottom = 8.dp))
        }
    }

    if (pinDialog) SetPinDialog(s.setPin, withHint = false, onSet = { pin, _ -> session.setAppLock(true, pin); appLock = true }) { pinDialog = false }
    if (confirmSignOut) ConfirmDialog(s.signOut, s.signOutConfirm, s.signOut, false, onSignOut) { confirmSignOut = false }
    when (vaultDialog) {
        "create" -> PassphraseDialog(true, s.vaultSetupTitle, s.vaultSetupBody, { session.setUpVault(it) }, { session.message(s.vaultReady) }) { vaultDialog = null }
        "unlock" -> PassphraseDialog(false, s.vaultUnlockTitle, s.vaultUnlockBody, { session.unlockVault(it) }, { session.message(s.vaultReady) }) { vaultDialog = null }
        "change" -> PassphraseDialog(true, s.changePassphrase, s.vaultSetupBody, { session.changeVaultPassphrase(it) }, { session.message(s.done) }) { vaultDialog = null }
    }
}

// ====================================================================== Import center
@Composable
fun ImportCenterScreen(session: SessionViewModel, data: DataViewModel) {
    val s = LocalStrings.current
    val task by data.task.collectAsState()
    var source by remember { mutableStateOf(ImportSource.FILES) }
    var paste by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) data.previewFiles(source, uris, s.analyzing) { session.navigate(Screen.ImportPreview) }
    }
    fun pick(src: ImportSource) { source = src; picker.launch(arrayOf("*/*")) }

    PageScaffold(s.importCenter, onBack = { session.back() }) {
        item { ExpandableText(s.importIntro, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), collapsedLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SourceCard(s.samsungNotes, s.samsungSteps) { FilledTonalButton(onClick = { pick(ImportSource.SAMSUNG) }) { Text(s.chooseFiles) } } }
        item {
            SourceCard(s.xiaomiNotes, s.xiaomiSteps) {
                FilledTonalButton(onClick = { pick(ImportSource.XIAOMI) }) { Text(s.chooseFiles) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { paste = true }) { Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(s.pasteText) }
            }
        }
        item { SourceCard(s.huaweiNotepad, s.huaweiSteps) { FilledTonalButton(onClick = { pick(ImportSource.HUAWEI) }) { Text(s.chooseFiles) } } }
        item { SourceCard(s.otherFiles, s.otherSteps) { FilledTonalButton(onClick = { pick(ImportSource.FILES) }) { Text(s.chooseFiles) } } }
    }
    TaskOverlay(task, data) { session.navigate(Screen.ImportPreview) }
    if (paste) {
        var text by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { paste = false }, title = { Text(s.pasteTextTitle) }, text = {
            OutlinedTextField(text, { text = it }, placeholder = { Text(s.pasteTextHint) }, modifier = Modifier.fillMaxWidth().height(260.dp))
        }, confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { paste = false; data.previewPasted(text, s.analyzing) { session.navigate(Screen.ImportPreview) } }) { Text(s.next) }
        }, dismissButton = { TextButton(onClick = { paste = false }) { Text(s.cancel) } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceCard(title: String, steps: String, actions: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            ExpandableText(steps, collapsedLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(verticalArrangement = Arrangement.Center) { actions() }
        }
    }
}

/** Progress / password prompts / results for long data tasks. */
@Composable
fun TaskOverlay(task: DataTask, data: DataViewModel, onRestoreReady: () -> Unit = {}) {
    val s = LocalStrings.current
    when (task) {
        is DataTask.Working -> AlertDialog(onDismissRequest = {}, confirmButton = {}, text = {
            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(16.dp)); Text(task.label.ifBlank { s.working }) }
        })
        is DataTask.NeedsBackupPassword -> {
            var pw by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { data.reset() }, title = { Text(s.restoreBackup) }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.enterBackupPassword)
                    SecretField(pw, { pw = it }, s.backupPassword, isError = task.wrong)
                    if (task.wrong) Text(s.wrongPassphrase, color = MaterialTheme.colorScheme.error)
                }
            }, confirmButton = { TextButton(enabled = pw.isNotEmpty(), onClick = { data.restorePreview(task.uri, pw.toCharArray(), null, s.analyzing, onRestoreReady) }) { Text(s.next) } },
                dismissButton = { TextButton(onClick = { data.reset() }) { Text(s.cancel) } })
        }
        is DataTask.NeedsVaultPassphrase -> {
            var pw by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = { data.reset() }, title = { Text(s.vaultTitle) }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(s.backupVaultPassphrase); SecretField(pw, { pw = it }, s.passphrase) }
            }, confirmButton = {
                TextButton(enabled = pw.isNotEmpty(), onClick = { data.restorePreview(task.uri, task.password, pw.toCharArray(), s.analyzing, onRestoreReady) }) { Text(s.next) }
            }, dismissButton = { TextButton(onClick = { data.reset(); onRestoreReady() }) { Text(s.continueWithout) } })
        }
        is DataTask.Failed -> AlertDialog(onDismissRequest = { data.reset() }, confirmButton = { TextButton(onClick = { data.reset() }) { Text(s.ok) } }, text = {
            Text(when (task.reason) {
                BackupException.Reason.CORRUPTED.name -> s.corruptedBackup
                BackupException.Reason.NEWER_VERSION.name -> s.newerBackup
                else -> s.notABackup
            })
        })
        else -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportPreviewScreen(session: SessionViewModel, data: DataViewModel) {
    val s = LocalStrings.current
    val preview by data.preview.collectAsState()
    val task by data.task.collectAsState()
    var intoFolder by remember { mutableStateOf(false) }
    var folderName by remember(preview?.source) {
        mutableStateOf(when (preview?.source) {
            ImportSource.SAMSUNG -> s.samsungNotes; ImportSource.XIAOMI -> s.xiaomiNotes; ImportSource.HUAWEI -> s.huaweiNotepad; else -> s.importCenter
        })
    }
    val p = preview
    LaunchedEffect(task) { if (task is DataTask.Done) Unit }
    if (task is DataTask.Done) {
        val r = (task as DataTask.Done).result
        AlertDialog(onDismissRequest = {}, title = { Text(s.importDone) }, text = {
            Column { Text(s.importResult(r.created, r.merged, r.skipped, r.attachments)); if (r.failed.isNotEmpty()) Text(s.failedItems(r.failed.size), color = MaterialTheme.colorScheme.error) }
        }, confirmButton = { TextButton(onClick = { data.reset(); session.home() }) { Text(s.done) } })
    }
    PageScaffold(s.preview, onBack = { data.discardPreview(); session.back() }) {
        if (p == null) return@PageScaffold
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.previewSummary(p.noteCount, p.attachmentCount, p.duplicateCount), style = MaterialTheme.typography.titleMedium)
                    p.backupInfo?.let { Text(s.date(it.createdAt) + " • " + s.backupContents(it.counts), style = MaterialTheme.typography.bodySmall) }
                    if (p.source != ImportSource.BACKUP) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(intoFolder, { intoFolder = it }); Text(s.importIntoFolder)
                    }
                    if (intoFolder) OutlinedTextField(folderName, { folderName = it.replace("\n", " ") }, maxLines = 3, label = { Text(s.folderName) }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { data.setAll(true) }) { Text(s.selectAll) }
                        Spacer(Modifier.weight(1f))
                        Button(enabled = p.noteCount > 0, onClick = { data.commit(if (intoFolder) folderName else null) }) { Text(s.importN(p.noteCount)) }
                    }
                }
            }
        }
        val issues = p.issues.filter { i -> p.candidates.none { it.sourceName == i.sourceName } || i.warning == ImportWarning.UNSUPPORTED || i.warning == ImportWarning.UNREADABLE }
        if (issues.isNotEmpty()) item {
            SectionLabel(s.warnings)
            PreferenceGroup {
                issues.take(30).forEach { i -> PreferenceRow(Icons.Rounded.Warning, i.sourceName, s.warning(i.warning)) }
            }
        }
        if (p.candidates.isEmpty()) item { Text(s.nothingToImport, Modifier.padding(20.dp)) }
        items(p.candidates, key = { it.key }) { c ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(c.action != ImportAction.SKIP, { on ->
                            data.setAction(c.key, if (on) (if (c.duplicate != DuplicateKind.NONE) ImportAction.KEEP_BOTH else ImportAction.IMPORT) else ImportAction.SKIP)
                        })
                        Column(Modifier.weight(1f)) {
                            Text(c.title.ifBlank { s.untitled }, style = MaterialTheme.typography.titleSmall)
                            val meta = listOfNotNull(
                                c.updatedAt?.let { s.date(it, false) },
                                if (c.checklist.isNotEmpty()) "${s.num(c.checklist.size)} ${s.typeChecklist}" else null,
                                if (c.attachments.isNotEmpty()) "${s.num(c.attachments.size)} ${s.attachments}" else null,
                                if (c.storedLocked != null) s.typeLocked else null
                            ).joinToString(" • ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val bodyPreview = com.example.ui.editor.MarkdownEngine.plain(c.body).take(20_000)
                    if (bodyPreview.isNotBlank()) ExpandableText(bodyPreview, collapsedLines = 3, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 48.dp))
                    c.warnings.forEach { w -> Text("⚠ " + s.warning(w), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 48.dp, top = 4.dp)) }
                    if (c.duplicate != DuplicateKind.NONE) {
                        Text(if (c.duplicate == DuplicateKind.EXACT) s.exactDuplicate else s.similarDuplicate, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 48.dp, top = 6.dp))
                        FlowRow(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(c.action == ImportAction.SKIP, { data.setAction(c.key, ImportAction.SKIP) }, { Text(s.skip) })
                            if (c.duplicateOfId != null && c.storedLocked == null) FilterChip(c.action == ImportAction.MERGE, { data.setAction(c.key, ImportAction.MERGE) }, { Text(s.merge) })
                            FilterChip(c.action == ImportAction.KEEP_BOTH, { data.setAction(c.key, ImportAction.KEEP_BOTH) }, { Text(s.keepBoth) })
                        }
                    }
                }
            }
        }
    }
    TaskOverlay(task, data)
}

// ====================================================================== Backup
@Composable
fun BackupScreen(session: SessionViewModel, data: DataViewModel) {
    val s = LocalStrings.current
    val prefs by session.prefs.collectAsState()
    val task by data.task.collectAsState()
    val history by data.history.collectAsState()
    val context = LocalContext.current
    var encrypt by remember { mutableStateOf(false) }
    var pw1 by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var exportSnapshot by remember { mutableStateOf<java.io.File?>(null) }
    var confirmDelete by remember { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(Unit) { data.loadHistory() }
    val fileName = "Notea-" + java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).format(java.util.Date()) + ".notea"
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val snap = exportSnapshot
        if (uri != null && snap != null) { data.exportSnapshot(snap, uri) { session.message(if (it) s.backupSaved else s.backupFailed) }; exportSnapshot = null }
        else if (uri != null) data.export(uri, if (encrypt) pw1.toCharArray() else null) { ok -> session.message(if (ok) s.backupSaved else s.backupFailed) }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) data.restorePreview(uri, null, null, s.analyzing) { session.navigate(Screen.ImportPreview) }
    }
    val pwOk = !encrypt || (pw1.length >= 8 && pw1 == pw2)

    PageScaffold(s.backupRestore, onBack = { session.back() }) {
        item {
            SectionLabel(s.exportBackup)
            PreferenceGroup {
                ExpandableText(s.exportBackupDesc, Modifier.padding(18.dp, 14.dp, 18.dp, 4.dp), collapsedLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow(Icons.Rounded.EnhancedEncryption, s.encryptBackup, s.backupPasswordInfo, encrypt) { encrypt = it }
                if (encrypt) Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecretField(pw1, { pw1 = it }, s.backupPassword)
                    SecretField(pw2, { pw2 = it }, s.confirmPassphrase, isError = pw2.isNotEmpty() && pw1 != pw2)
                }
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.End) {
                    Button(enabled = pwOk, onClick = { exportSnapshot = null; createDoc.launch(fileName) }) {
                        Icon(Icons.Rounded.Upload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(s.exportBackup)
                    }
                }
            }
        }
        item {
            SectionLabel(s.restoreBackup)
            PreferenceGroup {
                PreferenceRow(Icons.Rounded.FileOpen, s.restoreBackup, s.restoreBackupDesc, onClick = { openDoc.launch(arrayOf("*/*")) })
            }
        }
        item {
            SectionLabel(s.localHistory)
            PreferenceGroup {
                SwitchRow(Icons.Rounded.SystemUpdateAlt, s.autoBackup, s.autoBackupDesc, prefs.autoBackup) { on -> session.updatePrefs { it.copy(autoBackup = on) } }
                PreferenceRow(Icons.Rounded.Save, s.createSnapshotNow, null, onClick = { data.createSnapshot { session.message(if (it) s.snapshotCreated else s.backupFailed) } })
                if (history.isEmpty()) Text(s.noSnapshots, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                history.forEach { f ->
                    PreferenceRow(Icons.Rounded.Restore, s.date(f.lastModified()), android.text.format.Formatter.formatShortFileSize(context, f.length())) {
                        Row {
                            IconButton(onClick = { data.restoreSnapshot(f, s.analyzing) { session.navigate(Screen.ImportPreview) } }) { Icon(Icons.Rounded.Restore, s.restore) }
                            IconButton(onClick = { exportSnapshot = f; createDoc.launch(f.name) }) { Icon(Icons.Rounded.Upload, s.saveToFiles) }
                            IconButton(onClick = { confirmDelete = f }) { Icon(Icons.Rounded.DeleteOutline, s.delete) }
                        }
                    }
                }
            }
        }
    }
    TaskOverlay(task, data) { session.navigate(Screen.ImportPreview) }
    confirmDelete?.let { f -> ConfirmDialog(s.delete, s.date(f.lastModified()), s.delete, true, { data.deleteSnapshot(f) }) { confirmDelete = null } }
}
