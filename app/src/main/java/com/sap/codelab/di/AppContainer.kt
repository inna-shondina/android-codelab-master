package com.sap.codelab.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sap.codelab.location.LocationPickerFactory
import com.sap.codelab.location.MapLibreLocationPicker
import com.sap.codelab.repository.Database
import com.sap.codelab.repository.MemoRepository
import com.sap.codelab.repository.RoomMemoRepository
import com.sap.codelab.reminder.AndroidCurrentLocationProvider
import com.sap.codelab.reminder.AndroidMemoNotificationPublisher
import com.sap.codelab.reminder.AndroidProximityReminderScheduler
import com.sap.codelab.reminder.AndroidReminderPermissionChecker
import com.sap.codelab.reminder.ReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private const val DATABASE_NAME = "codelab"

/** Small composition root; a DI framework would be unnecessary for this app's object graph. */
internal class AppContainer(context: Context) {

    private val database = Room.databaseBuilder(
        context,
        Database::class.java,
        DATABASE_NAME
    ).addMigrations(MIGRATION_1_2).build()

    val memoRepository: MemoRepository = RoomMemoRepository(database.getMemoDao())
    val reminderPermissionChecker = AndroidReminderPermissionChecker(context)
    private val currentLocationProvider = AndroidCurrentLocationProvider(
        context,
        reminderPermissionChecker
    )
    val reminderManager = ReminderManager(
        memoRepository = memoRepository,
        scheduler = AndroidProximityReminderScheduler(context, reminderPermissionChecker),
        notificationPublisher = AndroidMemoNotificationPublisher(context),
        permissionChecker = reminderPermissionChecker,
        currentLocationProvider = currentLocationProvider
    )
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val viewModelFactory = MemoViewModelFactory(memoRepository, reminderManager)
    val locationPickerFactory: LocationPickerFactory = LocationPickerFactory { pickerContext ->
        MapLibreLocationPicker(pickerContext)
    }
}

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memo_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                creationId TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                reminderLatitude REAL NOT NULL,
                reminderLongitude REAL NOT NULL,
                reminderStatus TEXT NOT NULL,
                isDone INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO memo_new (
                id, creationId, title, description, reminderLatitude, reminderLongitude,
                reminderStatus, isDone
            )
            SELECT
                id, 'migrated-' || id, title, description,
                CAST(reminderLatitude AS REAL), CAST(reminderLongitude AS REAL),
                'INACTIVE', isDone
            FROM memo
            """.trimIndent()
        )
        db.execSQL("DROP TABLE memo")
        db.execSQL("ALTER TABLE memo_new RENAME TO memo")
        db.execSQL(
            "CREATE UNIQUE INDEX `index_memo_creationId` ON `memo` (`creationId`)"
        )
    }
}
