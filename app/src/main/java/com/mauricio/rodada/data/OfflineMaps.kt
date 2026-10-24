package com.mauricio.rodada.data

import android.content.Context
import org.maplibre.android.offline.OfflineGeometryRegionDefinition
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Descarga el mapa a lo largo de una ruta para verlo sin señal (sierra, desierto, terracería). */
object OfflineMaps {
    const val STYLE = "https://tiles.openfreemap.org/styles/liberty"

    fun download(ctx: Context, route: Route, name: String, progress: (Int) -> Unit, done: (Boolean) -> Unit) {
        val pts = Geo.simplify(route.points, (route.lengthM / 400).coerceAtLeast(60.0))
        val line = LineString.fromLngLats(pts.map { Point.fromLngLat(it.lon, it.lat) })
        val def = OfflineGeometryRegionDefinition(STYLE, line, 6.0, 15.0, ctx.resources.displayMetrics.density)
        OfflineManager.getInstance(ctx).createOfflineRegion(def, name.toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        val req = status.requiredResourceCount.coerceAtLeast(1)
                        progress((status.completedResourceCount * 100 / req).toInt().coerceIn(0, 100))
                        if (status.isComplete) { offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE); done(true) }
                    }
                    override fun onError(error: OfflineRegionError) {}
                    override fun mapboxTileCountLimitExceeded(limit: Long) { offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE); done(false) }
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }
            override fun onError(error: String) { done(false) }
        })
    }
}
