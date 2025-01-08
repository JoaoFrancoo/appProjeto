package com.example.appprojeto.classes

import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class MapClickOverlay(
    private val onMapClick: (GeoPoint) -> Unit
) : Overlay() {
    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val iGeoPoint = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
        val geoPoint = GeoPoint(iGeoPoint.latitude, iGeoPoint.longitude)
        onMapClick(geoPoint)
        return true
    }
}
