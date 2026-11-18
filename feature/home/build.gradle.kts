plugins {
    alias(libs.plugins.ai.feature)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.feature.home"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:ai"))
    implementation(project(":core:common"))
    implementation(project(":core:repository"))
    // 设置页的「测试并保存语音凭证」要真的合成一句，直接复用 core:tts 的协议实现。
    implementation(project(":core:tts"))
    implementation(project(":feature:agent"))
    implementation(project(":feature:room"))
    implementation(project(":feature:werewolf"))
    implementation(project(":ui:common"))
    implementation(project(":ui:agent"))
    implementation(project(":ui:room"))
    implementation(project(":ui:home"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.coil)
    implementation(libs.smart.kernel)
    implementation(libs.smart.classics)
}