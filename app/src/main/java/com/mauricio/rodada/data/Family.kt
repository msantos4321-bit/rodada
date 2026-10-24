package com.mauricio.rodada.data

import org.json.JSONObject

/** «Sígueme»: una liga para que la familia vea el viaje en vivo sin instalar nada. */
object Family {
    fun link(token: String) = "https://msantos4321-bit.github.io/rodada/sigueme/?t=$token"

    suspend fun start(dest: String, hours: Int = 12): String? {
        Group.register()
        val r = Group.call("rd_share_start", JSONObject().put("p_name", Store.profile.value.name).put("p_dest", dest).put("p_hours", hours))
            ?: return null
        return r.trim().trim('"').takeIf { it.length >= 8 }
    }

    suspend fun update(token: String, p: P?, speed: Double, etaMin: Int?, remainingKm: Double?, status: String) =
        Group.call("rd_share_update", JSONObject().put("p_token", token)
            .put("p_lat", p?.lat ?: JSONObject.NULL).put("p_lon", p?.lon ?: JSONObject.NULL)
            .put("p_speed", speed).put("p_eta", etaMin ?: JSONObject.NULL).put("p_remaining", remainingKm ?: JSONObject.NULL).put("p_status", status))

    fun message(token: String, dest: String): String =
        "Voy en moto${if (dest.isNotBlank()) " a $dest" else ""}. Aquí puedes ver por dónde voy y a qué hora llego, en vivo:\n${link(token)}\n\nSi me caigo, Rodada te avisa con mi ubicación."
}
