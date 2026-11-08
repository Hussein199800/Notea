package com.example.core.ai

import android.content.Context
import com.example.core.security.AndroidKeystoreWrapper
import com.example.core.security.DeviceKeyWrapper
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The user's own Gemini API key, stored only on this device and encrypted with Android Keystore.
 * Never synced, never included in backups (see backup rules).
 */
class AiKeyStore(context: Context, private val wrapper: DeviceKeyWrapper = AndroidKeystoreWrapper("notea_ai_kek_v1")) {
    private val prefs = context.getSharedPreferences("notea_ai", Context.MODE_PRIVATE)
    private val aad = "notea-ai-key".toByteArray()

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    /** "Don't ask again on this device" for the send-to-Google confirmation. */
    var rememberConsent: Boolean
        get() = prefs.getBoolean("consent", false)
        set(v) = prefs.edit().putBoolean("consent", v).apply()

    var model: String?
        get() = prefs.getString("model", null)
        set(v) = prefs.edit().putString("model", v).apply()

    fun hasKey() = prefs.contains("key")

    fun key(): String? = prefs.getString("key", null)?.let {
        runCatching { String(wrapper.unwrap(android.util.Base64.decode(it, android.util.Base64.NO_WRAP), aad)) }.getOrNull()
    }

    fun setKey(key: String) {
        val wrapped = wrapper.wrap(key.trim().toByteArray(), aad)
        prefs.edit().putString("key", android.util.Base64.encodeToString(wrapped, android.util.Base64.NO_WRAP)).apply()
    }

    fun clear() { prefs.edit().remove("key").remove("model").putBoolean("enabled", false).putBoolean("consent", false).apply() }
}

sealed class AiError(message: String) : Exception(message) {
    class InvalidKey : AiError("invalid key")
    class RegionUnsupported : AiError("region")
    class Quota : AiError("quota")
    class Offline : AiError("offline")
    class Blocked : AiError("blocked")
    class Other(msg: String) : AiError(msg)
}

/**
 * Minimal Gemini REST client (no SDK). Calls go straight from the phone to Google with the
 * user's key; limits/billing follow the user's own plan (free or paid).
 */
class GeminiClient(private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta") {

    /** Picks the cheapest capable model on this key: newest "flash-lite", else newest "flash". */
    fun pickModel(key: String): String {
        val json = request("GET", "$baseUrl/models?pageSize=200", key, null)
        val models = json.optJSONArray("models") ?: JSONArray()
        val names = (0 until models.length()).map { models.getJSONObject(it) }
            .filter { m -> m.optJSONArray("supportedGenerationMethods")?.let { a -> (0 until a.length()).any { a.getString(it) == "generateContent" } } == true }
            .map { it.getString("name") }
            .filter { it.contains("gemini") && !it.contains("preview") && !it.contains("exp") && !it.contains("image") && !it.contains("tts") && !it.contains("live") }
        fun versionOf(n: String) = Regex("gemini-(\\d+)(?:\\.(\\d+))?").find(n)?.let { it.groupValues[1].toInt() * 100 + (it.groupValues[2].toIntOrNull() ?: 0) } ?: 0
        return names.filter { it.contains("flash-lite") }.maxByOrNull { versionOf(it) }
            ?: names.filter { it.contains("flash") }.maxByOrNull { versionOf(it) }
            ?: names.firstOrNull()
            ?: throw AiError.Other("no model")
    }

    /**
     * One request. [maxOutputTokens] caps the cost; input is capped by callers.
     * When [jsonOutput] is true the model is asked for a JSON object.
     */
    fun generate(key: String, model: String, system: String, user: String, maxOutputTokens: Int = 900, jsonOutput: Boolean = true): String {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", maxOutputTokens).put("temperature", 0.3)
                .apply { if (jsonOutput) put("responseMimeType", "application/json") })
        val res = request("POST", "$baseUrl/$model:generateContent", key, body.toString())
        val cand = res.optJSONArray("candidates")?.optJSONObject(0) ?: throw AiError.Blocked()
        val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: throw AiError.Blocked()
        val text = (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
        if (text.isBlank()) throw AiError.Blocked()
        return text
    }

    /** One request with an image (base64 inline). Plain-text answer. */
    fun generateWithImage(key: String, model: String, system: String, prompt: String, image: ByteArray, mime: String, maxOutputTokens: Int = 4000): String {
        val parts = JSONArray()
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", android.util.Base64.encodeToString(image, android.util.Base64.NO_WRAP))))
            .put(JSONObject().put("text", prompt))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("maxOutputTokens", maxOutputTokens).put("temperature", 0.0))
        val res = request("POST", "$baseUrl/$model:generateContent", key, body.toString())
        val cand = res.optJSONArray("candidates")?.optJSONObject(0) ?: throw AiError.Blocked()
        val out = cand.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        return (0 until out.length()).joinToString("") { out.getJSONObject(it).optString("text") }
    }

    private fun request(method: String, url: String, key: String, body: String?): JSONObject {
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000; readTimeout = 60_000
                setRequestProperty("x-goog-api-key", key)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (body != null) { doOutput = true; outputStream.use { it.write(body.toByteArray()) } }
            }
        } catch (e: IOException) { throw AiError.Offline() }
        val code = try { conn.responseCode } catch (e: IOException) { throw AiError.Offline() }
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code in 200..299) return JSONObject(text)
        val err = runCatching { JSONObject(text).optJSONObject("error") }.getOrNull()
        val status = err?.optString("status").orEmpty()
        val msg = err?.optString("message").orEmpty()
        throw when {
            msg.contains("location is not supported", true) || msg.contains("not available in your country", true) -> AiError.RegionUnsupported()
            code == 429 || status == "RESOURCE_EXHAUSTED" -> AiError.Quota()
            code == 400 && (msg.contains("API key", true) || status == "INVALID_ARGUMENT" && msg.contains("key", true)) -> AiError.InvalidKey()
            code == 401 || code == 403 -> AiError.InvalidKey()
            else -> AiError.Other("$code $status")
        }
    }
}
