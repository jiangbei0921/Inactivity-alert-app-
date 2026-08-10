package com.sitbreak.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CheckInDao {

    @Insert
    suspend fun insert(record: CheckInRecord)

    @androidx.room.Delete
    suspend fun delete(record: CheckInRecord)

    @Query("SELECT * FROM check_in_records WHERE timestamp >= :startOfDay AND timestamp < :endOfDay ORDER BY timestamp DESC")
    suspend fun getTodayRecords(startOfDay: Long, endOfDay: Long): List<CheckInRecord>

    @Query(
        """
        SELECT COUNT(*) FROM check_in_records 
        WHERE timestamp >= :startOfDay AND timestamp < :endOfDay
        """
    )
    suspend fun getTodayCount(startOfDay: Long, endOfDay: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM check_in_records 
        WHERE timestamp >= :startOfDay AND timestamp < :endOfDay AND type = :type
        """
    )
    suspend fun getTodayCountByType(startOfDay: Long, endOfDay: Long, type: String): Int

    @Query(
        """
        SELECT ((timestamp + :tzOffset) / 86400000) * 86400000 - :tzOffset AS dayStart, COUNT(*) AS count
        FROM check_in_records
        WHERE timestamp >= :sevenDaysAgo
        GROUP BY dayStart
        ORDER BY dayStart ASC
        """
    )
    suspend fun getDailyCountsForLast7Days(sevenDaysAgo: Long, tzOffset: Long): List<DailyCount>

    data class DailyCount(
        val dayStart: Long,
        val count: Int
    )

    @Query("SELECT COUNT(*) FROM check_in_records")
    suspend fun getTotalCount(): Int

    @Query("SELECT DISTINCT (timestamp / 86400000) * 86400000 AS dayStart FROM check_in_records ORDER BY dayStart ASC")
    suspend fun getAllDistinctDays(): List<Long>

    @Query(
        """
        SELECT ((timestamp + :tzOffset) / 86400000) * 86400000 - :tzOffset AS dayStart, COUNT(*) AS count
        FROM check_in_records
        WHERE type = :type
        GROUP BY dayStart
        ORDER BY dayStart ASC
        """
    )
    suspend fun getAllDayCountsByType(type: String, tzOffset: Long): List<DailyCount>

    @Query(
        """
        SELECT CAST(strftime('%m', datetime((timestamp + :tzOffset) / 1000, 'unixepoch')) AS INTEGER) AS month,
        COUNT(*) AS count
        FROM check_in_records
        WHERE timestamp >= :yearStart
        GROUP BY month
        ORDER BY month ASC
        """
    )
    suspend fun getMonthlyCountsForYear(yearStart: Long, tzOffset: Long): List<MonthlyCount>

    data class MonthlyCount(
        val month: Int,
        val count: Int
    )
}