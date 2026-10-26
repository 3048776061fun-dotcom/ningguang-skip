// 顶层构建脚本：只声明插件，不写具体版本实现
//
// 版本对应是硬约束，改动前先看官方映射表：
//   https://developer.android.com/jetpack/androidx/releases/compose-kotlin
//   Kotlin 1.9.24  →  Compose Compiler 1.5.14（在 app/build.gradle.kts 里）
//   Kotlin 1.9.25  →  Compose Compiler 1.5.15
plugins {
    id("com.android.application") version "8.1.4" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
