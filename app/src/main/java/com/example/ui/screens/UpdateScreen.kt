package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.core.update.UpdateRules
import com.example.core.update.Updater
import com.example.ui.components.ConceptIcon
import com.example.ui.components.ExpandableText
import com.example.ui.components.PreferenceGroup
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.Concept
import com.example.ui.viewmodel.SessionViewModel

/** Check for updates (manual), download with progress, verify, install. Errors say cause + fix. */
@Composable
fun UpdateScreen(session: SessionViewModel) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val updater = session.container.updater
    val state by updater.state.collectAsState()
    LaunchedEffect(Unit) { if (state is Updater.State.Idle || state is Updater.State.Failed || state is Updater.State.UpToDate) updater.check(manual = true) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        (updater.state.value as? Updater.State.Ready)?.let { r -> if (updater.canInstall()) context.startActivity(updater.installIntent(r.file)) }
    }

    PageScaffold(s.updates, onBack = { updater.reset(); session.back() }) {
        item {
            PreferenceGroup(Modifier.padding(top = 8.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.installedVersion(BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (val st = state) {
                        Updater.State.Idle, Updater.State.Checking -> {
                            CircularProgressIndicator()
                            Text(s.checkingUpdates)
                        }
                        is Updater.State.UpToDate -> {
                            ConceptIcon(Icons.Rounded.TaskAlt, Concept.NOTES)
                            Text(s.upToDate, style = MaterialTheme.typography.titleMedium)
                            Text(s.lastChecked(s.relative(st.checkedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { updater.check() }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text(s.checkAgain) }
                        }
                        is Updater.State.Available -> {
                            ConceptIcon(Icons.Rounded.SystemUpdate, Concept.NOTES)
                            Text(s.updateAvailable(st.release.version.removePrefix("v")), style = MaterialTheme.typography.titleMedium)
                            if (st.release.notes.isNotBlank()) ExpandableText(st.release.notes, collapsedLines = 6, style = MaterialTheme.typography.bodyMedium)
                            st.release.asset?.let { Text(s.downloadSize(it.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Button(onClick = { updater.download(st.release) }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(6.dp)); Text(s.downloadUpdate) }
                        }
                        is Updater.State.Downloading -> {
                            Text(s.downloading(st.release.version.removePrefix("v")), style = MaterialTheme.typography.titleMedium)
                            if (st.total > 0) {
                                LinearProgressIndicator(progress = { (st.done.toFloat() / st.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                Text(s.progressPercent((st.done * 100 / st.total).toInt()) + " · " + s.mb(st.done) + " / " + s.mb(st.total), style = MaterialTheme.typography.bodySmall)
                            } else LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        is Updater.State.Ready -> {
                            ConceptIcon(Icons.Rounded.TaskAlt, Concept.NOTES)
                            Text(s.updateVerified, style = MaterialTheme.typography.titleMedium)
                            ExpandableText(if (updater.canInstall()) s.installHint else s.installPermissionHint, collapsedLines = 4, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = {
                                if (updater.canInstall()) context.startActivity(updater.installIntent(st.file)) else permission.launch(updater.permissionIntent())
                            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (updater.canInstall()) s.installNow else s.allowAndInstall) }
                        }
                        Updater.State.SignatureMismatch -> {
                            ConceptIcon(Icons.Rounded.ErrorOutline, Concept.TRASH)
                            Text(s.signatureMismatchTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            ExpandableText(s.signatureMismatchBody, collapsedLines = 8, style = MaterialTheme.typography.bodyMedium)
                            Text(UpdateRules.FIXED_LINK, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            OutlinedButton(onClick = { uri.openUri(UpdateRules.FIXED_LINK) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(s.openFixedLink) }
                        }
                        is Updater.State.Failed -> {
                            ConceptIcon(if (st.reason == Updater.Reason.OFFLINE) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline, Concept.ARCHIVE)
                            val (cause, fix) = s.updateError(st.reason)
                            Text(cause, style = MaterialTheme.typography.titleMedium)
                            ExpandableText(fix, collapsedLines = 5, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (st.reason != Updater.Reason.DEBUG_BUILD)
                                OutlinedButton(onClick = { updater.check() }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text(s.retry) }
                            OutlinedButton(onClick = { uri.openUri(UpdateRules.RELEASES_PAGE) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(s.openReleasesPage) }
                        }
                    }
                }
            }
        }
        item {
            ExpandableText(s.updateAutoInfo, collapsedLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}
