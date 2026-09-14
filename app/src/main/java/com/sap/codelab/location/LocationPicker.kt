package com.sap.codelab.location

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup

/** UI boundary that keeps the map SDK replaceable. */
internal interface LocationPicker {

    fun attach(
        host: ViewGroup,
        savedInstanceState: Bundle?,
        initialLocation: GeoPoint?,
        onLocationSelected: ((GeoPoint) -> Unit)?
    )

    fun showLocation(location: GeoPoint)
    fun onStart()
    fun onResume()
    fun onPause()
    fun onStop()
    fun onSaveInstanceState(outState: Bundle)
    fun onLowMemory()
    fun onDestroy()
}

internal fun interface LocationPickerFactory {
    fun create(context: Context): LocationPicker
}
