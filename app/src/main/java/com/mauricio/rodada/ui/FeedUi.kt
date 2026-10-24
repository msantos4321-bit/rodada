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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import com.mauricio.rodada.data.Comment
import com.mauricio.rodada.data.Feed
import com.mauricio.rodada.data.Geo
import com.mauricio.rodada.data.Group
import com.mauricio.rodada.data.Photos
import com.mauricio.rodada.data.Post
import com.mauricio.rodada.data.Ride
import com.mauricio.rodada.data.Rides
import com.mauricio.rodada.data.Store
import kotlinx.coroutines.launch
import java.io.File
import java.time.Duration
import java.time.OffsetDateTime

/** Para pasar datos entre pantallas de fotos. */
object FeedState {
    var post = mutableStateOf<Post?>(null)
    var viewer: List<Any> = emptyList()
    var viewerIndex = 0
    val dirty = mutableStateOf(0)
    /** Fotos y texto que llegan de otra pantalla (por ejemplo, una mejora de la bitácora). */
    var prefill: Pair<List<Uri>, String>? = null
}

fun ago(iso: String): String = runCatching {
    val d = Duration.between(OffsetDateTime.parse(iso).toInstant(), java.time.Instant.now())
    when {
        d.toMinutes() < 1 -> "ahorita"
        d.toMinutes() < 60 -> "hace ${d.toMinutes()} min"
        d.toHours() < 24 -> "hace ${d.toHours()} h"
        d.toDays() < 7 -> "hace ${d.toDays()} días"
        else -> fmtDay(OffsetDateTime.parse(iso).toLocalDate().toString())
    }
}.getOrDefault("")

@Composable
fun FeedScreen() {
    val scope = rememberCoroutineScope()
    val g = Group.current ?: run { Nav.back(); return }
    var posts by remember { mutableStateOf<List<Post>?>(null) }
    var more by remember { mutableStateOf(true) }
    val dirty by FeedState.dirty
    LaunchedEffect(g.id, dirty) { posts = Feed.list(g.id); more = (posts?.size ?: 0) >= 20 }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            Header("Fotos de las rodadas", g.name, onBack = { Nav.back() }) {
                SoftButton("Publicar", icon = Icons.Default.AddAPhoto, bg = C.accent, color = Color.White, height = 44.dp) { Nav.go(Scr.NewPost(null)) }
            }
        }
        val list = posts
        when {
            list == null -> Text("Cargando…", color = C.text2, modifier = Modifier.padding(16.dp))
            list.isEmpty() -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Todavía no hay fotos. Al terminar una rodada toca «Compartir con el grupo», o publica aquí.", color = C.text2)
                BigButton("Publicar fotos", icon = Icons.Default.AddAPhoto) { Nav.go(Scr.NewPost(null)) }
            }
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(list, key = { it.id }) { p ->
                    PostCard(p, onChange = { np -> posts = posts?.map { if (it.id == np.id) np else it } }) { FeedState.post.value = p; Nav.go(Scr.PostDetail) }
                }
                if (more) item {
                    OutlineBtn("Ver más", Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        scope.launch {
                            val next = Feed.list(g.id, list.lastOrNull()?.id).orEmpty()
                            posts = list + next; more = next.size >= 20
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
fun PostCard(p: Post, onChange: (Post) -> Unit, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().background(Color.White).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (p.avatar.isNotBlank()) AsyncImage(p.avatar, null, Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            else Avatar(p.name, C.parse(p.color, C.accent), 36.dp)
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.Bold)
                Text(ago(p.at), fontSize = 12.sp, color = C.text2)
            }
        }
        p.ride?.let { r ->
            Row(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(10.dp)).background(C.accentSoft).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.TwoWheeler, null, tint = C.accentText, modifier = Modifier.size(16.dp))
                Text("${r.title} · ${r.km} km · ${Geo.fmtTime(r.minutes * 60.0)} · ${r.maxLean}° · ${r.curves} curvas", fontSize = 13.sp, color = C.accentText, fontWeight = FontWeight.SemiBold)
            }
        }
        if (p.photos.isNotEmpty()) PhotoStrip(p.photos)
        if (p.body.isNotBlank()) Text(p.body, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { scope.launch { Feed.like(p.id)?.let(onChange) } }) {
                Icon(if (p.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Me gusta", tint = if (p.liked) C.red else C.text)
            }
            Text(if (p.likes > 0) "${p.likes}" else "", fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onOpen) { Icon(Icons.Default.ChatBubbleOutline, "Comentarios", tint = C.text) }
            Text(if (p.comments > 0) "${p.comments}" else "", fontWeight = FontWeight.SemiBold)
        }
        if (p.likers.isNotEmpty()) Text("A ${p.likers.take(3).joinToString(", ")}${if (p.likers.size > 3) " y ${p.likers.size - 3} más" else ""} les gusta",
            fontSize = 13.sp, color = C.text2, modifier = Modifier.padding(horizontal = 16.dp))
        p.last.forEach { (n, b) -> Text("$n  $b", fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp), maxLines = 2) }
        if (p.comments > p.last.size) Text("Ver los ${p.comments} comentarios", color = C.text2, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp).clickable(onClick = onOpen))
    }
}

@Composable
private fun PhotoStrip(urls: List<String>) {
    if (urls.size == 1) {
        AsyncImage(urls[0], null, Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(C.bg).clickable {
            FeedState.viewer = urls; FeedState.viewerIndex = 0; Nav.go(Scr.PhotoView)
        }, contentScale = ContentScale.Crop)
        return
    }
    val pager = rememberPagerState { urls.size }
    Box {
        HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(4f / 3f)) { i ->
            AsyncImage(urls[i], null, Modifier.fillMaxSize().background(C.bg).clickable {
                FeedState.viewer = urls; FeedState.viewerIndex = i; Nav.go(Scr.PhotoView)
            }, contentScale = ContentScale.Crop)
        }
        Text("${pager.currentPage + 1}/${urls.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/** Foto en grande, deslizando entre todas. */
@Composable
fun PhotoViewScreen() {
    val items = FeedState.viewer
    val pager = rememberPagerState(FeedState.viewerIndex) { items.size }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
            AsyncImage(items[i], null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        IconButton(onClick = { Nav.back() }, modifier = Modifier.statusBarsPadding().padding(8.dp)) { Icon(Icons.Default.Close, "Cerrar", tint = Color.White) }
    }
}

@Composable
fun PostDetailScreen() {
    val scope = rememberCoroutineScope()
    var post by FeedState.post
    val p = post ?: run { Nav.back(); return }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var del by remember { mutableStateOf(false) }
    LaunchedEffect(p.id) { comments = Feed.comments(p.id) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            Header("Publicación", p.name, onBack = { Nav.back() }) {
                if (p.member == Store.memberId) IconButton(onClick = { del = true }) { Icon(Icons.Default.Delete, "Borrar", tint = C.text2) }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PostCard(p, onChange = { post = it; FeedState.dirty.value++ }) {}
            comments.forEach { c ->
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(c.name, C.parse(c.color, C.blue), 30.dp)
                    Column {
                        Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(c.body, fontSize = 15.sp)
                        Text(ago(c.at), fontSize = 11.sp, color = C.text2)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Escribe un comentario…") }, shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.accent, unfocusedBorderColor = Color(0xFFE5E5EA)), maxLines = 4)
            IconButton(onClick = {
                val t = text.trim(); if (t.isBlank()) return@IconButton
                text = ""
                scope.launch { Feed.comment(p.id, t)?.let { post = it; FeedState.dirty.value++ }; comments = Feed.comments(p.id) }
            }, modifier = Modifier.size(48.dp).clip(CircleShape).background(C.accent)) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = Color.White) }
        }
    }
    if (del) Confirm("¿Borrar esta publicación?", "Se borra para todo el grupo.", "Borrar", {
        scope.launch { Feed.delete(p.id); FeedState.dirty.value++; Nav.back() }
    }, { del = false }, danger = true)
}

@Composable
fun NewPostScreen(rideId: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val g = Group.current
    val picked = remember { mutableStateListOf<Uri>() }
    var text by remember { mutableStateOf("") }
    var ride by remember { mutableStateOf<Ride?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val rides by Rides.list.collectAsState()
    LaunchedEffect(rideId) { rideId?.let { ride = Rides.load(it) } }
    LaunchedEffect(Unit) { FeedState.prefill?.let { (u, t) -> picked.addAll(u); text = t; FeedState.prefill = null } }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { list -> picked.addAll(list.filter { it !in picked }.take(10 - picked.size)) }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) camUri?.let { picked.add(it) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header("Compartir con el grupo", g?.name.orEmpty(), onBack = { Nav.back() })
        if (g == null) { Text("Primero únete a un grupo en la pestaña Grupo.", color = C.text2); return@Column }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Galería", Modifier.weight(1f), icon = Icons.Default.PhotoLibrary) {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            SoftButton("Cámara", Modifier.weight(1f), icon = Icons.Default.PhotoCamera) {
                val f = File(ctx.cacheDir, "camara-${System.currentTimeMillis()}.jpg")
                val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                camUri = u; cam.launch(u)
            }
        }
        if (picked.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            picked.forEach { u ->
                Box {
                    AsyncImage(u, null, Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape).background(Color(0xAA000000)).clickable { picked.remove(u) },
                        contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, "Quitar", tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
            }
        }
        Field(text, { text = it }, "¿Qué tal la rodada?", singleLine = false)
        Text("Rodada", fontSize = 13.sp, color = C.text2, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Ninguna", ride == null) { ride = null }
            rides.take(6).forEach { r -> Pill(r.title.take(24), ride?.id == r.id) { scope.launch { ride = Rides.load(r.id) } } }
        }
        ride?.let { Text("Se mostrará: ${Geo.fmtKm(it.stats.distanceM)} · ${Geo.fmtTime(it.stats.movingS)} · ${maxOf(it.stats.maxLeanL, it.stats.maxLeanR).toInt()}° · ${it.stats.curves} curvas", color = C.text2, fontSize = 13.sp) }
        if (err.isNotBlank()) Text(err, color = C.red)
        if (!done) BigButton(if (busy) progress.ifBlank { "Publicando…" } else "Publicar en ${g.name}", enabled = !busy && (picked.isNotEmpty() || text.isNotBlank())) {
            busy = true; err = ""
            scope.launch {
                progress = "Preparando fotos…"
                val files = picked.mapNotNull { Photos.compress(ctx, it) }
                val e = Feed.publish(g.id, text.trim(), files, ride) { n -> progress = "Subiendo foto $n de ${files.size}…" }
                busy = false
                if (e == null) { done = true; FeedState.dirty.value++ } else err = e
            }
        }
        if (busy) CircularProgressIndicator(color = C.accent)
        if (done) {
            Card {
                Text("¡Publicado!", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("¿La quieres compartir también en WhatsApp o Instagram? Se arma una tarjeta con la foto y los datos de la rodada.", color = C.text2)
                SoftButton("Compartir tarjeta", Modifier.fillMaxWidth(), icon = Icons.Default.Share, bg = C.bg) {
                    scope.launch {
                        val photo = picked.firstOrNull()?.let { Photos.compress(ctx, it, 1400) }
                        val r = ride
                        val lines = if (r != null) listOf("${Geo.fmtKm(r.stats.distanceM)} · ${Geo.fmtTime(r.stats.movingS)}",
                            "Inclinación máx. ${maxOf(r.stats.maxLeanL, r.stats.maxLeanR).toInt()}° · ${r.stats.curves} curvas") else listOf(g.name)
                        val f = Photos.card(ctx, photo, r?.title ?: text.take(28).ifBlank { "Rodada" }, lines) ?: return@launch
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartir"))
                    }
                }
                BigButton("Ver las fotos del grupo", height = 50.dp) { Nav.back(); if (Nav.current != Scr.Feed) Nav.go(Scr.Feed) }
            }
        }
        Text("Las fotos se achican para que carguen rápido y solo las ve tu grupo.", fontSize = 12.sp, color = C.text2)
        Spacer(Modifier.height(16.dp))
    }
}
