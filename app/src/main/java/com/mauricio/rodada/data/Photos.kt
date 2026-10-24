package com.mauricio.rodada.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Fotos: se achican antes de subirlas (≈300 KB) para que el espacio gratis dure mucho. */
object Photos {
    private const val STORAGE = "https://wnwecaapchcfhcljsvli.supabase.co/storage/v1/object/"
    private const val KEY = "sb_publishable_vnkSLd5EzN53_LBv76YxUA_QRo2u8ok"

    fun publicUrl(path: String) = "${STORAGE}public/rodada/$path"

    /** Lee una foto (galería o cámara), la endereza y la guarda como JPEG del tamaño indicado. */
    suspend fun compress(ctx: Context, uri: Uri, maxSide: Int = 1600, quality: Int = 82, into: File? = null): File? = withContext(Dispatchers.IO) {
        runCatching {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val bmp0 = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
            val rot = cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
                }
            } ?: 0f
            val scale = minOf(1f, maxSide.toFloat() / max(bmp0.width, bmp0.height))
            val m = Matrix().apply { postScale(scale, scale); postRotate(rot) }
            val bmp = Bitmap.createBitmap(bmp0, 0, 0, bmp0.width, bmp0.height, m, true)
            val out = into ?: File(ctx.cacheDir, "foto-${UUID.randomUUID()}.jpg")
            out.parentFile?.mkdirs()
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            out
        }.getOrNull()
    }

    /** Guarda una foto en la memoria de la app (bitácora, foto de la moto). */
    suspend fun keep(ctx: Context, uri: Uri, folder: String): String? =
        compress(ctx, uri, 1800, 85, File(File(ctx.filesDir, folder), "${UUID.randomUUID()}.jpg"))?.absolutePath

    /** Sube un archivo a rodada/<path> y devuelve su dirección pública. */
    suspend fun upload(file: File, path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${STORAGE}rodada/$path")
                .header("apikey", KEY).header("x-upsert", "true")
                .post(file.asRequestBody("image/jpeg".toMediaType())).build()
            Net.client.newCall(req).execute().use { r -> if (r.isSuccessful) publicUrl(path) else null }
        }.getOrNull()
    }

    /**
     * Tarjeta para compartir en WhatsApp o Instagram: la foto con los datos de la rodada abajo.
     */
    suspend fun card(ctx: Context, photo: File?, title: String, lines: List<String>): File? = withContext(Dispatchers.Default) {
        runCatching {
            val w = 1080; val h = 1350
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawColor(Color.parseColor("#F5F5F7"))
            val src = photo?.let { BitmapFactory.decodeFile(it.absolutePath) }
            if (src != null) {
                val s = max(w.toFloat() / src.width, h.toFloat() / src.height)
                val dw = src.width * s; val dh = src.height * s
                c.drawBitmap(src, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            val shade = Paint().apply { shader = LinearGradient(0f, h * 0.45f, 0f, h.toFloat(), Color.TRANSPARENT, Color.argb(215, 0, 0, 0), Shader.TileMode.CLAMP) }
            c.drawRect(0f, h * 0.45f, w.toFloat(), h.toFloat(), shade)
            val white = if (src != null) Color.WHITE else Color.parseColor("#1D1D1F")
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white; textSize = 66f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white; textSize = 42f; typeface = Typeface.DEFAULT }
            var y = h - 90f - lines.size * 58f
            c.drawText(title.take(30), 70f, y, tp)
            y += 70f
            lines.forEach { c.drawText(it, 70f, y, sp); y += 58f }
            val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D9480F") }
            c.drawRoundRect(RectF(w - 300f, 60f, w - 60f, 140f), 40f, 40f, badge)
            c.drawText("RODADA", w - 270f, 116f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 44f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })
            val f = File(ctx.cacheDir, "rodada-tarjeta-${System.currentTimeMillis()}.jpg")
            f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            f
        }.getOrNull()
    }
}

