package com.mauricio.rodada.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mauricio.rodada.MainActivity
import com.mauricio.rodada.R
import com.mauricio.rodada.RodadaApp
import com.mauricio.rodada.data.Group
import com.mauricio.rodada.data.Store
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Avisos en segundo plano (cada 15 minutos, lo mínimo que permite Android):
 * mensajes del grupo, alertas de ayuda, la ruta de la semana, mantenimiento y papeles por vencer.
 */
class Avisos(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<Avisos>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("avisos", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun canNotify(ctx: Context) = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun open(ctx: Context, tab: String, code: Int): PendingIntent = PendingIntent.getActivity(ctx, code,
            Intent(ctx, MainActivity::class.java).putExtra("tab", tab).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun notify(ctx: Context, id: Int, channel: String, title: String, text: String, tab: String, high: Boolean = false) {
            if (!canNotify(ctx)) return
            val n = NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(if (high) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(open(ctx, tab, id)).setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
        }

        fun weekKey(): String {
            val d = LocalDate.now()
            return "${d.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR)}-${d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
        }
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        runCatching { messages(ctx) }
        runCatching { weekly(ctx) }
        runCatching { weather(ctx) }
        runCatching { garage(ctx) }
        return Result.success()
    }

    private suspend fun messages(ctx: Context) {
        if (Group.groups.value.isEmpty()) Group.refresh()
        if (Group.groups.value.isEmpty()) return
        val first = Store.lastMsgId == 0L
        val msgs = Group.inbox(Store.lastMsgId) ?: return
        if (msgs.isEmpty()) return
        Store.lastMsgId = msgs.maxOf { it.id }
        if (first) return // la primera vez solo se marca dónde vamos
        msgs.filter { it.kind == "sos" }.forEach { m ->
            notify(ctx, 5000 + (m.id % 1000).toInt(), RodadaApp.CH_ALERT, "🚨 Alerta en ${m.groupName}", m.body + "\nToca para ver su ubicación.", "grupo", high = true)
        }
        val chat = msgs.filter { it.kind != "sos" && it.kind != "system" }
        if (chat.isNotEmpty()) {
            val g = chat.last().groupName
            val text = chat.takeLast(5).joinToString("\n") { if (it.kind == "text" || it.kind == "quick") "${it.name}: ${it.body}" else it.body }
            notify(ctx, 4000, RodadaApp.CH_GROUP, if (chat.size == 1) g else "$g · ${chat.size} mensajes nuevos", text, "chat")
        }
    }

    /** La ruta de la semana llega el día y la hora que elegiste (jueves 6 pm de fábrica). */
    private suspend fun weekly(ctx: Context) {
        val s = Store.settings.value
        val now = LocalDateTime.now()
        if (now.dayOfWeek.value != s.weeklyDay || now.hour < s.weeklyHour) return
        val wk = weekKey()
        if (Store.weeklyNotified == wk) return
        val g = Group.current
        if (g != null) {
            val w = Group.weekly(g.id) ?: return
            val whenTxt = w.startAt?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("EEEE d 'a las' HH:mm", Locale("es", "MX"))) ?: "este fin"
            val van = w.answers.count { it.answer == "voy" }
            val text = "${w.route.name} · ~${w.route.km} km\n${w.route.description}\nSalida: $whenTxt${if (w.meet.isNotBlank()) " en ${w.meet}" else ""}." +
                (if (van > 0) " Ya van $van." else "") + " ¿Te apuntas?"
            notify(ctx, 4100, RodadaApp.CH_GROUP, "🏍️ Ruta de la semana del grupo", text, "grupo")
        } else {
            val near = Store.home ?: Store.lastLocation
            val cat = Group.catalog(near).filter { !it.needsBorder }.take(8)
            if (cat.isEmpty()) return
            val pick = cat[(wk.hashCode() and 0x7fffffff) % cat.size]
            notify(ctx, 4100, RodadaApp.CH_GROUP, "🏍️ Ruta de la semana: ${pick.name}", "~${pick.km} km. ${pick.description} ¿La hacemos este fin?", "rutas")
        }
        Store.weeklyNotified = wk
    }

    /** Un día antes de la rodada del grupo: aviso si se pronostica lluvia o viento fuerte en la salida. */
    private suspend fun weather(ctx: Context) {
        val g = Group.current ?: return
        val w = Group.weekly(g.id) ?: return
        val start = w.startAt ?: return
        val hours = java.time.Duration.between(java.time.Instant.now(), start).toHours()
        if (hours !in 10..36) return
        val sp = ctx.getSharedPreferences("avisos_garaje", Context.MODE_PRIVATE)
        val key = "clima-${w.week}-${start.epochSecond}"
        if (sp.getBoolean(key, false)) return
        val p = w.meetP ?: w.route.waypoints.firstOrNull() ?: return
        val txt = com.mauricio.rodada.data.Net.get("https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
            "&hourly=precipitation_probability,wind_gusts_10m,temperature_2m&timezone=auto&forecast_days=3") ?: return
        val h = org.json.JSONObject(txt).getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val target = start.atZone(ZoneId.systemDefault()).toLocalDateTime().withMinute(0).withSecond(0).withNano(0)
        var idx = -1
        for (i in 0 until times.length()) if (LocalDateTime.parse(times.getString(i)) == target) { idx = i; break }
        if (idx < 0) return
        var rain = 0; var gust = 0.0; var minT = 99.0
        for (k in idx until minOf(idx + 6, times.length())) {
            rain = maxOf(rain, h.getJSONArray("precipitation_probability").optInt(k))
            gust = maxOf(gust, h.getJSONArray("wind_gusts_10m").optDouble(k))
            minT = minOf(minT, h.getJSONArray("temperature_2m").optDouble(k))
        }
        sp.edit().putBoolean(key, true).apply()
        val parts = ArrayList<String>()
        if (rain >= 40) parts += "lluvia $rain%"
        if (gust >= 50) parts += "ráfagas de ${gust.toInt()} km/h"
        if (minT <= 6) parts += "frío (${minT.toInt()} °C)"
        if (parts.isEmpty()) return
        notify(ctx, 4200, RodadaApp.CH_GROUP, "Pronóstico para «${w.route.name}»", "A la hora de salida: ${parts.joinToString(", ")}. Platíquenlo en el chat por si cambian la hora o la ruta.", "chat")
    }

    /** Mantenimiento que ya toca y papeles que vencen en 15 días (una vez por semana cada uno). */
    private fun garage(ctx: Context) {
        val now = LocalDateTime.now()
        if (now.hour !in 9..20) return
        val sp = ctx.getSharedPreferences("avisos_garaje", Context.MODE_PRIVATE)
        val wk = weekKey()
        for (b in Store.bikes.value) {
            for (m in b.maint) {
                val kmLeft = if (m.everyKm > 0) m.lastKm + m.everyKm - b.odometer else Int.MAX_VALUE
                val monthsLeft = if (m.everyMonths > 0 && m.lastDate.isNotBlank())
                    runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(m.lastDate).plusMonths(m.everyMonths.toLong())) }.getOrDefault(Long.MAX_VALUE)
                else Long.MAX_VALUE
                val due = kmLeft <= 150 || monthsLeft <= 7
                val key = "${b.id}-${m.id}-$wk"
                if (due && !sp.getBoolean(key, false)) {
                    sp.edit().putBoolean(key, true).apply()
                    val t = when {
                        kmLeft <= 0 -> "Ya te pasaste ${-kmLeft} km"
                        kmLeft <= 150 -> "Toca en $kmLeft km"
                        monthsLeft <= 0 -> "Ya venció por tiempo"
                        else -> "Toca en $monthsLeft días"
                    }
                    notify(ctx, 3000 + (key.hashCode() and 0xfff), RodadaApp.CH_GARAGE, "${m.name} · ${b.name}", t, "garaje")
                }
            }
            for (d in b.docs) {
                if (d.expires.isBlank()) continue
                val days = runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(d.expires)) }.getOrNull() ?: continue
                val key = "${b.id}-${d.id}-$wk"
                if (days <= 15 && !sp.getBoolean(key, false)) {
                    sp.edit().putBoolean(key, true).apply()
                    notify(ctx, 3500 + (key.hashCode() and 0xfff), RodadaApp.CH_GARAGE, "${d.name} · ${b.name}",
                        if (days < 0) "Ya venció hace ${-days} días" else if (days == 0L) "Vence hoy" else "Vence en $days días", "garaje")
                }
            }
        }
    }
}
