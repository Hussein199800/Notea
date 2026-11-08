package com.example.core.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class LanguagePreference { ARABIC, ENGLISH, SYSTEM }
enum class AutoLockTimeout(val minutes: Int) { IMMEDIATELY(0), ONE_MINUTE(1), FIVE_MINUTES(5), FIFTEEN_MINUTES(15) }
enum class NoteSort { MODIFIED, CREATED, TITLE, COLOR }

data class AppPrefs(
    val language: LanguagePreference = LanguagePreference.ARABIC,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val autoLock: AutoLockTimeout = AutoLockTimeout.IMMEDIATELY,
    val gridView: Boolean = true,
    val sort: NoteSort = NoteSort.MODIFIED,
    val autoBackup: Boolean = true,
    /** Text size multiplier: 0.9 / 1.0 / 1.15 / 1.3. */
    val textScale: Float = 1f
)

/** Device-local preferences. Keeps the v1 keys (`lang_pref`, `theme_mode`, `auto_lock_timeout`). */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("arabic_notes_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppPrefs> = _state.asStateFlow()

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        prefs.getString(key, null)?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

    private fun read() = AppPrefs(
        language = enumPref("lang_pref", LanguagePreference.ARABIC),
        theme = enumPref("theme_mode", ThemeMode.SYSTEM),
        autoLock = enumPref("auto_lock_timeout", AutoLockTimeout.IMMEDIATELY),
        gridView = prefs.getBoolean("grid_view", true),
        sort = enumPref("sort", NoteSort.MODIFIED),
        autoBackup = prefs.getBoolean("auto_backup", true),
        textScale = prefs.getFloat("text_scale", 1f).coerceIn(0.8f, 1.5f)
    )

    fun update(change: (AppPrefs) -> AppPrefs) {
        val n = change(_state.value)
        prefs.edit()
            .putString("lang_pref", n.language.name)
            .putString("theme_mode", n.theme.name)
            .putString("auto_lock_timeout", n.autoLock.name)
            .putBoolean("grid_view", n.gridView)
            .putString("sort", n.sort.name)
            .putBoolean("auto_backup", n.autoBackup)
            .putFloat("text_scale", n.textScale)
            .apply()
        _state.value = n
    }

    val deviceId: String
        get() = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }

    // App lock is per account.
    fun appLockHash(uid: String): String = prefs.getString("app_pin_$uid", "") ?: ""
    fun appLockEnabled(uid: String): Boolean = prefs.getBoolean("app_lock_$uid", false) && appLockHash(uid).isNotBlank()
    fun hasLocalLockSettings(uid: String) = prefs.contains("app_lock_$uid")
    fun setAppLock(uid: String, enabled: Boolean, hash: String) {
        prefs.edit().putBoolean("app_lock_$uid", enabled).putString("app_pin_$uid", hash).apply()
    }

    /** User chose to use Notea without an account (data stays on this device). */
    fun localMode(): Boolean = prefs.getBoolean("local_mode", false)
    fun setLocalMode(on: Boolean) = prefs.edit().putBoolean("local_mode", on).apply()

    /** First-run migration wizard was shown (or skipped) for this account. */
    fun onboardingDone(uid: String) = prefs.getBoolean("onboarding_$uid", false)
    fun setOnboardingDone(uid: String) = prefs.edit().putBoolean("onboarding_$uid", true).apply()

    /** Memory suggestions hidden today: "yyyyDDD:id1,id2". */
    fun dismissedMemory(today: String): Set<String> = prefs.getString("memory_dismissed", "")!!.let {
        if (it.substringBefore(':') == today) it.substringAfter(':').split(',').filter { s -> s.isNotBlank() }.toSet() else emptySet()
    }
    fun dismissMemory(today: String, id: String) {
        prefs.edit().putString("memory_dismissed", today + ":" + (dismissedMemory(today) + id).joinToString(",")).apply()
    }

    fun legacyUpgradeDone(): Boolean = prefs.getBoolean("legacy_upgrade_v2", false)
    fun setLegacyUpgradeDone() = prefs.edit().putBoolean("legacy_upgrade_v2", true).apply()
}
