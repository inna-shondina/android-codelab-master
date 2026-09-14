package com.sap.codelab.repository

import com.sap.codelab.model.Memo
import com.sap.codelab.model.ReminderStatus
import kotlinx.coroutines.flow.Flow

/**
 * The repository is used to retrieve data from a data source.
 */
internal class RoomMemoRepository(
    private val memoDao: MemoDao
) : MemoRepository {

    override fun observeAll(): Flow<List<Memo>> = memoDao.observeAll()

    override fun observeOpen(): Flow<List<Memo>> = memoDao.observeOpen()

    override suspend fun insert(memo: Memo): Long = memoDao.insertOrGet(memo)

    override suspend fun getMemoById(id: Long): Memo? = memoDao.getMemoById(id)

    override suspend fun getOpenReminders(): List<Memo> = memoDao.getOpenReminders()

    override suspend fun markDone(id: Long) = memoDao.markDone(id)

    override suspend fun updateReminderStatus(id: Long, status: ReminderStatus) =
        memoDao.updateReminderStatus(id, status)
}
