package com.cyz.scialmanac.ai

import com.cyz.scialmanac.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class AiClient(private val settings: SettingsRepository) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean get() = settings.apiBaseUrl.isNotBlank() && settings.apiKey.isNotBlank()

    private fun chatRequest(messages: List<Pair<String, String>>, stream: Boolean): Request {
        val body = buildJsonObject {
            put("model", settings.model)
            put("stream", stream)
            putJsonArray("messages") {
                for ((role, content) in messages) {
                    add(buildJsonObject {
                        put("role", role)
                        put("content", content)
                    })
                }
            }
        }.toString()
        return Request.Builder()
            .url(settings.apiBaseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer " + settings.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
    }

    /** Non-streaming chat. Returns message content or throws. */
    suspend fun chat(messages: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        val resp = client.newCall(chatRequest(messages, stream = false)).execute()
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw AiException(summarizeError(resp.code, body))
        val root = json.parseToJsonElement(body).jsonObject
        root["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.content
            ?: throw AiException("响应中没有 choices: " + body.take(300))
    }

    /** Streaming chat: invokes onDelta with each content chunk. Returns full text. */
    suspend fun chatStream(
        messages: List<Pair<String, String>>,
        onDelta: (String) -> Unit,
    ): String = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val full = StringBuilder()
        val source = EventSources.createFactory(client).newEventSource(
            chatRequest(messages, stream = true),
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    if (data == "[DONE]") return
                    try {
                        val root = json.parseToJsonElement(data).jsonObject
                        val delta = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("delta")?.jsonObject?.get("content")
                        val text = (delta as? JsonPrimitive)?.content
                        if (!text.isNullOrEmpty()) {
                            full.append(text)
                            onDelta(full.toString())
                        }
                    } catch (_: Exception) {
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    if (cont.isActive) cont.resumeWith(Result.success(full.toString()))
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                    if (cont.isActive) {
                        val detail = response?.let { summarizeError(it.code, it.body?.string().orEmpty()) }
                        cont.resumeWith(Result.failure(t ?: AiException("流式请求失败" + (detail ?: ""))))
                    }
                }
            }
        )
        cont.invokeOnCancellation { source.cancel() }
    }

    /** 从网关错误体中提取可读信息。 */
    private fun summarizeError(code: Int, body: String): String {
        val msg = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                ?: root["message"]?.jsonPrimitive?.content
        }.getOrNull()
        val clean = msg?.take(200)
        return if (clean != null) "HTTP " + code + ": " + clean else "HTTP " + code + ": " + body.take(200)
    }

    /** Fetch available model ids from the gateway. */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(settings.apiBaseUrl.trimEnd('/') + "/models")
            .addHeader("Authorization", "Bearer " + settings.apiKey)
            .build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw AiException(summarizeError(resp.code, body))
        val root = json.parseToJsonElement(body).jsonObject
        root["data"]?.jsonArray
            ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
            ?.distinct()
            ?.sorted()
            ?: emptyList()
    }

    /** Quick connectivity test. */
    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(settings.apiBaseUrl.trimEnd('/') + "/models")
            .addHeader("Authorization", "Bearer " + settings.apiKey)
            .build()
        val resp = client.newCall(req).execute()
        if (resp.isSuccessful) {
            "连接成功 (HTTP " + resp.code + ")"
        } else {
            throw AiException("HTTP " + resp.code)
        }
    }
}

class AiException(message: String) : Exception(message)
