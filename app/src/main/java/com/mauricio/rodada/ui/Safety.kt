package com.mauricio.rodada.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mauricio.rodada.BuildConfig
import com.mauricio.rodada.data.Contact
import com.mauricio.rodada.data.Store
import com.mauricio.rodada.data.Updater
import com.mauricio.rodada.ride.Session
import com.mauricio.rodada.ride.Sos
import kotlinx.coroutines.launch

@Composable
fun SafetyScreen() {
    val ctx = LocalContext.current
    val contacts by Store.contacts.collectAsState()
    val set by Store.settings.collectAsState()
    val prof by Store.profile.collectAsState()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var blood by remember { mutableStateOf(prof.blood) }
    var allergies by remember { mutableStateOf(prof.allergies) }
    var medical by remember { mutableStateOf(prof.medical) }
    var smsOk by remember { mutableStateOf(Sos.canSms(ctx)) }
    val smsPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsOk = it }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header("Seguridad", onBack = { Nav.back() })
        PermsCard()
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Detección de caídas", fontWeight = FontWeight.Bold)
                    Text("Golpe fuerte + la moto se detiene de golpe → alerta con cuenta regresiva", fontSize = 13.sp, color = C.text2)
                }
                Switch(set.crashDetection, { Store.saveSettings(set.copy(crashDetection = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = C.accent))
            }
            Text("Tiempo para responder", fontSize = 13.sp, color = C.text2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(45, 60, 90, 120).forEach { s -> Pill(if (s < 60) "$s s" else if (s == 60) "1 min" else if (s == 90) "1½ min" else "2 min", set.crashSeconds == s) {
                    Store.saveSettings(set.copy(crashSeconds = s)) } }
            }
            OutlineBtn("Probar la alerta (no manda nada)", Modifier.fillMaxWidth()) { Session.testCrash(ctx) }
        }
        if (!smsOk) Card {
            Text("Falta el permiso de SMS", fontWeight = FontWeight.Bold, color = C.red)
            Text("Sin él, la alerta solo llega a tu grupo. El SMS sale de tu celular y funciona aunque no tengas datos.", fontSize = 14.sp)
            BigButton("Dar permiso de SMS", height = 50.dp) { smsPerm.launch(Manifest.permission.SEND_SMS) }
        }
        SectionTitle("Contactos de emergencia")
        Card(padding = 14.dp) {
            if (contacts.isEmpty()) Text("Todavía no tienes contactos.", color = C.text2)
            contacts.forEachIndexed { i, c ->
                Row2(c.name.ifBlank { "Contacto" }, c.phone, divider = i < contacts.size - 1, trailing = {
                    IconButton(onClick = { Store.saveContacts(contacts.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Default.Delete, "Quitar", tint = C.text2) }
                })
            }
        }
        Card {
            Field(name, { name = it }, "Nombre")
            Field(phone, { phone = it }, "Teléfono (con lada)", phone = true)
            SoftButton("Agregar contacto", Modifier.fillMaxWidth(), bg = C.bg) {
                if (phone.isNotBlank()) { Store.saveContacts(contacts + Contact(name.trim(), phone.trim())); name = ""; phone = "" }
            }
        }
        SectionTitle("Ficha médica")
        Text("Aparece en la alerta y en el SMS para quien te ayude. Solo se guarda en tu celular.", fontSize = 13.sp, color = C.text2)
        Card {
            Field(blood, { blood = it }, "Tipo de sangre", placeholder = "O+")
            Field(allergies, { allergies = it }, "Alergias")
            Field(medical, { medical = it }, "Algo importante (medicinas, condiciones)", singleLine = false)
            SoftButton("Guardar ficha", Modifier.fillMaxWidth(), bg = C.bg) {
                Store.saveProfile(prof.copy(blood = blood.trim(), allergies = allergies.trim(), medical = medical.trim()))
            }
        }
        Text("Así se verá el SMS:\n\n" + Sos.text(Store.lastLocation, true), fontSize = 13.sp, color = C.text2)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val set by Store.settings.collectAsState()
    val prof by Store.profile.collectAsState()
    val upd by Updater.state.collectAsState()
    var name by remember { mutableStateOf(prof.name) }
    var homeMsg by remember { mutableStateOf("") }
    val days = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    var backupMsg by remember { mutableStateOf("") }
    var report by remember { mutableStateOf("") }
    var wipeAsk by remember { mutableStateOf(false) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { backupMsg = if (com.mauricio.rodada.data.Backup.export(ctx, uri)) "Respaldo guardado." else "No se pudo guardar el respaldo." }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        backupMsg = "Restaurando…"
        scope.launch { if (!com.mauricio.rodada.data.Backup.restore(ctx, uri)) backupMsg = "Ese archivo no es un respaldo de Rodada." }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header("Ajustes", onBack = { Nav.back() })
        Card {
            Field(name, { name = it }, "Tu nombre (así te ve el grupo)")
            SoftButton("Guardar nombre", Modifier.fillMaxWidth(), bg = C.bg) {
                Store.saveProfile(prof.copy(name = name.trim()))
                scope.launch { com.mauricio.rodada.data.Group.register() }
            }
        }
        Card {
            Toggle("Voz de navegación y avisos", "Se oye por el intercomunicador si está conectado", set.voice) { Store.saveSettings(set.copy(voice = it)) }
            Toggle("Pantalla encendida al rodar", "Si la apagas, todo sigue funcionando", set.keepScreenOn) { Store.saveSettings(set.copy(keepScreenOn = it)) }
            Text("Aviso de descanso", fontSize = 13.sp, color = C.text2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Nunca", 90 to "1½ h", 120 to "2 h", 180 to "3 h").forEach { (m, l) -> Pill(l, set.fatigueMinutes == m) { Store.saveSettings(set.copy(fatigueMinutes = m)) } }
            }
        }
        Card {
            Text("Ruta de la semana", fontWeight = FontWeight.Bold)
            Text("Qué día te llega la propuesta para animarte a rodar", fontSize = 13.sp, color = C.text2)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEachIndexed { i, d -> Pill(d, set.weeklyDay == i + 1) { Store.saveSettings(set.copy(weeklyDay = i + 1)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(9, 13, 18, 20).forEach { h -> Pill("$h:00", set.weeklyHour == h) { Store.saveSettings(set.copy(weeklyHour = h)) } }
            }
        }
        Card {
            Text("Punto de salida", fontWeight = FontWeight.Bold)
            Text(Store.home?.let { "Guardado. Las rutas sugeridas salen y regresan aquí." } ?: "Las rutas sugeridas salen de donde estés.", fontSize = 13.sp, color = C.text2)
            SoftButton("Usar mi ubicación actual", Modifier.fillMaxWidth(), bg = C.bg) {
                scope.launch {
                    val p = Loc.now(ctx)
                    if (p != null) { Store.home = p; homeMsg = "Listo." } else homeMsg = "No pude obtener tu ubicación."
                }
            }
            if (homeMsg.isNotBlank()) Text(homeMsg, fontSize = 13.sp, color = C.text2)
        }
        Card(onClick = { Nav.go(Scr.Safety) }) { Row2("Seguridad y contactos", "Caídas, SOS y ficha médica", divider = false) }
        Card(onClick = { Nav.go(Scr.Help) }) { Row2("Ayuda", "Preguntas frecuentes", divider = false) }
        Card {
            Text("Respaldo", fontWeight = FontWeight.Bold)
            Text("Guarda tus motos, bitácora con fotos, rodadas y contactos en un archivo para pasarlos a otro celular.", fontSize = 13.sp, color = C.text2)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Guardar respaldo", Modifier.weight(1f), bg = C.bg) { saveBackup.launch("Rodada-respaldo-${java.time.LocalDate.now()}.zip") }
                SoftButton("Restaurar", Modifier.weight(1f), bg = C.bg) { openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            }
            if (backupMsg.isNotBlank()) Text(backupMsg, fontSize = 13.sp, color = C.text2)
        }
        Card {
            Text("¿Algo no funciona o tienes una idea?", fontWeight = FontWeight.Bold)
            Field(report, { report = it }, "Cuéntanos qué pasó", singleLine = false)
            SoftButton("Mandar reporte", Modifier.fillMaxWidth(), bg = C.bg) {
                val t = report.trim(); if (t.isBlank()) return@SoftButton
                scope.launch {
                    val r = com.mauricio.rodada.data.Group.call("rd_crash_add", org.json.JSONObject().put("p_version", BuildConfig.VERSION_NAME)
                        .put("p_device", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).put("p_trace", "REPORTE: $t"))
                    backupMsg = if (r != null) "¡Gracias! Recibimos tu reporte." else "No se pudo mandar. Revisa tu internet."
                    if (r != null) report = ""
                }
            }
        }
        Card(onClick = { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://msantos4321-bit.github.io/rodada/privacidad.html"))) } }) {
            Row2("Aviso de privacidad y términos", "Qué datos usa Rodada y para qué", divider = false)
        }
        OutlineBtn("Borrar mis datos", Modifier.fillMaxWidth(), color = C.red) { wipeAsk = true }
        Card {
            Text("Rodada ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
            Text(when (upd) {
                is Updater.State.Checking -> "Buscando actualización…"
                is Updater.State.UpToDate -> "Tienes la versión más nueva."
                is Updater.State.Available -> "Hay versión nueva: ${(upd as Updater.State.Available).r.name}"
                is Updater.State.Failed -> (upd as Updater.State.Failed).msg
                else -> "Se actualiza sola al abrirla."
            }, fontSize = 13.sp, color = C.text2)
            SoftButton("Buscar actualización", Modifier.fillMaxWidth(), bg = C.bg) {
                scope.launch {
                    val r = Updater.check()
                    if (r != null) Updater.download(ctx, r)?.let { if (Updater.canInstall(ctx)) Updater.install(ctx, it) else Updater.openInstallPermission(ctx) }
                }
            }
            Text("Mapas © OpenStreetMap y OpenFreeMap · Rutas Valhalla · Búsqueda Photon · Clima Open-Meteo · Gasolineras Overpass.", fontSize = 11.sp, color = C.text2)
        }
        Spacer(Modifier.height(16.dp))
    }
    if (wipeAsk) Confirm("¿Borrar todos tus datos?", "Se borran tus motos, bitácora, rodadas, contactos y lo que compartiste en tus grupos. No se puede deshacer. Si quieres conservarlos, primero guarda un respaldo.", "Borrar todo", {
        scope.launch { com.mauricio.rodada.data.Group.call("rd_delete_me", org.json.JSONObject()); com.mauricio.rodada.data.Backup.wipe(ctx) }
    }, { wipeAsk = false }, danger = true)
}

@Composable
fun Toggle(title: String, sub: String, on: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = C.text2)
        }
        Switch(on, change, colors = SwitchDefaults.colors(checkedTrackColor = C.accent))
    }
}
