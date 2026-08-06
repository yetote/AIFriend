plugins {
    alias(libs.plugins.ai.feature)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.feature.werewolf"
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.coil)
    implementation(libs.smart.kernel)
    implementation(libs.smart.classics)
}