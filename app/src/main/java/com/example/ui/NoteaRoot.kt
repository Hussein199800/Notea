package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.AppContainer
import com.example.auth.authStateFlow
import com.example.core.prefs.LanguagePreference
import com.example.core.prefs.ThemeMode
import com.example.data.importer.ImportSource
import com.example.data.repository.NoteDraft
import com.example.ui.i18n.LocalStrings
import com.example.ui.i18n.Strings
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BackupScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImportCenterScreen
import com.example.ui.screens.ImportPreviewScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NoteaTheme
import com.example.ui.viewmodel.DataViewModel
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.IncomingIntent
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.SessionViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun NoteaRoot(container: AppContainer, incoming: Flow<IncomingIntent>, consumeIncoming: () -> Unit) {
    val prefs by container.settings.state.collectAsState()
    val dark = when (prefs.theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val arabic = when (prefs.language) {
        LanguagePreference.ARABIC -> true; LanguagePreference.ENGLISH -> false
        LanguagePreference.SYSTEM -> Locale.getDefault().language == "ar"
    }
    val strings = remember(arabic) { Strings(arabic) }
    val auth = remember { if (container.firebaseAvailable) runCatching { Firebase.auth }.getOrNull() else null }
    var localMode by remember { mutableStateOf(container.settings.localMode()) }
    fun setLocal(on: Boolean) { container.settings.setLocalMode(on); localMode = on }
    val user by (auth?.authStateFlow() ?: flowOf(null)).collectAsStateWithLifecycle(initialValue = auth?.currentUser)

    NoteaTheme(dark) {
      com.example.ui.theme.ScaledLayout(prefs.textScale) {
        CompositionLocalProvider(LocalStrings provides strings, LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                CrashReportDialog()
                val u = user
                val signIn: (() -> Unit)? = if (auth != null) ({ setLocal(false) }) else null
                when {
                    u != null -> LinkLocalData(container, u.uid) {
                        SessionContent(container, u.uid, u.email, incoming, consumeIncoming, null)
                    }
                    // No Firebase in this build, or the user chose "use without an account".
                    auth == null || localMode -> SessionContent(container, com.example.LOCAL_UID, null, incoming, consumeIncoming, signIn)
                    else -> AuthScreen(
                        onToggleLanguage = {
                            container.settings.update { it.copy(language = if (arabic) LanguagePreference.ENGLISH else LanguagePreference.ARABIC) }
                        },
                        onUseWithoutAccount = { setLocal(true) }
                    )
                }
            }
        }
      }
    }
}

@Composable
private fun SessionContent(container: AppContainer, uid: String, email: String?, incoming: Flow<IncomingIntent>, consumeIncoming: () -> Unit, onSignIn: (() -> Unit)?) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session: SessionViewModel = viewModel(key = "session-$uid", factory = viewModelFactory { initializer { SessionViewModel(container, uid) } })
    val home: HomeViewModel = viewModel(key = "home-$uid", factory = viewModelFactory { initializer { HomeViewModel(container, uid) } })
    val data: DataViewModel = viewModel(key = "data-$uid", factory = viewModelFactory { initializer { DataViewModel(container, uid) } })
    val stack by session.stack.collectAsState()
    val unlocked by session.unlocked.collectAsState()
    val folders by home.folders.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var pendingDraft by remember { mutableStateOf<Pair<String, NoteDraft>?>(null) }

    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, session) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> { session.onForeground(); session.startRealtimeSync() }
                Lifecycle.Event.ON_STOP -> { session.onBackground(); session.stopRealtimeSync() }
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(session) {
        session.messages.collect { m ->
            val r = snackbar.showSnackbar(m.text, actionLabel = if (m.undo != null) s.undo else null, withDismissAction = m.undo != null,
                duration = if (m.undo != null) androidx.compose.material3.SnackbarDuration.Long else androidx.compose.material3.SnackbarDuration.Short)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) m.undo?.invoke()
        }
    }
    LaunchedEffect(session) { session.maybeShowWelcome() }

    // Share sheet / widget / notification entry points.
    LaunchedEffect(incoming) {
        incoming.collect { intent ->
            when (intent) {
                is IncomingIntent.SharedText -> {
                    val c = container.importManager.previewSharedText(intent.subject, intent.text)
                    home.createNote({ id -> NoteDraft(id = id, title = c.title, body = c.body, checklist = c.checklist, importSource = ImportSource.SHARE.key) }) { id ->
                        session.home(); session.navigate(Screen.Editor(id)); session.message(s.sharedNoteCreated)
                    }
                }
                is IncomingIntent.SharedFiles -> data.previewFiles(ImportSource.SHARE, intent.uris, s.analyzing) { session.home(); session.navigate(Screen.ImportPreview) }
                is IncomingIntent.OpenNote -> {
                    val note = container.db.noteDao().getNote(intent.noteId)
                    if (note != null && (!note.isLocked || session.isOpened(note.id))) { session.home(); session.navigate(Screen.Editor(note.id)) } else session.home()
                }
                IncomingIntent.OpenUpdate -> { session.home(); session.navigate(Screen.Update) }
                IncomingIntent.NewNote -> home.createNote({ NoteDraft(id = it) }) { id -> session.home(); session.navigate(Screen.Editor(id, isNew = true)) }
                is IncomingIntent.NewNoteWithText -> home.createNote({ NoteDraft(id = it, title = intent.title, body = intent.body) }) { id -> session.navigate(Screen.Editor(id)) }
            }
            consumeIncoming()
        }
    }

    if (!unlocked) {
        AppLockScreen(verify = { session.verifyAppPin(it) }, onDeviceUnlock = { session.unlockWithDeviceCredential() }, onSignOut = {
            session.signOut { com.example.auth.signOut(context, CredentialManager.create(context), {}, scope) }
        })
        return
    }

    BackHandler(enabled = stack.size > 1) { session.back() }
    val top = stack.last()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        // Rail when the screen is wider than tall (phone landscape) or a tablet; bottom bar otherwise.
        val railNav = maxWidth >= 600.dp || maxWidth > maxHeight
        val listWidth = maxWidth * 0.42f
        val editorTop = top as? Screen.Editor
        val openNote: (String, Boolean) -> Unit = { id, isNew ->
            if (editorTop != null) session.replaceTop(Screen.Editor(id, isNew)) else session.navigate(Screen.Editor(id, isNew))
        }
        if (wide && (top is Screen.Home || editorTop != null)) {
            // Tablet / foldable: list + editor side by side.
            Row(Modifier.fillMaxSize()) {
              com.example.ui.screens.TabsChrome(session, home, rail = true) {
                Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(listWidth).fillMaxHeight()) { com.example.ui.screens.TabContent(session, home, editorTop?.noteId, openNote) }
                VerticalDivider()
                Box(Modifier.fillMaxSize()) {
                    if (editorTop != null) EditorHost(container, session, uid, editorTop, folders)
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        com.example.ui.screens.NoteaMark(Modifier.width(120.dp).fillMaxHeight(0.2f))
                    }
                }
                }
              }
            }
        } else {
            AnimatedContent(top, transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 12 }) togetherWith fadeOut()
            }, contentKey = { it::class to ((it as? Screen.Editor)?.noteId ?: "") }, label = "nav") { screen ->
                when (screen) {
                    Screen.Home -> com.example.ui.screens.TabsChrome(session, home, rail = railNav) { com.example.ui.screens.TabContent(session, home, null, openNote) }
                    is Screen.Editor -> EditorHost(container, session, uid, screen, folders)
                    Screen.Settings -> SettingsScreen(session, email, onSignOut = {
                        session.signOut { com.example.auth.signOut(context, CredentialManager.create(context), {}, scope) }
                    }, onSignIn = onSignIn)
                    Screen.ImportCenter -> ImportCenterScreen(session, data)
                    Screen.ImportPreview -> ImportPreviewScreen(session, data)
                    Screen.Backup -> BackupScreen(session, data)
                    Screen.About -> com.example.ui.screens.AboutScreen { session.back() }
                    is Screen.Welcome -> com.example.ui.screens.WelcomeScreen(session, screen.firstRun) { session.markOnboarded(); session.back() }
                    Screen.Transfer -> com.example.ui.screens.TransferScreen(session, data)
                    Screen.AssistantSettings -> com.example.ui.screens.AssistantSettingsScreen(session)
                    Screen.Releases -> com.example.ui.screens.ReleasesScreen(session)
                    Screen.Diagnostics -> com.example.ui.screens.DiagnosticsScreen(session)
                    Screen.Update -> com.example.ui.screens.UpdateScreen(session)
                }
            }
        }
        val help by session.help.collectAsState()
        if (top is Screen.Home) when (help) {
            SessionViewModel.HelpPrompt.TOUR -> com.example.ui.screens.TourDialog { session.dismissHelp() }
            SessionViewModel.HelpPrompt.WHATS_NEW -> com.example.ui.screens.WhatsNewDialog(onAll = { session.dismissHelp(); session.navigate(Screen.Releases) }) { session.dismissHelp() }
            null -> Unit
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
            .padding(bottom = if (top is Screen.Home && !railNav && !wide) 80.dp else 0.dp))
    }
}

@Composable
private fun EditorHost(container: AppContainer, session: SessionViewModel, uid: String, screen: Screen.Editor, folders: List<com.example.data.local.FolderEntity>) {
    val owner = remember(screen.noteId) {
        object : androidx.lifecycle.ViewModelStoreOwner { override val viewModelStore = session.editorStore(screen.noteId) }
    }
    val vm: EditorViewModel = viewModel(viewModelStoreOwner = owner, key = "editor-${screen.noteId}", factory = viewModelFactory {
        initializer { EditorViewModel(container, uid, screen.noteId) }
    })
    EditorScreen(session, vm, folders, startScan = screen.startScan, onOpenNote = { id -> session.navigate(Screen.Editor(id)) }) { session.back() }
}

/**
 * After signing in, moves notes created without an account into that account (once). Nothing is
 * deleted: if linking can't finish (offline, wrong vault passphrase) the user can retry later.
 */
@Composable
private fun LinkLocalData(container: AppContainer, uid: String, content: @Composable () -> Unit) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    var state by remember(uid) { mutableStateOf<com.example.data.repository.AccountLinker.Result?>(null) }
    var skipped by remember(uid) { mutableStateOf(false) }
    var working by remember(uid) { mutableStateOf(true) }
    var doneMessage by remember(uid) { mutableStateOf<String?>(null) }
    fun run(pass: CharArray? = null) = scope.launch {
        working = true
        val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { container.accountLinker.link(uid, pass) }.getOrElse { com.example.data.repository.AccountLinker.Result.NeedsInternet }
        }
        pass?.fill(' ')
        state = r; working = false
        if (r is com.example.data.repository.AccountLinker.Result.Done) doneMessage = s.linkDone(r.notes)
    }
    androidx.compose.runtime.LaunchedEffect(uid) { run() }
    val r = state
    when {
        skipped || r is com.example.data.repository.AccountLinker.Result.Done || r == com.example.data.repository.AccountLinker.Result.NothingToLink -> {
            content()
            doneMessage?.let { msg -> androidx.compose.runtime.LaunchedEffect(msg) { kotlinx.coroutines.delay(300); doneMessage = null } ; LinkToast(msg) }
        }
        working -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.CircularProgressIndicator()
                Text(s.linkingTitle, Modifier.padding(24.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
        r == com.example.data.repository.AccountLinker.Result.NeedsAccountPassphrase || r == com.example.data.repository.AccountLinker.Result.WrongPassphrase ->
            com.example.ui.components.PassphraseDialog(
                create = false, title = s.vaultTitle,
                body = s.linkNeedsPassphrase + if (r == com.example.data.repository.AccountLinker.Result.WrongPassphrase) "\n" + s.wrongPassphrase else "",
                submit = { p -> run(p); true }, onSuccess = {}, onDismiss = { skipped = true }
            )
        else -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { skipped = true },
            text = { com.example.ui.components.ExpandableText(s.linkNeedsInternet) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { run() }) { Text(s.retry) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { skipped = true }) { Text(s.later) } }
        )
    }
}

@Composable
private fun LinkToast(msg: String) {
    val ctx = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(msg) { android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show() }
}

/** Shows the reason of the last unexpected close once, with a share button. */
@Composable
private fun CrashReportDialog() {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    var report by remember { mutableStateOf(com.example.core.CrashReporter.pending(ctx)) }
    val r = report ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { com.example.core.CrashReporter.markSeen(ctx); report = null },
        title = { Text(s.crashTitle) },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                com.example.ui.components.ExpandableText(s.crashBody, collapsedLines = 4)
                androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    com.example.ui.components.ExpandableText(r, collapsedLines = 8, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                com.example.ui.platform.FileOpener.shareText(ctx, "Notea crash report", r)
                com.example.core.CrashReporter.markSeen(ctx); report = null
            }) { Text(s.shareReport) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { com.example.core.CrashReporter.markSeen(ctx); report = null }) { Text(s.close) } }
    )
}
