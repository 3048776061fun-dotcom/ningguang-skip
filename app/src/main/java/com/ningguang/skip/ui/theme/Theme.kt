package com.ningguang.skip.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Blue60,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF0A2E6B),
    secondary = GlowCyan,
    onSecondary = Color(0xFF00303D),
    tertiary = GlowViolet,
    background = LightBgTop,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFEEF1F8),
    onSurfaceVariant = LightTextSecondary,
    outline = LightStroke
)

private val DarkColors = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF062048),
    primaryContainer = Color(0xFF122A52),
    onPrimaryContainer = Color(0xFFCFE0FF),
    secondary = GlowCyan,
    onSecondary = Color(0xFF00303D),
    tertiary = GlowViolet,
    background = DarkBgTop,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = Color(0xFF1D2230),
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkStroke
)

/**
 * 凝光主题。
 * @param followSystem 是否跟随系统深浅色；false 时由 [darkTheme] 决定
 * @param dynamicColorInLight 预留参数，暂不使用（凝光依赖固定光色，动态取色会破坏光效一致性）
 */
@Composable
fun NingguangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    followSystem: Boolean = true,
    content: @Composable () -> Unit
) {
    val useDark = if (followSystem) isSystemInDarkTheme() else darkTheme
    val colorScheme = if (useDark) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // 悬浮提示跑在 WindowManager 里，context 不是 Activity，此时跳过窗口栏相关设置
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // 让内容可以绘制到状态栏/导航栏之下，凝光的光才能铺满整屏
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !useDark
            controller.isAppearanceLightNavigationBars = !useDark
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NingguangTypography,
        content = content
    )
}
