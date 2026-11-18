plugins {
    alias(libs.plugins.ai.library)
}

android {
    namespace = "com.core.network"
}

dependencies {
    api(libs.ktor.client.core)
    api(libs.ktor.client.android)
    // 讯飞在线合成走 WebSocket 流式接口，客户端插件在这里装、对上层的 tts 模块可见。
    api(libs.ktor.client.websockets)
    implementation(libs.ktor.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.log)
    implementation(libs.slf4j.android)

}