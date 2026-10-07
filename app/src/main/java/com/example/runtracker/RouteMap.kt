package com.example.runtracker

import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.graphics.Color as AColor

/**
 * 달린 경로를 OpenStreetMap 위에 그려 주는 지도.
 * 지도 타일은 인터넷이 필요하지만, 연결이 없어도 경로선과 출발/도착 표시는 그려진다.
 */
@Composable
fun RouteMap(route: List<List<RoutePoint>>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setMinZoomLevel(3.0)
            controller.setZoom(16.0)
        }
    }
    DisposableEffect(mapView) { onDispose { mapView.onDetach() } }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map -> drawRoute(map, route) },
    )
}

private fun drawRoute(map: MapView, route: List<List<RoutePoint>>) {
    map.overlays.clear()

    val segments = route.filter { it.isNotEmpty() }
    val all = segments.flatten().map { GeoPoint(it.lat, it.lon) }
    if (all.isEmpty()) return

    val density = map.resources.displayMetrics.density

    segments.forEach { seg ->
        if (seg.size >= 2) {
            val line = Polyline()
            line.setPoints(seg.map { GeoPoint(it.lat, it.lon) })
            line.outlinePaint.color = AColor.parseColor("#FF6B2C")
            line.outlinePaint.strokeWidth = 6f * density
            line.outlinePaint.strokeCap = Paint.Cap.ROUND
            map.overlays.add(line)
        }
    }
    map.overlays.add(dot(map, all.first(), "#2ECC71", "출발"))
    map.overlays.add(dot(map, all.last(), "#FF4D4F", "도착"))
    map.invalidate()

    // 화면 크기가 정해진 뒤 경로 전체가 보이도록 확대/이동
    if (map.isLayoutOccurred) {
        fitToRoute(map, all)
    } else {
        map.addOnFirstLayoutListener { _, _, _, _, _ -> fitToRoute(map, all) }
    }
}

private fun fitToRoute(map: MapView, points: List<GeoPoint>) {
    val box = BoundingBox.fromGeoPoints(points)
    if (box.latitudeSpan < 0.0002 && box.longitudeSpan < 0.0002) {
        // 거의 한 지점: 그 위치를 중심으로 확대
        map.controller.setZoom(18.0)
        map.controller.setCenter(GeoPoint(box.centerLatitude, box.centerLongitude))
    } else {
        val border = (24 * map.resources.displayMetrics.density).toInt()
        map.zoomToBoundingBox(box.increaseByScale(1.4f), false, border)
    }
}

/** 출발(초록) / 도착(빨강) 동그라미 표시 */
private fun dot(map: MapView, position: GeoPoint, colorHex: String, label: String): Marker {
    val density = map.resources.displayMetrics.density
    val size = (18 * density).toInt()
    val drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(AColor.parseColor(colorHex))
        setStroke((3 * density).toInt(), AColor.WHITE)
        setSize(size, size)
    }
    return Marker(map).apply {
        this.position = position
        icon = drawable
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setTitle(label)
    }
}
