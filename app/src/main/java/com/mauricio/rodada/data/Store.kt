package com.mauricio.rodada.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class Contact(val name: String, val phone: String)

data class Profile(
    val name: String = "",
    val phone: String = "",
    val blood: String = "",
    val allergies: String = "",
    val medical: String = "",
    val color: Long = 0xFFD9480F,
)

data class MaintItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val everyKm: Int,
    val everyMonths: Int = 0,
    val lastKm: Int = 0,
    val lastDate: String = "",
)

data class Doc(val id: String = UUID.randomUUID().toString(), val name: String, val expires: String)

data class FuelLog(val date: String, val km: Int, val liters: Double, val pesos: Double)

/** Bitácora: mantenimiento, reparación o modificación, con fotos guardadas en el celular. */
data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: String = LocalDate.now().toString(),
    val km: Int = 0,
    val type: String = "mantenimiento",
    val title: String = "",
    val notes: String = "",
    val cost: Double = 0.0,
    val shop: String = "",
    val photos: List<String> = emptyList(),
)

/** Datos propios de la moto que corrigen la ficha de referencia (los del manual o la etiqueta). */
data class MySpecs(
    val psiF: String = "", val psiR: String = "", val oil: String = "", val oilL: String = "",
    val plug: String = "", val tireF: String = "", val tireR: String = "", val chain: String = "", val notes: String = "",
)

data class Bike(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val year: String = "",
    val odometer: Int = 0,
    val tankL: Double = 15.0,
    val kmPerL: Double = 0.0,
    val maint: List<MaintItem> = defaultMaint(0),
    val docs: List<Doc> = listOf(Doc(name = "Seguro", expires = ""), Doc(name = "Tarjeta de circulación", expires = ""), Doc(name = "Licencia", expires = "")),
    val fuel: List<FuelLog> = emptyList(),
    /** Ficha del catálogo de Rodada (0 = sin ficha). */
    val specId: Int = 0,
    /** Foto de la moto: archivo en el celular y su copia en línea (la ve el grupo). */
    val photo: String = "",
    val photoUrl: String = "",
    val log: List<LogEntry> = emptyList(),
    val mine: MySpecs = MySpecs(),
    /** Kilometraje la última vez que se llenó el tanque (-1 = no se sabe). */
    val refuelKm: Int = -1,
) {
    /** Rendimiento real según las cargas de gasolina, o el que puso el dueño. */
    val realKmPerL: Double
        get() {
            val f = fuel.sortedBy { it.km }
            if (f.size >= 2) {
                val km = f.last().km - f.first().km
                val l = f.drop(1).sumOf { it.liters }
                if (km > 0 && l > 0) return km / l
            }
            return kmPerL
        }
    val rangeKm: Int get() = if (realKmPerL > 0) (realKmPerL * tankL).toInt() else 0

    companion object {
        fun defaultMaint(km: Int) = listOf(
            MaintItem(name = "Aceite y filtro", everyKm = 5000, everyMonths = 6, lastKm = km),
            MaintItem(name = "Lubricar cadena", everyKm = 600, lastKm = km),
            MaintItem(name = "Llanta delantera", everyKm = 15000, lastKm = km),
            MaintItem(name = "Llanta trasera", everyKm = 10000, lastKm = km),
            MaintItem(name = "Balatas", everyKm = 12000, lastKm = km),
            MaintItem(name = "Líquido de frenos", everyKm = 0, everyMonths = 24, lastKm = km, lastDate = LocalDate.now().toString()),
            MaintItem(name = "Filtro de aire", everyKm = 12000, lastKm = km),
            MaintItem(name = "Bujías", everyKm = 12000, lastKm = km),
        )
    }
}

data class Settings(
    val voice: Boolean = true,
    val crashDetection: Boolean = true,
    val crashSeconds: Int = 60,
    val weeklyDay: Int = 4,      // 1 = lunes … 7 = domingo (jueves por defecto)
    val weeklyHour: Int = 18,
    val avoidUnpaved: Boolean = true,
    val keepScreenOn: Boolean = true,
    val fatigueMinutes: Int = 120,
)

/** Todo lo que se guarda en el celular (sin nube), salvo lo del grupo. */
object Store {
    private lateinit var sp: SharedPreferences

    private val _profile = MutableStateFlow(Profile())
    val profile: StateFlow<Profile> get() = _profile
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> get() = _contacts
    private val _bikes = MutableStateFlow<List<Bike>>(emptyList())
    val bikes: StateFlow<List<Bike>> get() = _bikes
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> get() = _settings

    /** Identificador de este motociclista para el grupo (no es un dato personal). */
    lateinit var memberId: String; private set

    var currentBikeId: String
        get() = sp.getString("bike_now", "").orEmpty()
        set(v) { sp.edit().putString("bike_now", v).apply() }

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) { sp.edit().putBoolean("onboarded", v).apply() }

    var groupId: String
        get() = sp.getString("group", "").orEmpty()
        set(v) { sp.edit().putString("group", v).apply() }

    var lastMsgId: Long
        get() = sp.getLong("last_msg", 0L)
        set(v) { sp.edit().putLong("last_msg", v).apply() }

    var weeklyNotified: String
        get() = sp.getString("weekly_notified", "").orEmpty()
        set(v) { sp.edit().putString("weekly_notified", v).apply() }

    var home: P?
        get() = sp.getString("home", null)?.split(",")?.let { if (it.size == 2) P(it[0].toDouble(), it[1].toDouble()) else null }
        set(v) { sp.edit().putString("home", v?.let { "${it.lat},${it.lon}" }).apply() }

    var lastLocation: P?
        get() = sp.getString("last_loc", null)?.split(",")?.let { if (it.size == 2) P(it[0].toDouble(), it[1].toDouble()) else null }
        set(v) { sp.edit().putString("last_loc", v?.let { "${it.lat},${it.lon}" }).apply() }

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("rodada", Context.MODE_PRIVATE)
        memberId = sp.getString("member", null) ?: UUID.randomUUID().toString().also { sp.edit().putString("member", it).apply() }
        _profile.value = readProfile()
        _contacts.value = readContacts()
        _bikes.value = readBikes()
        _settings.value = readSettings()
    }

    val currentBike: Bike? get() = _bikes.value.firstOrNull { it.id == currentBikeId } ?: _bikes.value.firstOrNull()

    // ---------- Perfil ----------
    fun saveProfile(p: Profile) {
        _profile.value = p
        sp.edit().putString("profile", JSONObject()
            .put("name", p.name).put("phone", p.phone).put("blood", p.blood)
            .put("allergies", p.allergies).put("medical", p.medical).put("color", p.color).toString()).apply()
    }

    private fun readProfile(): Profile = runCatching {
        val o = JSONObject(sp.getString("profile", "{}")!!)
        Profile(o.optString("name"), o.optString("phone"), o.optString("blood"), o.optString("allergies"), o.optString("medical"), o.optLong("color", 0xFFD9480F))
    }.getOrDefault(Profile())

    // ---------- Contactos de emergencia ----------
    fun saveContacts(c: List<Contact>) {
        _contacts.value = c
        val a = JSONArray()
        c.forEach { a.put(JSONObject().put("name", it.name).put("phone", it.phone)) }
        sp.edit().putString("contacts", a.toString()).apply()
    }

    private fun readContacts(): List<Contact> = runCatching {
        val a = JSONArray(sp.getString("contacts", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Contact(o.optString("name"), o.optString("phone")) } }
    }.getOrDefault(emptyList())

    // ---------- Ajustes ----------
    fun saveSettings(s: Settings) {
        _settings.value = s
        sp.edit().putString("settings", JSONObject()
            .put("voice", s.voice).put("crash", s.crashDetection).put("crashSec", s.crashSeconds)
            .put("wDay", s.weeklyDay).put("wHour", s.weeklyHour).put("unpaved", s.avoidUnpaved)
            .put("screen", s.keepScreenOn).put("fatigue", s.fatigueMinutes).toString()).apply()
    }

    private fun readSettings(): Settings = runCatching {
        val o = JSONObject(sp.getString("settings", "{}")!!)
        val d = Settings()
        Settings(
            voice = o.optBoolean("voice", d.voice),
            crashDetection = o.optBoolean("crash", d.crashDetection),
            crashSeconds = o.optInt("crashSec", d.crashSeconds).coerceIn(30, 120),
            weeklyDay = o.optInt("wDay", d.weeklyDay),
            weeklyHour = o.optInt("wHour", d.weeklyHour),
            avoidUnpaved = o.optBoolean("unpaved", d.avoidUnpaved),
            keepScreenOn = o.optBoolean("screen", d.keepScreenOn),
            fatigueMinutes = o.optInt("fatigue", d.fatigueMinutes),
        )
    }.getOrDefault(Settings())

    // ---------- Motos ----------
    fun saveBikes(list: List<Bike>) {
        _bikes.value = list
        val a = JSONArray()
        list.forEach { a.put(bikeJson(it)) }
        sp.edit().putString("bikes", a.toString()).apply()
    }

    fun upsertBike(b: Bike) {
        val l = _bikes.value.toMutableList()
        val i = l.indexOfFirst { it.id == b.id }
        if (i >= 0) l[i] = b else l.add(b)
        saveBikes(l)
        if (currentBikeId.isBlank()) currentBikeId = b.id
    }

    fun deleteBike(id: String) {
        saveBikes(_bikes.value.filterNot { it.id == id })
        if (currentBikeId == id) currentBikeId = _bikes.value.firstOrNull()?.id.orEmpty()
    }

    /** Suma kilómetros de una rodada al odómetro de la moto en uso. */
    fun addKm(km: Double) {
        val b = currentBike ?: return
        upsertBike(b.copy(odometer = b.odometer + km.toInt()))
    }

    private fun bikeJson(b: Bike): JSONObject {
        val m = JSONArray(); b.maint.forEach { m.put(JSONObject().put("id", it.id).put("name", it.name).put("km", it.everyKm).put("months", it.everyMonths).put("lastKm", it.lastKm).put("lastDate", it.lastDate)) }
        val d = JSONArray(); b.docs.forEach { d.put(JSONObject().put("id", it.id).put("name", it.name).put("exp", it.expires)) }
        val f = JSONArray(); b.fuel.forEach { f.put(JSONObject().put("date", it.date).put("km", it.km).put("l", it.liters).put("p", it.pesos)) }
        val l = JSONArray(); b.log.forEach { e ->
            l.put(JSONObject().put("id", e.id).put("date", e.date).put("km", e.km).put("type", e.type).put("title", e.title)
                .put("notes", e.notes).put("cost", e.cost).put("shop", e.shop).put("photos", JSONArray(e.photos)))
        }
        val ms = b.mine
        val mj = JSONObject().put("psiF", ms.psiF).put("psiR", ms.psiR).put("oil", ms.oil).put("oilL", ms.oilL).put("plug", ms.plug)
            .put("tireF", ms.tireF).put("tireR", ms.tireR).put("chain", ms.chain).put("notes", ms.notes)
        return JSONObject().put("id", b.id).put("name", b.name).put("year", b.year).put("odo", b.odometer)
            .put("tank", b.tankL).put("kmpl", b.kmPerL).put("maint", m).put("docs", d).put("fuel", f)
            .put("spec", b.specId).put("photo", b.photo).put("photoUrl", b.photoUrl).put("log", l).put("mine", mj).put("refuel", b.refuelKm)
    }

    private fun readBikes(): List<Bike> = runCatching {
        val a = JSONArray(sp.getString("bikes", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val m = o.optJSONArray("maint") ?: JSONArray()
            val d = o.optJSONArray("docs") ?: JSONArray()
            val f = o.optJSONArray("fuel") ?: JSONArray()
            Bike(
                id = o.optString("id"), name = o.optString("name"), year = o.optString("year"),
                odometer = o.optInt("odo"), tankL = o.optDouble("tank", 15.0), kmPerL = o.optDouble("kmpl", 0.0),
                maint = (0 until m.length()).map { j -> m.getJSONObject(j).let { x -> MaintItem(x.optString("id"), x.optString("name"), x.optInt("km"), x.optInt("months"), x.optInt("lastKm"), x.optString("lastDate")) } },
                docs = (0 until d.length()).map { j -> d.getJSONObject(j).let { x -> Doc(x.optString("id"), x.optString("name"), x.optString("exp")) } },
                fuel = (0 until f.length()).map { j -> f.getJSONObject(j).let { x -> FuelLog(x.optString("date"), x.optInt("km"), x.optDouble("l"), x.optDouble("p")) } },
                specId = o.optInt("spec"), photo = o.optString("photo"), photoUrl = o.optString("photoUrl"),
                log = (o.optJSONArray("log") ?: JSONArray()).let { l -> (0 until l.length()).map { j -> l.getJSONObject(j).let { x ->
                    val ph = x.optJSONArray("photos") ?: JSONArray()
                    LogEntry(x.optString("id"), x.optString("date"), x.optInt("km"), x.optString("type"), x.optString("title"), x.optString("notes"),
                        x.optDouble("cost", 0.0), x.optString("shop"), (0 until ph.length()).map { k -> ph.getString(k) })
                } } },
                refuelKm = o.optInt("refuel", -1),
                mine = (o.optJSONObject("mine") ?: JSONObject()).let { x -> MySpecs(x.optString("psiF"), x.optString("psiR"), x.optString("oil"),
                    x.optString("oilL"), x.optString("plug"), x.optString("tireF"), x.optString("tireR"), x.optString("chain"), x.optString("notes")) },
            )
        }
    }.getOrDefault(emptyList())
}
