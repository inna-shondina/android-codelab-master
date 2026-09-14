package com.sap.codelab.view.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sap.codelab.model.Memo
import com.sap.codelab.repository.MemoRepository
import com.sap.codelab.reminder.ReminderManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home Activity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class HomeViewModel(
    private val memoRepository: MemoRepository,
    private val reminderManager: ReminderManager
) : ViewModel() {

    val isShowingAll = MutableStateFlow(false)
    val memos: StateFlow<List<Memo>> = isShowingAll
        .flatMapLatest { showAll ->
            if (showAll) memoRepository.observeAll() else memoRepository.observeOpen()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Loads all memos.
     */
    fun showAllMemos() {
        isShowingAll.value = true
    }

    /**
     * Loads all open (not done) memos.
     */
    fun showOpenMemos() {
        isShowingAll.value = false
    }

    /**
     * Updates the given memo, marking it as done if isChecked is true.
     *
     * @param memo      - the memo to update.
     * @param isChecked - whether the memo has been checked (marked as done).
     */
    fun markDone(memo: Memo, isChecked: Boolean) {
        if (!isChecked || memo.isDone) return
        viewModelScope.launch {
            runCatching { reminderManager.markDone(memo.id) }
        }
    }

    fun restoreReminders() {
        viewModelScope.launch {
            runCatching { reminderManager.restoreReminders() }
        }
    }
}
