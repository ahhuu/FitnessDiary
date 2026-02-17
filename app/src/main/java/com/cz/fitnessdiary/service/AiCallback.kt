package com.cz.fitnessdiary.service

/**
 * AI 服务通用回调接口
 * 用于 Qwen、DeepSeek 等多个 AI 服务的统一回调
 */
interface AICallback {
    /**
     * 成功回调
     * @param response AI 返回的主要内容
     * @param reasoning 可选的推理过程（如 DeepSeek Reasoner 的思维链）
     */
    fun onSuccess(response: String, reasoning: String? = null)

    /**
     * 部分更新回调（流式输出时使用）
     * @param content 部分内容
     * @param reasoning 部分推理内容
     */
    fun onPartialUpdate(content: String, reasoning: String? = null)

    /**
     * 错误回调
     * @param error 错误信息
     */
    fun onError(error: String)
}
