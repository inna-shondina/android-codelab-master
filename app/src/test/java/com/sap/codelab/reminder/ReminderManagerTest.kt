package com.sap.codelab.reminder

import com.sap.codelab.model.Memo
import com.sap.codelab.model.ReminderStatus
import com.sap.codelab.repository.MemoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ReminderManagerTest {

    @Test
    fun `creating memo schedules alert and persists active status`() = runTest {
        val fixture = Fixture(hasPermissions = true)

        val result = fixture.manager.createMemo(testMemo())

        assertEquals(ReminderStatus.ACTIVE, result.reminderStatus)
        assertEquals(listOf(result.memoId), fixture.scheduler.scheduledIds)
        assertEquals(ReminderStatus.ACTIVE, fixture.repository.memo(result.memoId)?.reminderStatus)
    }

    @Test
    fun `creating memo without permissions saves inactive reminder`() = runTest {
        val fixture = Fixture(hasPermissions = false)

        val result = fixture.manager.createMemo(testMemo())

        assertEquals(ReminderStatus.PERMISSION_REQUIRED, result.reminderStatus)
        assertTrue(fixture.scheduler.scheduledIds.isEmpty())
        assertEquals(listOf(result.memoId), fixture.scheduler.cancelledIds)
    }

    @Test
    fun `enter event publishes exactly once and completes reminder`() = runTest {
        val fixture = Fixture(hasPermissions = true)
        val created = fixture.manager.createMemo(testMemo())

        fixture.manager.onProximityEvent(created.memoId, isEntering = true)
        fixture.manager.onProximityEvent(created.memoId, isEntering = true)

        assertEquals(listOf(created.memoId), fixture.publisher.publishedIds)
        assertEquals(ReminderStatus.TRIGGERED, fixture.repository.memo(created.memoId)?.reminderStatus)
    }

    @Test
    fun `exit event does not publish notification`() = runTest {
        val fixture = Fixture(hasPermissions = true)
        val created = fixture.manager.createMemo(testMemo())

        fixture.manager.onProximityEvent(created.memoId, isEntering = false)

        assertTrue(fixture.publisher.publishedIds.isEmpty())
        assertEquals(ReminderStatus.ACTIVE, fixture.repository.memo(created.memoId)?.reminderStatus)
    }

    @Test
    fun `restore registers pending open reminders`() = runTest {
        val fixture = Fixture(hasPermissions = true)
        val memoId = fixture.repository.insert(
            testMemo().copy(reminderStatus = ReminderStatus.PERMISSION_REQUIRED)
        )

        fixture.manager.restoreReminders()

        assertEquals(listOf(memoId), fixture.scheduler.scheduledIds)
        assertEquals(ReminderStatus.ACTIVE, fixture.repository.memo(memoId)?.reminderStatus)
    }

    @Test
    fun `notification denial returns reminder to permission required state`() = runTest {
        val fixture = Fixture(hasPermissions = true, canPublish = false)
        val created = fixture.manager.createMemo(testMemo())

        fixture.manager.onProximityEvent(created.memoId, isEntering = true)

        assertEquals(
            ReminderStatus.PERMISSION_REQUIRED,
            fixture.repository.memo(created.memoId)?.reminderStatus
        )
        assertEquals(listOf(created.memoId), fixture.publisher.publishedIds)
    }

    @Test
    fun `marking memo done cancels its proximity alert`() = runTest {
        val fixture = Fixture(hasPermissions = true)
        val created = fixture.manager.createMemo(testMemo())

        fixture.manager.markDone(created.memoId)

        assertEquals(created.memoId, fixture.scheduler.cancelledIds.last())
        assertEquals(true, fixture.repository.memo(created.memoId)?.isDone)
    }

    private class Fixture(
        hasPermissions: Boolean,
        canPublish: Boolean = true
    ) {
        val repository = FakeMemoRepository()
        val scheduler = FakeScheduler()
        val publisher = FakeNotificationPublisher(canPublish)
        val manager = ReminderManager(
            memoRepository = repository,
            scheduler = scheduler,
            notificationPublisher = publisher,
            permissionChecker = FakePermissionChecker(hasPermissions)
        )
    }

    private class FakeMemoRepository : MemoRepository {
        private val storedMemos = linkedMapOf<Long, Memo>()
        private val all = MutableStateFlow<List<Memo>>(emptyList())
        private var nextId = 1L

        override fun observeAll(): Flow<List<Memo>> = all

        override fun observeOpen(): Flow<List<Memo>> = all

        override suspend fun insert(memo: Memo): Long {
            val id = nextId++
            storedMemos[id] = memo.copy(id = id)
            publish()
            return id
        }

        override suspend fun getMemoById(id: Long): Memo? = storedMemos[id]

        override suspend fun getOpenReminders(): List<Memo> = storedMemos.values.filter {
            !it.isDone && it.reminderStatus != ReminderStatus.TRIGGERED &&
                it.reminderStatus != ReminderStatus.INACTIVE
        }

        override suspend fun markDone(id: Long) {
            storedMemos[id] = requireNotNull(storedMemos[id]).copy(isDone = true)
            publish()
        }

        override suspend fun updateReminderStatus(id: Long, status: ReminderStatus) {
            storedMemos[id] = requireNotNull(storedMemos[id]).copy(reminderStatus = status)
            publish()
        }

        fun memo(id: Long): Memo? = storedMemos[id]

        private fun publish() {
            all.value = storedMemos.values.toList()
        }
    }

    private class FakeScheduler : ProximityReminderScheduler {
        val scheduledIds = mutableListOf<Long>()
        val cancelledIds = mutableListOf<Long>()

        override fun schedule(memo: Memo): ScheduleResult {
            scheduledIds += memo.id
            return ScheduleResult.Scheduled
        }

        override fun cancel(memoId: Long) {
            cancelledIds += memoId
        }
    }

    private class FakeNotificationPublisher(
        private val canPublish: Boolean
    ) : MemoNotificationPublisher {
        val publishedIds = mutableListOf<Long>()

        override fun publish(memo: Memo): Boolean {
            publishedIds += memo.id
            return canPublish
        }
    }

    private class FakePermissionChecker(
        private val hasPermissions: Boolean
    ) : ReminderPermissionChecker {
        override fun hasForegroundLocationPermission(): Boolean = hasPermissions
        override fun hasBackgroundLocationPermission(): Boolean = hasPermissions
        override fun hasNotificationPermission(): Boolean = hasPermissions
    }

    private companion object {
        fun testMemo() = Memo(
            title = "Groceries",
            description = "Buy milk",
            reminderLatitude = 42.6977,
            reminderLongitude = 23.3219
        )
    }
}
