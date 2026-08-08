plugins {
    alias(libs.plugins.ai.library)
    alias(libs.plugins.ktor.serialization)
}

android {
    namespace = "com.core.common"
}

dependencies {
    implementation(libs.androidx.compose.viewmodel)
    implementation(project(":core:network"))
}