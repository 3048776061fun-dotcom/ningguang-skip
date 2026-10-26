package com.ningguang.skip.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ningguang.skip.R

fun appLabel(context: Context, pkg: String): String = runCatching {
    val pm = context.packageManager
    val info = pm.getApplicationInfo(pkg, 0)
    pm.getApplicationLabel(info).toString()
}.getOrDefault(pkg)

/** 列出桌面可见的应用（受 manifest 中 queries 限制，只能拿到有 LAUNCHER 入口的应用） */
fun launcherApps(context: Context): List<Pair<String, String>> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return runCatching {
        context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }.getOrDefault(emptyList())
}

object Notify {
    const val CHANNEL_SKIP = "skip"
    private const val CHANNEL_STATUS = "status"
    private const val ID_SKIP = 1001
    private const val ID_STATUS = 1002
    private const val ID_SUB = 1003

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_SKIP) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SKIP, context.getString(R.string.channel_skip_name),
                    NotificationManager.IMPORTANCE_MIN).apply {
                    description = context.getString(R.string.channel_skip_desc)
                    setShowBadge(false)
                }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_STATUS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_STATUS, "服务状态", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun showSkip(context: Context, appName: String, text: String) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_SKIP)
            .setSmallIcon(R.drawable.ic_stat_skip)
            .setContentTitle("已跳过 $appName 的广告")
            .setContentText(if (text.isBlank()) "自动跳过" else "命中：$text")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setAutoCancel(true)
            .setTimeoutAfter(2500)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_SKIP, n) }
    }

    fun showSubscriptionUpdate(context: Context, added: Int, updated: Int, failed: Int) {
        if (!canNotify(context)) return
        val text = buildString {
            append("新增 $added 条 · 更新 $updated 条")
            if (failed > 0) append(" · $failed 个源失败")
        }
        val n = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_skip)
            .setContentTitle("规则订阅已更新")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_SUB, n) }
    }

    fun showStatus(context: Context, running: Boolean) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_skip)
            .setContentTitle(if (running) "凝光跳过正在运行" else "凝光跳过已停止")
            .setContentText(if (running) "无障碍服务已连接，正在监听广告" else "请在系统设置中开启无障碍服务")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_STATUS, n) }
    }

    fun cancelStatus(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_STATUS) }
    }
}

fun Context.vibrateTick(strength: Int = 30) {
    val v = getSystemService(Vibrator::class.java) ?: return
    runCatching {
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(14L, strength))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(14L)
        }
    }
}
