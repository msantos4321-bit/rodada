package com.mauricio.rodada.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class P(val lat: Double, val lon: Double)

object Geo {
    private const val R = 6_371_000.0

    fun rad(d: Double) = d * PI / 180.0
    fun deg(r: Double) = r * 180.0 / PI

    /** Distancia en metros. */
    fun dist(a: P, b: P): Double {
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * R * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Rumbo de a hacia b en grados (0 = norte). */
    fun bearing(a: P, b: P): Double {
        val y = sin(rad(b.lon - a.lon)) * cos(rad(b.lat))
        val x = cos(rad(a.lat)) * sin(rad(b.lat)) - sin(rad(a.lat)) * cos(rad(b.lat)) * cos(rad(b.lon - a.lon))
        return (deg(atan2(y, x)) + 360) % 360
    }

    /** Diferencia de rumbo con signo (-180..180). Positivo = gira a la derecha. */
    fun turn(b1: Double, b2: Double): Double {
        var d = b2 - b1
        while (d > 180) d -= 360
        while (d < -180) d += 360
        return d
    }

    /** Punto a cierta distancia (m) y rumbo (grados). */
    fun dest(a: P, meters: Double, brg: Double): P {
        val d = meters / R
        val b = rad(brg)
        val la1 = rad(a.lat); val lo1 = rad(a.lon)
        val la2 = kotlin.math.asin(sin(la1) * cos(d) + cos(la1) * sin(d) * cos(b))
        val lo2 = lo1 + atan2(sin(b) * sin(d) * cos(la1), cos(d) - sin(la1) * sin(la2))
        return P(deg(la2), deg(lo2))
    }

    fun length(pts: List<P>): Double {
        var s = 0.0
        for (i in 1 until pts.size) s += dist(pts[i - 1], pts[i])
        return s
    }

    /** Distancias acumuladas desde el inicio. */
    fun cumulative(pts: List<P>): DoubleArray {
        val out = DoubleArray(pts.size)
        for (i in 1 until pts.size) out[i] = out[i - 1] + dist(pts[i - 1], pts[i])
        return out
    }

    /** Polilínea codificada de Valhalla (precisión 6). */
    fun decode(enc: String, precision: Int = 6): List<P> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<P>()
        var i = 0; var lat = 0L; var lon = 0L
        while (i < enc.length) {
            var shift = 0; var result = 0L; var b: Int
            do { b = enc[i++].code - 63; result = result or ((b and 0x1f).toLong() shl shift); shift += 5 } while (b >= 0x20)
            lat += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            shift = 0; result = 0L
            do { b = enc[i++].code - 63; result = result or ((b and 0x1f).toLong() shl shift); shift += 5 } while (b >= 0x20)
            lon += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            out.add(P(lat / factor, lon / factor))
        }
        return out
    }

    /**
     * Qué tan curva es una ruta: grados de giro por kilómetro, contando solo
     * los cambios de dirección de tramos cortos (curvas reales, no esquinas de ciudad sueltas).
     */
    fun curviness(pts: List<P>): Double {
        if (pts.size < 3) return 0.0
        val s = simplify(pts, 30.0)
        var total = 0.0
        for (i in 1 until s.size - 1) {
            val t = abs(turn(bearing(s[i - 1], s[i]), bearing(s[i], s[i + 1])))
            if (t in 8.0..150.0) total += t
        }
        val km = length(pts) / 1000.0
        return if (km < 0.5) 0.0 else total / km
    }

    /** Cuenta curvas (cambios de dirección acumulados de 30° o más en menos de 400 m). */
    fun countCurves(pts: List<P>): Int {
        val s = simplify(pts, 25.0)
        var curves = 0
        var acc = 0.0; var accDist = 0.0; var lastSign = 0
        for (i in 1 until s.size - 1) {
            val t = turn(bearing(s[i - 1], s[i]), bearing(s[i], s[i + 1]))
            val sign = if (t > 0) 1 else -1
            val d = dist(s[i - 1], s[i])
            if (abs(t) < 5 || sign != lastSign || accDist > 400) {
                if (abs(acc) >= 30) curves++
                acc = 0.0; accDist = 0.0
            }
            if (abs(t) >= 5) { acc += t; accDist += d; lastSign = sign }
        }
        if (abs(acc) >= 30) curves++
        return curves
    }

    /** Remuestrea la línea cada [step] metros (para medir giros parejos). */
    fun simplify(pts: List<P>, step: Double): List<P> {
        if (pts.isEmpty()) return pts
        val out = ArrayList<P>()
        out.add(pts[0])
        var carry = 0.0
        for (i in 1 until pts.size) {
            var a = pts[i - 1]
            val b = pts[i]
            var seg = dist(a, b)
            while (carry + seg >= step) {
                val f = (step - carry) / seg
                val np = P(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
                out.add(np)
                seg -= (step - carry)
                a = np
                carry = 0.0
            }
            carry += seg
        }
        if (out.last() != pts.last()) out.add(pts.last())
        return out
    }

    data class Snap(val index: Int, val point: P, val offset: Double, val along: Double)

    /**
     * El punto de la línea más cercano a [p]. Busca desde [from] hacia adelante (y un poco hacia atrás)
     * para que en rutas que regresan por el mismo camino no salte al tramo equivocado.
     */
    fun snap(p: P, line: List<P>, cum: DoubleArray, from: Int = 0, window: Int = 600): Snap? {
        if (line.size < 2) return null
        val start = max(0, from - 20)
        val end = min(line.size - 1, if (window <= 0) line.size - 1 else from + window)
        var best: Snap? = null
        val cosLat = cos(rad(p.lat))
        for (i in start until end) {
            val a = line[i]; val b = line[i + 1]
            val ax = (a.lon - p.lon) * cosLat; val ay = a.lat - p.lat
            val bx = (b.lon - p.lon) * cosLat; val by = b.lat - p.lat
            val dx = bx - ax; val dy = by - ay
            val l2 = dx * dx + dy * dy
            var t = if (l2 == 0.0) 0.0 else -(ax * dx + ay * dy) / l2
            t = t.coerceIn(0.0, 1.0)
            val q = P(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            val d = dist(p, q)
            if (best == null || d < best.offset) best = Snap(i, q, d, cum[i] + dist(a, q))
        }
        return best
    }

    /** Distancia mínima de un punto a toda la línea (para gasolineras y peligros cerca de la ruta). */
    fun distToLine(p: P, line: List<P>): Double {
        var best = Double.MAX_VALUE
        val step = max(1, line.size / 2000)
        var i = 0
        while (i < line.size) { best = min(best, dist(p, line[i])); i += step }
        return best
    }

    fun fmtKm(m: Double): String = when {
        m < 950 -> "${((m / 10).toInt() * 10).coerceAtLeast(0)} m"
        m < 10_000 -> String.format(java.util.Locale.US, "%.1f km", m / 1000)
        else -> "${(m / 1000).toInt()} km"
    }

    fun fmtTime(sec: Double): String {
        val m = (sec / 60).toInt()
        return if (m < 60) "$m min" else "${m / 60} h ${String.format(java.util.Locale.US, "%02d", m % 60)}"
    }
}
