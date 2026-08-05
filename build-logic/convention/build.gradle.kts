import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
}

group = "com.logic.convention"

// Configure the build-logic plugins to target JDK 17
// This matches the JDK used to build the project, and is not related to what is running on device.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradleApiPlugin)
    compileOnly(libs.android.tools.common)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("aiApplication") {
            id = libs.plugins.ai.application.get().pluginId
            implementationClass = "AIApplicationPlugin"
        }
        register("aiLib") {
            id = libs.plugins.ai.library.get().pluginId
            implementationClass = "AILibPlugin"
        }
        register("aiFeature") {
            id = libs.plugins.ai.feature.get().pluginId
            implementationClass = "AIFeaturePlugin"
        }
        register("aiUi") {
            id = libs.plugins.ai.ui.get().pluginId
            implementationClass = "AIUiPlugin"
        }

    }
}
