package com.ningguang.skip.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.ningguang.skip.data.Prefs
import com.ningguang.skip.engine.SkipEngine
import com.ningguang.skip.ui.overlay.GlowToast
import com.ningguang.skip.util.Notify

class SkipAdService : AccessibilityService() {

    private lateinit var engine: SkipEngine

    private var lastHandleAt = 0L
    private var lastKey = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = SkipEngine(this)
        Notify.ensureChannels(this)
        Prefs.setServiceRunning(true)
        Notify.showStatus(this, true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // 内容变化事件非常密集，做一层节流：同一个窗口 320ms 内只扫一次
        val pkg = event.packageName?.toString() ?: return
        val key = pkg + (event.className ?: "")
        val now = android.os.SystemClock.uptimeMillis()
        val isStateChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!isStateChanged && now - lastHandleAt < 320 && key == lastKey) return
        lastHandleAt = now
        lastKey = key

        runCatching {
            engine.onWindowChanged(event.packageName, event.className)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        GlowToast.release()
        Prefs.setServiceRunning(false)
        Notify.cancelStatus(this)
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        GlowToast.release()
        Prefs.setServiceRunning(false)
        Notify.cancelStatus(this)
        return super.onUnbind(intent)
    }
}
