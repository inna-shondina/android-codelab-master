package com.sap.codelab.view.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sap.codelab.location.GeoPoint
import com.sap.codelab.model.Memo
import com.sap.codelab.model.ReminderStatus
import com.sap.codelab.reminder.ReminderManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for matching CreateMemo view. Handles user interactions.
 */
internal class CreateMemoViewModel(
    private val reminderManager: ReminderManager
) : ViewModel() {

    private var pendingDraft: MemoDraft? = null

    private val _uiState = MutableStateFlow(CreateMemoUiState())
    val uiState: StateFlow<CreateMemoUiState> = _uiState.asStateFlow()

    fun selectLocation(location: GeoPoint) {
        _uiState.update { it.copy(selectedLocation = location, saveError = null) }
    }

    fun prepareMemo(title: String, description: String): MemoValidationErrors {
        val location = _uiState.value.selectedLocation
        val errors = MemoValidationErrors(
            hasTitleError = title.isBlank(),
            hasDescriptionError = description.isBlank(),
            hasLocationError = location == null
        )
        if (errors.hasErrors || location == null || _uiState.value.isSaving) return errors

        pendingDraft = MemoDraft(title.trim(), description.trim(), location)
        return errors
    }

    fun savePreparedMemo() {
        val draft = pendingDraft ?: return
        if (_uiState.value.isSaving) return

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        pendingDraft = null
        viewModelScope.launch {
            runCatching {
                reminderManager.createMemo(
                    Memo(
                        title = draft.title,
                        description = draft.description,
                        reminderLatitude = draft.location.latitude,
                        reminderLongitude = draft.location.longitude
                    )
                )
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        savedMemoId = result.memoId,
                        savedReminderStatus = result.reminderStatus
                    )
                }
            }.onFailure {
                _uiState.update { state -> state.copy(isSaving = false, saveError = true) }
            }
        }
    }

    fun onSaveErrorShown() {
        _uiState.update { it.copy(saveError = null) }
    }
}

internal data class CreateMemoUiState(
    val selectedLocation: GeoPoint? = null,
    val isSaving: Boolean = false,
    val savedMemoId: Long? = null,
    val savedReminderStatus: ReminderStatus? = null,
    val saveError: Boolean? = null
)

private data class MemoDraft(
    val title: String,
    val description: String,
    val location: GeoPoint
)

internal data class MemoValidationErrors(
    val hasTitleError: Boolean = false,
    val hasDescriptionError: Boolean = false,
    val hasLocationError: Boolean = false
) {
    val hasErrors: Boolean
        get() = hasTitleError || hasDescriptionError || hasLocationError
}
