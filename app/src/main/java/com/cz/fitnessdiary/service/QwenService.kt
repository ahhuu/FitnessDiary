package com.cz.fitnessdiary.service

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult
import com.alibaba.dashscope.common.MultiModalMessage
import com.alibaba.dashscope.common.Role
import com.alibaba.dashscope.exception.ApiException
import com.alibaba.dashscope.exception.NoApiKeyException
import com.alibaba.dashscope.utils.JsonUtils
import com.cz.fitnessdiary.BuildConfig
import com.cz.fitnessdiary.database.entity.ChatMessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap

/**
 * 通义千问 (Qwen) AI 服务 - 基于 DashScope SDK
 * 专注于多模态任务（图片理解 + 文本），支持对话历史上下文
 */
object QwenService {
    private const val MODEL_NAME = "qwen3.5-plus" // 使用旗舰原生多模态模型
    private const val API_KEY = BuildConfig.QWEN_API_KEY
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    /**
     * 为 Java 提供的异步发送方法（支持多模态 + 对话历史）
     * @param message 用户文本消息
     * @param systemInstruction 可选的自定义系统指令
     * @param image 可选的图片（Bitmap）
     * @param history 历史消息列表（已按时间排序, 最旧在前）
     * @param callback 回调接口
     */
    @JvmStatic
    @JvmOverloads
    fun sendMessage(
        message: String,
        systemInstruction: String? = null,
        image: Bitmap? = null,
        history: List<ChatMessageEntity>? = null,
        callback: AICallback
    ) {
        serviceScope.launch {
            try {
                // 构建默认系统指令
                val finalSystemPrompt = systemInstruction 
                    ?: "你是健身日记（FitnessDiary）应用的 AI 助手，身份是一位专业的健身教练和营养师。" +
                       "在回答食物营养价值时，请务必以 ### [食物名] 开头，并明确列出 [热量]、[蛋白质]、[碳水] 等数值（以每100g为标准）。" +
                       "请始终使用中文回答。"

                // 构建消息列表
                val allMessages = mutableListOf<MultiModalMessage>()

                // 1. 系统指令
                val systemMessage = MultiModalMessage.builder()
                    .role(Role.SYSTEM.value)
                    .content(listOf(mapOf("text" to finalSystemPrompt)))
                    .build()
                allMessages.add(systemMessage)

                // 2. 历史对话（仅文本，不包含图片以节省 token）
                history?.forEach { msg ->
                    val role = if (msg.isUser) Role.USER.value else Role.ASSISTANT.value
                    val historyMsg = MultiModalMessage.builder()
                        .role(role)
                        .content(listOf(mapOf("text" to (msg.content ?: ""))))
                        .build()
                    allMessages.add(historyMsg)
                }

                // 3. 当前用户消息（可能含图片）
                val userContentList = mutableListOf<Map<String, Any>>()
                
                // 如果有图片，先添加图片
                if (image != null) {
                    val base64Image = bitmapToBase64(image)
                    userContentList.add(mapOf("image" to "data:image/jpeg;base64,$base64Image"))
                }
                
                // 再添加文本
                userContentList.add(mapOf("text" to message))

                val userMessage = MultiModalMessage.builder()
                    .role(Role.USER.value)
                    .content(userContentList)
                    .build()
                allMessages.add(userMessage)

                // 构建 API 参数
                val param = MultiModalConversationParam.builder()
                    .apiKey(API_KEY)
                    .model(MODEL_NAME)
                    .messages(allMessages)
                    .topP(0.8)
                    .build()

                // 调用 API
                val conv = MultiModalConversation()
                val result: MultiModalConversationResult = conv.call(param)

                // 解析响应
                val choice = result.output?.choices?.get(0)
                val responseText = choice?.message?.content?.get(0)?.get("text") as? String
                    ?: "抱歉，模型返回了空响应。"

                withContext(Dispatchers.Main) {
                    callback.onSuccess(responseText, null)
                }

            } catch (e: NoApiKeyException) {
                withContext(Dispatchers.Main) {
                    callback.onError("API Key 配置错误: ${e.message}")
                }
                android.util.Log.e("QwenService", "NoApiKeyException", e)
            } catch (e: ApiException) {
                withContext(Dispatchers.Main) {
                    val errorMsg = when {
                        e.message?.contains("insufficient_quota") == true || e.message?.contains("429") == true ->
                            "您的千问额度已耗尽。请前往阿里云控制台充值或检查免费额度。"
                        e.message?.contains("invalid_api_key") == true ->
                            "API Key 无效，请检查 local.properties 中的配置。"
                        else -> "千问 API 调用失败: ${e.message}"
                    }
                    callback.onError(errorMsg)
                }
                android.util.Log.e("QwenService", "ApiException", e)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError("千问服务连接失败: ${e.message}")
                }
                android.util.Log.e("QwenService", "Unexpected error", e)
            }
        }
    }

    /**
     * 将 Bitmap 转换为 Base64 编码字符串
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }
}
