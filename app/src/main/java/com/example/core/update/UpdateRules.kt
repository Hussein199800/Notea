package com.example.core.update

/** Pure update rules (UpdateRulesTest): versions, versionCode scheme, asset choice, install checks. */
object UpdateRules {
    const val REPO = "Hussein199800/Notea"
    const val PACKAGE = "com.notea.app"
    /** Fixed link that always serves the newest universal APK. */
    const val FIXED_LINK = "https://github.com/$REPO/releases/latest/download/Notea.apk"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    const val CHECK_INTERVAL_HOURS = 12L

    /** ABI digit appended to the versionCode: version×10 + abi (0 = universal). */
    val ABI_CODES = linkedMapOf("armeabi-v7a" to 1, "arm64-v8a" to 2)

    /** "v1.2.0" / "1.2.0" / "1.2" → [1, 2, 0]; null when not a version. */
    fun parse(v: String): List<Int>? {
        val core = v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        val parts = core.split('.')
        if (parts.isEmpty() || parts.size > 4) return null
        return parts.map { it.toIntOrNull() ?: return null }.let { it + List(3 - it.size.coerceAtMost(3)) { 0 } }
    }

    fun isNewer(remote: String, installed: String): Boolean {
        val r = parse(remote) ?: return false
        val i = parse(installed) ?: return true
        for (k in 0 until maxOf(r.size, i.size)) {
            val a = r.getOrElse(k) { 0 }; val b = i.getOrElse(k) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** 1.2.0 → 10200; versionCode = base×10 + abi digit. */
    fun baseCode(versionName: String): Int {
        val p = parse(versionName) ?: error("bad version $versionName")
        return p[0] * 10000 + p.getOrElse(1) { 0 } * 100 + p.getOrElse(2) { 0 }
    }
    fun versionCode(versionName: String, abi: String?): Int = baseCode(versionName) * 10 + (ABI_CODES[abi] ?: 0)

    data class Asset(val name: String, val url: String, val size: Long)

    /** Picks the APK for this phone's ABIs (preferred first), falling back to the universal Notea.apk. */
    fun pickAsset(assets: List<Asset>, deviceAbis: List<String>): Asset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        for (abi in deviceAbis) apks.firstOrNull { it.name.equals("Notea-$abi.apk", ignoreCase = true) }?.let { return it }
        return apks.firstOrNull { it.name.equals("Notea.apk", ignoreCase = true) }
    }

    sealed interface Verdict {
        data object Ok : Verdict
        data object WrongPackage : Verdict
        data object NotNewer : Verdict
        data object SignatureMismatch : Verdict
        data object Unreadable : Verdict
    }

    /** Checks a downloaded APK before handing it to the installer. Signatures are SHA-256 hex of the signing certificates. */
    fun verify(apkPackage: String?, apkVersionCode: Long, apkSigners: Set<String>, installedVersionCode: Long, installedSigners: Set<String>): Verdict = when {
        apkPackage == null -> Verdict.Unreadable
        apkPackage != PACKAGE -> Verdict.WrongPackage
        apkVersionCode <= installedVersionCode -> Verdict.NotNewer
        apkSigners.isEmpty() || installedSigners.isEmpty() || apkSigners.map { it.lowercase() }.toSet() != installedSigners.map { it.lowercase() }.toSet() -> Verdict.SignatureMismatch
        else -> Verdict.Ok
    }
}
