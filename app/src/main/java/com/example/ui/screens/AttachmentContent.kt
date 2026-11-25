package com.example.ui.screens

import android.text.format.Formatter
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.local.AttachmentEntity
import com.example.data.local.AttachmentKind
import com.example.data.repository.AttachmentStore
import com.example.ui.components.ExpandableText
import com.example.ui.i18n.LocalStrings
import com.example.ui.platform.AudioPlayer
import com.example.ui.platform.FileOpener
import com.example.ui.viewmodel.EditorViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AttachmentContent(vm: EditorViewModel, attachment: AttachmentEntity, noteLocked: Boolean, readOnly: Boolean) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mime = if (attachment.kind == AttachmentKind.PDF) "application/pdf"
        else AttachmentStore.mimeFor(attachment.displayName, attachment.mimeType)
    val locked = noteLocked || attachment.encrypted
    val isImage = attachment.kind == AttachmentKind.IMAGE
    val isPdf = attachment.kind == AttachmentKind.PDF || mime == "application/pdf"
    val isAudio = attachment.kind == AttachmentKind.AUDIO || mime.startsWith("audio/")
    val isLink = attachment.kind == AttachmentKind.LINK
    val canOpen = !locked || isPdf || isImage || isAudio || isLink
    var working by remember(attachment.id) { mutableStateOf(false) }
    var playing by remember(attachment.id) { mutableStateOf(false) }
    var playingPath by remember(attachment.id) { mutableStateOf<String?>(null) }
    var pdf by remember(attachment.id, locked) { mutableStateOf<File?>(null) }
    var imageOpen by remember(attachment.id, locked) { mutableStateOf(false) }

    fun message(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun open() {
        if (isLink) {
            if (!FileOpener.openUrl(context, attachment.url)) message(strings.noAppToOpen)
            return
        }
        if (isImage) {
            imageOpen = true
            return
        }
        if (working) return
        if (playing) {
            AudioPlayer.stopIfPlaying(playingPath)
            return
        }
        scope.launch {
            working = true
            try {
                val file = vm.viewableFile(attachment)
                when {
                    file == null -> message(strings.attachmentUnavailable)
                    isPdf -> pdf = file
                    isAudio -> {
                        playingPath = file.path
                        AudioPlayer.toggle(file, { playing = it }) { message(strings.attachmentUnavailable) }
                    }
                    !FileOpener.open(context, file, mime, attachment.displayName) -> message(strings.noAppToOpen)
                }
            } finally {
                working = false
            }
        }
    }

    if (isAudio) DisposableEffect(attachment.id) {
        onDispose { AudioPlayer.stopIfPlaying(playingPath) }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { destination ->
        if (destination != null) scope.launch {
            working = true
            try {
                message(if (vm.exportAttachment(attachment, destination)) strings.attachmentSaved else strings.attachmentSaveFailed)
            } finally {
                working = false
            }
        }
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = canOpen && !working, onClick = ::open)
                    .padding(end = if (readOnly) 0.dp else 36.dp, bottom = if (isImage) 8.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when {
                        isAudio -> if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow
                        isPdf -> Icons.Rounded.PictureAsPdf
                        isLink -> Icons.Rounded.Link
                        else -> Icons.Rounded.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f)) {
                    ExpandableText(
                        if (isLink) attachment.linkTitle.ifBlank { attachment.url } else attachment.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        collapsedLines = 2
                    )
                    val detail = when {
                        isLink -> attachment.linkDescription.ifBlank { attachment.url }
                        isAudio && attachment.durationMs > 0 ->
                            "%d:%02d".format(attachment.durationMs / 60000, (attachment.durationMs / 1000) % 60)
                        else -> Formatter.formatShortFileSize(context, attachment.sizeBytes)
                    }
                    ExpandableText(detail, collapsedLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (isImage) ImageAttachment(vm, attachment, locked, onClick = ::open)
            if (!isLink) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isAudio && canOpen) TextButton(enabled = !working, onClick = ::open) { Text(strings.openFile) }
                TextButton(enabled = !working, onClick = {
                    val name = attachment.displayName.substringAfterLast('/').substringAfterLast('\\').ifBlank { strings.file }
                    runCatching { export.launch(name) }.onFailure { message(strings.attachmentSaveFailed) }
                }) { Text(strings.saveToFiles) }
            }
        }
    }
    pdf?.let { file ->
        val openExternally: (() -> Unit)? = if (locked) null else ({
            if (!FileOpener.open(context, file, mime, attachment.displayName)) message(strings.noAppToOpen)
        })
        PdfAttachmentReader(
            file, attachment.displayName, onDismiss = { pdf = null }, onExternalOpen = openExternally,
            restoreFile = { vm.viewableFile(attachment) }
        )
    }
    if (imageOpen) {
        AlertDialog(
            onDismissRequest = { imageOpen = false },
            title = { ExpandableText(attachment.displayName, collapsedLines = 2,
                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content)) },
            text = { ImageAttachment(vm, attachment, locked) },
            confirmButton = { TextButton(onClick = { imageOpen = false }) { Text(strings.close) } }
        )
    }
}

@Composable
private fun ImageAttachment(vm: EditorViewModel, attachment: AttachmentEntity, locked: Boolean, onClick: (() -> Unit)? = null) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    var loading by remember(attachment.id, attachment.localPath, locked) { mutableStateOf(true) }
    val file by produceState<File?>(null, attachment.id, attachment.localPath, locked, attachment.encrypted) {
        value = null
        value = vm.viewableFile(attachment)
        loading = false
    }
    val request = remember(file, locked) {
        file?.let {
            ImageRequest.Builder(context).data(it).apply {
                if (locked) {
                    memoryCachePolicy(CachePolicy.DISABLED)
                    diskCachePolicy(CachePolicy.DISABLED)
                }
            }.build()
        }
    }
    var failed by remember(request) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 420.dp).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            file == null || failed -> Text(strings.attachmentUnavailable, Modifier.padding(12.dp))
            else -> AsyncImage(
                model = request,
                contentDescription = attachment.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
                onError = { failed = true }
            )
        }
    }
}
