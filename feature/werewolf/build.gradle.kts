plugins {
    alias(libs.plugins.ai.feature)
    alias(libs.plugins.kotlin.compose)
    // 决策结果的 DTO 是 @Serializable，需要 kotlinx.serialization 编译器插件
    alias(libs.plugins.ktor.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.feature.werewolf"
}

dependencies {
    // 只依赖 core，不依赖 feature:room / feature:agent —— 狼人杀与聊天室互相独立
    implementation(project(":core:ai"))
    implementation(libs.ktor.serialization.json)
    // 自己的库：存档不进 aifriend.db，避免为了狼人杀去动聊天室的 schema 和迁移链
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.coil)
    implementation(libs.smart.kernel)
    implementation(libs.smart.classics)
}