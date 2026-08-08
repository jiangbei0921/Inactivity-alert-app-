package com.sitbreak.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "check_in_records",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["type", "timestamp"])
    ]
)
data class CheckInRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = TYPE_STAND_UP,
    val verified: Boolean = false
) {
    companion object {
        const val TYPE_STAND_UP = "stand_up"
        const val TYPE_EXERCISE = "exercise"
    }
}