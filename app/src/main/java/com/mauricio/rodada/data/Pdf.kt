package com.mauricio.rodada.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.time.LocalDate

/** Historial de servicio de la moto en PDF (sube el valor al venderla). */
object Pdf {
    fun history(ctx: Context, b: Bike, owner: String): File {
        val doc = PdfDocument()
        val W = 595; val H = 842; val M = 44f
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        var c = page.canvas
        var y = M
        var n = 1
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#1D1D1F") }
        val h2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#1D1D1F") }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = Color.parseColor("#3A3A3C") }
        val gray = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.parseColor("#6E6E73") }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D9480F") }

        fun newPage() {
            doc.finishPage(page); n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()); c = page.canvas; y = M
        }
        fun need(h: Float) { if (y + h > H - M) newPage() }
        fun text(t: String, p: TextPaint, width: Float = W - 2 * M, x: Float = M) {
            val l = StaticLayout.Builder.obtain(t, 0, t.length, p, width.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            need(l.height.toFloat())
            c.save(); c.translate(x, y); l.draw(c); c.restore(); y += l.height
        }

        c.drawRect(0f, 0f, W.toFloat(), 8f, accent)
        text("Historial de servicio", title)
        y += 4
        text("${b.name}${if (b.year.isNotBlank()) " · ${b.year}" else ""} · ${"%,d".format(b.odometer)} km", h2)
        text("Dueño: $owner · Generado con Rodada el ${LocalDate.now()}", gray)
        y += 14
        text("Mantenimiento al día", h2); y += 4
        b.maint.forEach { m ->
            val left = if (m.everyKm > 0) m.lastKm + m.everyKm - b.odometer else null
            text("• ${m.name}: último a los ${"%,d".format(m.lastKm)} km${if (m.lastDate.isNotBlank()) " (${m.lastDate})" else ""}" +
                (left?.let { if (it <= 0) " · ya toca" else " · siguiente en ${"%,d".format(it)} km" } ?: ""), body)
        }
        y += 14
        val entries = b.log.sortedByDescending { it.date + it.km.toString().padStart(8, '0') }
        val total = entries.sumOf { it.cost }
        text("Bitácora (${entries.size} registros${if (total > 0) " · $${"%,.0f".format(total)} invertidos" else ""})", h2); y += 6
        for (e in entries) {
            need(60f)
            c.drawRect(M, y, M + 3f, y + 14f, accent)
            text("${e.date} · ${"%,d".format(e.km)} km · ${e.type.replaceFirstChar { it.uppercase() }}", gray, x = M + 10)
            text(e.title, h2, x = M + 10)
            if (e.notes.isNotBlank()) text(e.notes, body, x = M + 10, width = W - 2 * M - 10)
            val extra = listOfNotNull(e.shop.takeIf { it.isNotBlank() }?.let { "Taller: $it" }, if (e.cost > 0) "Costo: $${"%,.0f".format(e.cost)}" else null)
            if (extra.isNotEmpty()) text(extra.joinToString(" · "), gray, x = M + 10)
            val pics = e.photos.filter { File(it).exists() }.take(3)
            if (pics.isNotEmpty()) {
                need(110f); y += 4
                var x = M + 10
                pics.forEach { path ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(path, opts)?.let { bm ->
                        val hgt = 100f; val wid = hgt * bm.width / bm.height
                        c.drawBitmap(bm, null, RectF(x, y, x + wid, y + hgt), null)
                        x += wid + 8
                    }
                }
                y += 104
            }
            y += 12
        }
        doc.finishPage(page)
        val f = File(ctx.cacheDir, "Historial-${b.name.replace(Regex("[^A-Za-z0-9]"), "")}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }
}
