package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.core.Changelog
import com.example.core.Diagnostics
import com.example.ui.components.ConceptIcon
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.PreferenceGroup
import com.example.ui.components.SectionLabel
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.Concept
import com.example.ui.viewmodel.SessionViewModel

private data class TourStep(val icon: ImageVector, val concept: Concept, val title: String, val body: String)

/** First-run tour: 6 short steps, skippable, re-run from «المزيد». */
@Composable
fun TourDialog(onDone: () -> Unit) {
    val s = LocalStrings.current
    val steps = listOf(
        TourStep(Icons.AutoMirrored.Rounded.Notes, Concept.NOTES, s.tour1Title, s.tour1Body),
        TourStep(Icons.Rounded.TouchApp, Concept.NOTES, s.tour2Title, s.tour2Body),
        TourStep(Icons.Rounded.TaskAlt, Concept.TASKS, s.tour3Title, s.tour3Body),
        TourStep(Icons.Rounded.Folder, Concept.FOLDERS, s.tour4Title, s.tour4Body),
        TourStep(Icons.Rounded.Lock, Concept.SECURITY, s.tour5Title, s.tour5Body),
        TourStep(Icons.Rounded.MoreHoriz, Concept.DATA, s.tour6Title, s.tour6Body)
    )
    var i by remember { mutableIntStateOf(0) }
    val step = steps[i]
    AlertDialog(
        onDismissRequest = onDone,
        icon = { ConceptIcon(step.icon, step.concept, size = 56.dp) },
        title = { Text(step.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(step.body, style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    steps.indices.forEach { k ->
                        Box(Modifier.padding(3.dp).size(if (k == i) 10.dp else 7.dp).clip(CircleShape)
                            .background(if (k == i) step.concept.g2 else MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                Text(s.stepOf(i + 1, steps.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = {
            Button(onClick = { if (i < steps.lastIndex) i++ else onDone() }) { Text(if (i < steps.lastIndex) s.next else s.startUsing) }
        },
        dismissButton = {
            if (i > 0) TextButton(onClick = { i-- }) { Text(s.previous) } else TextButton(onClick = onDone) { Text(s.skip) }
        }
    )
}

/** «ما الجديد» — shown once after an update. */
@Composable
fun WhatsNewDialog(onAll: () -> Unit, onDone: () -> Unit) {
    val s = LocalStrings.current
    val r = Changelog.current
    AlertDialog(
        onDismissRequest = onDone,
        icon = { ConceptIcon(Icons.Rounded.NewReleases, Concept.NOTES, size = 48.dp) },
        title = { Text(s.whatsNewTitle(r.version)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (s.isArabic) r.ar else r.en).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text(s.ok) } },
        dismissButton = { TextButton(onClick = onAll) { Text(s.allReleases) } }
    )
}

/** All releases, newest first (plain text from [Changelog]). */
@Composable
fun ReleasesScreen(session: SessionViewModel) {
    val s = LocalStrings.current
    PageScaffold(s.releases, onBack = { session.back() }) {
        Changelog.releases.forEachIndexed { idx, r ->
            item {
                SectionLabel(s.versionLabelShort(r.version) + " · \u2066" + r.date + "\u2069" + if (idx == 0) " · " + s.currentVersion else "")
                PreferenceGroup {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (if (s.isArabic) r.ar else r.en).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

/** Diagnostics log: last 50 events, sensitive data masked, copy or clear. */
@Composable
fun DiagnosticsScreen(session: SessionViewModel) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var lines by remember { mutableStateOf(Diagnostics.read(context)) }
    var confirmClear by remember { mutableStateOf(false) }
    PageScaffold(s.diagnostics, onBack = { session.back() }) {
        item {
            Text(s.diagnosticsIntro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val header = "Notea ${com.example.BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n"
                    clipboard.setText(AnnotatedString(header + lines.joinToString("\n"))); session.message(s.copied)
                }, enabled = lines.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text(s.copyAll)
                }
                OutlinedButton(onClick = { confirmClear = true }, enabled = lines.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text(s.clearLog)
                }
            }
        }
        if (lines.isEmpty()) item { EmptyState(Icons.Rounded.AutoAwesome, s.diagnosticsEmpty, null, Modifier.fillMaxWidth().padding(vertical = 32.dp)) }
        else item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Log lines are English/technical: always left-to-right.
                    lines.asReversed().forEach { Text(it, style = MaterialTheme.typography.bodySmall.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Left, modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
    }
    if (confirmClear) ConfirmDialog(s.clearLog, s.clearLogConfirm, s.clearLog, true, { Diagnostics.clear(context); lines = emptyList() }) { confirmClear = false }
}
