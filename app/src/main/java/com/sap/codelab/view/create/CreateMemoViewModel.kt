package com.sap.codelab.view.create

import androidx.lifecycle.SavedStateHandle
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
    private val reminderManager: ReminderManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var pendingDraft: MemoDraft? = null

    private val _uiState = MutableStateFlow(savedStateHandle.restoreUiState())
    val uiState: StateFlow<CreateMemoUiState> = _uiState.asStateFlow()

    fun selectLocation(location: GeoPoint) {
        updateState { it.copy(selectedLocation = location, saveError = null) }
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
        if (_uiState.value.saveStage != MemoSaveStage.EDITING) return

        updateState {
            it.copy(saveStage = MemoSaveStage.PERSISTING, saveError = null)
        }
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
                updateState {
                    it.copy(
                        savedMemoId = result.memoId,
                        savedReminderStatus = result.reminderStatus,
                        saveStage = if (result.reminderStatus == ReminderStatus.PERMISSION_REQUIRED) {
                            MemoSaveStage.AWAITING_PERMISSIONS
                        } else {
                            MemoSaveStage.COMPLETED
                        }
                    )
                }
            }.onFailure {
                updateState { state ->
                    state.copy(saveStage = MemoSaveStage.EDITING, saveError = true)
                }
            }
        }
    }

    fun activateSavedMemo() {
        val memoId = _uiState.value.savedMemoId ?: return
        if (_uiState.value.saveStage != MemoSaveStage.AWAITING_PERMISSIONS) return

        updateState {
            it.copy(saveStage = MemoSaveStage.ACTIVATING, saveError = null)
        }
        viewModelScope.launch {
            runCatching { reminderManager.activateMemo(memoId) }
                .onSuccess { status ->
                    updateState {
                        it.copy(
                            savedReminderStatus = status,
                            saveStage = MemoSaveStage.COMPLETED
                        )
                    }
                }
                .onFailure {
                    updateState { state ->
                        state.copy(
                            savedReminderStatus = ReminderStatus.ERROR,
                            saveStage = MemoSaveStage.COMPLETED
                        )
                    }
                }
        }
    }

    fun onSaveErrorShown() {
        updateState { it.copy(saveError = null) }
    }

    private fun updateState(transform: (CreateMemoUiState) -> CreateMemoUiState) {
        _uiState.update(transform)
        savedStateHandle.persist(_uiState.value)
    }
}

internal data class CreateMemoUiState(
    val selectedLocation: GeoPoint? = null,
    val savedMemoId: Long? = null,
    val savedReminderStatus: ReminderStatus? = null,
    val saveStage: MemoSaveStage = MemoSaveStage.EDITING,
    val saveError: Boolean? = null
) {
    val isSaving: Boolean
        get() = saveStage == MemoSaveStage.PERSISTING || saveStage == MemoSaveStage.ACTIVATING
}

internal enum class MemoSaveStage {
    EDITING,
    PERSISTING,
    AWAITING_PERMISSIONS,
    ACTIVATING,
    COMPLETED
}

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

private const val SELECTED_LATITUDE_KEY = "selectedLatitude"
private const val SELECTED_LONGITUDE_KEY = "selectedLongitude"
private const val SAVED_MEMO_ID_KEY = "savedMemoId"
private const val REMINDER_STATUS_KEY = "savedReminderStatus"
private const val SAVE_STAGE_KEY = "saveStage"

private fun SavedStateHandle.restoreUiState(): CreateMemoUiState {
    val latitude = get<Double>(SELECTED_LATITUDE_KEY)
    val longitude = get<Double>(SELECTED_LONGITUDE_KEY)
    return CreateMemoUiState(
        selectedLocation = if (latitude != null && longitude != null) {
            GeoPoint(latitude, longitude)
        } else {
            null
        },
        savedMemoId = get(SAVED_MEMO_ID_KEY),
        savedReminderStatus = get<String>(REMINDER_STATUS_KEY)
            ?.toEnumOrNull<ReminderStatus>(),
        saveStage = get<String>(SAVE_STAGE_KEY)
            ?.toEnumOrNull<MemoSaveStage>()
            ?: MemoSaveStage.EDITING
    )
}

private fun SavedStateHandle.persist(state: CreateMemoUiState) {
    this[SELECTED_LATITUDE_KEY] = state.selectedLocation?.latitude
    this[SELECTED_LONGITUDE_KEY] = state.selectedLocation?.longitude
    this[SAVED_MEMO_ID_KEY] = state.savedMemoId
    this[REMINDER_STATUS_KEY] = state.savedReminderStatus?.name
    this[SAVE_STAGE_KEY] = state.saveStage.name
}

private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
    enumValues<T>().firstOrNull { it.name == this }
