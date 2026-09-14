package com.sap.codelab.location

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

private const val STYLE_ASSET = "osm_style.json"
private const val DEFAULT_ZOOM = 1.5
private const val LOCATION_ZOOM = 15.0
private const val MARKER_SOURCE_ID = "selected-location-source"
private const val MARKER_LAYER_ID = "selected-location-layer"

/** MapLibre adapter backed by OpenStreetMap raster tiles. */
internal class MapLibreLocationPicker(
    context: Context
) : LocationPicker {

    private val context = context.applicationContext
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var markerSource: GeoJsonSource? = null
    private var styleReady = false
    private var location: GeoPoint? = null
    private var hasSavedMapState = false

    override fun attach(
        host: ViewGroup,
        savedInstanceState: Bundle?,
        initialLocation: GeoPoint?,
        onLocationSelected: ((GeoPoint) -> Unit)?
    ) {
        check(!::mapView.isInitialized) { "LocationPicker is already attached" }
        MapLibre.getInstance(context)
        location = initialLocation
        hasSavedMapState = savedInstanceState != null
        mapView = MapView(host.context).also { view ->
            view.contentDescription = host.contentDescription
            host.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            view.onCreate(savedInstanceState)
            view.getMapAsync { readyMap -> configureMap(readyMap, onLocationSelected) }
        }
    }

    override fun showLocation(location: GeoPoint) {
        this.location = location
        map?.takeIf { styleReady }?.let { readyMap ->
            renderLocation(readyMap, location, moveCamera = true)
        }
    }

    override fun onStart() = withMapView(MapView::onStart)

    override fun onResume() = withMapView(MapView::onResume)

    override fun onPause() = withMapView(MapView::onPause)

    override fun onStop() = withMapView(MapView::onStop)

    override fun onSaveInstanceState(outState: Bundle) =
        withMapView { it.onSaveInstanceState(outState) }

    override fun onLowMemory() = withMapView(MapView::onLowMemory)

    override fun onDestroy() = withMapView(MapView::onDestroy)

    private fun configureMap(
        readyMap: MapLibreMap,
        onLocationSelected: ((GeoPoint) -> Unit)?
    ) {
        map = readyMap
        disableTilePrefetch(readyMap)
        readyMap.setStyle(Style.Builder().fromJson(readStyle())) { style ->
            markerSource = GeoJsonSource(MARKER_SOURCE_ID).also(style::addSource)
            style.addLayer(
                CircleLayer(MARKER_LAYER_ID, MARKER_SOURCE_ID).withProperties(
                    circleRadius(9f),
                    circleColor("#FF7043"),
                    circleStrokeColor("#FFFFFF"),
                    circleStrokeWidth(3f)
                )
            )
            styleReady = true
            val selectedLocation = location
            if (selectedLocation == null && !hasSavedMapState) {
                readyMap.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(20.0, 0.0))
                    .zoom(DEFAULT_ZOOM)
                    .build()
            } else if (selectedLocation != null) {
                renderLocation(readyMap, selectedLocation, moveCamera = !hasSavedMapState)
            }

            if (onLocationSelected != null) {
                readyMap.addOnMapClickListener { point ->
                    val selected = GeoPoint(point.latitude, point.longitude)
                    location = selected
                    renderLocation(readyMap, selected, moveCamera = false)
                    onLocationSelected(selected)
                    true
                }
            }
        }
    }

    private fun renderLocation(map: MapLibreMap, location: GeoPoint, moveCamera: Boolean) {
        val position = LatLng(location.latitude, location.longitude)
        markerSource?.setGeoJson(Point.fromLngLat(location.longitude, location.latitude))
        if (moveCamera) {
            map.cameraPosition = CameraPosition.Builder()
                .target(position)
                .zoom(LOCATION_ZOOM)
                .build()
        }
    }

    @Suppress("DEPRECATION")
    private fun disableTilePrefetch(map: MapLibreMap) {
        // The public OSM tile service explicitly prohibits prefetching/bulk downloading.
        map.setPrefetchesTiles(false)
    }

    private fun readStyle(): String =
        context.assets.open(STYLE_ASSET).bufferedReader().use { it.readText() }

    private fun withMapView(action: (MapView) -> Unit) {
        if (::mapView.isInitialized) action(mapView)
    }
}
