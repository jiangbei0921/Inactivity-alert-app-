package com.sitbreak.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 安装前的最后一道闸门。
 *
 * 增量更新最危险的失败模式不是「装不上」，而是「装上了一个错的包」。
 * 补丁算法即使有 bug 也可能产出一个大小正确的文件，所以在把 APK 交给系统安装器之前，
 * 这里连过四关，任何一关不通过都判定失败并回滚：
 *
 * 1. **内容摘要** —— 与清单里声明的 SHA-256 完全一致，等于证明合成结果和发布产物逐字节相同
 * 2. **可解析性** —— 系统能把它当作合法 APK 读出包信息，排除结构性损坏
 * 3. **身份一致** —— 包名相同、版本号更高，避免装错应用或触发降级失败
 * 4. **签名一致** —— 与已安装版本同签名，提前拦住「更新包与已安装应用签名不一致」
 *    这个在国产 ROM 上体验极差的错误（本项目吃过这个亏）
 */
@Singleton
class ApkVerifier @Inject constructor() {

    companion object {
        private const val TAG = "ApkVerifier"
    }

    sealed interface Result {
        data object Ok : Result
        data class Rejected(val reason: String) : Result
    }

    fun verify(
        context: Context,
        apk: File,
        expectedSha256: String,
        expectedVersionCode: Int,
    ): Result {
        if (!apk.isFile || apk.length() == 0L) {
            return Result.Rejected("产物文件缺失")
        }

        // ── 关卡 1：内容摘要
        if (expectedSha256.isNotBlank()) {
            val actual = try {
                Digests.sha256(apk)
            } catch (e: Exception) {
                return Result.Rejected("无法计算校验和：${e.message}")
            }
            if (!Digests.matches(expectedSha256, actual)) {
                Log.w(TAG, "sha256 mismatch: expected=$expectedSha256 actual=$actual")
                return Result.Rejected("安装包校验和不匹配")
            }
        }

        // ── 关卡 2：可解析性
        val pm = context.packageManager
        val archive: PackageInfo = try {
            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            pm.getPackageArchiveInfo(apk.absolutePath, flags)
                ?: return Result.Rejected("安装包无法解析")
        } catch (e: Exception) {
            return Result.Rejected("安装包解析失败：${e.message}")
        }

        // ── 关卡 3：身份与版本
        if (archive.packageName != context.packageName) {
            return Result.Rejected("包名不匹配（${archive.packageName}）")
        }
        val archiveVersion = archive.longVersionCodeCompat()
        val installedVersion = try {
            pm.getPackageInfo(context.packageName, 0).longVersionCodeCompat()
        } catch (e: Exception) {
            0L
        }
        if (archiveVersion <= installedVersion) {
            return Result.Rejected("版本号未提升（$archiveVersion ≤ $installedVersion）")
        }
        if (expectedVersionCode > 0 && archiveVersion != expectedVersionCode.toLong()) {
            return Result.Rejected("版本号与更新清单不符（$archiveVersion ≠ $expectedVersionCode）")
        }

        // ── 关卡 4：签名一致性
        val newSignatures = archive.signatureDigests()
        val installedSignatures = try {
            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            pm.getPackageInfo(context.packageName, flags).signatureDigests()
        } catch (e: Exception) {
            emptySet()
        }
        if (newSignatures.isEmpty() || installedSignatures.isEmpty()) {
            // 取不到签名信息就不硬拦——系统安装器还会再校验一次，不必在这里制造误报
            Log.w(TAG, "signature info unavailable, deferring to system installer")
        } else if (newSignatures.intersect(installedSignatures).isEmpty()) {
            return Result.Rejected("签名与已安装版本不一致，已阻止安装")
        }

        return Result.Ok
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    /** 取签名证书的 SHA-256 集合；同时兼容 v1 多签名与 v2/v3 的签名轮换历史。 */
    @Suppress("DEPRECATION")
    private fun PackageInfo.signatureDigests(): Set<String> {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = signingInfo ?: return emptySet()
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        } else {
            signatures
        } ?: return emptySet()

        return raw.mapNotNull { signature ->
            runCatching {
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }.toSet()
    }
}
