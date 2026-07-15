package com.cz.fitnessdiary.service

import com.cz.fitnessdiary.BuildConfig
import com.cz.fitnessdiary.database.entity.ChatMessageEntity
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Direct DeepSeek client for a private, single-user APK. */
object DeepSeekService {
    private const val API_URL = "https://api.deepseek.com/chat/completions"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    @JvmStatic
    fun sendMessage(message: String, systemInstruction: String?, callback: AICallback) {
        sendMessage(message, systemInstruction, false, null, callback)
    }

    @JvmStatic
    fun sendMessage(
        message: String,
        systemInstruction: String?,
        thinking: Boolean,
        history: List<ChatMessageEntity>?,
        callback: AICallback
    ) {
        sendMessageWithPolicy(
            message,
            systemInstruction,
            thinking,
            history,
            if (thinking) AiRequestPolicy.DEEP_THINKING_MAX_COMPLETION_TOKENS
            else AiRequestPolicy.ADVICE_MAX_COMPLETION_TOKENS,
            false,
            callback
        )
    }

    @JvmStatic
    fun sendMessageWithPolicy(
        message: String,
        systemInstruction: String?,
        thinking: Boolean,
        history: List<ChatMessageEntity>?,
        maxCompletionTokens: Int,
        structuredJson: Boolean,
        callback: AICallback
    ) {
        scope.launch {
            if (BuildConfig.DEEPSEEK_API_KEY.isBlank()) {
                withContext(Dispatchers.Main) {
                    callback.onError("未配置 DeepSeek 本地密钥，请检查 local.properties")
                }
                return@launch
            }
            try {
                val messages = JsonArray()
                messages.add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemInstruction ?: DEFAULT_SYSTEM_PROMPT)
                })
                history?.takeLast(AiRequestPolicy.CHAT_MAX_HISTORY_MESSAGES)?.forEach { item ->
                    messages.add(JsonObject().apply {
                        addProperty("role", if (item.isUser) "user" else "assistant")
                        addProperty("content", item.content?.take(700) ?: "")
                    })
                }
                messages.add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", message.take(AiRequestPolicy.CHAT_MAX_HISTORY_CHARS))
                })

                val requestJson = JsonObject().apply {
                    addProperty("model", if (thinking) "deepseek-v4-pro" else "deepseek-v4-flash")
                    add("messages", messages)
                    addProperty("max_completion_tokens", maxCompletionTokens)
                    addProperty("stream", false)
                    if (thinking) {
                        add("thinking", JsonObject().apply { addProperty("type", "enabled") })
                    }
                    if (structuredJson) {
                        add("response_format", JsonObject().apply {
                            addProperty("type", "json_object")
                        })
                    }
                }
                val request = Request.Builder()
                    .url(API_URL)
                    .header("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
                    .header("Content-Type", "application/json")
                    .post(gson.toJson(requestJson).toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            callback.onError("DeepSeek 请求失败（HTTP ${response.code}）")
                        }
                        return@launch
                    }
                    val root = gson.fromJson(responseText, JsonObject::class.java)
                    val choice = root.getAsJsonArray("choices")?.get(0)?.asJsonObject
                    val messageObject = choice?.getAsJsonObject("message")
                    val content = messageObject?.get("content")?.asString.orEmpty()
                    val reasoning = messageObject?.get("reasoning_content")?.asString
                    val usage = root.getAsJsonObject("usage")
                    AiUsageStore.record(
                        "DeepSeek",
                        usage?.get("prompt_tokens")?.asInt ?: 0,
                        usage?.get("completion_tokens")?.asInt ?: 0,
                        usage?.get("prompt_cache_hit_tokens")?.asInt ?: 0
                    )
                    withContext(Dispatchers.Main) { callback.onSuccess(content, reasoning) }
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError("DeepSeek 连接失败，请稍后重试")
                }
            }
        }
    }

    private const val DEFAULT_SYSTEM_PROMPT =
        "你是 FitnessDiary 的中文健身与营养助手，请简洁、准确地回答。"
}
