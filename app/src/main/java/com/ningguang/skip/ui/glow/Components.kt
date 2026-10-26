package com.ningguang.skip.ui.glow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ningguang.skip.ui.theme.isLightTheme
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(26.dp)
private val ItemShape = RoundedCornerShape(20.dp)

/* ---------------------------------------------------------------------------
 * 凝光卡片
 * ------------------------------------------------------------------------- */

@Composable
fun NingguangCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CardShape,
    glow: Color = MaterialTheme.colorScheme.primary,
    stateGlow: Float = 0f,
    elevation: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = rememberNingguangInteraction()
    val press = interaction.pressState.value
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val clickable = onClick != null

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = 1f - 0.032f * press
                scaleX = s
                scaleY = s
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                spotColor = glow.copy(alpha = 0.35f),
                ambientColor = glow.copy(alpha = 0.18f)
            )
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .ningguangGlass(
                    shape = shape,
                    glow = glow,
                    interaction = interaction,
                    stateGlow = stateGlow
                )
                .then(
                    if (clickable) {
                        Modifier.pointerInput(onClick) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val nx = (offset.x / size.width).coerceIn(0f, 1f)
                                    val ny = (offset.y / size.height).coerceIn(0f, 1f)
                                    scope.launch { interaction.pressAt(Offset(nx, ny)) }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    tryAwaitRelease()
                                    scope.launch { interaction.release() }
                                },
                                onTap = { onClick?.invoke() }
                            )
                        }
                    } else Modifier
                )
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    }
}

/* ---------------------------------------------------------------------------
 * 凝光按钮（柔性反馈：按下收缩，松手回弹）
 * ------------------------------------------------------------------------- */

@Composable
fun NingguangButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    glow: Color = MaterialTheme.colorScheme.primary,
    filled: Boolean = true,
    enabled: Boolean = true
) {
    val interaction = rememberNingguangInteraction()
    val press = interaction.pressState.value
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(24.dp)
    val cs = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.45f

    Box(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                val s = 1f - 0.05f * press
                scaleX = s
                scaleY = s
                this.alpha = alpha
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .shadow(
                elevation = if (filled) 12.dp else 4.dp,
                shape = shape,
                spotColor = glow.copy(alpha = 0.4f),
                ambientColor = glow.copy(alpha = 0.2f)
            )
            .clip(shape)
            .then(
                if (filled) Modifier.drawBehind {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(glow, glow.copy(alpha = 0.72f))
                        ),
                        cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                    )
                    // 顶部锋利高光
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.42f),
                            0.35f to Color.White.copy(alpha = 0.0f),
                            1f to Color.White.copy(alpha = 0.10f)
                        ),
                        cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                    )
                } else Modifier
            )
            .ningguangGlass(
                shape = shape,
                glow = glow,
                interaction = interaction,
                stateGlow = if (filled) 0f else 0.35f
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = { offset ->
                        val nx = (offset.x / size.width).coerceIn(0f, 1f)
                        val ny = (offset.y / size.height).coerceIn(0f, 1f)
                        scope.launch { interaction.pressAt(Offset(nx, ny)) }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tryAwaitRelease()
                        scope.launch { interaction.release() }
                    },
                    onTap = { onClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 18.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
                    tint = if (filled) Color.White else cs.primary)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (filled) Color.White else cs.primary
            )
        }
    }
}

/* ---------------------------------------------------------------------------
 * 凝光开关：滑块带光晕，拖动时拉伸（柔性反馈）
 * ------------------------------------------------------------------------- */

@Composable
fun NingguangSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    glow: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
) {
    val press = remember { Animatable(0f) }
    val pressValue = press.asState().value
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val cs = MaterialTheme.colorScheme

    val t by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "switch"
    )

    val w = 54.dp
    val h = 32.dp
    val pad = 3.dp
    val thumbBase = 26.dp
    // 按住时滑块被拉长，松手弹回 —— 这就是「果冻感」
    val thumbW = thumbBase + (6.dp * pressValue.coerceIn(0f, 1f))

    Box(
        modifier = modifier
            .size(w, h)
            .graphicsLayer { this.alpha = if (enabled) 1f else 0.4f }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        scope.launch { press.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
                        tryAwaitRelease()
                        scope.launch { press.animateTo(0f, spring(dampingRatio = 0.6f)) }
                    },
                    onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCheckedChange(!checked)
                    }
                )
            }
            .drawBehind {
                val wPx = size.width
                val hPx = size.height
                val padPx = pad.toPx()
                val thumbWPx = thumbW.toPx()
                val thumbHPx = thumbBase.toPx()
                val travel = wPx - padPx * 2 - thumbWPx
                val thumbX = padPx + travel * t

                // 轨道
                val trackColor = if (checked) glow else {
                    if (cs.isLightTheme) Color(0x33101420) else Color(0x33FFFFFF)
                }
                drawRoundRect(
                    color = trackColor.copy(alpha = if (checked) 0.55f else 1f),
                    cornerRadius = CornerRadius(hPx / 2, hPx / 2)
                )
                // 轨道内光晕：跟随滑块位置，光随状态流动
                if (NingguangConfig.enabled && checked) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(glow.copy(alpha = 0.75f), Color.Transparent),
                            center = Offset(thumbX + thumbWPx / 2, hPx / 2),
                            radius = wPx * 0.42f
                        ),
                        radius = wPx * 0.42f,
                        center = Offset(thumbX + thumbWPx / 2, hPx / 2),
                        blendMode = BlendMode.Screen
                    )
                }
                // 轨道描边
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (cs.isLightTheme) 0.55f else 0.24f),
                        1f to Color.White.copy(alpha = 0.06f)
                    ),
                    cornerRadius = CornerRadius(hPx / 2, hPx / 2),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                // 滑块
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White, Color(0xFFE9EEFA))
                    ),
                    topLeft = Offset(thumbX, (hPx - thumbHPx) / 2),
                    size = Size(thumbWPx, thumbHPx),
                    cornerRadius = CornerRadius(thumbHPx / 2, thumbHPx / 2)
                )
                // 滑块边缘高光
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.9f),
                        0.5f to Color.White.copy(alpha = 0.1f),
                        1f to glow.copy(alpha = 0.35f)
                    ),
                    topLeft = Offset(thumbX, (hPx - thumbHPx) / 2),
                    size = Size(thumbWPx, thumbHPx),
                    cornerRadius = CornerRadius(thumbHPx / 2, thumbHPx / 2),
                    style = Stroke(width = 1.1.dp.toPx())
                )
            }
    )
}

/* ---------------------------------------------------------------------------
 * 列表项 + 分区标题
 * ------------------------------------------------------------------------- */

@Composable
fun NingguangListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    glow: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    NingguangCard(
        modifier = modifier.fillMaxWidth(),
        shape = ItemShape,
        glow = glow,
        elevation = 4.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .drawBehind {
                            drawRoundRect(
                                brush = Brush.radialGradient(
                                    listOf(iconTint.copy(alpha = 0.30f), Color.Transparent),
                                    center = Offset(size.width / 2, size.height * 0.75f),
                                    radius = size.width * 0.72f
                                ),
                                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/* ---------------------------------------------------------------------------
 * 浮岛式导航栏：选中态药丸会「弹」到目标位置
 * ------------------------------------------------------------------------- */

data class IslandNavItem(val label: String, val icon: ImageVector)

@Composable
fun FloatingIslandNavBar(
    items: List<IslandNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    glow: Color = MaterialTheme.colorScheme.primary
) {
    val shape = RoundedCornerShape(32.dp)
    val cs = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        val barWidth = maxWidth
        val barHeight = 66.dp
        val itemWidth = barWidth / items.size
        val pillWidth = itemWidth - 8.dp

        val t by animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "island"
        )

        Box(
            modifier = Modifier
                .width(barWidth)
                .height(barHeight)
                .shadow(
                    elevation = 18.dp,
                    shape = shape,
                    spotColor = glow.copy(alpha = 0.28f),
                    ambientColor = glow.copy(alpha = 0.14f)
                )
                .clip(shape)
                .ningguangGlass(shape = shape, glow = glow, stateGlow = 0.55f)
        ) {
            // 选中药丸
            Box(
                modifier = Modifier
                    .offset(x = 4.dp + itemWidth * t)
                    .width(pillWidth)
                    .height(50.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(25.dp))
                    .drawBehind {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(glow.copy(alpha = 0.26f), glow.copy(alpha = 0.10f))
                            ),
                            cornerRadius = CornerRadius(25.dp.toPx(), 25.dp.toPx())
                        )
                        if (NingguangConfig.enabled) {
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.White.copy(alpha = 0.55f),
                                    0.4f to Color.White.copy(alpha = 0.05f),
                                    1f to glow.copy(alpha = 0.28f)
                                ),
                                cornerRadius = CornerRadius(25.dp.toPx(), 25.dp.toPx()),
                                style = Stroke(width = 1.3.dp.toPx())
                            )
                        }
                    }
            )

            Row(modifier = Modifier.matchParentSize()) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val interaction = rememberNingguangInteraction()
                    val press = interaction.pressState.value
                    val scope = rememberCoroutineScope()
                    val haptic = LocalHapticFeedback.current
                    val iconScale by animateFloatAsState(
                        targetValue = 1f - 0.12f * press,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "icon"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(barHeight)
                            .pointerInput(index) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        val nx = (offset.x / size.width).coerceIn(0f, 1f)
                                        val ny = (offset.y / size.height).coerceIn(0f, 1f)
                                        scope.launch { interaction.pressAt(Offset(nx, ny)) }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        tryAwaitRelease()
                                        scope.launch { interaction.release() }
                                    },
                                    onTap = { if (!selected) onSelect(index) }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selected) cs.primary else cs.onSurfaceVariant,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) cs.primary else cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
