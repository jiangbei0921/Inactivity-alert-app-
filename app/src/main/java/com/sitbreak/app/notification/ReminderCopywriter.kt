package com.sitbreak.app.notification

import kotlin.random.Random

object ReminderCopywriter {

    val healthCare = listOf(
        "久坐伤身，起来走动一下吧 🌿",
        "你的身体需要休息，站起来活动活动",
        "适当休息让你工作更高效 💪",
        "关爱自己，从站起来开始",
        "已经坐了很久了，起来喝杯水吧 💧",
        "站起来活动一下，身体会感谢你",
        "健康的身体需要你动起来 🏃",
    )

    val humorous = listOf(
        "警报！检测到人类化石正在形成 🦕",
        "你的椅子说：我也需要休息一下",
        "再不站起来，你和椅子就融为一体了",
        "系统检测：腿部功能即将进入省电模式",
        "起来！动一动！不然你会变成 JPG 的 📸",
        "你的屁股发来一条投诉：太热了，快起来",
        "再坐下去你就成摆件了 🗿",
    )

    val programmer = listOf(
        "检测到内存泄漏：你的腰椎正在 OOM",
        "站起来 || 腰椎报废 // 二选一",
        "if (sitting > 45min) { standUp() }",
        "你的身体抛出了 BackPainException",
        "// TODO: 站起来，现在就是现在",
        "git commit -m '站起来活动了一下'",
        "Stack Overflow：如何说服自己站起来？答案：看这条通知",
    )

    val worker = listOf(
        "打工人，先站起来再说 💼",
        "老板看不到你，但你的腰看得到",
        "摸鱼可以，但站起来摸",
        "站起来，你值得更好的血液循环",
        "再坐下去，你的身体要提离职了",
        "工位不是你的归宿，站起来才是 🏢",
        "今天摸鱼第一式：站起式",
    )

    val motivational = listOf(
        "每一次站立，都是对健康的投资 🚀",
        "坚持站立提醒，三个月后你会感谢自己",
        "伟大的人也需要站起来休息",
        "动起来！你离健康只差一个站立",
        "今天的站立，是明天的活力 ⚡",
        "站起来，你就是自己的英雄",
        "改变从站起来开始，现在就是最好的时机 ✨",
    )

    val styles = mapOf(
        "health_care" to healthCare,
        "humorous" to humorous,
        "programmer" to programmer,
        "worker" to worker,
        "motivational" to motivational,
    )

    val styleNames = mapOf(
        "health_care" to "健康关怀",
        "humorous" to "幽默风趣",
        "programmer" to "程序员专属",
        "worker" to "打工人日常",
        "motivational" to "励志正能量",
    )

    fun randomCopy(style: String, random: Random = Random.Default): String {
        val list = styles[style] ?: healthCare
        return list[random.nextInt(list.size)]
    }
}