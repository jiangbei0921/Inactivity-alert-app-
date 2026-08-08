package com.sitbreak.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sitbreak.app.data.CheckInRepository
import com.sitbreak.app.data.db.AppDatabase
import com.sitbreak.app.notification.NotificationHelper
import com.sitbreak.app.ui.home.HomeStatsCalculator
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * 每日小结 Worker：每天汇总「昨天」的站立次数并发一条低优先级通知。
 *
 * 为什么用 WorkManager 而不是 AlarmManager / 前台服务里 sleep 到第二天：
 * - 小结是可延迟任务，WorkManager 会自己合并唤醒、遵守 Doze，不额外耗电；
 * - 进程被杀 / 设备重启后调度仍然保留，不需要自己写 BOOT_COMPLETED 恢复逻辑；
 * - `KEEP` 策略保证重复 enqueue 不会产生多份任务。
 *
 * 依赖注入取舍：这里刻意没有引入 `androidx.hilt:hilt-work` + `@HiltWorker`。
 * 接入 HiltWorkerFactory 需要 Application 实现 `Configuration.Provider`，
 * 并在 Manifest 里移除 WorkManager 的默认 `androidx.startup` 初始化器；
 * 为了一个无参依赖的 Worker 付出这份启动期配置风险不划算，
 * 因此在 Worker 内部按 Context 自行组装依赖（依赖本身仍是单例，无重复开销）。
 */
class DailySummaryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = CheckInRepository(AppDatabase.getInstance(applicationContext).checkInDao())
            val (start, end) = DailySummarySchedule.yesterdayRange()
            val standCount = repository.getTodayStandCount(start, end)
            val activeMinutes = (HomeStatsCalculator.activeHours(standCount) * 60).roundToInt()

            NotificationHelper().sendDailySummary(applicationContext, standCount, activeMinutes)
            Result.success()
        } catch (_: Exception) {
            // 小结失败不值得反复重试消耗电量，直接放弃本次，等明天的周期任务
            Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "daily_summary"

        /** 幂等调度：每次冷启动都可安全调用。 */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(DailySummarySchedule.initialDelayMinutes(), TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
