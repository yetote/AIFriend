plugins {
    alias(libs.plugins.ai.ui)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.ui.home"
    buildFeatures {
        compose = true
    }
}
dependencies {
    implementation(project(":ui:common"))
    implementation(project(":ui:agent"))
    implementation(project(":ui:room"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}
