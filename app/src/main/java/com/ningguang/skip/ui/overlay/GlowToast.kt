package com.ningguang.skip.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.ningguang.skip.ui.glow.ningguangGlass
import com.ningguang.skip.ui.theme.GlowCyan
import com.ningguang.skip.ui.theme.NingguangTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

/**
 * 跳过时的凝光悬浮提示。
 *
 * 在别的应用上方弹一枚半透明玻璃胶囊，1.8 秒后自动向上滑出消失。
 * 窗口带 FLAG_NOT_TOUCHABLE / FLAG_NOT_FOCUSABLE，只做展示，绝不拦截用户的点击与输入。
 */
object GlowToast {

    private const val SHOW_MS = 1800L
    private const val EXIT_MS = 340L

    data class Payload(val appName: String, val text: String, val at: Long = System.currentTimeMillis())

    private val current = MutableStateFlow<Payload?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var wm: WindowManager? = null
    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var hideRunnable: Runnable? = null

    fun canOverlay(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        return Settings.canDrawOverlays(context)
    }

    fun show(context: Context, appName: String, text: String) {
        if (!canOverlay(context)) return
        mainHandler.post {
            val manager = context.getSystemService(WindowManager::class.java) ?: return@post
            cancelPendingHide()
            current.value = Payload(appName, text)
            if (view == null) attach(manager, context)
            scheduleHide()
        }
    }

    /** 服务停止时把窗口收干净，避免残留 */
    fun release() {
        mainHandler.post {
            cancelPendingHide()
            current.value = null
            detach()
        }
    }

    @SuppressLint("InflateParams")
    private fun attach(manager: WindowManager, context: Context) {
        val lifecycleOwner = OverlayLifecycleOwner().apply { start() }
        val composeView = ComposeView(context).apply {
            ViewTreeLifecycleOwner.set(this, lifecycleOwner)
            ViewTreeSavedStateRegistryOwner.set(this, lifecycleOwner)
            setContent {
                NingguangTheme {
                    val payload by current.collectAsStateWithLifecycle()
                    GlowCapsule(
                        payload = payload,
                        onExitFinished = { detach() }
                    )
                }
            }
        }
        runCatching {
            manager.addView(composeView, buildParams(context))
            wm = manager
            view = composeView
            owner = lifecycleOwner
        }.onFailure {
            lifecycleOwner.destroy()
        }
    }

    private fun detach() {
        val manager = wm
        val v = view
        if (manager != null && v != null) runCatching { manager.removeView(v) }
        owner?.destroy()
        wm = null
        view = null
        owner = null
    }

    private fun scheduleHide() {
        val r = Runnable {
            current.value = null
            hideRunnable = Runnable { detach() }
            mainHandler.postDelayed(hideRunnable!!, EXIT_MS)
        }
        hideRunnable = r
        mainHandler.postDelayed(r, SHOW_MS)
    }

    private fun cancelPendingHide() {
        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        hideRunnable = null
    }

    private fun buildParams(context: Context): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // 落在状态栏下方一点，不遮挡时钟与通知（y 是像素，要按密度换算）
            y = (36 * context.resources.displayMetrics.density).roundToInt()
            x = 0
            windowAnimations = 0
        }
    }
}

/* ---------------------------------------------------------------------------
 * 凝光胶囊
 * ------------------------------------------------------------------------- */

@Composable
private fun GlowCapsule(payload: GlowToast.Payload?, onExitFinished: () -> Unit) {
    val configuration = LocalConfiguration.current
    val maxWidth = (configuration.screenWidthDp.dp - 56.dp).coerceAtLeast(200.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = payload != null,
            enter = slideInVertically(initialOffsetY = { -it }) +
                fadeIn() +
                scaleIn(initialScale = 0.88f),
            exit = slideOutVertically(targetOffsetY = { -it }) +
                fadeOut() +
                scaleOut(targetScale = 0.92f)
        ) {
            if (payload != null) {
                CapsuleBody(payload = payload, maxWidth = maxWidth)
            }
        }
    }

    LaunchedEffect(payload == null) {
        if (payload == null) {
            // 等退出动画播完再拆窗口，避免胶囊直接闪没
            delay(340)
            onExitFinished()
        }
    }
}

@Composable
private fun CapsuleBody(payload: GlowToast.Payload, maxWidth: Dp) {
    val shape = RoundedCornerShape(30.dp)
    val cs = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .height(58.dp)
            .then(Modifier.ningguangGlass(shape = shape, glow = GlowCyan, stateGlow = 0.85f))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = null,
                tint = GlowCyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "已跳过 ${payload.appName.ifBlank { "广告" }} 的广告",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                if (payload.text.isNotBlank()) {
                    Text(
                        text = payload.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/* ---------------------------------------------------------------------------
 * 悬浮窗里的生命周期宿主（Compose 需要）
 * ------------------------------------------------------------------------- */

private class OverlayLifecycleOwner :
    LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun start() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
