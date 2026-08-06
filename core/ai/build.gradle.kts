plugins {
    alias(libs.plugins.ai.library)
    alias (libs.plugins.ktor.serialization)
}

android {
    namespace = "com.core.ai"
}

dependencies {
    implementation(project(":core:repository"))
    implementation(libs.androidx.datastore)
    implementation(libs.ktor.serialization.json)
}