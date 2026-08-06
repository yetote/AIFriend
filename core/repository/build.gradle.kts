plugins {
    alias(libs.plugins.ai.library)
    alias (libs.plugins.ktor.serialization)
}

android {
    namespace = "com.core.repository"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.ktor.serialization.json)
}