plugins {
    alias(libs.plugins.ai.library)
    alias (libs.plugins.ktor.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.core.repository"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.ktor.serialization.json)
    implementation(libs.androidx.datastore)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}