package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.core.prefs.AppPrefs
import com.example.core.security.PinHasher
import com.example.core.security.VaultState
import com.example.data.backup.AutoBackupWorker
import com.example.data.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiMessage(val text: String, val undo: (() -> Unit)? = null)

enum class MainTab { NOTES, TASKS, FOLDERS, MORE }

sealed interface Screen {
    data object Home : Screen
    data class Editor(val noteId: String, val isNew: Boolean = false, val startScan: Boolean = false) : Screen
    data object Settings : Screen
    data object ImportCenter : Screen
    data object ImportPreview : Screen
    data object Backup : Screen
    data object About : Screen
    data class Welcome(val firstRun: Boolean) : Screen
    data object Transfer : Screen
    data object AssistantSettings : Screen
    data object Releases : Screen
    data object Diagnostics : Screen
    data object Update : Screen
    data object AskNotes : Screen
}

/** Shared incoming content from Android share sheet / widgets / notifications. */
sealed interface IncomingIntent {
    data class SharedText(val subject: String?, val text: String) : IncomingIntent
    data class SharedFiles(val uris: List<android.net.Uri>) : IncomingIntent
    data class OpenNote(val noteId: String) : IncomingIntent
    data object NewNote : IncomingIntent
    data object OpenUpdate : IncomingIntent
    data class NewNoteWithText(val title: String, val body: String) : IncomingIntent
}

/** Per-account session: navigation, app lock, vault state, sync status and messages. */
class SessionViewModel(val container: AppContainer, val uid: String) : ViewModel() {
    /** False for the on-device account or builds without Firebase: everything stays local. */
    val online: Boolean = container.syncAvailable(uid)
    val isLocalAccount: Boolean = uid == com.example.LOCAL_UID
    private val settings = container.settings
    val prefs: StateFlow<AppPrefs> = settings.state

    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val stack: StateFlow<List<Screen>> = _stack.asStateFlow()

    /** Selected bottom tab (kept here so it survives opening a note and coming back). */
    private val _tab = MutableStateFlow(MainTab.NOTES)
    val tab: StateFlow<MainTab> = _tab.asStateFlow()
    fun selectTab(t: MainTab) { _tab.value = t }

    private val _unlocked = MutableStateFlow(!settings.appLockEnabled(uid))
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private var backgroundedAt = 0L

    private val _vaultState = MutableStateFlow(container.keyVault.state(uid))
    val vaultState: StateFlow<VaultState> = _vaultState.asStateFlow()

    private val _unprotectedLocked = MutableStateFlow(0)
    val unprotectedLocked: StateFlow<Int> = _unprotectedLocked.asStateFlow()

    val pendingSync = container.repository.observePendingSyncCount(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    /** Notes unlocked (PIN verified) during this session; cleared on app lock. */
    private val _openedLocked = MutableStateFlow<Set<String>>(emptySet())
    val openedLocked: StateFlow<Set<String>> = _openedLocked.asStateFlow()

    val appLockEnabled get() = settings.appLockEnabled(uid)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            container.repository.purgeExpiredTrash(uid)
            refreshVault()
        }
        if (online) viewModelScope.launch(Dispatchers.IO) {
            // Online-only extras; everything else works without network.
            container.syncEngine.fetchVaultEnvelope(uid)
            refreshVault()
            // Vault created while the upload failed: publish its envelope now.
            val local = container.keyVault.localEnvelope(uid)
            if (local != null && container.keyVault.masterKey(uid) != null &&
                container.syncEngine.checkRemoteVault(uid) == com.example.data.sync.SyncEngine.RemoteVault.Missing
            ) container.syncEngine.uploadVaultEnvelope(uid, local)
            if (!settings.hasLocalLockSettings(uid)) {
                container.syncEngine.fetchSettings(uid)?.let { remote ->
                    val enabled = remote["appLockEnabled"] as? Boolean ?: false
                    val hash = remote["appPinHash"] as? String ?: ""
                    if (enabled && hash.isNotBlank() && !settings.hasLocalLockSettings(uid)) settings.setAppLock(uid, true, hash)
                }
            }
        }
        if (online) SyncWorker.enqueue(container.app, uid, 0)
    }

    fun startRealtimeSync() = if (!online) Unit else runCatching { container.syncEngine.startListening(uid, container.scope) }
    fun stopRealtimeSync() = if (!online) Unit else runCatching { container.syncEngine.stopListening() }

    fun message(m: String) { _messages.tryEmit(UiMessage(m)) }
    fun restoreFromTrash(noteId: String) { viewModelScope.launch(Dispatchers.IO) { container.repository.restoreFromTrash(uid, noteId) } }
    /** Snackbar with an Undo action (used after every delete/move to trash). */
    fun messageWithUndo(m: String, undo: () -> Unit) { _messages.tryEmit(UiMessage(m, undo)) }

    // ------------------------------------------------------------------ navigation
    /** One ViewModelStore per open editor: survives rotation, cleared (and flushed) when the note leaves the stack. */
    private val editorStores = HashMap<String, androidx.lifecycle.ViewModelStore>()
    fun editorStore(noteId: String) = editorStores.getOrPut(noteId) { androidx.lifecycle.ViewModelStore() }

    private fun setStack(list: List<Screen>) {
        _stack.value = list
        val open = list.filterIsInstance<Screen.Editor>().map { it.noteId }.toSet()
        editorStores.keys.filter { it !in open }.forEach { editorStores.remove(it)?.clear() }
    }

    fun navigate(s: Screen) = setStack(_stack.value + s)
    fun replaceTop(s: Screen) = setStack(_stack.value.dropLast(1) + s)
    fun back(): Boolean {
        if (_stack.value.size <= 1) return false
        setStack(_stack.value.dropLast(1)); return true
    }
    fun home() = setStack(listOf(Screen.Home))

    override fun onCleared() {
        editorStores.values.forEach { it.clear() }
        editorStores.clear()
        super.onCleared()
    }

    // ------------------------------------------------------------------ preferences
    fun updatePrefs(change: (AppPrefs) -> AppPrefs) {
        val before = settings.state.value
        settings.update(change)
        val after = settings.state.value
        if (before.autoBackup != after.autoBackup) AutoBackupWorker.schedule(container.app, after.autoBackup)
        if (before.language != after.language) pushSettings()
    }

    private fun pushSettings() = viewModelScope.launch(Dispatchers.IO) {
        if (!online) return@launch
        container.syncEngine.pushSettings(uid, settings.appLockEnabled(uid), settings.appLockHash(uid), if (container.isArabic()) "ar" else "en")
    }

    // ------------------------------------------------------------------ app lock
    fun onBackground() {
        backgroundedAt = System.currentTimeMillis()
        if (appLockEnabled && prefs.value.autoLock.minutes == 0) lockNow()
        container.attachmentStore.clearDecryptedCache()
    }

    fun onForeground() {
        if (!appLockEnabled || backgroundedAt == 0L) return
        val elapsedMin = (System.currentTimeMillis() - backgroundedAt) / 60_000
        if (elapsedMin >= prefs.value.autoLock.minutes) lockNow()
    }

    fun lockNow() {
        if (appLockEnabled) _unlocked.value = false
        _openedLocked.value = emptySet()
    }

    suspend fun verifyAppPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        val hash = settings.appLockHash(uid)
        val ok = PinHasher.verify(pin, hash)
        if (ok) {
            if (PinHasher.needsUpgrade(hash)) { settings.setAppLock(uid, true, PinHasher.hash(pin)); pushSettings() }
            _unlocked.value = true
        }
        ok
    }

    fun unlockWithDeviceCredential() { _unlocked.value = true }

    fun setAppLock(enabled: Boolean, newPin: String?) = viewModelScope.launch(Dispatchers.Default) {
        val hash = newPin?.let { PinHasher.hash(it) } ?: settings.appLockHash(uid)
        settings.setAppLock(uid, enabled && hash.isNotBlank(), hash)
        _unlocked.value = true
        pushSettings()
    }

    // ------------------------------------------------------------------ locked notes
    fun markOpened(noteId: String) { _openedLocked.value = _openedLocked.value + noteId }
    fun isOpened(noteId: String) = noteId in _openedLocked.value

    suspend fun verifyNotePin(pin: String, hash: String): Boolean = withContext(Dispatchers.Default) { PinHasher.verify(pin, hash) }

    // ------------------------------------------------------------------ vault
    fun refreshVault() {
        _vaultState.value = container.keyVault.state(uid)
        viewModelScope.launch(Dispatchers.IO) { _unprotectedLocked.value = container.repository.countUnprotectedLocked(uid) }
    }

    /**
     * Creates the vault only after the server confirms none exists for this account; otherwise a
     * second device could silently create a different key and orphan notes locked elsewhere.
     */
    suspend fun setUpVault(passphrase: CharArray): Boolean = withContext(Dispatchers.IO) {
        try {
            if (online) when (val remote = container.syncEngine.checkRemoteVault(uid)) {
                is com.example.data.sync.SyncEngine.RemoteVault.Exists -> {
                    container.keyVault.rememberEnvelope(uid, remote.envelope)
                    refreshVault()
                    // The account already has a vault: try this passphrase against it.
                    return@withContext container.keyVault.unlockWithPassphrase(uid, passphrase).also { if (it) { refreshVault(); upgradeOldLockedNotes(true) } }
                }
                com.example.data.sync.SyncEngine.RemoteVault.Unknown -> { message(vaultNeedsOnlineMsg()); return@withContext false }
                com.example.data.sync.SyncEngine.RemoteVault.Missing -> Unit
            }
            val env = container.keyVault.setUp(uid, passphrase)
            if (online && !container.syncEngine.uploadVaultEnvelope(uid, env)) message(vaultNeedsOnlineMsg())
            refreshVault()
            upgradeOldLockedNotes(silent = true)
            true
        } catch (e: Exception) {
            false
        } finally {
            passphrase.fill(' ')
        }
    }

    suspend fun unlockVault(passphrase: CharArray): Boolean = withContext(Dispatchers.Default) {
        try {
            val ok = container.keyVault.unlockWithPassphrase(uid, passphrase)
            refreshVault()
            if (ok) upgradeOldLockedNotes(silent = true)
            ok
        } finally {
            passphrase.fill(' ')
        }
    }

    suspend fun changeVaultPassphrase(passphrase: CharArray): Boolean = withContext(Dispatchers.Default) {
        try {
            val env = container.keyVault.changePassphrase(uid, passphrase)
            if (online) container.syncEngine.uploadVaultEnvelope(uid, env) else true
        } catch (_: Exception) {
            false
        } finally {
            passphrase.fill(' ')
        }
    }

    suspend fun upgradeOldLockedNotes(silent: Boolean = false): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val r = container.repository.upgradeLockedNotes(uid)
        refreshVault()
        r
    }

    private fun vaultNeedsOnlineMsg() = if (container.isArabic()) "يلزم اتصال بالإنترنت لإنشاء الخزنة أول مرة" else "An internet connection is needed to create the vault the first time"

    fun markOnboarded() { settings.setOnboardingDone(uid); evaluatePrompts() }

    /** First-run tour (5–7 steps) or «ما الجديد» after an update — at most one, once. */
    enum class HelpPrompt { TOUR, WHATS_NEW }
    private val _help = MutableStateFlow<HelpPrompt?>(null)
    val help: StateFlow<HelpPrompt?> = _help.asStateFlow()
    private fun evaluatePrompts() {
        val current = com.example.core.Changelog.current.version
        _help.value = when {
            !settings.tourDone() -> HelpPrompt.TOUR
            settings.lastSeenVersion() != current -> HelpPrompt.WHATS_NEW
            else -> null
        }
    }
    fun startTour() { _help.value = HelpPrompt.TOUR }
    fun dismissHelp() {
        settings.setTourDone(); settings.setLastSeenVersion(com.example.core.Changelog.current.version); _help.value = null
    }

    /** First run on this account with no notes yet: open the migration center once. */
    fun maybeShowWelcome() = viewModelScope.launch(Dispatchers.IO) {
        if (settings.onboardingDone(uid)) { kotlinx.coroutines.withContext(Dispatchers.Main) { evaluatePrompts() }; return@launch }
        if (container.db.noteDao().getAllWithDetails(uid).isEmpty()) {
            kotlinx.coroutines.withContext(Dispatchers.Main) { if (_stack.value == listOf(Screen.Home)) navigate(Screen.Welcome(firstRun = true)) }
        } else { settings.setOnboardingDone(uid); kotlinx.coroutines.withContext(Dispatchers.Main) { evaluatePrompts() } }
    }

    fun syncNow() { if (online) SyncWorker.enqueue(container.app, uid, 0) }

    fun signOut(onDone: () -> Unit) {
        stopRealtimeSync()
        onDone()
    }
}
