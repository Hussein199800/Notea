package com.example.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.core.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** GitHub releases updater: check, download (with progress), verify package/version/signature, install. */
class Updater(private val context: Context, private val scope: CoroutineScope) {
    data class Release(val version: String, val notes: String, val asset: UpdateRules.Asset?)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class UpToDate(val checkedAt: Long) : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val done: Long, val total: Long) : State
        data class Ready(val release: Release, val file: File) : State
        data object SignatureMismatch : State
        data class Failed(val reason: Reason) : State
    }
    enum class Reason { OFFLINE, NO_RELEASE, NO_APK, SERVER, STORAGE, WRONG_PACKAGE, NOT_NEWER, UNREADABLE, DEBUG_BUILD }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null
    private val prefs get() = context.getSharedPreferences("notea_update", Context.MODE_PRIVATE)
    val lastCheck: Long get() = prefs.getLong("last_check", 0L)

    val isOfficialBuild get() = context.packageName == UpdateRules.PACKAGE

    fun check(manual: Boolean = true) {
        if (job?.isActive == true) return
        if (!isOfficialBuild) { _state.value = State.Failed(Reason.DEBUG_BUILD); return }
        job = scope.launch(Dispatchers.IO) {
            _state.value = State.Checking
            _state.value = runCatching { fetchLatest() }.fold(
                onSuccess = { r ->
                    prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
                    when {
                        r == null -> State.Failed(Reason.NO_RELEASE)
                        !UpdateRules.isNewer(r.version, BuildConfig.VERSION_NAME) -> State.UpToDate(System.currentTimeMillis())
                        r.asset == null -> State.Failed(Reason.NO_APK)
                        else -> State.Available(r)
                    }
                },
                onFailure = { e ->
                    if (manual) Diagnostics.log(context, "update", "Update check failed", e)
                    State.Failed(if (e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException) Reason.OFFLINE else Reason.SERVER)
                }
            )
        }
    }

    /** Background check (every 12h): returns the newer release, if any, without touching the UI state. */
    suspend fun checkQuietly(): Release? = withContext(Dispatchers.IO) {
        if (!isOfficialBuild) return@withContext null
        runCatching { fetchLatest() }.getOrNull()?.also { prefs.edit().putLong("last_check", System.currentTimeMillis()).apply() }
            ?.takeIf { it.asset != null && UpdateRules.isNewer(it.version, BuildConfig.VERSION_NAME) }
    }

    private fun fetchLatest(): Release? {
        val c = (URL("https://api.github.com/repos/${UpdateRules.REPO}/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Notea-Android")
        }
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val arr = j.optJSONArray("assets")
            val assets = (0 until (arr?.length() ?: 0)).map { i -> arr!!.getJSONObject(i).let { a ->
                UpdateRules.Asset(a.getString("name"), a.getString("browser_download_url"), a.optLong("size")) } }
            return Release(j.optString("tag_name"), j.optString("body"), UpdateRules.pickAsset(assets, Build.SUPPORTED_ABIS.toList()))
        } finally { c.disconnect() }
    }

    fun download(release: Release) {
        val asset = release.asset ?: return
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val part = File(dir, "Notea-${release.version}.apk.part")
            val apk = File(dir, "Notea-${release.version}.apk")
            try {
                var url = URL(asset.url)
                var conn: HttpURLConnection
                var hops = 0
                while (true) {   // GitHub redirects to its CDN
                    conn = (url.openConnection() as HttpURLConnection).apply { instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 30_000; setRequestProperty("User-Agent", "Notea-Android") }
                    if (conn.responseCode in 300..399 && hops++ < 5) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue }
                    break
                }
                if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: asset.size
                if (dir.usableSpace in 1 until total * 2) { _state.value = State.Failed(Reason.STORAGE); return@launch }
                var done = 0L
                conn.inputStream.use { inp -> part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = inp.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        _state.value = State.Downloading(release, done, total)
                    }
                } }
                conn.disconnect()
                if (!part.renameTo(apk)) throw java.io.IOException("rename")
                val v = verify(apk)
                _state.value = when (v) {
                    UpdateRules.Verdict.Ok -> State.Ready(release, apk)
                    UpdateRules.Verdict.SignatureMismatch -> { apk.delete(); State.SignatureMismatch }
                    UpdateRules.Verdict.WrongPackage -> { apk.delete(); State.Failed(Reason.WRONG_PACKAGE) }
                    UpdateRules.Verdict.NotNewer -> { apk.delete(); State.Failed(Reason.NOT_NEWER) }
                    UpdateRules.Verdict.Unreadable -> { apk.delete(); State.Failed(Reason.UNREADABLE) }
                }
                if (_state.value !is State.Ready) Diagnostics.log(context, "update", "Downloaded APK rejected: ${v::class.simpleName}")
            } catch (e: Exception) {
                part.delete()
                Diagnostics.log(context, "update", "Download failed", e)
                _state.value = State.Failed(if (e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.ConnectException) Reason.OFFLINE else Reason.SERVER)
            }
        }
    }

    private fun verify(apk: File): UpdateRules.Verdict {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
        val installed = pm.getPackageInfo(context.packageName, flags)
        return UpdateRules.verify(archive?.packageName, archive?.let { versionCode(it) } ?: 0, archive?.let { signers(it) } ?: emptySet(),
            versionCode(installed), signers(installed))
    }

    private fun versionCode(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()

    private fun signers(p: PackageInfo): Set<String> {
        val certs = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.let { si -> if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory }
        else @Suppress("DEPRECATION") p.signatures
        return certs.orEmpty().map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    /** True when Android lets us open the installer; otherwise the caller sends the user to the permission screen first. */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun permissionIntent(): Intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + context.packageName))

    fun reset() { if (job?.isActive != true) _state.value = State.Idle }
}
