package com.mauricio.rodada.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.mauricio.rodada.MainActivity
import com.mauricio.rodada.data.Geo
import com.mauricio.rodada.data.Group
import com.mauricio.rodada.data.GroupPlace
import com.mauricio.rodada.data.Hazard
import com.mauricio.rodada.data.KmRow
import com.mauricio.rodada.data.Live
import com.mauricio.rodada.data.Msg
import com.mauricio.rodada.data.P
import com.mauricio.rodada.data.Rides
import com.mauricio.rodada.data.Store
import com.mauricio.rodada.data.Weekly
import com.mauricio.rodada.ride.Navigator
import com.mauricio.rodada.ride.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val ES = Locale("es", "MX")

fun openMaps(ctx: android.content.Context, p: P, label: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}(${Uri.encode(label)})")))
    }
}

fun whenText(i: Instant?): String = i?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("EEEE d 'a las' HH:mm", ES))?.replaceFirstChar { it.uppercase() } ?: "Por definir"

@Composable
fun WeeklyCard(w: Weekly, rsvp: ((String) -> Unit)? = null, onOpen: () -> Unit) {
    Card(padding = 0.dp, onClick = onOpen) {
        Row(Modifier.fillMaxWidth().background(C.accent).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("RUTA DE LA SEMANA", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (w.source == "grupo") "Propuesta del grupo" else "Sugerida por Rodada", color = Color.White, fontSize = 13.sp)
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(w.route.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("~${w.route.km} km · ${w.route.description}", fontSize = 14.sp, color = Color(0xFF3A3A3C))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(whenText(w.startAt) + if (w.meet.isNotBlank()) " · ${w.meet}" else "")
                Tag("${w.answers.count { it.answer == "voy" }} van", C.greenSoft, C.green)
                if (w.route.needsBorder) Tag("Cruza a EE. UU.", C.amberSoft, C.amber)
            }
            if (rsvp != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("voy" to "Voy", "quiza" to "Tal vez", "no" to "No puedo").forEach { (k, l) ->
                    SoftButton(l, Modifier.weight(1f), bg = if (w.mine == k) C.accent else C.bg, color = if (w.mine == k) Color.White else C.text, height = 46.dp) { rsvp(k) }
                }
            }
        }
    }
}

@Composable
fun GroupScreen(act: MainActivity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val groups by Group.groups.collectAsState()
    var weekly by remember { mutableStateOf<Weekly?>(null) }
    var msgs by remember { mutableStateOf<List<Msg>>(emptyList()) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    var gid by remember { mutableStateOf(Store.groupId) }
    val g = groups.firstOrNull { it.id == gid } ?: groups.firstOrNull()

    LaunchedEffect(Unit) { Group.refresh() }
    LaunchedEffect(g?.id) {
        val id = g?.id ?: return@LaunchedEffect
        weekly = Group.weekly(id)
        msgs = Group.messages(id, 0).orEmpty()
    }

    if (g == null) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf(act.pendingJoin.value.orEmpty()) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Header("Tu grupo")
            Text("Arma tu grupo de amigos: cada semana les llega la misma ruta propuesta, se ponen de acuerdo en el chat y en la rodada todos ven dónde va cada quien.", color = C.text2)
            Card {
                Text("Unirme a un grupo", fontWeight = FontWeight.Bold)
                Field(code, { code = it.uppercase().take(8) }, "Código del grupo", placeholder = "Ej. K7PX2Q")
                BigButton(if (busy) "Entrando…" else "Unirme", height = 52.dp, enabled = code.length >= 6 && !busy) {
                    busy = true; scope.launch { err = Group.join(code).orEmpty(); busy = false; gid = Store.groupId; act.pendingJoin.value = null }
                }
            }
            Card {
                Text("Crear un grupo nuevo", fontWeight = FontWeight.Bold)
                Field(name, { name = it }, "Nombre del grupo", placeholder = "Ej. Los de la Rumorosa")
                BigButton(if (busy) "Creando…" else "Crear grupo", height = 52.dp, enabled = name.isNotBlank() && !busy) {
                    busy = true; scope.launch { err = Group.create(name.trim(), Store.home ?: Loc.now(ctx)).orEmpty(); busy = false; gid = Store.groupId }
                }
            }
            if (err.isNotBlank()) Text(err, color = C.red)
        }
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(g.name, "Grupo · código ${g.code}") {
            SoftButton("Mapa en vivo", bg = C.card, height = 44.dp) { Nav.go(Scr.Live) }
        }
        if (groups.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { x -> Pill(x.name, x.id == g.id) { gid = x.id; Store.groupId = x.id } }
        }
        weekly?.let { w ->
            WeeklyCard(w, rsvp = { a -> scope.launch { Group.rsvp(g.id, a); weekly = Group.weekly(g.id) } }) { Nav.go(Scr.Weekly) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Ver la ruta", Modifier.weight(1f), icon = Icons.Default.Map) { Nav.pendingRoute.value = w.route; Nav.tab(Scr.Routes) }
                SoftButton("Hora y salida", Modifier.weight(1f)) { Nav.go(Scr.Weekly) }
            }
        } ?: Text("Cargando la ruta de la semana…", color = C.text2)

        Card(onClick = { Nav.go(Scr.Chat) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Chat, null, tint = C.accentText)
                Text("  Chat del grupo", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Abrir", color = C.accentText, fontWeight = FontWeight.SemiBold)
            }
            msgs.takeLast(3).forEach { m -> Text(msgLine(m), fontSize = 14.sp, color = Color(0xFF3A3A3C), maxLines = 2) }
            if (msgs.isEmpty()) Text("Escribe para ponerse de acuerdo.", color = C.text2, fontSize = 14.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickCard("Comunidad", "Kilómetros del mes, logros y peligros", Icons.Default.EmojiEvents, Modifier.weight(1f)) { Nav.go(Scr.Community) }
            QuickCard("Lugares", "Cafés, talleres y miradores del grupo", Icons.Default.Place, Modifier.weight(1f)) { Nav.go(Scr.Places) }
        }
        Card {
            Row2("Ángel guardián", "Si alguien del grupo se cae o pide ayuda, todos reciben la alerta con su ubicación.",
                leading = { IconBadge(Icons.Default.Shield, C.red, C.redSoft) }, divider = false)
        }
        SectionTitle("Integrantes (${g.members.size})")
        Card(padding = 14.dp) {
            g.members.forEachIndexed { i, m ->
                Row2(m.name.ifBlank { "Sin nombre" } + if (m.id == Store.memberId) " (tú)" else "", listOf(m.bike, if (m.role == "admin") "creó el grupo" else "").filter { it.isNotBlank() }.joinToString(" · "),
                    leading = { Avatar(m.name, C.parse(m.color, C.accent)) }, divider = i < g.members.size - 1)
            }
        }
        BigButton("Invitar amigos", icon = Icons.Default.PersonAdd) {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Group.invite(g)), "Invitar al grupo"))
        }
        OutlineBtn("Salir del grupo", Modifier.fillMaxWidth(), color = C.red) { leave = true }
        if (err.isNotBlank()) Text(err, color = C.red)
        Spacer(Modifier.height(16.dp))
    }
    if (leave) Confirm("¿Salir de ${g.name}?", "Puedes volver a entrar con el código ${g.code}.", "Salir", { scope.launch { Group.leave(g.id) } }, { leave = false }, danger = true)
}

fun msgLine(m: Msg): String = when (m.kind) {
    "text", "quick" -> "${m.name}: ${m.body}"
    else -> m.body
}

@Composable
fun WeeklyScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val g = Group.current
    var w by remember { mutableStateOf<Weekly?>(null) }
    var meet by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { g?.let { w = Group.weekly(it.id); meet = w?.meet.orEmpty() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header("Ruta de la semana", g?.name.orEmpty(), onBack = { Nav.back() })
        val x = w ?: run { Text("Cargando…", color = C.text2); return@Column }
        WeeklyCard(x, rsvp = { a -> scope.launch { g?.let { Group.rsvp(it.id, a); w = Group.weekly(it.id) } } }) { Nav.pendingRoute.value = x.route; Nav.tab(Scr.Routes) }
        SectionTitle("Cambiar la hora")
        val sat = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        val options = listOf(sat to 7, sat to 8, sat to 9, sat.plusDays(1) to 7, sat.plusDays(1) to 8, sat.plusDays(1) to 9)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (d, h) ->
                val inst = d.atTime(LocalTime.of(h, 0)).atZone(ZoneId.systemDefault()).toInstant()
                Pill("${if (d.dayOfWeek == DayOfWeek.SATURDAY) "Sáb" else "Dom"} $h:00", x.startAt == inst) {
                    scope.launch { g?.let { Group.setWeekly(it.id, start = inst); w = Group.weekly(it.id) } }
                }
            }
        }
        SectionTitle("Punto de salida")
        Field(meet, { meet = it }, "Dónde se juntan", placeholder = "Ej. Pemex de Playas")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Guardar", Modifier.weight(1f)) { scope.launch { g?.let { Group.setWeekly(it.id, meet = meet.trim()); w = Group.weekly(it.id); msg = "Listo, se avisó al grupo." } } }
            SoftButton("Aquí estoy", Modifier.weight(1f)) {
                scope.launch {
                    val p = Loc.now(ctx) ?: return@launch
                    g?.let { Group.setWeekly(it.id, meet = meet.trim().ifBlank { "Punto en el mapa" }, meetP = p); w = Group.weekly(it.id); msg = "Listo, se guardó tu ubicación como punto de salida." }
                }
            }
        }
        x.meetP?.let { p -> OutlineBtn("Ver punto de salida en el mapa", Modifier.fillMaxWidth()) { openMaps(ctx, p, x.meet.ifBlank { "Salida" }) } }
        SectionTitle("¿Otra ruta?")
        OutlineBtn("Elegir de las rutas sugeridas", Modifier.fillMaxWidth()) { WeeklyPick.on = true; Nav.go(Scr.Catalog) }
        Text("O planea una en Rutas y toca «Proponer al grupo».", fontSize = 13.sp, color = C.text2)
        if (msg.isNotBlank()) Text(msg, color = C.green)
        SectionTitle("Quién va")
        Card(padding = 14.dp) {
            if (x.answers.isEmpty()) Text("Nadie ha contestado todavía.", color = C.text2)
            x.answers.sortedBy { listOf("voy", "quiza", "no").indexOf(it.answer) }.forEachIndexed { i, a ->
                Row2(a.name, when (a.answer) { "voy" -> "Va"; "quiza" -> "Tal vez"; else -> "No puede" },
                    leading = { Avatar(a.name, C.parse(a.color, C.accent)) }, divider = i < x.answers.size - 1)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Cuando se abre el catálogo para cambiar la ruta de la semana. */
object WeeklyPick { var on = false }

@Composable
fun ChatScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val g = Group.current ?: run { Nav.back(); return }
    var msgs by remember { mutableStateOf<List<Msg>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(g.id) {
        while (true) {
            val after = msgs.lastOrNull()?.id ?: 0
            Group.messages(g.id, after)?.let { new -> if (new.isNotEmpty()) { msgs = (msgs + new).distinctBy { it.id }; Store.lastMsgId = maxOf(Store.lastMsgId, msgs.last().id) } }
            delay(4000)
        }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.padding(horizontal = 16.dp)) { Header("Chat", g.name, onBack = { Nav.back() }) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs, key = { it.id }) { m -> Bubble(m) { p -> openMaps(ctx, p, m.name) } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineBtn("Cambiar hora") { Nav.go(Scr.Weekly) }
            OutlineBtn("Proponer otra ruta") { WeeklyPick.on = true; Nav.go(Scr.Catalog) }
            OutlineBtn("Punto de salida") { Nav.go(Scr.Weekly) }
            OutlineBtn("Mandar mi ubicación") {
                scope.launch { Loc.now(ctx)?.let { Group.send(g.id, "Aquí estoy", "location", JSONObject().put("lat", it.lat).put("lon", it.lon)) } }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Escribe al grupo…") }, shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.accent, unfocusedBorderColor = Color(0xFFE5E5EA)), maxLines = 4)
            IconButton(onClick = {
                val t = text.trim(); if (t.isBlank()) return@IconButton
                text = ""
                scope.launch { if (Group.send(g.id, t)) Group.messages(g.id, msgs.lastOrNull()?.id ?: 0)?.let { msgs = (msgs + it).distinctBy { m -> m.id } } }
            }, modifier = Modifier.size(48.dp).clip(CircleShape).background(C.accent)) {
                Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = Color.White)
            }
        }
    }
}

@Composable
private fun Bubble(m: Msg, openLoc: (P) -> Unit) {
    val mine = m.member == Store.memberId
    val time = runCatching { java.time.OffsetDateTime.parse(m.at).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrDefault("")
    val loc = m.data?.let { d -> if (d.has("lat")) P(d.optDouble("lat"), d.optDouble("lon")) else null }
    if (m.kind in listOf("system", "rsvp", "weekly", "hazard")) {
        Text(m.body + "  $time", fontSize = 13.sp, color = C.text2, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(16.dp))
            .background(when { m.kind == "sos" -> C.redSoft; mine -> C.accent; else -> Color.White })
            .let { if (loc != null) it.clickable { openLoc(loc) } else it }
            .padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (!mine) Text(m.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = C.parse(m.color, C.blue))
            Text(m.body, fontSize = 15.sp, color = when { m.kind == "sos" -> C.red; mine -> Color.White; else -> C.text })
            if (loc != null) Text("Ver ubicación ›", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (mine) Color.White else C.accentText)
            Text(time, fontSize = 11.sp, color = if (mine) Color(0xCCFFFFFF) else C.text2)
        }
    }
}

@Composable
fun LiveScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val g = Group.current ?: run { Nav.back(); return }
    val d by Session.dash.collectAsState()
    var riders by remember { mutableStateOf<List<Live>>(emptyList()) }
    var hazards by remember { mutableStateOf<List<Hazard>>(emptyList()) }
    var me by remember { mutableStateOf(Loc.last(ctx)) }
    var hazardPick by remember { mutableStateOf(false) }
    LaunchedEffect(g.id) {
        while (true) {
            Group.live(g.id)?.let { riders = it }
            hazards = Group.hazards(g.id)
            if (!d.recording) me = Loc.last(ctx)
            delay(5000)
        }
    }
    val my = d.location ?: me
    val others = riders.filter { it.member != Store.memberId }
    Box(Modifier.fillMaxSize()) {
        RMap(Modifier.fillMaxSize(),
            route = Session.route.collectAsState().value?.points.orEmpty(),
            marks = others.map { Mark(it.p, it.color.ifBlank { "#1E6FD9" }, it.name, big = it.status == "sos") } +
                hazards.map { Mark(it.p, "#F08C00", Navigator.hazardName(it.kind)) },
            me = my, fitKey = others.size)
        Box(Modifier.statusBarsPadding().padding(12.dp)) {
            SoftButton("‹ Regresar", height = 44.dp) { Nav.back() }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(Color.White).navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                Text("En la rodada · ${others.size + if (d.sharing) 1 else 0}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(if (d.sharing) "Compartiendo tu ubicación" else "No estás compartiendo", fontSize = 13.sp, color = if (d.sharing) C.green else C.text2)
            }
            if (others.isEmpty()) Text("Nadie del grupo está rodando ahorita. Cuando empiecen una rodada compartida, aparecen aquí.", color = C.text2, fontSize = 14.sp)
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                others.sortedBy { r -> my?.let { Geo.dist(it, r.p) } ?: 0.0 }.forEachIndexed { i, r ->
                    val dist = my?.let { Geo.fmtKm(Geo.dist(it, r.p)) } ?: ""
                    val st = when { r.status == "sos" -> "¡PIDIÓ AYUDA!"; r.ageS > 120 -> "sin señal (${r.ageS / 60} min)"; r.status == "detenido" -> "detenido"; r.status == "pausa" -> "en pausa"; else -> "${r.speed.toInt()} km/h" }
                    Row2(r.name, listOf(dist, st, if (r.battery in 0..20) "batería ${r.battery}%" else "").filter { it.isNotBlank() }.joinToString(" · "),
                        leading = { Avatar(r.name, C.parse(r.color, C.blue)) }, divider = i < others.size - 1, onClick = { openMaps(ctx, r.p, r.name) })
                }
            }
            if (d.recording && !d.sharing) BigButton("Compartir mi ubicación", height = 50.dp) { Session.setShare(ctx, true) }
            if (!d.recording) BigButton("Empezar rodada compartida", height = 50.dp) { Session.start(ctx, true); Nav.tab(Scr.Ride) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Marcar peligro", Modifier.weight(1f), icon = Icons.Default.Warning, bg = C.amberSoft, color = C.amber) { hazardPick = true }
                SoftButton("SOS", Modifier.weight(1f), bg = C.redSoft, color = C.red) { Session.sos(ctx) }
            }
        }
    }
    if (hazardPick) {
        androidx.compose.material3.AlertDialog(onDismissRequest = { hazardPick = false }, title = { Text("¿Qué hay aquí?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se marca en tu ubicación y le avisa al grupo cuando pasen cerca.", fontSize = 13.sp, color = C.text2)
                    listOf("bache", "grava", "aceite", "reten", "accidente", "animal", "obra", "trafico").chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { k -> SoftButton(Navigator.hazardName(k).replaceFirstChar { it.uppercase() }, Modifier.weight(1f), bg = C.bg, height = 46.dp) {
                                hazardPick = false
                                scope.launch { (my ?: Loc.now(ctx))?.let { Group.addHazard(g.id, k, it, ""); hazards = Group.hazards(g.id) } }
                            } }
                        }
                    }
                }
            }, confirmButton = {}, dismissButton = { androidx.compose.material3.TextButton(onClick = { hazardPick = false }) { Text("Cancelar", color = C.text2) } },
            containerColor = Color.White)
    }
}

data class Badge(val title: String, val sub: String, val got: Boolean)

fun badges(): List<Badge> {
    val r = Rides.list.value
    val total = r.sumOf { it.stats.distanceM } / 1000
    val longest = r.maxOfOrNull { it.stats.distanceM / 1000 } ?: 0.0
    val maxLean = r.maxOfOrNull { maxOf(it.stats.maxLeanL, it.stats.maxLeanR) } ?: 0.0
    val early = r.any { Instant.ofEpochMilli(it.start).atZone(ZoneId.systemDefault()).hour < 7 }
    val weeks = r.map { Instant.ofEpochMilli(it.start).atZone(ZoneId.systemDefault()).toLocalDate().let { d -> d.year * 100 + d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR) } }.toSet().size
    val smooth = r.any { it.stats.smooth >= 95 && it.stats.curves >= 30 }
    return listOf(
        Badge("Primera rodada", "Graba tu primera rodada", r.isNotEmpty()),
        Badge("Cien al día", "100 km en una sola rodada", longest >= 100),
        Badge("Fondo de carretera", "300 km en una sola rodada", longest >= 300),
        Badge("Mil kilómetros", "1,000 km grabados en Rodada", total >= 1000),
        Badge("Cinco mil", "5,000 km grabados", total >= 5000),
        Badge("Rodilla al piso", "Inclinar 40° o más", maxLean >= 40),
        Badge("Madrugador", "Salir antes de las 7 am", early),
        Badge("Constante", "Rodar en 4 semanas distintas", weeks >= 4),
        Badge("Seda", "Suavidad de 95 o más con 30 curvas", smooth),
    )
}

@Composable
fun CommunityScreen() {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val g = Group.current
    var board by remember { mutableStateOf<List<KmRow>>(emptyList()) }
    var hazards by remember { mutableStateOf<List<Hazard>>(emptyList()) }
    LaunchedEffect(g?.id) { g?.let { board = Group.board(it.id); hazards = Group.hazards(it.id) } }
    val month = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM", ES))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header("Comunidad", g?.name.orEmpty(), onBack = { Nav.back() })
        Card(padding = 14.dp) {
            Text("Kilómetros de $month", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (board.isEmpty()) Text("Todavía nadie ha grabado rodadas este mes.", color = C.text2)
            board.forEachIndexed { i, k ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}", fontWeight = FontWeight.ExtraBold, color = if (i == 0) C.accent else C.text2, modifier = Modifier.widthIn(min = 22.dp))
                    Avatar(k.name, C.parse(k.color, C.accent), 28.dp)
                    Text(k.name + if (k.member == Store.memberId) " (tú)" else "", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${k.km} km · ${k.rides} rod.", fontWeight = FontWeight.Bold)
                }
            }
            Text("Se cuentan kilómetros y rodadas, nunca velocidad.", fontSize = 12.sp, color = C.text2)
        }
        SectionTitle("Tus logros")
        val bs = badges()
        bs.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { b ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (b.got) C.card else C.bg).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.EmojiEvents, null, tint = if (b.got) C.orange else Color(0xFFC7C7CC), modifier = Modifier.size(30.dp))
                        Text(b.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (b.got) C.text else C.text2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text(b.sub, fontSize = 11.sp, color = C.text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        SectionTitle("Peligros marcados")
        Card(padding = 14.dp) {
            if (hazards.isEmpty()) Text("Sin peligros activos. Márcalos desde el mapa en vivo.", color = C.text2)
            hazards.forEachIndexed { i, h ->
                Row2(Navigator.hazardName(h.kind).replaceFirstChar { it.uppercase() }, "Marcó ${h.by}", divider = i < hazards.size - 1,
                    trailing = { androidx.compose.material3.TextButton(onClick = { scope.launch { Group.clearHazard(h.id); g?.let { hazards = Group.hazards(it.id) } } }) { Text("Ya no está", color = C.accentText) } },
                    onClick = { openMaps(ctx, h.p, Navigator.hazardName(h.kind)) })
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun PlacesScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val g = Group.current ?: run { Nav.back(); return }
    var places by remember { mutableStateOf<List<GroupPlace>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("cafe") }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(g.id) { places = Group.places(g.id) }
    val kinds = listOf("cafe" to "Café", "comida" to "Comida", "taller" to "Taller", "gasolinera" to "Gasolinera", "mirador" to "Mirador", "hotel" to "Hotel")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header("Lugares del grupo", g.name, onBack = { Nav.back() })
        if (places.isNotEmpty()) Card(padding = 0.dp) {
            RMap(Modifier.fillMaxWidth().height(220.dp), marks = places.map { Mark(it.p, "#1E6FD9", it.name) }, fitKey = places.size)
        }
        Card(padding = 14.dp) {
            if (places.isEmpty()) Text("Aún no hay lugares. Agrega el café de siempre o el taller de confianza.", color = C.text2)
            places.forEachIndexed { i, p ->
                Row2(p.name, listOf(kinds.firstOrNull { it.first == p.kind }?.second ?: p.kind, p.note, "por ${p.by}").filter { it.isNotBlank() }.joinToString(" · "),
                    divider = i < places.size - 1, onClick = { openMaps(ctx, p.p, p.name) })
            }
        }
        SectionTitle("Agregar donde estoy")
        Card {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { kinds.forEach { (k, l) -> Pill(l, kind == k) { kind = k } } }
            Field(name, { name = it }, "Nombre")
            Field(note, { note = it }, "Comentario (opcional)")
            SoftButton("Guardar lugar", Modifier.fillMaxWidth(), bg = C.bg) {
                scope.launch {
                    val p = Loc.now(ctx) ?: run { msg = "No pude obtener tu ubicación."; return@launch }
                    Group.addPlace(g.id, kind, name.trim().ifBlank { kinds.first { it.first == kind }.second }, p, note.trim())
                    places = Group.places(g.id); name = ""; note = ""; msg = "Guardado para todo el grupo."
                }
            }
            if (msg.isNotBlank()) Text(msg, fontSize = 13.sp, color = C.text2)
        }
        Spacer(Modifier.height(16.dp))
    }
}
