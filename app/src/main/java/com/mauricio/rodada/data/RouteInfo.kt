package com.mauricio.rodada.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Weather(
    val rainPct: Int,
    val minTemp: Double,
    val maxTemp: Double,
    val maxGust: Double,
    val crossWind: Boolean,
    val sunsetBeforeArrival: Boolean,
    val sunset: String,
) {
    fun summary(): String {
        val parts = ArrayList<String>()
        parts += when {
            rainPct >= 60 -> "lluvia probable ($rainPct%)"
            rainPct >= 30 -> "posible lluvia ($rainPct%)"
            else -> "sin lluvia"
        }
        parts += "${minTemp.toInt()}–${maxTemp.toInt()} °C"
        if (crossWind) parts += "viento de lado fuerte (${maxGust.toInt()} km/h)"
        else if (maxGust >= 45) parts += "ráfagas de ${maxGust.toInt()} km/h"
        if (sunsetBeforeArrival) parts += "anochece a las $sunset, antes de llegar"
        return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
    }
    val warn: Boolean get() = rainPct >= 40 || crossWind || maxGust >= 55 || minTemp <= 5 || sunsetBeforeArrival
}

data class Fuel(val stations: List<Station>, val firstKm: Double?, val maxGapKm: Double, val gapStartKm: Double)
data class Station(val name: String, val p: P, val alongKm: Double)

object RouteInfo {

    /** Puntos de la ruta cada cierta distancia con la hora estimada en que vas a pasar. */
    private fun samples(r: Route, n: Int): List<Pair<P, Double>> {
        if (r.points.isEmpty()) return emptyList()
        val total = r.cum.last().coerceAtLeast(1.0)
        val out = ArrayList<Pair<P, Double>>()
        var j = 0
        for (k in 0 until n) {
            val target = total * k / max(1, n - 1)
            while (j < r.points.size - 1 && r.cum[j] < target) j++
            out += r.points[j] to (r.timeS * (r.cum[j] / total))
        }
        return out
    }

    /** Clima a la hora en que vas a pasar por cada tramo (Open-Meteo, gratis y sin llave). */
    suspend fun weather(r: Route, departure: LocalDateTime = LocalDateTime.now()): Weather? {
        val n = min(8, max(2, (r.lengthM / 25_000).toInt() + 1))
        val sm = samples(r, n)
        if (sm.isEmpty()) return null
        val lats = sm.joinToString(",") { String.format(java.util.Locale.US, "%.4f", it.first.lat) }
        val lons = sm.joinToString(",") { String.format(java.util.Locale.US, "%.4f", it.first.lon) }
        val txt = Net.get("https://api.open-meteo.com/v1/forecast?latitude=$lats&longitude=$lons" +
            "&hourly=temperature_2m,precipitation_probability,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&daily=sunset&timezone=auto&forecast_days=3") ?: return null
        return runCatching {
            val arr: List<JSONObject> = if (txt.trimStart().startsWith("[")) JSONArray(txt).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } else listOf(JSONObject(txt))
            var rain = 0; var tMin = 99.0; var tMax = -99.0; var gust = 0.0; var cross = false
            var sunset = ""; var sunsetBefore = false
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            arr.forEachIndexed { i, o ->
                val (p, offset) = sm[min(i, sm.size - 1)]
                val eta = departure.plusSeconds(offset.toLong())
                val h = o.getJSONObject("hourly")
                val times = h.getJSONArray("time")
                var best = 0; var bestDiff = Long.MAX_VALUE
                for (k in 0 until times.length()) {
                    val t = LocalDateTime.parse(times.getString(k), fmt)
                    val diff = abs(java.time.Duration.between(t, eta).toMinutes())
                    if (diff < bestDiff) { bestDiff = diff; best = k }
                }
                rain = max(rain, h.getJSONArray("precipitation_probability").optInt(best))
                val temp = h.getJSONArray("temperature_2m").optDouble(best)
                tMin = min(tMin, temp); tMax = max(tMax, temp)
                val g = h.getJSONArray("wind_gusts_10m").optDouble(best)
                val w = h.getJSONArray("wind_speed_10m").optDouble(best)
                val wd = h.getJSONArray("wind_direction_10m").optDouble(best)
                gust = max(gust, g)
                // Viento de lado: dirección del viento casi perpendicular al camino y fuerte
                val idx = r.points.indexOf(p).coerceAtLeast(0)
                val next = r.points.getOrNull(min(r.points.size - 1, idx + 20)) ?: p
                val roadBrg = Geo.bearing(p, next)
                val ang = abs(Geo.turn(roadBrg, wd))
                if (w >= 30 && ang in 55.0..125.0) cross = true
                if (i == arr.size - 1) {
                    val days = o.getJSONObject("daily")
                    val ss = days.getJSONArray("sunset")
                    for (k in 0 until ss.length()) {
                        val s = LocalDateTime.parse(ss.getString(k), fmt)
                        if (s.toLocalDate() == eta.toLocalDate()) {
                            sunset = s.format(DateTimeFormatter.ofPattern("HH:mm"))
                            sunsetBefore = eta.isAfter(s) && departure.isBefore(s.plusHours(1))
                        }
                    }
                }
            }
            Weather(rain, tMin, tMax, gust, cross, sunsetBefore, sunset)
        }.getOrNull()
    }

    /** Gasolineras a menos de 1 km de la ruta (OpenStreetMap) y el tramo más largo sin ninguna. */
    suspend fun fuel(r: Route): Fuel? {
        if (r.points.size < 2) return null
        val minLat = r.points.minOf { it.lat } - 0.02; val maxLat = r.points.maxOf { it.lat } + 0.02
        val minLon = r.points.minOf { it.lon } - 0.02; val maxLon = r.points.maxOf { it.lon } + 0.02
        if ((maxLat - minLat) * (maxLon - minLon) > 25) return null // demasiado grande para una consulta
        val q = "[out:json][timeout:25];node[\"amenity\"=\"fuel\"]($minLat,$minLon,$maxLat,$maxLon);out 400;"
        val txt = Net.postForm("https://overpass-api.de/api/interpreter", "data", q) ?: return null
        return runCatching {
            val els = JSONObject(txt).getJSONArray("elements")
            val list = ArrayList<Station>()
            for (i in 0 until els.length()) {
                val e = els.getJSONObject(i)
                val p = P(e.getDouble("lat"), e.getDouble("lon"))
                if (Geo.distToLine(p, r.points) > 1000) continue
                val snap = Geo.snap(p, r.points, r.cum, 0, 0) ?: continue
                val tags = e.optJSONObject("tags")
                val name = tags?.optString("brand")?.ifBlank { null } ?: tags?.optString("name")?.ifBlank { null } ?: "Gasolinera"
                list += Station(name, p, snap.along / 1000)
            }
            list.sortBy { it.alongKm }
            val totalKm = r.lengthM / 1000
            var gap = 0.0; var gapStart = 0.0; var prev = 0.0
            for (s in list) {
                if (s.alongKm - prev > gap) { gap = s.alongKm - prev; gapStart = prev }
                prev = s.alongKm
            }
            if (totalKm - prev > gap) { gap = totalKm - prev; gapStart = prev }
            Fuel(list, list.firstOrNull()?.alongKm, gap, gapStart)
        }.getOrNull()
    }

    fun fuelText(f: Fuel?, bike: Bike?, routeKm: Double): Pair<String, Boolean>? {
        f ?: return null
        val range = bike?.rangeKm ?: 0
        if (f.stations.isEmpty()) {
            val warn = range in 1 until routeKm.toInt()
            return (if (warn) "No hay gasolineras en la ruta y tu alcance es de ~$range km: carga antes de salir"
            else "No hay gasolineras en la ruta: sal con tanque lleno") to true
        }
        val first = f.firstKm ?: 0.0
        return if (range > 0 && f.maxGapKm > range * 0.8)
            "Ojo: hay ${f.maxGapKm.toInt()} km sin gasolinera desde el km ${f.gapStartKm.toInt()} y tu alcance es de ~$range km" to true
        else "${f.stations.size} gasolineras en la ruta · la primera en ${first.toInt()} km · tramo más largo sin gasolina: ${f.maxGapKm.toInt()} km" to false
    }

    @Suppress("unused")
    private val zone: ZoneId = ZoneId.systemDefault()
}
