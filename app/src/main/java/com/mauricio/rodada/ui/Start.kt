package com.mauricio.rodada.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mauricio.rodada.R
import com.mauricio.rodada.data.Bike
import com.mauricio.rodada.data.Contact
import com.mauricio.rodada.data.License
import com.mauricio.rodada.data.Store
import kotlinx.coroutines.launch

@Composable
fun Onboarding(done: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val prof = Store.profile.value
    var name by remember { mutableStateOf(prof.name) }
    var bike by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var tank by remember { mutableStateOf("") }
    var kmpl by remember { mutableStateOf("") }
    var c1 by remember { mutableStateOf("") }; var p1 by remember { mutableStateOf("") }
    var c2 by remember { mutableStateOf("") }; var p2 by remember { mutableStateOf("") }

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Store.onboarded = true
        done()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(48.dp))
        when (step) {
            0 -> {
                Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
                Text("Bienvenido a Rodada", style = MaterialTheme.typography.headlineLarge)
                Text("La app para rodar en ciudad, carretera y terracería.", color = C.text2, fontSize = 17.sp)
                Card {
                    Row2("Rutas con curvas", "Rápida, curvas, súper curvas, sin casetas o terracería", leading = { IconBadge(Icons.Default.Map) })
                    Row2("Inclinación y aceleración", "Cada curva, fuerza G y 0–100, con coach de manejo", leading = { IconBadge(Icons.Default.Speed) })
                    Row2("Detección de caídas", "Si no respondes en un minuto, avisa a tus contactos", leading = { IconBadge(Icons.Default.HealthAndSafety) })
                    Row2("Tu grupo de amigos", "Ruta de la semana, chat y todos en el mapa en vivo", leading = { IconBadge(Icons.Default.Groups) }, divider = false)
                }
                Field(name, { name = it }, "¿Cómo te llamas?")
                BigButton("Continuar", enabled = name.isNotBlank()) {
                    Store.saveProfile(Store.profile.value.copy(name = name.trim())); step = 1
                }
            }
            1 -> {
                Text("Tu moto", style = MaterialTheme.typography.headlineLarge)
                Text("Con esto llevamos el mantenimiento y calculamos si te alcanza la gasolina. Lo puedes cambiar después.", color = C.text2)
                Field(bike, { bike = it }, "Marca y modelo", placeholder = "Ej. Yamaha MT-07")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(year, { year = it.filter(Char::isDigit).take(4) }, "Año", Modifier.weight(1f), number = true)
                    Field(km, { km = it.filter(Char::isDigit) }, "Kilometraje", Modifier.weight(1f), number = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(tank, { tank = it }, "Tanque (litros)", Modifier.weight(1f), number = true)
                    Field(kmpl, { kmpl = it }, "Rinde (km por litro)", Modifier.weight(1f), number = true)
                }
                BigButton("Continuar") {
                    if (bike.isNotBlank()) {
                        val odo = km.toIntOrNull() ?: 0
                        Store.upsertBike(Bike(name = bike.trim(), year = year, odometer = odo,
                            tankL = tank.replace(',', '.').toDoubleOrNull() ?: 15.0,
                            kmPerL = kmpl.replace(',', '.').toDoubleOrNull() ?: 0.0, maint = Bike.defaultMaint(odo)))
                    }
                    step = 2
                }
                OutlineBtn("Lo hago después", Modifier.fillMaxWidth()) { step = 2 }
            }
            else -> {
                Text("Contactos de emergencia", style = MaterialTheme.typography.headlineLarge)
                Text("Si Rodada detecta una caída y no respondes en un minuto, les manda un SMS con tu ubicación. Pon al menos uno.", color = C.text2)
                Card {
                    Field(c1, { c1 = it }, "Nombre")
                    Field(p1, { p1 = it }, "Teléfono", phone = true)
                }
                Card {
                    Field(c2, { c2 = it }, "Nombre (opcional)")
                    Field(p2, { p2 = it }, "Teléfono", phone = true)
                }
                Text("Ahora te va a pedir permisos: ubicación (para rutas y rodadas), notificaciones (grupo y avisos) y SMS (solo para emergencias).",
                    color = C.text2, fontSize = 14.sp)
                BigButton("Empezar a rodar") {
                    val list = listOf(Contact(c1.trim(), p1.trim()), Contact(c2.trim(), p2.trim())).filter { it.phone.isNotBlank() }
                    if (list.isNotEmpty()) Store.saveContacts(list)
                    val req = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.SEND_SMS)
                    if (Build.VERSION.SDK_INT >= 33) req += Manifest.permission.POST_NOTIFICATIONS
                    perms.launch(req.toTypedArray())
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Pantalla de activación: solo aparece cuando el dueño prende la venta desde el servidor. */
@Composable
fun LicenseScreen() {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val st by License.state.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(32.dp))
        Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
        Text("Activa Rodada", style = MaterialTheme.typography.headlineLarge)
        Text("Rutas con curvas, inclinación, detección de caídas, tu garaje y tu grupo de amigos en una sola app.", color = C.text2)
        (st as? License.State.Need)?.why?.let { Text(it, color = C.red) }
        Card {
            Field(code, { code = it.uppercase() }, "Código de activación", placeholder = "RO-XXXX-XXXX")
            BigButton(if (busy) "Revisando…" else "Activar", enabled = !busy && code.length >= 8) {
                busy = true; scope.launch { msg = License.activate(code).orEmpty(); busy = false }
            }
        }
        OutlineBtn("Probar gratis", Modifier.fillMaxWidth()) {
            busy = true; scope.launch { msg = License.trial().orEmpty(); busy = false }
        }
        if (msg.isNotBlank()) Text(msg, color = C.red)
        Text("Tu código te lo da quien te vendió Rodada. Funciona sin internet hasta 10 días.", fontSize = 13.sp, color = C.text2)
    }
}
