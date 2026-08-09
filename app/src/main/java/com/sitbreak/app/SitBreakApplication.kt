package com.sitbreak.app

import android.app.Application
import android.util.Log
import com.sitbreak.app.work.DailySummaryWorker
import com.sitbreak.app.work.UpdateCheckWorker
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
        // 尽早安装全局崩溃捕获，确保能记录启动期（含 Application 之后）的未捕获异常。
        CrashReporter.install(this)
        // enqueueUniquePeriodicWork + KEEP 保证幂等，重复冷启动不会累积任务。
        // 用 try-catch 包裹，避免 WorkManager 在极少数 ROM 上初始化异常导致整个应用启动即崩。
        try {
            DailySummaryWorker.schedule(this)
        } catch (e: Exception) {
            Log.e("SitBreakApplication", "schedule DailySummaryWorker failed", e)
        }
        // 更新检查同样是可延迟任务，单独 try-catch 保证它出问题不牵连小结任务
        try {
            UpdateCheckWorker.schedule(this)
        } catch (e: Exception) {
            Log.e("SitBreakApplication", "schedule UpdateCheckWorker failed", e)
        }
    }
}
