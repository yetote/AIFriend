plugins {
    alias(libs.plugins.ai.library)
}

android {
    namespace = "com.core.network"
}

dependencies {
    api(libs.ktor.client.core)
    api(libs.ktor.client.android)
    implementation(libs.ktor.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.log)
    implementation(libs.slf4j.android)

}