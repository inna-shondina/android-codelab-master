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
import java.util.UUID

/**
 * ViewModel for matching CreateMemo view. Handles user interactions.
 */
internal class CreateMemoViewModel(
    private val reminderManager: ReminderManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val restoredUiState = savedStateHandle.restoreUiState()
    private var pendingDraft: MemoDraft? = if (restoredUiState.saveStage == MemoSaveStage.PERSISTING) {
        savedStateHandle.restoreDraft(restoredUiState.selectedLocation)
    } else {
        null
    }
    private val _uiState = MutableStateFlow(restoredUiState)
    val uiState: StateFlow<CreateMemoUiState> = _uiState.asStateFlow()

    init {
        savedStateHandle.persist(restoredUiState)
        when (restoredUiState.saveStage) {
            MemoSaveStage.PERSISTING -> pendingDraft?.let(::persistAndActivate)
                ?: updateState { it.copy(saveStage = MemoSaveStage.EDITING) }

            MemoSaveStage.ACTIVATING -> restoredUiState.savedMemoId?.let { memoId ->
                resumeActivation(memoId, restoredUiState.requestPermissionsAfterActivation)
            } ?: updateState { it.copy(saveStage = MemoSaveStage.EDITING) }

            else -> Unit
        }
    }

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

        pendingDraft = MemoDraft(
            creationId = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            location = location
        )
        return errors
    }

    fun savePreparedMemo() {
        val draft = pendingDraft ?: return
        if (_uiState.value.saveStage != MemoSaveStage.EDITING) return

        savedStateHandle.persist(draft)
        updateState { it.copy(saveStage = MemoSaveStage.PERSISTING, saveError = null) }
        pendingDraft = null
        persistAndActivate(draft)
    }

    private fun persistAndActivate(draft: MemoDraft) {
        pendingDraft = null
        viewModelScope.launch {
            runCatching {
                reminderManager.persistMemo(
                    Memo(
                        creationId = draft.creationId,
                        title = draft.title,
                        description = draft.description,
                        reminderLatitude = draft.location.latitude,
                        reminderLongitude = draft.location.longitude
                    )
                )
            }.onSuccess { memoId ->
                updateState {
                    it.copy(
                        savedMemoId = memoId,
                        savedReminderStatus = ReminderStatus.PENDING,
                        saveStage = MemoSaveStage.ACTIVATING,
                        requestPermissionsAfterActivation = true
                    )
                }
                savedStateHandle.clearDraft()
                finishActivation(memoId, requestPermissionsIfRequired = true)
            }.onFailure {
                savedStateHandle.clearDraft()
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
            it.copy(
                saveStage = MemoSaveStage.ACTIVATING,
                requestPermissionsAfterActivation = false,
                saveError = null
            )
        }
        resumeActivation(memoId, requestPermissionsIfRequired = false)
    }

    private fun resumeActivation(memoId: Long, requestPermissionsIfRequired: Boolean) {
        viewModelScope.launch {
            finishActivation(memoId, requestPermissionsIfRequired)
        }
    }

    private suspend fun finishActivation(
        memoId: Long,
        requestPermissionsIfRequired: Boolean
    ) {
        runCatching { reminderManager.activateMemo(memoId) }
            .onSuccess { status ->
                updateState {
                    val nextStage = if (
                        status == ReminderStatus.PERMISSION_REQUIRED &&
                        requestPermissionsIfRequired
                    ) {
                        MemoSaveStage.AWAITING_PERMISSIONS
                    } else {
                        MemoSaveStage.COMPLETED
                    }
                    it.copy(
                        savedReminderStatus = status,
                        saveStage = nextStage,
                        requestPermissionsAfterActivation = false
                    )
                }
            }
            .onFailure {
                updateState { state ->
                    state.copy(
                        savedReminderStatus = ReminderStatus.ERROR,
                        saveStage = MemoSaveStage.COMPLETED,
                        requestPermissionsAfterActivation = false
                    )
                }
            }
    }

    fun onPermissionRequestLaunched() {
        updateState { it.copy(isPermissionRequestInFlight = true) }
    }

    fun onPermissionRequestFinished() {
        updateState { it.copy(isPermissionRequestInFlight = false) }
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
    val requestPermissionsAfterActivation: Boolean = false,
    val isPermissionRequestInFlight: Boolean = false,
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
    val creationId: String,
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
private const val REQUEST_PERMISSIONS_AFTER_ACTIVATION_KEY = "requestPermissionsAfterActivation"
private const val PERMISSION_REQUEST_IN_FLIGHT_KEY = "permissionRequestInFlight"
private const val DRAFT_CREATION_ID_KEY = "draftCreationId"
private const val DRAFT_TITLE_KEY = "draftTitle"
private const val DRAFT_DESCRIPTION_KEY = "draftDescription"

private fun SavedStateHandle.restoreUiState(): CreateMemoUiState {
    val latitude = get<Double>(SELECTED_LATITUDE_KEY)
    val longitude = get<Double>(SELECTED_LONGITUDE_KEY)
    val saveStage = get<String>(SAVE_STAGE_KEY)
        ?.toEnumOrNull<MemoSaveStage>()
        ?: MemoSaveStage.EDITING
    return CreateMemoUiState(
        selectedLocation = if (latitude != null && longitude != null) {
            GeoPoint(latitude, longitude)
        } else {
            null
        },
        savedMemoId = get(SAVED_MEMO_ID_KEY),
        savedReminderStatus = get<String>(REMINDER_STATUS_KEY)
            ?.toEnumOrNull<ReminderStatus>(),
        saveStage = saveStage,
        requestPermissionsAfterActivation =
            get<Boolean>(REQUEST_PERMISSIONS_AFTER_ACTIVATION_KEY) == true,
        isPermissionRequestInFlight = get<Boolean>(PERMISSION_REQUEST_IN_FLIGHT_KEY) == true
    )
}

private fun SavedStateHandle.persist(state: CreateMemoUiState) {
    this[SELECTED_LATITUDE_KEY] = state.selectedLocation?.latitude
    this[SELECTED_LONGITUDE_KEY] = state.selectedLocation?.longitude
    this[SAVED_MEMO_ID_KEY] = state.savedMemoId
    this[REMINDER_STATUS_KEY] = state.savedReminderStatus?.name
    this[SAVE_STAGE_KEY] = state.saveStage.name
    this[REQUEST_PERMISSIONS_AFTER_ACTIVATION_KEY] = state.requestPermissionsAfterActivation
    this[PERMISSION_REQUEST_IN_FLIGHT_KEY] = state.isPermissionRequestInFlight
}

private fun SavedStateHandle.persist(draft: MemoDraft) {
    this[DRAFT_CREATION_ID_KEY] = draft.creationId
    this[DRAFT_TITLE_KEY] = draft.title
    this[DRAFT_DESCRIPTION_KEY] = draft.description
}

private fun SavedStateHandle.restoreDraft(location: GeoPoint?): MemoDraft? {
    location ?: return null
    return MemoDraft(
        creationId = get<String>(DRAFT_CREATION_ID_KEY) ?: return null,
        title = get<String>(DRAFT_TITLE_KEY) ?: return null,
        description = get<String>(DRAFT_DESCRIPTION_KEY) ?: return null,
        location = location
    )
}

private fun SavedStateHandle.clearDraft() {
    remove<String>(DRAFT_CREATION_ID_KEY)
    remove<String>(DRAFT_TITLE_KEY)
    remove<String>(DRAFT_DESCRIPTION_KEY)
}

private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
    enumValues<T>().firstOrNull { it.name == this }
