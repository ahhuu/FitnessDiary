package com.cz.fitnessdiary.service

import android.graphics.Bitmap
import android.util.Base64
import com.cz.fitnessdiary.BuildConfig
import com.cz.fitnessdiary.database.entity.ChatMessageEntity
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Direct MiMo client for a private, single-user APK. */
object MiMoService {
    private const val API_URL = "https://api.xiaomimimo.com/v1/chat/completions"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    @JvmStatic
    fun sendMessage(
        message: String,
        systemInstruction: String?,
        image: Bitmap?,
        history: List<ChatMessageEntity>?,
        maxCompletionTokens: Int,
        structuredJson: Boolean,
        callback: AICallback
    ) {
        scope.launch {
            if (BuildConfig.MIMO_API_KEY.isBlank()) {
                withContext(Dispatchers.Main) {
                    callback.onError("未配置 MiMo 本地密钥，请检查 local.properties")
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
                val userContent = JsonArray()
                if (image != null) {
                    userContent.add(JsonObject().apply {
                        addProperty("type", "image_url")
                        add("image_url", JsonObject().apply {
                            addProperty("url", bitmapToDataUrl(image))
                        })
                    })
                }
                userContent.add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", message.take(3200))
                })
                messages.add(JsonObject().apply {
                    addProperty("role", "user")
                    add("content", userContent)
                })

                val requestJson = JsonObject().apply {
                    addProperty("model", "mimo-v2.5")
                    add("messages", messages)
                    addProperty("max_completion_tokens", maxCompletionTokens)
                    addProperty("stream", false)
                    add("thinking", JsonObject().apply { addProperty("type", "disabled") })
                    // MiMo may return multimodal content as an array of text parts.
                    // The prompt already requests JSON; omitting response_format keeps
                    // image recognition compatible with both MiMo response variants.
                }
                val request = Request.Builder()
                    .url(API_URL)
                    .header("api-key", BuildConfig.MIMO_API_KEY)
                    .header("Content-Type", "application/json")
                    .post(gson.toJson(requestJson).toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            callback.onError("MiMo 请求失败（HTTP ${response.code}）")
                        }
                        return@launch
                    }
                    val root = gson.fromJson(responseText, JsonObject::class.java)
                    val choice = root.getAsJsonArray("choices")?.get(0)?.asJsonObject
                    val messageObject = choice?.getAsJsonObject("message")
                    val content = readMessageText(messageObject?.get("content"))
                    val reasoning = readMessageText(messageObject?.get("reasoning_content"))
                    if (content.isBlank() && reasoning.isBlank()) {
                        withContext(Dispatchers.Main) {
                            callback.onError("AI 未返回识别内容，请重试")
                        }
                        return@launch
                    }
                    val usage = root.getAsJsonObject("usage")
                    AiUsageStore.record(
                        "MiMo",
                        usage?.get("prompt_tokens")?.asInt ?: 0,
                        usage?.get("completion_tokens")?.asInt ?: 0,
                        usage?.get("prompt_cache_hit_tokens")?.asInt ?: 0
                    )
                    withContext(Dispatchers.Main) { callback.onSuccess(content, reasoning) }
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError("MiMo 连接失败，请稍后重试")
                }
            }
        }
    }

    private fun readMessageText(element: JsonElement?): String {
        if (element == null || element.isJsonNull) return ""
        if (element.isJsonPrimitive) return element.asString
        if (element.isJsonArray) {
            return element.asJsonArray.joinToString("") { part ->
                if (part.isJsonPrimitive) {
                    part.asString
                } else if (part.isJsonObject) {
                    readMessageText(part.asJsonObject.get("text"))
                } else {
                    ""
                }
            }
        }
        if (element.isJsonObject) {
            return readMessageText(element.asJsonObject.get("text"))
        }
        return ""
    }

    private fun bitmapToDataUrl(source: Bitmap): String {
        val bitmap = scaleBitmap(source, AiRequestPolicy.IMAGE_MAX_SIDE_PX)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, AiRequestPolicy.IMAGE_JPEG_QUALITY, output)
        return "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleBitmap(source: Bitmap, maxSide: Int): Bitmap {
        if (source.width <= maxSide && source.height <= maxSide) return source
        val ratio = source.width.toFloat() / source.height.toFloat()
        val width = if (source.width >= source.height) maxSide else (maxSide * ratio).toInt()
        val height = if (source.height > source.width) maxSide else (maxSide / ratio).toInt()
        return Bitmap.createScaledBitmap(source, width.coerceAtLeast(1), height.coerceAtLeast(1), true)
    }

    private const val DEFAULT_SYSTEM_PROMPT =
        "你是 FitnessDiary 的食物识别助手。结构化请求只输出合法 JSON，不要输出 Markdown。"
}
