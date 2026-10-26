package com.ningguang.skip.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** 判断本 App 的无障碍服务是否已在系统中开启 */
fun isServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        ?: return false
    return runCatching {
        am.getEnabledAccessibilityServiceList(android.view.accessibility.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }.getOrDefault(false)
}

/** 跳到系统的无障碍设置页（不同 ROM 的位置不同，做降级） */
fun openAccessibilitySettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Intent(Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (i in intents) {
        runCatching {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
            return
        }
    }
}
