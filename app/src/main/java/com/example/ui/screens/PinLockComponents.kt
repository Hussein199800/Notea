package com.example.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.NoteBackupSerializer
import com.example.data.model.AutoLockTimeout
import com.example.data.model.LanguagePreference
import com.example.data.model.Note
import com.example.data.model.ThemeMode
import com.example.ui.i18n.LocalVaultStrings

@Composable
fun AppLockScreen(
    onVerifyPin: (String) -> Boolean,
    onBiometricUnlockSuccess: () -> Unit,
    onToggleLanguage: () -> Unit,
    onSignOut: () -> Unit
) {
    val strings = LocalVaultStrings.current
    val context = LocalContext.current
    var enteredPin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    val deviceCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onBiometricUnlockSuccess()
        }
    }

    val canUseDeviceCredential = remember(context) {
        val kg = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        kg?.isDeviceSecure == true
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("lock_screen_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = strings.signOut,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.signOut)
                }

                FilledTonalButton(
                    onClick = onToggleLanguage,
                    modifier = Modifier.testTag("lock_screen_language_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (strings.isArabic) "English" else "العربية")
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 400.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = strings.appLockedTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = strings.appLockedSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (canUseDeviceCredential) {
                    FilledTonalButton(
                        onClick = {
                            val kg = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                            @Suppress("DEPRECATION")
                            val intent = kg?.createConfirmDeviceCredentialIntent(
                                strings.appTitle,
                                strings.appLockedSubtitle
                            )
                            if (intent != null) {
                                deviceCredentialLauncher.launch(intent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("biometric_unlock_button")
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.unlockWithBiometricOrDevice)
                    }
                }

                PinDotsIndicator(
                    pinLength = enteredPin.length,
                    hasError = hasError
                )

                if (hasError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.invalidPinError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                NumericPinPad(
                    onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
                            hasError = false
                            val next = enteredPin + digit
                            enteredPin = next
                            if (next.length == 4) {
                                val unlocked = onVerifyPin(next)
                                if (!unlocked) {
                                    hasError = true
                                    enteredPin = ""
                                }
                            }
                        }
                    },
                    onBackspaceClick = {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                            hasError = false
                        }
                    },
                    onClearClick = {
                        enteredPin = ""
                        hasError = false
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun PinDotsIndicator(
    pinLength: Int,
    hasError: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val filled = index < pinLength
            val color = when {
                hasError -> MaterialTheme.colorScheme.error
                filled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun NumericPinPad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val strings = LocalVaultStrings.current
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { rowDigits ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                rowDigits.forEach { digit ->
                    PinKeyButton(
                        label = digit,
                        onClick = { onDigitClick(digit) },
                        testTag = "pin_key_$digit"
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClearClick)
                    .testTag("pin_key_clear")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = strings.clear,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            PinKeyButton(
                label = "0",
                onClick = { onDigitClick("0") },
                testTag = "pin_key_0"
            )

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBackspaceClick)
                    .testTag("pin_key_backspace")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = strings.clear,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PinKeyButton(
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 1.dp,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun UnlockNoteDialog(
    note: Note,
    onDismiss: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    onBiometricUnlock: () -> Unit
) {
    val strings = LocalVaultStrings.current
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    val deviceCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onBiometricUnlock()
        }
    }

    val canUseDeviceCredential = remember(context) {
        val kg = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        kg?.isDeviceSecure == true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = strings.unlockNoteTitle,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (note.cleanLockHint().isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.hintPrefix + note.cleanLockHint(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                if (canUseDeviceCredential) {
                    FilledTonalButton(
                        onClick = {
                            val kg = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                            @Suppress("DEPRECATION")
                            val intent = kg?.createConfirmDeviceCredentialIntent(
                                strings.unlockNoteTitle,
                                note.title
                            )
                            if (intent != null) {
                                deviceCredentialLauncher.launch(intent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.unlockWithBiometricOrDevice)
                    }
                }

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { value ->
                        val digits = value.filter { it.isDigit() }.take(4)
                        pinInput = digits
                        hasError = false
                    },
                    label = { Text(strings.enterPinPrompt) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = hasError,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("unlock_note_pin_input")
                )
                if (hasError) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.invalidPinError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinInput.length < 4) {
                        hasError = true
                    } else {
                        val ok = onVerifyPin(pinInput)
                        if (!ok) {
                            hasError = true
                        }
                    }
                },
                modifier = Modifier.testTag("unlock_note_confirm_button")
            ) {
                Text(strings.unlockNoteAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun SetPinDialog(
    title: String,
    includeHint: Boolean,
    initialHint: String = "",
    onDismiss: () -> Unit,
    onPinConfirmed: (pin: String, hint: String) -> Unit
) {
    val strings = LocalVaultStrings.current
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf(initialHint) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter { ch -> ch.isDigit() }.take(4)
                        errorText = null
                    },
                    label = { Text(strings.enterNewPinPrompt) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_pin_first_input")
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        confirmPin = it.filter { ch -> ch.isDigit() }.take(4)
                        errorText = null
                    },
                    label = { Text(strings.confirmPinPrompt) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("set_pin_confirm_input")
                )

                if (includeHint) {
                    OutlinedTextField(
                        value = hint,
                        onValueChange = { hint = it.take(100) },
                        label = { Text(strings.pinHintLabel) },
                        placeholder = { Text(strings.pinHintPlaceholder) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("set_pin_hint_input")
                    )
                }

                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        pin.length < 4 -> errorText = strings.pinTooShortError
                        pin != confirmPin -> errorText = strings.pinMismatchError
                        else -> onPinConfirmed(pin, hint.trim())
                    }
                },
                modifier = Modifier.testTag("set_pin_save_button")
            ) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityAndSettingsDialog(
    appLockEnabled: Boolean,
    hasAppPin: Boolean,
    languagePref: LanguagePreference,
    themeMode: ThemeMode,
    autoLockTimeout: AutoLockTimeout,
    allNotes: List<Note>,
    onDismiss: () -> Unit,
    onToggleAppLock: (Boolean) -> Unit,
    onRequestSetAppPin: () -> Unit,
    onSelectLanguagePref: (LanguagePreference) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectAutoLockTimeout: (AutoLockTimeout) -> Unit,
    onImportJson: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val strings = LocalVaultStrings.current
    val context = LocalContext.current
    var showImportField by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = strings.securitySettingsTitle,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Language Selection (Arabic, English, System)
                Text(
                    text = strings.languageSectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LanguagePreference.entries.forEach { pref ->
                        FilterChip(
                            selected = languagePref == pref,
                            onClick = { onSelectLanguagePref(pref) },
                            label = { Text(strings.languagePrefName(pref)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                HorizontalDivider()

                // 2. Theme Selection (System, Light, Dark)
                Text(
                    text = strings.themeSectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onSelectThemeMode(mode) },
                            label = { Text(strings.themeModeName(mode)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DarkMode,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                HorizontalDivider()

                // 3. App Lock & Biometric / PIN Security
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.appLockToggleLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.appLockToggleDesc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = appLockEnabled,
                                onCheckedChange = { checked ->
                                    if (checked && !hasAppPin) {
                                        onRequestSetAppPin()
                                    } else {
                                        onToggleAppLock(checked)
                                    }
                                },
                                modifier = Modifier.testTag("app_lock_switch")
                            )
                        }

                        FilledTonalButton(
                            onClick = onRequestSetAppPin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("change_app_pin_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.changeAppPinButton)
                        }

                        Text(
                            text = strings.autoLockTimeoutLabel,
                            style = MaterialTheme.typography.labelLarge
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AutoLockTimeout.entries.forEach { timeout ->
                                FilterChip(
                                    selected = autoLockTimeout == timeout,
                                    onClick = { onSelectAutoLockTimeout(timeout) },
                                    label = { Text(strings.timeoutName(timeout)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // 4. Backup & Restore (JSON / Plain Text)
                Text(
                    text = strings.backupSectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val json = NoteBackupSerializer.exportToJson(allNotes)
                            copyAndShareBackup(context, json, "ArabicNotes_Backup.json", strings.copiedToast, onShowMessage)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_json_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.exportJsonButton)
                    }

                    OutlinedButton(
                        onClick = {
                            val txt = NoteBackupSerializer.exportToPlainText(allNotes, strings.isArabic)
                            copyAndShareBackup(context, txt, "ArabicNotes_Backup.txt", strings.copiedToast, onShowMessage)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_text_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.exportTextButton)
                    }
                }

                FilledTonalButton(
                    onClick = { showImportField = !showImportField },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toggle_import_json_button")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.importJsonButton)
                }

                if (showImportField) {
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        placeholder = { Text(strings.importJsonPlaceholder) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (importJsonText.isNotBlank()) {
                                onImportJson(importJsonText)
                                showImportField = false
                                importJsonText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(strings.confirm)
                    }
                }

                HorizontalDivider()

                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.signOut)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.confirm)
            }
        }
    )
}

private fun copyAndShareBackup(
    context: Context,
    content: String,
    title: String,
    toastMessage: String,
    onShowMessage: (String) -> Unit
) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(title, content))
        onShowMessage(toastMessage)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(sendIntent, title))
    } catch (_: Exception) {
        onShowMessage(toastMessage)
    }
}
