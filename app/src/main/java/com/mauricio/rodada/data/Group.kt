package com.mauricio.rodada.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class Member(val id: String, val name: String, val color: String, val bike: String, val role: String, val photo: String = "")
data class GroupInfo(val id: String, val code: String, val name: String, val home: P?, val members: List<Member>)
data class Msg(val id: Long, val member: String, val name: String, val color: String, val kind: String, val body: String, val data: JSONObject?, val at: String, val group: String = "", val groupName: String = "")
data class Answer(val member: String, val name: String, val color: String, val answer: String)
data class CatalogRoute(val id: Int, val name: String, val kind: String, val km: Int, val description: String, val waypoints: List<P>, val needsBorder: Boolean, val distKm: Int?)
data class Weekly(
    val week: String,
    val route: CatalogRoute,
    val startAt: Instant?,
    val meet: String,
    val meetP: P?,
    val mine: String?,
    val answers: List<Answer>,
    val source: String,
)
data class Live(val member: String, val name: String, val color: String, val p: P, val speed: Double, val heading: Double, val battery: Int, val status: String, val ageS: Int)
data class Hazard(val id: Long, val kind: String, val p: P, val note: String, val by: String, val at: String)
data class GroupPlace(val id: Long, val kind: String, val name: String, val p: P, val note: String, val by: String)
data class KmRow(val member: String, val name: String, val color: String, val km: Int, val rides: Int)

/**
 * El grupo de amigos vive en Supabase (proyecto «cinecasa», tablas rd_*).
 * Cada celular tiene un identificador propio (Store.memberId); no hay contraseñas ni correos.
 */
object Group {
    private const val URL = "https://wnwecaapchcfhcljsvli.supabase.co/rest/v1/rpc/"
    private const val KEY = "sb_publishable_vnkSLd5EzN53_LBv76YxUA_QRo2u8ok"

    private val _groups = MutableStateFlow<List<GroupInfo>>(emptyList())
    val groups: StateFlow<List<GroupInfo>> get() = _groups

    val current: GroupInfo? get() = _groups.value.firstOrNull { it.id == Store.groupId } ?: _groups.value.firstOrNull()

    internal suspend fun call(fn: String, body: JSONObject): String? {
        body.put("p_me", Store.memberId)
        return Net.postJson(URL + fn, body.toString(), mapOf("apikey" to KEY), Net.quick)
    }

    private fun arr(txt: String?): JSONArray? = txt?.let { runCatching { JSONArray(it) }.getOrNull() }
    private fun obj(txt: String?): JSONObject? = txt?.let { runCatching { JSONObject(it) }.getOrNull() }

    suspend fun register(): Boolean {
        val p = Store.profile.value
        val color = String.format("#%06X", (p.color and 0xFFFFFF))
        return call("rd_register", JSONObject().put("p_name", p.name).put("p_color", color).put("p_bike", Store.currentBike?.name.orEmpty())) != null
    }

    private fun parseGroup(o: JSONObject): GroupInfo {
        val ms = o.optJSONArray("members") ?: JSONArray()
        val home = if (o.isNull("home_lat")) null else P(o.optDouble("home_lat"), o.optDouble("home_lon"))
        return GroupInfo(o.optString("id"), o.optString("code"), o.optString("name"), home,
            (0 until ms.length()).map { ms.getJSONObject(it).let { m -> Member(m.optString("id"), m.optString("name"), m.optString("color"), m.optString("bike"), m.optString("role"), m.optString("photo")) } })
    }

    suspend fun setPhoto(url: String) = call("rd_member_photo", JSONObject().put("p_url", url))

    suspend fun refresh(): Boolean {
        val a = arr(call("rd_groups_mine", JSONObject())) ?: return false
        _groups.value = (0 until a.length()).map { parseGroup(a.getJSONObject(it)) }
        if (_groups.value.none { it.id == Store.groupId }) Store.groupId = _groups.value.firstOrNull()?.id.orEmpty()
        return true
    }

    /** Devuelve null si salió bien o el mensaje de error. */
    suspend fun create(name: String, home: P?): String? {
        register()
        val o = obj(call("rd_group_create", JSONObject().put("p_name", name).put("p_lat", home?.lat ?: JSONObject.NULL).put("p_lon", home?.lon ?: JSONObject.NULL)))
            ?: return "No hay conexión con el grupo. Revisa tu internet."
        o.optString("error").takeIf { it.isNotBlank() }?.let { return if (it == "too_many") "Ya estás en 5 grupos." else "No se pudo crear ($it)." }
        Store.groupId = o.optString("id")
        refresh()
        return null
    }

    suspend fun join(code: String): String? {
        register()
        val o = obj(call("rd_group_join", JSONObject().put("p_code", code))) ?: return "No hay conexión con el grupo. Revisa tu internet."
        o.optString("error").takeIf { it.isNotBlank() }?.let {
            return when (it) { "unknown" -> "Ese código de grupo no existe. Revísalo letra por letra."; "full" -> "El grupo está lleno."; else -> "No se pudo entrar ($it)." }
        }
        Store.groupId = o.optString("id")
        refresh()
        return null
    }

    suspend fun leave(id: String) {
        call("rd_group_leave", JSONObject().put("p_group", id))
        refresh()
    }

    suspend fun rename(group: String, name: String) = call("rd_group_rename", JSONObject().put("p_group", group).put("p_name", name)).also { refresh() }
    suspend fun kick(group: String, member: String) = call("rd_group_kick", JSONObject().put("p_group", group).put("p_member", member)).also { refresh() }
    suspend fun makeAdmin(group: String, member: String) = call("rd_group_admin", JSONObject().put("p_group", group).put("p_member", member)).also { refresh() }
    suspend fun newCode(group: String) = call("rd_group_newcode", JSONObject().put("p_group", group)).also { refresh() }

    suspend fun setHome(group: String, p: P) = call("rd_group_home", JSONObject().put("p_group", group).put("p_lat", p.lat).put("p_lon", p.lon))

    private fun parseMsg(o: JSONObject) = Msg(o.optLong("id"), o.optString("member"), o.optString("name"), o.optString("color"),
        o.optString("kind"), o.optString("body"), o.optJSONObject("data"), o.optString("at"), o.optString("group"), o.optString("group_name"))

    suspend fun messages(group: String, after: Long): List<Msg>? {
        val a = arr(call("rd_msg_list", JSONObject().put("p_group", group).put("p_after", after))) ?: return null
        return (0 until a.length()).map { parseMsg(a.getJSONObject(it)) }
    }

    suspend fun send(group: String, body: String, kind: String = "text", data: JSONObject? = null): Boolean {
        val r = call("rd_msg_send", JSONObject().put("p_group", group).put("p_kind", kind).put("p_body", body).put("p_data", data ?: JSONObject.NULL))
        return r != null && (r.trim().toLongOrNull() ?: -1) > 0
    }

    suspend fun inbox(after: Long): List<Msg>? {
        val a = arr(call("rd_inbox", JSONObject().put("p_after", after))) ?: return null
        return (0 until a.length()).map { parseMsg(a.getJSONObject(it)) }
    }

    private fun parseRoute(o: JSONObject): CatalogRoute {
        val w = o.optJSONArray("waypoints") ?: JSONArray()
        val pts = (0 until w.length()).mapNotNull { i -> w.optJSONArray(i)?.let { P(it.getDouble(0), it.getDouble(1)) } }
        return CatalogRoute(o.optInt("id"), o.optString("name"), o.optString("kind"), o.optInt("km"), o.optString("description"), pts,
            o.optBoolean("needs_border"), if (o.isNull("dist") || !o.has("dist")) null else o.optInt("dist"))
    }

    suspend fun weekly(group: String): Weekly? {
        val o = obj(call("rd_weekly_get", JSONObject().put("p_group", group))) ?: return null
        if (o.has("error")) return null
        val ans = o.optJSONArray("answers") ?: JSONArray()
        val r = o.optJSONObject("route") ?: return null
        return Weekly(
            week = o.optString("week"),
            route = parseRoute(r),
            startAt = o.optString("start_at").takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() },
            meet = o.optString("meet"),
            meetP = if (o.isNull("meet_lat")) null else P(o.optDouble("meet_lat"), o.optDouble("meet_lon")),
            mine = o.optString("mine").takeIf { it.isNotBlank() && it != "null" },
            answers = (0 until ans.length()).map { ans.getJSONObject(it).let { a -> Answer(a.optString("member"), a.optString("name"), a.optString("color"), a.optString("answer")) } },
            source = r.optString("source"),
        )
    }

    suspend fun setWeekly(group: String, route: CatalogRoute? = null, start: Instant? = null, meet: String? = null, meetP: P? = null): Boolean {
        val rj = route?.let { r ->
            val w = JSONArray(); r.waypoints.forEach { w.put(JSONArray().put(it.lat).put(it.lon)) }
            JSONObject().put("name", r.name).put("kind", r.kind).put("km", r.km).put("description", r.description)
                .put("waypoints", w).put("needs_border", r.needsBorder).put("source", "grupo")
        }
        val body = JSONObject().put("p_group", group)
            .put("p_route", rj ?: JSONObject.NULL)
            .put("p_start", start?.toString() ?: JSONObject.NULL)
            .put("p_meet", meet ?: JSONObject.NULL)
            .put("p_meet_lat", meetP?.lat ?: JSONObject.NULL)
            .put("p_meet_lon", meetP?.lon ?: JSONObject.NULL)
        return call("rd_weekly_set", body)?.contains("ok") == true
    }

    suspend fun rsvp(group: String, answer: String) = call("rd_rsvp_set", JSONObject().put("p_group", group).put("p_answer", answer))

    suspend fun catalog(near: P?): List<CatalogRoute> {
        val a = arr(call("rd_catalog_list", JSONObject().put("p_lat", near?.lat ?: JSONObject.NULL).put("p_lon", near?.lon ?: JSONObject.NULL))) ?: return emptyList()
        return (0 until a.length()).map { parseRoute(a.getJSONObject(it)) }
    }

    suspend fun liveUpdate(group: String, p: P, speed: Double, heading: Double, battery: Int, status: String) =
        call("rd_live_update", JSONObject().put("p_group", group).put("p_lat", p.lat).put("p_lon", p.lon)
            .put("p_speed", speed).put("p_heading", heading).put("p_battery", battery).put("p_status", status))

    suspend fun liveStop(group: String) = call("rd_live_stop", JSONObject().put("p_group", group))

    suspend fun live(group: String): List<Live>? {
        val a = arr(call("rd_live_list", JSONObject().put("p_group", group))) ?: return null
        return (0 until a.length()).map { a.getJSONObject(it).let { o ->
            Live(o.optString("member"), o.optString("name"), o.optString("color"), P(o.optDouble("lat"), o.optDouble("lon")),
                o.optDouble("speed"), o.optDouble("heading"), o.optInt("battery", -1), o.optString("status"), o.optInt("age"))
        } }
    }

    suspend fun addHazard(group: String, kind: String, p: P, note: String) =
        call("rd_hazard_add", JSONObject().put("p_group", group).put("p_kind", kind).put("p_lat", p.lat).put("p_lon", p.lon).put("p_note", note))

    suspend fun hazards(group: String): List<Hazard> {
        val a = arr(call("rd_hazard_list", JSONObject().put("p_group", group))) ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it).let { o ->
            Hazard(o.optLong("id"), o.optString("kind"), P(o.optDouble("lat"), o.optDouble("lon")), o.optString("note"), o.optString("name"), o.optString("at"))
        } }
    }

    suspend fun clearHazard(id: Long) = call("rd_hazard_clear", JSONObject().put("p_id", id))

    suspend fun addPlace(group: String, kind: String, name: String, p: P, note: String) =
        call("rd_place_add", JSONObject().put("p_group", group).put("p_kind", kind).put("p_name", name).put("p_lat", p.lat).put("p_lon", p.lon).put("p_note", note))

    suspend fun places(group: String): List<GroupPlace> {
        val a = arr(call("rd_place_list", JSONObject().put("p_group", group))) ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it).let { o ->
            GroupPlace(o.optLong("id"), o.optString("kind"), o.optString("name"), P(o.optDouble("lat"), o.optDouble("lon")), o.optString("note"), o.optString("by"))
        } }
    }

    suspend fun deletePlace(id: Long) = call("rd_place_delete", JSONObject().put("p_id", id))

    suspend fun addKm(km: Double) = call("rd_km_add", JSONObject().put("p_km", km))

    suspend fun board(group: String): List<KmRow> {
        val a = arr(call("rd_km_board", JSONObject().put("p_group", group))) ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it).let { o -> KmRow(o.optString("member"), o.optString("name"), o.optString("color"), o.optInt("km"), o.optInt("rides")) } }
    }

    fun invite(g: GroupInfo): String =
        "Únete a mi grupo de motos «${g.name}» en Rodada 🏍️\n\n" +
        "1. Descarga la app: https://github.com/msantos4321-bit/rodada/releases/latest/download/Rodada.apk\n" +
        "2. Abre Grupo → Unirme y escribe el código: ${g.code}\n\n" +
        "Ahí vemos la ruta de la semana, quién va, el chat y dónde va cada quien en la rodada."
}
