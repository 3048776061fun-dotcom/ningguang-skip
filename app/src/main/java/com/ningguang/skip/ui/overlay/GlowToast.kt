package com.ningguang.skip.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ningguang.skip.R

/**
 * 跳过时的凝光悬浮胶囊。
 *
 * 用原生 View 实现，不走 Compose —— 悬浮窗的 context 不是 Activity，
 * 用 ComposeView 需要自己造 LifecycleOwner / SavedStateRegistry 宿主，
 * 而 ViewTreeLifecycleOwner 这类宿主类在编译期不可见，容易踩坑。
 * 原生 View 没有这个依赖，也能做出同样的玻璃质感。
 *
 * 窗口带 NOT_TOUCHABLE / NOT_FOCUSABLE，只做展示，绝不拦截用户对下层应用的点击。
 */
object GlowToast {

    private const val SHOW_MS = 1800L
    private const val EXIT_MS = 340L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var view: View? = null
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
            removeNow()

            val capsule = buildCapsule(context, appName, text)
            val added = runCatching {
                manager.addView(capsule, buildParams(context))
                true
            }.getOrDefault(false)
            if (!added) return@post

            wm = manager
            view = capsule

            // 入场：从上方滑入 + 淡入
            capsule.alpha = 0f
            capsule.translationY = -56f
            capsule.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260)
                .start()

            scheduleHide()
        }
    }

    /** 服务停止时收干净，避免悬浮窗残留 */
    fun release() {
        mainHandler.post {
            cancelPendingHide()
            removeNow()
        }
    }

    private fun scheduleHide() {
        val r = Runnable {
            val v = view ?: return@Runnable
            v.animate()
                .alpha(0f)
                .translationY(-48f)
                .setDuration(EXIT_MS)
                .withEndAction { removeNow() }
                .start()
        }
        hideRunnable = r
        mainHandler.postDelayed(r, SHOW_MS)
    }

    private fun cancelPendingHide() {
        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        hideRunnable = null
    }

    private fun removeNow() {
        val manager = wm
        val v = view
        if (manager != null && v != null) {
            runCatching { manager.removeView(v) }
        }
        wm = null
        view = null
    }

    /* ------------------------------------------------------------------ */
    /* 胶囊本体                                                            */
    /* ------------------------------------------------------------------ */

    private fun buildCapsule(context: Context, appName: String, text: String): View {
        val dp = context.resources.displayMetrics.density
        val padH = (18 * dp).toInt()
        val padV = (9 * dp).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xE61A1F2C.toInt(), 0xE60E1220.toInt())
            ).apply {
                cornerRadius = 30 * dp
                // 光感描边：一道偏冷的高光边，模拟凝光的边缘光
                setStroke((1.5 * dp).toInt(), 0x804FD8FF.toInt())
            }
            elevation = 14 * dp
        }

        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_glow_bolt)
            setColorFilter(0xFF4FD8FF.toInt())
            layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply {
                marginEnd = (10 * dp).toInt()
            }
        }
        root.addView(icon)

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(context).apply {
            this.text = "已跳过 ${appName.ifBlank { "广告" }} 的广告"
            setTextColor(0xFFF2F5FB.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            paint.isFakeBoldText = true
            maxLines = 1
        }
        textCol.addView(title)

        if (text.isNotBlank()) {
            val sub = TextView(context).apply {
                this.text = text
                setTextColor(0xFF9AA4BA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
            }
            textCol.addView(sub)
        }
        root.addView(textCol)
        return root
    }

    private fun buildParams(context: Context): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val dp = context.resources.displayMetrics.density
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
            // 落在状态栏下方，不遮挡时钟与通知
            y = (36 * dp).toInt()
            x = 0
            windowAnimations = 0
        }
    }
}
