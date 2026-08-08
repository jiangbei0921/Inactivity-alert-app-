package com.sitbreak.app

import android.app.Application
import com.sitbreak.app.work.DailySummaryWorker
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt 依赖注入入口。
 *
 * 这里只做两件事：触发 Hilt 组件生成、注册每日小结的周期任务。
 * 任何耗时初始化都不放在 onCreate，避免拖慢冷启动。
 */
@HiltAndroidApp
class SitBreakApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // enqueueUniquePeriodicWork + KEEP 保证幂等，重复冷启动不会累积任务
        DailySummaryWorker.schedule(this)
    }
}
