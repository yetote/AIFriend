plugins {
    alias(libs.plugins.ai.feature)
    alias(libs.plugins.kotlin.compose)
    // 决策结果的 DTO 是 @Serializable，需要 kotlinx.serialization 编译器插件
    alias(libs.plugins.ktor.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.feature.werewolf"
}

dependencies {
    // 只依赖 core 与自己的 ui，不依赖 feature:room / feature:agent —— 狼人杀与聊天室互相独立
    implementation(project(":core:ai"))
    // 自动朗读：系统 TTS 引擎 + 朗读队列
    implementation(project(":core:tts"))
    // 取好友列表（选参与者、拿人设与模型配置）
    implementation(project(":core:repository"))
    implementation(project(":ui:werewolf"))
    // BackHandler / setContent 这些在 activity-compose 里，它同时带进 compose runtime
    implementation(libs.androidx.activity.compose)
    // 用 BOM 统一 compose 各构件版本，否则传递依赖会把 ui 定到一个本地没有的版本
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    // 不额外声明 compose 依赖：ai.feature 注入的 lifecycle-viewmodel-compose 已经带进
    // compose runtime，和 feature:room 的做法一致。多加反而会把 lifecycle 的
    // atomic group 拉到本地缓存里没有的版本，导致离线环境解析失败。
    implementation(libs.ktor.serialization.json)
    // Room 的 KSP 任务需要把 classpath 里的 aar 转成 android-classes-jar 变体，
    // 而 lifecycle 2.8.7 只发布了 -android 变体、没有根构件的 aar，这里会解析失败。
    // 把整组抬到已有发布的 2.9.4：打包时本来也会被其它模块抬上去，这里只是提前对齐。
    constraints {
        implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.4")
    }
    // 自己的库：存档不进 aifriend.db，避免为了狼人杀去动聊天室的 schema 和迁移链
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.coil)
    implementation(libs.smart.kernel)
    implementation(libs.smart.classics)
}