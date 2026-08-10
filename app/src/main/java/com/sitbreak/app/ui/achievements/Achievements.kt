package com.sitbreak.app.ui.achievements

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.ui.graphics.vector.ImageVector

/** 成就度量的数据来源。 */
enum class AchievementMetric { TOTAL, STREAK, TODAY }

/** 单个成就的静态定义（阈值与图标），解锁态由统计数据实时派生，无需持久化。 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val metric: AchievementMetric,
    val threshold: Int,
)

/** 派生出的展示态：携带当前进度与是否解锁。 */
data class AchievementUi(
    val achievement: Achievement,
    val unlocked: Boolean,
    val progress: Int,
    val goal: Int,
)

/** 全部成就定义（派生计算，数据只来自本地 Room，不会联网）。 */
val ACHIEVEMENTS: List<Achievement> = listOf(
    Achievement("first", "初次打卡", "完成第一次起身打卡", Icons.Outlined.EventSeat, AchievementMetric.TOTAL, 1),
    Achievement("total10", "十全十美", "累计打卡 10 次", Icons.Outlined.FitnessCenter, AchievementMetric.TOTAL, 10),
    Achievement("total50", "半百里程", "累计打卡 50 次", Icons.Outlined.FitnessCenter, AchievementMetric.TOTAL, 50),
    Achievement("total100", "百炼成钢", "累计打卡 100 次", Icons.Outlined.FitnessCenter, AchievementMetric.TOTAL, 100),
    Achievement("total365", "一年如一日", "累计打卡 365 次", Icons.Outlined.EmojiEvents, AchievementMetric.TOTAL, 365),
    Achievement("streak3", "三日尝鲜", "连续打卡 3 天", Icons.Outlined.LocalFireDepartment, AchievementMetric.STREAK, 3),
    Achievement("streak7", "一周坚持", "连续打卡 7 天", Icons.Outlined.LocalFireDepartment, AchievementMetric.STREAK, 7),
    Achievement("streak30", "月度达人", "连续打卡 30 天", Icons.Outlined.LocalFireDepartment, AchievementMetric.STREAK, 30),
    Achievement("streak100", "百日习惯", "连续打卡 100 天", Icons.Outlined.LocalFireDepartment, AchievementMetric.STREAK, 100),
    Achievement("today5", "今日五连", "单日打卡 5 次", Icons.Outlined.FitnessCenter, AchievementMetric.TODAY, 5),
    Achievement("today10", "今日十连", "单日打卡 10 次", Icons.Outlined.FitnessCenter, AchievementMetric.TODAY, 10),
    Achievement("today20", "今日二十连", "单日打卡 20 次", Icons.Outlined.EmojiEvents, AchievementMetric.TODAY, 20),
)

/** 触发「连续天数庆祝」的里程碑集合。 */
val STREAK_MILESTONES: Set<Int> = setOf(3, 7, 14, 21, 30, 50, 100, 200, 365, 500, 1000)

/** 依据实时统计派生每个成就的解锁态与进度。 */
fun computeAchievements(total: Int, streak: Int, today: Int): List<AchievementUi> {
    return ACHIEVEMENTS.map { a ->
        val progress = when (a.metric) {
            AchievementMetric.TOTAL -> total
            AchievementMetric.STREAK -> streak
            AchievementMetric.TODAY -> today
        }
        AchievementUi(a, progress >= a.threshold, progress.coerceAtMost(a.threshold), a.threshold)
    }
}
