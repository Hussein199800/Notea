package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.backup.BackupException
import com.example.data.importer.ImportAction
import com.example.data.importer.ImportCandidate
import com.example.data.importer.ImportInput
import com.example.data.importer.ImportPreview
import com.example.data.importer.ImportResult
import com.example.data.importer.ImportSource
import com.example.data.importer.ImportWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface DataTask {
    data object Idle : DataTask
    data class Working(val label: String) : DataTask
    data class NeedsBackupPassword(val uri: Uri, val wrong: Boolean = false) : DataTask
    data class NeedsVaultPassphrase(val uri: Uri, val password: CharArray?, val wrong: Boolean = false) : DataTask
    data class Failed(val reason: String) : DataTask
    data class Done(val result: ImportResult) : DataTask
}

sealed interface TransferState {
    data object Idle : TransferState
    data object Preparing : TransferState
    data class Ready(val offer: com.example.data.transfer.DeviceTransfer.Offer, val qr: android.graphics.Bitmap) : TransferState
    data class Progress(val sending: Boolean, val done: Long, val total: Long) : TransferState
    data object Sent : TransferState
    data object NoNetwork : TransferState
    data object Failed : TransferState
}

/** Import center + backup/restore state (shared by the import, preview and backup screens). */
class DataViewModel(private val container: AppContainer, private val uid: String) : ViewModel() {
    private val importer = container.importManager
    private val backups = container.backupManager

    private val _preview = MutableStateFlow<ImportPreview?>(null)
    val preview: StateFlow<ImportPreview?> = _preview.asStateFlow()
    private val _task = MutableStateFlow<DataTask>(DataTask.Idle)
    val task: StateFlow<DataTask> = _task.asStateFlow()
    private val _history = MutableStateFlow<List<File>>(emptyList())
    val history: StateFlow<List<File>> = _history.asStateFlow()
    val folderTarget = MutableStateFlow<String?>(null)

    fun reset() { _task.value = DataTask.Idle }

    fun previewFiles(source: ImportSource, uris: List<Uri>, label: String, onReady: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        _task.value = DataTask.Working(label)
        // A single .notea file goes through the backup path.
        val inputs = uris.map { importer.inputFromUri(it) }
        if (inputs.size == 1 && inputs[0].extension == "notea") {
            restorePreview(uris[0], null, null, label, onReady); return@launch
        }
        val p = importer.preview(uid, source, inputs)
        _preview.value = p
        _task.value = DataTask.Idle
        launch(Dispatchers.Main) { onReady() }
    }

    fun previewInputs(source: ImportSource, inputs: List<ImportInput>, label: String, onReady: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        _task.value = DataTask.Working(label)
        _preview.value = importer.preview(uid, source, inputs)
        _task.value = DataTask.Idle
        launch(Dispatchers.Main) { onReady() }
    }

    /** Pasted text: notes separated by a line with `---`. */
    fun previewPasted(text: String, label: String, onReady: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        _task.value = DataTask.Working(label)
        val parts = text.split(Regex("(?m)^\\s*-{3,}\\s*$")).map { it.trim() }.filter { it.isNotBlank() }
        val cands = parts.map { importer.previewSharedText(null, it) }
        _preview.value = ImportPreview(ImportSource.XIAOMI, importer.markDuplicates(uid, cands), emptyList())
        _task.value = DataTask.Idle
        launch(Dispatchers.Main) { onReady() }
    }

    fun setAction(key: String, action: ImportAction) {
        val p = _preview.value ?: return
        _preview.value = p.copy(candidates = p.candidates.map { if (it.key == key) it.copy(action = action) else it })
    }

    fun setAll(include: Boolean) {
        val p = _preview.value ?: return
        _preview.value = p.copy(candidates = p.candidates.map {
            if (include) it.copy(action = if (it.action == ImportAction.SKIP) (if (it.duplicateOfId != null) ImportAction.KEEP_BOTH else ImportAction.IMPORT) else it.action)
            else it.copy(action = ImportAction.SKIP)
        })
    }

    fun commit(folderName: String?) = viewModelScope.launch(Dispatchers.IO) {
        val p = _preview.value ?: return@launch
        _task.value = DataTask.Working("")
        val folderId = folderName?.takeIf { it.isNotBlank() }?.let { n ->
            (container.repository.findFolderByName(uid, n) ?: container.repository.createFolder(uid, n)).id
        }
        val result = importer.commit(uid, p.source, p.candidates, folderId)
        if (p.source == ImportSource.BACKUP) backups.finishRestore(uid, result.createdIds)
        importer.clearTemp()
        _preview.value = null
        _task.value = DataTask.Done(result)
    }

    fun discardPreview() { _preview.value = null; importer.clearTemp() }

    // ------------------------------------------------------------------ backup
    fun export(uri: Uri, password: CharArray?, onDone: (Boolean) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        _task.value = DataTask.Working("")
        val ok = try {
            container.app.contentResolver.openOutputStream(uri, "w")?.use { backups.export(uid, it, password) } != null
        } catch (_: Exception) { false } finally { password?.fill(' ') }
        _task.value = DataTask.Idle
        launch(Dispatchers.Main) { onDone(ok) }
    }

    fun exportSnapshot(file: File, uri: Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val ok = runCatching { container.app.contentResolver.openOutputStream(uri, "w")?.use { o -> file.inputStream().use { it.copyTo(o) } } != null }.getOrDefault(false)
        launch(Dispatchers.Main) { onDone(ok) }
    }

    fun restorePreview(uri: Uri, password: CharArray?, vaultPassphrase: CharArray?, label: String, onReady: () -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            _task.value = DataTask.Working(label)
            try {
                val p = backups.read(uid, { container.app.contentResolver.openInputStream(uri) ?: throw IllegalStateException() }, password, vaultPassphrase)
                val needsVault = vaultPassphrase == null && p.backupInfo?.vaultEnvelope != null &&
                    p.backupInfo.vaultEnvelope.keyId != container.keyVault.currentKeyId(uid) &&
                    container.keyVault.currentKeyId(uid) != null &&
                    p.candidates.any { ImportWarning.LOCKED_CONTENT in it.warnings }
                if (needsVault) {
                    _task.value = DataTask.NeedsVaultPassphrase(uri, password); _preview.value = p
                    return@launch
                }
                _preview.value = p
                _task.value = DataTask.Idle
                launch(Dispatchers.Main) { onReady() }
            } catch (e: BackupException) {
                _task.value = when (e.reason) {
                    BackupException.Reason.PASSWORD_REQUIRED -> DataTask.NeedsBackupPassword(uri)
                    BackupException.Reason.WRONG_PASSWORD -> DataTask.NeedsBackupPassword(uri, wrong = true)
                    else -> DataTask.Failed(e.reason.name)
                }
            } catch (e: Exception) {
                _task.value = DataTask.Failed(BackupException.Reason.NOT_A_BACKUP.name)
            }
        }

    fun restoreSnapshot(file: File, label: String, onReady: () -> Unit) = restorePreview(Uri.fromFile(file), null, null, label, onReady)

    fun loadHistory() = viewModelScope.launch(Dispatchers.IO) { _history.value = backups.history(uid) }

    fun createSnapshot(onDone: (Boolean) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        _task.value = DataTask.Working("")
        val ok = runCatching { backups.createSnapshot(uid) }.isSuccess
        _history.value = backups.history(uid)
        _task.value = DataTask.Idle
        launch(Dispatchers.Main) { onDone(ok) }
    }

    fun deleteSnapshot(f: File) = viewModelScope.launch(Dispatchers.IO) { f.delete(); _history.value = backups.history(uid) }

    // ------------------------------------------------------------------ phone to phone
    private val _transfer = MutableStateFlow<TransferState>(TransferState.Idle)
    val transfer: StateFlow<TransferState> = _transfer.asStateFlow()
    private var server: com.example.data.transfer.DeviceTransfer.Server? = null
    private var transferJob: kotlinx.coroutines.Job? = null

    /** Old phone: encrypts a full backup with a one-time code and waits for the new phone. */
    fun startSending() {
        transferJob?.cancel()
        transferJob = viewModelScope.launch(Dispatchers.IO) {
            val host = com.example.data.transfer.DeviceTransfer.localAddress()
            if (host == null) { _transfer.value = TransferState.NoNetwork; return@launch }
            _transfer.value = TransferState.Preparing
            val code = com.example.data.transfer.DeviceTransfer.newCode()
            val file = File(container.app.cacheDir, "transfer/outgoing.notea").apply { parentFile?.mkdirs() }
            try {
                file.outputStream().use { backups.export(uid, it, code.toCharArray()) }
                val srv = com.example.data.transfer.DeviceTransfer.Server(file, code).also { server = it }
                val offer = com.example.data.transfer.DeviceTransfer.Offer(host, srv.port, code)
                _transfer.value = TransferState.Ready(offer, com.example.data.transfer.DeviceTransfer.qrBitmap(offer.toUri()))
                val ok = srv.serve { done, total -> _transfer.value = TransferState.Progress(true, done, total) }
                _transfer.value = if (ok) TransferState.Sent else if (srv.closed && _transfer.value is TransferState.Ready) TransferState.Idle else TransferState.Failed
            } catch (e: Exception) {
                _transfer.value = TransferState.Failed
            } finally {
                file.delete()
            }
        }
    }

    /** New phone: downloads, then opens the usual preview (counts, duplicates) before importing. */
    fun receive(offer: com.example.data.transfer.DeviceTransfer.Offer, label: String, onReady: () -> Unit) {
        transferJob?.cancel()
        transferJob = viewModelScope.launch(Dispatchers.IO) {
            val file = File(container.app.cacheDir, "transfer/incoming.notea").apply { parentFile?.mkdirs() }
            _transfer.value = TransferState.Progress(false, 0, 1)
            val r = com.example.data.transfer.DeviceTransfer.receive(offer, file) { d, t -> _transfer.value = TransferState.Progress(false, d, t) }
            if (r.isFailure) { _transfer.value = TransferState.Failed; return@launch }
            _transfer.value = TransferState.Idle
            restorePreview(android.net.Uri.fromFile(file), offer.code.toCharArray(), null, label, onReady)
        }
    }

    fun cancelTransfer() {
        server?.close(); server = null
        transferJob?.cancel()
        _transfer.value = TransferState.Idle
    }

    override fun onCleared() { server?.close(); super.onCleared() }

    fun candidatesWithIssues(): List<ImportCandidate> = _preview.value?.candidates?.filter { it.warnings.isNotEmpty() }.orEmpty()
}
