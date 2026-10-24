package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.NoteBackupSerializer
import com.example.data.model.AutoLockTimeout
import com.example.data.model.LanguagePreference
import com.example.data.model.Note
import com.example.data.model.NoteCategory
import com.example.data.model.NoteSortOption
import com.example.data.model.PinSecurity
import com.example.data.model.SyncStatus
import com.example.data.model.ThemeMode
import com.example.data.model.UserSettings
import com.example.data.repository.VaultRepository
import com.example.security.CryptoManager
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class VaultSection {
    NOTES,
    FAVORITES,
    ARCHIVE,
    TRASH
}

class VaultViewModel(
    private val repository: VaultRepository,
    private val currentUserId: String,
    initialLanguagePref: LanguagePreference = LanguagePreference.ARABIC,
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM,
    initialTimeout: AutoLockTimeout = AutoLockTimeout.IMMEDIATELY,
    private val onPreferencesPersistLocal: (LanguagePreference, ThemeMode, AutoLockTimeout) -> Unit = { _, _, _ -> }
) : ViewModel() {

    private val _syncStatus = MutableStateFlow(SyncStatus.SYNCING)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    val notesState: StateFlow<UiState<List<Note>>> = repository.observeNotes(currentUserId)
        .onEach { notes ->
            _syncStatus.value = SyncStatus.SYNCED
            purgeExpiredTrash(notes)
        }
        .map<List<Note>, UiState<List<Note>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing notes", error)
            _syncStatus.value = SyncStatus.OFFLINE
            val cached = repository.getCachedLocalNotes(currentUserId)
            if (cached.isNotEmpty()) {
                emit(UiState.Success(cached))
            } else {
                emit(UiState.Error(error.message ?: "Failed to load notes"))
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _userSettings = MutableStateFlow(
        UserSettings(
            userId = currentUserId,
            appLockEnabled = false,
            appPinHash = "",
            languageCode = resolveLanguageCode(initialLanguagePref)
        )
    )
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    private val _languagePreference = MutableStateFlow(initialLanguagePref)
    val languagePreference: StateFlow<LanguagePreference> = _languagePreference.asStateFlow()

    private val _languageCode = MutableStateFlow(resolveLanguageCode(initialLanguagePref))
    val languageCode: StateFlow<String> = _languageCode.asStateFlow()

    private val _themeMode = MutableStateFlow(initialThemeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _autoLockTimeout = MutableStateFlow(initialTimeout)
    val autoLockTimeout: StateFlow<AutoLockTimeout> = _autoLockTimeout.asStateFlow()

    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _sortOption = MutableStateFlow(NoteSortOption.PINNED)
    val sortOption: StateFlow<NoteSortOption> = _sortOption.asStateFlow()

    private val _activeSection = MutableStateFlow(VaultSection.NOTES)
    val activeSection: StateFlow<VaultSection> = _activeSection.asStateFlow()

    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private val _unlockedNoteIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedNoteIds: StateFlow<Set<String>> = _unlockedNoteIds.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow(NoteCategory.ALL.key)
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var lastBackgroundTimestampMillis: Long = 0L

    init {
        viewModelScope.launch {
            repository.observeUserSettings(currentUserId)
                .catch { error ->
                    Log.w(TAG, "Error observing user settings", error)
                }
                .collect { remoteSettings ->
                    if (remoteSettings != null) {
                        _userSettings.value = remoteSettings
                        if (_languagePreference.value != LanguagePreference.SYSTEM &&
                            (remoteSettings.languageCode == "ar" || remoteSettings.languageCode == "en")
                        ) {
                            _languageCode.value = remoteSettings.languageCode
                        }
                        if (!remoteSettings.appLockEnabled || remoteSettings.appPinHash.isBlank()) {
                            _isAppUnlocked.value = true
                        }
                    } else {
                        _isAppUnlocked.value = true
                    }
                }
        }
    }

    private fun purgeExpiredTrash(notes: List<Note>) {
        val now = System.currentTimeMillis()
        notes.forEach { note ->
            val trashedAt = note.trashedAtMillis
            if (note.isTrashed && trashedAt != null && (now - trashedAt) > Note.TRASH_RETENTION_MILLIS) {
                viewModelScope.launch {
                    repository.deleteNote(note.id)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(categoryKey: String) {
        _selectedCategory.value = categoryKey
    }

    fun selectSection(section: VaultSection) {
        _activeSection.value = section
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun setSortOption(option: NoteSortOption) {
        _sortOption.value = option
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun toggleLanguage() {
        val nextPref = if (_languageCode.value == "ar") {
            LanguagePreference.ENGLISH
        } else {
            LanguagePreference.ARABIC
        }
        setLanguagePreference(nextPref)
    }

    fun setLanguagePreference(pref: LanguagePreference) {
        _languagePreference.value = pref
        val resolved = resolveLanguageCode(pref)
        _languageCode.value = resolved
        onPreferencesPersistLocal(pref, _themeMode.value, _autoLockTimeout.value)
        val current = _userSettings.value
        viewModelScope.launch {
            repository.saveUserSettings(
                appLockEnabled = current.appLockEnabled,
                appPinHash = current.appPinHash,
                languageCode = resolved
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        onPreferencesPersistLocal(_languagePreference.value, mode, _autoLockTimeout.value)
    }

    fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        _autoLockTimeout.value = timeout
        onPreferencesPersistLocal(_languagePreference.value, _themeMode.value, timeout)
    }

    fun onAppBackgrounded() {
        lastBackgroundTimestampMillis = System.currentTimeMillis()
        if (_autoLockTimeout.value == AutoLockTimeout.IMMEDIATELY) {
            lockAppNow()
        }
    }

    fun onAppForegrounded() {
        if (lastBackgroundTimestampMillis == 0L) return
        val elapsedMinutes = (System.currentTimeMillis() - lastBackgroundTimestampMillis) / 60000L
        if (elapsedMinutes >= _autoLockTimeout.value.minutes) {
            lockAppNow()
        }
    }

    fun unlockAppWithBiometricOrDeviceCredential() {
        _isAppUnlocked.value = true
    }

    fun verifyAndUnlockApp(pinInput: String): Boolean {
        val current = _userSettings.value
        if (!current.appLockEnabled || current.appPinHash.isBlank()) {
            _isAppUnlocked.value = true
            return true
        }
        val matched = PinSecurity.verifyPin(pinInput, current.appPinHash)
        if (matched) {
            _isAppUnlocked.value = true
        }
        return matched
    }

    fun lockAppNow() {
        val current = _userSettings.value
        if (current.appLockEnabled && current.appPinHash.isNotBlank()) {
            _isAppUnlocked.value = false
            _unlockedNoteIds.value = emptySet()
        }
    }

    fun configureAppLock(enabled: Boolean, newPin: String? = null) {
        val current = _userSettings.value
        val updatedHash = when {
            newPin != null -> PinSecurity.hashPin(newPin)
            else -> current.appPinHash
        }
        val effectiveEnabled = enabled && updatedHash.isNotBlank()
        _isAppUnlocked.value = true
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.SYNCING
            val result = repository.saveUserSettings(
                appLockEnabled = effectiveEnabled,
                appPinHash = updatedHash,
                languageCode = _languageCode.value
            )
            if (result.isSuccess) {
                _syncStatus.value = SyncStatus.SYNCED
            } else {
                _syncStatus.value = SyncStatus.ERROR
            }
        }
    }

    fun unlockNoteWithBiometric(note: Note) {
        _unlockedNoteIds.value = _unlockedNoteIds.value + note.id
    }

    fun verifyAndUnlockNote(note: Note, pinInput: String): Boolean {
        val targetHash = note.notePinHash.ifBlank { _userSettings.value.appPinHash }
        if (targetHash.isBlank()) {
            _unlockedNoteIds.value = _unlockedNoteIds.value + note.id
            return true
        }
        val valid = PinSecurity.verifyPin(pinInput, targetHash)
        if (valid) {
            _unlockedNoteIds.value = _unlockedNoteIds.value + note.id
        }
        return valid
    }

    fun saveNote(
        existingNote: Note?,
        title: String,
        content: String,
        baseCategory: String,
        colorIndex: Int,
        isPinned: Boolean,
        isFavorite: Boolean,
        isLocked: Boolean,
        notePinHash: String,
        lockHint: String,
        isArchived: Boolean,
        onComplete: (String?) -> Unit = {}
    ) {
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.SYNCING
            val encodedCategory = Note.buildCategoryString(
                baseCategory = baseCategory,
                isFavorite = isFavorite,
                isTrashed = existingNote?.isTrashed == true,
                trashedAtMillis = existingNote?.trashedAtMillis
            )
            // Encrypt content via AES-256-GCM when note is locked
            val storedContent = if (isLocked) {
                CryptoManager.encrypt(content)
            } else {
                CryptoManager.decrypt(content)
            }

            if (existingNote == null || existingNote.id.isBlank()) {
                val res = repository.createNote(
                    title = title,
                    content = storedContent,
                    category = encodedCategory,
                    colorIndex = colorIndex,
                    isPinned = isPinned,
                    isLocked = isLocked,
                    notePinHash = notePinHash,
                    lockHint = lockHint,
                    isArchived = isArchived
                )
                if (res.isSuccess) {
                    _syncStatus.value = SyncStatus.SYNCED
                    val newId = res.getOrNull()
                    if (newId != null && isLocked) {
                        _unlockedNoteIds.value = _unlockedNoteIds.value + newId
                    }
                    onComplete(newId)
                } else {
                    _syncStatus.value = SyncStatus.OFFLINE
                    onComplete(null)
                }
            } else {
                val updated = existingNote.copy(
                    title = title,
                    content = storedContent,
                    category = encodedCategory,
                    colorIndex = colorIndex.toLong(),
                    isPinned = isPinned,
                    isLocked = isLocked,
                    notePinHash = notePinHash,
                    lockHint = lockHint,
                    isArchived = isArchived
                )
                val res = repository.updateNote(updated)
                if (res.isSuccess) {
                    _syncStatus.value = SyncStatus.SYNCED
                    onComplete(existingNote.id)
                } else {
                    _syncStatus.value = SyncStatus.OFFLINE
                    onComplete(existingNote.id)
                }
            }
        }
    }

    fun togglePinNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned))
        }
    }

    fun toggleFavoriteNote(note: Note) {
        viewModelScope.launch {
            val newCategory = Note.buildCategoryString(
                baseCategory = note.baseCategory,
                isFavorite = !note.isFavorite,
                isTrashed = note.isTrashed,
                trashedAtMillis = note.trashedAtMillis
            )
            repository.updateNote(note.copy(category = newCategory))
        }
    }

    fun toggleArchiveNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isArchived = !note.isArchived, isPinned = false))
        }
    }

    fun moveNoteToTrash(note: Note) {
        viewModelScope.launch {
            val trashCategory = Note.buildCategoryString(
                baseCategory = note.baseCategory,
                isFavorite = note.isFavorite,
                isTrashed = true,
                trashedAtMillis = System.currentTimeMillis()
            )
            repository.updateNote(
                note.copy(
                    category = trashCategory,
                    isPinned = false,
                    isArchived = false
                )
            )
        }
    }

    fun restoreNoteFromTrash(note: Note) {
        viewModelScope.launch {
            val restoredCategory = Note.buildCategoryString(
                baseCategory = note.baseCategory,
                isFavorite = note.isFavorite,
                isTrashed = false
            )
            repository.updateNote(note.copy(category = restoredCategory))
        }
    }

    fun deleteNotePermanently(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
        }
    }

    fun emptyTrash(allNotes: List<Note>) {
        viewModelScope.launch {
            allNotes.filter { it.isTrashed }.forEach { trashed ->
                repository.deleteNote(trashed.id)
            }
        }
    }

    fun importNotesFromJson(jsonString: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val imported = NoteBackupSerializer.importFromJson(jsonString, currentUserId)
                imported.forEach { note ->
                    repository.createNote(
                        title = note.title,
                        content = note.content,
                        category = note.category,
                        colorIndex = note.colorIndex.toInt(),
                        isPinned = note.isPinned,
                        isLocked = note.isLocked,
                        notePinHash = note.notePinHash,
                        lockHint = note.lockHint,
                        isArchived = note.isArchived
                    )
                }
                onResult(imported.size)
            } catch (e: Exception) {
                onResult(0)
            }
        }
    }

    fun sortNotes(notes: List<Note>, option: NoteSortOption): List<Note> {
        return when (option) {
            NoteSortOption.MODIFIED_DESC -> notes.sortedByDescending {
                it.updatedAt ?: it.createdAt ?: Timestamp(0, 0)
            }
            NoteSortOption.CREATED_DESC -> notes.sortedByDescending {
                it.createdAt ?: it.updatedAt ?: Timestamp(0, 0)
            }
            NoteSortOption.TITLE_ASC -> notes.sortedBy { it.title.lowercase() }
            NoteSortOption.COLOR -> notes.sortedByDescending { it.colorIndex }
            NoteSortOption.PINNED -> notes.sortedWith(
                compareByDescending<Note> { it.isPinned }
                    .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            )
        }
    }

    companion object {
        private const val TAG = "VaultViewModel"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun resolveLanguageCode(pref: LanguagePreference): String {
            return when (pref) {
                LanguagePreference.ARABIC -> "ar"
                LanguagePreference.ENGLISH -> "en"
                LanguagePreference.SYSTEM -> {
                    val sys = Locale.getDefault().language
                    if (sys == "ar") "ar" else "en"
                }
            }
        }
    }
}
