plugins {
    alias(libs.plugins.ai.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.feature.room"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:ai"))
    implementation(project(":core:common"))
    implementation(project(":core:repository"))
    implementation(project(":feature:agent"))
    implementation(project(":ui:room"))
    implementation(project(":ui:common"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.viewmodel)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
