package com.sitbreak.app.data

import com.sitbreak.app.data.db.CheckInDao
import com.sitbreak.app.data.db.CheckInRecord

class CheckInRepository(private val checkInDao: CheckInDao) {

    suspend fun insert(record: CheckInRecord) {
        checkInDao.insert(record)
    }

    suspend fun getTodayStandCount(startOfDay: Long, endOfDay: Long): Int {
        return checkInDao.getTodayCountByType(startOfDay, endOfDay, "stand_up")
    }

    suspend fun getTodayCount(startOfDay: Long, endOfDay: Long): Int {
        return checkInDao.getTodayCount(startOfDay, endOfDay)
    }

    suspend fun getTodayRecords(startOfDay: Long, endOfDay: Long): List<CheckInRecord> {
        return checkInDao.getTodayRecords(startOfDay, endOfDay)
    }

    suspend fun getTotalCount(): Int {
        return checkInDao.getTotalCount()
    }

    suspend fun getAllDistinctDays(): List<Long> {
        return checkInDao.getAllDistinctDays()
    }

    suspend fun getAllDayCountsByType(type: String): List<CheckInDao.DailyCount> {
        return checkInDao.getAllDayCountsByType(type)
    }

    suspend fun getDailyCountsForLast7Days(sevenDaysAgo: Long): List<CheckInDao.DailyCount> {
        return checkInDao.getDailyCountsForLast7Days(sevenDaysAgo)
    }

    suspend fun getMonthlyCountsForYear(yearStart: Long): List<CheckInDao.MonthlyCount> {
        return checkInDao.getMonthlyCountsForYear(yearStart)
    }
}