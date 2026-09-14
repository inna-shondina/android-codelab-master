package com.sap.codelab.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sap.codelab.model.Memo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class MemoDaoTest {

    private lateinit var database: Database

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, Database::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertOrGet_reusesRowForTheSameCreationId(): Unit = runBlocking {
        val memo = Memo(
            creationId = "stable-creation-id",
            title = "Original",
            description = "Persist once",
            reminderLatitude = 42.6977,
            reminderLongitude = 23.3219
        )

        val firstId = database.getMemoDao().insertOrGet(memo)
        val retriedId = database.getMemoDao().insertOrGet(memo.copy(title = "Retry"))

        assertEquals(firstId, retriedId)
        assertEquals(1, database.getMemoDao().observeAll().first().size)
        assertEquals("Original", database.getMemoDao().getMemoById(firstId)?.title)
    }
}
