package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.auth.onGoogleSignInClicked
import com.example.ui.i18n.LocalStrings
import com.example.ui.platform.DeviceCredential
import com.example.ui.theme.NoteaPalette
import kotlinx.coroutines.launch

/** Notea mark drawn in code (page + gold fold + emerald lines) so it scales crisply. */
@Composable
fun NoteaMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val pageW = w * 0.62f; val pageH = w * 0.78f
        val left = (w - pageW) / 2; val top = (size.height - pageH) / 2
        val fold = pageW * 0.3f
        val page = Path().apply {
            moveTo(left + 8f, top); lineTo(left + pageW - fold, top); lineTo(left + pageW, top + fold)
            lineTo(left + pageW, top + pageH - 8f); quadraticBezierTo(left + pageW, top + pageH, left + pageW - 8f, top + pageH)
            lineTo(left + 8f, top + pageH); quadraticBezierTo(left, top + pageH, left, top + pageH - 8f)
            lineTo(left, top + 8f); quadraticBezierTo(left, top, left + 8f, top); close()
        }
        drawPath(page, NoteaPalette.Ivory)
        drawPath(Path().apply { moveTo(left + pageW - fold, top); lineTo(left + pageW, top + fold); lineTo(left + pageW - fold, top + fold); close() }, NoteaPalette.Gold)
        val lineH = pageH * 0.055f
        listOf(0.45f to 0.76f, 0.6f to 0.76f, 0.75f to 0.5f).forEach { (y, len) ->
            drawRoundRect(NoteaPalette.Emerald700, Offset(left + pageW * 0.12f, top + pageH * y), Size(pageW * len, lineH), CornerRadius(lineH / 2))
        }
    }
}

@Composable
fun AuthScreen(onToggleLanguage: () -> Unit, onUseWithoutAccount: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, NoteaPalette.Emerald900))
        )
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onToggleLanguage) { Text(s.switchLanguage, color = NoteaPalette.Ivory) }
            }
            Spacer(Modifier.height(48.dp))
            Surface(shape = CircleShape, color = NoteaPalette.Ivory.copy(alpha = 0.08f), modifier = Modifier.size(150.dp)) {
                NoteaMark(Modifier.padding(22.dp).aspectRatio(1f))
            }
            Spacer(Modifier.height(28.dp))
            Text(s.appName, style = MaterialTheme.typography.displayMedium, color = NoteaPalette.Ivory)
            Spacer(Modifier.height(8.dp))
            Text(s.tagline, style = MaterialTheme.typography.titleMedium, color = NoteaPalette.Ivory.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp))
            Spacer(Modifier.height(56.dp))
            Button(
                onClick = {
                    busy = true; error = null
                    onGoogleSignInClicked(context, credentialManager, onAuthSuccess = { busy = false },
                        onAuthError = { busy = false; error = s.signInFailed + ": " + it }, scope = scope, onAuthCancelled = { busy = false })
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = NoteaPalette.Ivory, contentColor = NoteaPalette.Emerald900),
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).heightIn(min = 56.dp)
            ) {
                if (busy) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)); Text(s.signingIn) }
                else Text(s.signInGoogle, style = MaterialTheme.typography.titleMedium)
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = NoteaPalette.GoldSoft, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = onUseWithoutAccount, enabled = !busy,
                border = androidx.compose.foundation.BorderStroke(1.dp, NoteaPalette.Ivory.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).heightIn(min = 56.dp)
            ) { Text(s.useWithoutAccount, color = NoteaPalette.Ivory, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(8.dp))
            Text(s.withoutAccountNote, color = NoteaPalette.Ivory.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 380.dp))
            Spacer(Modifier.height(20.dp))
            Text(s.signInNote, color = NoteaPalette.Ivory.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 380.dp))
        }
    }
}

@Composable
fun AppLockScreen(verify: suspend (String) -> Boolean, onDeviceUnlock: () -> Unit, onSignOut: () -> Unit) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val canDevice = remember { DeviceCredential.available(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (DeviceCredential.isOk(it.resultCode)) onDeviceUnlock()
    }
    LaunchedEffect(pin) {
        if (pin.length >= 4) {
            if (verify(pin)) return@LaunchedEffect
            if (pin.length == 8) { error = true; pin = "" }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onSignOut) { Text(s.signOut) } }
            Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
            }
            Text(s.appLocked, style = MaterialTheme.typography.headlineSmall)
            Text(if (error) s.wrongPin else s.enterPin, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
            }
            // Keypad: digits are always laid out LTR like a phone keypad.
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 320.dp)) {
                    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { row.forEach { d -> Key(d) { if (pin.length < 8) { pin += d; error = false } } } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                            if (canDevice) IconButton(onClick = { DeviceCredential.intent(context, s.appLocked)?.let { launcher.launch(it) } }) {
                                Icon(Icons.Rounded.Fingerprint, s.useDeviceLock, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Key("0") { if (pin.length < 8) { pin += "0"; error = false } }
                        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                            IconButton(onClick = { pin = pin.dropLast(1) }) { Icon(Icons.AutoMirrored.Rounded.Backspace, null) }
                        }
                    }
                }
            }
            if (pin.length >= 4) TextButton(onClick = { scope.launch { if (!verify(pin)) { error = true; pin = "" } } }) { Text(s.confirm) }
        }
    }
}

@Composable
private fun Key(d: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(76.dp)) {
        Text(d, style = MaterialTheme.typography.headlineSmall)
    }
}
