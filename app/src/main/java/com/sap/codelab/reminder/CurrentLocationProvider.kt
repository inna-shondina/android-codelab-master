package com.sap.codelab.reminder

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.sap.codelab.location.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val CURRENT_LOCATION_TIMEOUT_MILLIS = 5_000L

internal fun interface CurrentLocationProvider {
    suspend fun currentLocation(): GeoPoint?
}

internal class AndroidCurrentLocationProvider(
    context: Context,
    private val permissionChecker: ReminderPermissionChecker
) : CurrentLocationProvider {

    private val context = context.applicationContext
    private val locationManager = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GeoPoint? {
        if (!permissionChecker.hasForegroundLocationPermission()) return null
        return withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MILLIS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                awaitCurrentLocation()
            } else {
                awaitSingleLocationUpdate()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private suspend fun awaitCurrentLocation(): GeoPoint? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            runCatching {
                locationManager.getCurrentLocation(
                    LocationManager.GPS_PROVIDER,
                    cancellationSignal,
                    ContextCompat.getMainExecutor(context)
                ) { location ->
                    if (continuation.isActive) continuation.resume(location?.toGeoPoint())
                }
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun awaitSingleLocationUpdate(): GeoPoint? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location.toGeoPoint())
                }

                override fun onProviderDisabled(provider: String) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(null)
                }

                @Deprecated("Deprecated by Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
            runCatching {
                locationManager.requestSingleUpdate(
                    LocationManager.GPS_PROVIDER,
                    listener,
                    Looper.getMainLooper()
                )
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun Location.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
}
