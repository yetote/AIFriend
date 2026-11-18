plugins {
    alias(libs.plugins.ai.library)
}

android {
    namespace = "com.core.network"
}

dependencies {
    api(libs.ktor.client.core)
    // Android 引擎（HttpURLConnection）不支持 WebSocket，讯飞流式 TTS 需要 OkHttp 引擎。
    api(libs.ktor.client.okhttp)
    // 讯飞在线合成走 WebSocket 流式接口，客户端插件在这里装、对上层的 tts 模块可见。
    api(libs.ktor.client.websockets)
    implementation(libs.ktor.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.log)
    implementation(libs.slf4j.android)

}