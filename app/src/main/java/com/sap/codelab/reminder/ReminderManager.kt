package com.sap.codelab.reminder

import com.sap.codelab.location.GeoPoint
import com.sap.codelab.location.distanceTo
import com.sap.codelab.model.Memo
import com.sap.codelab.model.ReminderStatus
import com.sap.codelab.repository.MemoRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class MemoCreationResult(
    val memoId: Long,
    val reminderStatus: ReminderStatus
)

/** Coordinates persistence, platform proximity alerts, and user notifications. */
internal class ReminderManager(
    private val memoRepository: MemoRepository,
    private val scheduler: ProximityReminderScheduler,
    private val notificationPublisher: MemoNotificationPublisher,
    private val permissionChecker: ReminderPermissionChecker,
    private val currentLocationProvider: CurrentLocationProvider
) {

    private val operationMutex = Mutex()

    suspend fun createMemo(memo: Memo): MemoCreationResult = operationMutex.withLock {
        val memoId = memoRepository.insert(memo)
        val savedMemo = memo.copy(id = memoId)
        val status = register(savedMemo)
        MemoCreationResult(memoId, status)
    }

    suspend fun markDone(memoId: Long) = operationMutex.withLock {
        scheduler.cancel(memoId)
        memoRepository.markDone(memoId)
    }

    suspend fun restoreReminders() = operationMutex.withLock {
        memoRepository.getOpenReminders().forEach { memo -> register(memo) }
    }

    suspend fun onProximityEvent(memoId: Long, isEntering: Boolean) = operationMutex.withLock {
        val memo = memoRepository.getMemoById(memoId) ?: return@withLock
        if (memo.isDone) return@withLock

        if (memo.reminderStatus == ReminderStatus.WAITING_FOR_EXIT) {
            if (!isEntering) updateStatus(memo, ReminderStatus.ACTIVE)
            return@withLock
        }
        if (!isEntering || memo.reminderStatus != ReminderStatus.ACTIVE) return@withLock

        val status = if (notificationPublisher.publish(memo)) {
            ReminderStatus.TRIGGERED
        } else {
            ReminderStatus.PERMISSION_REQUIRED
        }
        scheduler.cancel(memoId)
        memoRepository.updateReminderStatus(memoId, status)
    }

    private suspend fun register(memo: Memo): ReminderStatus {
        if (!permissionChecker.hasAllReminderPermissions()) {
            scheduler.cancel(memo.id)
            return updateStatus(memo, ReminderStatus.PERMISSION_REQUIRED)
        }
        val readyStatus = when (memo.reminderStatus) {
            ReminderStatus.WAITING_FOR_EXIT -> ReminderStatus.WAITING_FOR_EXIT
            ReminderStatus.ACTIVE -> ReminderStatus.ACTIVE
            else -> if (isCurrentlyInside(memo)) {
                ReminderStatus.WAITING_FOR_EXIT
            } else {
                ReminderStatus.ACTIVE
            }
        }
        val status = when (scheduler.schedule(memo)) {
            ScheduleResult.Scheduled -> readyStatus
            ScheduleResult.PermissionRequired -> ReminderStatus.PERMISSION_REQUIRED
            is ScheduleResult.Failed -> ReminderStatus.ERROR
        }
        return updateStatus(memo, status)
    }

    private suspend fun updateStatus(memo: Memo, status: ReminderStatus): ReminderStatus {
        if (memo.reminderStatus != status) {
            memoRepository.updateReminderStatus(memo.id, status)
        }
        return status
    }

    private suspend fun isCurrentlyInside(memo: Memo): Boolean {
        val currentLocation = currentLocationProvider.currentLocation() ?: return false
        val reminderLocation = GeoPoint(memo.reminderLatitude, memo.reminderLongitude)
        return currentLocation.distanceTo(reminderLocation) <= PROXIMITY_RADIUS_METERS
    }
}
