package com.cz.fitnessdiary.service

import com.cz.fitnessdiary.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DeepSeek AI 服务 - 标准 OpenAI 接口实现
 */
object DeepSeekService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)
    private const val API_URL = "https://api.deepseek.com/chat/completions"

    @JvmStatic
    @JvmOverloads
    fun sendMessage(message: String, systemInstruction: String? = null, thinking: Boolean = false, callback: AICallback) {
        scope.launch {
            try {
                val modelName = if (thinking) "deepseek-reasoner" else "deepseek-chat"
                val finalSystemPrompt = systemInstruction ?: "你是健身日记（FitnessDiary）应用的 AI 助手，身份是一位专业的健身教练和营养师。" +
                        "在回答食物营养价值时，请务必以 ### [食物名] 开头，并明确列出 [热量]、[蛋白质]、[碳水] 等数值（以每100g为标准）。" +
                        "请始终使用中文回答。"

                val jsonRequest = JsonObject().apply {
                    addProperty("model", modelName)
                    add("messages", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("role", "system")
                            addProperty("content", finalSystemPrompt)
                        })
                        add(JsonObject().apply {
                            addProperty("role", "user")
                            addProperty("content", message)
                        })
                    })
                    addProperty("stream", false)
                }

                val body = gson.toJson(jsonRequest).toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        android.util.Log.e("DeepSeekService", "API Error: ${response.code} $errorBody")
                        withContext(Dispatchers.Main) {
                            callback.onError("DeepSeek 错误: ${response.code} ${response.message}")
                        }
                        return@launch
                    }

                    val responseBody = response.body?.string() ?: ""
                    val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                    val choice = jsonResponse.getAsJsonArray("choices")?.get(0)?.asJsonObject
                    val messageObj = choice?.getAsJsonObject("message")
                    
                    val content = messageObj?.get("content")?.asString ?: ""
                    val reasoning = messageObj?.get("reasoning_content")?.asString

                    withContext(Dispatchers.Main) {
                        callback.onSuccess(content, reasoning)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError("DeepSeek 连接失败: ${e.message}")
                }
            }
        }
    }
}
