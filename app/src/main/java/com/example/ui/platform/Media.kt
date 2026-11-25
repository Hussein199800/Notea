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
        file = f
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        recorder = r
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(f.absolutePath)
        r.prepare(); r.start()
        startedAt = System.currentTimeMillis()
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
        file = null
        return try {
            r.stop()
            if (f != null && f.length() > 0) f to duration else {
                f?.delete()
                null
            }
        } catch (_: Exception) {
            f?.delete()
            null
        } finally {
            runCatching { r.release() }
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
    private var onStateChange: ((Boolean) -> Unit)? = null
    var playingPath: String? = null
        private set

    fun toggle(file: File, onStateChange: (Boolean) -> Unit, onError: () -> Unit) {
        if (playingPath == file.path) {
            stop()
            return
        }
        stop()
        try {
            val playback = MediaPlayer()
            player = playback
            this.onStateChange = onStateChange
            playingPath = file.path
            playback.apply {
                setDataSource(file.path)
                setOnPreparedListener { if (player === it) it.start() }
                setOnCompletionListener { if (player === it) stop() }
                setOnErrorListener { source, _, _ ->
                    if (player === source) {
                        stop()
                        onError()
                    }
                    true
                }
                prepareAsync()
            }
            onStateChange(true)
        } catch (_: Exception) {
            stop()
            onError()
        }
    }

    fun stopIfPlaying(path: String?) {
        if (path != null && playingPath == path) stop()
    }

    fun stop() {
        val oldPlayer = player
        val callback = onStateChange
        player = null
        onStateChange = null
        playingPath = null
        oldPlayer?.release()
        callback?.invoke(false)
    }
}

object FileOpener {
    fun open(context: Context, file: File, mime: String, displayName: String): Boolean = try {
        val name = displayName.substringAfterLast('/').substringAfterLast('\\').ifBlank { file.name }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file, name)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "application/octet-stream" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
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
