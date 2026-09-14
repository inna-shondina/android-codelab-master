package com.sap.codelab.view.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sap.codelab.location.GeoPoint
import com.sap.codelab.model.Memo
import com.sap.codelab.repository.MemoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for matching CreateMemo view. Handles user interactions.
 */
internal class CreateMemoViewModel(
    private val memoRepository: MemoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateMemoUiState())
    val uiState: StateFlow<CreateMemoUiState> = _uiState.asStateFlow()

    fun selectLocation(location: GeoPoint) {
        _uiState.update { it.copy(selectedLocation = location, saveError = null) }
    }

    fun saveMemo(title: String, description: String): MemoValidationErrors {
        val location = _uiState.value.selectedLocation
        val errors = MemoValidationErrors(
            hasTitleError = title.isBlank(),
            hasDescriptionError = description.isBlank(),
            hasLocationError = location == null
        )
        if (errors.hasErrors || location == null || _uiState.value.isSaving) return errors

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            runCatching {
                memoRepository.insert(
                    Memo(
                        title = title.trim(),
                        description = description.trim(),
                        reminderLatitude = location.latitude,
                        reminderLongitude = location.longitude
                    )
                )
            }.onSuccess { memoId ->
                _uiState.update { it.copy(isSaving = false, savedMemoId = memoId) }
            }.onFailure {
                _uiState.update { state -> state.copy(isSaving = false, saveError = true) }
            }
        }
        return errors
    }

    fun onSaveErrorShown() {
        _uiState.update { it.copy(saveError = null) }
    }
}

internal data class CreateMemoUiState(
    val selectedLocation: GeoPoint? = null,
    val isSaving: Boolean = false,
    val savedMemoId: Long? = null,
    val saveError: Boolean? = null
)

internal data class MemoValidationErrors(
    val hasTitleError: Boolean = false,
    val hasDescriptionError: Boolean = false,
    val hasLocationError: Boolean = false
) {
    val hasErrors: Boolean
        get() = hasTitleError || hasDescriptionError || hasLocationError
}
