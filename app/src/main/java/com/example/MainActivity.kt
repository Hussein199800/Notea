package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.example.ui.NoteaRoot
import com.example.ui.viewmodel.IncomingIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

class MainActivity : ComponentActivity() {
    private val incoming = MutableStateFlow<IncomingIntent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        val container = (application as NoteaApp).container
        setContent {
            NoteaRoot(container, incoming.filterNotNull()) { incoming.value = null }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        incoming.value = when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> IncomingIntent.SharedFiles(listOf(stream))
                    !text.isNullOrBlank() -> IncomingIntent.SharedText(intent.getStringExtra(Intent.EXTRA_SUBJECT), text)
                    else -> null
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                val texts = intent.getStringArrayListExtra(Intent.EXTRA_TEXT).orEmpty()
                when {
                    uris.isNotEmpty() -> IncomingIntent.SharedFiles(uris)
                    texts.isNotEmpty() -> IncomingIntent.SharedText(null, texts.joinToString("\n\n---\n\n"))
                    else -> null
                }
            }
            Intent.ACTION_VIEW -> intent.data?.let { IncomingIntent.SharedFiles(listOf(it)) }
            ACTION_OPEN_NOTE -> intent.getStringExtra(EXTRA_NOTE_ID)?.let { IncomingIntent.OpenNote(it) }
            ACTION_NEW_NOTE -> IncomingIntent.NewNote
            else -> null
        }
    }

    companion object {
        const val ACTION_OPEN_NOTE = "com.example.notea.OPEN_NOTE"
        const val ACTION_NEW_NOTE = "com.example.notea.NEW_NOTE"
        const val EXTRA_NOTE_ID = "noteId"
    }
}
