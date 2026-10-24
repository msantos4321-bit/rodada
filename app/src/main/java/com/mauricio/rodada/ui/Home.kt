package com.mauricio.rodada.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mauricio.rodada.data.CatalogRoute
import com.mauricio.rodada.data.Geo
import com.mauricio.rodada.data.Gpx
import com.mauricio.rodada.data.Group
import com.mauricio.rodada.data.P
import com.mauricio.rodada.data.Ride
import com.mauricio.rodada.data.Rides
import com.mauricio.rodada.data.Store
import com.mauricio.rodada.data.Updater
import com.mauricio.rodada.data.Weekly
import com.mauricio.rodada.ride.Session
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "MX")

fun fmtDate(ms: Long, pattern: String = "EEE d MMM, HH:mm"): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, ES)).replaceFirstChar { it.uppercase() }

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val prof by Store.profile.collectAsState()
    val bikes by Store.bikes.collectAsState()
    val rides by Rides.list.collectAsState()
    val groups by Group.groups.collectAsState()
    val upd by Updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    var weekly by remember { mutableStateOf<Weekly?>(null) }
    var suggestion by remember { mutableStateOf<CatalogRoute?>(null) }

    LaunchedEffect(groups) {
        val g = Group.current
        if (g != null) weekly = Group.weekly(g.id)
        else {
            val cat = Group.catalog(Store.home ?: Store.lastLocation).filter { !it.needsBorder }.take(8)
            if (cat.isNotEmpty()) suggestion = cat[(LocalDate.now().let { it.year * 100 + it.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) }) % cat.size]
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)).replaceFirstChar { it.uppercase() }, color = C.text2, fontSize = 15.sp)
                Text("Hola, ${prof.name.ifBlank { "motociclista" }}", style = MaterialTheme.typography.headlineLarge)
            }
            IconButton(onClick = { Nav.go(Scr.Settings) }) { Icon(Icons.Default.Settings, "Ajustes", tint = C.text2) }
        }

        if (upd is Updater.State.Available) {
            val r = (upd as Updater.State.Available).r
            Card(onClick = { scope.launch { Updater.download(ctx, r)?.let { if (Updater.canInstall(ctx)) Updater.install(ctx, it) else Updater.openInstallPermission(ctx) } } }) {
                Row2("Hay una versión nueva", "${r.name} · toca para actualizar", leading = { IconBadge(Icons.Default.SystemUpdate, C.blue, C.blueSoft) }, divider = false)
            }
        }
        if (upd is Updater.State.Downloading) Text("Descargando actualización… ${(upd as Updater.State.Downloading).pct}%", color = C.text2)
        if (upd is Updater.State.Ready) {
            val f = (upd as Updater.State.Ready).file
            BigButton("Instalar actualización", color = C.blue) { if (Updater.canInstall(ctx)) Updater.install(ctx, f) else Updater.openInstallPermission(ctx) }
        }

        val notice by com.mauricio.rodada.data.License.notice.collectAsState()
        if (notice.isNotBlank()) Card { Text(notice, fontSize = 15.sp) }
        PermsCard(onlyMissing = true)

        val bike = Store.currentBike
        Card(onClick = { Nav.tab(Scr.Garage) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (bike != null && (bike.photo.isNotBlank() || bike.photoUrl.isNotBlank())) BikeImage(bike, "", Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                else IconBadge(Icons.Default.TwoWheeler, size = 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Tu moto", fontSize = 13.sp, color = C.text2)
                    Text(bike?.name ?: "Agrega tu moto", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (bike != null) Text(nextService(bike), fontSize = 13.sp, color = C.text2)
                }
            }
        }

        if (bikes.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var cur by remember { mutableStateOf(Store.currentBikeId) }
            bikes.forEach { b -> Pill(b.name, b.id == (cur.ifBlank { bikes.first().id })) { cur = b.id; Store.currentBikeId = b.id } }
        }
        BigButton("Empezar rodada", icon = Icons.Default.PlayArrow, height = 64.dp) {
            Session.start(ctx, share = Group.current != null)
            Nav.tab(Scr.Ride)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickCard("Planear ruta", "Rápida, con curvas o sin casetas", Icons.Default.Route, Modifier.weight(1f)) { Nav.tab(Scr.Routes) }
            QuickCard("Paseo circular", "Dime cuánto tiempo y regreso a casa", Icons.Default.Autorenew, Modifier.weight(1f)) {
                RoutesState.loopMode.value = true; Nav.tab(Scr.Routes)
            }
        }

        // Ruta de la semana
        weekly?.let { w ->
            WeeklyCard(w) { Nav.tab(Scr.Group) }
        } ?: suggestion?.let { s ->
            Card(onClick = { Nav.pendingRoute.value = s; Nav.tab(Scr.Routes) }) {
                Tag("RUTA DE LA SEMANA", C.accent, Color.White)
                Text(s.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("~${s.km} km · ${s.description}", fontSize = 14.sp, color = C.text2)
                Text("Crea o únete a un grupo para invitar a tus amigos.", fontSize = 13.sp, color = C.accentText)
            }
        }

        Card(onClick = { Nav.go(Scr.Safety) }) {
            val n = Store.contacts.value.size
            Row2("Seguridad", if (n == 0) "Agrega contactos de emergencia" else "Detección de caídas activa · $n ${if (n == 1) "contacto" else "contactos"}",
                leading = { IconBadge(Icons.Default.HealthAndSafety, C.red, C.redSoft) }, divider = false)
        }

        SectionTitle("Últimas rodadas", action = if (rides.size > 3) "Ver todas" else null, onAction = { Nav.go(Scr.Rides) })
        if (rides.isEmpty()) Text("Aquí aparecerán tus rodadas con su mapa, inclinación y aceleración.", color = C.text2)
        else Card(padding = 12.dp) {
            rides.take(3).forEachIndexed { i, r -> RideRow(r, i < minOf(2, rides.size - 1)) }
        }
        val km = Rides.kmThisMonth()
        if (km > 0) Text("Este mes: ${km.toInt()} km en ${rides.count { java.time.Instant.ofEpochMilli(it.start).atZone(ZoneId.systemDefault()).month == LocalDate.now().month }} rodadas.", color = C.text2)
        Spacer(Modifier.height(16.dp))
    }
}

fun nextService(b: com.mauricio.rodada.data.Bike): String {
    val m = b.maint.filter { it.everyKm > 0 }.minByOrNull { it.lastKm + it.everyKm - b.odometer } ?: return "${b.odometer} km"
    val left = m.lastKm + m.everyKm - b.odometer
    return if (left <= 0) "${m.name}: ya toca (${-left} km pasado)" else "${m.name} en $left km"
}

@Composable
fun QuickCard(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier, onClick = onClick) {
        Icon(icon, null, tint = C.accentText, modifier = Modifier.size(26.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(sub, fontSize = 13.sp, color = C.text2)
    }
}

@Composable
fun RideRow(r: Ride, divider: Boolean) {
    val s = r.stats
    Row2(r.title, "${fmtDate(r.start, "d MMM")} · ${Geo.fmtKm(s.distanceM)} · ${Geo.fmtTime(s.movingS)} · ${maxOf(s.maxLeanL, s.maxLeanR).toInt()}° máx.",
        leading = {
            val bg = when { s.smooth >= 80 -> C.greenSoft; s.smooth >= 60 -> C.amberSoft; s.smooth > 0 -> C.redSoft; else -> C.bg }
            val fg = when { s.smooth >= 80 -> C.green; s.smooth >= 60 -> C.amber; s.smooth > 0 -> C.red; else -> C.text2 }
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text(if (s.smooth > 0) "${s.smooth}" else "—", color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }, divider = divider, onClick = { Nav.go(Scr.Summary(r.id)) })
}

@Composable
fun RidesScreen() {
    val all by Rides.list.collectAsState()
    val bikes by Store.bikes.collectAsState()
    var bikeF by remember { mutableStateOf<String?>(null) }
    val rides = all.filter { r -> bikeF == null || r.bikeId == bikeF || (r.bikeId.isBlank() && r.bike == bikes.firstOrNull { it.id == bikeF }?.name) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("Mis rodadas", onBack = { Nav.back() })
        if (bikes.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Todas", bikeF == null) { bikeF = null }
            bikes.forEach { b -> Pill(b.name, bikeF == b.id) { bikeF = b.id } }
        }
        val total = rides.sumOf { it.stats.distanceM }
        Text("${rides.size} rodadas · ${Geo.fmtKm(total)} en total", color = C.text2)
        if (rides.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Más larga", Geo.fmtKm(rides.maxOf { it.stats.distanceM }), Modifier.weight(1f))
            Stat("Incl. máx.", "${rides.maxOf { maxOf(it.stats.maxLeanL, it.stats.maxLeanR) }.toInt()}°", Modifier.weight(1f))
            Stat("Horas", Geo.fmtTime(rides.sumOf { it.stats.movingS }), Modifier.weight(1f))
        }
        Card(padding = 12.dp) { rides.forEachIndexed { i, r -> RideRow(r, i < rides.size - 1) } }
        Spacer(Modifier.height(16.dp))
    }
}

/** Divide la rodada en tramos del mismo color según la inclinación. */
fun leanSegments(r: Ride): List<Seg> {
    val out = ArrayList<Seg>()
    var cur = ArrayList<P>(); var color = ""
    for (pt in r.points) {
        val c = when { kotlin.math.abs(pt.lean) < 20 -> "#2F9E44"; kotlin.math.abs(pt.lean) < 35 -> "#F08C00"; else -> "#D9480F" }
        val p = P(pt.lat, pt.lon)
        if (c != color && cur.isNotEmpty()) { cur.add(p); out += Seg(cur, color); cur = arrayListOf(p) } else cur.add(p)
        color = c
    }
    if (cur.size > 1) out += Seg(cur, color)
    return out
}

@Composable
fun SummaryScreen(id: String) {
    val ctx = LocalContext.current
    var ride by remember { mutableStateOf<Ride?>(null) }
    var rename by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    LaunchedEffect(id) { ride = Rides.load(id) }
    val r = ride
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(r?.title ?: "Rodada", r?.let { fmtDate(it.start) + if (it.bike.isNotBlank()) " · ${it.bike}" else "" } ?: "", onBack = { Nav.back() }) {
            IconButton(onClick = { rename = true }) { Icon(Icons.Default.Edit, "Cambiar nombre", tint = C.text2) }
        }
        if (r == null) { Text("Cargando…", color = C.text2); return@Column }
        val s = r.stats
        Card(padding = 0.dp) {
            val segs = remember(r.id) { leanSegments(r) }
            val worst = remember(r.id) { r.points.maxByOrNull { kotlin.math.abs(it.lean) } }
            RMap(Modifier.fillMaxWidth().height(230.dp), segs = segs,
                marks = listOfNotNull(worst?.let { Mark(P(it.lat, it.lon), "#FFFFFF", "${kotlin.math.abs(it.lean).toInt()}°", big = true) }), fitKey = r.id)
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legend("#2F9E44", "0–20°"); Legend("#F08C00", "20–35°"); Legend("#D9480F", "35°+")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Distancia", Geo.fmtKm(s.distanceM), Modifier.weight(1f))
            Stat("En movimiento", Geo.fmtTime(s.movingS), Modifier.weight(1f))
            Stat("Promedio", "${s.avgMoving.toInt()} km/h", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Incl. izq / der", "${s.maxLeanL.toInt()}° / ${s.maxLeanR.toInt()}°", Modifier.weight(1f))
            Stat("Vel. máxima", "${s.maxSpeed.toInt()} km/h", Modifier.weight(1f))
            Stat("Curvas", "${s.curves}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Frenada máx.", String.format(Locale.US, "%.1f G", s.maxBrakeG), Modifier.weight(1f))
            Stat("Fuerza lateral", String.format(Locale.US, "%.1f G", s.maxLatG), Modifier.weight(1f))
            Stat("Subida", "${s.climbM.toInt()} m", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("0–60 km/h", if (s.best0to60 > 0) String.format(Locale.US, "%.1f s", s.best0to60) else "—", Modifier.weight(1f))
            Stat("0–100 km/h", if (s.best0to100 > 0) String.format(Locale.US, "%.1f s", s.best0to100) else "—", Modifier.weight(1f))
            Stat("100–0 km/h", if (s.best100to0 > 0) String.format(Locale.US, "%.1f s", s.best100to0) else "—", Modifier.weight(1f))
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val c = when { s.smooth >= 80 -> C.greenBright; s.smooth >= 60 -> C.orange; else -> C.red }
                Box(Modifier.size(60.dp).border(6.dp, if (s.smooth > 0) c else C.line, CircleShape), contentAlignment = Alignment.Center) {
                    Text(if (s.smooth > 0) "${s.smooth}" else "—", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column {
                    Text("Suavidad de manejo", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${s.curves} curvas · ${s.midCornerBrakes} frenadas a media curva", fontSize = 13.sp, color = C.text2)
                }
            }
            s.tips.forEach { Text("• $it", fontSize = 14.sp, color = Color(0xFF3A3A3C)) }
        }
        Text("La inclinación y la aceleración se calculan con los sensores y el GPS del celular: son una buena referencia, no una medición de laboratorio.",
            fontSize = 12.sp, color = C.text2)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Compartir GPX", Modifier.weight(1f), icon = Icons.Default.Share) {
                val f = File(ctx.cacheDir, "Rodada-${r.start}.gpx")
                f.writeText(Gpx.write(r))
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/gpx+xml")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartir rodada"))
            }
            SoftButton("Borrar", Modifier.weight(1f), icon = Icons.Default.Delete, color = C.red) { del = true }
        }
        if (Group.current != null) BigButton("Compartir fotos con el grupo", icon = Icons.Default.Share) { Nav.go(Scr.NewPost(r.id)) }
        Spacer(Modifier.height(16.dp))
    }
    if (rename && r != null) {
        var t by remember { mutableStateOf(r.title) }
        AlertDialog(onDismissRequest = { rename = false }, title = { Text("Nombre de la rodada") },
            text = { Field(t, { t = it }, "Nombre") },
            confirmButton = { TextButton(onClick = { Rides.rename(r.id, t.trim()); ride = Rides.load(r.id); rename = false }) { Text("Guardar", color = C.accentText) } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancelar", color = C.text2) } }, containerColor = Color.White)
    }
    if (del && r != null) Confirm("¿Borrar esta rodada?", "Se borra del celular. No se puede deshacer.", "Borrar",
        { Rides.delete(r.id); Nav.back() }, { del = false }, danger = true)
}

@Composable
private fun Legend(hex: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(C.parse(hex)))
        Text(label, fontSize = 12.sp, color = Color(0xFF3A3A3C))
    }
}
