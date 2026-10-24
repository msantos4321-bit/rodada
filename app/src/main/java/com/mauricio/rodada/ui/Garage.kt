package com.mauricio.rodada.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mauricio.rodada.data.Bike
import com.mauricio.rodada.data.Doc
import com.mauricio.rodada.data.FuelLog
import com.mauricio.rodada.data.MaintItem
import com.mauricio.rodada.data.Store
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun GarageScreen() {
    val bikes by Store.bikes.collectAsState()
    var sel by remember { mutableStateOf(Store.currentBike?.id) }
    var doneItem by remember { mutableStateOf<MaintItem?>(null) }
    var docEdit by remember { mutableStateOf<Doc?>(null) }
    var fuelAdd by remember { mutableStateOf(false) }
    val b = bikes.firstOrNull { it.id == sel } ?: bikes.firstOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Header("Garaje") {
            IconButton(onClick = { Nav.go(Scr.BikeEdit(null)) }) { Icon(Icons.Default.Add, "Agregar moto", tint = C.accentText) }
        }
        if (b == null) {
            Text("Agrega tu moto para llevar el mantenimiento, la gasolina y los papeles.", color = C.text2)
            BigButton("Agregar moto", icon = Icons.Default.Add) { Nav.go(Scr.BikeEdit(null)) }
            return@Column
        }
        if (bikes.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bikes.forEach { x -> Pill(x.name, x.id == b.id) { sel = x.id; Store.currentBikeId = x.id } }
        }
        Card(padding = 0.dp, onClick = { Nav.go(Scr.MyBike(b.id)) }) {
            BikeImage(b, "", Modifier.fillMaxWidth().height(190.dp))
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Ficha", Modifier.weight(1f), bg = C.bg, height = 44.dp) { Nav.go(Scr.MyBike(b.id)) }
                SoftButton("Bitácora", Modifier.weight(1f), bg = C.bg, height = 44.dp) { Nav.go(Scr.Log(b.id)) }
                SoftButton("Antes de salir", Modifier.weight(1.3f), bg = C.bg, height = 44.dp) { Nav.go(Scr.Checklist(b.id)) }
            }
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(listOf(b.year, "${"%,d".format(b.odometer)} km").filter { it.isNotBlank() }.joinToString(" · "), color = C.text2)
                }
                IconButton(onClick = { Nav.go(Scr.BikeEdit(b.id)) }) { Icon(Icons.Default.Edit, "Editar", tint = C.text2) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Rendimiento", if (b.realKmPerL > 0) String.format(Locale.US, "%.1f km/L", b.realKmPerL) else "— km/L", Modifier.weight(1f), C.bg)
                Stat("Alcance", if (b.rangeKm > 0) "${b.rangeKm} km" else "—", Modifier.weight(1f), C.bg)
                val month = LocalDate.now().toString().take(7)
                val spent = b.fuel.filter { it.date.startsWith(month) }.sumOf { it.pesos }
                Stat("Gasto mes", "$${spent.toInt()}", Modifier.weight(1f), C.bg)
            }
            SoftButton("Registrar carga de gasolina", Modifier.fillMaxWidth(), icon = Icons.Default.LocalGasStation, bg = C.bg) { fuelAdd = true }
            val rides by com.mauricio.rodada.data.Rides.list.collectAsState()
            val mine = rides.filter { it.bikeId == b.id || (it.bikeId.isBlank() && it.bike == b.name) }
            if (mine.isNotEmpty()) Text("Con Rodada: ${com.mauricio.rodada.data.Geo.fmtKm(mine.sumOf { it.stats.distanceM })} en ${mine.size} rodadas · gastado en la bitácora: $${"%,.0f".format(b.log.sumOf { it.cost })}",
                fontSize = 13.sp, color = C.text2)
        }

        SectionTitle("Mantenimiento")
        Card(padding = 14.dp) {
            b.maint.forEachIndexed { i, m ->
                val kmLeft = if (m.everyKm > 0) m.lastKm + m.everyKm - b.odometer else null
                val daysLeft = if (m.everyMonths > 0 && m.lastDate.isNotBlank()) runCatching {
                    ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(m.lastDate).plusMonths(m.everyMonths.toLong()))
                }.getOrNull() else null
                val frac = when {
                    kmLeft != null -> 1f - kmLeft.toFloat() / m.everyKm
                    daysLeft != null -> 1f - daysLeft.toFloat() / (m.everyMonths * 30f)
                    else -> 0f
                }
                val due = (kmLeft != null && kmLeft <= 150) || (daysLeft != null && daysLeft <= 7)
                val txt = when {
                    kmLeft != null && kmLeft <= 0 -> "ya toca (${-kmLeft} km pasado)"
                    kmLeft != null -> "en ${"%,d".format(kmLeft)} km"
                    daysLeft != null && daysLeft <= 0 -> "ya toca"
                    daysLeft != null -> "en ${daysLeft / 30} meses"
                    else -> ""
                }
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(txt, color = if (due) C.accentText else C.text2, fontWeight = if (due) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                        TextButton(onClick = { doneItem = m }) { Text("Hecho", color = C.accentText) }
                    }
                    Progress(frac, if (due) C.accent else C.greenBright)
                }
                if (i < b.maint.size - 1) Spacer(Modifier.height(2.dp))
            }
        }

        SectionTitle("Papeles")
        Card(padding = 14.dp) {
            b.docs.forEachIndexed { i, d ->
                val days = if (d.expires.isBlank()) null else runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(d.expires)) }.getOrNull()
                Row2(d.name, when {
                    d.expires.isBlank() -> "Toca para poner la fecha de vencimiento"
                    days == null -> d.expires
                    days < 0 -> "Venció hace ${-days} días"
                    days <= 30 -> "Vence en $days días (${fmtDay(d.expires)})"
                    else -> "Vence el ${fmtDay(d.expires)}"
                }, divider = i < b.docs.size - 1, onClick = { docEdit = d })
            }
        }

        if (b.fuel.isNotEmpty()) {
            SectionTitle("Cargas de gasolina")
            Card(padding = 14.dp) {
                b.fuel.sortedByDescending { it.km }.take(6).forEachIndexed { i, f ->
                    Row2("${f.liters} L · $${f.pesos.toInt()}", "${fmtDay(f.date)} · ${"%,d".format(f.km)} km", divider = i < minOf(5, b.fuel.size - 1))
                }
            }
        }
        Text("Rodada suma solo los kilómetros de cada rodada grabada. Si usas la moto sin grabar, actualiza el kilometraje al registrar gasolina.", fontSize = 12.sp, color = C.text2)
        Spacer(Modifier.height(16.dp))
    }

    doneItem?.let { m ->
        val bike = b ?: return@let
        Confirm("${m.name}", "¿Lo hiciste hoy a los ${"%,d".format(bike.odometer)} km? Se reinicia la cuenta.", "Sí, hecho", {
            Store.upsertBike(bike.copy(maint = bike.maint.map { if (it.id == m.id) it.copy(lastKm = bike.odometer, lastDate = LocalDate.now().toString()) else it }))
        }, { doneItem = null })
    }
    docEdit?.let { d ->
        val bike = b ?: return@let
        var date by remember(d.id) { mutableStateOf(d.expires) }
        AlertDialog(onDismissRequest = { docEdit = null }, title = { Text(d.name) },
            text = { Field(date, { date = it }, "Vence (AAAA-MM-DD)", placeholder = "2027-03-15") },
            confirmButton = { TextButton(onClick = {
                val ok = date.isBlank() || runCatching { LocalDate.parse(date.trim()) }.isSuccess
                if (ok) { Store.upsertBike(bike.copy(docs = bike.docs.map { if (it.id == d.id) it.copy(expires = date.trim()) else it })); docEdit = null }
            }) { Text("Guardar", color = C.accentText) } },
            dismissButton = { TextButton(onClick = { docEdit = null }) { Text("Cancelar", color = C.text2) } }, containerColor = Color.White)
    }
    if (fuelAdd && b != null) {
        var liters by remember { mutableStateOf("") }
        var pesos by remember { mutableStateOf("") }
        var km by remember { mutableStateOf(b.odometer.toString()) }
        AlertDialog(onDismissRequest = { fuelAdd = false }, title = { Text("Carga de gasolina") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(liters, { liters = it }, "Litros", number = true)
                Field(pesos, { pesos = it }, "Pesos", number = true)
                Field(km, { km = it.filter(Char::isDigit) }, "Kilometraje ahorita", number = true)
                Text("Llena el tanque cada vez para que el rendimiento salga exacto.", fontSize = 12.sp, color = C.text2)
            } },
            confirmButton = { TextButton(onClick = {
                val l = liters.replace(',', '.').toDoubleOrNull() ?: return@TextButton
                val odo = km.toIntOrNull() ?: b.odometer
                Store.upsertBike(b.copy(odometer = maxOf(odo, b.odometer), fuel = b.fuel + FuelLog(LocalDate.now().toString(), odo, l, pesos.replace(',', '.').toDoubleOrNull() ?: 0.0), refuelKm = maxOf(odo, b.odometer)))
                fuelAdd = false
            }) { Text("Guardar", color = C.accentText) } },
            dismissButton = { TextButton(onClick = { fuelAdd = false }) { Text("Cancelar", color = C.text2) } }, containerColor = Color.White)
    }
}

fun fmtDay(iso: String): String = runCatching {
    LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "MX")))
}.getOrDefault(iso)

@Composable
fun BikeEditScreen(id: String?) {
    val old = Store.bikes.value.firstOrNull { it.id == id }
    var name by remember { mutableStateOf(old?.name.orEmpty()) }
    var year by remember { mutableStateOf(old?.year.orEmpty()) }
    var odo by remember { mutableStateOf(old?.odometer?.toString().orEmpty()) }
    var tank by remember { mutableStateOf(old?.tankL?.toString().orEmpty()) }
    var kmpl by remember { mutableStateOf(old?.kmPerL?.takeIf { it > 0 }?.toString().orEmpty()) }
    val maint = remember { (old?.maint ?: Bike.defaultMaint(0)).map { mutableStateOf(it.everyKm.toString()) to it } }
    var del by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(if (old == null) "Nueva moto" else "Editar moto", onBack = { Nav.back() })
        Field(name, { name = it }, "Marca y modelo")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(year, { year = it.filter(Char::isDigit).take(4) }, "Año", Modifier.weight(1f), number = true)
            Field(odo, { odo = it.filter(Char::isDigit) }, "Kilometraje", Modifier.weight(1f), number = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(tank, { tank = it }, "Tanque (L)", Modifier.weight(1f), number = true)
            Field(kmpl, { kmpl = it }, "Rinde (km/L)", Modifier.weight(1f), number = true)
        }
        SectionTitle("Cada cuántos km")
        Text("Ajústalos según el manual de tu moto (0 = solo por tiempo).", fontSize = 13.sp, color = C.text2)
        maint.forEach { (st, m) ->
            var v by st
            Field(v, { v = it.filter(Char::isDigit) }, m.name, number = true)
        }
        BigButton("Guardar", enabled = name.isNotBlank()) {
            val km = odo.toIntOrNull() ?: 0
            val items = maint.map { (st, m) -> m.copy(everyKm = st.value.toIntOrNull() ?: m.everyKm, lastKm = if (old == null) km else m.lastKm) }
            val b = (old ?: Bike(name = name)).copy(name = name.trim(), year = year, odometer = km,
                tankL = tank.replace(',', '.').toDoubleOrNull() ?: 15.0, kmPerL = kmpl.replace(',', '.').toDoubleOrNull() ?: 0.0, maint = items)
            Store.upsertBike(b)
            if (old == null) Store.currentBikeId = b.id
            Nav.back()
        }
        if (old != null) OutlineBtn("Quitar esta moto", Modifier.fillMaxWidth(), color = C.red) { del = true }
        Spacer(Modifier.height(16.dp))
    }
    if (del && old != null) Confirm("¿Quitar ${old.name}?", "Se borra su mantenimiento y papeles.", "Quitar", { Store.deleteBike(old.id); Nav.back() }, { del = false }, danger = true)
}
