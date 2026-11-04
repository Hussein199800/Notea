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

    fun candidatesWithIssues(): List<ImportCandidate> = _preview.value?.candidates?.filter { it.warnings.isNotEmpty() }.orEmpty()
}
