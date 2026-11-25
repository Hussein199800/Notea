package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.ExpandableText
import com.example.ui.i18n.LocalStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@Composable
internal fun PdfAttachmentReader(
    file: File, name: String, onDismiss: () -> Unit, onExternalOpen: (() -> Unit)?, restoreFile: suspend () -> File?
) {
    val strings = LocalStrings.current
    var index by remember(file) { mutableIntStateOf(0) }
    var total by remember(file) { mutableIntStateOf(0) }
    val page by produceState<Result<RenderedPage>?>(null, file, index) {
        value = null
        val result = withContext(Dispatchers.IO) {
            try {
                val source = file.takeIf { it.isFile } ?: restoreFile() ?: throw IOException("Could not open PDF")
                Result.success(renderPage(source, index))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        result.getOrNull()?.let { total = it.count }
        value = result
    }
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpandableText(
                    name,
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                    collapsedLines = 2
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val bitmap = page?.getOrNull()?.bitmap
                    when {
                        page == null -> CircularProgressIndicator()
                        bitmap == null -> Text(strings.attachmentUnavailable)
                        else -> key(index) { PdfPageImage(bitmap, strings.pdfPage(index + 1, total)) }
                    }
                }
                if (total > 0) Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val rendered = page?.isSuccess == true
                    IconButton(onClick = { index-- }, enabled = rendered && index > 0) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, strings.previousPage)
                    }
                    Text(strings.pdfPage(index + 1, total), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { index++ }, enabled = rendered && index < total - 1) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, strings.nextPage)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (onExternalOpen != null) TextButton(onClick = onExternalOpen) { Text(strings.openInAnotherApp) }
                    TextButton(onClick = onDismiss) { Text(strings.close) }
                }
            }
        }
    }
}

@Composable
private fun PdfPageImage(bitmap: Bitmap, description: String) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var position by remember { mutableStateOf(Offset.Zero) }
    val gestures = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 4f)
        val fit = minOf(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
        val maxX = ((bitmap.width * fit * scale - viewport.width) / 2).coerceAtLeast(0f)
        val maxY = ((bitmap.height * fit * scale - viewport.height) / 2).coerceAtLeast(0f)
        position = Offset(
            (position.x + pan.x).coerceIn(-maxX, maxX),
            (position.y + pan.y).coerceIn(-maxY, maxY)
        )
    }
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }.transformable(gestures)) {
        Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = description,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = position.x
                translationY = position.y
            },
            contentScale = ContentScale.Fit
        )
    }
}

private data class RenderedPage(val bitmap: Bitmap, val count: Int)

private fun renderPage(file: File, index: Int): RenderedPage {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = try {
        PdfRenderer(descriptor)
    } catch (e: Exception) {
        descriptor.close()
        throw e
    }
    return renderer.use { document ->
        document.openPage(index).use { page ->
            val scale = 2000f / maxOf(page.width, page.height)
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).toInt().coerceAtLeast(1),
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                RenderedPage(bitmap, document.pageCount)
            } catch (e: Exception) {
                bitmap.recycle()
                throw e
            }
        }
    }
}
