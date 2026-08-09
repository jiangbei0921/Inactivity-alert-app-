package com.sitbreak.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把校验通过的 APK 交给系统安装器。
 *
 * 非系统应用无法静默安装，最后这一步必然要用户点一次确认——这也是合理的，
 * 用户理应知道自己的应用正在被替换。我们能做的是：把前面所有环节都做成无感的，
 * 只在真正需要用户决策时才打断他。
 *
 * 安装本身由 Android 保证原子性：成功则新版生效，失败或用户取消则旧版原封不动继续跑。
 * 这就是「更新失败不影响可用性」的底层依据，不需要我们自己实现版本回退。
 */
@Singleton
class ApkInstaller @Inject constructor() {

    companion object {
        private const val TAG = "ApkInstaller"
    }

    /** Android 8+ 需要「安装未知应用」授权，未授权时安装弹窗根本不会出现。 */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /**
     * 跳转到本应用的「安装未知应用」授权页。
     * @return 是否成功拉起设置页；个别定制 ROM 缺少该 Activity 时返回 false。
     */
    fun requestInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "cannot open unknown-sources settings", e)
            false
        }
    }

    /**
     * 拉起系统安装界面。
     * @return 失败原因，null 表示已成功交给系统。
     */
    fun install(context: Context, apk: File): String? {
        if (!apk.isFile) return "安装包不存在"
        if (!canInstall(context)) return "需要先允许「安装未知应用」"

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                // 从非 Activity 上下文启动必须带 NEW_TASK；
                // GRANT_READ 让安装器能读到我们私有目录下的这一个文件
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            Log.e(TAG, "failed to launch installer", e)
            "无法启动安装器：${e.message}"
        }
    }
}
