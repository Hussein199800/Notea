package com.example.ui.platform

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Records AAC voice notes into the cache; the file is then stored as a note attachment. */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording get() = recorder != null

    fun start(): Boolean = try {
        val f = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        recorder = r; file = f; startedAt = System.currentTimeMillis()
        true
    } catch (e: Exception) {
        cancel(); false
    }

    /** Returns the recorded file and its duration, or null if nothing usable was recorded. */
    fun stop(): Pair<File, Long>? {
        val r = recorder ?: return null
        val f = file
        val duration = System.currentTimeMillis() - startedAt
        recorder = null
        return try {
            r.stop(); r.release()
            if (f != null && f.length() > 0) f to duration else null
        } catch (e: Exception) {
            r.release(); f?.delete(); null
        }
    }

    fun cancel() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null; file?.delete(); file = null
    }
}

/** Single shared player for voice notes. */
object AudioPlayer {
    private var player: MediaPlayer? = null
    var playingPath: String? = null
        private set

    fun toggle(file: File, onStateChange: (Boolean) -> Unit) {
        if (playingPath == file.path) { stop(); onStateChange(false); return }
        stop()
        try {
            player = MediaPlayer().apply {
                setDataSource(file.path)
                setOnCompletionListener { stop(); onStateChange(false) }
                prepare(); start()
            }
            playingPath = file.path
            onStateChange(true)
        } catch (_: Exception) {
            stop(); onStateChange(false)
        }
    }

    fun stop() {
        try { player?.stop() } catch (_: Exception) {}
        player?.release(); player = null; playingPath = null
    }
}

object FileOpener {
    fun open(context: Context, file: File, mime: String): Boolean {
        val uri = runCatching { FileProvider.getUriForFile(context, context.packageName + ".files", file) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true } catch (_: ActivityNotFoundException) { false }
    }

    fun openUrl(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Exception) { false }

    fun shareText(context: Context, title: String, text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

object DeviceCredential {
    fun available(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

    @Suppress("DEPRECATION")
    fun intent(context: Context, title: String): Intent? =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.createConfirmDeviceCredentialIntent(title, null)

    fun isOk(resultCode: Int) = resultCode == Activity.RESULT_OK
}

/**
 * Fetches a link's title/description (OpenGraph or <title>). Runs only when the user explicitly
 * adds a link card; only the URL itself is contacted and no note content is sent.
 */
object LinkPreviewFetcher {
    data class Preview(val title: String, val description: String)

    fun fetch(url: String): Preview? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000; readTimeout = 6000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) NoteaLinkPreview")
            setRequestProperty("Accept", "text/html")
        }
        conn.inputStream.use { s ->
            val buf = ByteArray(256 * 1024)
            var total = 0
            while (total < buf.size) { val r = s.read(buf, total, buf.size - total); if (r < 0) break; total += r }
            val html = String(buf, 0, total, Charsets.UTF_8)
            fun meta(p: String) = Regex("<meta[^>]+(?:property|name)=[\"']$p[\"'][^>]+content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: Regex("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+(?:property|name)=[\"']$p[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            val title = meta("og:title") ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)
            val desc = meta("og:description") ?: meta("description")
            val dec = com.example.data.importer.HtmlConverter::decodeEntities
            Preview(dec(title.orEmpty()).trim().take(300), dec(desc.orEmpty()).trim().take(600))
        }
    } catch (_: Exception) { null }
}
