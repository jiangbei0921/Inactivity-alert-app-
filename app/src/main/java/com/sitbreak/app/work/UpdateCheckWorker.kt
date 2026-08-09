package com.sitbreak.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sitbreak.app.update.UpdatePreferences
import com.sitbreak.app.update.UpdateRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 每日静默检查新版本。
 *
 * 这个 Worker **只检查、不下载**。理由很实际：
 * - 检查一次只有几百字节，随手做；下载则涉及流量与存储，应该由用户点头。
 * - 后台自动下载一旦失败（弱网、存储满），用户既看不见也无从处理，只会变成隐形耗电。
 * 发现新版就发一条低优先级通知，用户点进来再决定要不要更新——这才是不打扰的做法。
 *
 * 依赖获取沿用项目既有取舍：不引入 `hilt-work`（会牵扯 Application 实现
 * Configuration.Provider 并移除默认 startup 初始化器），改用 [EntryPointAccessors]
 * 从 Hilt 单例组件里取依赖，零启动期风险，拿到的仍是同一批单例。
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun updateRepository(): UpdateRepository
        fun updatePreferences(): UpdatePreferences
    }

    override suspend fun doWork(): Result {
        return try {
            val deps = EntryPointAccessors.fromApplication(
                applicationContext,
                Dependencies::class.java,
            )
            if (!deps.updatePreferences().autoCheckEnabled.first()) {
                return Result.success()  // 用户关了自动检查，安静退出
            }
            // 挂起到检查真正结束，避免进程在网络回包前被回收
            deps.updateRepository().checkNow(silent = true)
            Result.success()
        } catch (_: Exception) {
            // 检查失败不值得立刻重试消耗电量，等明天的周期任务
            Result.success()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "update_check"

        /** 幂等调度：每次冷启动都可安全调用。 */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                // 刚装好就查一次没意义，且会和首启的其他初始化抢资源
                .setInitialDelay(3, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
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
