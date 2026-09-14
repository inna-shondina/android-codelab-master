package com.sap.codelab.view.detail

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sap.codelab.databinding.ActivityViewMemoBinding
import com.sap.codelab.location.GeoPoint
import com.sap.codelab.location.LocationPicker
import com.sap.codelab.model.Memo
import com.sap.codelab.model.ReminderStatus
import com.sap.codelab.repository.App
import kotlinx.coroutines.launch

internal const val BUNDLE_MEMO_ID: String = "memoId"

/**
 * Activity that allows a user to see the details of a memo.
 */
internal class ViewMemo : AppCompatActivity() {

    private lateinit var binding: ActivityViewMemoBinding
    private lateinit var locationPicker: LocationPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewMemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        val container = (application as App).container
        val model = ViewModelProvider(this, container.viewModelFactory)[ViewMemoViewModel::class.java]
        locationPicker = container.locationPickerFactory.create(this)
        locationPicker.attach(
            host = binding.contentCreateMemo.mapHost,
            savedInstanceState = savedInstanceState,
            initialLocation = model.memo.value?.toGeoPoint(),
            onLocationSelected = null
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.memo.collect { value ->
                    value?.let(::updateUI)
                }
            }
        }
        model.loadMemo(intent.getLongExtra(BUNDLE_MEMO_ID, -1))
    }

    /**
     * Updates the UI with the given memo details.
     *
     * @param memo - the memo whose details are to be displayed.
     */
    private fun updateUI(memo: Memo) {
        binding.contentCreateMemo.run {
            memoTitle.setText(memo.title)
            memoDescription.setText(memo.description)
            memoTitle.isEnabled = false
            memoDescription.isEnabled = false
            mapInstructions.visibility = android.view.View.GONE
            locationError.visibility = android.view.View.GONE
            if (memo.reminderStatus == ReminderStatus.INACTIVE) {
                mapHost.visibility = android.view.View.GONE
                selectedLocation.setText(com.sap.codelab.R.string.no_location_reminder)
            } else {
                selectedLocation.text = getString(
                    com.sap.codelab.R.string.selected_location,
                    memo.reminderLatitude,
                    memo.reminderLongitude
                )
            }
        }
        if (memo.reminderStatus != ReminderStatus.INACTIVE) {
            locationPicker.showLocation(memo.toGeoPoint())
        }
    }

    private fun Memo.toGeoPoint() = GeoPoint(reminderLatitude, reminderLongitude)

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
