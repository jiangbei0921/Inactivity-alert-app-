package com.sitbreak.app.update

import org.json.JSONException
import org.json.JSONObject
import java.net.URI

/**
 * 发布侧生成的更新清单（`update.json`），描述「最新版是什么」以及「从哪些旧版本可以走增量」。
 *
 * 示例：
 * ```json
 * {
 *   "schema": 1,
 *   "versionCode": 42,
 *   "versionName": "1.1.42",
 *   "releaseNotes": "修复提醒不响铃的问题",
 *   "publishedAt": "2026-08-09T13:00:00Z",
 *   "full": { "url": "app-debug.apk", "size": 12575288, "sha256": "…" },
 *   "patches": [
 *     { "fromVersionCode": 41, "fromSha256": "…", "url": "patches/p-41-to-42.patch",
 *       "size": 843122, "sha256": "…" }
 *   ]
 * }
 * ```
 *
 * 设计要点：**补丁的适用性由旧包的 SHA-256 判定，而不是 versionCode**。
 * 同一个 versionCode 完全可能对应多次构建（重跑 CI、改了资源没改版本号），
 * 只有内容摘要能保证「设备上这个 APK 正是生成补丁时用的那一个」，
 * 否则 bspatch 会算出一个大小正确但内容错乱的文件。
 */
data class UpdateManifest(
    val schema: Int,
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val publishedAt: String,
    val full: Artifact,
    val patches: List<Patch>,
) {
    /** 全量安装包。任何增量路径失败时都能降级到它，是可用性的最后保障。 */
    data class Artifact(
        val url: String,
        val size: Long,
        val sha256: String,
    )

    /** 一条差分记录：从 [fromSha256] 对应的那个 APK 升到本清单描述的新版。 */
    data class Patch(
        val fromVersionCode: Int,
        val fromSha256: String,
        val url: String,
        val size: Long,
        val sha256: String,
    )

    /** 找到适用于当前已安装 APK 的补丁；找不到就该走全量。 */
    fun patchFor(installedApkSha256: String): Patch? =
        patches.firstOrNull { it.fromSha256.equals(installedApkSha256, ignoreCase = true) }

    companion object {
        /** 当前客户端能理解的清单版本。将来格式不兼容时递增，老客户端会拒绝解析而不是误读。 */
        const val SUPPORTED_SCHEMA = 1

        /**
         * @param baseUrl 清单自身的 URL，用于把条目里的相对路径解析成绝对地址。
         *                相对路径让整站可以整体换域名而不必重新出包。
         */
        @Throws(ManifestException::class)
        fun parse(json: String, baseUrl: String): UpdateManifest {
            try {
                val root = JSONObject(json)
                val schema = root.optInt("schema", 1)
                if (schema > SUPPORTED_SCHEMA) {
                    throw ManifestException("manifest schema $schema is newer than supported $SUPPORTED_SCHEMA")
                }

                val versionCode = root.getInt("versionCode")
                if (versionCode <= 0) throw ManifestException("invalid versionCode: $versionCode")

                val fullJson = root.getJSONObject("full")
                val full = Artifact(
                    url = absolutize(baseUrl, fullJson.getString("url")),
                    size = fullJson.optLong("size", 0L),
                    sha256 = normalizeDigest(fullJson.getString("sha256")),
                )

                val patchesJson = root.optJSONArray("patches")
                val patches = buildList {
                    for (i in 0 until (patchesJson?.length() ?: 0)) {
                        val p = patchesJson!!.getJSONObject(i)
                        // 单条补丁畸形不该毁掉整次更新——跳过它，大不了走全量
                        val url = p.optString("url").takeIf { it.isNotBlank() } ?: continue
                        val fromSha = p.optString("fromSha256").takeIf { it.length == 64 } ?: continue
                        val sha = p.optString("sha256").takeIf { it.length == 64 } ?: continue
                        add(
                            Patch(
                                fromVersionCode = p.optInt("fromVersionCode", 0),
                                fromSha256 = normalizeDigest(fromSha),
                                url = absolutize(baseUrl, url),
                                size = p.optLong("size", 0L),
                                sha256 = normalizeDigest(sha),
                            )
                        )
                    }
                }

                return UpdateManifest(
                    schema = schema,
                    versionCode = versionCode,
                    versionName = root.optString("versionName", versionCode.toString()),
                    releaseNotes = root.optString("releaseNotes", ""),
                    publishedAt = root.optString("publishedAt", ""),
                    full = full,
                    patches = patches,
                )
            } catch (e: ManifestException) {
                throw e
            } catch (e: JSONException) {
                throw ManifestException("malformed update manifest: ${e.message}", e)
            }
        }

        private fun normalizeDigest(value: String): String = value.trim().lowercase()

        /** 相对路径按清单地址解析；已经是绝对地址的原样返回。 */
        private fun absolutize(baseUrl: String, url: String): String {
            if (url.startsWith("http://", true) || url.startsWith("https://", true)) return url
            return try {
                URI(baseUrl).resolve(url).toString()
            } catch (e: Exception) {
                throw ManifestException("cannot resolve '$url' against '$baseUrl'", e)
            }
        }
    }
}

/** 清单不可用（网络内容不是 JSON、字段缺失、版本过新等）。触发后保持当前版本不动。 */
class ManifestException(message: String, cause: Throwable? = null) : Exception(message, cause)
