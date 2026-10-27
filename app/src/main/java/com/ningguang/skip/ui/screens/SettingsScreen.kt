package com.ningguang.skip.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ningguang.skip.data.Prefs
import com.ningguang.skip.ui.glow.NingguangCard
import com.ningguang.skip.ui.glow.NingguangConfig
import com.ningguang.skip.ui.glow.NingguangListItem
import com.ningguang.skip.ui.glow.NingguangSwitch
import com.ningguang.skip.ui.glow.SectionTitle
import com.ningguang.skip.ui.theme.GlowCyan
import com.ningguang.skip.ui.theme.GlowGreen
import com.ningguang.skip.ui.theme.GlowViolet
import com.ningguang.skip.ui.theme.GlowWarm
import com.ningguang.skip.util.hasOverlayPermission
import com.ningguang.skip.util.isServiceEnabled
import com.ningguang.skip.util.openAccessibilitySettings
import com.ningguang.skip.util.openOverlayPermissionSettings
import com.ningguang.skip.worker.SubscriptionWorker
import kotlin.math.roundToInt

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings by Prefs.settingsFlow.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    var showKeywords by remember { mutableStateOf(false) }

    // 从系统授权页返回后要重新读一次权限，否则开关状态不会变
    var overlayOk by remember { mutableStateOf(hasOverlayPermission(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlayOk = hasOverlayPermission(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        ScreenHeader(title = "设置", subtitle = "行为、通知与凝光视效")

        /* ---------------- 权限 ---------------- */
        SectionTitle("权限")
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NingguangListItem(
                title = "无障碍服务",
                subtitle = if (isServiceEnabled(context)) "已开启 · 点击前往系统设置" else "未开启 · 点击前往开启",
                icon = Icons.Rounded.Shield,
                iconTint = GlowCyan,
                glow = GlowCyan,
                onClick = { openAccessibilitySettings(context) }
            )
            NingguangListItem(
                title = "电池优化白名单",
                subtitle = "加入后更容易在后台常驻，避免被系统回收",
                icon = Icons.Rounded.BatterySaver,
                iconTint = GlowWarm,
                glow = GlowWarm,
                onClick = { requestIgnoreBattery(context) }
            )
        }

        /* ---------------- 跳过行为 ---------------- */
        SectionTitle("跳过行为")
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SliderItem(
                title = "点击延迟",
                subtitle = "命中广告按钮后延迟点击，给动画留出时间",
                value = settings.clickDelayMs.toFloat(),
                range = 0f..1500f,
                steps = 5,
                glow = GlowCyan,
                format = { "${it.roundToInt()} ms" },
                onValueChange = { Prefs.updateSettings(settings.copy(clickDelayMs = it.roundToInt())) }
            )
            SliderItem(
                title = "冷却时间",
                subtitle = "同一规则在同一应用内的最小间隔，避免连点",
                value = settings.cooldownMs.toFloat(),
                range = 300f..5000f,
                steps = 8,
                glow = GlowCyan,
                format = { "${(it.roundToInt() / 100) * 100} ms" },
                onValueChange = { Prefs.updateSettings(settings.copy(cooldownMs = it.roundToInt())) }
            )
            SliderItem(
                title = "每分钟上限",
                subtitle = "同一应用每分钟最多跳过次数，防止误触循环",
                value = settings.maxPerMinute.toFloat(),
                range = 1f..12f,
                steps = 10,
                glow = GlowViolet,
                format = { "${it.roundToInt()} 次" },
                onValueChange = { Prefs.updateSettings(settings.copy(maxPerMinute = it.roundToInt())) }
            )
        }

        /* ---------------- 匹配词 ---------------- */
        SectionTitle("匹配词")
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NingguangListItem(
                title = "自定义关键词与控件 ID",
                subtitle = "在内置词库之外追加，用 | 分隔",
                icon = Icons.Rounded.TextFields,
                iconTint = GlowViolet,
                glow = GlowViolet,
                onClick = { showKeywords = !showKeywords }
            )
            if (showKeywords) {
                NingguangCard(
                    modifier = Modifier.fillMaxWidth(),
                    glow = GlowViolet,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    KeywordField(
                        label = "关键词",
                        value = settings.customKeywords,
                        placeholder = "跳过|关闭广告|Skip",
                        onValueChange = { Prefs.updateSettings(settings.copy(customKeywords = it)) }
                    )
                    Spacer(Modifier.height(10.dp))
                    KeywordField(
                        label = "控件 ID",
                        value = settings.customIds,
                        placeholder = "iv_skip|ad_close",
                        onValueChange = { Prefs.updateSettings(settings.copy(customIds = it)) }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("把「关闭」也算作广告按钮", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "命中率更高，但误触风险也更高",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        NingguangSwitch(
                            checked = settings.matchCloseText,
                            glow = GlowWarm,
                            onCheckedChange = { Prefs.updateSettings(settings.copy(matchCloseText = it)) }
                        )
                    }
                }
            }
        }

        /* ---------------- 通知与反馈 ---------------- */
        SectionTitle("通知与反馈")
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NingguangListItem(
                title = "凝光悬浮提示",
                subtitle = if (overlayOk) {
                    "跳过时在屏幕顶部弹一枚玻璃胶囊，1.8 秒后消失"
                } else {
                    "需要悬浮窗权限，点击前往授权"
                },
                icon = Icons.Rounded.OpenInNew,
                iconTint = GlowCyan,
                glow = GlowCyan,
                onClick = { if (!overlayOk) openOverlayPermissionSettings(context) }
            ) {
                NingguangSwitch(
                    checked = settings.overlayToast && overlayOk,
                    glow = GlowCyan,
                    onCheckedChange = { want ->
                        // 未授权时先跳授权页，本次不改设置（用 if/else 而不是 return@，
                        // 因为 onCheckedChange 不是 inline lambda，带标签返回不合法）
                        if (want && !overlayOk) {
                            openOverlayPermissionSettings(context)
                        } else {
                            Prefs.updateSettings(settings.copy(overlayToast = want))
                        }
                    }
                )
            }
            NingguangListItem(
                title = "跳过提醒",
                subtitle = if (settings.overlayToast && overlayOk) {
                    "已开启悬浮提示，通知不再重复发送"
                } else {
                    "跳过成功时发一条轻量通知"
                },
                icon = Icons.Rounded.Notifications,
                iconTint = GlowCyan
            ) {
                NingguangSwitch(
                    checked = settings.showNotification,
                    onCheckedChange = { Prefs.updateSettings(settings.copy(showNotification = it)) }
                )
            }
            NingguangListItem(
                title = "震动反馈",
                subtitle = "跳过时轻微震动一下",
                icon = Icons.Rounded.Vibration,
                iconTint = GlowViolet
            ) {
                NingguangSwitch(
                    checked = settings.hapticFeedback,
                    glow = GlowViolet,
                    onCheckedChange = { Prefs.updateSettings(settings.copy(hapticFeedback = it)) }
                )
            }
        }

        /* ---------------- 外观 ---------------- */
        SectionTitle("外观 · 凝光视效")
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NingguangListItem(
                title = "跟随系统深浅色",
                subtitle = "关闭后可手动指定",
                icon = Icons.Rounded.Palette,
                iconTint = GlowCyan
            ) {
                NingguangSwitch(
                    checked = settings.followSystemTheme,
                    onCheckedChange = { Prefs.updateSettings(settings.copy(followSystemTheme = it)) }
                )
            }
            if (!settings.followSystemTheme) {
                NingguangListItem(
                    title = "深色模式",
                    subtitle = "夜间更柔和",
                    icon = Icons.Rounded.Palette,
                    iconTint = GlowViolet
                ) {
                    NingguangSwitch(
                        checked = settings.darkMode,
                        glow = GlowViolet,
                        onCheckedChange = { Prefs.updateSettings(settings.copy(darkMode = it)) }
                    )
                }
            }
            NingguangListItem(
                title = "凝光视效",
                subtitle = "关闭后界面变为纯色，可省电",
                icon = Icons.Rounded.Palette,
                iconTint = GlowCyan
            ) {
                NingguangSwitch(
                    checked = settings.glowEnabled,
                    onCheckedChange = {
                        Prefs.updateSettings(settings.copy(glowEnabled = it))
                        NingguangConfig.enabled = it
                    }
                )
            }
            if (settings.glowEnabled) {
                SliderItem(
                    title = "光效强度",
                    subtitle = "调低更克制，调高更通透",
                    value = settings.glowIntensity,
                    range = 0.2f..1f,
                    steps = 7,
                    glow = GlowCyan,
                    format = { "${(it * 100).roundToInt()}%" },
                    onValueChange = {
                        Prefs.updateSettings(settings.copy(glowIntensity = it))
                        NingguangConfig.intensity = it
                    }
                )
            }
        }

        /* ---------------- 订阅更新 ---------------- */
        SectionTitle("规则订阅")
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NingguangListItem(
                title = "每天自动检查更新",
                subtitle = if (Prefs.subscriptions.isEmpty()) {
                    "未添加订阅源，不会产生任何网络请求"
                } else {
                    "联网时每天检查一次，版本号变大才更新"
                },
                icon = Icons.Rounded.CloudSync,
                iconTint = GlowCyan,
                glow = GlowCyan,
                onClick = {
                    val next = !settings.autoUpdateSubscription
                    Prefs.updateSettings(settings.copy(autoUpdateSubscription = next))
                    if (next) SubscriptionWorker.enableAutoUpdate(context)
                    else SubscriptionWorker.disableAutoUpdate(context)
                }
            ) {
                NingguangSwitch(
                    checked = settings.autoUpdateSubscription,
                    glow = GlowCyan,
                    onCheckedChange = { next ->
                        Prefs.updateSettings(settings.copy(autoUpdateSubscription = next))
                        if (next) SubscriptionWorker.enableAutoUpdate(context)
                        else SubscriptionWorker.disableAutoUpdate(context)
                    }
                )
            }
            NingguangListItem(
                title = "更新后通知我",
                subtitle = "有新规则时发一条通知",
                icon = Icons.Rounded.Notifications,
                iconTint = GlowViolet
            ) {
                NingguangSwitch(
                    checked = settings.notifySubscriptionUpdate,
                    glow = GlowViolet,
                    onCheckedChange = {
                        Prefs.updateSettings(settings.copy(notifySubscriptionUpdate = it))
                    }
                )
            }
            NingguangListItem(
                title = "立即检查一次",
                subtitle = "不等周期任务，马上拉取全部订阅",
                icon = Icons.Rounded.Refresh,
                iconTint = GlowGreen,
                glow = GlowGreen,
                onClick = { SubscriptionWorker.runOnce(context) }
            )
        }

        /* ---------------- 数据 ---------------- */
        SectionTitle("数据")
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NingguangListItem(
                title = "恢复内置规则",
                subtitle = "把内置规则恢复为默认，自定义规则保留",
                icon = Icons.Rounded.Refresh,
                iconTint = GlowWarm,
                glow = GlowWarm,
                onClick = { Prefs.resetBuiltInRules() }
            )
            NingguangListItem(
                title = "清空统计数据",
                subtitle = "重置今日与累计跳过次数",
                icon = Icons.Rounded.Refresh,
                iconTint = GlowWarm,
                glow = GlowWarm,
                onClick = { Prefs.resetCounters() }
            )
            NingguangListItem(
                title = "关于",
                subtitle = "凝光跳过 v1.0.0 · 本地运行，不联网",
                icon = Icons.Rounded.Info,
                iconTint = GlowCyan
            )
        }
    }
}

@Composable
private fun SliderItem(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    glow: Color,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    NingguangCard(
        modifier = Modifier.fillMaxWidth(),
        glow = glow,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                format(value),
                style = MaterialTheme.typography.labelLarge,
                color = glow
            )
        }
        Spacer(Modifier.height(6.dp))
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = glow,
                activeTrackColor = glow,
                inactiveTrackColor = glow.copy(alpha = 0.22f)
            )
        )
    }
}

@Composable
private fun KeywordField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GlowViolet)
    )
}

private fun requestIgnoreBattery(context: Context) {
    if (Build.VERSION.SDK_INT < 23) return
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    runCatching {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
