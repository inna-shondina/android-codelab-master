package com.sap.codelab.repository

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sap.codelab.di.MIGRATION_1_2
import com.sap.codelab.model.ReminderStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class DatabaseMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrationFromVersion1_preservesLegacyMemoAndMakesReminderInactive(): Unit = runBlocking {
        createLegacyDatabase(version = 1) { database ->
            database.execSQL(VERSION_1_SCHEMA)
            database.execSQL(
                """
                INSERT INTO memo (
                    id, title, description, reminderDate,
                    reminderLatitude, reminderLongitude, isDone
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(7L, "Legacy memo", "Must survive", 1_500L, 42L, 23L, 0)
            )
        }

        val database = openMigratedDatabase()
        try {
            val memo = requireNotNull(database.getMemoDao().getMemoById(7L))

            assertEquals("Legacy memo", memo.title)
            assertEquals("migrated-7", memo.creationId)
            assertEquals("Must survive", memo.description)
            assertEquals(42.0, memo.reminderLatitude, 0.0)
            assertEquals(23.0, memo.reminderLongitude, 0.0)
            assertEquals(ReminderStatus.INACTIVE, memo.reminderStatus)
            assertFalse(memo.isDone)
        } finally {
            database.close()
        }
    }

    private fun createLegacyDatabase(
        version: Int,
        populate: (SupportSQLiteDatabase) -> Unit
    ) {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        populate(db)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                }
            )
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase
        }
    }

    private fun openMigratedDatabase(): Database =
        Room.databaseBuilder(context, Database::class.java, TEST_DATABASE)
            .addMigrations(MIGRATION_1_2)
            .build()
            .also { it.openHelper.writableDatabase }

    private companion object {
        const val TEST_DATABASE = "migration-test"

        val VERSION_1_SCHEMA =
            """
            CREATE TABLE IF NOT EXISTS memo (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                reminderDate INTEGER NOT NULL,
                reminderLatitude INTEGER NOT NULL,
                reminderLongitude INTEGER NOT NULL,
                isDone INTEGER NOT NULL
            )
            """.trimIndent()
    }
}
