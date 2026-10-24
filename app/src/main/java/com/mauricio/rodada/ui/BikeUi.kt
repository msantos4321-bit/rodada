package com.mauricio.rodada.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.mauricio.rodada.data.Bike
import com.mauricio.rodada.data.Group
import com.mauricio.rodada.data.LogEntry
import com.mauricio.rodada.data.MaintItem
import com.mauricio.rodada.data.MySpecs
import com.mauricio.rodada.data.Pdf
import com.mauricio.rodada.data.Photos
import com.mauricio.rodada.data.Recall
import com.mauricio.rodada.data.Spec
import com.mauricio.rodada.data.Specs
import com.mauricio.rodada.data.Store
import com.mauricio.rodada.data.Wiki
import com.mauricio.rodada.ride.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.Locale

/** Foto de la moto: la tuya o, si no has puesto una, la de Wikipedia. */
@Composable
fun BikeImage(b: Bike?, wiki: String = "", modifier: Modifier) {
    val model: Any? = when {
        b != null && b.photo.isNotBlank() && File(b.photo).exists() -> File(b.photo)
        b != null && b.photoUrl.isNotBlank() -> b.photoUrl
        wiki.isNotBlank() -> wiki
        else -> null
    }
    if (model != null) AsyncImage(model, b?.name, modifier.background(C.bg), contentScale = ContentScale.Crop)
    else Box(modifier.background(C.accentSoft), contentAlignment = Alignment.Center) { Icon(Icons.Default.TwoWheeler, null, tint = C.accentText, modifier = Modifier.size(56.dp)) }
}

@Composable
fun MyBikeScreen(id: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bikes by Store.bikes.collectAsState()
    val b = bikes.firstOrNull { it.id == id } ?: run { Nav.back(); return }
    var spec by remember { mutableStateOf<Spec?>(null) }
    var wiki by remember { mutableStateOf<Wiki?>(null) }
    var recalls by remember { mutableStateOf<List<Recall>?>(null) }
    var q by remember { mutableStateOf(b.name) }
    var results by remember { mutableStateOf<List<Spec>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var camUri by remember { mutableStateOf<Uri?>(null) }

    fun setPhoto(u: Uri) = scope.launch {
        val path = Photos.keep(ctx, u, "motos") ?: return@launch
        Store.upsertBike(Store.bikes.value.first { it.id == id }.copy(photo = path))
        // Copia en línea para que el grupo vea tu moto
        val url = Photos.upload(File(path), "motos/${Store.memberId}/${b.id}.jpg")
        if (url != null) {
            Store.upsertBike(Store.bikes.value.first { it.id == id }.copy(photoUrl = "$url?v=${System.currentTimeMillis()}"))
            if (Store.currentBikeId == id || Store.bikes.value.size == 1) Group.setPhoto("$url?v=${System.currentTimeMillis()}")
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { u -> setPhoto(u) } }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) camUri?.let { setPhoto(it) } }

    LaunchedEffect(b.specId) {
        spec = if (b.specId > 0) Specs.get(b.specId) else null
        if (b.specId == 0 && b.name.isNotBlank()) { results = Specs.search(b.name); searched = true }
    }
    LaunchedEffect(spec?.id, b.name) {
        wiki = Specs.wiki(spec?.title ?: b.name)
        recalls = spec?.let { Specs.recalls(it.brand, it.model, b.year) }
    }
    LaunchedEffect(q) {
        if (q.length < 3 || b.specId > 0) return@LaunchedEffect
        delay(400); results = Specs.search(q); searched = true
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header(b.name, "Mi moto", onBack = { Nav.back() })
        Box {
            BikeImage(b, wiki?.image.orEmpty(), Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(20.dp)))
            Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Galería", icon = Icons.Default.PhotoLibrary, height = 40.dp) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                SoftButton("Cámara", icon = Icons.Default.PhotoCamera, height = 40.dp) {
                    val f = File(ctx.cacheDir, "moto-${System.currentTimeMillis()}.jpg")
                    val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f); camUri = u; cam.launch(u)
                }
            }
        }
        if (b.photo.isBlank() && wiki?.image?.isNotBlank() == true) Text("Foto de referencia (Wikipedia). Pon una foto de tu moto: también la verá tu grupo.", fontSize = 12.sp, color = C.text2)

        val s = spec
        if (s == null) {
            Card {
                Text("¿Cuál es tu moto?", fontWeight = FontWeight.Bold)
                Text("Elige el modelo para ver lo que necesita: aceite, llantas, presión, bujía y cada cuándo darle servicio.", fontSize = 14.sp, color = C.text2)
                Field(q, { q = it }, "Marca y modelo", placeholder = "Ej. Yamaha MT-07")
                results.forEach { r ->
                    Row2(r.title, listOf(r.years, r.kind).filter { it.isNotBlank() }.joinToString(" · "), divider = r != results.last(), onClick = {
                        Store.upsertBike(b.copy(specId = r.id)); msg = ""
                    })
                }
                if (searched && results.isEmpty() && q.length >= 3) {
                    Text("Todavía no tengo la ficha de «$q».", color = C.text2)
                    SoftButton("Pedir la ficha de mi moto", Modifier.fillMaxWidth(), bg = C.bg) {
                        scope.launch { Specs.request("$q ${b.year}".trim()); msg = "Listo, se pidió la ficha de tu moto. Mientras, escribe tus datos con «Mis datos»." }
                    }
                }
                if (msg.isNotBlank()) Text(msg, color = C.green, fontSize = 14.sp)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Motor", s.engine.ifBlank { "—" }, Modifier.weight(1.4f))
                Stat("Potencia", s.power.ifBlank { "—" }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Peso", s.weight.ifBlank { "—" }, Modifier.weight(1f))
                Stat("Tanque", s.fuelL?.let { "${fmtNum(it)} L" } ?: "—", Modifier.weight(1f))
                Stat("Tipo", s.kind.ifBlank { "—" }, Modifier.weight(1f))
            }
        }

        SectionTitle("Lo que necesita tu moto", action = "Mis datos", onAction = { edit = true })
        val m = b.mine
        Card(padding = 14.dp) {
            Need("Aceite", pick2(m.oil, s?.oil), pick2(m.oilL, s?.oilL)?.let { "Cantidad: $it" })
            Need("Llantas", listOfNotNull(pick2(m.tireF, s?.tireF)?.let { "Del. $it" }, pick2(m.tireR, s?.tireR)?.let { "Tras. $it" }).joinToString(" · ").ifBlank { null }, null)
            val pf = pick2(m.psiF, s?.psiF?.toString()); val pr = pick2(m.psiR, s?.psiR?.toString())
            Need("Presión (solo, en frío)", if (pf != null || pr != null) "Del. ${pf ?: "—"} psi · Tras. ${pr ?: "—"} psi" else null,
                if (pf != null && pr != null) "Con pasajero o equipaje sube según el manual" else null)
            Need("Bujía", pick2(m.plug, s?.plug), null)
            Need("Transmisión", pick2(m.chain, s?.chain)?.let { if (it.first().isDigit()) "Cadena $it" else it }, null)
            Need("Cambio de aceite", s?.oilKm?.let { "Cada ${"%,d".format(it)} km o una vez al año" }, null)
            Need("Revisión de válvulas", s?.valvesKm?.let { "Cada ${"%,d".format(it)} km" }, null, last = true)
        }
        if (s != null && (s.oilKm != null || s.valvesKm != null)) SoftButton("Usar estos intervalos en mi mantenimiento", Modifier.fillMaxWidth()) {
            var items = b.maint
            s.oilKm?.let { k -> items = items.map { if (it.name.startsWith("Aceite")) it.copy(everyKm = k, everyMonths = 12) else it } }
            s.valvesKm?.let { k -> if (items.none { it.name.contains("válvulas") }) items = items + MaintItem(name = "Revisión de válvulas", everyKm = k, lastKm = b.odometer)
                else items = items.map { if (it.name.contains("válvulas")) it.copy(everyKm = k) else it } }
            Store.upsertBike(b.copy(maint = items)); msg = "Listo: tu mantenimiento usa los intervalos de la ficha."
        }
        if (msg.isNotBlank() && s != null) Text(msg, color = C.green, fontSize = 14.sp)
        if (s != null) Text(if (s.verified) "Ficha verificada con el manual." else "Datos de referencia del fabricante: confírmalos con tu manual o la etiqueta de la moto. Lo que escribas en «Mis datos» manda sobre la ficha.",
            fontSize = 12.sp, color = C.text2)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Bitácora (${b.log.size})", Modifier.weight(1f), icon = Icons.Default.MenuBook) { Nav.go(Scr.Log(b.id)) }
            SoftButton("Antes de salir", Modifier.weight(1f), icon = Icons.Default.CheckCircle) { Nav.go(Scr.Checklist(b.id)) }
        }

        if (s != null && s.tips.isNotBlank()) { SectionTitle("Consejos"); Card { Text(s.tips, fontSize = 15.sp) } }
        if (s != null && s.mods.isNotBlank()) {
            SectionTitle("Mejoras que más le ponen")
            Card { s.mods.trimEnd('.').split(", ").forEach { Text("• ${it.replaceFirstChar { c -> c.uppercase() }}", fontSize = 15.sp) } }
        }
        recalls?.let { rc ->
            SectionTitle("Llamados a revisión")
            Card {
                if (rc.isEmpty()) Text(if (b.year.length == 4) "No hay llamados a revisión registrados en EE. UU. para ${s?.title} ${b.year}." else "Escribe el año de tu moto (Garaje → Editar) para revisar llamados a revisión.", color = C.text2, fontSize = 14.sp)
                rc.forEach { r ->
                    Text("${r.component} · ${r.date}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(r.summary, fontSize = 13.sp)
                    if (r.remedy.isNotBlank()) Text("Solución: ${r.remedy}", fontSize = 13.sp, color = C.text2)
                }
                if (rc.isNotEmpty()) Text("Fuente: NHTSA (EE. UU.), en inglés. Pregunta en tu agencia si aplica a tu número de serie.", fontSize = 12.sp, color = C.text2)
            }
        }
        wiki?.takeIf { it.extract.isNotBlank() }?.let { w ->
            SectionTitle("Sobre esta moto")
            Card(onClick = { if (w.url.isNotBlank()) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(w.url))) } }) {
                Text(w.extract, fontSize = 14.sp, maxLines = 8)
                Text("Wikipedia ›", color = C.accentText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        if (s != null) OutlineBtn("No es mi modelo, cambiar", Modifier.fillMaxWidth()) { Store.upsertBike(b.copy(specId = 0)); q = b.name }
        Spacer(Modifier.height(16.dp))
    }

    if (edit) {
        val f = remember { mutableStateListOf(b.mine.oil, b.mine.oilL, b.mine.tireF, b.mine.tireR, b.mine.psiF, b.mine.psiR, b.mine.plug, b.mine.chain) }
        val labels = listOf("Aceite (ej. 10W-40)", "Cantidad de aceite", "Llanta delantera", "Llanta trasera", "Presión del. (psi)", "Presión tras. (psi)", "Bujía", "Cadena o banda")
        AlertDialog(onDismissRequest = { edit = false }, title = { Text("Mis datos") }, containerColor = Color.White,
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lo que diga tu manual o la etiqueta de tu moto. Déjalo vacío para usar la ficha.", fontSize = 13.sp, color = C.text2)
                labels.forEachIndexed { i, l -> Field(f[i], { f[i] = it }, l, number = i in 4..5) }
            } },
            confirmButton = { TextButton(onClick = {
                Store.upsertBike(b.copy(mine = MySpecs(oil = f[0].trim(), oilL = f[1].trim(), tireF = f[2].trim(), tireR = f[3].trim(),
                    psiF = f[4].trim(), psiR = f[5].trim(), plug = f[6].trim(), chain = f[7].trim(), notes = b.mine.notes)))
                edit = false
            }) { Text("Guardar", color = C.accentText) } },
            dismissButton = { TextButton(onClick = { edit = false }) { Text("Cancelar", color = C.text2) } })
    }
}

private fun pick2(mine: String, ref: String?): String? = mine.ifBlank { ref }?.takeIf { it.isNotBlank() && it != "null" }
fun fmtNum(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)

@Composable
private fun Need(label: String, value: String?, sub: String?, last: Boolean = false) {
    Row2(label, listOfNotNull(value ?: "Consulta tu manual", sub).joinToString("\n"), divider = !last)
}

/** Revisión rápida antes de rodar (T-CLOCS de los cursos de manejo). */
@Composable
fun ChecklistScreen(id: String) {
    val ctx = LocalContext.current
    val b = Store.bikes.value.firstOrNull { it.id == id }
    var spec by remember { mutableStateOf<Spec?>(null) }
    LaunchedEffect(id) { b?.specId?.takeIf { it > 0 }?.let { spec = Specs.get(it) } }
    val pf = b?.mine?.psiF?.ifBlank { null } ?: spec?.psiF?.toString()
    val pr = b?.mine?.psiR?.ifBlank { null } ?: spec?.psiR?.toString()
    val items = listOf(
        "Llantas" to "Presión en frío${if (pf != null) ": del. $pf psi, tras. $pr psi" else ""}; sin clavos ni cortes; dibujo suficiente",
        "Frenos" to "Palancas firmes, líquido en nivel, pastillas con material",
        "Luces y claxon" to "Alta, baja, freno, direccionales y claxon",
        "Aceite y fugas" to "Nivel en la mirilla y nada goteando bajo la moto",
        "Cadena" to "Tensión correcta y lubricada; sin eslabones duros",
        "Tornillería y espejos" to "Nada flojo; espejos bien puestos",
        "Equipo" to "Casco, guantes, chamarra; documentos y licencia",
        "Para el camino" to "Tanque lleno, kit de ponchadura, agua y celular cargado",
    )
    val done = remember { mutableStateListOf<Int>() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("Antes de salir", b?.name.orEmpty(), onBack = { Nav.back() })
        Text("Dos minutos que evitan sustos. Toca cada punto cuando lo revises.", color = C.text2)
        Card(padding = 14.dp) {
            items.forEachIndexed { i, (t, d) ->
                val ok = i in done
                Row2(t, d, leading = {
                    Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (ok) C.greenBright else Color(0xFFC7C7CC), modifier = Modifier.size(28.dp))
                }, divider = i < items.size - 1, onClick = { if (ok) done.remove(i) else done.add(i) })
            }
        }
        if (done.size == items.size) Text("¡Todo listo!", color = C.green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        BigButton("Empezar rodada", icon = Icons.Default.PlayArrow, enabled = done.size == items.size) {
            if (Session.start(ctx, Group.current != null)) Nav.tab(Scr.Ride)
        }
        OutlineBtn("Salir sin revisar", Modifier.fillMaxWidth()) { if (Session.start(ctx, Group.current != null)) Nav.tab(Scr.Ride) }
        Spacer(Modifier.height(16.dp))
    }
}

private val TYPES = listOf("mantenimiento" to "Mantenimiento", "reparacion" to "Reparación", "modificacion" to "Modificación", "accesorio" to "Accesorio", "revision" to "Revisión")
fun typeName(t: String) = TYPES.firstOrNull { it.first == t }?.second ?: t.replaceFirstChar { it.uppercase() }

@Composable
fun LogScreen(id: String) {
    val ctx = LocalContext.current
    val bikes by Store.bikes.collectAsState()
    val b = bikes.firstOrNull { it.id == id } ?: run { Nav.back(); return }
    var filter by remember { mutableStateOf("todo") }
    val list = b.log.filter { filter == "todo" || it.type == filter }.sortedWith(compareByDescending<LogEntry> { it.date }.thenByDescending { it.km })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("Bitácora", b.name, onBack = { Nav.back() }) {
            IconButton(onClick = { Nav.go(Scr.LogEdit(b.id, null)) }) { Icon(Icons.Default.Add, "Agregar", tint = C.accentText) }
        }
        val maintCost = b.log.filter { it.type in listOf("mantenimiento", "reparacion", "revision") }.sumOf { it.cost }
        val modCost = b.log.filter { it.type in listOf("modificacion", "accesorio") }.sumOf { it.cost }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Registros", "${b.log.size}", Modifier.weight(1f))
            Stat("Servicio", "$${"%,.0f".format(maintCost)}", Modifier.weight(1f))
            Stat("Mejoras", "$${"%,.0f".format(modCost)}", Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Todo", filter == "todo") { filter = "todo" }
            TYPES.forEach { (k, l) -> Pill(l, filter == k) { filter = k } }
        }
        if (list.isEmpty()) Text("Anota cada servicio, reparación o mejora con fotos: así sabes qué se le hizo y cuándo, y vale más si la vendes.", color = C.text2)
        list.forEach { e ->
            Card(onClick = { Nav.go(Scr.LogEdit(b.id, e.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(typeName(e.type), if (e.type in listOf("modificacion", "accesorio")) C.blueSoft else C.accentSoft, if (e.type in listOf("modificacion", "accesorio")) C.blue else C.accentText)
                    Spacer(Modifier.weight(1f))
                    Text("${fmtDay(e.date)} · ${"%,d".format(e.km)} km", fontSize = 13.sp, color = C.text2)
                }
                Text(e.title.ifBlank { typeName(e.type) }, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (e.notes.isNotBlank()) Text(e.notes, fontSize = 14.sp, maxLines = 3, color = Color(0xFF3A3A3C))
                if (e.photos.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.photos.forEachIndexed { i, p -> AsyncImage(File(p), null, Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).clickable {
                        FeedState.viewer = e.photos.map { File(it) }; FeedState.viewerIndex = i; Nav.go(Scr.PhotoView) }, contentScale = ContentScale.Crop) }
                }
                val extra = listOfNotNull(e.shop.takeIf { it.isNotBlank() }, if (e.cost > 0) "$${"%,.0f".format(e.cost)}" else null)
                if (extra.isNotEmpty()) Text(extra.joinToString(" · "), fontSize = 13.sp, color = C.text2)
            }
        }
        BigButton("Agregar a la bitácora", icon = Icons.Default.Add) { Nav.go(Scr.LogEdit(b.id, null)) }
        if (b.log.isNotEmpty()) SoftButton("Historial en PDF (para vender o la agencia)", Modifier.fillMaxWidth(), icon = Icons.Default.PictureAsPdf) {
            val f = Pdf.history(ctx, b, Store.profile.value.name)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartir historial"))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun LogEditScreen(bikeId: String, entryId: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val b = Store.bikes.value.firstOrNull { it.id == bikeId } ?: run { Nav.back(); return }
    val old = b.log.firstOrNull { it.id == entryId }
    var type by remember { mutableStateOf(old?.type ?: "mantenimiento") }
    var title by remember { mutableStateOf(old?.title.orEmpty()) }
    var date by remember { mutableStateOf(old?.date ?: LocalDate.now().toString()) }
    var km by remember { mutableStateOf((old?.km ?: b.odometer).toString()) }
    var cost by remember { mutableStateOf(old?.cost?.takeIf { it > 0 }?.let { fmtNum(it) }.orEmpty()) }
    var shop by remember { mutableStateOf(old?.shop.orEmpty()) }
    var notes by remember { mutableStateOf(old?.notes.orEmpty()) }
    val photos = remember { mutableStateListOf<String>().apply { addAll(old?.photos.orEmpty()) } }
    var resets by remember { mutableStateOf(setOf<String>()) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { list ->
        scope.launch { busy = true; list.forEach { u -> Photos.keep(ctx, u, "bitacora")?.let { photos.add(it) } }; busy = false }
    }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) camUri?.let { u -> scope.launch { busy = true; Photos.keep(ctx, u, "bitacora")?.let { photos.add(it) }; busy = false } }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(if (old == null) "Nuevo registro" else "Editar registro", b.name, onBack = { Nav.back() })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TYPES.forEach { (k, l) -> Pill(l, type == k) { type = k } }
        }
        Field(title, { title = it }, "¿Qué se hizo?", placeholder = if (type in listOf("modificacion", "accesorio")) "Ej. Escape Akrapovič, defensas" else "Ej. Cambio de aceite y filtro")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(date, { date = it }, "Fecha (AAAA-MM-DD)", Modifier.weight(1f))
            Field(km, { km = it.filter(Char::isDigit) }, "Kilometraje", Modifier.weight(1f), number = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(cost, { cost = it }, "Costo ($)", Modifier.weight(1f), number = true)
            Field(shop, { shop = it }, "Taller o tienda", Modifier.weight(1f))
        }
        Field(notes, { notes = it }, "Detalles (marcas, números de parte, lo que notaste)", singleLine = false)
        if (type in listOf("mantenimiento", "revision", "reparacion")) {
            Text("¿Cuenta como alguno de estos? (se reinicia su aviso)", fontSize = 13.sp, color = C.text2, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                b.maint.forEach { mi -> Pill(mi.name, mi.id in resets) { resets = if (mi.id in resets) resets - mi.id else resets + mi.id } }
            }
        }
        Text("Fotos (ticket, piezas, antes y después)", fontSize = 13.sp, color = C.text2, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Galería", Modifier.weight(1f), icon = Icons.Default.PhotoLibrary) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            SoftButton("Cámara", Modifier.weight(1f), icon = Icons.Default.PhotoCamera) {
                val f = File(ctx.cacheDir, "bit-${System.currentTimeMillis()}.jpg")
                val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f); camUri = u; cam.launch(u)
            }
        }
        if (photos.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEach { p ->
                Box {
                    AsyncImage(File(p), null, Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape).background(Color(0xAA000000)).clickable { photos.remove(p) },
                        contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, "Quitar", tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
            }
        }
        if (busy) Text("Guardando fotos…", color = C.text2)
        if (err.isNotBlank()) Text(err, color = C.red)
        BigButton("Guardar", enabled = !busy) {
            if (runCatching { LocalDate.parse(date.trim()) }.isFailure) { err = "La fecha debe ser AAAA-MM-DD, por ejemplo ${LocalDate.now()}."; return@BigButton }
            val k = km.toIntOrNull() ?: b.odometer
            val e = LogEntry(old?.id ?: java.util.UUID.randomUUID().toString(), date.trim(), k, type, title.trim(), notes.trim(),
                cost.replace(',', '.').replace("$", "").toDoubleOrNull() ?: 0.0, shop.trim(), photos.toList())
            val cur = Store.bikes.value.first { it.id == b.id }
            val log = if (old == null) cur.log + e else cur.log.map { if (it.id == e.id) e else it }
            val maint = cur.maint.map { if (it.id in resets) it.copy(lastKm = k, lastDate = date.trim()) else it }
            Store.upsertBike(cur.copy(log = log, maint = maint, odometer = maxOf(cur.odometer, k)))
            Nav.back()
        }
        if (type in listOf("modificacion", "accesorio") && photos.isNotEmpty() && Group.current != null)
            SoftButton("Presumirla al grupo", Modifier.fillMaxWidth(), icon = Icons.Default.Share) {
                FeedState.prefill = photos.map { Uri.fromFile(File(it)) } to "Nueva mejora: ${title.trim()}"
                Nav.go(Scr.NewPost(null))
            }
        if (old != null) OutlineBtn("Borrar registro", Modifier.fillMaxWidth(), color = C.red) { del = true }
        Spacer(Modifier.height(16.dp))
    }
    if (del && old != null) Confirm("¿Borrar este registro?", "Se borra con sus fotos.", "Borrar", {
        old.photos.forEach { runCatching { File(it).delete() } }
        val cur = Store.bikes.value.first { it.id == b.id }
        Store.upsertBike(cur.copy(log = cur.log.filterNot { it.id == old.id })); Nav.back()
    }, { del = false }, danger = true)
}

