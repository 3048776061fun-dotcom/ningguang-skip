package com.ningguang.skip.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 凝光视效配色。
 *
 * 设计要点（对齐 ColorOS 17 的三条视觉规则）：
 * 1. 材质 = 模糊 + 亚克力半透 + 边缘高光，而不是一块死板的半透明色块；
 * 2. 光是有意图的：光跟随手指、随状态流动，用来指引注意力，而不是纯装饰；
 * 3. 可读性第一：无论深浅色，光效都不能压过文字。
 */

// —— 主色（OPPO 系偏蓝）——
val Blue60 = Color(0xFF1A6BFF)
val Blue50 = Color(0xFF4A8CFF)
val BlueDark = Color(0xFF7FB0FF)

// —— 凝光光源色 ——
val GlowCyan = Color(0xFF4FD8FF)
val GlowViolet = Color(0xFFB69CFF)
val GlowWarm = Color(0xFFFFB86B)
val GlowGreen = Color(0xFF5BE7B0)

// —— 浅色 ——
val LightBgTop = Color(0xFFF4F6FB)
val LightBgBottom = Color(0xFFE8EDF7)
val LightSurface = Color(0xFFFFFFFF)
val LightTextPrimary = Color(0xFF101420)
val LightTextSecondary = Color(0xFF5A6478)
val LightStroke = Color(0x1A101420)

// —— 深色 ——
val DarkBgTop = Color(0xFF0C0F18)
val DarkBgBottom = Color(0xFF05070C)
val DarkSurface = Color(0xFF161A24)
val DarkTextPrimary = Color(0xFFF2F5FB)
val DarkTextSecondary = Color(0xFF9AA4BA)
val DarkStroke = Color(0x26FFFFFF)

/** 卡片在浅色下的玻璃基色 */
val GlassLightTop = Color(0xB3FFFFFF)
val GlassLightBottom = Color(0x66FFFFFF)

/** 卡片在深色下的玻璃基色 */
val GlassDarkTop = Color(0x26FFFFFF)
val GlassDarkBottom = Color(0x0FFFFFFF)

/** 凝光要自己判断明暗：浅色下描边走哑光细边，深色下走柔和高亮边 */
val androidx.compose.material3.ColorScheme.isLightTheme: Boolean
    get() = background.luminance() > 0.5f
