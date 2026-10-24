package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.signOut
import com.example.data.model.Note
import com.example.data.model.NoteCategory
import com.example.data.model.NoteRichMetadata
import com.example.data.model.NoteSortOption
import com.example.data.model.SyncStatus
import com.example.security.CryptoManager
import com.example.ui.i18n.ArabicStrings
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.LocalVaultStrings
import com.example.ui.theme.NoteCardDarkColors
import com.example.ui.theme.NoteCardLightColors
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.VaultSection
import com.example.ui.viewmodel.VaultViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun VaultMainScreen(
    viewModel: VaultViewModel,
    onSignedOut: () -> Unit
) {
    val languageCode by viewModel.languageCode.collectAsStateWithLifecycle()
    val languagePref by viewModel.languagePreference.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val autoLockTimeout by viewModel.autoLockTimeout.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val activeSection by viewModel.activeSection.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsStateWithLifecycle()
    val unlockedNoteIds by viewModel.unlockedNoteIds.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val strings = if (languageCode == "ar") ArabicStrings else EnglishStrings
    val layoutDirection = if (languageCode == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var editingNote by remember { mutableStateOf<Note?>(null) }
    var isEditorOpen by remember { mutableStateOf(false) }
    var notePendingUnlock by remember { mutableStateOf<Note?>(null) }
    var showSecuritySettings by remember { mutableStateOf(false) }
    var showSetAppPinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val allNotesList = (notesState as? UiState.Success)?.data ?: emptyList()

    CompositionLocalProvider(
        LocalVaultStrings provides strings,
        LocalLayoutDirection provides layoutDirection
    ) {
        if (userSettings.appLockEnabled && userSettings.appPinHash.isNotBlank() && !isAppUnlocked) {
            AppLockScreen(
                onVerifyPin = { pin -> viewModel.verifyAndUnlockApp(pin) },
                onBiometricUnlockSuccess = { viewModel.unlockAppWithBiometricOrDeviceCredential() },
                onToggleLanguage = { viewModel.toggleLanguage() },
                onSignOut = {
                    signOut(context, credentialManager, onSignedOut, scope)
                }
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isExpandedScreen = maxWidth >= 720.dp

                if (isExpandedScreen) {
                    // Canonical Tablet / Foldable Layout: NavigationRail + List-Detail Two-Pane
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        contentWindowInsets = WindowInsets.safeDrawing
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            VaultNavigationRail(
                                activeSection = activeSection,
                                onSelectSection = { viewModel.selectSection(it) },
                                onNewNote = {
                                    editingNote = null
                                    isEditorOpen = true
                                }
                            )

                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )

                            Box(
                                modifier = Modifier
                                    .weight(0.44f)
                                    .fillMaxHeight()
                            ) {
                                VaultNotesListPane(
                                    notesState = notesState,
                                    searchQuery = searchQuery,
                                    selectedCategory = selectedCategory,
                                    activeSection = activeSection,
                                    isGridView = isGridView,
                                    sortOption = sortOption,
                                    syncStatus = syncStatus,
                                    unlockedNoteIds = unlockedNoteIds,
                                    appLockEnabled = userSettings.appLockEnabled && userSettings.appPinHash.isNotBlank(),
                                    onSearchChange = viewModel::updateSearchQuery,
                                    onSelectCategory = viewModel::selectCategory,
                                    onToggleGridView = viewModel::toggleGridView,
                                    onSelectSortOption = viewModel::setSortOption,
                                    onSortNotes = viewModel::sortNotes,
                                    onEmptyTrash = { viewModel.emptyTrash(allNotesList) },
                                    onToggleLanguage = viewModel::toggleLanguage,
                                    onLockNow = viewModel::lockAppNow,
                                    onOpenSecuritySettings = { showSecuritySettings = true },
                                    onNoteClick = { clickedNote ->
                                        if (clickedNote.isLocked && !unlockedNoteIds.contains(clickedNote.id)) {
                                            notePendingUnlock = clickedNote
                                        } else {
                                            editingNote = clickedNote
                                            isEditorOpen = true
                                        }
                                    },
                                    onCreateNewNote = {
                                        editingNote = null
                                        isEditorOpen = true
                                    }
                                )
                            }

                            VerticalDivider(
                                modifier = Modifier.fillMaxHeight(),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            Box(
                                modifier = Modifier
                                    .weight(0.56f)
                                    .fillMaxHeight()
                            ) {
                                if (isEditorOpen) {
                                    NoteEditorScreen(
                                        note = editingNote,
                                        isCompact = false,
                                        onClose = {
                                            isEditorOpen = false
                                            editingNote = null
                                        },
                                        onSave = { title, content, cat, color, pin, fav, locked, pinHash, hint, archived ->
                                            viewModel.saveNote(
                                                existingNote = editingNote,
                                                title = title,
                                                content = content,
                                                baseCategory = cat,
                                                colorIndex = color,
                                                isPinned = pin,
                                                isFavorite = fav,
                                                isLocked = locked,
                                                notePinHash = pinHash,
                                                lockHint = hint,
                                                isArchived = archived,
                                                onComplete = {
                                                    isEditorOpen = false
                                                    editingNote = null
                                                }
                                            )
                                        },
                                        onMoveToTrash = { note -> viewModel.moveNoteToTrash(note) },
                                        onRestoreFromTrash = { note -> viewModel.restoreNoteFromTrash(note) },
                                        onDeletePermanently = { id -> viewModel.deleteNotePermanently(id) }
                                    )
                                } else {
                                    EmptyDetailPanePlaceholder(
                                        onCreateNewNote = {
                                            editingNote = null
                                            isEditorOpen = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Compact Phone Layout with Bottom NavigationBar
                    if (isEditorOpen) {
                        NoteEditorScreen(
                            note = editingNote,
                            isCompact = true,
                            onClose = {
                                isEditorOpen = false
                                editingNote = null
                            },
                            onSave = { title, content, cat, color, pin, fav, locked, pinHash, hint, archived ->
                                viewModel.saveNote(
                                    existingNote = editingNote,
                                    title = title,
                                    content = content,
                                    baseCategory = cat,
                                    colorIndex = color,
                                    isPinned = pin,
                                    isFavorite = fav,
                                    isLocked = locked,
                                    notePinHash = pinHash,
                                    lockHint = hint,
                                    isArchived = archived,
                                    onComplete = {
                                        isEditorOpen = false
                                        editingNote = null
                                    }
                                )
                            },
                            onMoveToTrash = { note -> viewModel.moveNoteToTrash(note) },
                            onRestoreFromTrash = { note -> viewModel.restoreNoteFromTrash(note) },
                            onDeletePermanently = { id -> viewModel.deleteNotePermanently(id) }
                        )
                    } else {
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            contentWindowInsets = WindowInsets.safeDrawing,
                            bottomBar = {
                                VaultBottomNavigationBar(
                                    activeSection = activeSection,
                                    onSelectSection = { viewModel.selectSection(it) }
                                )
                            },
                            floatingActionButton = {
                                if (activeSection != VaultSection.TRASH) {
                                    ExtendedFloatingActionButton(
                                        onClick = {
                                            editingNote = null
                                            isEditorOpen = true
                                        },
                                        icon = {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                        },
                                        text = { Text(strings.newNote) },
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.testTag("create_note_fab")
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                VaultNotesListPane(
                                    notesState = notesState,
                                    searchQuery = searchQuery,
                                    selectedCategory = selectedCategory,
                                    activeSection = activeSection,
                                    isGridView = isGridView,
                                    sortOption = sortOption,
                                    syncStatus = syncStatus,
                                    unlockedNoteIds = unlockedNoteIds,
                                    appLockEnabled = userSettings.appLockEnabled && userSettings.appPinHash.isNotBlank(),
                                    onSearchChange = viewModel::updateSearchQuery,
                                    onSelectCategory = viewModel::selectCategory,
                                    onToggleGridView = viewModel::toggleGridView,
                                    onSelectSortOption = viewModel::setSortOption,
                                    onSortNotes = viewModel::sortNotes,
                                    onEmptyTrash = { viewModel.emptyTrash(allNotesList) },
                                    onToggleLanguage = viewModel::toggleLanguage,
                                    onLockNow = viewModel::lockAppNow,
                                    onOpenSecuritySettings = { showSecuritySettings = true },
                                    onNoteClick = { clickedNote ->
                                        if (clickedNote.isLocked && !unlockedNoteIds.contains(clickedNote.id)) {
                                            notePendingUnlock = clickedNote
                                        } else {
                                            editingNote = clickedNote
                                            isEditorOpen = true
                                        }
                                    },
                                    onCreateNewNote = {
                                        editingNote = null
                                        isEditorOpen = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        notePendingUnlock?.let { lockedNote ->
            UnlockNoteDialog(
                note = lockedNote,
                onDismiss = { notePendingUnlock = null },
                onVerifyPin = { pin ->
                    val ok = viewModel.verifyAndUnlockNote(lockedNote, pin)
                    if (ok) {
                        notePendingUnlock = null
                        editingNote = lockedNote
                        isEditorOpen = true
                    }
                    ok
                },
                onBiometricUnlock = {
                    viewModel.unlockNoteWithBiometric(lockedNote)
                    notePendingUnlock = null
                    editingNote = lockedNote
                    isEditorOpen = true
                }
            )
        }

        if (showSecuritySettings) {
            SecurityAndSettingsDialog(
                appLockEnabled = userSettings.appLockEnabled && userSettings.appPinHash.isNotBlank(),
                hasAppPin = userSettings.appPinHash.isNotBlank(),
                languagePref = languagePref,
                themeMode = themeMode,
                autoLockTimeout = autoLockTimeout,
                allNotes = allNotesList,
                onDismiss = { showSecuritySettings = false },
                onToggleAppLock = { enabled -> viewModel.configureAppLock(enabled) },
                onRequestSetAppPin = {
                    showSecuritySettings = false
                    showSetAppPinDialog = true
                },
                onSelectLanguagePref = { viewModel.setLanguagePreference(it) },
                onSelectThemeMode = { viewModel.setThemeMode(it) },
                onSelectAutoLockTimeout = { viewModel.setAutoLockTimeout(it) },
                onImportJson = { json ->
                    viewModel.importNotesFromJson(json) { count ->
                        viewModel.showMessage(
                            if (strings.isArabic) "تم استيراد $count ملاحظة بنجاح"
                            else "Imported $count notes"
                        )
                    }
                },
                onShowMessage = { viewModel.showMessage(it) },
                onSignOut = {
                    showSecuritySettings = false
                    signOut(context, credentialManager, onSignedOut, scope)
                }
            )
        }

        if (showSetAppPinDialog) {
            SetPinDialog(
                title = strings.setAppPinTitle,
                includeHint = false,
                onDismiss = { showSetAppPinDialog = false },
                onPinConfirmed = { newPin, _ ->
                    viewModel.configureAppLock(enabled = true, newPin = newPin)
                    showSetAppPinDialog = false
                }
            )
        }
    }
}

@Composable
private fun VaultBottomNavigationBar(
    activeSection: VaultSection,
    onSelectSection: (VaultSection) -> Unit
) {
    val strings = LocalVaultStrings.current
    NavigationBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        NavigationBarItem(
            selected = activeSection == VaultSection.NOTES,
            onClick = { onSelectSection(VaultSection.NOTES) },
            icon = { Icon(Icons.AutoMirrored.Outlined.StickyNote2, contentDescription = null) },
            label = { Text(strings.activeViewNotes) },
            modifier = Modifier.testTag("nav_tab_notes")
        )
        NavigationBarItem(
            selected = activeSection == VaultSection.FAVORITES,
            onClick = { onSelectSection(VaultSection.FAVORITES) },
            icon = { Icon(Icons.Default.Star, contentDescription = null) },
            label = { Text(strings.activeViewFavorites) },
            modifier = Modifier.testTag("nav_tab_favorites")
        )
        NavigationBarItem(
            selected = activeSection == VaultSection.ARCHIVE,
            onClick = { onSelectSection(VaultSection.ARCHIVE) },
            icon = { Icon(Icons.Default.Archive, contentDescription = null) },
            label = { Text(strings.activeViewArchive) },
            modifier = Modifier.testTag("nav_tab_archive")
        )
        NavigationBarItem(
            selected = activeSection == VaultSection.TRASH,
            onClick = { onSelectSection(VaultSection.TRASH) },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            label = { Text(strings.activeViewTrash) },
            modifier = Modifier.testTag("nav_tab_trash")
        )
    }
}

@Composable
private fun VaultNavigationRail(
    activeSection: VaultSection,
    onSelectSection: (VaultSection) -> Unit,
    onNewNote: () -> Unit
) {
    val strings = LocalVaultStrings.current
    NavigationRail(
        header = {
            FilledTonalIconButton(
                onClick = onNewNote,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .testTag("rail_create_note_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = strings.newNote)
            }
        }
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        NavigationRailItem(
            selected = activeSection == VaultSection.NOTES,
            onClick = { onSelectSection(VaultSection.NOTES) },
            icon = { Icon(Icons.AutoMirrored.Outlined.StickyNote2, contentDescription = null) },
            label = { Text(strings.activeViewNotes) }
        )
        NavigationRailItem(
            selected = activeSection == VaultSection.FAVORITES,
            onClick = { onSelectSection(VaultSection.FAVORITES) },
            icon = { Icon(Icons.Default.Star, contentDescription = null) },
            label = { Text(strings.activeViewFavorites) }
        )
        NavigationRailItem(
            selected = activeSection == VaultSection.ARCHIVE,
            onClick = { onSelectSection(VaultSection.ARCHIVE) },
            icon = { Icon(Icons.Default.Archive, contentDescription = null) },
            label = { Text(strings.activeViewArchive) }
        )
        NavigationRailItem(
            selected = activeSection == VaultSection.TRASH,
            onClick = { onSelectSection(VaultSection.TRASH) },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            label = { Text(strings.activeViewTrash) }
        )
    }
}

@Composable
private fun VaultNotesListPane(
    notesState: UiState<List<Note>>,
    searchQuery: String,
    selectedCategory: String,
    activeSection: VaultSection,
    isGridView: Boolean,
    sortOption: NoteSortOption,
    syncStatus: SyncStatus,
    unlockedNoteIds: Set<String>,
    appLockEnabled: Boolean,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onToggleGridView: () -> Unit,
    onSelectSortOption: (NoteSortOption) -> Unit,
    onSortNotes: (List<Note>, NoteSortOption) -> List<Note>,
    onEmptyTrash: () -> Unit,
    onToggleLanguage: () -> Unit,
    onLockNow: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onCreateNewNote: () -> Unit
) {
    val strings = LocalVaultStrings.current
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Header Row with Sync Status & Quick Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.appTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    SyncStatusChip(status = syncStatus)
                }
                Text(
                    text = strings.appSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onToggleLanguage,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("main_language_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (strings.isArabic) "EN" else "عربي",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                IconButton(
                    onClick = onToggleGridView,
                    modifier = Modifier.testTag("toggle_grid_list_button")
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                        contentDescription = if (isGridView) strings.viewModeList else strings.viewModeGrid
                    )
                }

                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("sort_notes_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = strings.sortByLabel
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        NoteSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(strings.sortOptionName(option)) },
                                onClick = {
                                    onSelectSortOption(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                if (appLockEnabled) {
                    FilledTonalIconButton(
                        onClick = onLockNow,
                        modifier = Modifier.testTag("quick_lock_vault_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = strings.lockVaultNow
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onOpenSecuritySettings,
                    modifier = Modifier.testTag("open_security_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = strings.securitySettingsTitle
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Full-Text Search Bar (Arabic & English)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(strings.searchPlaceholder) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = strings.clear)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vault_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category Filter Row
        if (activeSection == VaultSection.NOTES || activeSection == VaultSection.FAVORITES) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NoteCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat.key,
                        onClick = { onSelectCategory(cat.key) },
                        label = { Text(strings.categoryName(cat.key)) },
                        modifier = Modifier.testTag("filter_category_${cat.key}")
                    )
                }
            }
        } else if (activeSection == VaultSection.TRASH) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.trashAutoDeleteBanner,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onEmptyTrash,
                        modifier = Modifier.testTag("empty_trash_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.emptyTrashButton)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (notesState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.firebaseErrorDefault,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is UiState.Success -> {
                val filtered = remember(
                    notesState.data,
                    searchQuery,
                    selectedCategory,
                    activeSection,
                    sortOption,
                    unlockedNoteIds
                ) {
                    val rawFiltered = notesState.data.filter { note ->
                        val sectionMatch = when (activeSection) {
                            VaultSection.NOTES -> !note.isArchived && !note.isTrashed
                            VaultSection.FAVORITES -> note.isFavorite && !note.isTrashed
                            VaultSection.ARCHIVE -> note.isArchived && !note.isTrashed
                            VaultSection.TRASH -> note.isTrashed
                        }
                        val matchesCategory = selectedCategory == NoteCategory.ALL.key ||
                            note.baseCategory.equals(selectedCategory, ignoreCase = true)
                        val q = searchQuery.trim()
                        val isNoteAccessible = !note.isLocked || unlockedNoteIds.contains(note.id)
                        val decryptedContent = if (isNoteAccessible) {
                            CryptoManager.decrypt(note.content)
                        } else ""
                        val matchesSearch = q.isEmpty() ||
                            note.title.contains(q, ignoreCase = true) ||
                            (isNoteAccessible && decryptedContent.contains(q, ignoreCase = true))
                        sectionMatch && matchesCategory && matchesSearch
                    }
                    onSortNotes(rawFiltered, sortOption)
                }

                if (filtered.isEmpty()) {
                    EmptyVaultState(
                        activeSection = activeSection,
                        onCreateNewNote = onCreateNewNote
                    )
                } else {
                    val pinnedNotes = filtered.filter { it.isPinned && activeSection == VaultSection.NOTES }
                    val regularNotes = if (activeSection == VaultSection.NOTES) {
                        filtered.filterNot { it.isPinned }
                    } else {
                        filtered
                    }
                    val columns = if (isGridView) StaggeredGridCells.Adaptive(minSize = 165.dp) else StaggeredGridCells.Fixed(1)

                    LazyVerticalStaggeredGrid(
                        columns = columns,
                        verticalItemSpacing = 12.dp,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("notes_staggered_grid")
                    ) {
                        if (pinnedNotes.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                Text(
                                    text = strings.pinnedSection,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(pinnedNotes, key = { "pinned_${it.id}" }) { note ->
                                VaultNoteCard(
                                    note = note,
                                    isUnlockedInSession = unlockedNoteIds.contains(note.id),
                                    onClick = { onNoteClick(note) }
                                )
                            }
                        }

                        if (regularNotes.isNotEmpty()) {
                            if (pinnedNotes.isNotEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Text(
                                        text = strings.allNotesSection,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                            }
                            items(regularNotes, key = { "note_${it.id}" }) { note ->
                                VaultNoteCard(
                                    note = note,
                                    isUnlockedInSession = unlockedNoteIds.contains(note.id),
                                    onClick = { onNoteClick(note) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusChip(status: SyncStatus) {
    val strings = LocalVaultStrings.current
    val icon = when (status) {
        SyncStatus.SYNCED -> Icons.Default.CloudDone
        SyncStatus.SYNCING -> Icons.Default.CloudSync
        SyncStatus.OFFLINE -> Icons.Default.CloudOff
        SyncStatus.ERROR -> Icons.Default.CloudOff
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = strings.syncStatusName(status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun VaultNoteCard(
    note: Note,
    isUnlockedInSession: Boolean,
    onClick: () -> Unit
) {
    val strings = LocalVaultStrings.current
    val isDark = isSystemInDarkTheme()
    val palette = if (isDark) NoteCardDarkColors else NoteCardLightColors
    val cardColor = palette.getOrElse(note.colorIndex.toInt().coerceIn(0, 5)) { palette.first() }
    val showLockedMask = note.isLocked && !isUnlockedInSession

    val parsedMeta = remember(note.id, note.content, showLockedMask) {
        if (showLockedMask) null else NoteRichMetadata.parse(CryptoManager.decrypt(note.content))
    }

    val formattedDate = remember(note.updatedAt, note.createdAt) {
        val ts = note.updatedAt ?: note.createdAt
        ts?.toDate()?.let { date ->
            SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(date)
        } ?: ""
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .testTag("note_card_${note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (note.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFD99B00),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (note.isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (showLockedMask) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.tapToUnlockNote,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (parsedMeta != null) {
                if (parsedMeta.bodyText.isNotBlank()) {
                    Text(
                        text = parsedMeta.bodyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Rich attachment badges (Checklist count, Reminder, Photo, Voice)
                val checklistCount = parsedMeta.checklistItems.size
                val checkedCount = parsedMeta.checklistItems.count { it.isChecked }
                if (checklistCount > 0 || parsedMeta.reminderLabel.isNotBlank() ||
                    parsedMeta.imageUri.isNotBlank() || parsedMeta.voiceMemoLabel.isNotBlank()
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (checklistCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckBox, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("$checkedCount/$checklistCount", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (parsedMeta.reminderLabel.isNotBlank()) {
                            Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        if (parsedMeta.imageUri.isNotBlank()) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        if (parsedMeta.voiceMemoLabel.isNotBlank()) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = strings.categoryName(note.baseCategory),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun EmptyVaultState(
    activeSection: VaultSection,
    onCreateNewNote: () -> Unit
) {
    val strings = LocalVaultStrings.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val w = size.width
            val h = size.height
            drawCircle(
                color = primaryColor.copy(alpha = 0.1f),
                radius = w * 0.48f,
                center = Offset(w / 2f, h / 2f)
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(w * 0.26f, h * 0.22f),
                size = Size(w * 0.48f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
            )
            drawCircle(
                color = secondaryColor,
                radius = w * 0.12f,
                center = Offset(w * 0.66f, h * 0.68f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val title = when (activeSection) {
            VaultSection.NOTES -> strings.emptyNotesTitle
            VaultSection.FAVORITES -> strings.emptyFavoritesTitle
            VaultSection.ARCHIVE -> strings.emptyArchivedTitle
            VaultSection.TRASH -> strings.emptyTrashTitle
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        if (activeSection == VaultSection.NOTES) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = strings.emptyNotesSubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            FilledTonalButton(
                onClick = onCreateNewNote,
                modifier = Modifier.testTag("empty_state_create_note_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.newNote)
            }
        }
    }
}

@Composable
private fun EmptyDetailPanePlaceholder(
    onCreateNewNote: () -> Unit
) {
    val strings = LocalVaultStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.StickyNote2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = strings.selectNotePrompt,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        FilledTonalButton(onClick = onCreateNewNote) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(strings.newNote)
        }
    }
}
