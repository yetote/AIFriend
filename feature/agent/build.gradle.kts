plugins {
    alias(libs.plugins.ai.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.feature.agent"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:ai"))
    implementation(project(":core:common"))
    // 报错文案要带上「是哪个模型」：404 的原文只有 getReadableMessage 知道，
    // 拼提示的 withModelHint 也放在 network 里，不在这里另写一份
    implementation(project(":core:network"))
    implementation(project(":core:persona"))
    implementation(project(":core:repository"))
    // 建角色页要能试听音色：音色能不能出声取决于账号开通情况，
    // 选的时候当场响一声，比配完角色进游戏才发现没声音强。
    implementation(project(":core:tts"))
    implementation(project(":ui:agent"))
    implementation(project(":ui:common"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
