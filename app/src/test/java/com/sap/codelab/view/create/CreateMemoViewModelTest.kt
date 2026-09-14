package com.sap.codelab.view.create

import androidx.lifecycle.SavedStateHandle
import com.sap.codelab.location.GeoPoint
import com.sap.codelab.model.Memo
import com.sap.codelab.model.ReminderStatus
import com.sap.codelab.reminder.CurrentLocationProvider
import com.sap.codelab.reminder.MemoNotificationPublisher
import com.sap.codelab.reminder.ProximityReminderScheduler
import com.sap.codelab.reminder.ReminderManager
import com.sap.codelab.reminder.ReminderPermissionChecker
import com.sap.codelab.reminder.ScheduleResult
import com.sap.codelab.repository.MemoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
internal class CreateMemoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `memo exists before permissions and activation survives ViewModel recreation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = InMemoryMemoRepository()
            val permissionChecker = MutablePermissionChecker(hasPermissions = false)
            val manager = ReminderManager(
                memoRepository = repository,
                scheduler = RecordingScheduler(),
                notificationPublisher = NoOpNotificationPublisher,
                permissionChecker = permissionChecker,
                currentLocationProvider = CurrentLocationProvider { GeoPoint(42.0, 23.0) }
            )
            val savedState = SavedStateHandle()
            val selectedLocation = GeoPoint(42.6977, 23.3219)
            val original = CreateMemoViewModel(manager, savedState)

            original.selectLocation(selectedLocation)
            assertEquals(
                MemoValidationErrors(),
                original.prepareMemo(title = "Groceries", description = "Buy milk")
            )
            original.savePreparedMemo()
            advanceUntilIdle()

            val savedMemoId = original.uiState.value.savedMemoId
            assertNotNull(savedMemoId)
            assertEquals(MemoSaveStage.AWAITING_PERMISSIONS, original.uiState.value.saveStage)
            assertEquals(1, repository.size)
            original.onPermissionRequestLaunched()

            val restored = CreateMemoViewModel(manager, savedState)
            assertEquals(selectedLocation, restored.uiState.value.selectedLocation)
            assertEquals(savedMemoId, restored.uiState.value.savedMemoId)
            assertEquals(MemoSaveStage.AWAITING_PERMISSIONS, restored.uiState.value.saveStage)
            assertTrue(restored.uiState.value.isPermissionRequestInFlight)

            restored.onPermissionRequestFinished()
            permissionChecker.hasPermissions = true
            restored.activateSavedMemo()
            advanceUntilIdle()

            assertEquals(1, repository.size)
            assertEquals(MemoSaveStage.COMPLETED, restored.uiState.value.saveStage)
            assertEquals(ReminderStatus.ACTIVE, restored.uiState.value.savedReminderStatus)
        }

    private class InMemoryMemoRepository : MemoRepository {
        private val memos = linkedMapOf<Long, Memo>()
        private val all = MutableStateFlow<List<Memo>>(emptyList())
        private var nextId = 1L

        val size: Int
            get() = memos.size

        override fun observeAll(): Flow<List<Memo>> = all

        override fun observeOpen(): Flow<List<Memo>> = all

        override suspend fun insert(memo: Memo): Long {
            val id = nextId++
            memos[id] = memo.copy(id = id)
            publish()
            return id
        }

        override suspend fun getMemoById(id: Long): Memo? = memos[id]

        override suspend fun getOpenReminders(): List<Memo> = memos.values.filterNot(Memo::isDone)

        override suspend fun markDone(id: Long) {
            memos[id] = requireNotNull(memos[id]).copy(isDone = true)
            publish()
        }

        override suspend fun updateReminderStatus(id: Long, status: ReminderStatus) {
            memos[id] = requireNotNull(memos[id]).copy(reminderStatus = status)
            publish()
        }

        private fun publish() {
            all.value = memos.values.toList()
        }
    }

    private class MutablePermissionChecker(
        var hasPermissions: Boolean
    ) : ReminderPermissionChecker {
        override fun hasForegroundLocationPermission(): Boolean = hasPermissions

        override fun hasBackgroundLocationPermission(): Boolean = hasPermissions

        override fun hasNotificationPermission(): Boolean = hasPermissions
    }

    private class RecordingScheduler : ProximityReminderScheduler {
        override fun schedule(memo: Memo): ScheduleResult = ScheduleResult.Scheduled

        override fun cancel(memoId: Long) = Unit
    }

    private object NoOpNotificationPublisher : MemoNotificationPublisher {
        override fun publish(memo: Memo): Boolean = true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
