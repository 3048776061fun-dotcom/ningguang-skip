package com.ningguang.skip.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ningguang.skip.ui.glow.NingguangCard
import com.ningguang.skip.ui.theme.GlowCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun timeText(time: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(time))

fun dateText(time: Long): String =
    SimpleDateFormat("MM月dd日", Locale.getDefault()).format(Date(time))

fun dateTimeText(time: Long): String =
    if (time <= 0L) "从未更新" else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(time))

/** 半屏弹层外壳：背景压暗 + 从下往上弹性浮起，点击空白关闭 */
@Composable
fun NingguangSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val slide = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        slide.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .offset(y = (slide.value * 420f).dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* 吃掉点击，避免穿透关闭 */ }
                )
        ) {
            NingguangCard(
                modifier = Modifier.fillMaxWidth(),
                glow = GlowCyan,
                stateGlow = 0.6f,
                elevation = 24.dp,
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 22.dp)
            .padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun StatCard(
    value: Int,
    label: String,
    glow: Color,
    modifier: Modifier = Modifier
) {
    // 数字用弹簧过渡，跳过一次时有个「弹一下」的反馈
    val shown by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 220f),
        label = "stat"
    )
    NingguangCard(
        modifier = modifier,
        glow = glow,
        stateGlow = 0.5f,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = shown.toInt().toString(),
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                color = glow
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
