plugins {
    alias(libs.plugins.ai.library)
}

android {
    namespace = "com.core.ai"
}

dependencies {
    implementation(libs.androidx.datastore)
}