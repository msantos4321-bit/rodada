package com.mauricio.rodada.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Lo que Rodada necesita del celular para que la detección de caídas funcione con la pantalla apagada. */
object Perms {
    data class Item(val title: String, val why: String, val ok: Boolean, val fix: () -> Unit)

    private fun has(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun appSettings(ctx: Context) = runCatching {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun list(ctx: Context, request: (Array<String>) -> Unit): List<Item> {
        val out = ArrayList<Item>()
        out += Item("Ubicación precisa", "Para grabar la rodada, navegar y mandar tu ubicación si te caes",
            has(ctx, Manifest.permission.ACCESS_FINE_LOCATION)) { request(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
        if (Build.VERSION.SDK_INT >= 33) out += Item("Notificaciones", "Avisos del grupo, ruta de la semana y mantenimiento",
            has(ctx, Manifest.permission.POST_NOTIFICATIONS)) { request(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
        out += Item("Mandar SMS", "El aviso de caída llega por SMS aunque no tengas datos",
            has(ctx, Manifest.permission.SEND_SMS)) { request(arrayOf(Manifest.permission.SEND_SMS)) }
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            out += Item("Alerta a pantalla completa", "Para que «¿Estás bien?» aparezca aunque el celular esté bloqueado", nm.canUseFullScreenIntent()) {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { appSettings(ctx) }
            }
        }
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        out += Item("Sin límite de batería", "Algunos celulares apagan las apps en el bolsillo; así Rodada no se detiene a media rodada",
            pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            runCatching {
                @Suppress("BatteryLife")
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { appSettings(ctx) }
        }
        return out
    }
}

/** Tarjeta con lo que falta activar; [onlyMissing] la esconde cuando todo está bien. */
@Composable
fun PermsCard(onlyMissing: Boolean = false) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        tick++
        if (r.values.any { !it }) Perms.appSettings(ctx)
    }
    val items = remember(tick) { Perms.list(ctx) { launcher.launch(it) } }
    val missing = items.filter { !it.ok }
    if (onlyMissing && missing.isEmpty()) return
    Card(padding = 14.dp) {
        Text(if (missing.isEmpty()) "Todo listo en tu celular" else "Falta activar ${missing.size} ${if (missing.size == 1) "cosa" else "cosas"}",
            fontWeight = FontWeight.Bold, color = if (missing.isEmpty()) C.green else C.accentText)
        (if (onlyMissing) missing else items).forEachIndexed { i, it ->
            Row2(it.title, it.why, leading = {
                Icon(if (it.ok) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if (it.ok) C.greenBright else C.accent, modifier = Modifier.size(26.dp))
            }, divider = i < (if (onlyMissing) missing.size else items.size) - 1, trailing = {
                if (!it.ok) TextButton(onClick = it.fix) { Text("Activar", color = C.accentText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            })
        }
    }
}
