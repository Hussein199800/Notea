package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.NoteaApp
import com.example.ui.editor.MarkdownEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class WidgetNote(val id: String, val title: String, val preview: String)

/** Home-screen widget: quick "new note" plus the most recent pinned/edited notes. Locked notes never show content. */
class NoteaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = (context.applicationContext as NoteaApp).container
        val uid = c.currentUid()
        val arabic = c.isArabic()
        val notes = if (uid == null) emptyList() else c.db.noteDao().getAllWithDetails(uid)
            .filter { it.note.trashedAt == null && !it.note.isArchived }
            .sortedWith(compareByDescending<com.example.data.local.NoteWithDetails> { it.note.isPinned }.thenByDescending { it.note.updatedAt })
            .take(8)
            .map {
                WidgetNote(
                    it.note.id,
                    it.note.title.ifBlank { if (arabic) "بلا عنوان" else "Untitled" },
                    if (it.note.isLocked) (if (arabic) "🔒 ملاحظة مقفلة" else "🔒 Locked note") else MarkdownEngine.plain(it.note.body).take(240).replace('\n', ' ')
                )
            }
        provideContent { GlanceTheme { Content(context, notes, arabic) } }
    }

    @Composable
    private fun Content(context: Context, notes: List<WidgetNote>, arabic: Boolean) {
        val ivory = ColorProvider(Color(0xFFFAF6EC))
        val emerald = ColorProvider(Color(0xFF0E5A43))
        val ink = ColorProvider(Color(0xFF1D1B16))
        val soft = ColorProvider(Color(0xFF4A473D))
        Column(GlanceModifier.fillMaxSize().background(ivory).cornerRadius(24.dp).padding(12.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Notea", style = TextStyle(color = emerald, fontWeight = FontWeight.Bold, fontSize = 18.sp), modifier = GlanceModifier.defaultWeight())
                Box(
                    GlanceModifier.background(emerald).cornerRadius(18.dp).padding(horizontal = 14.dp, vertical = 6.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_NEW_NOTE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)))
                ) { Text(if (arabic) "+ ملاحظة" else "+ Note", style = TextStyle(color = ivory, fontWeight = FontWeight.Bold)) }
            }
            Spacer(GlanceModifier.height(8.dp))
            if (notes.isEmpty()) {
                Text(if (arabic) "لا توجد ملاحظات بعد" else "No notes yet", style = TextStyle(color = soft))
            } else LazyColumn {
                items(notes, itemId = { it.id.hashCode().toLong() }) { n ->
                    Column(
                        GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)
                            .clickable(actionStartActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_NOTE)
                                .putExtra(MainActivity.EXTRA_NOTE_ID, n.id).setData(android.net.Uri.parse("notea://note/${n.id}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)))
                    ) {
                        Text(n.title, style = TextStyle(color = ink, fontWeight = FontWeight.Medium), maxLines = 2)
                        if (n.preview.isNotBlank()) Text(n.preview, style = TextStyle(color = soft, fontSize = 12.sp), maxLines = 3)
                    }
                }
            }
        }
    }
}

class NoteaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoteaWidget()
}

object NoteaWidgetUpdater {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    /** Debounced refresh after local changes. */
    fun requestUpdate(context: Context) {
        job?.cancel()
        job = scope.launch {
            delay(1500)
            runCatching {
                if (GlanceAppWidgetManager(context).getGlanceIds(NoteaWidget::class.java).isNotEmpty()) NoteaWidget().updateAll(context)
            }
        }
    }
}
