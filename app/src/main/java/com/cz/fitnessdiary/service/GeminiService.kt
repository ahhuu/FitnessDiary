package com.cz.fitnessdiary.service

import com.cz.fitnessdiary.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gemini AI 服务 - Kotlin 实现
 */
object GeminiService {
    private val config = generationConfig {
        temperature = 0.7f
        topK = 40
        topP = 0.95f
        maxOutputTokens = 2048
    }

    private val model = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = config,
        systemInstruction = content {
            text("你是健身日记（FitnessDiary）应用的 AI 助手，身份是一位专业的健身教练和营养师。 " +
                 "你的任务是为用户提供科学的运动建议、饮食指导以及情绪激励。 " +
                 "回答风格应专业、严谨且富有亲和力。如果用户问非健身/健康相关的问题，请礼貌地引导回健康话题。 " +
                 "在回答食物营养价值时，请务必包含：### 名称、热量（每100g/大卡）、蛋白质（g）、碳水（g）等明确标识，方便解析。 " +
                 "请始终使用中文回答。")
        }
    )

    private var chat = model.startChat()
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    /**
     * 为 Java 提供的异步发送方法
     * @param systemInstruction 可选的自定义系统指令（用于注入用户信息）
     */
    @JvmStatic
    @JvmOverloads
    fun sendMessage(message: String, systemInstruction: String? = null, search: Boolean = false, image: android.graphics.Bitmap? = null, callback: GeminiCallback) {
        serviceScope.launch {
            try {
                val augmentedInstruction = if (search) {
                    (systemInstruction ?: "") + "\n【重要】请使用您的内置 Google 搜索功能（Google Search）来查找最新信息以回答用户。"
                } else {
                    systemInstruction
                }

                val currentModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    generationConfig = config,
                    systemInstruction = if (augmentedInstruction != null) content { text(augmentedInstruction) } else null
                )
                
                // 处理多模态输入 (图片 + 文字)
                if (image != null) {
                    val response = currentModel.startChat().sendMessage(content {
                        image(image)
                        text(message)
                    })
                    val text = response.text
                    withContext(Dispatchers.Main) {
                        if (text != null) {
                            callback.onSuccess(text)
                        } else {
                            callback.onError("AI 响应为空")
                        }
                    }
                } else {
                    val response = currentModel.startChat().sendMessage(message)
                    val text = response.text
                    withContext(Dispatchers.Main) {
                        if (text != null) {
                            callback.onSuccess(text)
                        } else {
                            callback.onError("AI 响应为空")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = e.message ?: ""
                    android.util.Log.e("GeminiService", "Send message failed", e)
                    val displayError = when {
                        errorMsg.contains("RESOURCE_EXHAUSTED") || errorMsg.contains("429") -> 
                            "您的 API 额度已耗尽 (429)。请稍后再试，或在 local.properties 中更换您个人的 Gemini API Key。"
                        errorMsg.contains("SAFETY") -> 
                            "抱歉，该话题触及了安全过滤机制，教练无法回答。"
                        else -> "连接 Google AI 失败: ${e.toString()}\n原因: ${e.cause?.toString() ?: "无底层原因"}"
                    }
                    callback.onError(displayError)
                }
            }
        }
    }

    interface GeminiCallback {
        fun onSuccess(response: String, reasoning: String? = null)
        fun onPartialUpdate(content: String, reasoning: String? = null)
        fun onError(error: String)
    }
}
