package com.sitbreak.app.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.sitbreak.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 增量更新的编排中枢：检查 → 选路 → 下载 → 合成 → 校验 → 交付安装。
 *
 * ## 为什么是 Singleton + 自有 CoroutineScope
 * 更新一旦开始就不该被「用户切到别的页面」打断。这里用应用级作用域承载任务，
 * ViewModel 只是订阅 [state]；离开设置页、甚至关掉界面，下载都会继续，
 * 回来时还能看到当前进度。计时服务跑在自己的前台服务里，两者互不干涉——
 * 这就是「更新过程不中断用户核心功能」的实现方式。
 *
 * ## 回滚模型
 * 所有中间产物都在 `cacheDir/update/` 里生成，已安装的应用在整个过程中零改动。
 * 任何一步失败就删掉中间产物、状态归位，用户手上的版本永远是可用的。
 * 增量路径失败还会自动降级成全量下载再试一次——补丁只是优化手段，不是可用性依赖。
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: UpdateDownloader,
    private val verifier: ApkVerifier,
    private val installer: ApkInstaller,
    private val preferences: UpdatePreferences,
    private val notifier: UpdateNotifier,
) {
    companion object {
        private const val TAG = "UpdateRepository"
        private const val WORK_DIR = "update"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** 最近一次成功拉取的清单，供「用户确认后再开始下载」使用。 */
    @Volatile
    private var cachedManifest: UpdateManifest? = null

    private var runningJob: Job? = null

    private val workDir: File
        get() = File(context.cacheDir, WORK_DIR).apply { mkdirs() }

    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    // ── 检查更新 ────────────────────────────────────────────────────────────

    /**
     * @param silent 后台自动检查。发现新版只发通知，不打断前台；被用户忽略的版本直接跳过。
     */
    fun check(silent: Boolean = false) {
        if (!canStartCheck()) return
        runningJob?.cancel()
        runningJob = scope.launch { performCheck(silent) }
    }

    /**
     * 后台 Worker 专用：在调用者的协程里跑完整个检查。
     *
     * 与 [check] 的区别只在于「谁来等」——WorkManager 需要 doWork 挂起到任务真正结束，
     * 否则进程可能在网络请求回来之前就被回收，白白浪费一次唤醒。
     */
    suspend fun checkNow(silent: Boolean = true) {
        if (!canStartCheck()) return
        performCheck(silent)
    }

    /** 正在升级时不接受新的检查请求，别用一次检查把进度冲掉。 */
    private fun canStartCheck(): Boolean = when (_state.value) {
        is UpdateState.Checking,
        is UpdateState.Downloading,
        is UpdateState.Patching,
        is UpdateState.Verifying,
        -> false

        else -> true
    }

    private suspend fun performCheck(silent: Boolean) {
        _state.value = UpdateState.Checking
        try {
            val manifest = fetchManifest()
            cachedManifest = manifest
            preferences.markChecked()

            if (manifest.versionCode <= currentVersionCode) {
                cleanupWorkDir()
                _state.value = UpdateState.UpToDate(System.currentTimeMillis())
                return
            }

            if (silent && preferences.isIgnored(manifest.versionCode)) {
                _state.value = UpdateState.Idle
                return
            }

            val info = buildInfo(manifest)

            // 上次下完但用户没点安装的包，直接复用，别让他再下一遍
            val staged = stagedApk(manifest.versionCode)
            if (staged != null) {
                _state.value = UpdateState.ReadyToInstall(info, UpdateStrategy.FULL, staged)
                if (silent) notifier.notifyReady(context, info)
                return
            }

            _state.value = UpdateState.Available(info)
            if (silent) notifier.notifyAvailable(context, info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ManifestException) {
            Log.w(TAG, "manifest unusable", e)
            _state.value = UpdateState.Failed(UpdateStage.CHECK, "更新信息格式异常", retryable = false)
        } catch (e: Exception) {
            Log.w(TAG, "check failed", e)
            _state.value = UpdateState.Failed(UpdateStage.CHECK, "无法连接更新服务器", retryable = true)
        }
    }

    private suspend fun fetchManifest(): UpdateManifest {
        val url = BuildConfig.UPDATE_MANIFEST_URL
        val body = downloader.fetchText(url)
        return UpdateManifest.parse(body, url)
    }

    private suspend fun buildInfo(manifest: UpdateManifest): UpdateInfo {
        val patch = runCatching { manifest.patchFor(installedApkSha256()) }.getOrNull()
        return UpdateInfo(
            versionCode = manifest.versionCode,
            versionName = manifest.versionName,
            releaseNotes = manifest.releaseNotes,
            patchSize = patch?.size,
            fullSize = manifest.full.size,
        )
    }

    private suspend fun installedApkSha256(): String = withContext(Dispatchers.IO) {
        Digests.sha256(File(context.applicationInfo.sourceDir))
    }

    // ── 执行更新 ────────────────────────────────────────────────────────────

    /**
     * 开始下载并准备安装包。
     *
     * @param allowMetered 用户手动点「立即更新」时为 true，忽略「仅 Wi-Fi」限制——
     *                     他既然点了，就是知道自己在做什么。
     */
    fun startUpdate(allowMetered: Boolean = true) {
        val manifest = cachedManifest ?: run {
            check(silent = false)
            return
        }
        if (manifest.versionCode <= currentVersionCode) {
            _state.value = UpdateState.UpToDate(System.currentTimeMillis())
            return
        }

        runningJob?.cancel()
        runningJob = scope.launch {
            val info = buildInfo(manifest)
            val target = File(workDir, "update-${manifest.versionCode}.apk")
            try {
                if (!allowMetered && preferences.wifiOnly.first() && !isUnmeteredNetwork()) {
                    _state.value = UpdateState.Failed(
                        UpdateStage.DOWNLOAD, "当前不是 Wi-Fi，已暂停自动下载", retryable = true, info = info,
                    )
                    return@launch
                }

                cleanupStale(manifest.versionCode)

                // 复用上次已经校验通过的成品
                stagedApk(manifest.versionCode)?.let { ready ->
                    _state.value = UpdateState.ReadyToInstall(info, UpdateStrategy.FULL, ready)
                    return@launch
                }

                val patch = manifest.patchFor(installedApkSha256())
                var strategy = UpdateStrategy.FULL

                if (patch != null) {
                    strategy = UpdateStrategy.INCREMENTAL
                    val ok = runIncremental(manifest, patch, info, target)
                    if (ok) {
                        finish(info, strategy, target)
                        return@launch
                    }
                    // 增量这条路走不通就换全量，用户不需要知道细节，只需要更新成功
                    Log.w(TAG, "incremental path failed, falling back to full download")
                    strategy = UpdateStrategy.FULL
                    target.delete()
                }

                runFull(manifest, info, target)
                finish(info, strategy, target)
            } catch (e: CancellationException) {
                notifier.cancel(context)
                throw e
            } catch (e: UpdateFailure) {
                rollback(keepResumable = e.stage == UpdateStage.DOWNLOAD && e.retryable)
                _state.value = UpdateState.Failed(e.stage, e.userMessage, e.retryable, info)
                notifier.notifyFailed(context, e.userMessage)
            } catch (e: Exception) {
                Log.e(TAG, "update failed", e)
                rollback(keepResumable = false)
                target.delete()
                _state.value = UpdateState.Failed(UpdateStage.DOWNLOAD, "更新失败：${e.message}", true, info)
                notifier.notifyFailed(context, "更新失败")
            }
        }
    }

    /**
     * 增量路径。失败返回 false 让调用方降级，而不是抛异常——
     * 补丁失败是**预期内**的情况（旧包被 ROM 改过、补丁损坏、设备存储不足），
     * 不该让用户看到任何错误。
     */
    private suspend fun runIncremental(
        manifest: UpdateManifest,
        patch: UpdateManifest.Patch,
        info: UpdateInfo,
        target: File,
    ): Boolean {
        val patchFile = File(workDir, "patch-${patch.fromVersionCode}-${manifest.versionCode}.bin")
        return try {
            _state.value = UpdateState.Downloading(info, UpdateStrategy.INCREMENTAL, 0, patch.size)
            notifier.notifyProgress(context, info, 0f, UpdateStrategy.INCREMENTAL)

            downloader.download(patch.url, patchFile, patch.size, patch.sha256) { done, total ->
                _state.value = UpdateState.Downloading(info, UpdateStrategy.INCREMENTAL, done, total)
                notifier.notifyProgress(
                    context, info,
                    if (total > 0) done.toFloat() / total else 0f,
                    UpdateStrategy.INCREMENTAL,
                )
            }

            _state.value = UpdateState.Patching(info, 0f)
            val installedApk = File(context.applicationInfo.sourceDir)
            withContext(Dispatchers.Default) {
                BsPatch.apply(installedApk, patchFile, target) { progress ->
                    _state.value = UpdateState.Patching(info, progress)
                }
            }

            _state.value = UpdateState.Verifying(info)
            when (val result = verifier.verify(context, target, manifest.full.sha256, manifest.versionCode)) {
                is ApkVerifier.Result.Ok -> true
                is ApkVerifier.Result.Rejected -> {
                    Log.w(TAG, "patched apk rejected: ${result.reason}")
                    false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "incremental update failed: ${e.message}")
            false
        } finally {
            patchFile.delete()
            File(patchFile.parentFile, patchFile.name + ".part").delete()
        }
    }

    private suspend fun runFull(manifest: UpdateManifest, info: UpdateInfo, target: File) {
        _state.value = UpdateState.Downloading(info, UpdateStrategy.FULL, 0, manifest.full.size)
        notifier.notifyProgress(context, info, 0f, UpdateStrategy.FULL)

        try {
            downloader.download(manifest.full.url, target, manifest.full.size, manifest.full.sha256) { done, total ->
                _state.value = UpdateState.Downloading(info, UpdateStrategy.FULL, done, total)
                notifier.notifyProgress(
                    context, info,
                    if (total > 0) done.toFloat() / total else 0f,
                    UpdateStrategy.FULL,
                )
            }
        } catch (e: DownloadException) {
            throw UpdateFailure(
                UpdateStage.DOWNLOAD,
                if (e.resumable) "下载中断，可重试继续" else "下载的文件校验失败",
                retryable = true,
                cause = e,
            )
        }

        _state.value = UpdateState.Verifying(info)
        when (val result = verifier.verify(context, target, manifest.full.sha256, manifest.versionCode)) {
            is ApkVerifier.Result.Ok -> Unit
            is ApkVerifier.Result.Rejected -> {
                target.delete()
                throw UpdateFailure(UpdateStage.VERIFY, result.reason, retryable = true)
            }
        }
    }

    private fun finish(info: UpdateInfo, strategy: UpdateStrategy, apk: File) {
        _state.value = UpdateState.ReadyToInstall(info, strategy, apk)
        notifier.notifyReady(context, info)
    }

    // ── 安装 ────────────────────────────────────────────────────────────────

    /** @return 错误提示；null 表示系统安装界面已拉起。 */
    fun install(): String? {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return "安装包尚未准备好"
        val error = installer.install(context, ready.apkFile)
        if (error == null) {
            _state.value = UpdateState.Installing(ready.info)
            notifier.cancel(context)
        }
        return error
    }

    fun canInstall(): Boolean = installer.canInstall(context)

    fun requestInstallPermission(): Boolean = installer.requestInstallPermission(context)

    // ── 用户操作 ────────────────────────────────────────────────────────────

    /** 取消进行中的下载/合成，并清掉中间产物。已安装版本不受影响。 */
    fun cancel() {
        runningJob?.cancel()
        runningJob = null
        scope.launch {
            rollback(keepResumable = true)
            notifier.cancel(context)
            _state.value = UpdateState.Idle
        }
    }

    /** 跳过这个版本，后台不再提醒；用户仍可在设置里手动检查。 */
    fun ignoreCurrentVersion() {
        val version = when (val s = _state.value) {
            is UpdateState.Available -> s.info.versionCode
            is UpdateState.ReadyToInstall -> s.info.versionCode
            else -> return
        }
        scope.launch {
            preferences.ignoreVersion(version)
            notifier.cancel(context)
            _state.value = UpdateState.Idle
        }
    }

    /** 把失败/完成提示收起来，回到静默状态。 */
    fun dismiss() {
        when (_state.value) {
            is UpdateState.Failed, is UpdateState.UpToDate, is UpdateState.Available ->
                _state.value = UpdateState.Idle
            else -> Unit
        }
    }

    // ── 清理与回滚 ──────────────────────────────────────────────────────────

    /**
     * 回滚：删除中间产物。
     *
     * 注意这里**不需要**回退已安装的应用——安装动作由系统原子完成，
     * 在我们这一侧，"未完成的更新" 与 "从未开始的更新" 在磁盘上是同一个状态。
     *
     * @param keepResumable 网络中断这类可恢复的失败，保留 `update-*.apk.part` 让下次接着传；
     *                      校验失败等脏数据场景必须清干净，否则续传只会一错再错。
     */
    private fun rollback(keepResumable: Boolean) {
        runCatching {
            workDir.listFiles()?.forEach { file ->
                val isResumableChunk = file.name.startsWith("update-") && file.name.endsWith(".apk.part")
                val shouldKeep = keepResumable && isResumableChunk
                if (!shouldKeep && (file.name.startsWith("patch-") || file.name.endsWith(".part"))) {
                    file.delete()
                }
            }
        }.onFailure { Log.w(TAG, "rollback cleanup failed", it) }
    }

    /** 清掉与本次目标无关的历史文件，避免缓存目录无限增长。 */
    private fun cleanupStale(targetVersionCode: Int) {
        runCatching {
            val keep = "update-$targetVersionCode.apk"
            workDir.listFiles()?.forEach { file ->
                if (file.name != keep && file.name != "$keep.part") file.delete()
            }
        }.onFailure { Log.w(TAG, "stale cleanup failed", it) }
    }

    private fun cleanupWorkDir() {
        runCatching { workDir.listFiles()?.forEach { it.delete() } }
    }

    /** 上次已完整下载并通过校验的安装包；没有就返回 null。 */
    private fun stagedApk(versionCode: Int): File? {
        val file = File(workDir, "update-$versionCode.apk")
        if (!file.isFile || file.length() == 0L) return null
        val manifest = cachedManifest ?: return null
        return when (verifier.verify(context, file, manifest.full.sha256, versionCode)) {
            is ApkVerifier.Result.Ok -> file
            is ApkVerifier.Result.Rejected -> {
                file.delete()
                null
            }
        }
    }

    private fun isUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

/** 携带用户可读文案的更新失败，避免把技术堆栈直接甩给用户。 */
private class UpdateFailure(
    val stage: UpdateStage,
    val userMessage: String,
    val retryable: Boolean,
    cause: Throwable? = null,
) : Exception(userMessage, cause)
