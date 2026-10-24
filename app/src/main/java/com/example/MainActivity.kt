package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.data.local.ArabicNotesDatabase
import com.example.data.model.AutoLockTimeout
import com.example.data.model.LanguagePreference
import com.example.data.model.ThemeMode
import com.example.data.repository.VaultRepository
import com.example.ui.i18n.ArabicStrings
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.LocalVaultStrings
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.VaultMainScreen
import com.example.ui.theme.VaultNotesTheme
import com.example.ui.viewmodel.VaultViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

private const val PREFS_NAME = "arabic_notes_prefs"
private const val KEY_LANG_PREF = "lang_pref"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_AUTO_LOCK = "auto_lock_timeout"

@Composable
fun AppRoot(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var localLangPref by remember {
        val saved = prefs.getString(KEY_LANG_PREF, LanguagePreference.ARABIC.name)
        val parsed = LanguagePreference.entries.firstOrNull { it.name == saved } ?: LanguagePreference.ARABIC
        mutableStateOf(parsed)
    }
    var localThemeMode by remember {
        val saved = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        val parsed = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
        mutableStateOf(parsed)
    }
    var localAutoLock by remember {
        val saved = prefs.getString(KEY_AUTO_LOCK, AutoLockTimeout.IMMEDIATELY.name)
        val parsed = AutoLockTimeout.entries.firstOrNull { it.name == saved } ?: AutoLockTimeout.IMMEDIATELY
        mutableStateOf(parsed)
    }

    val isDarkTheme = when (localThemeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    VaultNotesTheme(darkTheme = isDarkTheme) {
        val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
        val user = currentUser

        val resolvedLangCode = VaultViewModel.resolveLanguageCode(localLangPref)
        val strings = if (resolvedLangCode == "ar") ArabicStrings else EnglishStrings
        val layoutDirection = if (resolvedLangCode == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

        if (user == null) {
            CompositionLocalProvider(
                LocalVaultStrings provides strings,
                LocalLayoutDirection provides layoutDirection
            ) {
                AuthScreen(
                    onToggleLanguage = {
                        val next = if (resolvedLangCode == "ar") LanguagePreference.ENGLISH else LanguagePreference.ARABIC
                        localLangPref = next
                        prefs.edit().putString(KEY_LANG_PREF, next.name).apply()
                    },
                    onAuthSuccess = {}
                )
            }
        } else {
            val viewModel: VaultViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        val noteDao = ArabicNotesDatabase.getInstance(app).noteDao()
                        VaultViewModel(
                            repository = VaultRepository(db, noteDao),
                            currentUserId = user.uid,
                            initialLanguagePref = localLangPref,
                            initialThemeMode = localThemeMode,
                            initialTimeout = localAutoLock,
                            onPreferencesPersistLocal = { langPref, theme, timeout ->
                                localLangPref = langPref
                                localThemeMode = theme
                                localAutoLock = timeout
                                prefs.edit()
                                    .putString(KEY_LANG_PREF, langPref.name)
                                    .putString(KEY_THEME_MODE, theme.name)
                                    .putString(KEY_AUTO_LOCK, timeout.name)
                                    .apply()
                            }
                        )
                    }
                }
            )

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, viewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                        Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            VaultMainScreen(
                viewModel = viewModel,
                onSignedOut = {}
            )
        }
    }
}
