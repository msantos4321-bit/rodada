package com.mauricio.rodada.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class RideBadge(val title: String, val km: Int, val minutes: Int, val maxLean: Int, val curves: Int)
data class Comment(val id: Long, val member: String, val name: String, val color: String, val body: String, val at: String)
data class Post(
    val id: Long,
    val member: String,
    val name: String,
    val color: String,
    val avatar: String,
    val body: String,
    val photos: List<String>,
    val ride: RideBadge?,
    val at: String,
    val likes: Int,
    val liked: Boolean,
    val likers: List<String>,
    val comments: Int,
    val last: List<Pair<String, String>>,
)

/** Fotos de las rodadas: solo las ve el grupo. */
object Feed {

    private fun parse(o: JSONObject): Post {
        val ph = o.optJSONArray("photos") ?: JSONArray()
        val lk = o.optJSONArray("likers") ?: JSONArray()
        val ls = o.optJSONArray("last") ?: JSONArray()
        val r = o.optJSONObject("ride")
        return Post(
            o.optLong("id"), o.optString("member"), o.optString("name"), o.optString("color"), o.optString("photo"), o.optString("body"),
            (0 until ph.length()).map { ph.getString(it) },
            r?.let { RideBadge(it.optString("title"), it.optInt("km"), it.optInt("min"), it.optInt("lean"), it.optInt("curves")) },
            o.optString("at"), o.optInt("likes"), o.optBoolean("liked"), (0 until lk.length()).map { lk.optString(it) },
            o.optInt("comments"), (0 until ls.length()).map { ls.getJSONObject(it).let { c -> c.optString("name") to c.optString("body") } },
        )
    }

    suspend fun list(group: String, before: Long? = null): List<Post>? {
        val txt = Group.call("rd_feed", JSONObject().put("p_group", group).put("p_before", before ?: JSONObject.NULL)) ?: return null
        return runCatching { JSONArray(txt).let { a -> (0 until a.length()).map { parse(a.getJSONObject(it)) } } }.getOrNull()
    }

    /**
     * Sube las fotos (ya achicadas) y publica. [progress] recibe cuántas fotos van.
     * Devuelve null si salió bien o el mensaje de error.
     */
    suspend fun publish(group: String, body: String, files: List<File>, ride: Ride?, progress: (Int) -> Unit): String? {
        val urls = ArrayList<String>()
        files.forEachIndexed { i, f ->
            val url = Photos.upload(f, "fotos/$group/${UUID.randomUUID()}.jpg") ?: return "No se pudo subir la foto ${i + 1}. Revisa tu internet e intenta otra vez."
            urls += url
            progress(i + 1)
        }
        val r = ride?.let {
            JSONObject().put("id", it.id).put("title", it.title).put("km", (it.stats.distanceM / 1000).toInt())
                .put("min", (it.stats.movingS / 60).toInt()).put("lean", maxOf(it.stats.maxLeanL, it.stats.maxLeanR).toInt()).put("curves", it.stats.curves)
        }
        val txt = Group.call("rd_post_add", JSONObject().put("p_group", group).put("p_body", body)
            .put("p_photos", JSONArray(urls)).put("p_ride", r ?: JSONObject.NULL)) ?: return "No se pudo publicar. Revisa tu internet."
        return if ((txt.trim().toLongOrNull() ?: -1) > 0) null else "No se pudo publicar ($txt)."
    }

    suspend fun like(post: Long): Post? = Group.call("rd_like", JSONObject().put("p_post", post))
        ?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }

    suspend fun comment(post: Long, body: String): Post? = Group.call("rd_comment_add", JSONObject().put("p_post", post).put("p_body", body))
        ?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }

    suspend fun comments(post: Long): List<Comment> {
        val txt = Group.call("rd_comments", JSONObject().put("p_post", post)) ?: return emptyList()
        return runCatching {
            JSONArray(txt).let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o ->
                Comment(o.optLong("id"), o.optString("member"), o.optString("name"), o.optString("color"), o.optString("body"), o.optString("at"))
            } } }
        }.getOrDefault(emptyList())
    }

    suspend fun delete(post: Long) = Group.call("rd_post_delete", JSONObject().put("p_post", post))
}
