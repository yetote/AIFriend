package com.core.repository

import com.core.network.DEEPSEEK_KEY
import com.core.network.DOUBAO_KEY
import com.core.network.GEMINI_KEY
import com.core.network.KIMI_KEY
import com.core.network.QIANWEN_KEY
import com.core.network.URL_DEEPSEEK
import com.core.network.URL_DOUBAO
import com.core.network.URL_GEMINI
import com.core.network.URL_KIMI
import com.core.network.URL_QIANWEN
import com.core.network.URL_ZHIPU
import com.core.network.ZHIPU_KEY
import io.ktor.http.ContentType

data class ApiConfig(
    val baseUrl: String,
    val chatUrl: String = "",
    val apiKey: String,
    val headers: Map<String, String> = emptyMap(),
    val contentType: ContentType = ContentType.Application.Json
)

object AIConfigs{
    fun geminiConfig() = ApiConfig(
        baseUrl = URL_GEMINI,
        chatUrl=URL_GEMINI,
        apiKey = GEMINI_KEY,
        headers = mapOf("x-goog-api-key" to GEMINI_KEY)
    )

    fun deepseekConfig() = ApiConfig(
        baseUrl = URL_DEEPSEEK,
        chatUrl = "$URL_DEEPSEEK/responses",
        apiKey = DEEPSEEK_KEY,
        headers = mapOf("Authorization" to "Bearer $DEEPSEEK_KEY")
    )
    fun doubaoConfig() = ApiConfig(
        baseUrl = URL_DOUBAO,
        chatUrl = "$URL_DOUBAO/responses",
        apiKey = DOUBAO_KEY,
        headers = mapOf("Authorization" to "Bearer $DOUBAO_KEY")
    )
    fun zhipuConfig() = ApiConfig(
        baseUrl = URL_ZHIPU,
        chatUrl = "$URL_ZHIPU/responses",
        apiKey = ZHIPU_KEY,
        headers = mapOf("Authorization" to "Bearer $ZHIPU_KEY")
    )
    fun kimiConfig() = ApiConfig(
        baseUrl = URL_KIMI,
        chatUrl = "$URL_KIMI/responses",
        apiKey = KIMI_KEY,
        headers = mapOf("Authorization" to "Bearer $KIMI_KEY")
    )
    fun qianwenConfig() = ApiConfig(
        baseUrl = URL_QIANWEN,
        chatUrl = "$URL_QIANWEN/responses",
        apiKey = QIANWEN_KEY,
        headers = mapOf("Authorization" to "Bearer $QIANWEN_KEY")
    )
}