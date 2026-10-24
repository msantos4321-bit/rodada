package com.mauricio.rodada.data

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class Mode(val label: String, val hint: String) {
    RAPIDA("Rápida", "La más corta en tiempo"),
    CURVAS("Curvas", "Busca caminos con curvas sin alargar mucho"),
    SUPER("Súper curvas", "Las más curvas, aunque tarde más"),
    SIN_AUTOPISTA("Sin autopista", "Evita autopistas"),
    SIN_CASETAS("Sin casetas", "Evita cuotas"),
    TERRACERIA("Terracería", "Prefiere caminos de tierra"),
}

data class Maneuver(
    val type: Int,
    val instruction: String,
    val verbal: String,
    val shapeIndex: Int,
    val lengthM: Double,
    val street: String,
)

data class Route(
    val points: List<P>,
    val maneuvers: List<Maneuver>,
    val lengthM: Double,
    val timeS: Double,
    val mode: Mode?,
    val name: String = "",
    /** Ruta importada (GPX): se sigue el trazo sin instrucciones de vuelta. */
    val isTrack: Boolean = false,
) {
    val curviness: Double by lazy { Geo.curviness(points) }
    val curves: Int by lazy { Geo.countCurves(points) }
    val cum: DoubleArray by lazy { Geo.cumulative(points) }
}

/**
 * Rutas con Valhalla (servidor libre de OpenStreetMap, perfil de moto).
 * Sin llave ni mensualidad. El servidor pide no abusar, por eso las búsquedas van una por una.
 */
object Routing {
    private const val URL = "https://valhalla1.openstreetmap.de/route"

    var lastError: String = ""; private set

    private fun costing(mode: Mode): JSONObject = JSONObject().apply {
        when (mode) {
            Mode.RAPIDA -> { put("use_highways", 1.0); put("use_tolls", 0.6); put("use_trails", 0.0) }
            Mode.CURVAS -> { put("use_highways", 0.15); put("use_tolls", 0.2); put("use_primary", 0.5); put("use_trails", 0.0) }
            Mode.SUPER -> { put("use_highways", 0.0); put("use_tolls", 0.0); put("use_primary", 0.2); put("use_trails", 0.0) }
            Mode.SIN_AUTOPISTA -> { put("use_highways", 0.0); put("use_tolls", 0.4); put("use_trails", 0.0) }
            Mode.SIN_CASETAS -> { put("use_highways", 0.5); put("use_tolls", 0.0); put("use_trails", 0.0) }
            Mode.TERRACERIA -> { put("use_highways", 0.0); put("use_tolls", 0.0); put("use_primary", 0.1); put("use_trails", 1.0) }
        }
    }

    /** Una consulta. [stops] = salida, paradas y destino; [through] = puntos de paso sin parada. */
    private suspend fun query(stops: List<P>, mode: Mode, alternates: Int = 0, throughIdx: Set<Int> = emptySet()): List<Route> {
        val locs = JSONArray()
        stops.forEachIndexed { i, p ->
            val o = JSONObject().put("lat", p.lat).put("lon", p.lon)
            if (i in throughIdx) o.put("type", "through")
            locs.put(o)
        }
        val req = JSONObject()
            .put("locations", locs)
            .put("costing", "motorcycle")
            .put("costing_options", JSONObject().put("motorcycle", costing(mode)))
            .put("language", "es-ES")
            .put("units", "kilometers")
            .put("directions_options", JSONObject().put("language", "es-ES").put("units", "kilometers"))
        if (alternates > 0 && stops.size == 2) req.put("alternates", alternates)
        val txt = Net.get("$URL?json=" + URLEncoder.encode(req.toString(), "UTF-8"))
        if (txt == null) { lastError = "No se pudo calcular la ruta. Revisa tu internet."; return emptyList() }
        return runCatching {
            val o = JSONObject(txt)
            if (o.has("error")) { lastError = "No hay camino entre esos puntos (${o.optString("error")})."; return emptyList() }
            val out = ArrayList<Route>()
            parseTrip(o.optJSONObject("trip"), mode)?.let { out.add(it) }
            o.optJSONArray("alternates")?.let { a ->
                for (i in 0 until a.length()) parseTrip(a.optJSONObject(i)?.optJSONObject("trip"), mode)?.let { out.add(it) }
            }
            out
        }.getOrElse { lastError = "Respuesta rara del servidor de rutas."; emptyList() }
    }

    private fun parseTrip(trip: JSONObject?, mode: Mode): Route? {
        trip ?: return null
        val legs = trip.optJSONArray("legs") ?: return null
        val pts = ArrayList<P>()
        val mans = ArrayList<Maneuver>()
        for (l in 0 until legs.length()) {
            val leg = legs.getJSONObject(l)
            val offset = pts.size
            pts.addAll(Geo.decode(leg.optString("shape")))
            val ms = leg.optJSONArray("maneuvers") ?: continue
            for (k in 0 until ms.length()) {
                val m = ms.getJSONObject(k)
                val type = m.optInt("type")
                // En rutas con paradas, el "llegaste" intermedio y la "salida" siguiente no se anuncian dos veces
                if (l > 0 && type in 1..3) continue
                val streets = m.optJSONArray("street_names")
                mans.add(Maneuver(
                    type = type,
                    instruction = m.optString("instruction"),
                    verbal = m.optString("verbal_pre_transition_instruction").ifBlank { m.optString("instruction") },
                    shapeIndex = offset + m.optInt("begin_shape_index"),
                    lengthM = m.optDouble("length", 0.0) * 1000,
                    street = if (streets != null && streets.length() > 0) streets.optString(0) else "",
                ))
            }
        }
        val s = trip.optJSONObject("summary")
        return Route(pts, mans, (s?.optDouble("length", 0.0) ?: 0.0) * 1000, s?.optDouble("time", 0.0) ?: 0.0, mode)
    }

    /**
     * Ruta de A a B (con paradas opcionales) según el tipo.
     * Para "Curvas" y "Súper curvas" prueba varias opciones y se queda con la más curva que no tarde demasiado.
     */
    suspend fun route(stops: List<P>, mode: Mode): Route? {
        lastError = ""
        if (stops.size < 2) return null
        if (mode != Mode.CURVAS && mode != Mode.SUPER) return query(stops, mode).firstOrNull()

        val candidates = ArrayList<Route>()
        candidates += query(stops, mode, alternates = if (stops.size == 2) 2 else 0)
        // Puntos de paso a los lados de la línea recta, para descubrir caminos que el servidor no propone
        if (stops.size == 2) {
            val a = stops[0]; val b = stops[1]
            val d = Geo.dist(a, b)
            if (d > 8_000) {
                val brg = Geo.bearing(a, b)
                val mid = Geo.dest(a, d / 2, brg)
                val off = min(max(d * 0.22, 4_000.0), 35_000.0)
                for (side in listOf(-90.0, 90.0)) {
                    delay(1100)
                    val via = Geo.dest(mid, off, brg + side)
                    candidates += query(listOf(a, via, b), mode, throughIdx = setOf(1))
                }
            }
        }
        if (candidates.isEmpty()) return null
        val fastest = candidates.minOf { it.timeS }
        val limit = if (mode == Mode.CURVAS) 1.35 else 1.9
        return candidates.filter { it.timeS <= fastest * limit }.maxByOrNull { it.curviness } ?: candidates.first()
    }

    /** Paseo circular: sale y regresa al mismo punto en más o menos [minutes] minutos. */
    suspend fun loop(start: P, minutes: Int, mode: Mode, headingHint: Double? = null): Route? {
        lastError = ""
        val avgKmh = when (mode) { Mode.RAPIDA -> 70.0; Mode.TERRACERIA -> 30.0; else -> 50.0 }
        val targetM = minutes / 60.0 * avgKmh * 1000
        // Triángulo: dos puntos a distancia d; el camino real suele ser ~1.3 veces la línea recta
        val d = targetM / 3.0 / 1.3
        val tries = ArrayList<Route>()
        val base = headingHint ?: Random.nextDouble(0.0, 360.0)
        for (k in 0 until 3) {
            val h = base + k * 120
            val p1 = Geo.dest(start, d, h - 30)
            val p2 = Geo.dest(start, d, h + 30)
            query(listOf(start, p1, p2, start), mode, throughIdx = setOf(1, 2)).firstOrNull()?.let { tries += it }
            delay(1100)
        }
        if (tries.isEmpty()) { if (lastError.isBlank()) lastError = "No encontré un paseo desde aquí. Prueba otro tipo o menos tiempo."; return null }
        val ok = tries.filter { it.lengthM in targetM * 0.6..targetM * 1.5 }.ifEmpty { tries }
        return ok.maxByOrNull { it.curviness - kotlin.math.abs(it.lengthM - targetM) / targetM * 20 }
    }

    /** Ruta desde donde estoy que pasa por los puntos de una ruta del grupo y regresa (si [back]). */
    suspend fun through(start: P, waypoints: List<P>, mode: Mode, back: Boolean): Route? {
        val stops = ArrayList<P>()
        stops += start
        stops += waypoints
        if (back) stops += start
        val through = (1 until stops.size - 1).toSet()
        lastError = ""
        return query(stops, mode, throughIdx = through).firstOrNull()
    }

    /** Buscador de lugares (Photon, de OpenStreetMap), con preferencia cerca de donde estás. */
    suspend fun search(text: String, near: P?): List<Place> {
        val q = URLEncoder.encode(text, "UTF-8")
        val bias = near?.let { "&lat=${it.lat}&lon=${it.lon}" }.orEmpty()
        val txt = Net.get("https://photon.komoot.io/api/?q=$q&limit=8$bias") ?: return emptyList()
        return runCatching {
            val f = JSONObject(txt).getJSONArray("features")
            (0 until f.length()).mapNotNull { i ->
                val o = f.getJSONObject(i)
                val c = o.getJSONObject("geometry").getJSONArray("coordinates")
                val pr = o.getJSONObject("properties")
                val name = pr.optString("name").ifBlank { pr.optString("street") }
                if (name.isBlank()) return@mapNotNull null
                val parts = listOf(pr.optString("street"), pr.optString("district"), pr.optString("city"), pr.optString("state"))
                    .filter { it.isNotBlank() && it != name }.distinct()
                Place(name, parts.joinToString(", "), P(c.getDouble(1), c.getDouble(0)))
            }
        }.getOrDefault(emptyList())
    }
}

data class Place(val name: String, val detail: String, val p: P)
