package com.core.repository

import io.ktor.http.ContentType

/**
 * 一次请求要用的连接信息。
 *
 * 注意这里带 [apiKey]，所以不要把它打印到日志里，
 * 也不要塞进任何会被序列化后上报的结构。
 */
data class ApiConfig(
    val baseUrl: String,
    val chatUrl: String = "",
    val apiKey: String,
    val headers: Map<String, String> = emptyMap(),
    val contentType: ContentType = ContentType.Application.Json
)
