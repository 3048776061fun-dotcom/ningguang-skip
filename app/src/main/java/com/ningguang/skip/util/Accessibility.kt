package com.ningguang.skip.util

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * 判断本 App 的无障碍服务是否已在系统中开启。
 *
 * 直接读系统的 ENABLED_ACCESSIBILITY_SERVICES 字符串（形如 "pkg/Cls:pkg2/Cls2"），
 * 不依赖 AccessibilityManager / AccessibilityServiceInfo —— 后者在不同编译环境下
 * 出现过 Unresolved reference，用字符串匹配更稳也更好排查。
 */
fun isServiceEnabled(context: Context): Boolean {
    val pkg = context.packageName
    return runCatching {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (flat.isNullOrBlank()) return false
        flat.split(':').any { entry ->
            // entry 形如 com.ningguang.skip/com.ningguang.skip.service.SkipAdService
            val slash = entry.indexOf('/')
            if (slash > 0) entry.substring(0, slash) == pkg else entry.startsWith("$pkg.")
        }
    }.getOrDefault(false)
}

/**
 * 跳到系统的无障碍设置页。
 * 只使用确定存在的常量（ACTION_ACCESSIBILITY_DETAILS_SETTINGS 在 SDK 中并不存在，引用会编译失败）。
 */
fun openAccessibilitySettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in candidates) {
        val started = runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (started) return
    }
}
