package com.sap.codelab.view.create

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.net.toUri
import com.sap.codelab.R
import com.sap.codelab.databinding.ActivityCreateMemoBinding
import com.sap.codelab.location.LocationPicker
import com.sap.codelab.model.ReminderStatus
import com.sap.codelab.repository.App
import com.sap.codelab.reminder.ReminderPermissionChecker
import com.sap.codelab.view.applySideAndBottomInsetsToPadding
import com.sap.codelab.view.enableEdgeToEdgeLayout
import kotlinx.coroutines.launch

/**
 * Activity that allows a user to create a new Memo.
 */
internal class CreateMemo : AppCompatActivity() {

    private lateinit var binding: ActivityCreateMemoBinding
    private lateinit var model: CreateMemoViewModel
    private lateinit var locationPicker: LocationPicker
    private lateinit var permissionChecker: ReminderPermissionChecker
    private var saveMenuItem: MenuItem? = null
    private var isPermissionFlowActive = false

    private val foregroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            requestRemainingPermissions()
        } else {
            completePermissionFlow()
        }
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) requestRemainingPermissions() else completePermissionFlow()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (permissionChecker.hasBackgroundLocationPermission()) {
            requestRemainingPermissions()
        } else {
            completePermissionFlow()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        completePermissionFlow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isPermissionFlowActive = savedInstanceState?.getBoolean(PERMISSION_FLOW_ACTIVE_KEY) == true
        enableEdgeToEdgeLayout()
        binding = ActivityCreateMemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.contentCreateMemo.root.applySideAndBottomInsetsToPadding()
        setSupportActionBar(binding.toolbar)
        val container = (application as App).container
        permissionChecker = container.reminderPermissionChecker
        model = ViewModelProvider(this, container.viewModelFactory)[CreateMemoViewModel::class.java]
        locationPicker = container.locationPickerFactory.create(this)
        locationPicker.attach(
            host = binding.contentCreateMemo.mapHost,
            savedInstanceState = savedInstanceState,
            initialLocation = model.uiState.value.selectedLocation,
            onLocationSelected = model::selectLocation
        )
        observeUiState()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_create_memo, menu)
        saveMenuItem = menu.findItem(R.id.action_save).apply {
            isEnabled = isSaveEnabled()
        }
        return true
    }

    /**
     * Handles actionbar interactions.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveMemo()
                true
            }

            else             -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Saves the memo if the input is valid; otherwise shows the corresponding error messages.
     */
    private fun saveMemo() {
        binding.contentCreateMemo.run {
            val errors = model.prepareMemo(
                title = memoTitle.text.toString(),
                description = memoDescription.text.toString()
            )
            memoTitleContainer.error = getErrorMessage(
                errors.hasTitleError,
                R.string.memo_title_empty_error
            )
            memoDescriptionContainer.error = getErrorMessage(
                errors.hasDescriptionError,
                R.string.memo_text_empty_error
            )
            locationError.visibility = if (errors.hasLocationError) View.VISIBLE else View.GONE
            if (!errors.hasErrors) {
                updateSaveAction()
                model.savePreparedMemo()
            }
        }
    }

    private fun requestRequiredPermissions() {
        if (permissionChecker.hasForegroundLocationPermission()) {
            requestRemainingPermissions()
            return
        }
        val request = {
            foregroundPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.location_permission_title)
                .setMessage(R.string.location_permission_rationale)
                .setPositiveButton(R.string.continue_action) { _, _ -> request() }
                .setNegativeButton(R.string.save_without_reminder) { _, _ -> completePermissionFlow() }
                .setOnCancelListener { completePermissionFlow() }
                .show()
        } else {
            request()
        }
    }

    private fun requestRemainingPermissions() {
        when {
            !permissionChecker.hasBackgroundLocationPermission() ->
                showBackgroundLocationRationale()

            !permissionChecker.hasNotificationPermission() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            else -> completePermissionFlow()
        }
    }

    private fun showBackgroundLocationRationale() {
        AlertDialog.Builder(this)
            .setTitle(R.string.background_location_permission_title)
            .setMessage(R.string.background_location_permission_rationale)
            .setPositiveButton(R.string.open_settings) { _, _ -> requestBackgroundLocation() }
            .setNegativeButton(R.string.save_without_reminder) { _, _ -> completePermissionFlow() }
            .setOnCancelListener { completePermissionFlow() }
            .show()
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            settingsLauncher.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:$packageName".toUri()
                )
            )
        }
    }

    private fun completePermissionFlow() {
        isPermissionFlowActive = false
        updateSaveAction()
        model.activateSavedMemo()
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.uiState.collect { state ->
                    updateSaveAction()
                    state.selectedLocation?.let { location ->
                        binding.contentCreateMemo.selectedLocation.text = getString(
                            R.string.selected_location,
                            location.latitude,
                            location.longitude
                        )
                        binding.contentCreateMemo.locationError.visibility = View.GONE
                    }
                    if (
                        state.saveStage == MemoSaveStage.AWAITING_PERMISSIONS &&
                        !isPermissionFlowActive
                    ) {
                        isPermissionFlowActive = true
                        updateSaveAction()
                        requestRequiredPermissions()
                    } else if (state.saveStage == MemoSaveStage.COMPLETED) {
                        when (state.savedReminderStatus) {
                            ReminderStatus.PERMISSION_REQUIRED -> showToast(R.string.memo_saved_permission_required)
                            ReminderStatus.ERROR -> showToast(R.string.memo_saved_reminder_error)
                            else -> Unit
                        }
                        setResult(RESULT_OK)
                        finish()
                    } else if (state.saveError == true) {
                        showToast(R.string.memo_save_error)
                        model.onSaveErrorShown()
                    }
                }
            }
        }
    }

    private fun isSaveEnabled(): Boolean =
        !isPermissionFlowActive && model.uiState.value.saveStage == MemoSaveStage.EDITING

    private fun updateSaveAction() {
        saveMenuItem?.isEnabled = isSaveEnabled()
    }

    private fun showToast(@StringRes message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Returns the error message if there is an error, or an empty string otherwise.
     *
     * @param hasError          - whether there is an error.
     * @param errorMessageResId - the resource id of the error message to show.
     * @return the error message if there is an error, or an empty string otherwise.
     */
    private fun getErrorMessage(hasError: Boolean, @StringRes errorMessageResId: Int): String? {
        return if (hasError) {
            getString(errorMessageResId)
        } else {
            null
        }
    }

    override fun onStart() {
        super.onStart()
        locationPicker.onStart()
    }

    override fun onResume() {
        super.onResume()
        locationPicker.onResume()
    }

    override fun onPause() {
        locationPicker.onPause()
        super.onPause()
    }

    override fun onStop() {
        locationPicker.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(PERMISSION_FLOW_ACTIVE_KEY, isPermissionFlowActive)
        locationPicker.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        locationPicker.onLowMemory()
    }

    override fun onDestroy() {
        locationPicker.onDestroy()
        super.onDestroy()
    }
}

private const val PERMISSION_FLOW_ACTIVE_KEY = "permissionFlowActive"
