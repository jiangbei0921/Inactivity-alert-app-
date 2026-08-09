package com.sitbreak.app

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常捕获器。
 *
 * 作用：当应用任意线程（含 UI 线程的 Compose 组合异常）抛出未捕获异常时，
 * 先把「设备型号 + Android 版本 + 异常堆栈」写入 filesDir/crash_log.txt，
 * 再转发给系统默认处理器（照常弹出崩溃提示）。下次冷启动读取该文件并弹出对话框，
 * 用户一键复制即可反馈给开发者，用于精确定位机型相关崩溃（如部分国产 ROM）。
 */
object CrashReporter {

    private const val FILE_NAME = "crash_log.txt"
    private const val TAG = "CrashReporter"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                append(appContext, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "failed to write crash log", e)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    @Synchronized
    private fun append(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val sb = buildString {
            append("==== 崩溃时间: $now ====\n")
            append("设备: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND})\n")
            append("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("线程: ${thread.name}\n")
            append("异常: ${throwable.javaClass.name}: ${throwable.message}\n")
            append(sw.toString())
            append("\n")
        }
        runCatching { File(context.filesDir, FILE_NAME).appendText(sb) }
    }

    fun read(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return runCatching { file.readText().ifBlank { null } }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
