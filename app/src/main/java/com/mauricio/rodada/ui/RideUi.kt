package com.mauricio.rodada.ui

import com.mauricio.rodada.data.Family
import androidx.compose.foundation.horizontalScroll
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mauricio.rodada.data.Geo
import com.mauricio.rodada.data.Group
import com.mauricio.rodada.data.Store
import com.mauricio.rodada.ride.CrashState
import com.mauricio.rodada.ride.Session
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

fun maneuverIcon(type: Int): ImageVector = when (type) {
    4, 5, 6 -> Icons.Default.Flag
    9, 18, 20, 23, 37 -> Icons.Default.TurnSlightRight
    10, 2 -> Icons.Default.TurnRight
    11 -> Icons.Default.TurnSharpRight
    12 -> Icons.Default.UTurnRight
    13 -> Icons.Default.UTurnLeft
    14 -> Icons.Default.TurnSharpLeft
    15, 3 -> Icons.Default.TurnLeft
    16, 19, 21, 24, 38 -> Icons.Default.TurnSlightLeft
    26, 27 -> Icons.Default.RoundaboutRight
    else -> Icons.Default.Straight
}

@Composable
fun RideScreen() {
    val ctx = LocalContext.current
    val d by Session.dash.collectAsState()
    val route by Session.route.collectAsState()
    val groups by Group.groups.collectAsState()
    var follow by remember { mutableStateOf(true) }

    if (!d.recording) {
        // Antes de salir
        var share by remember { mutableStateOf(Group.current != null) }
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header("Rodar")
            Card(padding = 0.dp) {
                RMap(Modifier.fillMaxWidth().height(260.dp), route = route?.points.orEmpty(), me = Loc.last(ctx), fitKey = route?.points?.size)
            }
            route?.let { r ->
                Card {
                    Text(r.name.ifBlank { "Ruta lista" }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${Geo.fmtKm(r.lengthM)} · ${Geo.fmtTime(r.timeS)} · ${r.curves} curvas", color = C.text2)
                    OutlineBtn("Quitar ruta (rodar libre)", Modifier.fillMaxWidth()) { Session.setRoute(null) }
                }
            } ?: Text("Sin ruta: se graba tu rodada libre. Para navegar, planea una ruta en Rutas.", color = C.text2)
            val bikes by Store.bikes.collectAsState()
            if (bikes.size > 1) Card {
                Text("¿Con qué moto vas?", fontWeight = FontWeight.Bold)
                var cur by remember { mutableStateOf(Store.currentBike?.id) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    bikes.forEach { b -> Pill(b.name, b.id == cur) { cur = b.id; Store.currentBikeId = b.id } }
                }
            }
            Store.currentBike?.let { b ->
                val left = if (b.rangeKm > 0 && b.refuelKm >= 0) b.rangeKm - (b.odometer - b.refuelKm) else null
                if (left != null) Text("Gasolina: te alcanza para ~${left.coerceAtLeast(0)} km (según tu última carga).", color = if (left < 60) C.accentText else C.text2, fontSize = 14.sp)
            }
            var family by remember { mutableStateOf(false) }
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Avisar a mi familia (Sígueme)", fontWeight = FontWeight.Bold)
                        Text("Les mandas una liga y ven por dónde vas y a qué hora llegas, sin instalar nada. Al terminar, ven «Llegó bien».", fontSize = 13.sp, color = C.text2)
                    }
                    Switch(family, { family = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.accent))
                }
            }
            familyRef.value = family
            if (groups.isNotEmpty()) Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Compartir con ${Group.current?.name ?: "el grupo"}", fontWeight = FontWeight.Bold)
                        Text("Todos ven dónde va cada quien y te avisa si alguien se queda atrás", fontSize = 13.sp, color = C.text2)
                    }
                    Switch(share, { share = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.accent))
                }
            }
            val scope0 = rememberCoroutineScope()
            BigButton("Empezar rodada", icon = Icons.Default.PlayArrow, height = 64.dp) {
                if (familyRef.value) scope0.launch {
                    val tok = Family.start(route?.name.orEmpty())
                    if (tok != null) {
                        Session.familyToken = tok
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, Family.message(tok, route?.name.orEmpty())), "Mandar a mi familia"))
                    }
                    Session.start(ctx, share)
                } else Session.start(ctx, share)
            }
            Text("Puedes apagar la pantalla o guardar el celular: la rodada, la voz y la detección de caídas siguen funcionando.", fontSize = 13.sp, color = C.text2)
            Spacer(Modifier.height(12.dp))
        }
        return
    }

    Box(Modifier.fillMaxSize().background(C.map)) {
        val riders = d.riders.map { Mark(it.p, it.color.ifBlank { "#1E6FD9" }, it.name, big = it.status == "sos") }
        RMap(Modifier.fillMaxSize(), route = route?.points.orEmpty(), segs = listOf(Seg(d.track, "#1E6FD9")).filter { route == null },
            marks = riders, me = d.location, heading = d.heading, follow = follow,
            onTap = { follow = false })

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val nav = d.nav
            if (nav != null && !nav.isTrack) {
                Row(Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(C.accent), contentAlignment = Alignment.Center) {
                        Icon(nav.maneuver?.let { maneuverIcon(it.type) } ?: Icons.Default.Straight, null, tint = Color.White, modifier = Modifier.size(42.dp))
                    }
                    Column {
                        Text(Geo.fmtKm(nav.distToManeuver), fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Text(nav.maneuver?.instruction ?: "Sigue derecho", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3A3A3C), maxLines = 2)
                    }
                }
            } else if (nav != null && nav.isTrack && nav.offRouteM > 40) {
                Chip(Icons.Default.Navigation, "Regresa al trazo: ${Geo.fmtKm(nav.offRouteM)} hacia ${cardinal(nav.backBearing)}", C.amber)
            }
            if (d.rerouting) Chip(Icons.Default.Navigation, "Recalculando…", C.blue)
            nav?.curveAhead?.let { c -> if (nav.curveDist in 0.0..600.0) Chip(Icons.Default.Warning, "Curva cerrada a la ${if (c.left) "izquierda" else "derecha"} en ${Geo.fmtKm(nav.curveDist)}", C.accentText) }
            if (d.groupAlert.isNotBlank()) Chip(Icons.Default.Groups, d.groupAlert, C.amber)
            d.sos?.let { m -> Chip(Icons.Default.Warning, m.body.replace("🚨", "").trim(), C.red) }
            if (d.notice.isNotBlank()) Chip(Icons.Default.Warning, d.notice, C.blue)
            if (d.launch.isNotBlank()) Chip(Icons.Default.Flag, d.launch, C.green)
        }

        // Volver a centrar
        if (!follow) Box(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(52.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White)
            .clickable { follow = true }, contentAlignment = Alignment.Center) { Icon(Icons.Default.MyLocation, "Centrar", tint = C.blue) }

        // Panel de abajo
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(10.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(Color.White)
            .navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${d.speed.toInt()}", fontSize = 64.sp, fontWeight = FontWeight.Black, lineHeight = 64.sp)
                    Text(if (d.paused) "en pausa" else "km/h", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (d.paused) C.accentText else C.text2)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    LeanGauge(d.lean, Modifier.width(150.dp).height(82.dp))
                    val l = d.lean
                    Text("${if (l < -1) "Izq " else if (l > 1) "Der " else ""}${abs(l).toInt()}°", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Máx: izq ${d.maxLeanL.toInt()}° · der ${d.maxLeanR.toInt()}°", fontSize = 12.sp, color = C.text2)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                val nav = d.nav
                if (nav != null) {
                    Mini(Geo.fmtKm(nav.remainingM), "Faltan", Modifier.weight(1f))
                    Mini(LocalTime.now().plusSeconds(nav.remainingS.toLong()).format(DateTimeFormatter.ofPattern("HH:mm")), "Llegada", Modifier.weight(1f))
                } else {
                    Mini(Geo.fmtKm(d.distanceM), "Recorrido", Modifier.weight(1f))
                    Mini(Geo.fmtTime(d.movingS), "En movimiento", Modifier.weight(1f))
                }
                if (d.fuelKm >= 0) Box(Modifier.weight(1f).clickable { refuelAsk.value = true }) { Mini("${d.fuelKm} km", "Gasolina", Modifier.fillMaxWidth()) }
                else Mini(String.format(Locale.US, "%.1f G", maxOf(abs(d.latG), abs(d.longG))), "Fuerza", Modifier.weight(1f))
            }
            if (d.sharingFamily) Text("Tu familia ve tu viaje en vivo", fontSize = 12.sp, color = C.green, fontWeight = FontWeight.SemiBold)
            val scope = rememberCoroutineScope()
            if (d.sharing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Gasolina", "Parada", "Peligro").forEach { q ->
                    SoftButton(q, Modifier.weight(1f), bg = C.bg, height = 50.dp) {
                        scope.launch {
                            val g = Store.groupId
                            if (q == "Peligro") d.location?.let { Group.addHazard(g, "peligro", it, "") }
                            else Group.send(g, if (q == "Gasolina") "Necesito cargar gasolina" else "Hagamos una parada", "quick",
                                d.location?.let { org.json.JSONObject().put("lat", it.lat).put("lon", it.lon) })
                        }
                    }
                }
                SoftButton("Grupo", Modifier.weight(1f), bg = C.blueSoft, color = C.blue, height = 50.dp) { Nav.go(Scr.Live) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("SOS", Modifier.weight(1f), bg = C.redSoft, color = C.red, height = 56.dp) { Session.sos(ctx) }
                SoftButton(if (d.paused) "Seguir" else "Pausa", Modifier.weight(1f), bg = C.bg, height = 56.dp) { Session.pause(ctx) }
                SoftButton("Terminar", Modifier.weight(1f), bg = C.text, color = Color.White, height = 56.dp) { Session.stop(ctx) }
            }
        }
    }
    if (refuelAsk.value) Confirm("¿Llenaste el tanque?", "Reinicio la cuenta de gasolina con el alcance de tu moto.", "Sí, lleno", { Session.refuel(ctx) }, { refuelAsk.value = false })
}

private val familyRef = mutableStateOf(false)
private val refuelAsk = mutableStateOf(false)

private fun cardinal(b: Double): String {
    if (b < 0) return "la ruta"
    val n = listOf("el norte", "el noreste", "el este", "el sureste", "el sur", "el suroeste", "el oeste", "el noroeste")
    return n[(((b + 22.5) % 360) / 45).toInt().coerceIn(0, 7)]
}

@Composable
private fun Chip(icon: ImageVector, text: String, tint: Color) {
    Row(Modifier.shadow(3.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Mini(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, fontSize = 12.sp, color = C.text2)
    }
}

/** Medidor de inclinación: media luna con la aguja en el ángulo real. */
@Composable
fun LeanGauge(lean: Double, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 12.dp.toPx()
        val r = minOf(size.width / 2, size.height) - stroke
        val c = Offset(size.width / 2, size.height - stroke / 2)
        val tl = Offset(c.x - r, c.y - r)
        val sz = Size(r * 2, r * 2)
        drawArc(Color(0xFFEDEDF0), 180f, 180f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(Color(0xFFF3B9A0), 180f, 35f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(Color(0xFFF3B9A0), 325f, 35f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        val ang = lean.coerceIn(-60.0, 60.0).toFloat() * 1.5f
        rotate(ang, c) {
            drawLine(Color(0xFF1D1D1F), c, Offset(c.x, c.y - r + 4.dp.toPx()), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(C.lean(lean), 7.dp.toPx(), c)
    }
}

/** Alerta de caída dentro de la app (también hay pantalla sobre el bloqueo). */
@Composable
fun CrashOverlay(c: CrashState) {
    val prof by Store.profile.collectAsState()
    Box(Modifier.fillMaxSize().background(Color(0xFFFFF5F5)).clickable(enabled = false) {}) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(22.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.size(72.dp).clip(CircleShape).background(C.red), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(38.dp))
            }
            if (!c.sent) {
                Text("¿Estás bien?", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("Detectamos un golpe fuerte y la moto dejó de moverse.", textAlign = TextAlign.Center, color = Color(0xFF3A3A3C), fontSize = 16.sp)
                Box(Modifier.size(180.dp).border(10.dp, C.red, CircleShape).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${c.secondsLeft}", fontSize = 64.sp, fontWeight = FontWeight.Black, color = C.red)
                        Text("segundos", fontSize = 13.sp, color = C.text2)
                    }
                }
                Text("Tienes un minuto. Si no respondes, avisamos a tus contactos y a tu grupo con tu ubicación.", textAlign = TextAlign.Center, color = Color(0xFF3A3A3C))
                BigButton("Estoy bien", color = C.green, height = 68.dp) { Session.imOk() }
                OutlineBtn("Pedir ayuda ahora", Modifier.fillMaxWidth(), color = C.red) { Session.helpNow() }
            } else {
                Text("Aviso enviado", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(c.message, textAlign = TextAlign.Center, fontSize = 16.sp)
                MedicalCard(prof)
                BigButton("Cerrar") { Session.setCrash(null) }
            }
        }
    }
}

@Composable
fun MedicalCard(p: com.mauricio.rodada.data.Profile) {
    if (p.blood.isBlank() && p.allergies.isBlank() && p.medical.isBlank()) return
    Card(Modifier.fillMaxWidth()) {
        Text("Ficha médica de ${p.name}", fontWeight = FontWeight.Bold, color = C.red)
        if (p.blood.isNotBlank()) Text("Tipo de sangre: ${p.blood}")
        if (p.allergies.isNotBlank()) Text("Alergias: ${p.allergies}")
        if (p.medical.isNotBlank()) Text(p.medical)
        Store.contacts.value.firstOrNull()?.let { Text("Contacto: ${it.name} ${it.phone}") }
    }
}
