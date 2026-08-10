package com.sitbreak.app.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.sitbreak.app.R
import java.io.File
import java.io.FileOutputStream
import android.net.Uri

/**
 * 本地生成打卡分享图（完全离线，不联网、不产生任何网络请求）：
 * 用 android.graphics 离屏绘制一张 PNG 存入应用私有目录，再通过 FileProvider 授权分享。
 */
object ShareCardGenerator {

    private const val W = 1080
    private const val H = 1440

    /** 绘制并保存分享图，返回可供分享的 content:// Uri；任何异常都安全返回 null。 */
    fun generate(context: Context, data: ShareCardData): Uri? {
        return try {
            val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bitmap)
            drawCard(canvas, data)
            val dir = File(context.filesDir, "share")
            dir.mkdirs()
            val file = File(dir, "card_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    /** 调起系统分享面板分享生成的图片（授予一次性读权限）。 */
    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_title)))
    }

    private fun drawCard(canvas: AndroidCanvas, data: ShareCardData) {
        // 背景
        val bg = Paint().apply { color = AndroidColor.parseColor("#2563EB") }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        // 卡片
        val cardPaint = Paint().apply { color = AndroidColor.WHITE }
        val card = RectF(60f, 170f, (W - 60).toFloat(), (H - 170).toFloat())
        canvas.drawRoundRect(card, 48f, 48f, cardPaint)

        // 标题与日期
        val titlePaint = Paint().apply {
            color = AndroidColor.parseColor("#111827")
            textSize = 58f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("站一站", (W / 2).toFloat(), 300f, titlePaint)

        val datePaint = Paint().apply {
            color = AndroidColor.parseColor("#6B7280")
            textSize = 34f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(data.dateText, (W / 2).toFloat(), 360f, datePaint)

        // 大号连续天数
        val streakPaint = Paint().apply {
            color = AndroidColor.parseColor("#EA580C")
            textSize = 168f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${data.streak}", (W / 2).toFloat(), 600f, streakPaint)

        val streakLabel = Paint().apply {
            color = AndroidColor.parseColor("#EA580C")
            textSize = 40f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("连续打卡天数", (W / 2).toFloat(), 668f, streakLabel)

        // 三项今日统计
        val labels = listOf("今日站立", "完成率", "活动时长")
        val values = listOf(
            "${data.todayCount} 次",
            "${(data.completionRate * 100).toInt()}%",
            "%.1fh".format(data.activeHours),
        )
        val colX = listOf(W * 0.25f, W * 0.5f, W * 0.75f)
        val valPaint = Paint().apply {
            color = AndroidColor.parseColor("#2563EB")
            textSize = 54f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val labPaint = Paint().apply {
            color = AndroidColor.parseColor("#6B7280")
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        val baseY = 900f
        colX.forEachIndexed { i, x ->
            canvas.drawText(values[i], x, baseY, valPaint)
            canvas.drawText(labels[i], x, baseY + 52f, labPaint)
        }

        // 底部标语
        val footer = Paint().apply {
            color = AndroidColor.parseColor("#9CA3AF")
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("数据只留在这台手机上 · 站一站", (W / 2).toFloat(), (H - 230).toFloat(), footer)
    }
}

/** 分享卡所需的数据快照。 */
data class ShareCardData(
    val streak: Int,
    val todayCount: Int,
    val completionRate: Float,
    val activeHours: Float,
    val dateText: String,
)
