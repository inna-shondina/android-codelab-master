package com.sap.codelab.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sap.codelab.model.Memo
import com.sap.codelab.model.ReminderStatus
import kotlinx.coroutines.flow.Flow

/**
 * The Dao representation of a Memo.
 */
@Dao
internal interface MemoDao {

    /**
     * @return all memos that are currently in the database.
     */
    @Query("SELECT * FROM memo ORDER BY id DESC")
    fun observeAll(): Flow<List<Memo>>

    /**
     * @return all memos that are currently in the database and have not yet been marked as "done".
     */
    @Query("SELECT * FROM memo WHERE isDone = 0 ORDER BY id DESC")
    fun observeOpen(): Flow<List<Memo>>

    /**
     * Inserts the given Memo into the database. We currently do not support updating of memos.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringConflict(memo: Memo): Long

    @Query("SELECT * FROM memo WHERE creationId = :creationId")
    suspend fun getMemoByCreationId(creationId: String): Memo?

    @Transaction
    suspend fun insertOrGet(memo: Memo): Long {
        val insertedId = insertIgnoringConflict(memo)
        if (insertedId != INSERT_CONFLICT) return insertedId
        return requireNotNull(getMemoByCreationId(memo.creationId)) {
            "Memo insert conflicted without a matching creationId"
        }.id
    }

    /**
     * @return the memo whose id matches the given id.
     */
    @Query("SELECT * FROM memo WHERE id = :memoId")
    suspend fun getMemoById(memoId: Long): Memo?

    @Query(
        """
        SELECT * FROM memo
        WHERE isDone = 0
        AND reminderStatus IN (
            'PENDING', 'WAITING_FOR_EXIT', 'ACTIVE', 'PERMISSION_REQUIRED', 'ERROR'
        )
        """
    )
    suspend fun getOpenReminders(): List<Memo>

    @Query("UPDATE memo SET isDone = 1 WHERE id = :memoId")
    suspend fun markDone(memoId: Long)

    @Query("UPDATE memo SET reminderStatus = :status WHERE id = :memoId")
    suspend fun updateReminderStatus(memoId: Long, status: ReminderStatus)

    private companion object {
        const val INSERT_CONFLICT = -1L
    }
}
