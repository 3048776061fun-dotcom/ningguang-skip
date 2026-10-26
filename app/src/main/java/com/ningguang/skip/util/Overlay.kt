package com.ningguang.skip.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.ningguang.skip.ui.overlay.GlowToast

/** 跳到系统的悬浮窗授权页（各 ROM 的位置不同，逐级降级） */
fun openOverlayPermissionSettings(context: Context) {
    val pkg = context.packageName
    val intents = listOf(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$pkg")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    for (i in intents) {
        runCatching {
            context.startActivity(i)
            return
        }
    }
}

fun hasOverlayPermission(context: Context): Boolean = GlowToast.canOverlay(context)
