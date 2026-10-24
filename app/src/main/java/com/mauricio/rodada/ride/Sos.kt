package com.mauricio.rodada.ride

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.mauricio.rodada.data.Group
import com.mauricio.rodada.data.P
import com.mauricio.rodada.data.Store
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Avisos de emergencia: SMS a los contactos y alerta al grupo con la ubicación. */
object Sos {

    fun mapsLink(p: P) = "https://maps.google.com/?q=${String.format(java.util.Locale.US, "%.6f,%.6f", p.lat, p.lon)}"

    fun canSms(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    fun text(p: P?, crash: Boolean): String {
        val prof = Store.profile.value
        val who = prof.name.ifBlank { "Tu contacto" }
        val hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val where = p?.let { "Ubicación: ${mapsLink(it)}" } ?: "No se pudo obtener su ubicación."
        val med = listOf(
            prof.blood.takeIf { it.isNotBlank() }?.let { "Sangre $it" },
            prof.allergies.takeIf { it.isNotBlank() }?.let { "Alergias: $it" },
        ).filterNotNull().joinToString(". ")
        val head = if (crash) "RODADA: posible caída de $who en moto ($hora) y no respondió a la alerta."
        else "RODADA: $who pidió ayuda desde su moto ($hora)."
        return listOf(head, where, med.takeIf { it.isNotBlank() }, "Llámale. Si no contesta, llama al 911 y da esta ubicación.").filterNotNull().joinToString("\n")
    }

    /** Manda los SMS. Devuelve a cuántos contactos se envió. */
    fun sms(ctx: Context, p: P?, crash: Boolean): Int {
        if (!canSms(ctx)) return 0
        val sm: SmsManager = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
        val msg = text(p, crash)
        var n = 0
        for (c in Store.contacts.value) {
            val num = c.phone.filter { it.isDigit() || it == '+' }
            if (num.length < 7) continue
            runCatching {
                val parts = sm.divideMessage(msg)
                sm.sendMultipartTextMessage(num, null, parts, null, null)
                n++
            }
        }
        return n
    }

    /** Alerta en el chat de todos mis grupos (la ven en el mapa en vivo y como aviso). */
    suspend fun group(p: P?, crash: Boolean) {
        val name = Store.profile.value.name.ifBlank { "Alguien" }
        val body = if (crash) "🚨 $name pudo haberse caído y no respondió. Revisa su ubicación." else "🚨 $name pide ayuda."
        val data = JSONObject().put("crash", crash)
        if (p != null) data.put("lat", p.lat).put("lon", p.lon)
        for (g in Group.groups.value) {
            Group.send(g.id, body, "sos", data)
            if (p != null) Group.liveUpdate(g.id, p, 0.0, 0.0, -1, "sos")
        }
    }
}
