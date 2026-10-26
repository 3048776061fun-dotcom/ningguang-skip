package com.ningguang.skip.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ningguang.skip.data.Prefs
import com.ningguang.skip.engine.SubscriptionRepository
import com.ningguang.skip.util.Notify
import java.util.concurrent.TimeUnit

/**
 * 订阅自动更新。
 *
 * 每 24 小时跑一次（系统实际调度会有些浮动），只在联网时执行，且只在用户真的添加了订阅源之后
 * 才产生网络请求 —— 没有源就安静待着，保持 App 默认形态不联网。
 */
class SubscriptionWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!Prefs.settings.autoUpdateSubscription) return Result.success()
        val subs = Prefs.subscriptions.filter { it.enabled }
        if (subs.isEmpty()) return Result.success()

        var failed = 0
        var added = 0
        var updated = 0
        subs.forEach { sub ->
            val result = SubscriptionRepository.refresh(sub)
            if (result.ok) {
                added += result.added
                updated += result.updated
            } else {
                failed++
            }
        }

        val settings = Prefs.settings
        if (settings.notifySubscriptionUpdate && (added > 0 || updated > 0)) {
            Notify.showSubscriptionUpdate(applicationContext, added, updated, failed)
        }
        return if (failed == subs.size) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC = "ningguang_sub_periodic"
        private const val ONCE = "ningguang_sub_once"

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enableAutoUpdate(context: Context) {
            val request = PeriodicWorkRequestBuilder<SubscriptionWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints())
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request
                )
            }
        }

        fun disableAutoUpdate(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(PERIODIC) }
        }

        /** 立即检查一次（设置页的「立即检查」按钮走这里，不占用周期任务） */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                .setConstraints(constraints())
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONCE, ExistingWorkPolicy.REPLACE, request
                )
            }
        }
    }
}
