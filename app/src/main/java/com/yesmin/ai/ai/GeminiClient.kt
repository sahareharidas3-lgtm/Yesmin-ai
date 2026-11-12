package com.yesmin.ai.ai

import com.yesmin.ai.data.ChatMessage
import com.yesmin.ai.data.SecureStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ApiStatus(
    val connected: Boolean? = null,       // null = untested
    val message: String = "Key not tested yet",
    val log: List<String> = emptyList()
)

sealed class AiResult {
    data class Ok(val text: String, val sources: List<String> = emptyList()) : AiResult()
    data class Err(val message: String, val http: Int = 0) : AiResult()
}

/**
 * Talks directly to the Gemini REST API (generativelanguage.googleapis.com).
 * The key is sent in the x-goog-api-key header (never in the URL, so it is not logged in URLs).
 */
class GeminiClient(private val secure: SecureStore) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val base = "https://generativelanguage.googleapis.com/v1beta"
    private val keyName = "gemini_api_key"

    val status = MutableStateFlow(ApiStatus())

    fun saveKey(k: String) {
        secure.putSecret(keyName, k.trim())
        status.value = ApiStatus(null, "Key saved. Tap 'Test connection'.", status.value.log)
    }
    fun clearKey() {
        secure.remove(keyName)
        status.value = ApiStatus(null, "No key", status.value.log)
    }
    private fun key(): String? = secure.getSecret(keyName)?.takeIf { it.isNotBlank() }
    fun hasKey() = key() != null
    fun keyTail(): String = key()?.takeLast(4)?.let { "••••••••$it" } ?: ""

    private fun logErr(msg: String): AiResult.Err {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        status.value = status.value.copy(connected = false, message = msg, log = (listOf("$t  $msg") + status.value.log).take(10))
        return AiResult.Err(msg)
    }

    suspend fun generate(
        model: String, system: String, history: List<ChatMessage>, search: Boolean
    ): AiResult = withContext(Dispatchers.IO) {
        val key = key() ?: return@withContext logErr("No API key. Add it in Settings → Gemini API.")
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", JSONArray().apply {
                history.forEach { m ->
                    put(JSONObject().put("role", if (m.isUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", m.text))))
                }
            })
            put("generationConfig", JSONObject().put("temperature", 0.9).put("maxOutputTokens", 2048))
            if (search) put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        }
        val req = Request.Builder()
            .url("$base/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            http.newCall(req).await().use { resp ->
                val txt = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = friendly(resp.code, txt)
                    // Some models reject the search tool: retry once without it.
                    if (search && resp.code == 400 && txt.contains("search", true)) {
                        return@withContext generate(model, system, history, false)
                    }
                    return@use logErr(msg).copy(http = resp.code)
                }
                parse(txt, model)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            logErr("Network problem: ${e.message ?: "no internet?"}")
        } catch (e: Exception) {
            logErr("Unexpected error: ${e.message}")
        }
    }

    suspend fun test(model: String): AiResult =
        generate(model, "Reply with exactly the word: OK", listOf(ChatMessage(0, "user", "ping", 0)), false)

    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        val key = key() ?: return@withContext Result.failure(IllegalStateException("No API key saved"))
        try {
            val req = Request.Builder().url("$base/models?pageSize=200").header("x-goog-api-key", key).build()
            http.newCall(req).await().use { resp ->
                val txt = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@use Result.failure<List<String>>(IllegalStateException(friendly(resp.code, txt)))
                val arr = JSONObject(txt).optJSONArray("models") ?: JSONArray()
                val out = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val name = m.optString("name").removePrefix("models/")
                    val methods = m.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
                    val bad = listOf("embedding", "tts", "image", "live", "audio", "aqa", "imagen", "veo")
                    if (name.contains("gemini") && methods.contains("generateContent") && bad.none { name.contains(it) }) out += name
                }
                Result.success(out.sorted())
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    private fun parse(txt: String, model: String): AiResult {
        val j = JSONObject(txt)
        val c = j.optJSONArray("candidates")
        if (c == null || c.length() == 0) {
            val br = j.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            return logErr(if (br.isNotBlank()) "Request blocked by Gemini safety filter ($br)" else "Gemini returned an empty answer")
        }
        val cand = c.getJSONObject(0)
        val parts = cand.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        if (sb.isBlank()) return logErr("Empty answer (finishReason: ${cand.optString("finishReason")})")
        val sources = mutableListOf<String>()
        val chunks = cand.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
        if (chunks != null) for (i in 0 until chunks.length()) {
            chunks.getJSONObject(i).optJSONObject("web")?.optString("title")?.takeIf { it.isNotBlank() }?.let { sources += it }
        }
        status.value = status.value.copy(connected = true, message = "Connected ✓ ($model)")
        return AiResult.Ok(sb.toString().trim(), sources.distinct().take(4))
    }

    private fun friendly(code: Int, body: String): String {
        val api = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrDefault(body.take(160))
        val hint = when (code) {
            400 -> "Bad request or invalid API key"
            401, 403 -> "API key rejected or not allowed"
            404 -> "Model not found - pick another model"
            429 -> "Quota / rate limit reached - wait a bit"
            in 500..599 -> "Google server problem - try again"
            else -> "Error"
        }
        return "$hint (HTTP $code): $api"
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { c ->
    c.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (c.isActive) c.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { c.resume(response) }
    })
}
