package com.mauricio.rodada.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object Net {
    private const val UA = "Rodada/1.0 (app para motociclistas; github.com/msantos4321-bit/rodada)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .addInterceptor { ch -> ch.proceed(ch.request().newBuilder().header("User-Agent", UA).build()) }
        .build()

    val quick: OkHttpClient = client.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /** GET que devuelve el texto o null si falla. */
    suspend fun get(url: String, c: OkHttpClient = client): String? = withContext(Dispatchers.IO) {
        runCatching {
            c.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }.getOrNull()
    }

    suspend fun postForm(url: String, field: String, value: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = okhttp3.FormBody.Builder().add(field, value).build()
            client.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }.getOrNull()
    }

    suspend fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap(), c: OkHttpClient = client): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val b = Request.Builder().url(url).post(json.toRequestBody("application/json".toMediaType()))
                headers.forEach { (k, v) -> b.header(k, v) }
                c.newCall(b.build()).execute().use { r -> if (r.isSuccessful) r.body?.string().orEmpty() else null }
            }.getOrNull()
        }
}
