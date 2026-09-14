package com.sap.codelab.view.create

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sap.codelab.R
import com.sap.codelab.databinding.ActivityCreateMemoBinding
import com.sap.codelab.location.LocationPicker
import com.sap.codelab.repository.App
import kotlinx.coroutines.launch

/**
 * Activity that allows a user to create a new Memo.
 */
internal class CreateMemo : AppCompatActivity() {

    private lateinit var binding: ActivityCreateMemoBinding
    private lateinit var model: CreateMemoViewModel
    private lateinit var locationPicker: LocationPicker
    private var saveMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateMemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        val container = (application as App).container
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
            isEnabled = !model.uiState.value.isSaving
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
            val errors = model.saveMemo(
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
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.uiState.collect { state ->
                    saveMenuItem?.isEnabled = !state.isSaving
                    state.selectedLocation?.let { location ->
                        binding.contentCreateMemo.selectedLocation.text = getString(
                            R.string.selected_location,
                            location.latitude,
                            location.longitude
                        )
                        binding.contentCreateMemo.locationError.visibility = View.GONE
                    }
                    if (state.savedMemoId != null) {
                        setResult(RESULT_OK)
                        finish()
                    } else if (state.saveError == true) {
                        Toast.makeText(this@CreateMemo, R.string.memo_save_error, Toast.LENGTH_LONG).show()
                        model.onSaveErrorShown()
                    }
                }
            }
        }
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
