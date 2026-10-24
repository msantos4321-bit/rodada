package com.mauricio.rodada.ui

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mauricio.rodada.data.P
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Un punto en el mapa: amigos del grupo, peligros, gasolineras, lugares. */
data class Mark(val p: P, val color: String, val label: String = "", val big: Boolean = false)

/** Tramo coloreado (por ejemplo, la rodada pintada por inclinación). */
data class Seg(val pts: List<P>, val color: String)

private const val STYLE = "https://tiles.openfreemap.org/styles/liberty"

/**
 * Mapa libre (OpenStreetMap / OpenFreeMap, sin llave ni costo).
 * [follow] centra en [me] girando el mapa hacia donde vas; si no, ajusta para que quepa la ruta una vez.
 */
@Composable
fun RMap(
    modifier: Modifier = Modifier,
    route: List<P> = emptyList(),
    alt: List<P> = emptyList(),
    segs: List<Seg> = emptyList(),
    marks: List<Mark> = emptyList(),
    me: P? = null,
    heading: Double = 0.0,
    follow: Boolean = false,
    fitKey: Any? = route.size,
    onLongPress: ((P) -> Unit)? = null,
    onTap: ((P) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val longRef = rememberUpdatedState(onLongPress)
    val tapRef = rememberUpdatedState(onTap)
    val view = remember {
        MapView(ctx).apply {
            onCreate(Bundle())
            getMapAsync { m ->
                m.uiSettings.isAttributionEnabled = true
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.isCompassEnabled = true
                m.uiSettings.isRotateGesturesEnabled = true
                m.setStyle(Style.Builder().fromUri(STYLE)) { s ->
                    setup(s)
                    style = s
                }
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(32.5276, -117.1198), 10.0))
                m.addOnMapLongClickListener { ll -> val f = longRef.value; f?.invoke(P(ll.latitude, ll.longitude)); f != null }
                m.addOnMapClickListener { ll -> val f = tapRef.value; f?.invoke(P(ll.latitude, ll.longitude)); f != null }
                map = m
            }
        }
    }

    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        onDispose {
            owner.lifecycle.removeObserver(obs)
            runCatching { view.onPause(); view.onStop(); view.onDestroy() }
        }
    }

    // Datos
    LaunchedEffect(style, route, alt, segs, marks, me) {
        val s = style ?: return@LaunchedEffect
        (s.getSource("route") as? GeoJsonSource)?.setGeoJson(line(route))
        (s.getSource("alt") as? GeoJsonSource)?.setGeoJson(line(alt))
        (s.getSource("segs") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(segs.filter { it.pts.size >= 2 }.map { sg ->
            Feature.fromGeometry(LineString.fromLngLats(sg.pts.map { Point.fromLngLat(it.lon, it.lat) })).also { it.addStringProperty("color", sg.color) }
        }))
        (s.getSource("marks") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(marks.map { mk ->
            Feature.fromGeometry(Point.fromLngLat(mk.p.lon, mk.p.lat)).also {
                it.addStringProperty("color", mk.color); it.addStringProperty("label", mk.label); it.addNumberProperty("r", if (mk.big) 11 else 8)
            }
        }))
        (s.getSource("me") as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(
            me?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))) } ?: emptyList()))
    }

    // Cámara: seguir o encuadrar
    LaunchedEffect(map, me, heading, follow) {
        val m = map ?: return@LaunchedEffect
        if (follow && me != null) {
            m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
                .target(LatLng(me.lat, me.lon)).zoom(16.0).bearing(heading).tilt(40.0).build()), 700)
        }
    }
    LaunchedEffect(map, fitKey) {
        val m = map ?: return@LaunchedEffect
        if (follow) return@LaunchedEffect
        val pts = (route + alt + segs.flatMap { it.pts } + marks.map { it.p }).ifEmpty { listOfNotNull(me) }
        if (pts.size == 1) m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pts[0].lat, pts[0].lon), 13.0))
        else if (pts.size > 1) {
            val b = LatLngBounds.Builder()
            pts.forEach { b.include(LatLng(it.lat, it.lon)) }
            runCatching { m.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 90)) }
        }
    }

    AndroidView(factory = { view }, modifier = modifier)
}

private fun line(pts: List<P>): FeatureCollection =
    if (pts.size < 2) FeatureCollection.fromFeatures(emptyList())
    else FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.lon, it.lat) })))

private fun setup(s: Style) {
    s.addSource(GeoJsonSource("alt"))
    s.addSource(GeoJsonSource("route"))
    s.addSource(GeoJsonSource("segs"))
    s.addSource(GeoJsonSource("marks"))
    s.addSource(GeoJsonSource("me"))
    s.addLayer(LineLayer("alt-l", "alt").withProperties(
        PropertyFactory.lineColor("#9AA0A6"), PropertyFactory.lineWidth(5f), PropertyFactory.lineOpacity(0.7f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
    s.addLayer(LineLayer("route-casing", "route").withProperties(
        PropertyFactory.lineColor("#FFFFFF"), PropertyFactory.lineWidth(10f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
    s.addLayer(LineLayer("route-l", "route").withProperties(
        PropertyFactory.lineColor("#D9480F"), PropertyFactory.lineWidth(6f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
    s.addLayer(LineLayer("segs-l", "segs").withProperties(
        PropertyFactory.lineColor(Expression.get("color")), PropertyFactory.lineWidth(6f),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)))
    s.addLayer(CircleLayer("marks-c", "marks").withProperties(
        PropertyFactory.circleColor(Expression.get("color")), PropertyFactory.circleRadius(Expression.get("r")),
        PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3f)))
    s.addLayer(SymbolLayer("marks-t", "marks").withProperties(
        PropertyFactory.textField(Expression.get("label")), PropertyFactory.textSize(13f),
        PropertyFactory.textColor("#1D1D1F"), PropertyFactory.textHaloColor("#FFFFFF"), PropertyFactory.textHaloWidth(2f),
        PropertyFactory.textOffset(arrayOf(0f, 1.5f)), PropertyFactory.textAllowOverlap(false),
        PropertyFactory.textFont(arrayOf("Noto Sans Regular"))))
    s.addLayer(CircleLayer("me-halo", "me").withProperties(
        PropertyFactory.circleColor("#1E6FD9"), PropertyFactory.circleOpacity(0.18f), PropertyFactory.circleRadius(22f)))
    s.addLayer(CircleLayer("me-c", "me").withProperties(
        PropertyFactory.circleColor("#1E6FD9"), PropertyFactory.circleRadius(9f),
        PropertyFactory.circleStrokeColor("#FFFFFF"), PropertyFactory.circleStrokeWidth(3f)))
}
