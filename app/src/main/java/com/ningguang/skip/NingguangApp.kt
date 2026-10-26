package com.ningguang.skip

import android.app.Application
import com.ningguang.skip.data.Prefs
import com.ningguang.skip.ui.glow.NingguangConfig
import com.ningguang.skip.util.Notify
import com.ningguang.skip.worker.SubscriptionWorker

class NingguangApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Notify.ensureChannels(this)
        NingguangConfig.enabled = Prefs.settings.glowEnabled
        NingguangConfig.intensity = Prefs.settings.glowIntensity
        // 自动更新默认开启；没有订阅源时 Worker 会立刻空跑返回，不产生网络请求
        if (Prefs.settings.autoUpdateSubscription) {
            SubscriptionWorker.enableAutoUpdate(this)
        }
    }
}
