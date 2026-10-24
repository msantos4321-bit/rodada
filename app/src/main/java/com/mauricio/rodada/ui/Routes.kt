package com.mauricio.rodada.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.mauricio.rodada.data.Fuel
import com.mauricio.rodada.data.Geo
import com.mauricio.rodada.data.Gpx
import com.mauricio.rodada.data.Group
import com.mauricio.rodada.data.Mode
import com.mauricio.rodada.data.Place
import com.mauricio.rodada.data.Route
import com.mauricio.rodada.data.RouteInfo
import com.mauricio.rodada.data.Routing
import com.mauricio.rodada.data.Store
import com.mauricio.rodada.data.Weather
import com.mauricio.rodada.ride.Session
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Lo que se planeó en la pestaña Rutas se conserva al cambiar de pestaña. */
object RoutesState {
    val loopMode = mutableStateOf(false)
    val loopMinutes = mutableStateOf(90)
    val mode = mutableStateOf(Mode.CURVAS)
    val dest = mutableStateOf<Place?>(null)
    val route = mutableStateOf<Route?>(null)
    val weather = mutableStateOf<Weather?>(null)
    val fuel = mutableStateOf<Fuel?>(null)
    val group = mutableStateOf<CatalogRoute?>(null)
}

@Composable
fun RoutesScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loop by RoutesState.loopMode
    var minutes by RoutesState.loopMinutes
    var mode by RoutesState.mode
    var dest by RoutesState.dest
    var route by RoutesState.route
    var weather by RoutesState.weather
    var fuel by RoutesState.fuel
    var groupRoute by RoutesState.group
    var me by remember { mutableStateOf(Loc.last(ctx)) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var offline by remember { mutableStateOf("") }
    val imported by Nav.importedRoute.collectAsState()

    fun extras(r: Route) {
        weather = null; fuel = null
        scope.launch { weather = RouteInfo.weather(r) }
        scope.launch { fuel = RouteInfo.fuel(r) }
    }

    fun compute() {
        job?.cancel()
        msg = ""
        job = scope.launch {
            busy = true
            val start = me ?: Loc.now(ctx).also { me = it }
            if (start == null) { msg = "No sé dónde estás. Activa la ubicación."; busy = false; return@launch }
            val gr = groupRoute
            val r = when {
                gr != null -> Routing.through(Store.home ?: start, gr.waypoints, mode, back = true)?.copy(name = gr.name)
                loop -> Routing.loop(start, minutes, mode)?.copy(name = "Paseo de ${minutes} min")
                dest != null -> Routing.route(listOf(start, dest!!.p), mode)?.copy(name = dest!!.name)
                else -> null
            }
            busy = false
            if (r == null) { if (gr != null || loop || dest != null) msg = Routing.lastError.ifBlank { "No se pudo calcular." }; return@launch }
            route = r
            extras(r)
        }
    }

    LaunchedEffect(Unit) {
        Nav.pendingRoute.value?.let { groupRoute = it; Nav.pendingRoute.value = null; loop = false; dest = null; compute() }
        if (me == null) me = Loc.now(ctx)
    }
    LaunchedEffect(imported) {
        imported?.let { route = it; groupRoute = null; dest = null; loop = false; extras(it); Nav.importedRoute.value = null }
    }
    LaunchedEffect(query) {
        if (query.length < 3) { results = emptyList(); return@LaunchedEffect }
        delay(450)
        results = Routing.search(query, me)
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val r = runCatching { ctx.contentResolver.openInputStream(uri)?.use { Gpx.read(it, "Ruta importada") } }.getOrNull()
        if (r == null) msg = "Ese archivo no tiene una ruta GPX que pueda leer." else Nav.importedRoute.value = r
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            val r = route
            RMap(Modifier.fillMaxSize(), route = r?.points.orEmpty(),
                marks = fuel?.stations?.map { Mark(it.p, "#1E7B34") }.orEmpty() + listOfNotNull(dest?.let { Mark(it.p, "#D9480F", it.name, big = true) }),
                me = me, fitKey = r?.points?.size ?: dest?.p,
                onLongPress = { p -> dest = Place("Punto en el mapa", "", p); groupRoute = null; loop = false; compute() })
            if (busy) Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = C.accent, strokeWidth = 3.dp)
                    Text(if (mode == Mode.CURVAS || mode == Mode.SUPER) "Buscando las mejores curvas…" else "Calculando ruta…", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Ir a un lugar", !loop && groupRoute == null) { loop = false; groupRoute = null }
                Pill("Paseo circular", loop) { loop = true; groupRoute = null; dest = null; route = null }
                Pill("Sugeridas", false) { Nav.go(Scr.Catalog) }
            }
            if (groupRoute != null) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(groupRoute!!.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(groupRoute!!.description, fontSize = 13.sp, color = C.text2)
                        }
                        IconButton(onClick = { groupRoute = null; route = null }) { Icon(Icons.Default.Close, "Quitar", tint = C.text2) }
                    }
                    if (groupRoute!!.needsBorder) Tag("Requiere cruzar a EE. UU. (pasaporte o visa)", C.amberSoft, C.amber)
                }
            } else if (loop) {
                Text("¿Cuánto tiempo quieres rodar?", fontWeight = FontWeight.SemiBold, color = C.text2)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 90, 120, 180, 240).forEach { m -> Pill(if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60}", minutes == m) { minutes = m } }
                }
            } else {
                Field(query, { query = it }, "¿A dónde vas?", placeholder = "Busca un lugar o deja presionado el mapa")
                results.take(6).forEach { p ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        dest = p; query = ""; results = emptyList(); compute()
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Place, null, tint = C.accentText)
                        Column { Text(p.name, fontWeight = FontWeight.SemiBold); if (p.detail.isNotBlank()) Text(p.detail, fontSize = 13.sp, color = C.text2) }
                    }
                }
                dest?.let { Text("Destino: ${it.name}", fontWeight = FontWeight.SemiBold) }
            }

            Text("Tipo de ruta", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.text2)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach { m -> Pill(m.label, mode == m) { mode = m; if (route != null || loop || dest != null || groupRoute != null) compute() } }
            }
            Text(mode.hint, fontSize = 13.sp, color = C.text2)
            if (loop && route == null) BigButton("Armar paseo", icon = Icons.Default.Search, enabled = !busy) { compute() }
            if (msg.isNotBlank()) Text(msg, color = C.red)

            route?.let { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Distancia", Geo.fmtKm(r.lengthM), Modifier.weight(1f), C.bg)
                    Stat("Tiempo", Geo.fmtTime(r.timeS), Modifier.weight(1f), C.bg)
                    Stat("Curvas", "${r.curves}", Modifier.weight(1f), C.bg)
                }
                val ft = RouteInfo.fuelText(fuel, Store.currentBike, r.lengthM / 1000)
                InfoLine(Icons.Default.LocalGasStation, ft?.first ?: "Buscando gasolineras en la ruta…", if (ft?.second == true) C.red else C.green)
                InfoLine(Icons.Default.WbSunny, weather?.let { "Clima al pasar: ${it.summary()}" } ?: "Revisando el clima en el camino…",
                    if (weather?.warn == true) C.amber else C.blue)
                if (r.isTrack) Text("Ruta importada: te guío por el trazo y te aviso si te sales.", fontSize = 13.sp, color = C.text2)
                BigButton("Iniciar navegación", icon = Icons.Default.Navigation) {
                    Session.setRoute(r)
                    Session.start(ctx, share = Group.current != null)
                    Nav.tab(Scr.Ride)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (Group.current != null) SoftButton("Proponer al grupo", Modifier.weight(1f), icon = Icons.Default.Groups) {
                        scope.launch {
                            val g = Group.current ?: return@launch
                            val s = Geo.simplify(r.points, (r.lengthM / 7).coerceAtLeast(500.0))
                            val wps = s.drop(1).dropLast(1).take(6)
                            val ok = Group.setWeekly(g.id, CatalogRoute(0, r.name.ifBlank { "Ruta propuesta" }, mode.label.lowercase(), (r.lengthM / 1000).toInt(),
                                "Ruta ${mode.label.lowercase()} de ${Geo.fmtKm(r.lengthM)} propuesta por ${Store.profile.value.name}.", wps, false, null))
                            msg = if (ok) "Listo: es la ruta de esta semana del grupo." else "No se pudo mandar al grupo."
                        }
                    }
                    SoftButton("GPX", Modifier.weight(if (Group.current != null) 0.6f else 1f), icon = Icons.Default.Share) {
                        val f = File(ctx.cacheDir, "${r.name.ifBlank { "ruta" }.replace(Regex("[^A-Za-z0-9áéíóúñÁÉÍÓÚÑ ]"), "")}.gpx")
                        f.writeText(Gpx.write(r, r.name.ifBlank { "Ruta Rodada" }))
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/gpx+xml")
                            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartir ruta"))
                    }
                }
            }
            route?.let { r ->
                OutlineBtn(offline.ifBlank { "Descargar mapa de la ruta (sin señal)" }, Modifier.fillMaxWidth()) {
                    offline = "Descargando mapa… 0%"
                    com.mauricio.rodada.data.OfflineMaps.download(ctx, r, r.name.ifBlank { "Ruta" }, { pct -> offline = "Descargando mapa… $pct%" }) { ok ->
                        offline = if (ok) "Mapa listo: lo verás aunque no haya señal" else "La ruta es muy larga para bajarla completa"
                    }
                }
            }
            OutlineBtn("Abrir un archivo GPX", Modifier.fillMaxWidth()) { pick.launch("*/*") }
            Text("Rutas: Valhalla · OpenStreetMap. Clima: Open-Meteo. Revisa el camino antes de salir.", fontSize = 11.sp, color = C.text2)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

/** Rutas sugeridas (catálogo de Baja California y San Diego), las más cercanas primero. */
@Composable
fun CatalogScreen() {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<CatalogRoute>?>(null) }
    var filter by remember { mutableStateOf("todas") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { list = Group.catalog(Store.home ?: Loc.last(ctx)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(if (WeeklyPick.on) "Elige la ruta de la semana" else "Rutas sugeridas", onBack = { WeeklyPick.on = false; Nav.back() })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("todas" to "Todas", "curvas" to "Curvas", "costa" to "Costa", "terraceria" to "Terracería", "viaje" to "Viajes").forEach { (k, l) ->
                Pill(l, filter == k) { filter = k }
            }
        }
        val l = list
        if (l == null) Text("Cargando…", color = C.text2)
        else if (l.isEmpty()) Text("No se pudieron cargar las rutas. Revisa tu internet.", color = C.text2)
        else l.filter { filter == "todas" || it.kind == filter }.forEach { r ->
            Card(onClick = {
                val g = Group.current
                if (WeeklyPick.on && g != null) scope.launch { Group.setWeekly(g.id, route = r); WeeklyPick.on = false; Nav.back() }
                else { WeeklyPick.on = false; Nav.pendingRoute.value = r; Nav.tab(Scr.Routes) }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Star, null, tint = C.orange, modifier = Modifier.size(18.dp))
                }
                Text(r.description, fontSize = 14.sp, color = Color(0xFF3A3A3C))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag("~${r.km} km")
                    Tag(kindName(r.kind), C.accentSoft, C.accentText)
                    if (r.needsBorder) Tag("Cruza a EE. UU.", C.amberSoft, C.amber)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

fun kindName(k: String) = when (k) { "curvas" -> "Curvas"; "costa" -> "Costa"; "terraceria" -> "Terracería"; "viaje" -> "Viaje"; "carretera" -> "Carretera"; else -> k.replaceFirstChar { it.uppercase() } }

