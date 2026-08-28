plugins {
    alias(libs.plugins.ai.library)
    alias(libs.plugins.ktor.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.core.common"
}

dependencies {
    implementation(libs.androidx.compose.viewmodel)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(project(":core:network"))
}