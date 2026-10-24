package com.mauricio.rodada.ride

import com.mauricio.rodada.data.Geo
import com.mauricio.rodada.data.P
import com.mauricio.rodada.data.RidePoint
import com.mauricio.rodada.data.RideStats
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Cálculos de la rodada con los sensores del celular.
 *
 * Inclinación: no se puede medir con el acelerómetro solo (en una curva la "gravedad" que siente
 * el celular se inclina junto con la moto). Usamos la física de la curva:
 *   sen(inclinación) = velocidad × giro alrededor del eje vertical de la moto / g
 * El giro sale del giroscopio proyectado sobre la gravedad aparente, así que no importa
 * cómo esté montado el celular (manubrio, tanque o bolsillo).
 */
class Telemetry {
    companion object {
        const val G = 9.80665
    }

    // ---- Estado de sensores ----
    private val up = DoubleArray(3)          // gravedad aparente filtrada (eje vertical de la moto, en el marco del celular)
    private val upSlow = DoubleArray(3)      // promedio lento (posición normal de manejo)
    private var upInit = false
    private val bias = DoubleArray(3)        // sesgo del giroscopio (se aprende parado)
    private var biasN = 0
    private var lastAccT = 0L
    private var lastGyroT = 0L

    /** Último pico de aceleración (en G) y cuándo. */
    var lastSpikeG = 0.0; private set
    var lastSpikeT = 0L; private set

    // ---- Resultados en vivo ----
    @Volatile var lean = 0.0; private set          // grados; + derecha
    @Volatile var latG = 0.0; private set
    @Volatile var longG = 0.0; private set
    @Volatile var speedMs = 0.0; private set
    var gpsSpeedT = 0L; private set

    // ---- Estadísticas ----
    var distance = 0.0; private set
    var movingS = 0.0; private set
    var maxSpeed = 0.0; private set
    var maxLeanL = 0.0; private set
    var maxLeanR = 0.0; private set
    var maxBrakeG = 0.0; private set
    var maxAccelG = 0.0; private set
    var maxLatG = 0.0; private set
    var climb = 0.0; private set
    var best0to100 = 0.0; private set
    var best0to60 = 0.0; private set
    var best100to0 = 0.0; private set
    var curves = 0; private set
    var midCornerBrakes = 0; private set
    var hardBrakes = 0; private set
    private var leanSumAbs = 0.0
    private var leanSamples = 0
    private val curveMaxL = ArrayList<Double>()
    private val curveMaxR = ArrayList<Double>()

    /** Último resultado de aceleración para mostrar ("0–100 en 5.2 s"). */
    @Volatile var lastLaunch: String = ""; private set

    val points = ArrayList<RidePoint>()
    private var lastFix: P? = null
    private var lastFixT = 0L
    private var lastAlt = Double.NaN
    private var lastSpeed = 0.0

    // ---- Aceleración 0–100 ----
    private var launchT0 = 0L
    private var launch60 = 0.0
    private var launchArmed = false
    private var prevSpeedKmh = 0.0
    private var prevSpeedT = 0L
    // ---- Frenada 100–0 ----
    private var brakeT0 = 0L
    private var brakeArmed = false

    // ---- Curvas ----
    private var inCurve = false
    private var curvePeak = 0.0
    private var curveBraked = false

    // ---------- Sensores ----------

    fun onAccel(x: Float, y: Float, z: Float, tNanos: Long) {
        val t = tNanos / 1_000_000
        val mag = sqrt((x * x + y * y + z * z).toDouble())
        val g = mag / G
        if (g > lastSpikeG || t - lastSpikeT > 3000) { if (g > 1.8) { lastSpikeG = g; lastSpikeT = t } }
        if (!upInit) {
            up[0] = x.toDouble(); up[1] = y.toDouble(); up[2] = z.toDouble()
            upSlow[0] = up[0]; upSlow[1] = up[1]; upSlow[2] = up[2]
            upInit = true; lastAccT = t; return
        }
        val dt = ((t - lastAccT).coerceIn(1, 200)) / 1000.0
        lastAccT = t
        val a = 1 - exp(-dt / 0.35)
        val b = 1 - exp(-dt / 20.0)
        up[0] += a * (x - up[0]); up[1] += a * (y - up[1]); up[2] += a * (z - up[2])
        upSlow[0] += b * (x - upSlow[0]); upSlow[1] += b * (y - upSlow[1]); upSlow[2] += b * (z - upSlow[2])
    }

    fun onGyro(x: Float, y: Float, z: Float, tNanos: Long) {
        val t = tNanos / 1_000_000
        val dt = if (lastGyroT == 0L) 0.0 else ((t - lastGyroT).coerceIn(1, 200)) / 1000.0
        lastGyroT = t
        if (!upInit) return
        val n = sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2])
        if (n < 1) return
        // Aprender el sesgo del giroscopio cuando la moto está parada y quieta
        val gm = sqrt((x * x + y * y + z * z).toDouble())
        if (speedMs < 0.5 && gm < 0.05 && System.currentTimeMillis() - gpsSpeedT < 5000) {
            val k = 1.0 / (++biasN).coerceAtMost(500)
            bias[0] += k * (x - bias[0]); bias[1] += k * (y - bias[1]); bias[2] += k * (z - bias[2])
        }
        val gx = x - bias[0]; val gy = y - bias[1]; val gz = z - bias[2]
        // Giro alrededor del eje vertical de la moto (positivo = hacia la izquierda)
        val yaw = (gx * up[0] + gy * up[1] + gz * up[2]) / n
        val v = currentSpeed()
        val s = (v * yaw / G).coerceIn(-0.95, 0.95)
        val target = if (v < 2.5) 0.0 else -Math.toDegrees(asin(s))
        // Suavizado ligero (0.12 s)
        val k = if (dt <= 0) 1.0 else 1 - exp(-dt / 0.12)
        lean += k * (target - lean)
        latG = v * yaw / G
        if (dt > 0) trackLean(lean)
    }

    /** Velocidad estimada entre lecturas del GPS (sigue la tendencia de aceleración). */
    private fun currentSpeed(): Double {
        val since = (System.currentTimeMillis() - gpsSpeedT) / 1000.0
        if (since > 3) return speedMs
        return max(0.0, speedMs + longG * G * since.coerceAtMost(1.0))
    }

    /** Ángulo (grados) entre la posición normal de manejo y la de ahora: sirve para saber si la moto quedó tirada. */
    fun tiltFromNormal(): Double {
        val a = up; val b = upSlow
        val na = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
        val nb = sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2])
        if (na < 1 || nb < 1) return 0.0
        val c = ((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (na * nb)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(c))
    }

    private fun trackLean(l: Double) {
        val al = abs(l)
        if (l < 0) maxLeanL = max(maxLeanL, al) else maxLeanR = max(maxLeanR, al)
        if (!inCurve && al > 12) { inCurve = true; curvePeak = 0.0; curveBraked = false }
        if (inCurve) {
            curvePeak = max(curvePeak, al)
            if (al > 15 && longG < -0.25) curveBraked = true
            if (al < 6) {
                inCurve = false
                curves++
                if (curveBraked) midCornerBrakes++
            }
        }
    }

    // ---------- GPS ----------

    /** Devuelve true si el punto se guardó. */
    fun onLocation(p: P, alt: Double, speedKmhIn: Double?, accuracy: Float, t: Long): Boolean {
        val prev = lastFix
        val dtS = if (lastFixT == 0L) 0.0 else (t - lastFixT) / 1000.0
        // Velocidad: la del GPS si viene, si no, distancia entre puntos
        val step = if (prev != null) Geo.dist(prev, p) else 0.0
        val kmh = speedKmhIn ?: if (dtS > 0) step / dtS * 3.6 else 0.0
        if (accuracy > 50) return false

        // Aceleración longitudinal por cambio de velocidad
        if (prevSpeedT != 0L) {
            val dt = (t - prevSpeedT) / 1000.0
            if (dt in 0.15..3.0) {
                val acc = ((kmh - prevSpeedKmh) / 3.6) / dt / G
                longG = longG * 0.4 + acc * 0.6
                if (longG < -maxBrakeG) maxBrakeG = -longG
                if (longG > maxAccelG && kmh > 10) maxAccelG = longG
                if (longG < -0.6 && kmh > 20) hardBrakes++
            }
        }
        launchTiming(prevSpeedKmh, prevSpeedT, kmh, t)
        prevSpeedKmh = kmh; prevSpeedT = t
        speedMs = kmh / 3.6; gpsSpeedT = System.currentTimeMillis()

        maxLatG = max(maxLatG, abs(latG))
        if (abs(lean) > 3) { leanSumAbs += abs(lean); leanSamples++ }

        if (prev != null && accuracy < 30 && kmh > 2) {
            distance += step
            if (dtS < 10) movingS += dtS
            maxSpeed = max(maxSpeed, kmh)
        }
        if (!alt.isNaN() && !lastAlt.isNaN() && accuracy < 20) { val d = alt - lastAlt; if (d in 0.5..30.0) climb += d }
        if (!alt.isNaN()) lastAlt = alt

        lastFix = p; lastFixT = t; lastSpeed = kmh
        // Guardar un punto cada 1 s aprox (o si cambió mucho la inclinación)
        val last = points.lastOrNull()
        if (last == null || t - last.time >= 1000 || abs(last.lean - lean) > 8) {
            points += RidePoint(t, p.lat, p.lon, alt, kmh, lean)
            return true
        }
        return false
    }

    private fun cross(v1: Double, t1: Long, v2: Double, t2: Long, target: Double): Long =
        if (v2 == v1) t2 else t1 + ((target - v1) / (v2 - v1) * (t2 - t1)).toLong()

    private fun launchTiming(v1: Double, t1: Long, v2: Double, t2: Long) {
        if (t1 == 0L) return
        // 0–100: arranca desde casi parado
        if (v2 < 4) { launchArmed = true; launchT0 = 0; launch60 = 0.0 }
        if (launchArmed && launchT0 == 0L && v1 < 4 && v2 >= 4) launchT0 = cross(v1, t1, v2, t2, 4.0) - 150
        if (launchArmed && launchT0 != 0L) {
            if (v2 < v1 - 3 || t2 - launchT0 > 25_000) { launchArmed = false; launchT0 = 0 }
            else {
                if (launch60 == 0.0 && v1 < 60 && v2 >= 60) {
                    launch60 = (cross(v1, t1, v2, t2, 60.0) - launchT0) / 1000.0
                    if (best0to60 == 0.0 || launch60 < best0to60) best0to60 = launch60
                }
                if (v1 < 100 && v2 >= 100) {
                    val s = (cross(v1, t1, v2, t2, 100.0) - launchT0) / 1000.0
                    if (s > 1.5) {
                        if (best0to100 == 0.0 || s < best0to100) best0to100 = s
                        lastLaunch = "0–100 km/h en ${String.format(java.util.Locale.US, "%.1f", s)} s"
                    }
                    launchArmed = false; launchT0 = 0
                }
            }
        }
        // 100–0
        if (v2 >= 100) { brakeArmed = true; brakeT0 = 0 }
        if (brakeArmed && brakeT0 == 0L && v1 >= 100 && v2 < 100) brakeT0 = cross(v1, t1, v2, t2, 100.0)
        if (brakeArmed && brakeT0 != 0L) {
            if (v2 > v1 + 2) { brakeArmed = false; brakeT0 = 0 }
            else if (v2 < 3) {
                val s = (cross(v1, t1, v2, t2, 3.0) - brakeT0) / 1000.0
                if (s in 1.5..15.0 && (best100to0 == 0.0 || s < best100to0)) best100to0 = s
                brakeArmed = false; brakeT0 = 0
            }
        }
    }

    // ---------- Resumen ----------

    fun stats(totalS: Double): RideStats {
        val tips = ArrayList<String>()
        if (midCornerBrakes > 0) tips += "Frenaste a media curva $midCornerBrakes ${if (midCornerBrakes == 1) "vez" else "veces"}: frena antes de entrar y mantén el gas parejo dentro de la curva."
        if (curves >= 8 && abs(maxLeanL - maxLeanR) > 6) {
            val weak = if (maxLeanL < maxLeanR) "izquierda" else "derecha"
            tips += "Tus curvas a la $weak son menos inclinadas (${maxLeanL.toInt()}° izq. contra ${maxLeanR.toInt()}° der.). Practica ese lado con calma y la mirada en la salida."
        }
        if (hardBrakes > 2) tips += "$hardBrakes frenadas fuertes: deja más distancia con el de adelante y anticipa los altos."
        if (maxLatG > 0.9) tips += "Llegaste a ${String.format(java.util.Locale.US, "%.1f", maxLatG)} G de lado: estás cerca del límite de agarre de una llanta de calle."
        if (tips.isEmpty() && curves > 0) tips += "Manejo parejo: sin frenadas a media curva. ¡Así se hace!"
        val penalty = midCornerBrakes * 5 + hardBrakes * 2 + (if (abs(maxLeanL - maxLeanR) > 10) 5 else 0)
        val smooth = if (curves + hardBrakes == 0) 0 else (100 - penalty).coerceIn(35, 100)
        return RideStats(
            distanceM = distance, movingS = movingS, totalS = totalS, maxSpeed = maxSpeed,
            maxLeanL = maxLeanL, maxLeanR = maxLeanR, maxBrakeG = maxBrakeG, maxAccelG = maxAccelG, maxLatG = maxLatG,
            best0to100 = best0to100, best0to60 = best0to60, best100to0 = best100to0,
            curves = curves, smooth = smooth, midCornerBrakes = midCornerBrakes, wheelies = 0, climbM = climb, tips = tips,
        )
    }
}
