package com.sitbreak.app.data

import com.sitbreak.app.data.db.CheckInDao
import com.sitbreak.app.data.db.CheckInRecord

class CheckInRepository(private val checkInDao: CheckInDao) {

    suspend fun insert(record: CheckInRecord) {
        checkInDao.insert(record)
    }

    suspend fun getTodayStandCount(startOfDay: Long, endOfDay: Long): Int {
        return checkInDao.getTodayCountByType(startOfDay, endOfDay, CheckInRecord.TYPE_STAND_UP)
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

    suspend fun getAllDayCountsByType(type: String, tzOffset: Long): List<CheckInDao.DailyCount> {
        return checkInDao.getAllDayCountsByType(type, tzOffset)
    }

    suspend fun getDailyCountsForLast7Days(sevenDaysAgo: Long, tzOffset: Long): List<CheckInDao.DailyCount> {
        return checkInDao.getDailyCountsForLast7Days(sevenDaysAgo, tzOffset)
    }

    suspend fun getMonthlyCountsForYear(yearStart: Long, tzOffset: Long): List<CheckInDao.MonthlyCount> {
        return checkInDao.getMonthlyCountsForYear(yearStart, tzOffset)
    }
}