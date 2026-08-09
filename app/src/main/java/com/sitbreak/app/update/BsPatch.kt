package com.sitbreak.app.update

import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.InflaterInputStream

/**
 * 增量更新的补丁内核：把「旧 APK + 补丁」还原成「新 APK」。
 *
 * ## 为什么自定义格式而不是直接用 bsdiff 的 BSDIFF40
 * 标准 bsdiff 的三个数据块用 bzip2 压缩，而 Android/JDK 运行时**不自带 bzip2 解压器**，
 * 引入 commons-compress 会给一个 12MB 的 APK 再加约 1MB 体积，得不偿失。
 * 因此 CI 侧在 `bsdiff` 生成 BSDIFF40 之后，用 `tools/make_patch.py` 做一次**无损转码**：
 * 解出三个块的原始字节（内容一字不改），改用 deflate 重新压缩，写成 SBPATCH1。
 * 这样客户端只需 JDK 内置的 [InflaterInputStream]，**零新增依赖**。
 *
 * ## SBPATCH1 二进制布局（所有整数均为小端 int64）
 * ```
 * 偏移      长度   含义
 * 0        8      magic = "SBPATCH1"
 * 8        8      ctrlZLen  —— deflate 后的控制块长度
 * 16       8      diffZLen  —— deflate 后的差分块长度
 * 24       8      newSize   —— 还原出的新文件字节数
 * 32       …      控制块（deflate）
 * …        …      差分块（deflate）
 * …        …      追加块（deflate，长度为文件剩余部分）
 * ```
 *
 * 控制块由若干组三元组 (diffLen, extraLen, oldSeek) 构成，每个数字 8 字节，
 * 采用 bsdiff 自有的「小端绝对值 + 最高位符号位」编码（见 [offtin]）——
 * 转码时原样保留，所以这里必须用同一套解码规则。
 */
object BsPatch {

    private val MAGIC = "SBPATCH1".toByteArray(Charsets.US_ASCII)

    /** 头部固定长度：magic(8) + ctrlZLen(8) + diffZLen(8) + newSize(8)。 */
    const val HEADER_SIZE = 32

    /** 单次 IO 的搬运块大小，64KB 在移动端是吞吐与内存占用的平衡点。 */
    private const val CHUNK = 64 * 1024

    /**
     * 应用补丁。
     *
     * @param oldFile 已安装的旧 APK（通常取自 `applicationInfo.sourceDir`），只读。
     * @param patchFile SBPATCH1 补丁文件。
     * @param newFile 输出的新 APK；调用方负责保证其位于可写的临时目录。
     * @param onProgress 0f..1f 的还原进度，按已写出的字节数估算；调用频率约每 64KB 一次。
     * @throws PatchException 任何格式错误、数据越界或 IO 失败都会包装成该异常，
     *         方便上层统一走「回滚 + 降级到全量包」的分支。
     */
    @Throws(PatchException::class)
    fun apply(
        oldFile: File,
        patchFile: File,
        newFile: File,
        onProgress: (Float) -> Unit = {},
    ) {
        try {
            applyInternal(oldFile, patchFile, newFile, onProgress)
        } catch (e: PatchException) {
            newFile.delete()
            throw e
        } catch (e: Exception) {
            // OutOfMemory 之外的所有异常统一收口；合成失败必须删掉半成品，
            // 否则下次校验会拿到一个「大小对但内容错」的文件。
            newFile.delete()
            throw PatchException("apply patch failed: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private fun applyInternal(
        oldFile: File,
        patchFile: File,
        newFile: File,
        onProgress: (Float) -> Unit,
    ) {
        if (!oldFile.isFile) throw PatchException("old file not found: ${oldFile.name}")
        if (!patchFile.isFile) throw PatchException("patch file not found: ${patchFile.name}")

        val patchBytes = patchFile.readBytes()
        if (patchBytes.size < HEADER_SIZE) throw PatchException("patch too small: ${patchBytes.size}")
        for (i in MAGIC.indices) {
            if (patchBytes[i] != MAGIC[i]) throw PatchException("bad patch magic")
        }

        val ctrlZLen = readLongLE(patchBytes, 8)
        val diffZLen = readLongLE(patchBytes, 16)
        val newSize = readLongLE(patchBytes, 24)

        val extraOffset = HEADER_SIZE + ctrlZLen + diffZLen
        if (ctrlZLen < 0 || diffZLen < 0 || newSize < 0 || extraOffset > patchBytes.size) {
            throw PatchException("corrupt patch header: ctrl=$ctrlZLen diff=$diffZLen new=$newSize")
        }
        // 512MB 上限纯属防御：正常 APK 不过几十 MB，异常值多半意味着文件被截断或被中间人替换。
        if (newSize > 512L * 1024 * 1024) throw PatchException("unreasonable new size: $newSize")

        val old = oldFile.readBytes()
        val oldSize = old.size

        val ctrlIn = inflater(patchBytes, HEADER_SIZE, ctrlZLen.toInt())
        val diffIn = inflater(patchBytes, HEADER_SIZE + ctrlZLen.toInt(), diffZLen.toInt())
        val extraIn = inflater(patchBytes, extraOffset.toInt(), patchBytes.size - extraOffset.toInt())

        newFile.parentFile?.mkdirs()

        ctrlIn.use { ctrl ->
            diffIn.use { diff ->
                extraIn.use { extra ->
                    BufferedOutputStream(FileOutputStream(newFile), CHUNK).use { out ->
                        var oldPos = 0L
                        var newPos = 0L
                        val header = ByteArray(8)
                        val buf = ByteArray(CHUNK)
                        var lastReported = -1

                        while (newPos < newSize) {
                            val diffLen = readOfft(ctrl, header)
                            val extraLen = readOfft(ctrl, header)
                            val oldSeek = readOfft(ctrl, header)

                            if (diffLen < 0 || extraLen < 0) {
                                throw PatchException("negative block length: diff=$diffLen extra=$extraLen")
                            }
                            if (newPos + diffLen + extraLen > newSize) {
                                throw PatchException("block overflows new size at $newPos")
                            }

                            // ── 差分段：新字节 = 补丁字节 + 旧字节（逐字节模 256 相加）
                            var remaining = diffLen
                            while (remaining > 0) {
                                val n = minOf(remaining, CHUNK.toLong()).toInt()
                                readFully(diff, buf, n)
                                for (i in 0 until n) {
                                    val op = oldPos + i
                                    // bsdiff 允许 oldPos 越界，越界处按 0 处理（等价于纯追加）
                                    if (op in 0 until oldSize) {
                                        buf[i] = (buf[i] + old[op.toInt()]).toByte()
                                    }
                                }
                                out.write(buf, 0, n)
                                oldPos += n
                                newPos += n
                                remaining -= n
                            }

                            // ── 追加段：新文件独有的内容，原样写出
                            remaining = extraLen
                            while (remaining > 0) {
                                val n = minOf(remaining, CHUNK.toLong()).toInt()
                                readFully(extra, buf, n)
                                out.write(buf, 0, n)
                                newPos += n
                                remaining -= n
                            }

                            oldPos += oldSeek

                            val percent = ((newPos * 100) / newSize).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent / 100f)
                            }
                        }
                        out.flush()
                    }
                }
            }
        }

        if (newFile.length() != newSize) {
            throw PatchException("size mismatch: expected $newSize got ${newFile.length()}")
        }
        onProgress(1f)
    }

    /**
     * 刻意使用单参数构造：由 [InflaterInputStream] 自己创建并在 close 时 `end()` 掉 Inflater。
     * 传入自建 Inflater 的重载不会释放 native 内存，在反复检查更新的场景下会缓慢泄漏。
     */
    private fun inflater(data: ByteArray, offset: Int, length: Int): InputStream =
        InflaterInputStream(ByteArrayInputStream(data, offset, length))

    private fun readOfft(stream: InputStream, buf: ByteArray): Long {
        readFully(stream, buf, 8)
        return offtin(buf)
    }

    /**
     * bsdiff 的整数编码：低 7 字节 + 第 8 字节低 7 位存绝对值（小端），
     * 第 8 字节最高位为符号位。注意这**不是**补码，直接按补码读会得到错误的负数。
     */
    private fun offtin(buf: ByteArray): Long {
        var y = buf[7].toLong() and 0x7F
        for (i in 6 downTo 0) {
            y = y shl 8
            y += buf[i].toLong() and 0xFF
        }
        return if (buf[7].toInt() and 0x80 != 0) -y else y
    }

    private fun readLongLE(buf: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) {
            v = v shl 8
            v = v or (buf[offset + i].toLong() and 0xFF)
        }
        return v
    }

    private fun readFully(stream: InputStream, buf: ByteArray, len: Int) {
        var read = 0
        while (read < len) {
            val n = stream.read(buf, read, len - read)
            if (n < 0) throw PatchException("unexpected end of patch stream (need $len, got $read)")
            read += n
        }
    }
}

/** 补丁应用过程中的所有可预期失败，上层据此触发回滚与全量降级。 */
class PatchException(message: String, cause: Throwable? = null) : IOException(message, cause)
