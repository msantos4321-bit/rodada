package com.mauricio.rodada.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mauricio.rodada.MainActivity
import com.mauricio.rodada.data.Group
import com.mauricio.rodada.data.License
import com.mauricio.rodada.data.Route
import com.mauricio.rodada.data.Store
import com.mauricio.rodada.data.Updater
import com.mauricio.rodada.ride.Session
import kotlinx.coroutines.flow.MutableStateFlow

/** Pantallas de la app. */
sealed interface Scr {
    data object Home : Scr
    data object Routes : Scr
    data object Ride : Scr
    data object Group : Scr
    data object Garage : Scr
    data class Summary(val id: String) : Scr
    data object Rides : Scr
    data object Safety : Scr
    data object Settings : Scr
    data object Chat : Scr
    data object Live : Scr
    data object Community : Scr
    data object Places : Scr
    data class BikeEdit(val id: String?) : Scr
    data object Weekly : Scr
    data object Catalog : Scr
    data object Feed : Scr
    data class NewPost(val rideId: String?) : Scr
    data object PostDetail : Scr
    data object PhotoView : Scr
    data class MyBike(val id: String) : Scr
    data class Log(val id: String) : Scr
    data class LogEdit(val bikeId: String, val entryId: String?) : Scr
    data class Checklist(val id: String) : Scr
    data object Help : Scr
}

/** Navegación sencilla con pila (el botón Regresar funciona en todas las pantallas). */
object Nav {
    val stack = mutableStateListOf<Scr>(Scr.Home)
    val current: Scr get() = stack.last()
    fun go(s: Scr) { stack.add(s) }
    fun tab(s: Scr) { stack.clear(); stack.add(s) }
    fun back(): Boolean { if (stack.size > 1) { stack.removeAt(stack.size - 1); return true }; return false }
    /** Ruta que llegó desde un archivo GPX. */
    val importedRoute = MutableStateFlow<Route?>(null)
    /** Ruta del grupo para planear en la pestaña Rutas. */
    val pendingRoute = mutableStateOf<com.mauricio.rodada.data.CatalogRoute?>(null)
}

@Composable
fun App(act: MainActivity) {
    val lic by License.state.collectAsState()
    var onboarded by androidx.compose.runtime.remember { mutableStateOf(Store.onboarded) }

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        if (Store.onboarded) perms.launch(list.toTypedArray())
        Group.refresh()
        Updater.check()
    }

    if (License.required && lic is License.State.Need) { LicenseScreen(); return }
    if (!onboarded) { Onboarding { onboarded = true }; return }

    // Notificaciones y enlaces que piden abrir una pestaña
    val tab by act.pendingTab
    LaunchedEffect(tab) {
        when (tab) {
            "grupo" -> Nav.tab(Scr.Group)
            "chat" -> { Nav.tab(Scr.Group); Nav.go(Scr.Chat) }
            "garaje" -> Nav.tab(Scr.Garage)
            "rutas" -> Nav.tab(Scr.Routes)
            "rodar" -> Nav.tab(Scr.Ride)
        }
        act.pendingTab.value = null
    }
    // Al terminar una rodada se abre su resumen
    val finished by Session.finished.collectAsState()
    LaunchedEffect(finished) {
        finished?.let { Nav.tab(Scr.Home); Nav.go(Scr.Summary(it)); Session.finished.value = null }
    }

    // Regresar: primero dentro de la sección; desde cualquier pestaña, al Inicio (no se cierra la app a media rodada)
    BackHandler(enabled = Nav.stack.size > 1 || Nav.current != Scr.Home) { if (!Nav.back()) Nav.tab(Scr.Home) }

    val cur = Nav.current
    val crash by Session.crash.collectAsState()
    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().let { if (cur == Scr.Ride || cur == Scr.Live || cur == Scr.PhotoView) it else it.statusBarsPadding() }) {
                when (cur) {
                    Scr.Home -> HomeScreen()
                    Scr.Routes -> RoutesScreen()
                    Scr.Ride -> RideScreen()
                    Scr.Group -> GroupScreen(act)
                    Scr.Garage -> GarageScreen()
                    is Scr.Summary -> SummaryScreen(cur.id)
                    Scr.Rides -> RidesScreen()
                    Scr.Safety -> SafetyScreen()
                    Scr.Settings -> SettingsScreen()
                    Scr.Chat -> ChatScreen()
                    Scr.Live -> LiveScreen()
                    Scr.Community -> CommunityScreen()
                    Scr.Places -> PlacesScreen()
                    is Scr.BikeEdit -> BikeEditScreen(cur.id)
                    Scr.Weekly -> WeeklyScreen()
                    Scr.Catalog -> CatalogScreen()
                    Scr.Feed -> FeedScreen()
                    is Scr.NewPost -> NewPostScreen(cur.rideId)
                    Scr.PostDetail -> PostDetailScreen()
                    Scr.PhotoView -> PhotoViewScreen()
                    is Scr.MyBike -> MyBikeScreen(cur.id)
                    is Scr.Log -> LogScreen(cur.id)
                    is Scr.LogEdit -> LogEditScreen(cur.bikeId, cur.entryId)
                    is Scr.Checklist -> ChecklistScreen(cur.id)
                    Scr.Help -> HelpScreen()
                }
            }
            val tabs = listOf(
                Triple(Scr.Home, "Inicio", Icons.Default.Home),
                Triple(Scr.Routes, "Rutas", Icons.Default.Map),
                Triple(Scr.Ride, "Rodar", Icons.Default.Speed),
                Triple(Scr.Group, "Grupo", Icons.Default.Groups),
                Triple(Scr.Garage, "Garaje", Icons.Default.Garage),
            )
            val root = Nav.stack.first()
            val dash by Session.dash.collectAsState()
            // Barra «Rodando» en las demás pantallas para volver a la rodada
            if (dash.recording && cur != Scr.Ride && cur != Scr.Live && cur != Scr.PhotoView) {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().background(C.accent).clickable { Nav.tab(Scr.Ride) }
                    .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.size(10.dp).background(Color.White, androidx.compose.foundation.shape.CircleShape))
                    Text("  ${if (dash.paused) "Rodada en pausa" else "Rodando"} · ${com.mauricio.rodada.data.Geo.fmtKm(dash.distanceM)} · ${dash.speed.toInt()} km/h",
                        color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("Volver ›", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
            val fullRide = cur == Scr.Ride && dash.recording
            if (!fullRide && cur != Scr.Live && cur != Scr.PhotoView) NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                tabs.forEach { (s, label, icon) ->
                    NavigationBarItem(selected = root == s, onClick = { Nav.tab(s) },
                        icon = { Icon(icon, label) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = C.accent, selectedTextColor = C.accent,
                            indicatorColor = C.accentSoft, unselectedIconColor = C.text2, unselectedTextColor = C.text2))
                }
            }
        }
        if (crash != null) CrashOverlay(crash!!)
    }
}

