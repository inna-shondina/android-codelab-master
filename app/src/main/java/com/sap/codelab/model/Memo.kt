package com.sap.codelab.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a memo.
 */
@Entity(tableName = "memo")
internal data class Memo(
    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "reminderLatitude")
    val reminderLatitude: Double,
    @ColumnInfo(name = "reminderLongitude")
    val reminderLongitude: Double,
    @ColumnInfo(name = "reminderStatus")
    val reminderStatus: ReminderStatus = ReminderStatus.PENDING,
    @ColumnInfo(name = "isDone")
    val isDone: Boolean = false
)

internal enum class ReminderStatus {
    PENDING,
    ACTIVE,
    PERMISSION_REQUIRED,
    TRIGGERED,
    ERROR
}
