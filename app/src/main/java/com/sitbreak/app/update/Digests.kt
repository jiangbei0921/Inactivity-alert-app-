package com.sitbreak.app.update

import java.io.File
import java.security.MessageDigest

/** 文件摘要工具。增量更新的每一次「这个文件是我要的那个吗」都走这里。 */
object Digests {

    private const val BUFFER = 64 * 1024

    /** 流式计算 SHA-256，返回小写十六进制。12MB 的 APK 约 100ms，可安全放在 IO 线程。 */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER).use { input ->
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /**
     * 常量时间比较。摘要比对本身不涉及机密，但养成习惯没有坏处，
     * 也顺手规避了 equals 在大小写/空白上的意外。
     */
    fun matches(expected: String, actual: String): Boolean {
        val a = expected.trim().lowercase()
        val b = actual.trim().lowercase()
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun ByteArray.toHex(): String {
        val hex = CharArray(size * 2)
        val digits = "0123456789abcdef"
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            hex[i * 2] = digits[v ushr 4]
            hex[i * 2 + 1] = digits[v and 0x0F]
        }
        return String(hex)
    }
}
