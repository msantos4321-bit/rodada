package com.mauricio.rodada.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class Spec(
    val id: Int, val brand: String, val model: String, val years: String, val kind: String, val engine: String,
    val power: String, val weight: String, val fuelL: Double?, val oil: String, val oilL: String,
    val tireF: String, val tireR: String, val psiF: Int?, val psiR: Int?, val plug: String, val chain: String,
    val oilKm: Int?, val valvesKm: Int?, val tips: String, val mods: String, val verified: Boolean,
) { val title get() = "$brand $model" }

data class Wiki(val image: String, val extract: String, val url: String)
data class Recall(val campaign: String, val date: String, val component: String, val summary: String, val remedy: String)

/** Fichas de motos (catálogo de Rodada en Supabase), foto e información de Wikipedia y llamados a revisión (NHTSA). */
object Specs {
    private fun parse(o: JSONObject) = Spec(
        o.optInt("id"), o.optString("brand"), o.optString("model"), o.optString("years"), o.optString("kind"), o.optString("engine"),
        o.optString("power"), o.optString("weight"), if (o.isNull("fuel_l")) null else o.optDouble("fuel_l"), o.optString("oil"), o.optString("oil_l"),
        o.optString("tire_f"), o.optString("tire_r"), if (o.isNull("psi_f")) null else o.optInt("psi_f"), if (o.isNull("psi_r")) null else o.optInt("psi_r"),
        o.optString("plug"), o.optString("chain"), if (o.isNull("oil_km")) null else o.optInt("oil_km"), if (o.isNull("valves_km")) null else o.optInt("valves_km"),
        o.optString("tips"), o.optString("mods"), o.optBoolean("verified"),
    )

    suspend fun search(q: String): List<Spec> {
        val txt = Group.call("rd_specs_search", JSONObject().put("p_q", q)) ?: return emptyList()
        return runCatching { JSONArray(txt).let { a -> (0 until a.length()).map { parse(a.getJSONObject(it)) } } }.getOrDefault(emptyList())
    }

    suspend fun get(id: Int): Spec? = Group.call("rd_spec_get", JSONObject().put("p_id", id))
        ?.takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }

    suspend fun request(q: String) = Group.call("rd_spec_request", JSONObject().put("p_q", q))

    /** Foto y resumen de la moto en Wikipedia (español y, si no hay, inglés). */
    suspend fun wiki(q: String): Wiki? {
        for (lang in listOf("es", "en")) {
            val s = Net.get("https://$lang.wikipedia.org/w/api.php?action=query&list=search&srlimit=1&format=json&srsearch=" +
                URLEncoder.encode("$q motocicleta", "UTF-8")) ?: continue
            val title = runCatching { JSONObject(s).getJSONObject("query").getJSONArray("search").getJSONObject(0).getString("title") }.getOrNull() ?: continue
            val sum = Net.get("https://$lang.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")) ?: continue
            val o = runCatching { JSONObject(sum) }.getOrNull() ?: continue
            val img = o.optJSONObject("originalimage")?.optString("source") ?: o.optJSONObject("thumbnail")?.optString("source") ?: ""
            val words = q.lowercase().split(" ").filter { it.length > 1 }
            // Que de verdad sea la moto buscada
            if (words.none { title.lowercase().contains(it) }) continue
            return Wiki(img, o.optString("extract"), o.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page").orEmpty())
        }
        return null
    }

    /** Llamados a revisión del fabricante en EE. UU. (NHTSA). */
    suspend fun recalls(make: String, model: String, year: String): List<Recall> {
        if (year.length != 4) return emptyList()
        val m = model.substringBefore(" / ").substringBefore(" (").trim()
        val txt = Net.get("https://api.nhtsa.gov/recalls/recallsByVehicle?make=${URLEncoder.encode(make, "UTF-8")}" +
            "&model=${URLEncoder.encode(m, "UTF-8")}&modelYear=$year") ?: return emptyList()
        return runCatching {
            val a = JSONObject(txt).optJSONArray("results") ?: JSONArray()
            (0 until a.length()).map { a.getJSONObject(it).let { o ->
                Recall(o.optString("NHTSACampaignNumber"), o.optString("ReportReceivedDate"), o.optString("Component"), o.optString("Summary"), o.optString("Remedy"))
            } }
        }.getOrDefault(emptyList())
    }
}
