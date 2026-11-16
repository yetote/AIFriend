plugins {
    alias(libs.plugins.ai.library)
    alias (libs.plugins.ktor.serialization)
}

android {
    namespace = "com.core.ai"
}

dependencies {
    implementation(project(":core:repository"))
    implementation(project(":core:common"))
    // 探活要把网络异常翻成用户能读的一句话（getReadableMessage），
    // core:common 是 implementation 依赖，取不到，这里直接声明
    implementation(project(":core:network"))
    implementation(libs.androidx.datastore)
    implementation(libs.ktor.serialization.json)
}