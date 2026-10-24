package com.mauricio.rodada.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RidePoint(
    val time: Long,
    val lat: Double,
    val lon: Double,
    val alt: Double,
    /** km/h */
    val speed: Double,
    /** grados; negativo = izquierda, positivo = derecha */
    val lean: Double,
)

data class RideStats(
    val distanceM: Double = 0.0,
    val movingS: Double = 0.0,
    val totalS: Double = 0.0,
    val maxSpeed: Double = 0.0,
    val maxLeanL: Double = 0.0,
    val maxLeanR: Double = 0.0,
    val maxBrakeG: Double = 0.0,
    val maxAccelG: Double = 0.0,
    val maxLatG: Double = 0.0,
    val best0to100: Double = 0.0,
    val best0to60: Double = 0.0,
    val best100to0: Double = 0.0,
    val curves: Int = 0,
    val smooth: Int = 0,
    val midCornerBrakes: Int = 0,
    val wheelies: Int = 0,
    val climbM: Double = 0.0,
    val tips: List<String> = emptyList(),
) {
    val avgMoving: Double get() = if (movingS > 0) distanceM / movingS * 3.6 else 0.0
}

data class Ride(
    val id: String,
    val title: String,
    val start: Long,
    val end: Long,
    val bike: String,
    val stats: RideStats,
    val bikeId: String = "",
    val points: List<RidePoint> = emptyList(),
)

object Rides {
    private lateinit var dir: File
    private val _list = MutableStateFlow<List<Ride>>(emptyList())
    /** Lista sin puntos (ligera). */
    val list: StateFlow<List<Ride>> get() = _list

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "rodadas").apply { mkdirs() }
        _list.value = readIndex()
    }

    private fun statsJson(s: RideStats) = JSONObject()
        .put("d", s.distanceM).put("mv", s.movingS).put("t", s.totalS).put("vmax", s.maxSpeed)
        .put("ll", s.maxLeanL).put("lr", s.maxLeanR).put("gb", s.maxBrakeG).put("ga", s.maxAccelG).put("gl", s.maxLatG)
        .put("a100", s.best0to100).put("a60", s.best0to60).put("b100", s.best100to0)
        .put("c", s.curves).put("sm", s.smooth).put("mcb", s.midCornerBrakes).put("wh", s.wheelies).put("up", s.climbM)
        .put("tips", JSONArray(s.tips))

    private fun statsFrom(o: JSONObject): RideStats {
        val tips = o.optJSONArray("tips") ?: JSONArray()
        return RideStats(
            o.optDouble("d"), o.optDouble("mv"), o.optDouble("t"), o.optDouble("vmax"),
            o.optDouble("ll"), o.optDouble("lr"), o.optDouble("gb"), o.optDouble("ga"), o.optDouble("gl"),
            o.optDouble("a100"), o.optDouble("a60"), o.optDouble("b100"),
            o.optInt("c"), o.optInt("sm"), o.optInt("mcb"), o.optInt("wh"), o.optDouble("up"),
            (0 until tips.length()).map { tips.getString(it) },
        )
    }

    private fun head(r: Ride) = JSONObject().put("id", r.id).put("title", r.title).put("start", r.start).put("end", r.end)
        .put("bike", r.bike).put("bikeId", r.bikeId).put("stats", statsJson(r.stats))

    private fun fromHead(o: JSONObject) = Ride(o.getString("id"), o.optString("title"), o.optLong("start"), o.optLong("end"),
        o.optString("bike"), statsFrom(o.optJSONObject("stats") ?: JSONObject()), o.optString("bikeId"))

    private fun readIndex(): List<Ride> = runCatching {
        val f = File(dir, "indice.json")
        if (!f.exists()) return emptyList()
        val a = JSONArray(f.readText())
        (0 until a.length()).map { fromHead(a.getJSONObject(it)) }.sortedByDescending { it.start }
    }.getOrDefault(emptyList())

    private fun writeIndex(l: List<Ride>) {
        val a = JSONArray(); l.forEach { a.put(head(it)) }
        File(dir, "indice.json").writeText(a.toString())
        _list.value = l.sortedByDescending { it.start }
    }

    fun save(r: Ride) {
        val o = head(r)
        // Puntos en arreglos compactos
        val t = JSONArray(); val la = JSONArray(); val lo = JSONArray(); val al = JSONArray(); val sp = JSONArray(); val le = JSONArray()
        r.points.forEach {
            t.put(it.time); la.put(it.lat); lo.put(it.lon); al.put(if (it.alt.isNaN()) 0.0 else it.alt); sp.put(it.speed); le.put(it.lean)
        }
        o.put("pts", JSONObject().put("t", t).put("la", la).put("lo", lo).put("al", al).put("sp", sp).put("le", le))
        File(dir, "${r.id}.json").writeText(o.toString())
        writeIndex(_list.value.filterNot { it.id == r.id } + r.copy(points = emptyList()))
    }

    fun load(id: String): Ride? = runCatching {
        val o = JSONObject(File(dir, "$id.json").readText())
        val base = fromHead(o)
        val p = o.getJSONObject("pts")
        val t = p.getJSONArray("t"); val la = p.getJSONArray("la"); val lo = p.getJSONArray("lo")
        val al = p.getJSONArray("al"); val sp = p.getJSONArray("sp"); val le = p.getJSONArray("le")
        base.copy(points = (0 until t.length()).map { i -> RidePoint(t.getLong(i), la.getDouble(i), lo.getDouble(i), al.getDouble(i), sp.getDouble(i), le.getDouble(i)) })
    }.getOrNull()

    fun rename(id: String, title: String) {
        val r = load(id) ?: return
        save(r.copy(title = title))
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        writeIndex(_list.value.filterNot { it.id == id })
    }

    fun file(id: String): File = File(dir, "$id.json")

    /** Kilómetros de este mes (para el grupo y el inicio). */
    fun kmThisMonth(): Double {
        val now = java.time.LocalDate.now()
        return _list.value.filter {
            val d = java.time.Instant.ofEpochMilli(it.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            d.year == now.year && d.month == now.month
        }.sumOf { it.stats.distanceM } / 1000
    }
}
