plugins {
    alias(libs.plugins.ai.library)
    alias(libs.plugins.ktor.serialization)
}

android {
    namespace = "com.core.tts"
}

dependencies {
    // 云端语音要读用户已填的 TTS Key，并复用统一网络栈。
    // 依赖方向 core:tts → core:repository → core:network，不会成环。
    implementation(project(":core:repository"))
    implementation(project(":core:network"))
    // TTSProtocols 直接调用 kotlinx-serialization 的 buildJsonObject/JsonObject。
    implementation(libs.ktor.serialization.json)
}
