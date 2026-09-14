package com.sap.codelab.repository

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.sap.codelab.model.Memo
import com.sap.codelab.model.ReminderStatus

/**
 * That database that is used to store information.
 */
@Database(entities = [Memo::class], version = 2, exportSchema = true)
@TypeConverters(DatabaseConverters::class)
internal abstract class Database : RoomDatabase() {

    abstract fun getMemoDao(): MemoDao
}

internal class DatabaseConverters {

    @TypeConverter
    fun reminderStatusToString(value: ReminderStatus): String = value.name

    @TypeConverter
    fun stringToReminderStatus(value: String): ReminderStatus = ReminderStatus.valueOf(value)
}
