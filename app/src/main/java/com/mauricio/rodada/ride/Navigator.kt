package com.mauricio.rodada.ride

import com.mauricio.rodada.data.Geo
import com.mauricio.rodada.data.Hazard
import com.mauricio.rodada.data.Maneuver
import com.mauricio.rodada.data.P
import com.mauricio.rodada.data.Route
import kotlin.math.abs
import kotlin.math.max

data class SharpCurve(val alongM: Double, val left: Boolean, val radiusM: Double)

data class NavInfo(
    val maneuver: Maneuver?,
    val distToManeuver: Double,
    val remainingM: Double,
    val remainingS: Double,
    val offRouteM: Double,
    val curveAhead: SharpCurve?,
    val curveDist: Double,
    val arrived: Boolean,
    val isTrack: Boolean,
    val backBearing: Double,
)

/**
 * Sigue una ruta: dónde vas, cuál es la siguiente vuelta, curvas cerradas adelante y si te saliste.
 * Las frases que hay que decir salen por [speak].
 */
class Navigator(var route: Route, private val speak: (String, Boolean) -> Unit) {
    var index = 0; private set
    private var along = 0.0
    private val announced = HashSet<String>()
    private var offCount = 0
    var curves: List<SharpCurve> = findCurves(route); private set
    var hazards: List<Hazard> = emptyList()
    private var arrivedSaid = false

    fun replace(r: Route) {
        route = r; index = 0; along = 0.0; offCount = 0
        curves = findCurves(r)
        announced.removeAll { it.startsWith("m") || it.startsWith("c") }
    }

    /** Devuelve true si conviene recalcular la ruta (te saliste varias veces seguidas). */
    fun update(p: P, speedKmh: Double, accuracy: Float): Pair<NavInfo, Boolean> {
        val r = route
        val snap = Geo.snap(p, r.points, r.cum, index, 800) ?: Geo.snap(p, r.points, r.cum, 0, 0)
        var reroute = false
        val off = snap?.offset ?: 0.0
        if (snap != null) {
            if (off < 45 || snap.index >= index) { index = snap.index; along = snap.along }
            if (off > 70 && accuracy < 35) offCount++ else offCount = 0
            if (offCount == 4) {
                if (r.isTrack) speak("Te saliste del trazo", false) else { speak("Recalculando", false); reroute = true }
            }
        }
        val total = r.cum.lastOrNull() ?: 0.0
        val remaining = max(0.0, total - along)
        val remainingS = if (total > 0) r.timeS * remaining / total else 0.0

        // Siguiente maniobra
        val next = r.maneuvers.firstOrNull { it.shapeIndex > index && it.type != 1 && it.type != 2 && it.type != 3 }
        val dMan = next?.let { max(0.0, (r.cum.getOrNull(it.shapeIndex) ?: total) - along) } ?: remaining
        if (next != null && offCount == 0) announceManeuver(next, dMan, speedKmh)

        // Curva cerrada adelante
        val curve = curves.firstOrNull { it.alongM > along - 20 }
        val dCurve = curve?.let { it.alongM - along } ?: Double.MAX_VALUE
        if (curve != null && speedKmh > 35) {
            val warnAt = (speedKmh * 4.5).coerceIn(150.0, 450.0)
            val key = "c${curve.alongM.toInt()}"
            if (dCurve in 40.0..warnAt && key !in announced && (next == null || abs(dMan - dCurve) > 120)) {
                announced += key
                speak("Curva cerrada a la ${if (curve.left) "izquierda" else "derecha"} en ${round(dCurve)} metros", false)
            }
        }

        // Peligros marcados por el grupo cerca del camino
        for (h in hazards) {
            val key = "h${h.id}"
            if (key in announced) continue
            val d = Geo.dist(p, h.p)
            if (d < 600) {
                val s = Geo.snap(h.p, r.points, r.cum, index, 400)
                if (s != null && s.offset < 60 && s.along > along) {
                    announced += key
                    speak("Peligro marcado por el grupo: ${hazardName(h.kind)} en ${round(s.along - along)} metros", true)
                }
            }
        }

        val arrived = !r.isTrack && remaining < 40 && total > 200
        if (arrived && !arrivedSaid) { arrivedSaid = true; speak("Llegaste a tu destino", false) }
        val back = if (snap != null && off > 40) Geo.bearing(p, snap.point) else -1.0
        return NavInfo(next, dMan, remaining, remainingS, off, curve?.takeIf { dCurve < 800 }, dCurve, arrived, r.isTrack, back) to reroute
    }

    private fun round(m: Double): Int = when {
        m > 900 -> ((m / 100).toInt() * 100)
        m > 200 -> ((m / 50).toInt() * 50)
        else -> ((m / 10).toInt() * 10)
    }

    private fun announceManeuver(m: Maneuver, d: Double, kmh: Double) {
        val far = if (kmh > 80) 1500.0 else 800.0
        val key = "m${m.shapeIndex}"
        val text = m.verbal.trimEnd('.')
        if (d in (far * 0.6)..far && "$key-1" !in announced) {
            announced += "$key-1"; speak("En ${round(d)} metros, ${lower(text)}", false)
        } else if (d in 120.0..(if (kmh > 70) 450.0 else 300.0) && "$key-2" !in announced) {
            announced += "$key-2"; announced += "$key-1"; speak("En ${round(d)} metros, ${lower(text)}", false)
        } else if (d < max(40.0, kmh / 3.6 * 4) && "$key-3" !in announced) {
            announced += "$key-3"; announced += "$key-2"; announced += "$key-1"; speak(text, false)
        }
    }

    private fun lower(s: String) = s.replaceFirstChar { it.lowercase() }

    companion object {
        fun hazardName(k: String) = when (k) {
            "bache" -> "bache"; "grava" -> "grava"; "aceite" -> "aceite"; "reten" -> "retén"
            "accidente" -> "accidente"; "animal" -> "animales en el camino"; "obra" -> "obra"; "trafico" -> "tráfico"
            else -> k
        }

        /** Curvas con radio menor a ~70 m lejos de cruces (las que de verdad sorprenden). */
        fun findCurves(r: Route): List<SharpCurve> {
            if (r.points.size < 5) return emptyList()
            val s = Geo.simplify(r.points, 15.0)
            val cum = Geo.cumulative(s)
            val manAlong = r.maneuvers.mapNotNull { r.cum.getOrNull(it.shapeIndex) }
            val out = ArrayList<SharpCurve>()
            var i = 2
            while (i < s.size - 4) {
                // giro acumulado en ~60 m
                val b1 = Geo.bearing(s[i - 2], s[i])
                val b2 = Geo.bearing(s[i], s[i + 4])
                val t = Geo.turn(b1, b2)
                if (abs(t) >= 55) {
                    val len = cum[i + 4] - cum[i - 2]
                    val radius = len / Math.toRadians(abs(t))
                    val at = cum[i]
                    val nearTurn = manAlong.any { abs(it - at) < 60 }
                    if (radius < 70 && !nearTurn && (out.isEmpty() || at - out.last().alongM > 120)) {
                        out += SharpCurve(at, t < 0, radius)
                        i += 5; continue
                    }
                }
                i++
            }
            return out
        }
    }
}
