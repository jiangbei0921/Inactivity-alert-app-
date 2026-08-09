package com.sitbreak.app.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.DeflaterOutputStream

/**
 * bspatch 内核的正确性验证。
 *
 * 这里不依赖真实的 bsdiff 工具，而是在测试内自建同格式的补丁构造器 [buildPatch]，
 * 逐个覆盖算法的四条关键路径：差分段相加、追加段直写、oldSeek 跳转（含负向回退）、
 * 以及 oldPos 越界时按 0 处理。再加上头部损坏与数据截断的失败路径，
 * 确保任何异常都收敛成 PatchException，让上层能可靠地触发回滚。
 */
class BsPatchTest {

    @get:Rule
    val temp = TemporaryFolder()

    // ── 构造工具 ────────────────────────────────────────────────────────────

    /** bsdiff 的整数编码：小端绝对值 + 最高位符号位（与 BsPatch.offtin 对称）。 */
    private fun offtout(value: Long): ByteArray {
        val negative = value < 0
        var y = if (negative) -value else value
        val b = ByteArray(8)
        for (i in 0..7) {
            b[i] = (y and 0xFF).toByte()
            y = y shr 8
        }
        if (negative) b[7] = (b[7].toInt() or 0x80).toByte()
        return b
    }

    private fun deflate(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun longLE(value: Long): ByteArray {
        val b = ByteArray(8)
        var y = value
        for (i in 0..7) {
            b[i] = (y and 0xFF).toByte()
            y = y shr 8
        }
        return b
    }

    /**
     * @param controls 控制三元组列表 (diffLen, extraLen, oldSeek)
     */
    private fun buildPatch(
        controls: List<Triple<Long, Long, Long>>,
        diff: ByteArray,
        extra: ByteArray,
        newSize: Long,
        magic: ByteArray = "SBPATCH1".toByteArray(),
    ): ByteArray {
        val ctrlRaw = ByteArrayOutputStream().apply {
            controls.forEach { (d, e, s) ->
                write(offtout(d)); write(offtout(e)); write(offtout(s))
            }
        }.toByteArray()

        val zc = deflate(ctrlRaw)
        val zd = deflate(diff)
        val ze = deflate(extra)

        return ByteArrayOutputStream().apply {
            write(magic)
            write(longLE(zc.size.toLong()))
            write(longLE(zd.size.toLong()))
            write(longLE(newSize))
            write(zc); write(zd); write(ze)
        }.toByteArray()
    }

    /** 生成「new 相对 old 的逐字节差」，old 不足处按 0 计（与 bspatch 的越界规则一致）。 */
    private fun byteDelta(old: ByteArray, new: ByteArray, oldOffset: Int, count: Int): ByteArray =
        ByteArray(count) { i ->
            val o = if (oldOffset + i in old.indices) old[oldOffset + i] else 0
            (new[i] - o).toByte()
        }

    private fun run(old: ByteArray, patch: ByteArray, onProgress: (Float) -> Unit = {}): ByteArray {
        val oldFile = temp.newFile("old-${System.nanoTime()}.bin").apply { writeBytes(old) }
        val patchFile = temp.newFile("patch-${System.nanoTime()}.bin").apply { writeBytes(patch) }
        val newFile = File(temp.root, "new-${System.nanoTime()}.bin")
        BsPatch.apply(oldFile, patchFile, newFile, onProgress)
        return newFile.readBytes()
    }

    // ── 正确性 ──────────────────────────────────────────────────────────────

    @Test
    fun `pure diff block reconstructs new bytes`() {
        val old = "ABCDEFGH".toByteArray()
        val new = "ABXDEFGH".toByteArray()
        val patch = buildPatch(
            controls = listOf(Triple(8L, 0L, 0L)),
            diff = byteDelta(old, new, 0, 8),
            extra = ByteArray(0),
            newSize = 8,
        )
        assertArrayEquals(new, run(old, patch))
    }

    @Test
    fun `extra block appends bytes not present in old file`() {
        val old = "1234".toByteArray()
        val head = "1235".toByteArray()
        val tail = "-NEW".toByteArray()
        val new = head + tail

        val patch = buildPatch(
            controls = listOf(Triple(4L, 4L, 0L)),
            diff = byteDelta(old, head, 0, 4),
            extra = tail,
            newSize = new.size.toLong(),
        )
        assertArrayEquals(new, run(old, patch))
    }

    @Test
    fun `negative oldSeek rewinds and reuses earlier old bytes`() {
        // old = "HELLOWORLD"，新文件把前 5 字节复制两遍：需要第二块把 oldPos 回退 5
        val old = "HELLOWORLD".toByteArray()
        val new = "HELLOHELLO".toByteArray()

        val first = byteDelta(old, "HELLO".toByteArray(), 0, 5)
        val second = byteDelta(old, "HELLO".toByteArray(), 0, 5)

        val patch = buildPatch(
            controls = listOf(
                Triple(5L, 0L, -5L),  // 写完前 5 字节后把 oldPos 从 5 拨回 0
                Triple(5L, 0L, 0L),
            ),
            diff = first + second,
            extra = ByteArray(0),
            newSize = 10,
        )
        assertArrayEquals(new, run(old, patch))
    }

    @Test
    fun `diff beyond old file length treats missing old bytes as zero`() {
        val old = "AB".toByteArray()
        val new = "ABCDEF".toByteArray()
        val patch = buildPatch(
            controls = listOf(Triple(6L, 0L, 0L)),
            diff = byteDelta(old, new, 0, 6),
            extra = ByteArray(0),
            newSize = 6,
        )
        assertArrayEquals(new, run(old, patch))
    }

    @Test
    fun `large payload crossing chunk boundary is reconstructed intact`() {
        // 200KB 跨越多个 64KB 搬运块，用来暴露块边界上的 oldPos 累加错误
        val size = 200_000
        val old = ByteArray(size) { (it % 251).toByte() }
        val new = ByteArray(size) { ((it * 7 + 3) % 253).toByte() }
        val patch = buildPatch(
            controls = listOf(Triple(size.toLong(), 0L, 0L)),
            diff = byteDelta(old, new, 0, size),
            extra = ByteArray(0),
            newSize = size.toLong(),
        )
        assertArrayEquals(new, run(old, patch))
    }

    @Test
    fun `progress is monotonic and ends at one`() {
        val old = ByteArray(100_000) { it.toByte() }
        val new = ByteArray(100_000) { (it + 1).toByte() }
        val patch = buildPatch(
            controls = listOf(Triple(100_000L, 0L, 0L)),
            diff = byteDelta(old, new, 0, 100_000),
            extra = ByteArray(0),
            newSize = 100_000,
        )

        val seen = mutableListOf<Float>()
        run(old, patch) { seen.add(it) }

        assertTrue("should report progress at least once", seen.isNotEmpty())
        assertEquals(1f, seen.last(), 0.0001f)
        assertTrue("progress must never go backwards", seen.zipWithNext().all { it.first <= it.second })
        assertTrue("progress must stay in 0..1", seen.all { it in 0f..1f })
    }

    // ── 失败路径 ────────────────────────────────────────────────────────────

    @Test(expected = PatchException::class)
    fun `wrong magic is rejected`() {
        val old = "AB".toByteArray()
        val patch = buildPatch(
            controls = listOf(Triple(2L, 0L, 0L)),
            diff = ByteArray(2),
            extra = ByteArray(0),
            newSize = 2,
            magic = "XXPATCH1".toByteArray(),
        )
        run(old, patch)
    }

    @Test(expected = PatchException::class)
    fun `truncated patch is rejected`() {
        // 追加块必须有实际内容，否则截断落在空 deflate 流上不会被读到
        val old = "AB".toByteArray()
        val patch = buildPatch(
            controls = listOf(Triple(2L, 4L, 0L)),
            diff = ByteArray(2),
            extra = "TAIL".toByteArray(),
            newSize = 6,
        )
        run(old, patch.copyOf(patch.size - 6))
    }

    @Test(expected = PatchException::class)
    fun `header shorter than 32 bytes is rejected`() {
        run("AB".toByteArray(), "SBPATCH1".toByteArray())
    }

    @Test(expected = PatchException::class)
    fun `block overflowing declared new size is rejected`() {
        val old = "ABCD".toByteArray()
        val patch = buildPatch(
            controls = listOf(Triple(100L, 0L, 0L)),  // 声明 newSize=4 却要写 100 字节
            diff = ByteArray(100),
            extra = ByteArray(0),
            newSize = 4,
        )
        run(old, patch)
    }

    @Test
    fun `failed patch leaves no partial output file`() {
        val old = temp.newFile("old.bin").apply { writeBytes("ABCD".toByteArray()) }
        val patch = temp.newFile("bad.patch").apply {
            writeBytes(
                buildPatch(
                    controls = listOf(Triple(100L, 0L, 0L)),
                    diff = ByteArray(100),
                    extra = ByteArray(0),
                    newSize = 4,
                )
            )
        }
        val out = File(temp.root, "out.apk")

        runCatching { BsPatch.apply(old, patch, out) }

        assertFalse("half-written output must be deleted so it can never pass verification", out.exists())
    }

    @Test(expected = PatchException::class)
    fun `missing old file is rejected`() {
        val patch = temp.newFile("p.patch").apply {
            writeBytes(
                buildPatch(listOf(Triple(1L, 0L, 0L)), ByteArray(1), ByteArray(0), 1)
            )
        }
        BsPatch.apply(File(temp.root, "nope.apk"), patch, File(temp.root, "out.apk"))
    }
}
