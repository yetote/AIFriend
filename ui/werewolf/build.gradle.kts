plugins {
    alias(libs.plugins.ai.ui)
}
android {
    namespace="com.ui.werewolf"
}
dependencies {
    implementation(project(":core:ai"))
}