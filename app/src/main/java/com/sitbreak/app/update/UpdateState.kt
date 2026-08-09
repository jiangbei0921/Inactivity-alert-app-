package com.sitbreak.app.update

import java.io.File

/** 本次升级实际走的通道。UI 用它告诉用户「省了多少流量」。 */
enum class UpdateStrategy {
    /** 只下载差分补丁，本地与旧包合成新包。 */
    INCREMENTAL,

    /** 下载完整安装包。没有匹配补丁、或增量失败降级时使用。 */
    FULL,
}

/** 失败发生在哪一步。决定了「能否重试」以及「要不要降级到全量」。 */
enum class UpdateStage {
    CHECK,
    DOWNLOAD,
    PATCH,
    VERIFY,
    INSTALL,
}

/** 更新目标的摘要信息，贯穿整个状态机，供 UI 展示。 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    /** 走增量时要传输的字节数；无可用补丁时为 null。 */
    val patchSize: Long?,
    /** 全量包大小，用于计算「节省了多少流量」。 */
    val fullSize: Long,
) {
    /** 相对全量包节省的流量占比，0f..1f。没有补丁时为 0。 */
    val savedRatio: Float
        get() {
            val patch = patchSize ?: return 0f
            if (fullSize <= 0) return 0f
            return ((fullSize - patch).coerceAtLeast(0L).toFloat() / fullSize).coerceIn(0f, 1f)
        }
}

/**
 * 更新流程的单一状态源。
 *
 * 状态迁移是线性的，任何一步出错都收敛到 [Failed]，而 [Failed] 只会回到 [Idle] 或重试，
 * 不会留下中间态——这正是「回滚」在客户端的含义：临时文件清空、已安装版本纹丝不动。
 *
 * ```
 * Idle → Checking → UpToDate
 *                 → Available → Downloading → Patching → Verifying → ReadyToInstall → Installing
 *                                    ↓            ↓          ↓
 *                                  Failed ←───────┴──────────┘   (增量失败可自动降级重走 Downloading)
 * ```
 */
sealed interface UpdateState {

    /** 空闲：没检查过，或用户关闭了更新提示。 */
    data object Idle : UpdateState

    /** 正在拉取更新清单。 */
    data object Checking : UpdateState

    /** 已是最新版。[checkedAt] 为本次检查的时间戳，UI 用来显示「刚刚检查过」。 */
    data class UpToDate(val checkedAt: Long) : UpdateState

    /** 发现新版本，等待用户确认。 */
    data class Available(val info: UpdateInfo) : UpdateState

    /** 下载中。[bytesDownloaded]/[totalBytes] 让 UI 能显示「1.2 MB / 3.4 MB」。 */
    data class Downloading(
        val info: UpdateInfo,
        val strategy: UpdateStrategy,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : UpdateState {
        val progress: Float
            get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    /** 正在用补丁合成新安装包（纯本地 CPU 操作，不消耗流量）。 */
    data class Patching(val info: UpdateInfo, val progress: Float) : UpdateState

    /** 正在校验产物：摘要、可解析性、包名、版本号、签名。 */
    data class Verifying(val info: UpdateInfo) : UpdateState

    /** 安装包已就绪并通过全部校验，等待用户确认安装。 */
    data class ReadyToInstall(
        val info: UpdateInfo,
        val strategy: UpdateStrategy,
        val apkFile: File,
    ) : UpdateState

    /** 已把安装请求交给系统安装器，等待用户在系统弹窗中操作。 */
    data class Installing(val info: UpdateInfo) : UpdateState

    /**
     * 失败并已完成回滚。
     *
     * @param retryable 是否值得让用户点「重试」。清单格式错误这类问题重试也没用，
     *                  就不要用一个假按钮消耗用户耐心。
     */
    data class Failed(
        val stage: UpdateStage,
        val message: String,
        val retryable: Boolean,
        val info: UpdateInfo? = null,
    ) : UpdateState
}
