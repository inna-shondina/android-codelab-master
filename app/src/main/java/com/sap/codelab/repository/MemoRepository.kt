package com.sap.codelab.repository

import com.sap.codelab.model.Memo
import com.sap.codelab.model.ReminderStatus
import kotlinx.coroutines.flow.Flow

/** Repository boundary for memo persistence. */
internal interface MemoRepository {

    fun observeAll(): Flow<List<Memo>>

    fun observeOpen(): Flow<List<Memo>>

    suspend fun insert(memo: Memo): Long

    suspend fun getMemoById(id: Long): Memo?

    suspend fun getOpenReminders(): List<Memo>

    suspend fun markDone(id: Long)

    suspend fun updateReminderStatus(id: Long, status: ReminderStatus)
}
