plugins {
    alias(libs.plugins.ai.feature)
    alias(libs.plugins.kotlin.compose)
    // 决策结果的 DTO 是 @Serializable，需要 kotlinx.serialization 编译器插件
    alias(libs.plugins.ktor.serialization)
}

android {
    namespace = "com.feature.werewolf"
}

dependencies {
    // 只依赖 core，不依赖 feature:room / feature:agent —— 狼人杀与聊天室互相独立
    implementation(project(":core:ai"))
    implementation(libs.ktor.serialization.json)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.coil)
    implementation(libs.smart.kernel)
    implementation(libs.smart.classics)
}