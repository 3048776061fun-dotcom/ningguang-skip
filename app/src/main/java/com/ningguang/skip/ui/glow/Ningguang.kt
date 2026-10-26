package com.ningguang.skip.ui.glow

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ningguang.skip.ui.theme.GlassDarkBottom
import com.ningguang.skip.ui.theme.GlassDarkTop
import com.ningguang.skip.ui.theme.GlassLightBottom
import com.ningguang.skip.ui.theme.GlassLightTop
import com.ningguang.skip.ui.theme.GlowCyan
import com.ningguang.skip.ui.theme.GlowViolet
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/* ============================================================================
 * 凝光视效（Luminous Effect）
 *
 * 三件事做到位就有那个味儿：
 *   1) 材质：半透玻璃 + 顶部锋利高光边 + 底部柔和反射光 + 彩色投影（不是一块半透色块）
 *   2) 光必须有意图：光跟随手指弥散、随状态常亮，用来指引注意力
 *   3) 柔性反馈：按下收缩、松手弹性回弹，视觉与震动同步
 * ========================================================================== */

/** 全局光效强度：0 = 关闭（省电），1 = 满血。由设置页控制并透传到这里 */
object NingguangConfig {
    var intensity: Float = 1f
    var enabled: Boolean = true
}

private fun Float.gated() = (this * (if (NingguangConfig.enabled) NingguangConfig.intensity else 0f)).coerceIn(0f, 1f)

/* ---------------------------------------------------------------------------
 * 交互状态：按压进度 + 归一化触点
 * ------------------------------------------------------------------------- */

class NingguangInteraction internal constructor() {
    private val _press = Animatable(0f)
    private val _touch = Animatable(Offset(0.5f, 0.5f), Offset.VectorConverter)

    /** 0 = 未按下，1 = 完全按下（弹簧值，会超过 1 一点点，这就是回弹的来源） */
    val press: Float get() = _press.value
    val pressState get() = _press.asState()
    val touch: Offset get() = _touch.value
    val touchState get() = _touch.asState()

    internal suspend fun pressAt(point: Offset) {
        coroutineScope {
            // 光点追手与按压回弹并行跑，光才有「跟手」的滞后感
            launch { _touch.animateTo(point, spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.72f)) }
            _press.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    internal suspend fun release() {
        _press.animateTo(
            0f,
            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
        )
    }

    internal suspend fun moveTo(point: Offset) {
        _touch.animateTo(point, spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.85f))
    }
}

@Composable
fun rememberNingguangInteraction(): NingguangInteraction = remember { NingguangInteraction() }

/* ---------------------------------------------------------------------------
 *  Modifier：凝光玻璃材质
 * ------------------------------------------------------------------------- */

/**
 * 凝光玻璃层。必须放在 clip 之前，配合 [shape] 使用。
 *
 * @param glow 光的颜色（通常取语义色：成功=青、主操作=蓝、危险=暖）
 * @param stateGlow 0..1，与手指无关的「状态光」，用来表达开关打开 / 服务运行中
 */
fun Modifier.ningguangGlass(
    shape: Shape,
    glow: Color = GlowCyan,
    interaction: NingguangInteraction? = null,
    stateGlow: Float = 0f,
    dark: Boolean = false
): Modifier = composed {
    val press = interaction?.pressState?.value ?: 0f
    val touch = interaction?.touchState?.value ?: Offset(0.5f, 0.5f)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    this
        .graphicsLayer {
            // Screen 混合需要离屏合成，否则会把整块窗口内容一起提亮
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            // 静态部分缓存：玻璃基底
            val glassBrush = Brush.verticalGradient(
                colors = if (dark) listOf(GlassDarkTop, GlassDarkBottom)
                else listOf(GlassLightTop, GlassLightBottom)
            )
            onDrawBehind {
                drawOutline(outline = outline, brush = glassBrush, style = Fill)
            }
        }
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val w = size.width
            val h = size.height

            // —— 1. 手指弥散光：跟着触点走，按下越实光越强 ——
            val pressGlow = press.coerceIn(0f, 1f).gated()
            if (pressGlow > 0.01f) {
                val cx = touch.x * w
                val cy = touch.y * h
                val radius = (w.coerceAtLeast(h) * 0.62f) * (0.55f + 0.45f * pressGlow)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glow.copy(alpha = 0.42f * pressGlow),
                            glow.copy(alpha = 0.16f * pressGlow),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = radius
                    ),
                    center = Offset(cx, cy),
                    radius = radius,
                    blendMode = BlendMode.Screen
                )
            }

            // —— 2. 状态光：从底部聚起的一层柔和反射光 ——
            val sg = stateGlow.gated()
            if (sg > 0.01f) {
                drawOutline(
                    outline = outline,
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to glow.copy(alpha = 0.20f * sg)
                    ),
                    style = Fill,
                    blendMode = BlendMode.Screen
                )
            }

            // —— 3. 质感模糊：在玻璃层上做一次轻模糊，模拟亚克力颗粒 ——
            // （真正的实时背景模糊由 NingguangBackground 的底层光斑提供，这里只做材质层）

            // —— 4. 光感描边：顶部锋利高光 + 底部柔和反射边 ——
            if (NingguangConfig.enabled) {
                val strokeW = with(density) { 1.4.dp.toPx() }
                drawOutline(
                    outline = outline,
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (dark) 0.34f else 0.85f),
                        0.32f to Color.White.copy(alpha = 0.05f),
                        0.78f to Color.White.copy(alpha = 0.03f),
                        1f to Color.White.copy(alpha = if (dark) 0.14f else 0.38f)
                    ),
                    style = Stroke(width = strokeW)
                )
            }
        }
}

/** 兼容处理：Android 12 以下没有 RenderEffect，退化成无模糊的纯渐变玻璃 */
fun Modifier.ningguangBlur(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= 31 && NingguangConfig.enabled) {
        this.blur(radius, blendedEdgeColor = Color.Transparent)
    } else this

/* ---------------------------------------------------------------------------
 *  全屏背景：呼吸流动的两团大光斑
 * ------------------------------------------------------------------------- */

@Composable
fun NingguangBackground(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val topColor = if (dark) Color(0xFF0C0F18) else Color(0xFFF4F6FB)
    val bottomColor = if (dark) Color(0xFF05070C) else Color(0xFFE9EEF8)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
            .drawBehind {
                if (!NingguangConfig.enabled) return@drawBehind
                val i = NingguangConfig.intensity
                val w = size.width
                val h = size.height
                // 两团缓慢呼吸的光，给玻璃提供「透」的底子
                val r1 = w * 0.85f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(GlowCyan.copy(alpha = 0.30f * i), Color.Transparent),
                        center = Offset(w * 0.15f, h * 0.12f),
                        radius = r1
                    ),
                    radius = r1,
                    center = Offset(w * 0.15f, h * 0.12f),
                    blendMode = BlendMode.Screen
                )
                val r2 = w * 0.95f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(GlowViolet.copy(alpha = 0.26f * i), Color.Transparent),
                        center = Offset(w * 0.92f, h * 0.62f),
                        radius = r2
                    ),
                    radius = r2,
                    center = Offset(w * 0.92f, h * 0.62f),
                    blendMode = BlendMode.Screen
                )
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        content = content
    )
}
