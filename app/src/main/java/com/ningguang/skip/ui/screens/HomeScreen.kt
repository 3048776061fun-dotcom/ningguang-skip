package com.ningguang.skip.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ningguang.skip.data.Prefs
import com.ningguang.skip.ui.glow.NingguangButton
import com.ningguang.skip.ui.glow.NingguangCard
import com.ningguang.skip.ui.glow.NingguangConfig
import com.ningguang.skip.ui.glow.NingguangInteraction
import com.ningguang.skip.ui.glow.NingguangListItem
import com.ningguang.skip.ui.glow.NingguangSwitch
import com.ningguang.skip.ui.glow.SectionTitle
import com.ningguang.skip.ui.glow.ningguangGlass
import com.ningguang.skip.ui.glow.rememberNingguangInteraction
import com.ningguang.skip.ui.theme.GlowCyan
import com.ningguang.skip.ui.theme.GlowGreen
import com.ningguang.skip.ui.theme.GlowViolet
import com.ningguang.skip.ui.theme.GlowWarm
import com.ningguang.skip.util.isServiceEnabled
import com.ningguang.skip.util.openAccessibilitySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val settings by Prefs.settingsFlow.collectAsStateWithLifecycle()
    val today by Prefs.todayCountFlow.collectAsStateWithLifecycle()
    val total by Prefs.totalCountFlow.collectAsStateWithLifecycle()
    val logs by Prefs.logsFlow.collectAsStateWithLifecycle()

    var serviceOn by remember { mutableStateOf(isServiceEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        serviceOn = isServiceEnabled(context)
        Prefs.setServiceRunning(serviceOn)
    }

    val running = serviceOn && settings.masterEnabled
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        ScreenHeader(
            title = "凝光跳过",
            subtitle = if (serviceOn) "无障碍服务已连接" else "无障碍服务未开启"
        )

        // —— 主状态卡 ——
        NingguangCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            glow = if (running) GlowCyan else cs.onSurfaceVariant,
            stateGlow = if (running) 1f else 0f,
            contentPadding = PaddingValues(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlowCore(
                    active = running,
                    glow = if (running) GlowCyan else cs.onSurfaceVariant,
                    onClick = {
                        if (!serviceOn) {
                            openAccessibilitySettings(context)
                        } else {
                            Prefs.updateSettings(settings.copy(masterEnabled = !settings.masterEnabled))
                        }
                    }
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = when {
                        !serviceOn -> "点击开启无障碍服务"
                        running -> "正在守护 · 自动跳过广告"
                        else -> "已暂停"
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        !serviceOn -> "首次使用需要在系统设置里开启一次，之后即可后台常驻"
                        running -> "通用跳过 + 规则引擎 双引擎运行中"
                        else -> "开启后才会监听窗口并跳过广告"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // —— 未授权提示 ——
        if (!serviceOn) {
            Spacer(Modifier.height(14.dp))
            NingguangCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                glow = GlowWarm,
                stateGlow = 0.6f,
                contentPadding = PaddingValues(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Shield, null,
                        tint = GlowWarm,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("需要无障碍权限", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "本 App 只在本地识别广告按钮并代替你点击，不联网、不上传任何内容",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                NingguangButton(
                    text = "去开启",
                    icon = Icons.Rounded.Bolt,
                    glow = GlowWarm,
                    onClick = { openAccessibilitySettings(context) }
                )
            }
        }

        // —— 统计 ——
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatCard(value = today, label = "今日跳过", glow = GlowCyan, modifier = Modifier.weight(1f))
            StatCard(value = total, label = "累计跳过", glow = GlowViolet, modifier = Modifier.weight(1f))
        }

        // —— 引擎开关 ——
        SectionTitle("引擎")
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NingguangListItem(
                title = "通用跳过",
                subtitle = "内置关键词与控件 ID，装完即用",
                icon = Icons.Rounded.Bolt,
                iconTint = GlowCyan,
                glow = GlowCyan
            ) {
                NingguangSwitch(
                    checked = settings.genericEnabled,
                    glow = GlowCyan,
                    onCheckedChange = { Prefs.updateSettings(settings.copy(genericEnabled = it)) }
                )
            }
            NingguangListItem(
                title = "规则引擎",
                subtitle = "按应用自定义规则，支持订阅导入",
                icon = Icons.Rounded.Shield,
                iconTint = GlowViolet,
                glow = GlowViolet
            ) {
                NingguangSwitch(
                    checked = settings.ruleEnabled,
                    glow = GlowViolet,
                    onCheckedChange = { Prefs.updateSettings(settings.copy(ruleEnabled = it)) }
                )
            }
        }

        // —— 最近记录 ——
        SectionTitle("最近跳过")
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (logs.isEmpty()) {
                NingguangCard(
                    modifier = Modifier.fillMaxWidth(),
                    glow = cs.onSurfaceVariant,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Text(
                        "还没有跳过记录。保持服务开启，下次开屏广告出现时会自动跳过。",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            } else {
                logs.take(4).forEach { log ->
                    NingguangCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        glow = GlowGreen,
                        elevation = 3.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(log.appName.ifBlank { log.pkg }, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    log.matchedText.ifBlank { log.ruleName },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            Text(
                                timeText(log.time),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 凝光核心：两道旋转光环 + 玻璃圆球，按下收缩、松手回弹 */
@Composable
private fun GlowCore(
    active: Boolean,
    glow: Color,
    onClick: () -> Unit
) {
    val infinite: InfiniteTransition = rememberInfiniteTransition(label = "core")
    val rotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(11_000, easing = LinearEasing)),
        label = "rot"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.92f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    val interaction = rememberNingguangInteraction()
    val press = interaction.pressState.value
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = (1f - 0.06f * press) * (if (active) pulse else 1f),
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "coreScale"
    )

    Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // 旋转光带：只在运行时转，光随状态变化
        if (active && NingguangConfig.enabled) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = rotation }
            ) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(glow.copy(alpha = 0.9f), Color.Transparent, glow.copy(alpha = 0.5f))),
                    startAngle = 0f,
                    sweepAngle = 150f,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = -rotation * 0.7f }
            ) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, glow.copy(alpha = 0.45f), Color.Transparent)),
                    startAngle = 140f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // 玻璃球体
        Box(
            modifier = Modifier
                .size(124.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .then(
                    Modifier.ningguangGlass(
                        shape = CircleShape,
                        glow = glow,
                        interaction = interaction,
                        stateGlow = if (active) 0.9f else 0.2f
                    )
                )
                .pointerInput(onClick) {
                    detectCoreTap(
                        interaction = interaction,
                        scope = scope,
                        onPressHaptic = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onClick = onClick
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = if (active) glow else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

private suspend fun PointerInputScope.detectCoreTap(
    interaction: NingguangInteraction,
    scope: CoroutineScope,
    onPressHaptic: () -> Unit,
    onClick: () -> Unit
) {
    detectTapGestures(
        onPress = { offset ->
            val nx = (offset.x / size.width).coerceIn(0f, 1f)
            val ny = (offset.y / size.height).coerceIn(0f, 1f)
            scope.launch { interaction.pressAt(Offset(nx, ny)) }
            onPressHaptic()
            tryAwaitRelease()
            scope.launch { interaction.release() }
        },
        onTap = { onClick() }
    )
}
