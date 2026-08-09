package com.sitbreak.app.update

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 极简 HTTP 下载器：只用 JDK 自带的 [HttpURLConnection]，不引入 OkHttp。
 *
 * 支持的能力都是增量更新真正需要的那几样：
 * - **断点续传**：网络抖动是移动端常态，重新下 12MB 全量包的体验无法接受。
 *   已下载部分保留在 `.part` 文件里，重试时用 `Range` 头接着传。
 * - **协作式取消**：用户随时可以退出更新流程，每写一块就检查一次协程状态。
 * - **进度回调**：节流到「每 64KB 或每 200ms」，避免高频刷新拖累 UI。
 */
@Singleton
class UpdateDownloader @Inject constructor() {

    companion object {
        private const val TAG = "UpdateDownloader"
        private const val BUFFER = 64 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val PROGRESS_INTERVAL_MS = 200L
        private const val MAX_REDIRECTS = 5
    }

    /**
     * 下载 [url] 到 [target]，中途数据落在 `target.part`，完整校验通过后才改名到位。
     *
     * 这个「先写 .part、成功才改名」的约定很关键：磁盘上永远不会出现一个看起来完整、
     * 实际被截断的目标文件，后续步骤也就不必怀疑输入。
     *
     * @param expectedSha256 期望摘要。为空则跳过校验（清单里没写 size/sha 的兼容情况）。
     * @throws DownloadException 网络或校验失败；[DownloadException.resumable] 指示 `.part` 是否值得保留。
     */
    @Throws(DownloadException::class)
    suspend fun download(
        url: String,
        target: File,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        val partFile = File(target.parentFile, target.name + ".part")

        // 上一轮残留的 .part 若已超过预期大小，说明它对应的是另一个文件，直接丢弃
        if (expectedSize > 0 && partFile.exists() && partFile.length() > expectedSize) {
            Log.w(TAG, "discarding oversized part file (${partFile.length()} > $expectedSize)")
            partFile.delete()
        }

        try {
            fetchToPartFile(url, partFile, expectedSize, onProgress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            // 网络类异常保留 .part，下次续传
            throw DownloadException("download failed: ${e.message}", resumable = true, cause = e)
        }

        if (expectedSize > 0 && partFile.length() != expectedSize) {
            partFile.delete()
            throw DownloadException(
                "size mismatch: expected $expectedSize got ${partFile.length()}",
                resumable = false,
            )
        }

        if (expectedSha256.isNotBlank()) {
            val actual = Digests.sha256(partFile)
            if (!Digests.matches(expectedSha256, actual)) {
                // 内容错了，续传没有意义，必须重头来
                partFile.delete()
                throw DownloadException(
                    "checksum mismatch: expected $expectedSha256 got $actual",
                    resumable = false,
                )
            }
        }

        target.delete()
        if (!partFile.renameTo(target)) {
            partFile.copyTo(target, overwrite = true)
            partFile.delete()
        }
    }

    private suspend fun fetchToPartFile(
        url: String,
        partFile: File,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        var existing = if (partFile.exists()) partFile.length() else 0L
        var currentUrl = url
        var redirects = 0

        while (true) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                // 静态站通常不会主动压缩 APK，显式声明避免中间层再套一层 gzip 打乱字节计数
                setRequestProperty("Accept-Encoding", "identity")
                instanceFollowRedirects = false
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }

            try {
                val code = connection.responseCode

                if (code in 301..308 && code != 304) {
                    val location = connection.getHeaderField("Location")
                        ?: throw DownloadException("redirect without Location", resumable = false)
                    if (++redirects > MAX_REDIRECTS) {
                        throw DownloadException("too many redirects", resumable = false)
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    continue
                }

                when (code) {
                    HttpURLConnection.HTTP_PARTIAL -> Unit  // 服务端接受续传
                    HttpURLConnection.HTTP_OK -> {
                        // 服务端忽略了 Range（很多静态站如此），只能从头写
                        if (existing > 0) {
                            Log.i(TAG, "server ignored Range, restarting from 0")
                            existing = 0
                            partFile.delete()
                        }
                    }
                    416 -> {
                        // Range 越界：本地 .part 比服务端文件还长，作废重来
                        partFile.delete()
                        throw DownloadException("range not satisfiable, part file discarded", resumable = true)
                    }
                    else -> throw DownloadException("HTTP $code", resumable = code >= 500)
                }

                val contentLength = connection.contentLengthLong
                val total = when {
                    expectedSize > 0 -> expectedSize
                    contentLength > 0 -> existing + contentLength
                    else -> 0L
                }

                RandomAccessFile(partFile, "rw").use { out ->
                    out.seek(existing)
                    connection.inputStream.buffered(BUFFER).use { input ->
                        val buf = ByteArray(BUFFER)
                        var written = existing
                        var lastReport = 0L
                        onProgress(written, total)

                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n

                            val now = System.currentTimeMillis()
                            if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                                lastReport = now
                                onProgress(written, total)
                            }
                        }
                        onProgress(written, total)
                    }
                }
                return
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * 抓取一小段文本（更新清单）。限制 512KB 以防被超大响应拖垮。
     */
    @Throws(DownloadException::class)
    suspend fun fetchText(url: String): String {
        var currentUrl = url
        var redirects = 0

        while (true) {
            currentCoroutineContext().ensureActive()
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = false
                // 清单会随发布频繁变化，禁掉缓存免得读到旧版本
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                val code = connection.responseCode
                if (code in 301..308 && code != 304) {
                    val location = connection.getHeaderField("Location")
                        ?: throw DownloadException("redirect without Location", resumable = false)
                    if (++redirects > MAX_REDIRECTS) {
                        throw DownloadException("too many redirects", resumable = false)
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    throw DownloadException("HTTP $code while fetching manifest", resumable = code >= 500)
                }
                return connection.inputStream.buffered().use { input ->
                    val limited = ByteArray(512 * 1024)
                    var read = 0
                    while (read < limited.size) {
                        val n = input.read(limited, read, limited.size - read)
                        if (n < 0) break
                        read += n
                    }
                    String(limited, 0, read, Charsets.UTF_8)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadException) {
                throw e
            } catch (e: IOException) {
                throw DownloadException("cannot reach update server: ${e.message}", resumable = true, cause = e)
            } finally {
                connection.disconnect()
            }
        }
    }
}

/**
 * @param resumable true 表示已下载的分片仍然可信，重试时可以续传；
 *                  false 表示分片已被判定为脏数据并删除，必须从头下载。
 */
class DownloadException(
    message: String,
    val resumable: Boolean,
    cause: Throwable? = null,
) : IOException(message, cause)
